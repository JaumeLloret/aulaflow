package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;

final class CrossSiteRequestGuard {

    private CrossSiteRequestGuard() {
    }

    static boolean isCrossSite(
            HttpExchange exchange
    ) {
        String fetchSite =
                exchange
                        .getRequestHeaders()
                        .getFirst("Sec-Fetch-Site");

        return fetchSite != null
                && "cross-site"
                .equalsIgnoreCase(
                        fetchSite.trim()
                );
    }
}
