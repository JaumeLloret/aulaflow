package es.aulaflow.presentation.http.staticcontent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpResponseWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StaticResourceHandler implements HttpHandler {

    public static final String PATH =
            "/";

    public static final String STYLESHEET_PATH =
            "/assets/css/app.css";

    public static final String SCRIPT_PATH =
            "/assets/js/app.js";

    public static final String VALENCIAN_CATALOG_PATH =
            "/assets/i18n/ca.json";

    public static final String SPANISH_CATALOG_PATH =
            "/assets/i18n/es.json";

    private static final String GET_METHOD =
            "GET";

    private static final String INDEX_RESOURCE_PATH =
            "/web/index.html";

    private static final String STYLESHEET_RESOURCE_PATH =
            "/web/assets/css/app.css";

    private static final String SCRIPT_RESOURCE_PATH =
            "/web/assets/js/app.js";

    private static final String VALENCIAN_CATALOG_RESOURCE_PATH =
            "/web/assets/i18n/ca.json";

    private static final String SPANISH_CATALOG_RESOURCE_PATH =
            "/web/assets/i18n/es.json";

    private static final int OK =
            200;

    private static final Map<String, String> RESOURCE_PATHS =
            Map.of(
                    PATH,
                    INDEX_RESOURCE_PATH,
                    STYLESHEET_PATH,
                    STYLESHEET_RESOURCE_PATH,
                    SCRIPT_PATH,
                    SCRIPT_RESOURCE_PATH,
                    VALENCIAN_CATALOG_PATH,
                    VALENCIAN_CATALOG_RESOURCE_PATH,
                    SPANISH_CATALOG_PATH,
                    SPANISH_CATALOG_RESOURCE_PATH
            );

    private static final String CACHE_CONTROL_HEADER =
            "Cache-Control";

    private static final String NO_STORE =
            "no-store";

    private final ClasspathResourceLoader resourceLoader =
            new ClasspathResourceLoader();

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        Objects.requireNonNull(
                exchange,
                "El intercambio HTTP no puede ser null."
        );

        String requestedPath =
                exchange.getRequestURI().getPath();

        Optional<String> resolvedResourcePath =
                resolveResourcePath(requestedPath);

        if (resolvedResourcePath.isEmpty()) {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        String requestMethod =
                exchange.getRequestMethod();

        if (!GET_METHOD.equals(requestMethod)) {
            HttpErrorResponses.methodNotAllowed(
                    exchange,
                    GET_METHOD
            );

            return;
        }

        String resourcePath =
                resolvedResourcePath.orElseThrow();

        byte[] responseBytes =
                loadResource(resourcePath);

        exchange.getResponseHeaders().set(
                CACHE_CONTROL_HEADER,
                NO_STORE
        );

        HttpResponseWriter.sendBytes(
                exchange,
                OK,
                StaticContentTypes.forResourcePath(
                        resourcePath
                ),
                responseBytes
        );
    }

    private static Optional<String> resolveResourcePath(
            String requestedPath
    ) {
        return Optional.ofNullable(
                RESOURCE_PATHS.get(requestedPath)
        );
    }

    private byte[] loadResource(
            String resourcePath
    ) {
        try {
            return resourceLoader
                    .load(resourcePath)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "No se ha encontrado el recurso estático: "
                                            + resourcePath
                            )
                    );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se ha podido leer el recurso estático: "
                            + resourcePath,
                    exception
            );
        }
    }
}
