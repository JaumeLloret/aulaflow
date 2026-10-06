package es.aulaflow.infrastructure.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieConfigTest {

    @Test
    void usesInsecureCookieForExplicitLocalHttpDefault() {
        assertFalse(
                SessionCookieConfig
                        .from(Map.of())
                        .isSecure()
        );
    }

    @Test
    void enablesSecureAttributeForHttpsDeployment() {
        assertTrue(
                SessionCookieConfig
                        .from(
                                Map.of(
                                        "AULAFLOW_SESSION_COOKIE_SECURE",
                                        "true"
                                )
                        )
                        .isSecure()
        );
    }

    @Test
    void rejectsUnknownBooleanValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionCookieConfig.from(
                        Map.of(
                                "AULAFLOW_SESSION_COOKIE_SECURE",
                                "yes"
                        )
                )
        );
    }
}
