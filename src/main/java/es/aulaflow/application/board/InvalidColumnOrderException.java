package es.aulaflow.application.board;

public final class InvalidColumnOrderException
        extends RuntimeException {

    public InvalidColumnOrderException() {
        super("El orden de columnas no es una permutación válida.");
    }
}
