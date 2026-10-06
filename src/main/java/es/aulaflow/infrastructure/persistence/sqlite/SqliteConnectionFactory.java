package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class SqliteConnectionFactory {

    private static final String JDBC_URL_PREFIX =
            "jdbc:sqlite:";

    private static final String ENABLE_FOREIGN_KEYS =
            "PRAGMA foreign_keys = ON";

    private final Path databasePath;

    public SqliteConnectionFactory(
            SqliteConfig config
    ) {
        Objects.requireNonNull(
                config,
                "La configuración SQLite no puede ser null."
        );

        this.databasePath =
                config.getDatabasePath();
    }

    public Connection openConnection() {
        createParentDirectories();

        Connection connection = null;

        try {
            connection = DriverManager.getConnection(
                    JDBC_URL_PREFIX + databasePath
            );

            enableForeignKeys(connection);

            return connection;
        } catch (SQLException exception) {
            closeAfterFailure(
                    connection,
                    exception
            );

            throw new PersistenceException(
                    "No se ha podido abrir la base de datos SQLite.",
                    exception
            );
        }
    }

    private void createParentDirectories() {
        Path parentDirectory =
                databasePath.getParent();

        if (parentDirectory == null) {
            throw new IllegalStateException(
                    "La ruta SQLite debe tener un directorio padre."
            );
        }

        try {
            Files.createDirectories(parentDirectory);
        } catch (IOException | SecurityException exception) {
            throw new PersistenceException(
                    "No se ha podido preparar el directorio "
                            + "de la base de datos.",
                    exception
            );
        }
    }

    private static void enableForeignKeys(
            Connection connection
    ) throws SQLException {
        try (
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(ENABLE_FOREIGN_KEYS);
        }
    }

    private static void closeAfterFailure(
            Connection connection,
            SQLException originalException
    ) {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException closeException) {
            originalException.addSuppressed(
                    closeException
            );
        }
    }
}
