package es.aulaflow.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteTransactionTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteConnectionFactory connectionFactory;

    @BeforeEach
    void createTemporaryDatabase() throws Exception {
        Path databasePath =
                temporaryDirectory.resolve(
                        "transaction.db"
                );

        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                databasePath.toString()
                        ),
                        temporaryDirectory
                );

        connectionFactory =
                new SqliteConnectionFactory(config);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    CREATE TABLE transaction_examples (
                        id INTEGER PRIMARY KEY,
                        description TEXT NOT NULL
                    )
                    """);
        }
    }

    @Test
    void commitPersistsChangesAcrossConnections()
            throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            insertDescription(
                    connection,
                    "cambio confirmado"
            );

            connection.commit();
        }

        assertEquals(
                1,
                countRows()
        );
    }

    @Test
    void rollbackDiscardsChangesAcrossConnections()
            throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            insertDescription(
                    connection,
                    "cambio cancelado"
            );

            connection.rollback();
        }

        assertEquals(
                0,
                countRows()
        );
    }

    private static void insertDescription(
            Connection connection,
            String description
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO transaction_examples (
                                    description
                                ) VALUES (?)
                                """)
        ) {
            statement.setString(
                    1,
                    description
            );

            assertEquals(
                    1,
                    statement.executeUpdate()
            );
        }
    }

    private int countRows() throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT COUNT(*)
                                FROM transaction_examples
                                """);
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            resultSet.next();

            return resultSet.getInt(1);
        }
    }
}
