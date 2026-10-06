package es.aulaflow.infrastructure.http;

import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.ProvisionInitialAdministrator;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteAdministratorRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteBoardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteChecklistItemRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConnectionFactory;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteLabelRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteMigrator;
import es.aulaflow.infrastructure.security.Pbkdf2PasswordHasher;
import es.aulaflow.infrastructure.security.SecureRandomCsrfTokenGenerator;
import es.aulaflow.infrastructure.security.SecureRandomSessionIdGenerator;
import es.aulaflow.presentation.auth.AccountHandler;
import es.aulaflow.presentation.auth.ApiAuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.LoginHandler;
import es.aulaflow.presentation.auth.LogoutHandler;
import es.aulaflow.presentation.auth.SessionCookie;
import es.aulaflow.presentation.board.BoardApiHandler;
import es.aulaflow.presentation.board.BoardHtmlHandler;
import es.aulaflow.presentation.http.HttpHandlerPipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre el corte vertical completo de AulaFlow mediante peticiones
 * HTTP reales contra un servidor real y una base SQLite temporal, sin
 * navegador: login con credenciales reales, sesión, CSRF, tablero,
 * columna, tarjeta, etiqueta, checklist, persistencia tras un
 * reinicio simulado (grafo de objetos y almacén de sesiones nuevos) y
 * logout con invalidación de la sesión anterior.
 */
final class VerticalSliceEndToEndTest {

