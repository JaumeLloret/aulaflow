package es.aulaflow.presentation.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class HttpResponseWriter {

    private static final String CONTENT_TYPE_HEADER =
            "Content-Type";

    private static final String JSON_CONTENT_TYPE =
            "application/json; charset=utf-8";

    private static final String CONTENT_TYPE_OPTIONS_HEADER =
            "X-Content-Type-Options";

    private static final String NO_SNIFF =
            "nosniff";

    private HttpResponseWriter() {
    }

    public static void sendJson(
            HttpExchange exchange,
            int statusCode,
            String jsonBody
    ) throws IOException {
        Objects.requireNonNull(
                jsonBody,
                "El cuerpo JSON no puede ser null."
        );

        if (jsonBody.isBlank()) {
            throw new IllegalArgumentException(
                    "El cuerpo JSON no puede estar vacío."
            );
        }

        sendBytes(
                exchange,
                statusCode,
                JSON_CONTENT_TYPE,
                jsonBody.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static void sendBytes(
            HttpExchange exchange,
            int statusCode,
            String contentType,
            byte[] responseBytes
    ) throws IOException {
        Objects.requireNonNull(
                exchange,
                "El intercambio HTTP no puede ser null."
        );

        Objects.requireNonNull(
                contentType,
                "El tipo de contenido no puede ser null."
        );

        Objects.requireNonNull(
                responseBytes,
                "El cuerpo de la respuesta no puede ser null."
        );

        if (contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo de contenido no puede estar vacío."
            );
        }

        validateStatusCode(statusCode);

        Headers responseHeaders =
                exchange.getResponseHeaders();

        responseHeaders.set(
                CONTENT_TYPE_HEADER,
                contentType.trim()
        );

        responseHeaders.set(
                CONTENT_TYPE_OPTIONS_HEADER,
                NO_SNIFF
        );

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );

        try (
                OutputStream responseBody =
                        exchange.getResponseBody()
        ) {
            responseBody.write(responseBytes);
        }
    }

    public static void sendNoContent(
            HttpExchange exchange
    ) throws IOException {
        Objects.requireNonNull(
                exchange,
                "El intercambio HTTP no puede ser null."
        );
        exchange.getResponseHeaders().set(
                CONTENT_TYPE_OPTIONS_HEADER,
                NO_SNIFF
        );
        exchange.sendResponseHeaders(204, -1);
    }

    private static void validateStatusCode(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                    "Código de estado HTTP inválido: "
                            + statusCode
            );
        }
    }
}
