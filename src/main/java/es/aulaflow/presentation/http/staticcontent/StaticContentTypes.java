package es.aulaflow.presentation.http.staticcontent;

import java.util.Objects;

final class StaticContentTypes {

    private static final String HTML =
            "text/html; charset=utf-8";

    private static final String CSS =
            "text/css; charset=utf-8";

    private static final String JAVASCRIPT =
            "text/javascript; charset=utf-8";

    private static final String JSON =
            "application/json; charset=utf-8";

    private static final String BINARY =
            "application/octet-stream";

    private StaticContentTypes() {
    }

    static String forResourcePath(String resourcePath) {
        Objects.requireNonNull(
                resourcePath,
                "La ruta del recurso no puede ser null."
        );

        if (resourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "La ruta del recurso no puede estar vacía."
            );
        }

        if (resourcePath.endsWith(".html")) {
            return HTML;
        }

        if (resourcePath.endsWith(".css")) {
            return CSS;
        }

        if (resourcePath.endsWith(".js")) {
            return JAVASCRIPT;
        }

        if (resourcePath.endsWith(".json")) {
            return JSON;
        }

        return BINARY;
    }
}
