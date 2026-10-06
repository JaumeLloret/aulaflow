package es.aulaflow.presentation.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpResponseWriter;
import es.aulaflow.presentation.http.JsonText;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

public final class HealthHandler implements HttpHandler {

    public static final String PATH =
            "/api/v1/health";

    private static final String GET_METHOD =
            "GET";

    private static final int OK =
            200;

    private static final String CACHE_CONTROL_HEADER =
            "Cache-Control";

    private static final String NO_STORE =
            "no-store";

    private final String applicationVersion;
    private final String environment;
    private final Clock clock;

    public HealthHandler(
            String applicationVersion,
            String environment
    ) {
        this(
                applicationVersion,
                environment,
                Clock.systemUTC()
        );
    }

    public HealthHandler(
            String applicationVersion,
            String environment,
            Clock clock
    ) {
        this.applicationVersion = requireText(
                applicationVersion,
                "La versión de la aplicación"
        );

        this.environment = requireText(
                environment,
                "El entorno de la aplicación"
        );

        this.clock = Objects.requireNonNull(
                clock,
                "El reloj no puede ser null."
        );
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        Objects.requireNonNull(
                exchange,
                "El intercambio HTTP no puede ser null."
        );

        String requestedPath =
                exchange.getRequestURI().getPath();

        if (!PATH.equals(requestedPath)) {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        String requestMethod =
                exchange.getRequestMethod();

        if (!GET_METHOD.equals(requestMethod)) {
            HttpErrorResponses.methodNotAllowed(
                    exchange,
                    GET_METHOD
            );

            return;
        }

        exchange.getResponseHeaders().set(
                CACHE_CONTROL_HEADER,
                NO_STORE
        );

        HttpResponseWriter.sendJson(
                exchange,
                OK,
                createResponseBody()
        );
    }

    private String createResponseBody() {
        return """
                {
                  "status": "UP",
                  "version": %s,
                  "environment": %s,
                  "time": %s
                }
                """.formatted(
                JsonText.quote(applicationVersion),
                JsonText.quote(environment),
                JsonText.quote(
                        clock.instant().toString()
                )
        ).strip();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " no puede estar vacío."
            );
        }

        return value.trim();
    }
}
