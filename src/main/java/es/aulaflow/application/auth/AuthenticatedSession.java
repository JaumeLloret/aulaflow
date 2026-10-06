package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.Administrator;

import java.time.Instant;
import java.util.Objects;

public record AuthenticatedSession(
        SessionId id,
        Administrator administrator,
        CsrfToken csrfToken,
        Instant createdAt,
        Instant expiresAt
) {

    public AuthenticatedSession {
        Objects.requireNonNull(
                id,
                "El identificador de sesión no puede ser null."
        );

        Objects.requireNonNull(
                administrator,
                "El administrador no puede ser null."
        );

        Objects.requireNonNull(
                csrfToken,
                "El token CSRF no puede ser null."
        );

        Objects.requireNonNull(
                createdAt,
                "El instante de creación no puede ser null."
        );

        Objects.requireNonNull(
                expiresAt,
                "El instante de expiración no puede ser null."
        );

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "La expiración debe ser posterior a la creación."
            );
        }
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(
                instant,
                "El instante no puede ser null."
        );

        return !instant.isBefore(expiresAt);
    }
}
