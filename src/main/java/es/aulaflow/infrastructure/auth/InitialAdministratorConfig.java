package es.aulaflow.infrastructure.auth;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class InitialAdministratorConfig
        implements AutoCloseable {

    public static final String USERNAME_VARIABLE =
            "AULAFLOW_ADMIN_USERNAME";

    public static final String PASSWORD_VARIABLE =
            "AULAFLOW_ADMIN_PASSWORD";

    private final String username;
    private final char[] password;

    private InitialAdministratorConfig(
            String username,
            char[] password
    ) {
        this.username = username;
        this.password = password;
    }

    public static InitialAdministratorConfig
    fromEnvironment() {
        return from(System.getenv());
    }

    static InitialAdministratorConfig from(
            Map<String, String> environment
    ) {
        Objects.requireNonNull(
                environment,
                "El entorno no puede ser null."
        );

        String username =
                environment.get(USERNAME_VARIABLE);

        String password =
                environment.get(PASSWORD_VARIABLE);

        if (
                username == null
                        || username.isBlank()
                        || password == null
                        || password.isEmpty()
        ) {
            throw new IllegalStateException(
                    "Una base sin administrador requiere "
                            + USERNAME_VARIABLE
                            + " y "
                            + PASSWORD_VARIABLE
                            + "."
            );
        }

        return new InitialAdministratorConfig(
                username,
                password.toCharArray()
        );
    }

    public String username() {
        return username;
    }

    public char[] copyPassword() {
        return password.clone();
    }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }
}
