package es.aulaflow.domain.board;

import java.util.Objects;

public record CardDescription(String value) {

    public CardDescription {
        Objects.requireNonNull(
                value,
                "La descripción no puede ser null."
        );

        value = value
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        int codePoints = value.codePointCount(
                0,
                value.length()
        );

        if (codePoints > 4000) {
            throw new IllegalArgumentException(
                    "La descripción no puede superar 4000 puntos de código Unicode."
            );
        }
    }
}
