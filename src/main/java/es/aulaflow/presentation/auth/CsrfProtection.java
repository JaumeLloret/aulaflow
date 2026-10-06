package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.presentation.http.HttpErrorResponses;

import java.io.IOException;

public final class CsrfProtection {

    public static final String FORM_FIELD = "_csrf";
    public static final String HEADER = "X-CSRF-Token";

    private CsrfProtection() {
    }

    public static boolean require(
            HttpExchange exchange,
            String presentedToken
    ) throws IOException {
        Object value = exchange.getAttribute(
                AuthenticationRequiredHandler
                        .SESSION_ATTRIBUTE
        );

        if (
                !(value instanceof
                        AuthenticatedSession session)
                        || CrossSiteRequestGuard
                        .isCrossSite(exchange)
                        || !session
                        .csrfToken()
                        .matches(presentedToken)
        ) {
            HttpErrorResponses.forbidden(exchange);
            return false;
        }

        return true;
    }
}
