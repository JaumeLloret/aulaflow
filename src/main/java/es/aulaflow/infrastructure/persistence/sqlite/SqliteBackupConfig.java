package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class SqliteBackupConfig {

    public static final String BACKUP_DIRECTORY_VARIABLE =
            "AULAFLOW_BACKUP_DIR";

    public static final String DEFAULT_BACKUP_DIRECTORY =
            "backups";

    private final Path backupDirectory;

    private SqliteBackupConfig(Path backupDirectory) {
        this.backupDirectory = backupDirectory;
    }

    public static SqliteBackupConfig fromEnvironment() {
        return from(
                System.getenv(),
                Path.of("").toAbsolutePath()
        );
    }

    public static SqliteBackupConfig from(
            Map<String, String> environmentVariables,
            Path workingDirectory
    ) {
        Objects.requireNonNull(
                environmentVariables,
                "El mapa de variables de entorno no puede ser null."
        );

        Objects.requireNonNull(
                workingDirectory,
                "El directorio de trabajo no puede ser null."
        );

        String configuredDirectory =
                environmentVariables.get(
                        BACKUP_DIRECTORY_VARIABLE
                );

        if (
                configuredDirectory == null
                        || configuredDirectory.isBlank()
        ) {
            configuredDirectory = DEFAULT_BACKUP_DIRECTORY;
        } else {
            configuredDirectory = configuredDirectory.trim();
        }

        final Path path;

        try {
            path = Path.of(configuredDirectory);
        } catch (InvalidPathException exception) {
            throw new PersistenceException(
                    BACKUP_DIRECTORY_VARIABLE
                            + " no contiene una ruta válida.",
                    exception
            );
        }

        Path absoluteWorkingDirectory =
                workingDirectory
                        .toAbsolutePath()
                        .normalize();

        Path resolvedDirectory =
                path.isAbsolute()
                        ? path.normalize()
                        : absoluteWorkingDirectory
                                .resolve(path)
                                .normalize();

        return new SqliteBackupConfig(resolvedDirectory);
    }

    public Path getBackupDirectory() {
        return backupDirectory;
    }

    public Path resolveBackupFile(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe indicarse el nombre del backup."
            );
        }

        String fileName = rawFileName.trim();
        final Path relativePath;

        try {
            relativePath = Path.of(fileName);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    "El nombre del backup no es válido.",
                    exception
            );
        }

        if (
                relativePath.isAbsolute()
                        || relativePath.getNameCount() != 1
                        || relativePath.getFileName() == null
                        || !fileName.endsWith(".db")
        ) {
            throw new IllegalArgumentException(
                    "El backup debe ser un único archivo .db "
                            + "dentro del directorio configurado."
            );
        }

        Path resolved =
                backupDirectory
                        .resolve(relativePath)
                        .normalize();

        if (!backupDirectory.equals(resolved.getParent())) {
            throw new IllegalArgumentException(
                    "El backup debe permanecer dentro del "
                            + "directorio configurado."
            );
        }

        return resolved;
    }
}
