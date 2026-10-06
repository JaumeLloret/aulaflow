package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.CardMoveResult;
import es.aulaflow.application.board.CardNotFoundException;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SqliteCardRepository
        implements CardRepository {

    private static final String NEXT_CARD_POSITION = """
            SELECT COALESCE(MAX(ca.position) + 1, 0)
            FROM board_columns bc
            JOIN boards b ON b.id = bc.board_id
            LEFT JOIN cards ca ON ca.column_id = bc.id
            WHERE bc.id = ?
              AND bc.board_id = ?
              AND b.owner_id = ?
            GROUP BY bc.id
            """;

    private static final String INSERT_CARD = """
            INSERT INTO cards (column_id, title, description, position)
            VALUES (?, ?, ?, ?)
            """;

    private static final String FIND_CARD_BY_PK = """
            SELECT id, column_id, title, description, position, created_on, updated_on
            FROM cards
            WHERE id = ?
            """;

    private static final String FIND_CARD_OWNED = """
            SELECT ca.id, ca.column_id, ca.title, ca.description,
                   ca.position, ca.created_on, ca.updated_on
            FROM cards ca
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ca.id = ? AND b.id = ? AND b.owner_id = ?
            """;

    private static final String LIST_CARDS_BY_BOARD = """
            SELECT ca.id, ca.column_id, ca.title, ca.description,
                   ca.position, ca.created_on, ca.updated_on
            FROM cards ca
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE b.id = ? AND b.owner_id = ?
            ORDER BY bc.position, ca.position
            """;

    private static final String UPDATE_CARD = """
            UPDATE cards
            SET title = ?, description = ?, updated_on = unixepoch()
            WHERE id = ?
              AND EXISTS (
                  SELECT 1
                  FROM board_columns bc
                  JOIN boards b ON b.id = bc.board_id
                  WHERE bc.id = cards.column_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private static final String DELETE_CARD = """
            DELETE FROM cards
            WHERE id = ?
              AND EXISTS (
                  SELECT 1
                  FROM board_columns bc
                  JOIN boards b ON b.id = bc.board_id
                  WHERE bc.id = cards.column_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private static final String FIND_CARD_LOCATION = """
            SELECT ca.position, ca.column_id
            FROM cards ca
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ca.id = ? AND b.id = ? AND b.owner_id = ?
            """;

    private static final String VERIFY_COLUMN_IN_BOARD = """
            SELECT bc.id
            FROM board_columns bc
            JOIN boards b ON b.id = bc.board_id
            WHERE bc.id = ? AND b.id = ? AND b.owner_id = ?
            """;

    private static final String COUNT_CARDS_IN_COLUMN = """
            SELECT COUNT(*) FROM cards WHERE column_id = ?
            """;

    private static final String MAX_POSITION_IN_COLUMN = """
            SELECT COALESCE(MAX(position), -1)
            FROM cards
            WHERE column_id = ?
            """;

    private static final String SELECT_IDS_IN_COLUMN = """
            SELECT id FROM cards WHERE column_id = ? ORDER BY position
            """;

    private static final String UPDATE_POSITION = """
            UPDATE cards SET position = ? WHERE id = ?
            """;

    private static final String MOVE_TO_COLUMN = """
            UPDATE cards
            SET column_id = ?, position = ?, updated_on = unixepoch()
            WHERE id = ?
            """;

    private static final String TOUCH_UPDATED = """
            UPDATE cards SET updated_on = unixepoch() WHERE id = ?
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteCardRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );
    }

    @Override
    public Card create(
            long ownerId,
            long boardId,
            ColumnId columnId,
            CardTitle title,
            CardDescription description
    ) {
        Objects.requireNonNull(columnId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(description);

        try (Connection connection =
                     connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                Optional<Integer> position =
                        nextCardPosition(
                                connection,
                                columnId.value(),
                                boardId,
                                ownerId
                        );

                if (position.isEmpty()) {
                    connection.rollback();
                    throw new CardNotFoundException();
                }

                long cardId = insertCard(
                        connection,
                        columnId.value(),
                        title,
                        description,
                        position.orElseThrow()
                );

                Card card = readCardByPk(connection, cardId)
                        .orElseThrow(() -> new SQLException(
                                "No se pudo releer la tarjeta creada."
                        ));

                connection.commit();
                return card;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public List<Card> listByBoard(
            long ownerId,
            long boardId
    ) {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                LIST_CARDS_BY_BOARD
                        )
        ) {
            statement.setLong(1, boardId);
            statement.setLong(2, ownerId);

            try (ResultSet rs = statement.executeQuery()) {
                List<Card> cards = new ArrayList<>();
                while (rs.next()) {
                    cards.add(readCard(rs));
                }
                return List.copyOf(cards);
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Optional<Card> findById(
            long ownerId,
            long boardId,
            CardId cardId
    ) {
        Objects.requireNonNull(cardId);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_CARD_OWNED
                        )
        ) {
            statement.setLong(1, cardId.value());
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next()
                        ? Optional.of(readCard(rs))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean update(
            long ownerId,
            long boardId,
            CardId cardId,
            CardTitle title,
            CardDescription description
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(description);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_CARD
                        )
        ) {
            statement.setString(1, title.value());
            statement.setString(2, description.value());
            statement.setLong(3, cardId.value());
            statement.setLong(4, boardId);
            statement.setLong(5, ownerId);

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean delete(
            long ownerId,
            long boardId,
            CardId cardId
    ) {
        Objects.requireNonNull(cardId);

        try (Connection connection =
                     connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                long[] location = findCardLocation(
                        connection,
                        cardId.value(),
                        boardId,
                        ownerId
                );

                if (location == null) {
                    connection.rollback();
                    return false;
                }

                long columnId = location[0];

                try (PreparedStatement del =
                             connection.prepareStatement(
                                     DELETE_CARD
                             )) {
                    del.setLong(1, cardId.value());
                    del.setLong(2, boardId);
                    del.setLong(3, ownerId);
                    del.executeUpdate();
                }

                List<Long> remainingIds = readColumnIds(
                        connection,
                        columnId
                );

                applyPositions(
                        connection,
                        columnId,
                        remainingIds
                );

                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public CardMoveResult move(
            long ownerId,
            long boardId,
            CardId cardId,
            ColumnId targetColumnId,
            int targetPosition
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(targetColumnId);

        try (Connection connection =
                     connectionFactory.openConnection()) {
            connection.setAutoCommit(false);

            try {
                long[] location = findCardLocation(
                        connection,
                        cardId.value(),
                        boardId,
                        ownerId
                );

                if (location == null) {
                    connection.rollback();
                    return CardMoveResult.NOT_FOUND;
                }

                long srcColId = location[0];
                int srcPos = (int) location[1];

                if (!isColumnInBoard(
                        connection,
                        targetColumnId.value(),
                        boardId,
                        ownerId
                )) {
                    connection.rollback();
                    return CardMoveResult.NOT_FOUND;
                }

                int targetCount = countInColumn(
                        connection,
                        targetColumnId.value()
                );

                boolean sameColumn =
                        srcColId == targetColumnId.value();

                int maxValid = sameColumn
                        ? targetCount - 1
                        : targetCount;

                if (
                        targetPosition < 0
                                || targetPosition > maxValid
                ) {
                    connection.rollback();
                    return CardMoveResult.INVALID_POSITION;
                }

                if (sameColumn && srcPos == targetPosition) {
                    connection.rollback();
                    return CardMoveResult.MOVED;
                }

                if (sameColumn) {
                    moveSameColumn(
                            connection,
                            cardId.value(),
                            srcColId,
                            srcPos,
                            targetPosition
                    );
                } else {
                    moveBetweenColumns(
                            connection,
                            cardId.value(),
                            srcColId,
                            targetColumnId.value(),
                            targetPosition
                    );
                }

                connection.commit();
                return CardMoveResult.MOVED;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static void moveSameColumn(
            Connection connection,
            long cardId,
            long columnId,
            int srcPos,
            int dstPos
    ) throws SQLException {
        List<Long> ids =
                readColumnIds(connection, columnId);

        ids.remove(srcPos);
        ids.add(dstPos, cardId);

        applyPositions(
                connection,
                columnId,
                ids
        );

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                TOUCH_UPDATED
                        )
        ) {
            statement.setLong(1, cardId);
            statement.executeUpdate();
        }
    }

    private static void moveBetweenColumns(
            Connection connection,
            long cardId,
            long sourceColumnId,
            long targetColumnId,
            int targetPosition
    ) throws SQLException {
        int temporaryPosition =
                nextTemporaryPosition(
                        connection,
                        targetColumnId
                );

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                MOVE_TO_COLUMN
                        )
        ) {
            statement.setLong(1, targetColumnId);
            statement.setInt(
                    2,
                    temporaryPosition
            );
            statement.setLong(3, cardId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "No se pudo mover temporalmente la tarjeta."
                );
            }
        }

        List<Long> sourceIds =
                readColumnIds(
                        connection,
                        sourceColumnId
                );

        applyPositions(
                connection,
                sourceColumnId,
                sourceIds
        );

        List<Long> targetIds =
                readColumnIds(
                        connection,
                        targetColumnId
                );

        targetIds.remove(
                Long.valueOf(cardId)
        );
        targetIds.add(
                targetPosition,
                cardId
        );

        applyPositions(
                connection,
                targetColumnId,
                targetIds
        );
    }

    private static Optional<Integer> nextCardPosition(
            Connection connection,
            long columnId,
            long boardId,
            long ownerId
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             NEXT_CARD_POSITION
                     )) {
            statement.setLong(1, columnId);
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Object value = rs.getObject(1);
                return value == null
                        ? Optional.empty()
                        : Optional.of(rs.getInt(1));
            }
        }
    }

    private static long insertCard(
            Connection connection,
            long columnId,
            CardTitle title,
            CardDescription description,
            int position
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_CARD,
                             Statement.RETURN_GENERATED_KEYS
                     )) {
            statement.setLong(1, columnId);
            statement.setString(2, title.value());
            statement.setString(3, description.value());
            statement.setInt(4, position);
            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                            "SQLite no devolvió el identificador de la tarjeta."
                    );
                }
                return keys.getLong(1);
            }
        }
    }

    private static Optional<Card> readCardByPk(
            Connection connection,
            long cardId
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_CARD_BY_PK
                     )) {
            statement.setLong(1, cardId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next()
                        ? Optional.of(readCard(rs))
                        : Optional.empty();
            }
        }
    }

    private static long[] findCardLocation(
            Connection connection,
            long cardId,
            long boardId,
            long ownerId
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_CARD_LOCATION
                     )) {
            statement.setLong(1, cardId);
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new long[]{
                        rs.getLong("column_id"),
                        rs.getInt("position")
                };
            }
        }
    }

    private static boolean isColumnInBoard(
            Connection connection,
            long columnId,
            long boardId,
            long ownerId
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             VERIFY_COLUMN_IN_BOARD
                     )) {
            statement.setLong(1, columnId);
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int nextTemporaryPosition(
            Connection connection,
            long columnId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                MAX_POSITION_IN_COLUMN
                        )
        ) {
            statement.setLong(1, columnId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "No se pudo obtener la posición máxima."
                    );
                }

                int maximumPosition =
                        resultSet.getInt(1);

                return Math.addExact(
                        maximumPosition,
                        1
                );
            }
        }
    }

    private static int countInColumn(
            Connection connection,
            long columnId
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             COUNT_CARDS_IN_COLUMN
                     )) {
            statement.setLong(1, columnId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static List<Long> readColumnIds(
            Connection connection,
            long columnId
    ) throws SQLException {
        try (PreparedStatement st =
                     connection.prepareStatement(
                             SELECT_IDS_IN_COLUMN
                     )) {
            st.setLong(1, columnId);
            try (ResultSet rs = st.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        }
    }

    private static void applyPositions(
            Connection connection,
            long columnId,
            List<Long> cardIds
    ) throws SQLException {
        int temporaryBase =
                nextTemporaryPosition(
                        connection,
                        columnId
                );

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_POSITION
                        )
        ) {
            for (
                    int index = 0;
                    index < cardIds.size();
                    index++
            ) {
                statement.setInt(
                        1,
                        Math.addExact(
                                temporaryBase,
                                index
                        )
                );
                statement.setLong(
                        2,
                        cardIds.get(index)
                );

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo asignar una posición temporal."
                    );
                }
            }

            for (
                    int index = 0;
                    index < cardIds.size();
                    index++
            ) {
                statement.setInt(1, index);
                statement.setLong(
                        2,
                        cardIds.get(index)
                );

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo asignar la posición definitiva."
                    );
                }
            }
        }
    }

    private static Card readCard(
            ResultSet rs
    ) throws SQLException {
        return new Card(
                new CardId(rs.getLong("id")),
                new ColumnId(rs.getLong("column_id")),
                new CardTitle(rs.getString("title")),
                new CardDescription(
                        rs.getString("description")
                ),
                rs.getInt("position"),
                Instant.ofEpochSecond(
                        rs.getLong("created_on")
                ),
                Instant.ofEpochSecond(
                        rs.getLong("updated_on")
                )
        );
    }

    private static void rollback(
            Connection connection,
            SQLException original
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static PersistenceException persistenceFailure(
            SQLException exception
    ) {
        return new PersistenceException(
                "No se ha podido acceder a las tarjetas persistidas.",
                exception
        );
    }
}
