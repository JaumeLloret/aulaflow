package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.FormUrlEncodedParser;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LogoutHandler
        implements HttpHandler {

    public static final String PATH = "/logout";

    private static final String POST = "POST";

    private final AuthenticationService authenticationService;
    private final SessionCookie sessionCookie;

    public LogoutHandler(
            AuthenticationService authenticationService,
            SessionCookie sessionCookie
    ) {
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
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        if (!PATH.equals(exchange.getRequestURI().getPath())) {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        if (!POST.equals(exchange.getRequestMethod())) {
            HttpErrorResponses.methodNotAllowed(
                    exchange,
                    POST
            );

            return;
        }

        Optional<String> rawSessionId =
                sessionCookie.read(
                        exchange.getRequestHeaders()
                );

        Optional<AuthenticatedSession> session =
                rawSessionId.flatMap(
                        authenticationService
                                ::authenticateSession
                );

        if (session.isPresent()) {
            exchange.setAttribute(
                    AuthenticationRequiredHandler
                            .SESSION_ATTRIBUTE,
                    session.orElseThrow()
            );

            Map<String, String> fields;

            try {
                fields = FormUrlEncodedParser
                        .parse(exchange);
            } catch (
                    FormUrlEncodedParser
                            .InvalidFormException exception
            ) {
                HttpErrorResponses.badRequest(exchange);
                return;
            }

            if (
                    !CsrfProtection.require(
                            exchange,
                            fields.get(
                                    CsrfProtection
                                            .FORM_FIELD
                            )
                    )
            ) {
                return;
            }

            authenticationService.logout(
                    rawSessionId.orElseThrow()
            );
        } else if (
                CrossSiteRequestGuard.isCrossSite(
                        exchange
                )
        ) {
            HttpErrorResponses.forbidden(exchange);
            return;
        }

        sessionCookie.clear(
                exchange.getResponseHeaders()
        );

        AuthHttpResponses.redirect(
                exchange,
                LoginHandler.PATH
        );
    }
}
