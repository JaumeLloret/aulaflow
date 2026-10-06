package es.aulaflow.domain.board;

public record CardId(long value) {

    public CardId {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "El identificador de tarjeta debe ser positivo."
            );
        }
    }
}
