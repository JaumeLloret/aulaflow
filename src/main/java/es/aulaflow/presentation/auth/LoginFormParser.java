package es.aulaflow.presentation.auth;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class LoginFormParser {

    private static final String FORM_CONTENT_TYPE =
            "application/x-www-form-urlencoded";

    private static final int MAXIMUM_BODY_BYTES =
            8_192;

    FormCredentials parse(
            HttpExchange exchange
    ) throws IOException {
        String contentType =
                exchange
                        .getRequestHeaders()
                        .getFirst("Content-Type");

        if (
                contentType == null
                        || !contentType
                        .toLowerCase(Locale.ROOT)
                        .startsWith(
                                FORM_CONTENT_TYPE
                        )
        ) {
            throw new InvalidLoginRequestException();
        }

        byte[] body;

        try (var requestBody = exchange.getRequestBody()) {
            body = requestBody.readNBytes(
                    MAXIMUM_BODY_BYTES + 1
            );
        }

        if (body.length > MAXIMUM_BODY_BYTES) {
            throw new InvalidLoginRequestException();
        }

        Map<String, String> fields =
                parseFields(
                        new String(
                                body,
                                StandardCharsets.UTF_8
                        )
                );

        String username = fields.get("username");
        String password = fields.get("password");

        if (
                username == null
                        || username.isBlank()
                        || username.length() > 128
                        || password == null
                        || password.isEmpty()
                        || password.codePointCount(
                                0,
                                password.length()
                        ) > 128
        ) {
            throw new InvalidLoginRequestException();
        }

        return new FormCredentials(
                username,
                password.toCharArray()
        );
    }

    private static Map<String, String> parseFields(
            String body
    ) {
        Map<String, String> fields =
                new HashMap<>();

        try {
            for (String pair : body.split("&", -1)) {
                int separator = pair.indexOf('=');

                if (separator < 1) {
                    throw new InvalidLoginRequestException();
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
                    throw new InvalidLoginRequestException();
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidLoginRequestException();
        }

        return fields;
    }

    static final class InvalidLoginRequestException
            extends RuntimeException {
    }
}
