package es.aulaflow.infrastructure.auth;

import java.util.Map;
import java.util.Objects;

public final class SessionCookieConfig {

    public static final String SECURE_VARIABLE =
            "AULAFLOW_SESSION_COOKIE_SECURE";

    private final boolean secure;

    private SessionCookieConfig(boolean secure) {
        this.secure = secure;
    }

    public static SessionCookieConfig
    fromEnvironment() {
        return from(System.getenv());
    }

    static SessionCookieConfig from(
            Map<String, String> environment
    ) {
        Objects.requireNonNull(
                environment,
                "El entorno no puede ser null."
        );

        String configured =
                environment.get(SECURE_VARIABLE);

        if (
                configured == null
                        || configured.isBlank()
        ) {
            return new SessionCookieConfig(false);
        }

        if ("true".equalsIgnoreCase(configured.trim())) {
            return new SessionCookieConfig(true);
        }

        if ("false".equalsIgnoreCase(configured.trim())) {
            return new SessionCookieConfig(false);
        }

        throw new IllegalArgumentException(
                SECURE_VARIABLE
                        + " debe contener true o false."
        );
    }

    public boolean isSecure() {
        return secure;
    }
}
