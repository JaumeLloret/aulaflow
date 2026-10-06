package es.aulaflow.presentation.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;

public final class SecurityHeadersHandler
        implements HttpHandler {

    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'; "
                    + "frame-ancestors 'none'; "
                    + "object-src 'none'; "
                    + "script-src 'self'; "
                    + "style-src 'self'; "
                    + "img-src 'self' data:";

    public static final String PERMISSIONS_POLICY =
            "geolocation=(), camera=(), microphone=()";

    private final HttpHandler delegate;

    public SecurityHeadersHandler(HttpHandler delegate) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "El manejador delegado no puede ser null."
        );
    }

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {
        Headers headers = exchange.getResponseHeaders();

        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(
                "Content-Security-Policy",
                CONTENT_SECURITY_POLICY
        );
        headers.set("X-Frame-Options", "DENY");
        headers.set(
                "Referrer-Policy",
                "strict-origin-when-cross-origin"
        );
        headers.set(
                "Permissions-Policy",
                PERMISSIONS_POLICY
        );

        delegate.handle(exchange);
    }
}
