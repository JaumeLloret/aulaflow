package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;

import java.util.Optional;

public interface AdministratorRepository {

    boolean exists();

    Administrator create(
            AdministratorUsername username,
            PasswordVerifier passwordVerifier
    );

    Optional<StoredAdministrator> findByUsername(
            AdministratorUsername username
    );
}
