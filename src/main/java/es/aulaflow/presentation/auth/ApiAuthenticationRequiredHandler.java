package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.presentation.http.HttpErrorResponses;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class ApiAuthenticationRequiredHandler
        implements HttpHandler {

    private final String protectedPath;
    private final AuthenticationService authenticationService;
    private final SessionCookie sessionCookie;
    private final HttpHandler delegate;

    public ApiAuthenticationRequiredHandler(
            String protectedPath,
            AuthenticationService authenticationService,
            SessionCookie sessionCookie,
            HttpHandler delegate
    ) {
        this.protectedPath = Objects.requireNonNull(
                protectedPath
        );
        this.authenticationService =
                Objects.requireNonNull(
                        authenticationService
                );
        this.sessionCookie =
                Objects.requireNonNull(sessionCookie);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        String requestPath =
                exchange.getRequestURI().getPath();

        if (
                !protectedPath.equals(requestPath)
                        && !requestPath.startsWith(
                                protectedPath + "/"
                        )
        ) {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        Optional<AuthenticatedSession> session =
                sessionCookie
                        .read(
                                exchange
                                        .getRequestHeaders()
                        )
                        .flatMap(
                                authenticationService
                                        ::authenticateSession
                        );

        if (session.isEmpty()) {
            sessionCookie.clear(
                    exchange.getResponseHeaders()
            );
            HttpErrorResponses.unauthorized(exchange);
            return;
        }

        AuthenticatedSession authenticated =
                session.orElseThrow();

        exchange.setAttribute(
                AuthenticationRequiredHandler
                        .SESSION_ATTRIBUTE,
                authenticated
        );
        exchange.setAttribute(
                AuthenticationRequiredHandler
                        .ADMINISTRATOR_ATTRIBUTE,
                authenticated.administrator()
        );

        delegate.handle(exchange);
    }
}
