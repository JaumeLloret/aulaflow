package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.CsrfToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecureRandomCsrfTokenGeneratorTest {

    @Test
    void generatesIndependentBase64UrlTokens() {
        SecureRandomCsrfTokenGenerator generator =
                new SecureRandomCsrfTokenGenerator();

        CsrfToken first = generator.generate();
        CsrfToken second = generator.generate();

        assertEquals(43, first.value().length());
        assertEquals(43, second.value().length());
        assertNotEquals(first, second);
    }
}
