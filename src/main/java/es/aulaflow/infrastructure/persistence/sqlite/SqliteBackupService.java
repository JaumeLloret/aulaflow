package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class SqliteBackupService {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter
                    .ofPattern("uuuuMMdd'T'HHmmssSSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private final Path activeDatabasePath;
    private final SqliteConnectionFactory connectionFactory;
    private final SqliteBackupConfig backupConfig;
    private final SqliteDatabaseValidator validator;
    private final Clock clock;

    public SqliteBackupService(
            SqliteConfig sqliteConfig,
            SqliteConnectionFactory connectionFactory,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator
    ) {
        this(
                sqliteConfig,
                connectionFactory,
                backupConfig,
                validator,
                Clock.systemUTC()
        );
    }

    SqliteBackupService(
            SqliteConfig sqliteConfig,
            SqliteConnectionFactory connectionFactory,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator,
            Clock clock
    ) {
        Objects.requireNonNull(
                sqliteConfig,
                "La configuración SQLite no puede ser null."
        );

        this.activeDatabasePath =
                sqliteConfig
                        .getDatabasePath()
                        .toAbsolutePath()
                        .normalize();

        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );

        this.backupConfig = Objects.requireNonNull(
                backupConfig,
                "La configuración de backups no puede ser null."
        );

        this.validator = Objects.requireNonNull(
                validator,
                "El validador no puede ser null."
        );

        this.clock = Objects.requireNonNull(
                clock,
                "El reloj no puede ser null."
        );
    }

    public Path createBackup() {
        requireActiveDatabase();

        Path backupDirectory =
                backupConfig.getBackupDirectory();

        prepareBackupDirectory(backupDirectory);

        String fileName =
                "aulaflow-"
                        + FILE_TIMESTAMP.format(clock.instant())
                        + ".db";

        Path destination =
                backupConfig.resolveBackupFile(fileName);

        if (Files.exists(destination)) {
            throw new PersistenceException(
                    "Ya existe una copia con el mismo sello temporal.",
                    null
            );
        }

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            if (!(connection instanceof SQLiteConnection sqliteConnection)) {
                throw new PersistenceException(
                        "La conexión activa no es una conexión SQLite "
                                + "compatible con la operación de backup.",
                        null
                );
            }

            int result =
                    sqliteConnection
                            .getDatabase()
                            .backup(
                                    "main",
                                    destination.toString(),
                                    null
                            );

            if (result != SQLiteErrorCode.SQLITE_OK.code) {
                throw new PersistenceException(
                        "SQLite no ha podido completar la copia.",
                        null
                );
            }

            validator.validate(destination);
            return destination;
        } catch (SQLException exception) {
            deleteFailedBackup(destination, exception);

            throw new PersistenceException(
                    "No se ha podido crear la copia SQLite.",
                    exception
            );
        } catch (RuntimeException exception) {
            deleteFailedBackup(destination, exception);
            throw exception;
        }
    }

    private void requireActiveDatabase() {
        if (
                !Files.isRegularFile(
                        activeDatabasePath,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new PersistenceException(
                    "No existe una base SQLite activa que copiar.",
                    null
            );
        }
    }

    private static void prepareBackupDirectory(
            Path backupDirectory
    ) {
        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException | SecurityException exception) {
            throw new PersistenceException(
                    "No se ha podido preparar el directorio de backups.",
                    exception
            );
        }
    }

    private static void deleteFailedBackup(
            Path destination,
            Exception originalException
    ) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException deleteException) {
            originalException.addSuppressed(deleteException);
        }
    }
}
