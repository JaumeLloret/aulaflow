package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.Headers;
import es.aulaflow.application.auth.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SessionCookie {

    public static final String NAME =
            "AULAFLOW_SESSION";

    private static final String COOKIE_HEADER =
            "Cookie";

    private static final String SET_COOKIE_HEADER =
            "Set-Cookie";

    private final boolean secure;

    public SessionCookie(boolean secure) {
        this.secure = secure;
    }

    public Optional<String> read(Headers requestHeaders) {
        Objects.requireNonNull(
                requestHeaders,
                "Las cabeceras no pueden ser null."
        );

        List<String> matchingValues =
                new ArrayList<>();

        for (
                String header
                : requestHeaders.getOrDefault(
                        COOKIE_HEADER,
                        List.of()
                )
        ) {
            for (String field : header.split(";")) {
                String trimmed = field.trim();
                int separator = trimmed.indexOf('=');

                if (separator < 1) {
                    continue;
                }

                String name =
                        trimmed.substring(
                                0,
                                separator
                        );

                if (!NAME.equals(name)) {
                    continue;
                }

                matchingValues.add(
                        trimmed.substring(
                                separator + 1
                        )
                );
            }
        }

        if (matchingValues.size() != 1) {
            return Optional.empty();
        }

        String value = matchingValues.getFirst();

        if (value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public void set(
            Headers responseHeaders,
            SessionId sessionId
    ) {
        Objects.requireNonNull(
                sessionId,
                "El identificador no puede ser null."
        );

        responseHeaders.set(
                SET_COOKIE_HEADER,
                headerValue(
                        sessionId.value(),
                        false
                )
        );
    }

    public void clear(Headers responseHeaders) {
        responseHeaders.set(
                SET_COOKIE_HEADER,
                headerValue("", true)
        );
    }

    private String headerValue(
            String value,
            boolean clear
    ) {
        StringBuilder header =
                new StringBuilder()
                        .append(NAME)
                        .append('=')
                        .append(value)
                        .append("; Path=/")
                        .append("; HttpOnly")
                        .append("; SameSite=Strict");

        if (clear) {
            header.append("; Max-Age=0");
        }

        if (secure) {
            header.append("; Secure");
        }

        return header.toString();
    }
}
