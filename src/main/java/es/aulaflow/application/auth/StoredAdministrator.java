package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.Administrator;

import java.util.Objects;

public record StoredAdministrator(
        Administrator administrator,
        PasswordVerifier passwordVerifier
) {

    public StoredAdministrator {
        Objects.requireNonNull(
                administrator,
                "El administrador no puede ser null."
        );

        Objects.requireNonNull(
                passwordVerifier,
                "El verificador no puede ser null."
        );
    }
}
