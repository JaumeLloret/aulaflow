package es.aulaflow.infrastructure.http;

import es.aulaflow.presentation.health.HealthHandler;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpHandlerPipeline;
import es.aulaflow.presentation.http.RequestIds;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEndpointTest {

    private static final String APPLICATION_VERSION =
            "0.1.0-SNAPSHOT";

    private static final String ENVIRONMENT =
            "test";

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse(
                            "2026-07-21T00:00:00Z"
                    ),
                    ZoneOffset.UTC
            );

    @Test
    void returnsHealthInformationForGet()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            HealthHandler.PATH
                    );

            String expectedBody = """
                    {
                      "status": "UP",
                      "version": "0.1.0-SNAPSHOT",
                      "environment": "test",
                      "time": "2026-07-21T00:00:00Z"
                    }
                    """.strip();

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
                    expectedBody,
                    response.body()
            );

            readValidRequestId(response);
        }
    }

    @Test
    void rejectsUnsupportedHttpMethod()
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
                            HealthHandler.PATH
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
    void rejectsPathThatOnlySharesHealthPrefix()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            "/api/v1/health/details"
                    );

            assertEquals(
                    404,
                    response.statusCode()
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
        }
    }

    @Test
    void returnsJsonNotFoundForUnknownResource()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        createConfiguredServer()
        ) {
            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            "/api/v1/unknown"
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
        }
    }

    @Test
    void convertsUnexpectedExceptionIntoInternalServerError()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        createEmptyServer()
        ) {
            server.registerContext(
                    "/api/v1/failure",
                    HttpHandlerPipeline.standard(
                            exchange -> {
                                throw new IllegalStateException(
                                        "Detalle interno sensible"
                                );
                            }
                    )
            );

            server.start();

            HttpResponse<String> response =
                    sendRequest(
                            server,
                            "GET",
                            "/api/v1/failure"
                    );

            assertEquals(
                    500,
                    response.statusCode()
            );

            assertEquals(
                    "no-store",
                    response
                            .headers()
                            .firstValue("Cache-Control")
                            .orElseThrow()
            );

            String requestId =
                    readValidRequestId(response);

            assertTrue(
                    response
                            .body()
                            .contains(
                                    "INTERNAL_SERVER_ERROR"
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
                                    "Detalle interno sensible"
                            )
            );
        }
    }

    private static AulaFlowHttpServer
    createConfiguredServer() throws Exception {
        AulaFlowHttpServer server =
                createEmptyServer();

        HealthHandler healthHandler =
                new HealthHandler(
                        APPLICATION_VERSION,
                        ENVIRONMENT,
                        FIXED_CLOCK
                );

        server.registerContext(
                HealthHandler.PATH,
                HttpHandlerPipeline.standard(
                        healthHandler
                )
        );

        server.registerContext(
                "/",
                HttpHandlerPipeline.standard(
                        HttpErrorResponses::notFound
                )
        );

        return server;
    }

    private static AulaFlowHttpServer
    createEmptyServer() throws Exception {
        return AulaFlowHttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                )
        );
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
                .header(
                        "Accept",
                        "application/json"
                )
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
}
