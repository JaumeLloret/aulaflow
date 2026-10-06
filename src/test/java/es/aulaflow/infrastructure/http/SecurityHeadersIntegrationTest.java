package es.aulaflow.infrastructure.http;

import es.aulaflow.presentation.http.HttpHandlerPipeline;
import es.aulaflow.presentation.http.HttpResponseWriter;
import es.aulaflow.presentation.http.SecurityHeadersHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersIntegrationTest {

    @Test
    void standardPipelineAddsSecurityHeaders()
            throws Exception {
        try (
                AulaFlowHttpServer server =
                        AulaFlowHttpServer.create(
                                new InetSocketAddress(
                                        "127.0.0.1",
                                        0
                                )
                        )
        ) {
            server.registerContext(
                    "/security-test",
                    HttpHandlerPipeline.standard(
                            exchange ->
                                    HttpResponseWriter.sendJson(
                                            exchange,
                                            200,
                                            "{\"status\":\"ok\"}"
                                    )
                    )
            );

            server.start();

            URI uri = URI.create(
                    "http://127.0.0.1:"
                            + server.getAddress().getPort()
                            + "/security-test"
            );

            HttpRequest request =
                    HttpRequest
                            .newBuilder(uri)
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();

            try (
                    HttpClient client =
                            HttpClient.newHttpClient()
            ) {
                HttpResponse<Void> response =
                        client.send(
                                request,
                                HttpResponse
                                        .BodyHandlers
                                        .discarding()
                        );

                assertEquals(200, response.statusCode());

                assertEquals(
                        "no-store",
                        response.headers()
                                .firstValue("Cache-Control")
                                .orElseThrow()
                );

                assertEquals(
                        "nosniff",
                        response.headers()
                                .firstValue(
                                        "X-Content-Type-Options"
                                )
                                .orElseThrow()
                );

                assertEquals(
                        SecurityHeadersHandler.CONTENT_SECURITY_POLICY,
                        response.headers()
                                .firstValue(
                                        "Content-Security-Policy"
                                )
                                .orElseThrow()
                );

                assertEquals(
                        "DENY",
                        response.headers()
                                .firstValue("X-Frame-Options")
                                .orElseThrow()
                );

                assertEquals(
                        "strict-origin-when-cross-origin",
                        response.headers()
                                .firstValue("Referrer-Policy")
                                .orElseThrow()
                );

                assertEquals(
                        SecurityHeadersHandler.PERMISSIONS_POLICY,
                        response.headers()
                                .firstValue("Permissions-Policy")
                                .orElseThrow()
                );

                String csp = response.headers()
                        .firstValue("Content-Security-Policy")
                        .orElseThrow();

                assertTrue(csp.contains("frame-ancestors 'none'"));
                assertFalse(csp.contains("'unsafe-inline'"));
                assertFalse(csp.contains("'unsafe-eval'"));
            }
        }
    }
}
