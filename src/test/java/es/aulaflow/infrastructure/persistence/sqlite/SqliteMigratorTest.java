package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMigratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesEmptyDatabaseToExpectedVersion()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory("fresh.db");

        SqliteMigrator migrator =
                new SqliteMigrator(
                        connectionFactory
                );

        migrator.migrate();

        assertTrue(
                tableExists(
                        connectionFactory,
                        "schema_migrations"
                )
        );

        assertEquals(
                List.of(
                        "V001",
                        "V002",
                        "V003",
                        "V004",
                        "V005",
                        "V006",
                        "V007",
                        "V008",
                        "V009",
                        "V010",
                        "V011",
                        "V012"
                ),
                readAppliedVersions(connectionFactory)
        );
    }

    @Test
    void doesNotApplyRegisteredMigrationsAgain()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        "idempotent.db"
                );

        SqliteMigrator migrator =
                new SqliteMigrator(
                        connectionFactory
                );

        migrator.migrate();
        migrator.migrate();

        assertEquals(
                List.of(
                        "V001",
                        "V002",
                        "V003",
                        "V004",
                        "V005",
                        "V006",
                        "V007",
                        "V008",
                        "V009",
                        "V010",
                        "V011",
                        "V012"
                ),
                readAppliedVersions(connectionFactory)
        );
    }

    @Test
    void appliesMigrationsInManifestOrder()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        "ordered.db"
                );

        SqliteMigrator migrator =
                migratorForManifest(
                        connectionFactory,
                        "baseline.txt"
                );

        migrator.migrate();

        assertEquals(
                List.of(
                        "V001",
                        "V002"
                ),
                readAppliedVersions(connectionFactory)
        );
    }

    @Test
    void rollsBackAllPendingMigrationsAfterFailure()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        "rollback.db"
                );

        migratorForManifest(
                connectionFactory,
                "baseline.txt"
        ).migrate();

        SqliteMigrator failingMigrator =
                migratorForManifest(
                        connectionFactory,
                        "failing.txt"
                );

        assertThrows(
                PersistenceException.class,
                failingMigrator::migrate
        );

        assertFalse(
                tableExists(
                        connectionFactory,
                        "pending_marker"
                )
        );

        assertEquals(
                List.of(
                        "V001",
                        "V002"
                ),
                readAppliedVersions(connectionFactory)
        );
    }

    @Test
    void refusesToStartWithUnrecognizedSchemaVersion()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        "future-schema.db"
                );

        new SqliteMigrator(connectionFactory).migrate();

        recordUnknownMigration(
                connectionFactory,
                "V999"
        );

        SqliteMigrator migratorAfterRestart =
                new SqliteMigrator(connectionFactory);

        PersistenceException exception =
                assertThrows(
                        PersistenceException.class,
                        migratorAfterRestart::migrate
                );

        assertTrue(
                exception.getMessage()
                        .contains("V999")
        );

        assertEquals(
                List.of(
                        "V001",
                        "V002",
                        "V003",
                        "V004",
                        "V005",
                        "V006",
                        "V007",
                        "V008",
                        "V009",
                        "V010",
                        "V011",
                        "V012",
                        "V999"
                ),
                readAppliedVersions(connectionFactory)
        );
    }

    @Test
    void upgradesFromV008BaselinePreservingExistingData()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                createConnectionFactory("v008-baseline.db");

        List<SqliteMigration> v008Catalog =
                ClasspathMigrationCatalog.loadDefault()
                        .subList(0, 8);

        new SqliteMigrator(
                connectionFactory,
                v008Catalog
        ).migrate();

        assertEquals(
                List.of(
                        "V001", "V002", "V003", "V004",
                        "V005", "V006", "V007", "V008"
                ),
                readAppliedVersions(connectionFactory)
        );

        insertLegacyScenario(connectionFactory);

        new SqliteMigrator(connectionFactory).migrate();

        assertEquals(
                List.of(
                        "V001", "V002", "V003", "V004",
                        "V005", "V006", "V007", "V008",
                        "V009", "V010", "V011", "V012"
                ),
                readAppliedVersions(connectionFactory)
        );

        assertTrue(
                tableExists(connectionFactory, "labels")
        );

        assertTrue(
                tableExists(connectionFactory, "card_labels")
        );

        assertTrue(
                tableExists(
                        connectionFactory,
                        "checklist_items"
                )
        );

        assertEquals(
                "Administradora preexistent",
                readSingleColumn(
                        connectionFactory,
                        "SELECT username FROM administrators"
                )
        );

        assertEquals(
                "Tauler preexistent",
                readSingleColumn(
                        connectionFactory,
                        "SELECT name FROM boards"
                )
        );

        assertEquals(
                "Targeta preexistent",
                readSingleColumn(
                        connectionFactory,
                        "SELECT title FROM cards"
                )
        );
    }

    private static void insertLegacyScenario(
            SqliteConnectionFactory connectionFactory
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    INSERT INTO administrators (
                        id, username, password_verifier
                    ) VALUES (
                        1, 'Administradora preexistent',
                        'placeholder'
                    )
                    """);

            statement.execute("""
                    INSERT INTO boards (id, owner_id, name)
                    VALUES (1, 1, 'Tauler preexistent')
                    """);

            statement.execute("""
                    INSERT INTO board_columns (
                        id, board_id, name, position
                    ) VALUES (1, 1, 'Pendent', 0)
                    """);

            statement.execute("""
                    INSERT INTO cards (
                        id, column_id, title, position
                    ) VALUES (
                        1, 1, 'Targeta preexistent', 0
                    )
                    """);
        }

        assertEquals(
                "Administradora preexistent",
                readSingleColumn(
                        connectionFactory,
                        "SELECT username FROM administrators"
                )
        );
    }

    private static String readSingleColumn(
            SqliteConnectionFactory connectionFactory,
            String query
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(query)
        ) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    @Test
    void refusesToStartOnCorruptedDatabaseFile()
            throws Exception {
        Path corruptedDatabase =
                temporaryDirectory.resolve("corrupted.db");

        Files.write(
                corruptedDatabase,
                ("esto no es un archivo SQLite válido, "
                        + "sino contenido corrupto de prueba")
                        .getBytes(StandardCharsets.UTF_8)
        );

        byte[] originalContent =
                Files.readAllBytes(corruptedDatabase);

        SqliteConnectionFactory connectionFactory =
                createConnectionFactory(
                        corruptedDatabase.getFileName()
                                .toString()
                );

        SqliteMigrator migrator =
                new SqliteMigrator(connectionFactory);

        PersistenceException exception =
                assertThrows(
                        PersistenceException.class,
                        migrator::migrate
                );

        assertFalse(
                exception.getMessage().contains(
                        corruptedDatabase.toString()
                ),
                "El mensaje de diagnóstico no debe filtrar "
                        + "la ruta absoluta del archivo."
        );

        assertArrayEquals(
                originalContent,
                Files.readAllBytes(corruptedDatabase),
                "Un arranque fallido no debe sustituir ni "
                        + "truncar el archivo dañado original."
        );
    }

    private static void recordUnknownMigration(
            SqliteConnectionFactory connectionFactory,
            String version
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO schema_migrations (
                                    version,
                                    description
                                ) VALUES (?, ?)
                                """)
        ) {
            statement.setString(1, version);
            statement.setString(
                    2,
                    "migración de una versión futura de AulaFlow"
            );
            statement.executeUpdate();
        }
    }

    private SqliteConnectionFactory createConnectionFactory(
            String databaseName
    ) {
        Path databasePath =
                temporaryDirectory.resolve(
                        databaseName
                );

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

    private static SqliteMigrator migratorForManifest(
            SqliteConnectionFactory connectionFactory,
            String manifestName
    ) {
        return new SqliteMigrator(
                connectionFactory,
                ClasspathMigrationCatalog.load(
                        "/db/migration/scenarios/"
                                + manifestName
                )
        );
    }

    private static List<String> readAppliedVersions(
            SqliteConnectionFactory connectionFactory
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT version
                                FROM schema_migrations
                                ORDER BY rowid
                                """);
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            java.util.ArrayList<String> versions =
                    new java.util.ArrayList<>();

            while (resultSet.next()) {
                versions.add(
                        resultSet.getString("version")
                );
            }

            return List.copyOf(versions);
        }
    }

    private static boolean tableExists(
            SqliteConnectionFactory connectionFactory,
            String tableName
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                SELECT 1
                                FROM sqlite_schema
                                WHERE type = ?
                                  AND name = ?
                                """)
        ) {
            statement.setString(1, "table");
            statement.setString(2, tableName);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next();
            }
        }
    }
}
