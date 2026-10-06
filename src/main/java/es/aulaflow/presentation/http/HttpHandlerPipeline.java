package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpHandler;

import java.util.Objects;

public final class HttpHandlerPipeline {

    private HttpHandlerPipeline() {
    }

    public static HttpHandler standard(
            HttpHandler endpointHandler
    ) {
        Objects.requireNonNull(
                endpointHandler,
                "El manejador del endpoint no puede ser null."
        );

        HttpHandler exceptionHandlingHandler =
                new ExceptionHandlingHandler(
                        endpointHandler
                );

        HttpHandler securityHeadersHandler =
                new SecurityHeadersHandler(
                        exceptionHandlingHandler
                );

        return new RequestTracingHandler(
                securityHeadersHandler
        );
    }
}
