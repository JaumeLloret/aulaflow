package es.aulaflow.presentation.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class AuthenticationPages {

    private static final String LOGIN_PAGE =
            "/web/login.html";

    private static final String ACCOUNT_PAGE =
            "/web/account.html";

    private static final String HIDDEN_ERROR =
            "data-auth-error hidden";

    private static final String VISIBLE_ERROR =
            "data-auth-error";

    private AuthenticationPages() {
    }

    static String login(boolean showError) {
        String page = read(LOGIN_PAGE);

        if (!showError) {
            return page;
        }

        return page.replace(
                HIDDEN_ERROR,
                VISIBLE_ERROR
        );
    }

    static String account(String csrfToken) {
        return read(ACCOUNT_PAGE).replace(
                "{{CSRF_TOKEN}}",
                csrfToken
        );
    }

    private static String read(String resourcePath) {
        try (
                InputStream inputStream =
                        AuthenticationPages.class
                                .getResourceAsStream(
                                        resourcePath
                                )
        ) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "No se ha encontrado una página "
                                + "de autenticación."
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se ha podido leer una página "
                            + "de autenticación.",
                    exception
            );
        }
    }
}
