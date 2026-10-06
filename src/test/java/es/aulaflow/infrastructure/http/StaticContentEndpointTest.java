package es.aulaflow.infrastructure.http;

import es.aulaflow.presentation.http.HttpHandlerPipeline;
import es.aulaflow.presentation.http.RequestIds;
import es.aulaflow.presentation.http.staticcontent.StaticResourceHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticContentEndpointTest {

    @Test
    void returnsIndexPageForGet() throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            StaticResourceHandler.PATH
                    );

            assertEquals(
                    200,
                    response.statusCode()
            );

            assertEquals(
                    "text/html; charset=utf-8",
                    response
                            .headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
            );

            assertEquals(
                    "no-store",
                    response
                            .headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
            );

            assertEquals(
                    "nosniff",
                    response
                            .headers()
                            .firstValue(
                                    "X-Content-Type-Options"
                            )
                            .orElseThrow()
            );

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "data-i18n=\"document.title\""
                            )
            );

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "href=\"/assets/css/app.css\""
                            )
            );

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "src=\"/assets/js/app.js\""
                            )
            );

            readValidRequestId(response);
        }
    }

    @Test
    void rejectsUnsupportedHttpMethodForIndex()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "POST",
                            StaticResourceHandler.PATH
                    );

            assertEquals(
                    405,
                    response.statusCode()
            );

            assertEquals(
                    "GET",
                    response
                            .headers()
                            .firstValue("Allow")
                            .orElseThrow()
            );

            String requestId =
                    readValidRequestId(response);

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "METHOD_NOT_ALLOWED"
                            )
            );

            assertTrue(
                    response
                            .body()
                            .contains(requestId)
            );
        }
    }

    @Test
    void returnsNotFoundForUnknownStaticPath()
            throws Exception {
        assertStaticPathNotFound(
                "/missing"
        );
    }

    @Test
    void rejectsDirectAccessToInternalClasspathPath()
            throws Exception {
        assertStaticPathNotFound(
                "/web/index.html"
        );
    }

    @Test
    void rejectsLiteralPathTraversal()
            throws Exception {
        assertStaticPathNotFound(
                "/assets/../web/index.html"
        );
    }

    @Test
    void rejectsEncodedPathTraversal()
            throws Exception {
        assertStaticPathNotFound(
                "/assets/%2e%2e/web/index.html"
        );
    }

    private static void assertStaticPathNotFound(
            String path
    ) throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            path
                    );

            assertEquals(
                    404,
                    response.statusCode()
            );

            assertEquals(
                    "application/json; charset=utf-8",
                    response
                            .headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
            );

            String requestId =
                    readValidRequestId(response);

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "RESOURCE_NOT_FOUND"
                            )
            );

            assertTrue(
                    response
                            .body()
                            .contains(requestId)
            );

            assertFalse(
                    response
                            .body()
                            .contains(
                                    "<!DOCTYPE html>"
                            ),
                    "Una ruta rechazada no debe exponer "
                            + "el recurso HTML interno."
            );
        }
    }

    private static AulaFlowHttpServer
    createConfiguredServer() throws Exception {
        AulaFlowHttpServer server =
                AulaFlowHttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1",
                                0
                        )
                );

        StaticResourceHandler staticResourceHandler =
                new StaticResourceHandler();

        server.registerContext(
                StaticResourceHandler.PATH,
                HttpHandlerPipeline.standard(
                        staticResourceHandler
                )
        );

        return server;
    }

    private static HttpResponse<String> sendRequest(
            AulaFlowHttpServer server,
            String method,
            String path
    ) throws Exception {
        int assignedPort =
                server.getAddress().getPort();

        URI uri = URI.create(
                "http://127.0.0.1:"
                        + assignedPort
                        + path
        );

        HttpRequest request = HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofSeconds(2))
                .method(
                        method,
                        HttpRequest
                                .BodyPublishers
                                .noBody()
                )
                .build();

        try (
                HttpClient client = HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(2)
                        )
                        .build()
        ) {
            return client.send(
                    request,
                    HttpResponse
                            .BodyHandlers
                            .ofString(
                                    StandardCharsets.UTF_8
                            )
            );
        }
    }

    private static String readValidRequestId(
            HttpResponse<?> response
    ) {
        String requestId = response
                .headers()
                .firstValue(
                        RequestIds.RESPONSE_HEADER
                )
                .orElseThrow();

        UUID.fromString(requestId);

        return requestId;
    }


    @Test
    void returnsStylesheetForGet() throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            StaticResourceHandler
                                    .STYLESHEET_PATH
                    );

            assertEquals(
                    200,
                    response.statusCode()
            );

            assertEquals(
                    "text/css; charset=utf-8",
                    response
                            .headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
            );

            assertEquals(
                    "no-store",
                    response
                            .headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
            );

            assertEquals(
                    "nosniff",
                    response
                            .headers()
                            .firstValue(
                                    "X-Content-Type-Options"
                            )
                            .orElseThrow()
            );

            assertTrue(
                    response
                            .body()
                            .contains(":root")
            );

            readValidRequestId(response);
        }
    }

    @Test
    void returnsJavaScriptForGet() throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            StaticResourceHandler
                                    .SCRIPT_PATH
                    );

            assertEquals(
                    200,
                    response.statusCode()
            );

            assertEquals(
                    "text/javascript; charset=utf-8",
                    response
                            .headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
            );

            assertEquals(
                    "no-store",
                    response
                            .headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
            );

            assertEquals(
                    "nosniff",
                    response
                            .headers()
                            .firstValue(
                                    "X-Content-Type-Options"
                            )
                            .orElseThrow()
            );

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "dataset.js"
                            )
            );

            readValidRequestId(response);
        }
    }

    @Test
    void returnsValencianCatalogForGet() throws Exception {
        assertCatalogResponse(
                StaticResourceHandler
                        .VALENCIAN_CATALOG_PATH,
                "\"home.welcome.title\": "
                        + "\"Benvingut a AulaFlow\""
        );
    }

    @Test
    void returnsSpanishCatalogForGet() throws Exception {
        assertCatalogResponse(
                StaticResourceHandler
                        .SPANISH_CATALOG_PATH,
                "\"home.welcome.title\": "
                        + "\"Bienvenido a AulaFlow\""
        );
    }

    private static void assertCatalogResponse(
            String path,
            String expectedTranslation
    ) throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            path
                    );

            assertEquals(
                    200,
                    response.statusCode()
            );

            assertEquals(
                    "application/json; charset=utf-8",
                    response
                            .headers()
                            .firstValue("Content-Type")
                            .orElseThrow()
            );

            assertEquals(
                    "no-store",
                    response
                            .headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
            );

            assertEquals(
                    "nosniff",
                    response
                            .headers()
                            .firstValue(
                                    "X-Content-Type-Options"
                            )
                            .orElseThrow()
            );

            assertTrue(
                    response
                            .body()
                            .contains(expectedTranslation)
            );

            readValidRequestId(response);
        }
    }
}
