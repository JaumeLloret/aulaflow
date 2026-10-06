package es.aulaflow.domain.board;

import java.util.List;

/**
 * Progreso derivado de un checklist. No se almacena en SQLite: se
 * calcula siempre a partir de los elementos actuales para evitar un
 * porcentaje redundante que pudiera desincronizarse.
 */
public record ChecklistProgress(
        int totalItems,
        int completedItems
) {

    public ChecklistProgress {
        if (totalItems < 0) {
            throw new IllegalArgumentException(
                    "El total de elementos no puede ser negativo."
            );
        }

        if (completedItems < 0 || completedItems > totalItems) {
            throw new IllegalArgumentException(
                    "Los elementos completados no pueden ser "
                            + "negativos ni superar el total."
            );
        }
    }

    public int percentage() {
        return totalItems == 0
                ? 0
                : (int) Math.round(
                        (completedItems * 100.0) / totalItems
                );
    }

    public static ChecklistProgress of(
            List<ChecklistItem> items
    ) {
        int total = items.size();

        int completed = (int) items.stream()
                .filter(ChecklistItem::completed)
                .count();

        return new ChecklistProgress(total, completed);
    }
}
