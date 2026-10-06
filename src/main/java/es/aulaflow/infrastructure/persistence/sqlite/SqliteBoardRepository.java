package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SqliteBoardRepository
        implements BoardRepository {

    private static final String INSERT_BOARD = """
            INSERT INTO boards (owner_id, name)
            VALUES (?, ?)
            """;

    private static final String INSERT_COLUMN = """
            INSERT INTO board_columns (
                board_id,
                name,
                position
            ) VALUES (?, ?, ?)
            """;

    private static final String LIST_BOARDS = """
            SELECT id, owner_id, name, created_on
            FROM boards
            WHERE owner_id = ?
            ORDER BY id
            """;

    private static final String FIND_BOARD = """
            SELECT id, owner_id, name, created_on
            FROM boards
            WHERE id = ?
              AND owner_id = ?
            """;

    private static final String LIST_COLUMNS = """
            SELECT id, board_id, name, position, created_on
            FROM board_columns
            WHERE board_id = ?
            ORDER BY position
            """;

    private static final String RENAME_BOARD = """
            UPDATE boards
            SET name = ?
            WHERE id = ?
              AND owner_id = ?
            """;

    private static final String NEXT_COLUMN_POSITION = """
            SELECT COALESCE(MAX(c.position) + 1, 0)
            FROM boards b
            LEFT JOIN board_columns c
              ON c.board_id = b.id
            WHERE b.id = ?
              AND b.owner_id = ?
            GROUP BY b.id
            """;

    private static final String RENAME_COLUMN = """
            UPDATE board_columns
            SET name = ?
            WHERE id = ?
              AND board_id = ?
              AND EXISTS (
                  SELECT 1
                  FROM boards
                  WHERE boards.id = board_columns.board_id
                    AND boards.owner_id = ?
              )
            """;

    private static final String UPDATE_POSITION = """
            UPDATE board_columns
            SET position = ?
            WHERE id = ?
              AND board_id = ?
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteBoardRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );
    }

    @Override
    public BoardDetails create(
            long ownerId,
            BoardName name,
            List<ColumnName> initialColumns
    ) {
        Objects.requireNonNull(name, "El nombre no puede ser null.");
        List<ColumnName> columns = List.copyOf(
                Objects.requireNonNull(
                        initialColumns,
                        "Las columnas no pueden ser null."
                )
        );

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                Board board = insertBoard(
                        connection,
                        ownerId,
                        name
                );

                List<BoardColumn> createdColumns =
                        new ArrayList<>();

                for (int position = 0;
                     position < columns.size();
                     position++) {
                    createdColumns.add(
                            insertColumn(
                                    connection,
                                    board.id(),
                                    columns.get(position),
                                    position
                            )
                    );
                }

                connection.commit();

                return new BoardDetails(
                        board,
                        createdColumns
                );
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public List<Board> findAllByOwner(long ownerId) {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                LIST_BOARDS
                        )
        ) {
            statement.setLong(1, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                List<Board> boards = new ArrayList<>();

                while (resultSet.next()) {
                    boards.add(readBoard(resultSet));
                }

                return List.copyOf(boards);
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Optional<BoardDetails> findByIdAndOwner(
            BoardId boardId,
            long ownerId
    ) {
        Objects.requireNonNull(
                boardId,
                "El identificador no puede ser null."
        );

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            Optional<Board> board = findBoard(
                    connection,
                    boardId,
                    ownerId
            );

            if (board.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(
                    new BoardDetails(
                            board.orElseThrow(),
                            findColumns(
                                    connection,
                                    boardId
                            )
                    )
            );
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean renameBoard(
            BoardId boardId,
            long ownerId,
            BoardName name
    ) {
        Objects.requireNonNull(boardId);
        Objects.requireNonNull(name);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                RENAME_BOARD
                        )
        ) {
            statement.setString(1, name.value());
            statement.setLong(2, boardId.value());
            statement.setLong(3, ownerId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Optional<BoardColumn> addColumn(
            BoardId boardId,
            long ownerId,
            ColumnName name
    ) {
        Objects.requireNonNull(boardId);
        Objects.requireNonNull(name);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                Optional<Integer> position =
                        nextPosition(
                                connection,
                                boardId,
                                ownerId
                        );

                if (position.isEmpty()) {
                    connection.rollback();
                    return Optional.empty();
                }

                BoardColumn column = insertColumn(
                        connection,
                        boardId,
                        name,
                        position.orElseThrow()
                );

                connection.commit();
                return Optional.of(column);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public boolean renameColumn(
            BoardId boardId,
            ColumnId columnId,
            long ownerId,
            ColumnName name
    ) {
        Objects.requireNonNull(boardId);
        Objects.requireNonNull(columnId);
        Objects.requireNonNull(name);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                RENAME_COLUMN
                        )
        ) {
            statement.setString(1, name.value());
            statement.setLong(2, columnId.value());
            statement.setLong(3, boardId.value());
            statement.setLong(4, ownerId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public ReorderResult reorderColumns(
            BoardId boardId,
            long ownerId,
            List<ColumnId> orderedColumnIds
    ) {
        Objects.requireNonNull(boardId);
        List<ColumnId> requestedOrder = List.copyOf(
                Objects.requireNonNull(orderedColumnIds)
        );

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                if (
                        findBoard(
                                connection,
                                boardId,
                                ownerId
                        ).isEmpty()
                ) {
                    connection.rollback();
                    return ReorderResult.BOARD_NOT_FOUND;
                }

                List<BoardColumn> currentColumns =
                        findColumns(
                                connection,
                                boardId
                        );

                Set<ColumnId> currentIds =
                        currentColumns.stream()
                                .map(BoardColumn::id)
                                .collect(
                                        java.util.stream
                                                .Collectors.toSet()
                                );

                if (
                        currentIds.size()
                                != requestedOrder.size()
                                || !currentIds.equals(
                                        new HashSet<>(
                                                requestedOrder
                                        )
                                )
                ) {
                    connection.rollback();
                    return ReorderResult.INVALID_ORDER;
                }

                updatePositions(
                        connection,
                        boardId,
                        requestedOrder,
                        true
                );

                updatePositions(
                        connection,
                        boardId,
                        requestedOrder,
                        false
                );

                connection.commit();
                return ReorderResult.UPDATED;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static Board insertBoard(
            Connection connection,
            long ownerId,
            BoardName name
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_BOARD,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, ownerId);
            statement.setString(2, name.value());
            statement.executeUpdate();

            try (
                    ResultSet keys =
                            statement.getGeneratedKeys()
            ) {
                if (!keys.next()) {
                    throw new SQLException(
                            "SQLite no devolvió el identificador del tablero."
                    );
                }

                BoardId boardId =
                        new BoardId(keys.getLong(1));

                return findBoard(
                        connection,
                        boardId,
                        ownerId
                ).orElseThrow(
                        () -> new SQLException(
                                "No se pudo releer el tablero creado."
                        )
                );
            }
        }
    }

    private static BoardColumn insertColumn(
            Connection connection,
            BoardId boardId,
            ColumnName name,
            int position
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_COLUMN,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, boardId.value());
            statement.setString(2, name.value());
            statement.setInt(3, position);
            statement.executeUpdate();

            try (
                    ResultSet keys =
                            statement.getGeneratedKeys()
            ) {
                if (!keys.next()) {
                    throw new SQLException(
                            "SQLite no devolvió el identificador de columna."
                    );
                }

                return new BoardColumn(
                        new ColumnId(keys.getLong(1)),
                        boardId,
                        name,
                        position,
                        Instant.now()
                );
            }
        }
    }

    private static Optional<Board> findBoard(
            Connection connection,
            BoardId boardId,
            long ownerId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_BOARD
                        )
        ) {
            statement.setLong(1, boardId.value());
            statement.setLong(2, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next()
                        ? Optional.of(readBoard(resultSet))
                        : Optional.empty();
            }
        }
    }

    private static List<BoardColumn> findColumns(
            Connection connection,
            BoardId boardId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                LIST_COLUMNS
                        )
        ) {
            statement.setLong(1, boardId.value());

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                List<BoardColumn> columns =
                        new ArrayList<>();

                while (resultSet.next()) {
                    columns.add(
                            new BoardColumn(
                                    new ColumnId(
                                            resultSet.getLong(
                                                    "id"
                                            )
                                    ),
                                    new BoardId(
                                            resultSet.getLong(
                                                    "board_id"
                                            )
                                    ),
                                    new ColumnName(
                                            resultSet.getString(
                                                    "name"
                                            )
                                    ),
                                    resultSet.getInt(
                                            "position"
                                    ),
                                    Instant.ofEpochSecond(
                                            resultSet.getLong(
                                                    "created_on"
                                            )
                                    )
                            )
                    );
                }

                return List.copyOf(columns);
            }
        }
    }

    private static Optional<Integer> nextPosition(
            Connection connection,
            BoardId boardId,
            long ownerId
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                NEXT_COLUMN_POSITION
                        )
        ) {
            statement.setLong(1, boardId.value());
            statement.setLong(2, ownerId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Object value = resultSet.getObject(1);
                return value == null
                        ? Optional.empty()
                        : Optional.of(
                                resultSet.getInt(1)
                        );
            }
        }
    }

    private static void updatePositions(
            Connection connection,
            BoardId boardId,
            List<ColumnId> order,
            boolean temporary
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_POSITION
                        )
        ) {
            for (int position = 0;
                 position < order.size();
                 position++) {
                statement.setInt(
                        1,
                        temporary
                                ? order.size() + position
                                : position
                );
                statement.setLong(
                        2,
                        order.get(position).value()
                );
                statement.setLong(
                        3,
                        boardId.value()
                );
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private static Board readBoard(
            ResultSet resultSet
    ) throws SQLException {
        return new Board(
                new BoardId(resultSet.getLong("id")),
                resultSet.getLong("owner_id"),
                new BoardName(
                        resultSet.getString("name")
                ),
                Instant.ofEpochSecond(
                        resultSet.getLong("created_on")
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
                "No se ha podido acceder a los tableros persistidos.",
                exception
        );
    }
}
