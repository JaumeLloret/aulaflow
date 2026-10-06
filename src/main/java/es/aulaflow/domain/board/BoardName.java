package es.aulaflow.domain.board;

import java.util.Objects;

public record BoardName(String value) {

    public static final int MAXIMUM_CODE_POINTS = 100;

    public BoardName {
        Objects.requireNonNull(
                value,
                "El nombre del tablero no puede ser null."
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
                    "El nombre del tablero debe tener entre 1 y 100 caracteres."
            );
        }
    }
}
