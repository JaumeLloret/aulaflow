package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class HtmlResponses {

    private static final String CONTENT_TYPE =
            "text/html; charset=utf-8";

    private HtmlResponses() {
    }

    public static void send(
            HttpExchange exchange,
            int status,
            String html
    ) throws IOException {
        Objects.requireNonNull(html);
        noStore(exchange);
        HttpResponseWriter.sendBytes(
                exchange,
                status,
                CONTENT_TYPE,
                html.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {
        Objects.requireNonNull(location);
        noStore(exchange);
        exchange.getResponseHeaders().set(
                "Location",
                location
        );
        HttpResponseWriter.sendBytes(
                exchange,
                303,
                "text/plain; charset=utf-8",
                new byte[0]
        );
    }

    private static void noStore(
            HttpExchange exchange
    ) {
        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );
    }
}
