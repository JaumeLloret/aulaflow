package es.aulaflow.application.csv;

import es.aulaflow.domain.board.BoardName;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Plan de importación inmutable, ya validado por completo, listo
 * para escribirse de forma atómica. No representa ningún dato hasta
 * que se confirma la importación.
 */
public record CsvImportPlan(
        BoardName boardName,
        List<PlannedColumn> columns
) {

    public CsvImportPlan {
        Objects.requireNonNull(
                boardName,
                "El nombre del tablero no puede ser null."
        );

        columns = List.copyOf(
                Objects.requireNonNull(
                        columns,
                        "Las columnas no pueden ser null."
                )
        );

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un plan de importación debe tener "
                            + "al menos una columna."
            );
        }

        List<PlannedColumn> ordered = columns.stream()
                .sorted(
                        Comparator.comparingInt(
                                PlannedColumn::position
                        )
                )
                .toList();

        for (int index = 0;
             index < ordered.size();
             index++) {
            if (ordered.get(index).position() != index) {
                throw new IllegalArgumentException(
                        "Las columnas del plan deben tener "
                                + "posiciones contiguas "
                                + "desde 0."
                );
            }
        }

        columns = ordered;
    }

    public int totalCards() {
        return columns.stream()
                .mapToInt(
                        column -> column.cards().size()
                )
                .sum();
    }
}
