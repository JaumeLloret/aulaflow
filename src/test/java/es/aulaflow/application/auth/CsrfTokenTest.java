package es.aulaflow.application.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenTest {

    @Test
    void acceptsExpectedBase64UrlShape() {
        CsrfToken token =
                new CsrfToken("A".repeat(43));

        assertTrue(token.matches("A".repeat(43)));
        assertFalse(token.matches("B".repeat(43)));
        assertFalse(token.matches(null));
    }

    @Test
    void rejectsValuesOutsideTheContract() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsrfToken("short")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsrfToken("!".repeat(43))
        );
    }
}
