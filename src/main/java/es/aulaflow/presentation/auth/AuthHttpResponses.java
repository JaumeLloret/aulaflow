package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import es.aulaflow.presentation.http.HttpResponseWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class AuthHttpResponses {

    private static final String CACHE_CONTROL_HEADER =
            "Cache-Control";

    private static final String NO_STORE =
            "no-store";

    private static final String HTML_CONTENT_TYPE =
            "text/html; charset=utf-8";

    private static final String TEXT_CONTENT_TYPE =
            "text/plain; charset=utf-8";

    private AuthHttpResponses() {
    }

    static void sendHtml(
            HttpExchange exchange,
            int status,
            String html
    ) throws IOException {
        Objects.requireNonNull(
                html,
                "El HTML no puede ser null."
        );

        noStore(exchange);

        HttpResponseWriter.sendBytes(
                exchange,
                status,
                HTML_CONTENT_TYPE,
                html.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    static void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {
        Objects.requireNonNull(
                location,
                "La ubicación no puede ser null."
        );

        noStore(exchange);

        exchange.getResponseHeaders().set(
                "Location",
                location
        );

        HttpResponseWriter.sendBytes(
                exchange,
                303,
                TEXT_CONTENT_TYPE,
                new byte[0]
        );
    }

    private static void noStore(
            HttpExchange exchange
    ) {
        exchange.getResponseHeaders().set(
                CACHE_CONTROL_HEADER,
                NO_STORE
        );
    }
}
