package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.AdministratorRepository;
import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class SqliteAdministratorRepository
        implements AdministratorRepository {

    private static final long ADMINISTRATOR_ID = 1L;

    private static final String EXISTS_SQL = """
            SELECT 1
            FROM administrators
            LIMIT 1
            """;

    private static final String INSERT_SQL = """
            INSERT INTO administrators (
                id,
                username,
                password_verifier
            ) VALUES (?, ?, ?)
            """;

    private static final String FIND_BY_USERNAME_SQL = """
            SELECT
                id,
                username,
                password_verifier
            FROM administrators
            WHERE username = ?
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteAdministratorRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory =
                Objects.requireNonNull(
                        connectionFactory,
                        "La factoría de conexiones no puede ser null."
                );
    }

    @Override
    public boolean exists() {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                EXISTS_SQL
                        );
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Administrator create(
            AdministratorUsername username,
            PasswordVerifier passwordVerifier
    ) {
        Objects.requireNonNull(
                username,
                "El nombre no puede ser null."
        );

        Objects.requireNonNull(
                passwordVerifier,
                "El verificador no puede ser null."
        );

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_SQL
                        )
        ) {
            statement.setLong(
                    1,
                    ADMINISTRATOR_ID
            );

            statement.setString(
                    2,
                    username.value()
            );

            statement.setString(
                    3,
                    passwordVerifier.encodedValue()
            );

            statement.executeUpdate();

            return new Administrator(
                    ADMINISTRATOR_ID,
                    username
            );
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    @Override
    public Optional<StoredAdministrator>
    findByUsername(
            AdministratorUsername username
    ) {
        Objects.requireNonNull(
                username,
                "El nombre no puede ser null."
        );

        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_BY_USERNAME_SQL
                        )
        ) {
            statement.setString(
                    1,
                    username.value()
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Administrator administrator =
                        new Administrator(
                                resultSet.getLong("id"),
                                new AdministratorUsername(
                                        resultSet.getString(
                                                "username"
                                        )
                                )
                        );

                PasswordVerifier passwordVerifier =
                        new PasswordVerifier(
                                resultSet.getString(
                                        "password_verifier"
                                )
                        );

                return Optional.of(
                        new StoredAdministrator(
                                administrator,
                                passwordVerifier
                        )
                );
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static PersistenceException
    persistenceFailure(SQLException exception) {
        return new PersistenceException(
                "No se ha podido acceder "
                        + "al administrador persistido.",
                exception
        );
    }
}
