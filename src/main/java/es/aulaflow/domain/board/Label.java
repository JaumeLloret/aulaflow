package es.aulaflow.domain.board;

import java.time.Instant;
import java.util.Objects;

public record Label(
        LabelId id,
        BoardId boardId,
        LabelName name,
        LabelColor color,
        Instant createdAt,
        Instant updatedAt
) {

    public Label {
        Objects.requireNonNull(
                id,
                "El identificador de la etiqueta no puede ser null."
        );

        Objects.requireNonNull(
                boardId,
                "El tablero de la etiqueta no puede ser null."
        );

        Objects.requireNonNull(
                name,
                "El nombre de la etiqueta no puede ser null."
        );

        Objects.requireNonNull(
                color,
                "El color de la etiqueta no puede ser null."
        );

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
