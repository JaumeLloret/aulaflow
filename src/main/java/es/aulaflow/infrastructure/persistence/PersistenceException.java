package es.aulaflow.infrastructure.persistence;

public final class PersistenceException
        extends RuntimeException {

    public PersistenceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
