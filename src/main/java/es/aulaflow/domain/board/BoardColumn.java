package es.aulaflow.domain.board;

import java.time.Instant;
import java.util.Objects;

public record BoardColumn(
        ColumnId id,
        BoardId boardId,
        ColumnName name,
        int position,
        Instant createdAt
) {

    public BoardColumn {
        Objects.requireNonNull(
                id,
                "El identificador de columna no puede ser null."
        );

        Objects.requireNonNull(
                boardId,
                "El tablero de la columna no puede ser null."
        );

        Objects.requireNonNull(
                name,
                "El nombre de columna no puede ser null."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "La posición de columna no puede ser negativa."
            );
        }

        Objects.requireNonNull(
                createdAt,
                "La fecha de creación no puede ser null."
        );
    }
}
