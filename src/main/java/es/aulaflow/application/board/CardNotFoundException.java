package es.aulaflow.application.board;

public final class CardNotFoundException
        extends RuntimeException {

    public CardNotFoundException() {
        super(
                "La tarjeta no existe o pertenece a otro propietario."
        );
    }
}
