package es.aulaflow.application.csv;

import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardTitle;

import java.util.Objects;

public record PlannedCard(
        CardTitle title,
        CardDescription description,
        int position
) {

    public PlannedCard {
        Objects.requireNonNull(
                title,
                "El título no puede ser null."
        );

        Objects.requireNonNull(
                description,
                "La descripción no puede ser null."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "La posición de la tarjeta no puede "
                            + "ser negativa."
            );
        }
    }
}
