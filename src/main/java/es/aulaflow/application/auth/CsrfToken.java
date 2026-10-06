package es.aulaflow.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.regex.Pattern;

public record CsrfToken(String value) {

    private static final Pattern ENCODED_TOKEN =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    public CsrfToken {
        Objects.requireNonNull(
                value,
                "El token CSRF no puede ser null."
        );

        if (!ENCODED_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El token CSRF no tiene un formato válido."
            );
        }
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }

        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(
                        StandardCharsets.US_ASCII
                )
        );
    }

    @Override
    public String toString() {
        return "CsrfToken[protected]";
    }
}
