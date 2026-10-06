package es.aulaflow.domain.board;

import java.time.Instant;
import java.util.Objects;

public record ChecklistItem(
        ChecklistItemId id,
        CardId cardId,
        ChecklistItemText text,
        boolean completed,
        int position,
        Instant createdAt,
        Instant updatedAt
) {

    public ChecklistItem {
        Objects.requireNonNull(
                id,
                "El identificador del elemento no puede ser null."
        );

        Objects.requireNonNull(
                cardId,
                "La tarjeta del elemento no puede ser null."
        );

        Objects.requireNonNull(
                text,
                "El texto del elemento no puede ser null."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "La posición del elemento no puede ser negativa."
            );
        }

        Objects.requireNonNull(
                createdAt,
                "La fecha de creación no puede ser null."
        );

        Objects.requireNonNull(
                updatedAt,
                "La fecha de actualización no puede ser null."
        );

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "La fecha de actualización no puede ser anterior a la de creación."
            );
        }
    }
}
