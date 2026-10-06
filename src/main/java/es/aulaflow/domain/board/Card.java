package es.aulaflow.domain.board;

import java.time.Instant;
import java.util.Objects;

public record Card(
        CardId id,
        ColumnId columnId,
        CardTitle title,
        CardDescription description,
        int position,
        Instant createdAt,
        Instant updatedAt
) {

    public Card {
        Objects.requireNonNull(
                id,
                "El identificador de tarjeta no puede ser null."
        );

        Objects.requireNonNull(
                columnId,
                "La columna de la tarjeta no puede ser null."
        );

        Objects.requireNonNull(
                title,
                "El título de la tarjeta no puede ser null."
        );

        Objects.requireNonNull(
                description,
                "La descripción de la tarjeta no puede ser null."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "La posición de la tarjeta no puede ser negativa."
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
