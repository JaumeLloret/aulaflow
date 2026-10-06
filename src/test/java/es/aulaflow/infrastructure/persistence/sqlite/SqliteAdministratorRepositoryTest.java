package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.ProvisionInitialAdministrator;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.security.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAdministratorRepositoryTest {

    private static final String USERNAME =
            "teacher";

    private static final String PASSWORD =
            "fictional classroom password";

    @TempDir
    Path temporaryDirectory;

    @Test
    void provisionsAdministratorOnlyOnce() {
        SqliteAdministratorRepository repository =
                createRepository("provision.db");

        ProvisionInitialAdministrator provisioner =
                new ProvisionInitialAdministrator(
                        repository,
                        new Pbkdf2PasswordHasher()
                );

        assertTrue(
                provisioner.execute(
                        USERNAME,
                        PASSWORD.toCharArray()
                )
        );

        assertFalse(
                provisioner.execute(
                        "another-admin",
                        "another fictional password"
                                .toCharArray()
                )
        );

        StoredAdministrator stored =
                repository
                        .findByUsername(
                                new AdministratorUsername(
                                        USERNAME
                                )
                        )
                        .orElseThrow();

        assertEquals(
                1L,
                stored.administrator().id()
        );

        assertEquals(
                USERNAME,
                stored
                        .administrator()
                        .username()
                        .value()
        );
    }

    @Test
    void persistsAdministratorAcrossIndependentConnections() {
        String databaseName =
                "persistent-administrator.db";

        SqliteAdministratorRepository firstRepository =
                createRepository(databaseName);

        new ProvisionInitialAdministrator(
                firstRepository,
                new Pbkdf2PasswordHasher()
        ).execute(
                USERNAME,
                PASSWORD.toCharArray()
        );

        SqliteAdministratorRepository reopenedRepository =
                createRepository(databaseName);

        assertTrue(reopenedRepository.exists());

        assertTrue(
                reopenedRepository
                        .findByUsername(
                                new AdministratorUsername(
                                        USERNAME
                                )
                        )
                        .isPresent()
        );
    }

    @Test
    void doesNotStorePlainTextPassword() {
        SqliteAdministratorRepository repository =
                createRepository("protected-password.db");

        new ProvisionInitialAdministrator(
                repository,
                new Pbkdf2PasswordHasher()
        ).execute(
                USERNAME,
                PASSWORD.toCharArray()
        );

        StoredAdministrator stored =
                repository
                        .findByUsername(
                                new AdministratorUsername(
                                        USERNAME
                                )
                        )
                        .orElseThrow();

        assertFalse(
                stored
                        .passwordVerifier()
                        .encodedValue()
                        .contains(PASSWORD)
        );
    }

    private SqliteAdministratorRepository createRepository(
            String databaseName
    ) {
        Path databasePath =
                temporaryDirectory.resolve(
                        databaseName
                );

        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                databasePath.toString()
                        ),
                        temporaryDirectory
                );

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(config);

        new SqliteMigrator(
                connectionFactory
        ).migrate();

        return new SqliteAdministratorRepository(
                connectionFactory
        );
    }
}
