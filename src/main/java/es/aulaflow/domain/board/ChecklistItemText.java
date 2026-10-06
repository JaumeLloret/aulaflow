package es.aulaflow.domain.board;

import java.util.Objects;

public record ChecklistItemText(String value) {

    public static final int MAXIMUM_CODE_POINTS = 280;

    public ChecklistItemText {
        Objects.requireNonNull(
                value,
                "El texto del elemento no puede ser null."
        );

        value = value.trim();

        int codePoints = value.codePointCount(
                0,
                value.length()
        );

        if (codePoints < 1 || codePoints > MAXIMUM_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "El texto del elemento debe tener entre 1 y "
                            + MAXIMUM_CODE_POINTS
                            + " puntos de código Unicode."
            );
        }
    }
}
