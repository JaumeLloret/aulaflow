package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.SessionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecureRandomSessionIdGeneratorTest {

    @Test
    void generatesIndependentBase64UrlIdentifiers() {
        SecureRandomSessionIdGenerator generator =
                new SecureRandomSessionIdGenerator();

        SessionId first = generator.generate();
        SessionId second = generator.generate();

        assertEquals(
                43,
                first.value().length()
        );

        assertNotEquals(
                first,
                second
        );
    }
}
