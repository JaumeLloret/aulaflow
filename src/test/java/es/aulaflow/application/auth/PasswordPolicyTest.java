package es.aulaflow.application.auth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsSpacesWithoutCompositionRules() {
        char[] password =
                "frase docente segura".toCharArray();

        char[] normalized =
                PasswordPolicy.normalizeAndValidate(
                        password
                );

        try {
            assertArrayEquals(
                    password,
                    normalized
            );
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(normalized, '\0');
        }
    }

    @Test
    void normalizesEquivalentUnicodeToNfc() {
        char[] decomposed =
                "contrasen\u0303a docente 2026"
                        .toCharArray();

        char[] normalized =
                PasswordPolicy.normalizeAndValidate(
                        decomposed
                );

        try {
            assertEquals(
                    "contraseña docente 2026",
                    new String(normalized)
            );
        } finally {
            Arrays.fill(decomposed, '\0');
            Arrays.fill(normalized, '\0');
        }
    }

    @Test
    void rejectsPasswordBelowMinimumLength() {
        char[] password =
                "muy corta".toCharArray();

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PasswordPolicy
                            .normalizeAndValidate(
                                    password
                            )
            );
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16Units() {
        char[] password =
                "🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐"
                        .toCharArray();

        char[] normalized =
                PasswordPolicy.normalizeAndValidate(
                        password
                );

        try {
            assertEquals(
                    15,
                    new String(normalized)
                            .codePointCount(
                                    0,
                                    normalized.length
                            )
            );
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(normalized, '\0');
        }
    }
}
