package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;

import java.util.Objects;
import java.util.UUID;

public final class RequestIds {

    public static final String RESPONSE_HEADER =
            "X-Request-Id";

    private RequestIds() {
    }

    public static String getOrCreate(
            HttpExchange exchange
    ) {
        Objects.requireNonNull(
                exchange,
                "El intercambio HTTP no puede ser null."
        );

        String existingRequestId =
                exchange
                        .getResponseHeaders()
                        .getFirst(RESPONSE_HEADER);

        if (
                existingRequestId != null
                        && !existingRequestId.isBlank()
        ) {
            return existingRequestId;
        }

        String requestId =
                UUID.randomUUID().toString();

        exchange.getResponseHeaders().set(
                RESPONSE_HEADER,
                requestId
        );

        return requestId;
    }
}
