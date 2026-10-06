package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.ChecklistItemMoveResult;
import es.aulaflow.application.board.ChecklistItemRepository;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistItemId;
import es.aulaflow.domain.board.ChecklistItemText;
import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SqliteChecklistItemRepository
        implements ChecklistItemRepository {

    private static final String FIND_OWNED_CARD = """
            SELECT ca.id
            FROM cards ca
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ca.id = ? AND b.id = ? AND b.owner_id = ?
            """;

    private static final String NEXT_POSITION = """
            SELECT COALESCE(MAX(position) + 1, 0)
            FROM checklist_items
            WHERE card_id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO checklist_items (
                card_id, text, position
            ) VALUES (?, ?, ?)
            """;

    private static final String FIND_ITEM_BY_PK = """
            SELECT id, card_id, text, completed, position,
                   created_on, updated_on
            FROM checklist_items
            WHERE id = ?
            """;

    private static final String LIST_ITEMS_BY_CARD = """
            SELECT ci.id, ci.card_id, ci.text, ci.completed,
                   ci.position, ci.created_on, ci.updated_on
            FROM checklist_items ci
            JOIN cards ca ON ca.id = ci.card_id
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ca.id = ? AND b.id = ? AND b.owner_id = ?
            ORDER BY ci.position
            """;

    private static final String UPDATE_TEXT = """
            UPDATE checklist_items
            SET text = ?, updated_on = unixepoch()
            WHERE id = ?
              AND card_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM cards ca
                  JOIN board_columns bc ON bc.id = ca.column_id
                  JOIN boards b ON b.id = bc.board_id
                  WHERE ca.id = checklist_items.card_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private static final String UPDATE_COMPLETED = """
            UPDATE checklist_items
            SET completed = ?, updated_on = unixepoch()
            WHERE id = ?
              AND card_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM cards ca
                  JOIN board_columns bc ON bc.id = ca.column_id
                  JOIN boards b ON b.id = bc.board_id
                  WHERE ca.id = checklist_items.card_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM checklist_items
            WHERE id = ?
              AND card_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM cards ca
                  JOIN board_columns bc ON bc.id = ca.column_id
                  JOIN boards b ON b.id = bc.board_id
                  WHERE ca.id = checklist_items.card_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private static final String FIND_ITEM_LOCATION = """
            SELECT ci.position
            FROM checklist_items ci
            JOIN cards ca ON ca.id = ci.card_id
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ci.id = ? AND ci.card_id = ?
              AND b.id = ? AND b.owner_id = ?
            """;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*)
            FROM checklist_items
            WHERE card_id = ?
            """;

    private static final String SELECT_IDS_IN_CARD = """
            SELECT id
            FROM checklist_items
            WHERE card_id = ?
            ORDER BY position
            """;

    private static final String UPDATE_POSITION = """
            UPDATE checklist_items
            SET position = ?
            WHERE id = ? AND card_id = ?
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteChecklistItemRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );
    }

    @Override
    public Optional<ChecklistItem> create(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemText text
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(text);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                if (
                        !exists(
                                connection,
                                FIND_OWNED_CARD,
                                cardId.value(),
                                boardId,
                                ownerId
                        )
                ) {
                    connection.rollback();
                    return Optional.empty();
                }

                int position = nextPosition(
                        connection,
                        cardId.value()
                );

                long itemId;

                try (
                        PreparedStatement statement =
                                connection.prepareStatement(
                                        INSERT_ITEM,
                                        Statement.RETURN_GENERATED_KEYS
                                )
                ) {
                    statement.setLong(1, cardId.value());
                    statement.setString(2, text.value());
                    statement.setInt(3, position);
                    statement.executeUpdate();

                    try (
                            ResultSet keys =
                                    statement.getGeneratedKeys()
                    ) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "SQLite no devolvió el "
                                            + "identificador del elemento."
                            );
                        }

                        itemId = keys.getLong(1);
                    }
                }

                ChecklistItem created = findByPk(
                        connection,
                        itemId
                ).orElseThrow(
                        () -> new SQLException(
                                "No se pudo releer el elemento creado."
                        )
                );

                connection.commit();
                return Optional.of(created);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public List<ChecklistItem> listByCard(
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
                                LIST_ITEMS_BY_CARD
                        )
        ) {
            statement.setLong(1, cardId.value());
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                List<ChecklistItem> items = new ArrayList<>();

                while (resultSet.next()) {
                    items.add(readItem(resultSet));
                }

                return List.copyOf(items);
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Map<Long, List<ChecklistItem>> listByCards(
            long ownerId,
            long boardId,
            List<CardId> cardIds
    ) {
        Objects.requireNonNull(cardIds);

        if (cardIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = cardIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = """
                SELECT ci.id, ci.card_id, ci.text, ci.completed,
                       ci.position, ci.created_on, ci.updated_on
                FROM checklist_items ci
                JOIN cards ca ON ca.id = ci.card_id
                JOIN board_columns bc ON bc.id = ca.column_id
                JOIN boards b ON b.id = bc.board_id
                WHERE b.id = ? AND b.owner_id = ?
                  AND ci.card_id IN (%s)
                ORDER BY ci.card_id, ci.position
                """.formatted(placeholders);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, boardId);
            statement.setLong(2, ownerId);

            int parameterIndex = 3;
            for (CardId cardId : cardIds) {
                statement.setLong(
                        parameterIndex++,
                        cardId.value()
                );
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                Map<Long, List<ChecklistItem>> mutable =
                        new LinkedHashMap<>();

                while (resultSet.next()) {
                    long cardId =
                            resultSet.getLong("card_id");

                    mutable.computeIfAbsent(
                            cardId,
                            ignored -> new ArrayList<>()
                    ).add(readItem(resultSet));
                }

                Map<Long, List<ChecklistItem>> immutable =
                        new HashMap<>();

                mutable.forEach(
                        (cardId, items) -> immutable.put(
                                cardId,
                                List.copyOf(items)
                        )
                );

                return Map.copyOf(immutable);
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean updateText(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            ChecklistItemText text
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(text);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(UPDATE_TEXT)
        ) {
            statement.setString(1, text.value());
            statement.setLong(2, itemId.value());
            statement.setLong(3, cardId.value());
            statement.setLong(4, boardId);
            statement.setLong(5, ownerId);

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean setCompleted(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            boolean completed
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(itemId);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_COMPLETED
                        )
        ) {
            statement.setInt(1, completed ? 1 : 0);
            statement.setLong(2, itemId.value());
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
            CardId cardId,
            ChecklistItemId itemId
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(itemId);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                Integer position = findPosition(
                        connection,
                        itemId.value(),
                        cardId.value(),
                        boardId,
                        ownerId
                );

                if (position == null) {
                    connection.rollback();
                    return false;
                }

                try (
                        PreparedStatement statement =
                                connection.prepareStatement(
                                        DELETE_ITEM
                                )
                ) {
                    statement.setLong(1, itemId.value());
                    statement.setLong(2, cardId.value());
                    statement.setLong(3, boardId);
                    statement.setLong(4, ownerId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "No se pudo eliminar el elemento."
                        );
                    }
                }

                List<Long> remainingIds = readCardItemIds(
                        connection,
                        cardId.value()
                );

                applyPositions(
                        connection,
                        cardId.value(),
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
    public ChecklistItemMoveResult move(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            int targetPosition
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(itemId);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                Integer sourcePosition = findPosition(
                        connection,
                        itemId.value(),
                        cardId.value(),
                        boardId,
                        ownerId
                );

                if (sourcePosition == null) {
                    connection.rollback();
                    return ChecklistItemMoveResult.NOT_FOUND;
                }

                int count = countItems(
                        connection,
                        cardId.value()
                );

                if (
                        targetPosition < 0
                                || targetPosition >= count
                ) {
                    connection.rollback();
                    return ChecklistItemMoveResult
                            .INVALID_POSITION;
                }

                if (sourcePosition == targetPosition) {
                    connection.rollback();
                    return ChecklistItemMoveResult.MOVED;
                }

                List<Long> itemIds = readCardItemIds(
                        connection,
                        cardId.value()
                );

                itemIds.remove((int) sourcePosition);
                itemIds.add(targetPosition, itemId.value());

                applyPositions(
                        connection,
                        cardId.value(),
                        itemIds
                );

                connection.commit();
                return ChecklistItemMoveResult.MOVED;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static boolean exists(
            Connection connection,
            String sql,
            long... parameters
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            for (
                    int index = 0;
                    index < parameters.length;
                    index++
            ) {
                statement.setLong(
                        index + 1,
                        parameters[index]
                );
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next();
            }
        }
    }

    private static int nextPosition(
            Connection connection,
            long cardId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                NEXT_POSITION
                        )
        ) {
            statement.setLong(1, cardId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static Integer findPosition(
            Connection connection,
            long itemId,
            long cardId,
            long boardId,
            long ownerId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_ITEM_LOCATION
                        )
        ) {
            statement.setLong(1, itemId);
            statement.setLong(2, cardId);
            statement.setLong(3, boardId);
            statement.setLong(4, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next()
                        ? resultSet.getInt("position")
                        : null;
            }
        }
    }

    private static int countItems(
            Connection connection,
            long cardId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(COUNT_ITEMS)
        ) {
            statement.setLong(1, cardId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static List<Long> readCardItemIds(
            Connection connection,
            long cardId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                SELECT_IDS_IN_CARD
                        )
        ) {
            statement.setLong(1, cardId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                List<Long> ids = new ArrayList<>();

                while (resultSet.next()) {
                    ids.add(resultSet.getLong(1));
                }

                return ids;
            }
        }
    }

    /**
     * Reasigna posiciones en dos pasadas para no violar
     * {@code UNIQUE(card_id, position)}. Esta misma operación se usa
     * tanto al mover como al compactar después de eliminar, de modo
     * que una eliminación posterior a una reordenación también sea
     * atómica y segura.
     */
    private static void applyPositions(
            Connection connection,
            long cardId,
            List<Long> itemIds
    ) throws SQLException {
        int temporaryBase = itemIds.size() + 1000;

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_POSITION
                        )
        ) {
            for (
                    int index = 0;
                    index < itemIds.size();
                    index++
            ) {
                statement.setInt(
                        1,
                        temporaryBase + index
                );
                statement.setLong(
                        2,
                        itemIds.get(index)
                );
                statement.setLong(3, cardId);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo asignar una "
                                    + "posición temporal."
                    );
                }
            }

            for (
                    int index = 0;
                    index < itemIds.size();
                    index++
            ) {
                statement.setInt(1, index);
                statement.setLong(
                        2,
                        itemIds.get(index)
                );
                statement.setLong(3, cardId);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo asignar la "
                                    + "posición definitiva."
                    );
                }
            }
        }
    }

    private static Optional<ChecklistItem> findByPk(
            Connection connection,
            long itemId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_ITEM_BY_PK
                        )
        ) {
            statement.setLong(1, itemId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next()
                        ? Optional.of(readItem(resultSet))
                        : Optional.empty();
            }
        }
    }

    private static ChecklistItem readItem(
            ResultSet resultSet
    ) throws SQLException {
        return new ChecklistItem(
                new ChecklistItemId(
                        resultSet.getLong("id")
                ),
                new CardId(
                        resultSet.getLong("card_id")
                ),
                new ChecklistItemText(
                        resultSet.getString("text")
                ),
                resultSet.getInt("completed") == 1,
                resultSet.getInt("position"),
                Instant.ofEpochSecond(
                        resultSet.getLong("created_on")
                ),
                Instant.ofEpochSecond(
                        resultSet.getLong("updated_on")
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
                "No se ha podido acceder al checklist persistido.",
                exception
        );
    }
}
