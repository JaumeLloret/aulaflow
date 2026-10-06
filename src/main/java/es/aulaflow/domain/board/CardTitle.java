package es.aulaflow.domain.board;

import java.util.Objects;

public record CardTitle(String value) {

    public CardTitle {
        Objects.requireNonNull(
                value,
                "El título no puede ser null."
        );

        value = value.trim();

        int codePoints = value.codePointCount(
                0,
                value.length()
        );

        if (codePoints < 1 || codePoints > 160) {
            throw new IllegalArgumentException(
                    "El título debe tener entre 1 y 160 puntos de código Unicode."
            );
        }
    }
}