    private static final String USERNAME = "profesora.e2e";
    private static final String PASSWORD =
            "una-contrasenya-molt-llarga-1";

    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "name=\"_csrf\" value=\"([^\"]+)\""
    );

    private static final Pattern LOCATION_ID_PATTERN =
            Pattern.compile("/boards/(\\d+)$");

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeVerticalSliceSurvivesRestart() throws Exception {
        Path databasePath =
                temporaryDirectory.resolve("e2e.db");

        provisionDatabase(databasePath);

        Session firstCycle = new Session(databasePath);

        try {
            firstCycle.start();

            // Login con credenciales reales
            HttpResponse<String> loginPage = firstCycle.request(
                    "GET", LoginHandler.PATH, null, null, null
            );
            assertEquals(200, loginPage.statusCode());

            HttpResponse<String> wrongLogin = firstCycle.request(
                    "POST",
                    LoginHandler.PATH,
                    "username=" + USERNAME
                            + "&password=" + encode("incorrecta"),
                    null,
                    null
            );
            assertEquals(401, wrongLogin.statusCode());

            HttpResponse<String> login = firstCycle.request(
                    "POST",
                    LoginHandler.PATH,
                    "username=" + USERNAME
                            + "&password=" + encode(PASSWORD),
                    null,
                    null
            );
            assertEquals(303, login.statusCode());

            String cookie = extractSessionCookie(login);
            assertNotNull(cookie);

            // Sesión: recurso privado accesible con la cookie
            HttpResponse<String> boardsPage = firstCycle.request(
                    "GET", BoardHtmlHandler.PATH, null, cookie, null
            );
            assertEquals(200, boardsPage.statusCode());

            String csrf = extractCsrf(boardsPage.body());
            assertNotNull(csrf);

            // Tablero
            HttpResponse<String> boardCreated = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH,
                    "name=" + encode("Tauler E2E")
                            + "&_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, boardCreated.statusCode());

            long boardId = extractBoardId(
                    boardCreated.headers()
                            .firstValue("Location")
                            .orElseThrow()
            );

            HttpResponse<String> boardPage = firstCycle.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + boardId,
                    null,
                    cookie,
                    null
            );
            long columnId = extractFirstId(
                    boardPage.body(),
                    "data-column-id=\"(\\d+)\""
            );

            // Tarjeta
            HttpResponse<String> cardCreated = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + boardId
                            + "/columns/" + columnId + "/cards",
                    "title=" + encode("Targeta E2E")
                            + "&description="
                            + encode("Descripció amb accent")
                            + "&_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, cardCreated.statusCode());

            HttpResponse<String> boardWithCard = firstCycle.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + boardId,
                    null,
                    cookie,
                    null
            );
            long cardId = extractFirstId(
                    boardWithCard.body(),
                    "data-card-id=\"(\\d+)\""
            );

            // Etiqueta
            HttpResponse<String> labelCreated = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + boardId + "/labels",
                    "name=" + encode("Urgent")
                            + "&color=red&_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, labelCreated.statusCode());

            HttpResponse<String> boardWithLabel = firstCycle.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + boardId,
                    null,
                    cookie,
                    null
            );
            long labelId = extractFirstId(
                    boardWithLabel.body(),
                    "/labels/(\\d+)/assign"
            );

            HttpResponse<String> labelAssigned = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + boardId
                            + "/cards/" + cardId + "/labels/"
                            + labelId + "/assign",
                    "_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, labelAssigned.statusCode());

            // Checklist
            HttpResponse<String> itemCreated = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + boardId
                            + "/cards/" + cardId
                            + "/checklist-items",
                    "text=" + encode("Revisar abans de lliurar")
                            + "&_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, itemCreated.statusCode());

            HttpResponse<String> boardWithItem = firstCycle.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + boardId,
                    null,
                    cookie,
                    null
            );
            long itemId = extractFirstId(
                    boardWithItem.body(),
                    "data-item-id=\"(\\d+)\""
            );

            HttpResponse<String> itemToggled = firstCycle.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + boardId
                            + "/cards/" + cardId
                            + "/checklist-items/" + itemId
                            + "/toggle",
                    "completed=true&_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, itemToggled.statusCode());

            HttpResponse<String> finalBoardPage = firstCycle.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + boardId,
                    null,
                    cookie,
                    null
            );

            assertTrue(
                    finalBoardPage.body().contains("Tauler E2E")
            );
            assertTrue(
                    finalBoardPage.body().contains("Targeta E2E")
            );
            assertTrue(
                    finalBoardPage.body().contains("Urgent")
            );
            assertTrue(
                    finalBoardPage.body().contains(
                            "Revisar abans de lliurar"
                    )
            );
            assertTrue(
                    finalBoardPage.body().contains("1/1")
            );

            // Logout
            HttpResponse<String> loggedOut = firstCycle.request(
                    "POST",
                    LogoutHandler.PATH,
                    "_csrf=" + csrf,
                    cookie,
                    null
            );
            assertEquals(303, loggedOut.statusCode());

            // La sesión anterior ya no vale
            HttpResponse<String> afterLogout = firstCycle.request(
                    "GET",
                    AccountHandler.PATH,
                    null,
                    cookie,
                    null
            );
            assertEquals(303, afterLogout.statusCode());

            String finalState = finalBoardPage.body();

            // --- Reinicio: nuevo grafo de objetos, mismo archivo ---
            Session secondCycle = new Session(databasePath);

            try {
                secondCycle.start();

                HttpResponse<String> sessionAfterRestart =
                        secondCycle.request(
                                "GET",
                                AccountHandler.PATH,
                                null,
                                cookie,
                                null
                        );

                assertEquals(
                        303,
                        sessionAfterRestart.statusCode(),
                        "La sesión del ciclo anterior no debe "
                                + "sobrevivir a un reinicio."
                );

                HttpResponse<String> newLogin = secondCycle.request(
                        "POST",
                        LoginHandler.PATH,
                        "username=" + USERNAME
                                + "&password=" + encode(PASSWORD),
                        null,
                        null
                );
                assertEquals(303, newLogin.statusCode());

                String newCookie = extractSessionCookie(newLogin);
                assertNotNull(newCookie);
                assertFalse(newCookie.equals(cookie));

                HttpResponse<String> recoveredBoard =
                        secondCycle.request(
                                "GET",
                                BoardHtmlHandler.PATH
                                        + "/" + boardId,
                                null,
                                newCookie,
                                null
                        );

                assertEquals(200, recoveredBoard.statusCode());

                assertTrue(
                        recoveredBoard.body()
                                .contains("Tauler E2E")
                );
                assertTrue(
                        recoveredBoard.body()
                                .contains("Targeta E2E")
                );
                assertTrue(
                        recoveredBoard.body().contains("Urgent")
                );
                assertTrue(
                        recoveredBoard.body().contains(
                                "Revisar abans de lliurar"
                        )
                );
                assertTrue(
                        recoveredBoard.body().contains("1/1")
                );
            } finally {
                secondCycle.close();
            }
        } finally {
            firstCycle.close();
        }
    }

    private void provisionDatabase(Path databasePath)
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        SqliteConfig.from(
                                Map.of(
                                        "AULAFLOW_DB_PATH",
                                        databasePath.toString()
                                ),
                                temporaryDirectory
                        )
                );

        new SqliteMigrator(connectionFactory).migrate();

        new ProvisionInitialAdministrator(
                new SqliteAdministratorRepository(
                        connectionFactory
                ),
                new Pbkdf2PasswordHasher()
        ).execute(
                USERNAME,
                PASSWORD.toCharArray()
        );
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static String extractSessionCookie(
            HttpResponse<?> response
    ) {
        String setCookie = response.headers()
                .firstValue("Set-Cookie")
                .orElseThrow();

        Matcher matcher = Pattern.compile(
                SessionCookie.NAME + "=([^;]+)"
        ).matcher(setCookie);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractCsrf(String html) {
        Matcher matcher = CSRF_PATTERN.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static long extractBoardId(String location) {
        Matcher matcher = LOCATION_ID_PATTERN.matcher(location);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "No se encontró el identificador de tablero "
                            + "en: " + location
            );
        }

        return Long.parseLong(matcher.group(1));
    }

    private static long extractFirstId(
            String html,
            String regex
    ) {
        Matcher matcher =
                Pattern.compile(regex).matcher(html);

        if (!matcher.find()) {
            throw new IllegalStateException(
                    "No se encontró el patrón '" + regex
                            + "' en el HTML."
            );
        }

        return Long.parseLong(matcher.group(1));
    }

    /**
     * Una sesión de servidor completa (mismo montaje que
     * {@code AulaFlowApplication}), con su propio almacén de
     * sesiones en memoria. Cerrarla y crear una nueva sobre el mismo
     * archivo simula un reinicio real: ninguna referencia de esta
     * clase sobrevive entre ciclos.
     */
    private static final class Session
            implements AutoCloseable {

        private final Path databasePath;
        private es.aulaflow.infrastructure.http.AulaFlowHttpServer
                server;

        Session(Path databasePath) {
            this.databasePath = databasePath;
        }

        void start() throws Exception {
            SqliteConnectionFactory connectionFactory =
                    new SqliteConnectionFactory(
                            SqliteConfig.from(
                                    Map.of(
                                            "AULAFLOW_DB_PATH",
                                            databasePath.toString()
                                    ),
                                    databasePath.getParent()
                            )
                    );

            new SqliteMigrator(connectionFactory).migrate();

            AuthenticationService authenticationService =
                    new AuthenticationService(
                            new SqliteAdministratorRepository(
                                    connectionFactory
                            ),
                            new Pbkdf2PasswordHasher(),
                            new InMemorySessionStore(),
                            new SecureRandomSessionIdGenerator(),
                            new SecureRandomCsrfTokenGenerator(),
                            new InMemoryLoginAttemptLimiter()
                    );

            SessionCookie cookie = new SessionCookie(false);

            BoardService boardService = new BoardService(
                    new SqliteBoardRepository(connectionFactory)
            );

            CardService cardService = new CardService(
                    new SqliteCardRepository(connectionFactory)
            );

            LabelService labelService = new LabelService(
                    new SqliteLabelRepository(connectionFactory)
            );

            ChecklistService checklistService =
                    new ChecklistService(
                            new SqliteChecklistItemRepository(
                                    connectionFactory
                            )
                    );

            server = es.aulaflow.infrastructure.http
                    .AulaFlowHttpServer.create(
                            new InetSocketAddress("127.0.0.1", 0)
                    );

            server.registerContext(
                    LoginHandler.PATH,
                    HttpHandlerPipeline.standard(
                            new LoginHandler(
                                    authenticationService,
                                    cookie
                            )
                    )
            );

            server.registerContext(
                    LogoutHandler.PATH,
                    HttpHandlerPipeline.standard(
                            new LogoutHandler(
                                    authenticationService,
                                    cookie
                            )
                    )
            );

            server.registerContext(
                    AccountHandler.PATH,
                    HttpHandlerPipeline.standard(
                            new AuthenticationRequiredHandler(
                                    AccountHandler.PATH,
                                    authenticationService,
                                    cookie,
                                    new AccountHandler()
                            )
                    )
            );

            server.registerContext(
                    BoardHtmlHandler.PATH,
                    HttpHandlerPipeline.standard(
                            new AuthenticationRequiredHandler(
                                    BoardHtmlHandler.PATH,
                                    true,
                                    authenticationService,
                                    cookie,
                                    new BoardHtmlHandler(
                                            boardService,
                                            cardService,
                                            labelService,
                                            checklistService,
                                            new CsvExportService(
                                                    boardService,
                                                    cardService
                                            )
                                    )
                            )
                    )
            );

            server.registerContext(
                    BoardApiHandler.PATH,
                    HttpHandlerPipeline.standard(
                            new ApiAuthenticationRequiredHandler(
                                    BoardApiHandler.PATH,
                                    authenticationService,
                                    cookie,
                                    new BoardApiHandler(
                                            boardService,
                                            cardService,
                                            labelService,
                                            checklistService,
                                            new CsvExportService(
                                                    boardService,
                                                    cardService
                                            )
                                    )
                            )
                    )
            );

            server.start();
        }

        HttpResponse<String> request(
                String method,
                String path,
                String formBody,
                String sessionCookie,
                String fetchSite
        ) throws Exception {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(
                            URI.create(
                                    "http://127.0.0.1:"
                                            + server.getAddress()
                                            .getPort()
                                            + path
                            )
                    ).timeout(Duration.ofSeconds(3));

            if (sessionCookie != null) {
                builder.header(
                        "Cookie",
                        SessionCookie.NAME + "="
                                + sessionCookie
                );
            }

            if (formBody != null) {
                builder.header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                );
            }

            builder.header(
                    "Sec-Fetch-Site",
                    fetchSite == null
                            ? "same-origin"
                            : fetchSite
            );

            builder.method(
                    method,
                    formBody == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers
                            .ofString(formBody)
            );

            try (
                    HttpClient client = HttpClient.newBuilder()
                            .followRedirects(
                                    HttpClient.Redirect.NEVER
                            )
                            .build()
            ) {
                return client.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
            }
        }

        @Override
        public void close() {
            if (server != null) {
                server.close();
            }
        }
    }
}
