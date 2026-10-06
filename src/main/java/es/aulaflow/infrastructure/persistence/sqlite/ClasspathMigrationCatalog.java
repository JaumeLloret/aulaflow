package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClasspathMigrationCatalog {

    static final String DEFAULT_MANIFEST_PATH =
            "/db/migration/migrations.txt";

    private static final Pattern MIGRATION_NAME_PATTERN =
            Pattern.compile(
                    "V(\\d{3})__([a-z0-9_]+)\\.sql"
            );

    private ClasspathMigrationCatalog() {
    }

    static List<SqliteMigration> loadDefault() {
        return load(DEFAULT_MANIFEST_PATH);
    }

    static List<SqliteMigration> load(
            String manifestPath
    ) {
        List<String> migrationNames =
                readManifest(manifestPath);

        if (migrationNames.isEmpty()) {
            throw new IllegalStateException(
                    "El manifiesto de migraciones está vacío."
            );
        }

        String resourceDirectory =
                resourceDirectoryOf(manifestPath);

        List<SqliteMigration> migrations =
                new ArrayList<>();

        Set<String> versions =
                new HashSet<>();

        int previousNumber = -1;

        for (String migrationName : migrationNames) {
            Matcher matcher =
                    MIGRATION_NAME_PATTERN.matcher(
                            migrationName
                    );

            if (!matcher.matches()) {
                throw new IllegalStateException(
                        "Nombre de migración no válido: "
                                + migrationName
                );
            }

            String version =
                    "V" + matcher.group(1);

            int versionNumber =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            if (!versions.add(version)) {
                throw new IllegalStateException(
                        "Versión de migración duplicada: "
                                + version
                );
            }

            if (versionNumber <= previousNumber) {
                throw new IllegalStateException(
                        "El manifiesto de migraciones "
                                + "no está ordenado."
                );
            }

            previousNumber = versionNumber;

            String resourcePath =
                    resourceDirectory
                            + migrationName;

            migrations.add(
                    new SqliteMigration(
                            version,
                            matcher.group(2),
                            resourcePath,
                            readResource(resourcePath)
                    )
            );
        }

        return List.copyOf(migrations);
    }

    private static List<String> readManifest(
            String manifestPath
    ) {
        try (
                InputStream inputStream =
                        openResource(manifestPath);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            return reader
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException exception) {
            throw new PersistenceException(
                    "No se ha podido leer el manifiesto "
                            + "de migraciones.",
                    exception
            );
        }
    }

    private static String readResource(
            String resourcePath
    ) {
        try (
                InputStream inputStream =
                        openResource(resourcePath)
        ) {
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new PersistenceException(
                    "No se ha podido leer una migración SQL.",
                    exception
            );
        }
    }

    private static InputStream openResource(
            String resourcePath
    ) {
        InputStream inputStream =
                ClasspathMigrationCatalog.class
                        .getResourceAsStream(
                                resourcePath
                        );

        if (inputStream == null) {
            throw new IllegalStateException(
                    "No se ha encontrado el recurso "
                            + "de migración: "
                            + resourcePath
            );
        }

        return inputStream;
    }

    private static String resourceDirectoryOf(
            String manifestPath
    ) {
        int lastSeparator =
                manifestPath.lastIndexOf('/');

        if (
                lastSeparator < 0
                        || lastSeparator
                        == manifestPath.length() - 1
        ) {
            throw new IllegalArgumentException(
                    "La ruta del manifiesto no es válida."
            );
        }

        return manifestPath.substring(
                0,
                lastSeparator + 1
        );
    }
}
