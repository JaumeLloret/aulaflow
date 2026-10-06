package es.aulaflow.infrastructure.http;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AulaFlowHttpServerTest {

    @Test
    void startsOnAnAvailableOperatingSystemPort()
            throws Exception {
        InetSocketAddress requestedAddress =
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                );

        try (
                AulaFlowHttpServer server = AulaFlowHttpServer.create(
                        requestedAddress
                )
        ) {
            assertFalse(server.isStarted());

            server.start();

            assertTrue(server.isStarted());
            assertTrue(
                    server.getAddress().getPort() > 0
            );
        }
    }

    @Test
    void returnsNotFoundWhenNoContextIsRegistered() throws Exception {
        InetSocketAddress requestedAddress =
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                );

        try (
                AulaFlowHttpServer server =
                        AulaFlowHttpServer.create(requestedAddress)
        ) {
            server.start();

            int assignedPort = server.getAddress().getPort();

            URI uri = URI.create(
                    "http://127.0.0.1:" + assignedPort + "/unregistered"
            );

            HttpRequest request = HttpRequest
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

                assertEquals(
                        404,
                        response.statusCode()
                );
            }
        }
    }

    @Test
    void rejectsStartingTheSameServerTwice() throws Exception {
        InetSocketAddress requestedAddress =
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                );

        try (
                AulaFlowHttpServer server =
                        AulaFlowHttpServer.create(
                                requestedAddress
                        )
        ) {
            server.start();

            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            server::start
                    );

            assertTrue(
                    exception
                            .getMessage()
                            .contains("ya está iniciado")
            );
        }
    }
}
