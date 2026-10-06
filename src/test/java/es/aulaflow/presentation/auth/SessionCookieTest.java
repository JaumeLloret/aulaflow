package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.Headers;
import es.aulaflow.application.auth.SessionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieTest {

    private static final SessionId SESSION_ID =
            new SessionId("A".repeat(43));

    @Test
    void writesLocalCookieWithRequiredAttributes() {
        Headers responseHeaders = new Headers();

        new SessionCookie(false).set(
                responseHeaders,
                SESSION_ID
        );

        assertEquals(
                "AULAFLOW_SESSION="
                        + SESSION_ID.value()
                        + "; Path=/; HttpOnly; SameSite=Strict",
                responseHeaders.getFirst("Set-Cookie")
        );
    }

    @Test
    void addsSecureOnlyWhenConfigured() {
        Headers responseHeaders = new Headers();

        new SessionCookie(true).set(
                responseHeaders,
                SESSION_ID
        );

        assertTrue(
                responseHeaders
                        .getFirst("Set-Cookie")
                        .endsWith("; Secure")
        );
    }

    @Test
    void clearCookieUsesSameScopeAndImmediateExpiry() {
        Headers responseHeaders = new Headers();

        new SessionCookie(true).clear(
                responseHeaders
        );

        assertEquals(
                "AULAFLOW_SESSION=; Path=/; HttpOnly; "
                        + "SameSite=Strict; Max-Age=0; Secure",
                responseHeaders.getFirst("Set-Cookie")
        );
    }

    @Test
    void readsSingleSessionCookieAmongOtherCookies() {
        Headers requestHeaders = new Headers();

        requestHeaders.add(
                "Cookie",
                "theme=ca; AULAFLOW_SESSION="
                        + SESSION_ID.value()
        );

        assertEquals(
                SESSION_ID.value(),
                new SessionCookie(false)
                        .read(requestHeaders)
                        .orElseThrow()
        );
    }

    @Test
    void rejectsAmbiguousDuplicateSessionCookies() {
        Headers requestHeaders = new Headers();

        requestHeaders.add(
                "Cookie",
                "AULAFLOW_SESSION="
                        + SESSION_ID.value()
                        + "; AULAFLOW_SESSION="
                        + "B".repeat(43)
        );

        assertTrue(
                new SessionCookie(false)
                        .read(requestHeaders)
                        .isEmpty()
        );
    }

    @Test
    void doesNotExposeIdentifierThroughToString() {
        assertFalse(
                SESSION_ID
                        .toString()
                        .contains(
                                SESSION_ID.value()
                        )
        );
    }
}
