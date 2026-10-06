package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.DuplicateLabelNameException;
import es.aulaflow.application.board.LabelRepository;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelId;
import es.aulaflow.domain.board.LabelName;
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

public final class SqliteLabelRepository
        implements LabelRepository {

    private static final String INSERT_LABEL = """
            INSERT INTO labels (
                board_id, name, normalized_name, color
            ) VALUES (?, ?, ?, ?)
            """;

    private static final String BOARD_OWNED_BY = """
            SELECT 1 FROM boards
            WHERE id = ? AND owner_id = ?
            """;

    private static final String FIND_LABEL_BY_PK = """
            SELECT id, board_id, name, color, created_on, updated_on
            FROM labels
            WHERE id = ?
            """;

    private static final String LIST_LABELS_BY_BOARD = """
            SELECT l.id, l.board_id, l.name, l.color,
                   l.created_on, l.updated_on
            FROM labels l
            JOIN boards b ON b.id = l.board_id
            WHERE l.board_id = ? AND b.owner_id = ?
            ORDER BY l.id
            """;

    private static final String UPDATE_LABEL = """
            UPDATE labels
            SET name = ?, normalized_name = ?, color = ?,
                updated_on = unixepoch()
            WHERE id = ?
              AND board_id = ?
              AND EXISTS (
                  SELECT 1 FROM boards
                  WHERE boards.id = labels.board_id
                    AND boards.owner_id = ?
              )
            """;

    private static final String DELETE_LABEL = """
            DELETE FROM labels
            WHERE id = ?
              AND board_id = ?
              AND EXISTS (
                  SELECT 1 FROM boards
                  WHERE boards.id = labels.board_id
                    AND boards.owner_id = ?
              )
            """;

    private static final String FIND_OWNED_CARD = """
            SELECT ca.id
            FROM cards ca
            JOIN board_columns bc ON bc.id = ca.column_id
            JOIN boards b ON b.id = bc.board_id
            WHERE ca.id = ? AND b.id = ? AND b.owner_id = ?
            """;

    private static final String FIND_LABEL_IN_BOARD = """
            SELECT id FROM labels
            WHERE id = ? AND board_id = ?
            """;

    private static final String FIND_ASSIGNMENT = """
            SELECT 1 FROM card_labels
            WHERE card_id = ? AND label_id = ?
            """;

    private static final String INSERT_ASSIGNMENT = """
            INSERT INTO card_labels (card_id, label_id)
            VALUES (?, ?)
            """;

    private static final String DELETE_ASSIGNMENT = """
            DELETE FROM card_labels
            WHERE card_id = ?
              AND label_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM cards ca
                  JOIN board_columns bc ON bc.id = ca.column_id
                  JOIN boards b ON b.id = bc.board_id
                  WHERE ca.id = card_labels.card_id
                    AND b.id = ? AND b.owner_id = ?
              )
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteLabelRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );
    }

    @Override
    public Optional<Label> create(
            long ownerId,
            long boardId,
            LabelName name,
            LabelColor color
    ) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(color);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            if (!isBoardOwned(connection, boardId, ownerId)) {
                return Optional.empty();
            }

            try (
                    PreparedStatement statement =
                            connection.prepareStatement(
                                    INSERT_LABEL,
                                    Statement
                                            .RETURN_GENERATED_KEYS
                            )
            ) {
                statement.setLong(1, boardId);
                statement.setString(2, name.value());
                statement.setString(3, name.normalized());
                statement.setString(4, color.key());
                statement.executeUpdate();

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {
                    if (!keys.next()) {
                        throw new SQLException(
                                "SQLite no devolvió el "
                                        + "identificador de la "
                                        + "etiqueta."
                        );
                    }

                    return findByPk(
                            connection,
                            keys.getLong(1)
                    );
                }
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<Label> listByBoard(
            long ownerId,
            long boardId
    ) {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                LIST_LABELS_BY_BOARD
                        )
        ) {
            statement.setLong(1, boardId);
            statement.setLong(2, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                List<Label> labels = new ArrayList<>();

                while (resultSet.next()) {
                    labels.add(readLabel(resultSet));
                }

                return List.copyOf(labels);
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Map<Long, List<Label>> listByCards(
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
                SELECT cl.card_id, l.id, l.board_id, l.name,
                       l.color, l.created_on, l.updated_on
                FROM card_labels cl
                JOIN labels l ON l.id = cl.label_id
                JOIN cards ca ON ca.id = cl.card_id
                JOIN board_columns bc ON bc.id = ca.column_id
                JOIN boards b ON b.id = bc.board_id
                WHERE b.id = ? AND b.owner_id = ?
                  AND cl.card_id IN (%s)
                ORDER BY cl.card_id, l.id
                """.formatted(placeholders);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, boardId);
            statement.setLong(2, ownerId);

            int index = 3;
            for (CardId cardId : cardIds) {
                statement.setLong(index++, cardId.value());
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                Map<Long, List<Label>> result =
                        new LinkedHashMap<>();

                while (resultSet.next()) {
                    long cardId = resultSet.getLong("card_id");
                    result.computeIfAbsent(
                            cardId,
                            key -> new ArrayList<>()
                    ).add(readLabel(resultSet));
                }

                Map<Long, List<Label>> immutable =
                        new HashMap<>();

                result.forEach((key, value) ->
                        immutable.put(
                                key,
                                List.copyOf(value)
                        )
                );

                return Map.copyOf(immutable);
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean update(
            long ownerId,
            long boardId,
            LabelId labelId,
            LabelName name,
            LabelColor color
    ) {
        Objects.requireNonNull(labelId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(color);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_LABEL
                        )
        ) {
            statement.setString(1, name.value());
            statement.setString(2, name.normalized());
            statement.setString(3, color.key());
            statement.setLong(4, labelId.value());
            statement.setLong(5, boardId);
            statement.setLong(6, ownerId);

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean delete(
            long ownerId,
            long boardId,
            LabelId labelId
    ) {
        Objects.requireNonNull(labelId);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                DELETE_LABEL
                        )
        ) {
            statement.setLong(1, labelId.value());
            statement.setLong(2, boardId);
            statement.setLong(3, ownerId);

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public AssignmentResult assign(
            long ownerId,
            long boardId,
            CardId cardId,
            LabelId labelId
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(labelId);

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
                    return AssignmentResult.CARD_NOT_FOUND;
                }

                if (
                        !exists(
                                connection,
                                FIND_LABEL_IN_BOARD,
                                labelId.value(),
                                boardId
                        )
                ) {
                    connection.rollback();
                    return AssignmentResult.LABEL_NOT_FOUND;
                }

                boolean alreadyAssigned = exists(
                        connection,
                        FIND_ASSIGNMENT,
                        cardId.value(),
                        labelId.value()
                );

                if (alreadyAssigned) {
                    connection.rollback();
                    return AssignmentResult.ALREADY_ASSIGNED;
                }

                try (
                        PreparedStatement statement =
                                connection.prepareStatement(
                                        INSERT_ASSIGNMENT
                                )
                ) {
                    statement.setLong(1, cardId.value());
                    statement.setLong(2, labelId.value());
                    statement.executeUpdate();
                }

                connection.commit();
                return AssignmentResult.ASSIGNED;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean unassign(
            long ownerId,
            long boardId,
            CardId cardId,
            LabelId labelId
    ) {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(labelId);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                DELETE_ASSIGNMENT
                        )
        ) {
            statement.setLong(1, cardId.value());
            statement.setLong(2, labelId.value());
            statement.setLong(3, boardId);
            statement.setLong(4, ownerId);

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private static boolean isBoardOwned(
            Connection connection,
            long boardId,
            long ownerId
    ) throws SQLException {
        return exists(
                connection,
                BOARD_OWNED_BY,
                boardId,
                ownerId
        );
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

    private static Optional<Label> findByPk(
            Connection connection,
            long labelId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_LABEL_BY_PK
                        )
        ) {
            statement.setLong(1, labelId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next()
                        ? Optional.of(readLabel(resultSet))
                        : Optional.empty();
            }
        }
    }

    private static Label readLabel(
            ResultSet resultSet
    ) throws SQLException {
        return new Label(
                new LabelId(resultSet.getLong("id")),
                new BoardId(resultSet.getLong("board_id")),
                new LabelName(resultSet.getString("name")),
                LabelColor.fromKey(
                        resultSet.getString("color")
                ),
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

    /**
     * Traduce un fallo SQL en la excepción correspondiente. Cuando la
     * causa es la violación de {@code UNIQUE(board_id, normalized_name)}
     * lanza directamente {@link DuplicateLabelNameException}; en
     * cualquier otro caso devuelve una {@link PersistenceException}
     * genérica para que la vuelva a lanzar quien llama.
     */
    private static PersistenceException translate(
            SQLException exception
    ) {
        if (isUniqueViolation(exception)) {
            throw new DuplicateLabelNameException();
        }

        return new PersistenceException(
                "No se ha podido acceder a las etiquetas persistidas.",
                exception
        );
    }

    private static boolean isUniqueViolation(
            SQLException exception
    ) {
        return exception
                instanceof org.sqlite.SQLiteException sqliteException
                && sqliteException.getResultCode()
                == org.sqlite.SQLiteErrorCode
                        .SQLITE_CONSTRAINT_UNIQUE;
    }
}
