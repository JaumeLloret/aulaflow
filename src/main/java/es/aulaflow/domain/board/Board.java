package es.aulaflow.domain.board;

import java.time.Instant;
import java.util.Objects;

public record Board(
        BoardId id,
        long ownerId,
        BoardName name,
        Instant createdAt
) {

    public Board {
        Objects.requireNonNull(
                id,
                "El identificador del tablero no puede ser null."
        );

        if (ownerId < 1) {
            throw new IllegalArgumentException(
                    "El propietario del tablero debe ser positivo."
            );
        }

        Objects.requireNonNull(
                name,
                "El nombre del tablero no puede ser null."
        );

        Objects.requireNonNull(
                createdAt,
                "La fecha de creación no puede ser null."
        );
    }
}
