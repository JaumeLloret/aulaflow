package es.aulaflow.application.board;

public final class ChecklistItemNotFoundException
        extends RuntimeException {

    public ChecklistItemNotFoundException() {
        super(
                "El elemento de checklist no existe o "
                        + "pertenece a otra tarjeta."
        );
    }
}
