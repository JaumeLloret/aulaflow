package es.aulaflow.domain.board;

import java.util.Objects;

public record ColumnName(String value) {

    public static final int MAXIMUM_CODE_POINTS = 80;

    public ColumnName {
        Objects.requireNonNull(
                value,
                "El nombre de la columna no puede ser null."
        );

        value = value.trim();

        int length = value.codePointCount(
                0,
                value.length()
        );

        if (
                length < 1
                        || length > MAXIMUM_CODE_POINTS
        ) {
            throw new IllegalArgumentException(
                    "El nombre de la columna debe tener entre 1 y 80 caracteres."
            );
        }
    }
}
