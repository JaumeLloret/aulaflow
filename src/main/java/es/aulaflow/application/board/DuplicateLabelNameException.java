package es.aulaflow.application.board;

public final class DuplicateLabelNameException
        extends RuntimeException {

    public DuplicateLabelNameException() {
        super(
                "Ya existe una etiqueta con ese nombre en este tablero."
        );
    }
}
