package es.aulaflow.application.auth;

import java.util.Objects;
import java.util.regex.Pattern;

public record SessionId(String value) {

    private static final Pattern VALID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    public SessionId {
        Objects.requireNonNull(
                value,
                "El identificador de sesión no puede ser null."
        );

        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El identificador de sesión no es válido."
            );
        }
    }

    @Override
    public String toString() {
        return "SessionId[protected]";
    }
}
