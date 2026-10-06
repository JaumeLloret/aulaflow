package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class SqliteConfig {

    public static final String DATABASE_PATH_VARIABLE =
            "AULAFLOW_DB_PATH";

    public static final String DEFAULT_DATABASE_PATH =
            "data/aulaflow.db";

    private final Path databasePath;

    private SqliteConfig(Path databasePath) {
        this.databasePath = databasePath;
    }

    public static SqliteConfig fromEnvironment() {
        return from(
                System.getenv(),
                Path.of("")
                        .toAbsolutePath()
        );
    }

    public static SqliteConfig from(
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

        String configuredPath =
                environmentVariables.get(
                        DATABASE_PATH_VARIABLE
                );

        if (
                configuredPath == null
                        || configuredPath.isBlank()
        ) {
            configuredPath = DEFAULT_DATABASE_PATH;
        } else {
            configuredPath = configuredPath.trim();
        }

        final Path path;

        try {
            path = Path.of(configuredPath);
        } catch (InvalidPathException exception) {
            throw new PersistenceException(
                    DATABASE_PATH_VARIABLE
                            + " no contiene una ruta válida.",
                    exception
            );
        }

        Path absoluteWorkingDirectory =
                workingDirectory
                        .toAbsolutePath()
                        .normalize();

        Path resolvedPath =
                path.isAbsolute()
                        ? path.normalize()
                        : absoluteWorkingDirectory
                                .resolve(path)
                                .normalize();

        if (resolvedPath.getFileName() == null) {
            throw new IllegalArgumentException(
                    DATABASE_PATH_VARIABLE
                            + " debe identificar un archivo."
            );
        }

        return new SqliteConfig(resolvedPath);
    }

    public Path getDatabasePath() {
        return databasePath;
    }
}
