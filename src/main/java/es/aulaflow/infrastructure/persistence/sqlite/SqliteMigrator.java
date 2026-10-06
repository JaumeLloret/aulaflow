package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SqliteMigrator {

    private static final String HISTORY_TABLE =
            "schema_migrations";

    private static final String CHECK_HISTORY_TABLE = """
            SELECT 1
            FROM sqlite_schema
            WHERE type = ?
              AND name = ?
            """;

    private static final String READ_APPLIED_VERSIONS = """
            SELECT version
            FROM schema_migrations
            """;

    private static final String RECORD_MIGRATION = """
            INSERT INTO schema_migrations (
                version,
                description
            ) VALUES (?, ?)
            """;

    private final SqliteConnectionFactory connectionFactory;
    private final List<SqliteMigration> migrations;

    public SqliteMigrator(
            SqliteConnectionFactory connectionFactory
    ) {
        this(
                connectionFactory,
                ClasspathMigrationCatalog.loadDefault()
        );
    }

    SqliteMigrator(
            SqliteConnectionFactory connectionFactory,
            List<SqliteMigration> migrations
    ) {
        this.connectionFactory =
                Objects.requireNonNull(
                        connectionFactory,
                        "La factoría de conexiones no puede ser null."
                );

        this.migrations =
                List.copyOf(
                        Objects.requireNonNull(
                                migrations,
                                "Las migraciones no pueden ser null."
                        )
                );
    }

    public void migrate() {
        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            Set<String> appliedVersions =
                    readAppliedVersions(connection);

            rejectUnrecognizedSchema(appliedVersions);

            applyPendingMigrations(
                    connection,
                    appliedVersions
            );
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "No se han podido aplicar "
                            + "las migraciones SQLite.",
                    exception
            );
        }
    }

    private Set<String> readAppliedVersions(
            Connection connection
    ) throws SQLException {
        if (!historyTableExists(connection)) {
            return Set.of();
        }

        Set<String> appliedVersions =
                new HashSet<>();

        try (
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                READ_APPLIED_VERSIONS
                        )
        ) {
            while (resultSet.next()) {
                appliedVersions.add(
                        resultSet.getString("version")
                );
            }
        }

        return Set.copyOf(appliedVersions);
    }

    private void rejectUnrecognizedSchema(
            Set<String> appliedVersions
    ) {
        Set<String> knownVersions =
                migrations
                        .stream()
                        .map(SqliteMigration::getVersion)
                        .collect(Collectors.toSet());

        Set<String> unrecognizedVersions =
                new TreeSet<>(appliedVersions);

        unrecognizedVersions.removeAll(knownVersions);

        if (!unrecognizedVersions.isEmpty()) {
            throw new PersistenceException(
                    "La base de datos SQLite contiene versiones "
                            + "de esquema no reconocidas por esta "
                            + "instalación de AulaFlow: "
                            + unrecognizedVersions
                            + ". Puede proceder de una versión "
                            + "más reciente. Se aborta el arranque "
                            + "para no dañar los datos existentes.",
                    null
            );
        }
    }

    private boolean historyTableExists(
            Connection connection
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                CHECK_HISTORY_TABLE
                        )
        ) {
            statement.setString(1, "table");
            statement.setString(2, HISTORY_TABLE);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                return resultSet.next();
            }
        }
    }

    private void applyPendingMigrations(
            Connection connection,
            Set<String> appliedVersions
    ) throws SQLException {
        List<SqliteMigration> pendingMigrations =
                migrations
                        .stream()
                        .filter(
                                migration ->
                                        !appliedVersions.contains(
                                                migration.getVersion()
                                        )
                        )
                        .toList();

        if (pendingMigrations.isEmpty()) {
            return;
        }

        connection.setAutoCommit(false);

        try {
            for (
                    SqliteMigration migration
                    : pendingMigrations
            ) {
                applyMigration(
                        connection,
                        migration
                );
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackAfterFailure(
                    connection,
                    exception
            );

            throw exception;
        }
    }

    private static void applyMigration(
            Connection connection,
            SqliteMigration migration
    ) throws SQLException {
        try (
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(
                    migration.getSql()
            );
        }

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                RECORD_MIGRATION
                        )
        ) {
            statement.setString(
                    1,
                    migration.getVersion()
            );

            statement.setString(
                    2,
                    migration.getDescription()
            );

            statement.executeUpdate();
        }
    }

    private static void rollbackAfterFailure(
            Connection connection,
            SQLException originalException
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(
                    rollbackException
            );
        }
    }
}
