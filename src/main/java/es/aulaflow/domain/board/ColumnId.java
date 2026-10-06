package es.aulaflow.domain.board;

public record ColumnId(long value) {

    public ColumnId {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "El identificador de la columna debe ser positivo."
            );
        }
    }
}
