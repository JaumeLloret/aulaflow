package es.aulaflow.presentation.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FormUrlEncodedParser {

    private static final String CONTENT_TYPE =
            "application/x-www-form-urlencoded";

    private static final int MAXIMUM_BODY_BYTES =
            16_384;

    private FormUrlEncodedParser() {
    }

    public static Map<String, String> parse(
            HttpExchange exchange
    ) throws IOException {
        String contentType = exchange
                .getRequestHeaders()
                .getFirst("Content-Type");

        if (
                contentType == null
                        || !contentType
                        .toLowerCase(Locale.ROOT)
                        .startsWith(CONTENT_TYPE)
        ) {
            throw new InvalidFormException();
        }

        byte[] body;

        try (var requestBody = exchange.getRequestBody()) {
            body = requestBody.readNBytes(
                    MAXIMUM_BODY_BYTES + 1
            );
        }

        if (body.length > MAXIMUM_BODY_BYTES) {
            throw new InvalidFormException();
        }

        Map<String, String> fields =
                new LinkedHashMap<>();

        try {
            String encoded = new String(
                    body,
                    StandardCharsets.UTF_8
            );

            if (encoded.isEmpty()) {
                return Map.of();
            }

            for (String pair : encoded.split("&", -1)) {
                int separator = pair.indexOf('=');

                if (separator < 1) {
                    throw new InvalidFormException();
                }

                String name = URLDecoder.decode(
                        pair.substring(0, separator),
                        StandardCharsets.UTF_8
                );
                String value = URLDecoder.decode(
                        pair.substring(separator + 1),
                        StandardCharsets.UTF_8
                );

                if (
                        fields.putIfAbsent(
                                name,
                                value
                        ) != null
                ) {
                    throw new InvalidFormException();
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidFormException();
        }

        return Map.copyOf(fields);
    }

    public static final class InvalidFormException
            extends RuntimeException {
    }
}
