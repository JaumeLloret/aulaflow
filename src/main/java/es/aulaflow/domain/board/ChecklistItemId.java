package es.aulaflow.domain.board;

public record ChecklistItemId(long value) {

    public ChecklistItemId {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "El identificador del elemento debe ser positivo."
            );
        }
    }
}
