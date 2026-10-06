package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteBackupRestoreIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsValidatedBackupAndRestoresPreviousState()
            throws Exception {
        TestContext context = createContext();

        writeProbeValue(
                context.connectionFactory(),
                "before-backup"
        );

        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-08-15T12:34:56.789Z"),
                ZoneOffset.UTC
        );

        SqliteBackupService backupService =
                new SqliteBackupService(
                        context.sqliteConfig(),
                        context.connectionFactory(),
                        context.backupConfig(),
                        context.validator(),
                        fixedClock
                );

        Path backup = backupService.createBackup();

        assertEquals(
                "aulaflow-20260815T123456789Z.db",
                backup.getFileName().toString()
        );
        assertTrue(Files.isRegularFile(backup));

        context.validator().validate(backup);

        writeProbeValue(
                context.connectionFactory(),
                "after-backup"
        );

        assertEquals(
                "after-backup",
                readProbeValue(context.connectionFactory())
        );

        SqliteRestoreService restoreService =
                new SqliteRestoreService(
                        context.sqliteConfig(),
                        context.backupConfig(),
                        context.validator()
                );

        restoreService.restore(
                backup.getFileName().toString()
        );

        assertEquals(
                "before-backup",
                readProbeValue(context.connectionFactory())
        );
    }

    @Test
    void invalidBackupNeverReplacesActiveDatabase()
            throws Exception {
        TestContext context = createContext();

        writeProbeValue(
                context.connectionFactory(),
                "active-value"
        );

        byte[] activeBefore = Files.readAllBytes(
                context.sqliteConfig().getDatabasePath()
        );

        Files.createDirectories(
                context.backupConfig().getBackupDirectory()
        );

        Path corruptBackup =
                context.backupConfig()
                        .resolveBackupFile("corrupt.db");

        Files.writeString(
                corruptBackup,
                "this is not sqlite",
                StandardCharsets.UTF_8
        );

        SqliteRestoreService restoreService =
                new SqliteRestoreService(
                        context.sqliteConfig(),
                        context.backupConfig(),
                        context.validator()
                );

        assertThrows(
                PersistenceException.class,
                () -> restoreService.restore("corrupt.db")
        );

        assertArrayEquals(
                activeBefore,
                Files.readAllBytes(
                        context.sqliteConfig().getDatabasePath()
                )
        );

        assertEquals(
                "active-value",
                readProbeValue(context.connectionFactory())
        );
    }

    @Test
    void validatorRejectsUnknownSchemaVersion()
            throws Exception {
        TestContext context = createContext();

        Files.createDirectories(
                context.backupConfig().getBackupDirectory()
        );

        Path futureBackup =
                context.backupConfig()
                        .resolveBackupFile("future.db");

        try (
                Connection connection =
                        DriverManager.getConnection(
                                "jdbc:sqlite:" + futureBackup
                        );
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    CREATE TABLE schema_migrations (
                        version TEXT PRIMARY KEY,
                        description TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schema_migrations (
                        version,
                        description
                    ) VALUES ('V999', 'future')
                    """);
        }

        assertThrows(
                PersistenceException.class,
                () -> context.validator().validate(futureBackup)
        );
    }

    private TestContext createContext() {
        Path activeDatabase =
                temporaryDirectory.resolve("data/aulaflow.db");

        Path backupDirectory =
                temporaryDirectory.resolve("backups");

        SqliteConfig sqliteConfig =
                SqliteConfig.from(
                        Map.of(
                                SqliteConfig.DATABASE_PATH_VARIABLE,
                                activeDatabase.toString()
                        ),
                        temporaryDirectory
                );

        SqliteBackupConfig backupConfig =
                SqliteBackupConfig.from(
                        Map.of(
                                SqliteBackupConfig.BACKUP_DIRECTORY_VARIABLE,
                                backupDirectory.toString()
                        ),
                        temporaryDirectory
                );

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(sqliteConfig);

        new SqliteMigrator(connectionFactory).migrate();

        return new TestContext(
                sqliteConfig,
                backupConfig,
                connectionFactory,
                new SqliteDatabaseValidator()
        );
    }

    private static void writeProbeValue(
            SqliteConnectionFactory connectionFactory,
            String value
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS recovery_probe (
                        value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate(
                    "DELETE FROM recovery_probe"
            );

            try (
                    var preparedStatement =
                            connection.prepareStatement(
                                    "INSERT INTO recovery_probe(value) VALUES (?)"
                            )
            ) {
                preparedStatement.setString(1, value);
                preparedStatement.executeUpdate();
            }
        }
    }

    private static String readProbeValue(
            SqliteConnectionFactory connectionFactory
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT value FROM recovery_probe"
                        )
        ) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private record TestContext(
            SqliteConfig sqliteConfig,
            SqliteBackupConfig backupConfig,
            SqliteConnectionFactory connectionFactory,
            SqliteDatabaseValidator validator
    ) {
    }
}
