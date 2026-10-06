package es.aulaflow.application.board;

public final class BoardNotFoundException
        extends RuntimeException {

    public BoardNotFoundException() {
        super("El tablero solicitado no existe.");
    }
}
