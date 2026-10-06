package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.presentation.http.HttpErrorResponses;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class AuthenticationRequiredHandler
        implements HttpHandler {

    public static final String ADMINISTRATOR_ATTRIBUTE =
            Administrator.class.getName();

    public static final String SESSION_ATTRIBUTE =
            AuthenticatedSession.class.getName();

    private final String protectedPath;
    private final boolean includeSubpaths;
    private final AuthenticationService authenticationService;
    private final SessionCookie sessionCookie;
    private final HttpHandler delegate;

    public AuthenticationRequiredHandler(
            String protectedPath,
            AuthenticationService authenticationService,
            SessionCookie sessionCookie,
            HttpHandler delegate
    ) {
        this(
                protectedPath,
                false,
                authenticationService,
                sessionCookie,
                delegate
        );
    }

    public AuthenticationRequiredHandler(
            String protectedPath,
            boolean includeSubpaths,
            AuthenticationService authenticationService,
            SessionCookie sessionCookie,
            HttpHandler delegate
    ) {
        this.protectedPath =
                Objects.requireNonNull(
                        protectedPath,
                        "La ruta protegida no puede ser null."
                );

        this.includeSubpaths = includeSubpaths;

        this.authenticationService =
                Objects.requireNonNull(
                        authenticationService,
                        "El servicio no puede ser null."
                );

        this.sessionCookie =
                Objects.requireNonNull(
                        sessionCookie,
                        "La cookie no puede ser null."
                );

        this.delegate = Objects.requireNonNull(
                delegate,
                "El manejador no puede ser null."
        );
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        if (
                !matchesProtectedPath(
                        exchange
                                .getRequestURI()
                                .getPath()
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

            AuthHttpResponses.redirect(
                    exchange,
                    LoginHandler.PATH
            );

            return;
        }

        AuthenticatedSession authenticatedSession =
                session.orElseThrow();

        exchange.setAttribute(
                ADMINISTRATOR_ATTRIBUTE,
                authenticatedSession.administrator()
        );

        exchange.setAttribute(
                SESSION_ATTRIBUTE,
                authenticatedSession
        );

        delegate.handle(exchange);
    }

    private boolean matchesProtectedPath(String requestPath) {
        return protectedPath.equals(requestPath)
                || (
                includeSubpaths
                        && requestPath.startsWith(
                                protectedPath + "/"
                        )
        );
    }
}
