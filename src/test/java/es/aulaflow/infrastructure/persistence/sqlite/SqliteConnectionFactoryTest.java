package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteConnectionFactoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDirectoriesAndDatabaseFile()
            throws Exception {
        Path databasePath =
                temporaryDirectory.resolve(
                        "nested/data/aulaflow.db"
                );

        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(databasePath);

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            assertTrue(connection.isValid(1));
        }

        assertTrue(
                Files.isRegularFile(databasePath)
        );
    }

    @Test
    void enablesForeignKeysForEveryConnection()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        temporaryDirectory.resolve(
                                "foreign-keys.db"
                        )
                );

        assertForeignKeysEnabled(
                connectionFactory.openConnection()
        );

        assertForeignKeysEnabled(
                connectionFactory.openConnection()
        );
    }

    @Test
    void persistsParameterizedDataAcrossConnections()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        temporaryDirectory.resolve(
                                "persistence.db"
                        )
                );

        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    CREATE TABLE learning_notes (
                        id INTEGER PRIMARY KEY,
                        content TEXT NOT NULL
                    )
                    """);
        }

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO learning_notes (
                                    content
                                ) VALUES (?)
                                """)
        ) {
            statement.setString(
                    1,
                    "JDBC y SQLite"
            );

            assertEquals(
                    1,
                    statement.executeUpdate()
            );
        }

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT content
                                FROM learning_notes
                                WHERE id = ?
                                """)
        ) {
            statement.setInt(1, 1);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertTrue(resultSet.next());

                assertEquals(
                        "JDBC y SQLite",
                        resultSet.getString("content")
                );
            }
        }
    }

    @Test
    void translatesConnectionFailure() {
        Path directoryInsteadOfDatabase =
                temporaryDirectory.resolve(
                        "not-a-database"
                );

        assertTrue(
                directoryInsteadOfDatabase
                        .toFile()
                        .mkdir()
        );

        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        directoryInsteadOfDatabase
                );

        PersistenceException exception =
                assertThrows(
                        PersistenceException.class,
                        connectionFactory::openConnection
                );

        assertEquals(
                "No se ha podido abrir la base de datos SQLite.",
                exception.getMessage()
        );
    }

    private SqliteConnectionFactory createConnectionFactory(
            Path databasePath
    ) {
        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                databasePath.toString()
                        ),
                        temporaryDirectory
                );

        return new SqliteConnectionFactory(config);
    }

    private static void assertForeignKeysEnabled(
            Connection connection
    ) throws Exception {
        try (
                connection;
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "PRAGMA foreign_keys"
                        )
        ) {
            assertTrue(resultSet.next());

            assertEquals(
                    1,
                    resultSet.getInt(1)
            );
        }
    }
}
