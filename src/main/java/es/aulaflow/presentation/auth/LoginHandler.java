package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.presentation.http.HttpErrorResponses;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class LoginHandler
        implements HttpHandler {

    public static final String PATH = "/login";

    private static final String GET = "GET";
    private static final String POST = "POST";

    private final AuthenticationService authenticationService;
    private final SessionCookie sessionCookie;
    private final LoginFormParser formParser =
            new LoginFormParser();

    public LoginHandler(
            AuthenticationService authenticationService,
            SessionCookie sessionCookie
    ) {
        this.authenticationService =
                Objects.requireNonNull(
                        authenticationService,
                        "El servicio de autenticación no puede ser null."
                );

        this.sessionCookie =
                Objects.requireNonNull(
                        sessionCookie,
                        "La cookie no puede ser null."
                );
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        if (!PATH.equals(exchange.getRequestURI().getPath())) {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        switch (exchange.getRequestMethod()) {
            case GET -> showLogin(exchange, false, 200);
            case POST -> attemptLogin(exchange);
            default -> HttpErrorResponses
                    .methodNotAllowed(
                            exchange,
                            "GET, POST"
                    );
        }
    }

    private void attemptLogin(
            HttpExchange exchange
    ) throws IOException {
        if (CrossSiteRequestGuard.isCrossSite(exchange)) {
            HttpErrorResponses.forbidden(exchange);
            return;
        }

        try (
                FormCredentials credentials =
                        formParser.parse(exchange)
        ) {
            Optional<AuthenticatedSession> session =
                    authenticationService.login(
                            credentials.username(),
                            credentials.password()
                    );

            if (session.isEmpty()) {
                showLogin(exchange, true, 401);
                return;
            }

            sessionCookie.set(
                    exchange.getResponseHeaders(),
                    session.orElseThrow().id()
            );

            AuthHttpResponses.redirect(
                    exchange,
                    AccountHandler.PATH
            );
        } catch (
                LoginFormParser
                        .InvalidLoginRequestException
                        exception
        ) {
            HttpErrorResponses.badRequest(exchange);
        }
    }

    private static void showLogin(
            HttpExchange exchange,
            boolean showError,
            int status
    ) throws IOException {
        AuthHttpResponses.sendHtml(
                exchange,
                status,
                AuthenticationPages.login(
                        showError
                )
        );
    }
}
