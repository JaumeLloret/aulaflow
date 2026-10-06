package es.aulaflow.application.csv;

public final class InvalidImportTokenException
        extends RuntimeException {

    public InvalidImportTokenException() {
        super(
                "El token de importación no es válido, ha "
                        + "expirado, ya se ha usado o no "
                        + "pertenece a esta sesión."
        );
    }
}
