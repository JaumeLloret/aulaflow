package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.application.auth.AuthenticatedSession;

import java.io.IOException;

public final class AccountHandler
        implements HttpHandler {

    public static final String PATH = "/account";

    private static final String GET = "GET";

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        if (!GET.equals(exchange.getRequestMethod())) {
            HttpErrorResponses.methodNotAllowed(
                    exchange,
                    GET
            );

            return;
        }

        AuthenticatedSession session =
                (AuthenticatedSession)
                        exchange.getAttribute(
                                AuthenticationRequiredHandler
                                        .SESSION_ATTRIBUTE
                        );

        AuthHttpResponses.sendHtml(
                exchange,
                200,
                AuthenticationPages.account(
                        session.csrfToken().value()
                )
        );
    }
}
