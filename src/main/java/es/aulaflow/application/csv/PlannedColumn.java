package es.aulaflow.application.csv;

import es.aulaflow.domain.board.ColumnName;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PlannedColumn(
        ColumnName name,
        int position,
        List<PlannedCard> cards
) {

    public PlannedColumn {
        Objects.requireNonNull(
                name,
                "El nombre de columna no puede ser null."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "La posición de columna no puede "
                            + "ser negativa."
            );
        }

        cards = List.copyOf(
                Objects.requireNonNull(
                        cards,
                        "Las tarjetas no pueden ser null."
                )
        );

        List<PlannedCard> ordered = cards.stream()
                .sorted(
                        Comparator.comparingInt(
                                PlannedCard::position
                        )
                )
                .toList();

        for (int index = 0;
             index < ordered.size();
             index++) {
            if (ordered.get(index).position() != index) {
                throw new IllegalArgumentException(
                        "Las tarjetas de una columna deben "
                                + "tener posiciones contiguas "
                                + "desde 0."
                );
            }
        }

        cards = ordered;
    }
}
