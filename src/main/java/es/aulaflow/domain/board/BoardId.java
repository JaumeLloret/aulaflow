package es.aulaflow.domain.board;

public record BoardId(long value) {

    public BoardId {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "El identificador del tablero debe ser positivo."
            );
        }
    }
}
