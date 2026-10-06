package es.aulaflow.infrastructure.persistence.sqlite;

import java.util.Objects;

final class SqliteMigration {

    private final String version;
    private final String description;
    private final String resourcePath;
    private final String sql;

    SqliteMigration(
            String version,
            String description,
            String resourcePath,
            String sql
    ) {
        this.version = requireText(
                version,
                "La versión de la migración"
        );

        this.description = requireText(
                description,
                "La descripción de la migración"
        );

        this.resourcePath = requireText(
                resourcePath,
                "La ruta de la migración"
        );

        this.sql = requireText(
                sql,
                "El SQL de la migración"
        );
    }

    String getVersion() {
        return version;
    }

    String getDescription() {
        return description;
    }

    String getResourcePath() {
        return resourcePath;
    }

    String getSql() {
        return sql;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        Objects.requireNonNull(
                value,
                fieldName + " no puede ser null."
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " no puede estar vacío."
            );
        }

        return value.trim();
    }
}
