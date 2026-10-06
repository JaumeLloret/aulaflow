package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;

import static java.lang.System.Logger.Level.ERROR;

public final class ExceptionHandlingHandler
        implements HttpHandler {

    private static final System.Logger LOGGER =
            System.getLogger(
                    ExceptionHandlingHandler.class.getName()
            );

    private final HttpHandler delegate;

    public ExceptionHandlingHandler(
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
        try {
            delegate.handle(exchange);
        } catch (RuntimeException exception) {
            handleUnexpectedException(
                    exchange,
                    exception
            );
        }
    }

    private static void handleUnexpectedException(
            HttpExchange exchange,
            RuntimeException exception
    ) throws IOException {
        String requestId =
                RequestIds.getOrCreate(exchange);

        LOGGER.log(
                ERROR,
                "Error HTTP no controlado. requestId="
                        + requestId
                        + " type="
                        + exception
                                .getClass()
                                .getSimpleName()
        );

        if (exchange.getResponseCode() == -1) {
            HttpErrorResponses.internalServerError(
                    exchange
            );

            return;
        }

        exchange.close();
    }
}
