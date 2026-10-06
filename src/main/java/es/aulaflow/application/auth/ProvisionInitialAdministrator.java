package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.AdministratorUsername;

import java.util.Arrays;
import java.util.Objects;

public final class ProvisionInitialAdministrator {

    private final AdministratorRepository repository;
    private final PasswordHasher passwordHasher;

    public ProvisionInitialAdministrator(
            AdministratorRepository repository,
            PasswordHasher passwordHasher
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "El repositorio no puede ser null."
        );

        this.passwordHasher = Objects.requireNonNull(
                passwordHasher,
                "El derivador de contraseñas no puede ser null."
        );
    }

    public boolean execute(
            String username,
            char[] password
    ) {
        Objects.requireNonNull(
                password,
                "La contraseña no puede ser null."
        );

        if (repository.exists()) {
            Arrays.fill(password, '\0');
            return false;
        }

        char[] normalizedPassword = null;

        try {
            AdministratorUsername administratorUsername =
                    new AdministratorUsername(username);

            normalizedPassword =
                    PasswordPolicy.normalizeAndValidate(
                            password
                    );

            PasswordVerifier passwordVerifier =
                    passwordHasher.hash(
                            normalizedPassword
                    );

            repository.create(
                    administratorUsername,
                    passwordVerifier
            );

            return true;
        } finally {
            if (normalizedPassword != null) {
                Arrays.fill(
                        normalizedPassword,
                        '\0'
                );
            }

            Arrays.fill(password, '\0');
        }
    }
}
