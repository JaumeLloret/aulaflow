package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class SqliteRestoreService {

    private final Path activeDatabasePath;
    private final SqliteBackupConfig backupConfig;
    private final SqliteDatabaseValidator validator;

    public SqliteRestoreService(
            SqliteConfig sqliteConfig,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator
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

        this.backupConfig = Objects.requireNonNull(
                backupConfig,
                "La configuración de backups no puede ser null."
        );

        this.validator = Objects.requireNonNull(
                validator,
                "El validador no puede ser null."
        );
    }

    public void restore(String backupFileName) {
        Path backupPath =
                backupConfig.resolveBackupFile(
                        backupFileName
                );

        if (activeDatabasePath.equals(backupPath)) {
            throw new IllegalArgumentException(
                    "La copia no puede ser la propia base activa."
            );
        }

        validator.validate(backupPath);

        Path parent = activeDatabasePath.getParent();

        if (parent == null) {
            throw new PersistenceException(
                    "La base activa debe tener un directorio padre.",
                    null
            );
        }

        Path temporary = null;

        try {
            Files.createDirectories(parent);

            temporary = Files.createTempFile(
                    parent,
                    ".aulaflow-restore-",
                    ".db.tmp"
            );

            Files.copy(
                    backupPath,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING
            );

            validator.validate(temporary);

            replaceActiveDatabase(temporary);
            temporary = null;

            removeStaleSidecars();
        } catch (IOException | SecurityException exception) {
            throw new PersistenceException(
                    "No se ha podido restaurar la base SQLite.",
                    exception
            );
        } finally {
            deleteTemporary(temporary);
        }
    }

    private void replaceActiveDatabase(
            Path temporary
    ) throws IOException {
        try {
            Files.move(
                    temporary,
                    activeDatabasePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporary,
                    activeDatabasePath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void removeStaleSidecars() throws IOException {
        Files.deleteIfExists(
                Path.of(activeDatabasePath + "-wal")
        );
        Files.deleteIfExists(
                Path.of(activeDatabasePath + "-shm")
        );
        Files.deleteIfExists(
                Path.of(activeDatabasePath + "-journal")
        );
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // La restauración ya ha fallado y el temporal no debe
            // ocultar la causa principal. La guía operativa explica
            // cómo localizar y limpiar temporales si fuera necesario.
        }
    }
}
