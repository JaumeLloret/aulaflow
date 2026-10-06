package es.aulaflow.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprueba que las restricciones relacionales de las migraciones
 * (claves foráneas y borrado en cascada) se aplican realmente cuando
 * {@code PRAGMA foreign_keys} está activo, tal como exige la
 * recuperación segura tras un reinicio: ninguna referencia huérfana
 * debe poder persistirse.
 */
class SqliteRelationalIntegrityTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsCardReferencingNonExistentColumn()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("orphan-card.db");

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO cards (
                                    column_id, title, position
                                ) VALUES (?, ?, ?)
                                """)
        ) {
            statement.setLong(1, 999_999L);
            statement.setString(2, "Tarjeta huérfana");
            statement.setInt(3, 0);

            assertThrows(
                    SQLException.class,
                    statement::executeUpdate
            );
        }
    }

    @Test
    void rejectsColumnReferencingNonExistentBoard()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("orphan-column.db");

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO board_columns (
                                    board_id, name, position
                                ) VALUES (?, ?, ?)
                                """)
        ) {
            statement.setLong(1, 999_999L);
            statement.setString(2, "Columna huérfana");
            statement.setInt(3, 0);

            assertThrows(
                    SQLException.class,
                    statement::executeUpdate
            );
        }
    }

    @Test
    void rejectsBoardReferencingNonExistentAdministrator()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("orphan-board.db");

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO boards (
                                    owner_id, name
                                ) VALUES (?, ?)
                                """)
        ) {
            statement.setLong(1, 999_999L);
            statement.setString(2, "Tablero huérfano");

            assertThrows(
                    SQLException.class,
                    statement::executeUpdate
            );
        }
    }

    @Test
    void cascadeDeletingBoardRemovesColumnsAndCards()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("cascade-delete.db");

        long administratorId = insertAdministrator(
                connectionFactory,
                "profesora.integritat"
        );

        long boardId = insertBoard(
                connectionFactory,
                administratorId,
                "Tauler amb cascada"
        );

        long columnId = insertColumn(
                connectionFactory,
                boardId,
                "Pendent"
        );

        insertCard(connectionFactory, columnId, "Targeta 1");
        insertCard(connectionFactory, columnId, "Targeta 2");

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "DELETE FROM boards WHERE id = ?"
                        )
        ) {
            statement.setLong(1, boardId);
            assertEquals(1, statement.executeUpdate());
        }

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "board_columns",
                        "board_id",
                        boardId
                )
        );

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "cards",
                        "column_id",
                        columnId
                )
        );
    }

    @Test
    void cascadeDeletingAdministratorRemovesCompleteKanban()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory(
                        "administrator-cascade-delete.db"
                );

        long administratorId =
                insertAdministrator(
                        connectionFactory,
                        "profesora.cascada"
                );

        long boardId =
                insertBoard(
                        connectionFactory,
                        administratorId,
                        "Tauler complet"
                );

        long columnId =
                insertColumn(
                        connectionFactory,
                        boardId,
                        "Pendent"
                );

        insertCard(
                connectionFactory,
                columnId,
                "Targeta 1"
        );

        insertCard(
                connectionFactory,
                columnId,
                "Targeta 2"
        );

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "DELETE FROM administrators "
                                        + "WHERE id = ?"
                        )
        ) {
            statement.setLong(
                    1,
                    administratorId
            );

            assertEquals(
                    1,
                    statement.executeUpdate()
            );
        }

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "administrators",
                        "id",
                        administratorId
                )
        );

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "boards",
                        "owner_id",
                        administratorId
                )
        );

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "board_columns",
                        "board_id",
                        boardId
                )
        );

        assertEquals(
                0,
                countRows(
                        connectionFactory,
                        "cards",
                        "column_id",
                        columnId
                )
        );
    }

    private SqliteConnectionFactory migratedConnectionFactory(
            String databaseName
    ) {
        Path databasePath =
                temporaryDirectory.resolve(databaseName);

        SqliteConfig config = SqliteConfig.from(
                Map.of(
                        "AULAFLOW_DB_PATH",
                        databasePath.toString()
                ),
                temporaryDirectory
        );

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(config);

        new SqliteMigrator(connectionFactory).migrate();

        return connectionFactory;
    }

    private static long insertAdministrator(
            SqliteConnectionFactory connectionFactory,
            String username
    ) throws SQLException {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO administrators (
                                    id, username, password_verifier
                                ) VALUES (1, ?, 'placeholder')
                                """)
        ) {
            statement.setString(1, username);
            statement.executeUpdate();
            return 1L;
        }
    }

    private static long insertBoard(
            SqliteConnectionFactory connectionFactory,
            long ownerId,
            String name
    ) throws SQLException {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO boards (
                                    owner_id, name
                                ) VALUES (?, ?)
                                """,
                                java.sql.Statement
                                        .RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, ownerId);
            statement.setString(2, name);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertColumn(
            SqliteConnectionFactory connectionFactory,
            long boardId,
            String name
    ) throws SQLException {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO board_columns (
                                    board_id, name, position
                                ) VALUES (?, ?, 0)
                                """,
                                java.sql.Statement
                                        .RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, boardId);
            statement.setString(2, name);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertCard(
            SqliteConnectionFactory connectionFactory,
            long columnId,
            String title
    ) throws SQLException {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO cards (
                                    column_id, title, position
                                ) VALUES (?, ?,
                                    (SELECT COALESCE(MAX(position) + 1, 0)
                                     FROM cards WHERE column_id = ?)
                                )
                                """)
        ) {
            statement.setLong(1, columnId);
            statement.setString(2, title);
            statement.setLong(3, columnId);
            statement.executeUpdate();
        }
    }

    private static int countRows(
            SqliteConnectionFactory connectionFactory,
            String table,
            String column,
            long value
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE " + column + " = ?";

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
