package es.aulaflow.infrastructure.http;

import es.aulaflow.application.auth.AdministratorRepository;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.application.auth.PasswordHasher;
import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.auth.SessionId;
import es.aulaflow.application.auth.SessionIdGenerator;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import es.aulaflow.presentation.auth.AccountHandler;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.LoginHandler;
import es.aulaflow.presentation.auth.LogoutHandler;
import es.aulaflow.presentation.auth.SessionCookie;
import es.aulaflow.presentation.http.HttpHandlerPipeline;
import es.aulaflow.presentation.http.RequestIds;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationEndpointTest {

    private static final String USERNAME =
            "teacher";

    private static final String PASSWORD =
            "fictional classroom password";

    @Test
    void servesAccessibleLoginForm() throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> response =
                    configured.send(
                            "GET",
                            LoginHandler.PATH,
                            null,
                            null,
                            null
                    );

            assertEquals(200, response.statusCode());
            assertEquals(
                    "text/html; charset=utf-8",
                    header(response, "Content-Type")
            );
            assertEquals(
                    "no-store",
                    header(response, "Cache-Control")
            );
            assertEquals(
                    "nosniff",
                    header(
                            response,
                            "X-Content-Type-Options"
                    )
            );
            assertTrue(
                    response.body().contains(
                            "action=\"/login\""
                    )
            );
            assertRequestId(response);
        }
    }

    @Test
    void correctLoginCreatesSecurelyScopedSession()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> response =
                    configured.login(
                            USERNAME,
                            PASSWORD
                    );

            assertEquals(303, response.statusCode());
            assertEquals(
                    AccountHandler.PATH,
                    header(response, "Location")
            );

            String setCookie =
                    header(response, "Set-Cookie");

            assertTrue(
                    setCookie.startsWith(
                            "AULAFLOW_SESSION="
                    )
            );
            assertTrue(setCookie.contains("; Path=/"));
            assertTrue(setCookie.contains("; HttpOnly"));
            assertTrue(
                    setCookie.contains(
                            "; SameSite=Strict"
                    )
            );
            assertFalse(setCookie.contains("; Secure"));
            assertFalse(setCookie.contains(USERNAME));
            assertFalse(setCookie.contains(PASSWORD));
            assertRequestId(response);
        }
    }

    @Test
    void invalidCredentialsReturnGenericError()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> unknownUser =
                    configured.login(
                            "unknown",
                            PASSWORD
                    );

            HttpResponse<String> wrongPassword =
                    configured.login(
                            USERNAME,
                            "different fictional password"
                    );

            assertEquals(
                    401,
                    unknownUser.statusCode()
            );
            assertEquals(
                    401,
                    wrongPassword.statusCode()
            );
            assertTrue(
                    unknownUser.body().contains(
                            "data-auth-error"
                    )
            );
            assertTrue(
                    wrongPassword.body().contains(
                            "data-auth-error"
                    )
            );
            assertFalse(
                    unknownUser.body().contains("unknown")
            );
            assertFalse(
                    wrongPassword.body().contains(PASSWORD)
            );
        }
    }

    @Test
    void rejectsInvalidAndCrossSiteLoginRequests()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> invalidBody =
                    configured.send(
                            "POST",
                            LoginHandler.PATH,
                            "text/plain",
                            "username=teacher",
                            null
                    );

            assertEquals(
                    400,
                    invalidBody.statusCode()
            );
            assertTrue(
                    invalidBody
                            .body()
                            .contains("INVALID_REQUEST")
            );
            assertRequestId(invalidBody);

            HttpResponse<String> crossSite =
                    configured.send(
                            "POST",
                            LoginHandler.PATH,
                            "application/x-www-form-urlencoded",
                            loginBody(
                                    USERNAME,
                                    PASSWORD
                            ),
                            "cross-site"
                    );

            assertEquals(
                    403,
                    crossSite.statusCode()
            );
            assertTrue(
                    crossSite
                            .body()
                            .contains("REQUEST_FORBIDDEN")
            );
        }
    }

    @Test
    void privatePageRequiresKnownSession()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> withoutSession =
                    configured.send(
                            "GET",
                            AccountHandler.PATH,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    303,
                    withoutSession.statusCode()
            );
            assertEquals(
                    LoginHandler.PATH,
                    header(withoutSession, "Location")
            );

            String cookie =
                    cookiePair(
                            configured.login(
                                    USERNAME,
                                    PASSWORD
                            )
                    );

            HttpResponse<String> authenticated =
                    configured.sendWithCookie(
                            "GET",
                            AccountHandler.PATH,
                            cookie
                    );

            assertEquals(
                    200,
                    authenticated.statusCode()
            );
            assertTrue(
                    authenticated.body().contains(
                            "action=\"/logout\""
                    )
            );
        }
    }

    @Test
    void logoutInvalidatesCookieEvenWhenRepeated()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            String cookie =
                    cookiePair(
                            configured.login(
                                    USERNAME,
                                    PASSWORD
                            )
                    );

            HttpResponse<String> account =
                    configured.sendWithCookie(
                            "GET",
                            AccountHandler.PATH,
                            cookie
                    );

            String csrfToken = account.body()
                    .split(
                            "name=\"_csrf\"",
                            2
                    )[1]
                    .split(
                            "value=\"",
                            2
                    )[1]
                    .split("\"", 2)[0];

            HttpResponse<String> logout =
                    configured.sendWithCookie(
                            "POST",
                            LogoutHandler.PATH,
                            cookie,
                            "application/x-www-form-urlencoded",
                            "_csrf=" + csrfToken
                    );

            assertEquals(303, logout.statusCode());
            assertTrue(
                    header(logout, "Set-Cookie")
                            .contains("Max-Age=0")
            );

            HttpResponse<String> reused =
                    configured.sendWithCookie(
                            "GET",
                            AccountHandler.PATH,
                            cookie
                    );

            assertEquals(303, reused.statusCode());

            HttpResponse<String> repeated =
                    configured.send(
                            "POST",
                            LogoutHandler.PATH,
                            null,
                            "",
                            "same-origin"
                    );

            assertEquals(
                    303,
                    repeated.statusCode()
            );
        }
    }

    @Test
    void rejectsMethodsAndPrefixPaths()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer()
        ) {
            HttpResponse<String> method =
                    configured.send(
                            "PUT",
                            LoginHandler.PATH,
                            null,
                            null,
                            null
                    );

            assertEquals(405, method.statusCode());
            assertEquals(
                    "GET, POST",
                    header(method, "Allow")
            );

            HttpResponse<String> prefix =
                    configured.send(
                            "GET",
                            "/login/extra",
                            null,
                            null,
                            null
                    );

            assertEquals(404, prefix.statusCode());
        }
    }

    private static ConfiguredServer createServer()
            throws Exception {
        AulaFlowHttpServer server =
                AulaFlowHttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1",
                                0
                        )
                );

        AuthenticationService authenticationService =
                new AuthenticationService(
                        new TestAdministratorRepository(),
                        new TestPasswordHasher(),
                        new InMemorySessionStore(),
                        new SequentialSessionIdGenerator(),
                        () -> new CsrfToken(
                                "C".repeat(43)
                        ),
                        new InMemoryLoginAttemptLimiter(),
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-30T20:00:00Z"
                                ),
                                ZoneOffset.UTC
                        ),
                        Duration.ofMinutes(30)
                );

        SessionCookie sessionCookie =
                new SessionCookie(false);

        server.registerContext(
                LoginHandler.PATH,
                HttpHandlerPipeline.standard(
                        new LoginHandler(
                                authenticationService,
                                sessionCookie
                        )
                )
        );

        server.registerContext(
                AccountHandler.PATH,
                HttpHandlerPipeline.standard(
                        new AuthenticationRequiredHandler(
                                AccountHandler.PATH,
                                authenticationService,
                                sessionCookie,
                                new AccountHandler()
                        )
                )
        );

        server.registerContext(
                LogoutHandler.PATH,
                HttpHandlerPipeline.standard(
                        new LogoutHandler(
                                authenticationService,
                                sessionCookie
                        )
                )
        );

        server.start();

        return new ConfiguredServer(server);
    }

    private static String loginBody(
            String username,
            String password
    ) {
        return "username="
                + encode(username)
                + "&password="
                + encode(password);
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static String cookiePair(
            HttpResponse<?> response
    ) {
        return header(response, "Set-Cookie")
                .split(";", 2)[0];
    }

    private static String header(
            HttpResponse<?> response,
            String name
    ) {
        return response
                .headers()
                .firstValue(name)
                .orElseThrow();
    }

    private static void assertRequestId(
            HttpResponse<?> response
    ) {
        String requestId =
                header(
                        response,
                        RequestIds.RESPONSE_HEADER
                );

        java.util.UUID.fromString(requestId);
    }

    private static final class ConfiguredServer
            implements AutoCloseable {

        private final AulaFlowHttpServer server;

        private ConfiguredServer(
                AulaFlowHttpServer server
        ) {
            this.server = server;
        }

        private HttpResponse<String> login(
                String username,
                String password
        ) throws Exception {
            return send(
                    "POST",
                    LoginHandler.PATH,
                    "application/x-www-form-urlencoded",
                    loginBody(username, password),
                    "same-origin"
            );
        }

        private HttpResponse<String> sendWithCookie(
                String method,
                String path,
                String cookie
        ) throws Exception {
            return sendWithCookie(
                    method,
                    path,
                    cookie,
                    null,
                    null
            );
        }

        private HttpResponse<String> sendWithCookie(
                String method,
                String path,
                String cookie,
                String contentType,
                String body
        ) throws Exception {
            int port = server.getAddress().getPort();

            HttpRequest.Builder builder = HttpRequest
                    .newBuilder(
                            URI.create(
                                    "http://127.0.0.1:"
                                            + port
                                            + path
                            )
                    )
                    .timeout(Duration.ofSeconds(2))
                    .header("Cookie", cookie)
                    .header(
                            "Sec-Fetch-Site",
                            "same-origin"
                    );

            if (contentType != null) {
                builder.header(
                        "Content-Type",
                        contentType
                );
            }

            HttpRequest request = builder.method(
                            method,
                            body == null
                                    ? HttpRequest
                                    .BodyPublishers
                                    .noBody()
                                    : HttpRequest
                                    .BodyPublishers
                                    .ofString(body)
                    )
                    .build();

            return send(request);
        }

        private HttpResponse<String> send(
                String method,
                String path,
                String contentType,
                String body,
                String fetchSite
        ) throws Exception {
            int port = server.getAddress().getPort();

            HttpRequest.Builder builder =
                    HttpRequest
                            .newBuilder(
                                    URI.create(
                                            "http://127.0.0.1:"
                                                    + port
                                                    + path
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(2)
                            );

            if (contentType != null) {
                builder.header(
                        "Content-Type",
                        contentType
                );
            }

            if (fetchSite != null) {
                builder.header(
                        "Sec-Fetch-Site",
                        fetchSite
                );
            }

            builder.method(
                    method,
                    body == null
                            ? HttpRequest
                            .BodyPublishers
                            .noBody()
                            : HttpRequest
                            .BodyPublishers
                            .ofString(
                                    body,
                                    StandardCharsets.UTF_8
                            )
            );

            return send(builder.build());
        }

        private HttpResponse<String> send(
                HttpRequest request
        ) throws Exception {
            try (
                    HttpClient client =
                            HttpClient
                                    .newBuilder()
                                    .followRedirects(
                                            HttpClient
                                            .Redirect.NEVER
                                    )
                                    .connectTimeout(
                                            Duration.ofSeconds(
                                                    2
                                            )
                                    )
                                    .build()
            ) {
                return client.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString(
                                        StandardCharsets
                                                .UTF_8
                                )
                );
            }
        }

        @Override
        public void close() {
            server.close();
        }
    }

    private static final class
    TestAdministratorRepository
            implements AdministratorRepository {

        private final StoredAdministrator stored =
                new StoredAdministrator(
                        new Administrator(
                                1L,
                                new AdministratorUsername(
                                        USERNAME
                                )
                        ),
                        new PasswordVerifier(
                                "test-verifier"
                        )
                );

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Administrator create(
                AdministratorUsername username,
                PasswordVerifier passwordVerifier
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredAdministrator>
        findByUsername(
                AdministratorUsername username
        ) {
            if (
                    stored
                            .administrator()
                            .username()
                            .equals(username)
            ) {
                return Optional.of(stored);
            }

            return Optional.empty();
        }
    }

    private static final class TestPasswordHasher
            implements PasswordHasher {

        private static final PasswordVerifier REAL =
                new PasswordVerifier("test-verifier");

        @Override
        public PasswordVerifier hash(char[] password) {
            return REAL;
        }

        @Override
        public boolean verify(
                char[] password,
                PasswordVerifier passwordVerifier
        ) {
            return Arrays.equals(
                    PASSWORD.toCharArray(),
                    password
            ) && "test-verifier".equals(
                    passwordVerifier.encodedValue()
            );
        }

        @Override
        public PasswordVerifier createDummyVerifier() {
            return new PasswordVerifier(
                    "dummy-verifier"
            );
        }
    }

    private static final class
    SequentialSessionIdGenerator
            implements SessionIdGenerator {

        private final AtomicInteger sequence =
                new AtomicInteger();

        @Override
        public SessionId generate() {
            char value = (char) (
                    'A' + sequence.getAndIncrement()
            );

            return new SessionId(
                    Character.toString(value)
                            .repeat(43)
            );
        }
    }
}
