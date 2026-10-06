package es.aulaflow.presentation.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationPageContractTest {

    @Test
    void loginPageUsesSemanticAccessibleForm()
            throws IOException {
        String html =
                load("/web/login.html");

        assertTrue(html.contains("<main"));
        assertTrue(html.contains("<form"));
        assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("action=\"/login\""));
        assertTrue(html.contains("for=\"username\""));
        assertTrue(html.contains("id=\"username\""));
        assertTrue(html.contains("autocomplete=\"username\""));
        assertTrue(html.contains("for=\"password\""));
        assertTrue(html.contains("id=\"password\""));
        assertTrue(
                html.contains(
                        "autocomplete=\"current-password\""
                )
        );
        assertTrue(html.contains("type=\"submit\""));
        assertTrue(html.contains("role=\"alert\""));
        assertTrue(html.contains("autofocus"));
        assertFalse(html.contains("value=\"password"));
    }

    @Test
    void accountPageCanLogoutWithoutJavaScript()
            throws IOException {
        String html =
                load("/web/account.html");

        assertTrue(html.contains("<main"));
        assertTrue(html.contains("<form"));
        assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("action=\"/logout\""));
        assertTrue(html.contains("type=\"submit\""));
    }

    private static String load(
            String resourcePath
    ) throws IOException {
        try (
                InputStream inputStream =
                        AuthenticationPageContractTest
                                .class
                                .getResourceAsStream(
                                        resourcePath
                                )
        ) {
            assertNotNull(inputStream);

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
