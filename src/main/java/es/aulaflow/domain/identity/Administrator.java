package es.aulaflow.domain.identity;

import java.util.Objects;

public record Administrator(
        long id,
        AdministratorUsername username
) {

    public Administrator {
        if (id < 1) {
            throw new IllegalArgumentException(
                    "El identificador del administrador debe ser positivo."
            );
        }

        Objects.requireNonNull(
                username,
                "El nombre del administrador no puede ser null."
        );
    }
}
