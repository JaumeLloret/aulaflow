package es.aulaflow.shared.config;

import java.util.Map;
import java.util.Objects;

public final class ApplicationConfig {

    private static final String HTTP_HOST_VARIABLE =
            "AULAFLOW_HTTP_HOST";

    private static final String HTTP_PORT_VARIABLE =
            "AULAFLOW_HTTP_PORT";

    private static final String ENVIRONMENT_VARIABLE =
            "AULAFLOW_ENV";

    private static final String DEFAULT_HTTP_HOST =
            "127.0.0.1";

    private static final String DEFAULT_HTTP_PORT =
            "8080";

    private static final String DEFAULT_ENVIRONMENT =
            "development";

    private final String httpHost;
    private final int httpPort;
    private final String environment;

    private ApplicationConfig(
            String httpHost,
            int httpPort,
            String environment
    ) {
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.environment = environment;
    }

    public static ApplicationConfig fromEnvironment() {
        return from(System.getenv());
    }

    static ApplicationConfig from(
            Map<String, String> environmentVariables
    ) {
        Objects.requireNonNull(
                environmentVariables,
                "El mapa de variables de entorno no puede ser null."
        );

        String httpHost = readOrDefault(
                environmentVariables,
                HTTP_HOST_VARIABLE,
                DEFAULT_HTTP_HOST
        );

        String rawHttpPort = readOrDefault(
                environmentVariables,
                HTTP_PORT_VARIABLE,
                DEFAULT_HTTP_PORT
        );

        String environment = readOrDefault(
                environmentVariables,
                ENVIRONMENT_VARIABLE,
                DEFAULT_ENVIRONMENT
        );

        int httpPort = parsePort(rawHttpPort);

        return new ApplicationConfig(
                httpHost,
                httpPort,
                environment
        );
    }

    private static String readOrDefault(
            Map<String, String> environmentVariables,
            String variableName,
            String defaultValue
    ) {
        String configuredValue =
                environmentVariables.get(variableName);

        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }

        return configuredValue.trim();
    }

    private static int parsePort(String rawPort) {
        final int port;

        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    HTTP_PORT_VARIABLE
                            + " debe contener un número entero "
                            + "entre 1 y 65535. Valor recibido: "
                            + rawPort,
                    exception
            );
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    HTTP_PORT_VARIABLE
                            + " debe estar entre 1 y 65535. "
                            + "Valor recibido: "
                            + port
            );
        }

        return port;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getEnvironment() {
        return environment;
    }
}
