package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.PasswordVerifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pbkdf2PasswordHasherTest {

    private static final String TEST_PASSWORD =
            "fictional classroom password";

    private final Pbkdf2PasswordHasher passwordHasher =
            new Pbkdf2PasswordHasher();

    @Test
    void hashesAndVerifiesCorrectPassword() {
        char[] password =
                TEST_PASSWORD.toCharArray();

        PasswordVerifier verifier;

        try {
            verifier = passwordHasher.hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }

        char[] candidate =
                TEST_PASSWORD.toCharArray();

        try {
            assertTrue(
                    passwordHasher.verify(
                            candidate,
                            verifier
                    )
            );
        } finally {
            Arrays.fill(candidate, '\0');
        }
    }

    @Test
    void rejectsIncorrectPassword() {
        PasswordVerifier verifier =
                passwordHasher.hash(
                        TEST_PASSWORD.toCharArray()
                );

        char[] candidate =
                "different fictional password"
                        .toCharArray();

        try {
            assertFalse(
                    passwordHasher.verify(
                            candidate,
                            verifier
                    )
            );
        } finally {
            Arrays.fill(candidate, '\0');
        }
    }

    @Test
    void usesIndependentSaltForSamePassword() {
        char[] firstPassword =
                TEST_PASSWORD.toCharArray();

        char[] secondPassword =
                TEST_PASSWORD.toCharArray();

        try {
            PasswordVerifier first =
                    passwordHasher.hash(firstPassword);

            PasswordVerifier second =
                    passwordHasher.hash(secondPassword);

            assertNotEquals(
                    first.encodedValue(),
                    second.encodedValue()
            );
        } finally {
            Arrays.fill(firstPassword, '\0');
            Arrays.fill(secondPassword, '\0');
        }
    }

    @Test
    void storesVersionedAlgorithmAndParameters() {
        char[] password =
                TEST_PASSWORD.toCharArray();

        try {
            PasswordVerifier verifier =
                    passwordHasher.hash(password);

            assertTrue(
                    verifier
                            .encodedValue()
                            .startsWith(
                                    "pbkdf2-sha256"
                                            + "$v1"
                                            + "$600000$"
                            )
            );

            assertFalse(
                    verifier
                            .encodedValue()
                            .contains(TEST_PASSWORD)
            );
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test
    void rejectsUnknownVerifierFormat() {
        char[] password =
                TEST_PASSWORD.toCharArray();

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> passwordHasher.verify(
                            password,
                            new PasswordVerifier(
                                    "unknown$v1$1$salt$hash"
                            )
                    )
            );
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test
    void createsDummyVerifierWithProductionFormat() {
        PasswordVerifier dummy =
                passwordHasher.createDummyVerifier();

        assertTrue(
                dummy
                        .encodedValue()
                        .startsWith(
                                "pbkdf2-sha256"
                                        + "$v1"
                                        + "$600000$"
                        )
        );
    }
}
