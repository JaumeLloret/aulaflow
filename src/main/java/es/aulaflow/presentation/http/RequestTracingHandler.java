package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

public final class RequestTracingHandler
        implements HttpHandler {

    private static final System.Logger LOGGER =
            System.getLogger(
                    RequestTracingHandler.class.getName()
            );

    private final HttpHandler delegate;

    public RequestTracingHandler(
            HttpHandler delegate
    ) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "El manejador delegado no puede ser null."
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

        String requestId =
                RequestIds.getOrCreate(exchange);

        String requestMethod =
                exchange.getRequestMethod();

        String requestPath =
                exchange.getRequestURI().getPath();

        long startNanos =
                System.nanoTime();

        try {
            delegate.handle(exchange);
        } catch (IOException exception) {
            LOGGER.log(
                    WARNING,
                    "Error de entrada/salida durante "
                            + requestMethod
                            + " "
                            + requestPath
                            + ". requestId="
                            + requestId,
                    exception
            );

            throw exception;
        } finally {
            logCompletedRequest(
                    exchange,
                    requestId,
                    requestMethod,
                    requestPath,
                    startNanos
            );
        }
    }

    private static void logCompletedRequest(
            HttpExchange exchange,
            String requestId,
            String requestMethod,
            String requestPath,
            long startNanos
    ) {
        long elapsedNanos =
                System.nanoTime() - startNanos;

        long elapsedMillis =
                TimeUnit.NANOSECONDS.toMillis(
                        elapsedNanos
                );

        int responseCode =
                exchange.getResponseCode();

        String status =
                responseCode == -1
                        ? "UNSENT"
                        : Integer.toString(responseCode);

        LOGGER.log(
                INFO,
                () -> "HTTP "
                        + requestMethod
                        + " "
                        + requestPath
                        + " -> "
                        + status
                        + " in "
                        + elapsedMillis
                        + " ms"
                        + " requestId="
                        + requestId
        );
    }
}
