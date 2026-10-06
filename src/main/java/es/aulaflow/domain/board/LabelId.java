package es.aulaflow.domain.board;

public record LabelId(long value) {

    public LabelId {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "El identificador de la etiqueta debe ser positivo."
            );
        }
    }
}
