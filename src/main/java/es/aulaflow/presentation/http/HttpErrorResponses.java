package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public final class HttpErrorResponses {

    private static final int NOT_FOUND =
            404;

    private static final int METHOD_NOT_ALLOWED =
            405;

    private static final int BAD_REQUEST =
            400;

    private static final int FORBIDDEN =
            403;

    private static final int UNAUTHORIZED =
            401;

    private static final int INTERNAL_SERVER_ERROR =
            500;

    private static final String CACHE_CONTROL_HEADER =
            "Cache-Control";

    private static final String NO_STORE =
            "no-store";

    private HttpErrorResponses() {
    }

    public static void notFound(
            HttpExchange exchange
    ) throws IOException {
        sendError(
                exchange,
                NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "El recurso solicitado no existe"
        );
    }

    public static void methodNotAllowed(
            HttpExchange exchange,
            String allowedMethod
    ) throws IOException {
        if (
                allowedMethod == null
                        || allowedMethod.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "El método permitido no puede estar vacío."
            );
        }

        exchange.getResponseHeaders().set(
                "Allow",
                allowedMethod.trim()
        );

        sendError(
                exchange,
                METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "El método HTTP no está permitido "
                        + "para este recurso"
        );
    }

    public static void badRequest(
            HttpExchange exchange
    ) throws IOException {
        sendError(
                exchange,
                BAD_REQUEST,
                "INVALID_REQUEST",
                "La petición no tiene un formato válido"
        );
    }

    public static void forbidden(
            HttpExchange exchange
    ) throws IOException {
        sendError(
                exchange,
                FORBIDDEN,
                "REQUEST_FORBIDDEN",
                "La petición no está permitida"
        );
    }

    public static void unauthorized(
            HttpExchange exchange
    ) throws IOException {
        sendError(
                exchange,
                UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Es necesario iniciar sesión"
        );
    }

    public static void internalServerError(
            HttpExchange exchange
    ) throws IOException {
        sendError(
                exchange,
                INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Se ha producido un error interno"
        );
    }

    private static void sendError(
            HttpExchange exchange,
            int statusCode,
            String code,
            String message
    ) throws IOException {
        String requestId =
                RequestIds.getOrCreate(exchange);

        exchange.getResponseHeaders().set(
                CACHE_CONTROL_HEADER,
                NO_STORE
        );

        String responseBody = """
                {
                  "code": %s,
                  "message": %s,
                  "requestId": %s
                }
                """.formatted(
                JsonText.quote(code),
                JsonText.quote(message),
                JsonText.quote(requestId)
        ).strip();

        HttpResponseWriter.sendJson(
                exchange,
                statusCode,
                responseBody
        );
    }
}
