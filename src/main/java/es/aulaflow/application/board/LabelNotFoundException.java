package es.aulaflow.application.board;

public final class LabelNotFoundException
        extends RuntimeException {

    public LabelNotFoundException() {
        super(
                "La etiqueta no existe o pertenece a otro tablero."
        );
    }
}
