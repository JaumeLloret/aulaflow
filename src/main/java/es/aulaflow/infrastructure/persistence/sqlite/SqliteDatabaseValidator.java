package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SqliteDatabaseValidator {

    private static final String JDBC_URL_PREFIX =
            "jdbc:sqlite:";

    private static final String INTEGRITY_CHECK =
            "PRAGMA integrity_check";

    private static final String FOREIGN_KEY_CHECK =
            "PRAGMA foreign_key_check";

    private static final String CHECK_HISTORY_TABLE = """
            SELECT 1
            FROM sqlite_schema
            WHERE type = 'table'
              AND name = 'schema_migrations'
            """;

    private static final String READ_APPLIED_VERSIONS = """
            SELECT version
            FROM schema_migrations
            """;

    public void validate(Path databasePath) {
        Path path = requireRegularDatabaseFile(databasePath);

        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setReadOnly(true);

        try (
                Connection connection =
                        DriverManager.getConnection(
                                JDBC_URL_PREFIX + path,
                                sqliteConfig.toProperties()
                        )
        ) {
            checkIntegrity(connection);
            checkForeignKeys(connection);
            checkMigrationHistory(connection);
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "La copia SQLite no ha superado la validación.",
                    exception
            );
        }
    }

    private static Path requireRegularDatabaseFile(
            Path databasePath
    ) {
        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "La ruta de la copia no puede ser null."
            );
        }

        Path path = databasePath
                .toAbsolutePath()
                .normalize();

        if (
                !Files.isRegularFile(
                        path,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new PersistenceException(
                    "La copia SQLite no existe o no es un "
                            + "archivo regular.",
                    null
            );
        }

        try {
            if (Files.size(path) == 0) {
                throw new PersistenceException(
                        "La copia SQLite está vacía.",
                        null
                );
            }
        } catch (IOException exception) {
            throw new PersistenceException(
                    "No se ha podido leer la copia SQLite.",
                    exception
            );
        }

        return path;
    }

    private static void checkIntegrity(
            Connection connection
    ) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(INTEGRITY_CHECK)
        ) {
            int rows = 0;

            while (resultSet.next()) {
                rows++;

                if (!"ok".equalsIgnoreCase(resultSet.getString(1))) {
                    throw new PersistenceException(
                            "La copia SQLite contiene errores "
                                    + "de integridad.",
                            null
                    );
                }
            }

            if (rows != 1) {
                throw new PersistenceException(
                        "La comprobación de integridad SQLite "
                                + "no ha devuelto el resultado esperado.",
                        null
                );
            }
        }
    }

    private static void checkForeignKeys(
            Connection connection
    ) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(FOREIGN_KEY_CHECK)
        ) {
            if (resultSet.next()) {
                throw new PersistenceException(
                        "La copia SQLite contiene referencias "
                                + "foráneas inválidas.",
                        null
                );
            }
        }
    }

    private static void checkMigrationHistory(
            Connection connection
    ) throws SQLException {
        if (!historyTableExists(connection)) {
            throw new PersistenceException(
                    "La copia SQLite no contiene el historial "
                            + "de esquema de AulaFlow.",
                    null
            );
        }

        Set<String> knownVersions =
                ClasspathMigrationCatalog
                        .loadDefault()
                        .stream()
                        .map(SqliteMigration::getVersion)
                        .collect(Collectors.toUnmodifiableSet());

        Set<String> unknownVersions = new TreeSet<>();

        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                READ_APPLIED_VERSIONS
                        )
        ) {
            while (resultSet.next()) {
                String version = resultSet.getString(1);

                if (!knownVersions.contains(version)) {
                    unknownVersions.add(version);
                }
            }
        }

        if (!unknownVersions.isEmpty()) {
            throw new PersistenceException(
                    "La copia SQLite contiene versiones de "
                            + "esquema no reconocidas: "
                            + unknownVersions,
                    null
            );
        }
    }

    private static boolean historyTableExists(
            Connection connection
    ) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                CHECK_HISTORY_TABLE
                        )
        ) {
            return resultSet.next();
        }
    }
}
