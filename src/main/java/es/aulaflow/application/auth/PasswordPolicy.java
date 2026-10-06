package es.aulaflow.application.auth;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;

public final class PasswordPolicy {

    public static final int MINIMUM_CODE_POINTS = 15;
    public static final int MAXIMUM_CODE_POINTS = 128;

    private PasswordPolicy() {
    }

    public static char[] normalizeAndValidate(
            char[] password
    ) {
        Objects.requireNonNull(
                password,
                "La contraseña no puede ser null."
        );

        String normalized = Normalizer.normalize(
                new String(password),
                Normalizer.Form.NFC
        );

        int codePointCount =
                normalized.codePointCount(
                        0,
                        normalized.length()
                );

        if (
                codePointCount < MINIMUM_CODE_POINTS
                        || codePointCount
                        > MAXIMUM_CODE_POINTS
        ) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener entre "
                            + MINIMUM_CODE_POINTS
                            + " y "
                            + MAXIMUM_CODE_POINTS
                            + " caracteres."
            );
        }

        char[] normalizedPassword =
                normalized.toCharArray();

        if (!Arrays.equals(password, normalizedPassword)) {
            Arrays.fill(password, '\0');
        }

        return normalizedPassword;
    }
}
