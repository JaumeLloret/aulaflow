package es.aulaflow.infrastructure.http;

import es.aulaflow.application.auth.AdministratorRepository;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.application.auth.PasswordHasher;
import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.auth.SessionId;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteAdministratorRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteBoardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteChecklistItemRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteLabelRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConnectionFactory;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteMigrator;
import es.aulaflow.presentation.auth.ApiAuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardEndpointTest {

    private static final SessionId SESSION_ID =
            new SessionId("S".repeat(43));

    private static final CsrfToken CSRF_TOKEN =
            new CsrfToken("C".repeat(43));

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresAuthenticationForHtmlAndApi()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("auth.db")
        ) {
            assertEquals(
                    303,
                    configured.request(
                            "GET",
                            BoardHtmlHandler.PATH,
                            false,
                            null,
                            null,
                            null
                    ).statusCode()
            );

            HttpResponse<String> api =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH,
                            false,
                            null,
                            null,
                            null
                    );

            assertEquals(401, api.statusCode());
            assertTrue(
                    api.body().contains(
                            "AUTHENTICATION_REQUIRED"
                    )
            );
        }
    }

    @Test
    void htmlCreatesAndRendersPersistentBoard()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("html.db")
        ) {
            HttpResponse<String> empty =
                    configured.request(
                            "GET",
                            BoardHtmlHandler.PATH,
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(200, empty.statusCode());
            assertTrue(
                    empty.body().contains(
                            "name=\"_csrf\""
                    )
            );
            assertTrue(
                    empty.body().contains(
                            CSRF_TOKEN.value()
                    )
            );

            HttpResponse<String> created =
                    configured.request(
                            "POST",
                            BoardHtmlHandler.PATH,
                            true,
                            "_csrf="
                                    + CSRF_TOKEN.value()
                                    + "&name="
                                    + encode(
                                    "Projecte <1>"
                            ),
                            null,
                            "same-origin"
                    );

            assertEquals(303, created.statusCode());

            HttpResponse<String> details =
                    configured.request(
                            "GET",
                            header(created, "Location"),
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(200, details.statusCode());
            assertTrue(
                    details.body().contains(
                            "Projecte &lt;1&gt;"
                    )
            );
            assertTrue(details.body().contains("Per fer"));
            assertTrue(details.body().contains("En curs"));
            assertTrue(details.body().contains("Fet"));
            assertFalse(
                    details.body().contains(
                            "<h1 id=\"board-title\">Projecte <1>"
                    )
            );

            assertTrue(
                    details.body().contains(
                            "data-i18n=\"cards.dnd.available\""
                    )
            );
        }
    }

    @Test
    void apiCreatesRenamesAddsAndReorders()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api.db")
        ) {
            HttpResponse<String> created =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=" + encode("Aula"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(201, created.statusCode());
            assertEquals(
                    "no-store",
                    header(created, "Cache-Control")
            );

            long boardId = identifiers(
                    created.body()
            )[0];

            HttpResponse<String> renamed =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/" + boardId,
                            true,
                            "name="
                                    + encode("Aula nova"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(200, renamed.statusCode());
            assertTrue(
                    renamed.body().contains(
                            "\"name\":\"Aula nova\""
                    )
            );

            HttpResponse<String> withColumn =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns",
                            true,
                            "name=" + encode("Revisió"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            long[] ids = identifiers(
                    withColumn.body()
            );

            String reverseOrder =
                    ids[4] + ","
                            + ids[3] + ","
                            + ids[2] + ","
                            + ids[1];

            HttpResponse<String> reordered =
                    configured.request(
                            "PUT",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns/order",
                            true,
                            "order=" + reverseOrder,
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(200, reordered.statusCode());
            assertTrue(
                    reordered.body().indexOf("Revisió")
                            < reordered.body()
                            .indexOf("Fet")
            );
        }
    }

    @Test
    void apiCompletesCardLifecycle()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "card-api-lifecycle.db"
                        )
        ) {
            HttpResponse<String> createdBoard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=" + encode("Aula"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdBoard.statusCode()
            );

            long[] boardIdentifiers =
                    identifiers(
                            createdBoard.body()
                    );

            long boardId =
                    boardIdentifiers[0];

            long sourceColumnId =
                    boardIdentifiers[1];

            long targetColumnId =
                    boardIdentifiers[2];

            long followingColumnId =
                    boardIdentifiers[3];

            HttpResponse<String> createdCard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns/"
                                    + sourceColumnId
                                    + "/cards",
                            true,
                            "title="
                                    + encode(
                                    "Preparar tema"
                            )
                                    + "&description="
                                    + encode(
                                    "Revisar exemples"
                            ),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdCard.statusCode()
            );

            assertEquals(
                    "no-store",
                    header(
                            createdCard,
                            "Cache-Control"
                    )
            );

            assertFalse(
                    header(
                            createdCard,
                            "X-Request-Id"
                    ).isBlank()
            );

            assertTrue(
                    createdCard.body().contains(
                            "\"title\":\"Preparar tema\""
                    )
            );

            assertTrue(
                    createdCard.body().contains(
                            "\"description\":"
                                    + "\"Revisar exemples\""
                    )
            );

            long cardId =
                    identifiers(
                            createdCard.body()
                    )[0];

            HttpResponse<String> boardWithCard =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    200,
                    boardWithCard.statusCode()
            );

            assertTrue(
                    boardWithCard.body().contains(
                            "\"cards\":["
                    )
            );

            assertTrue(
                    boardWithCard.body().contains(
                            "\"title\":\"Preparar tema\""
                    )
            );

            HttpResponse<String> updatedCard =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/" + cardId,
                            true,
                            "title="
                                    + encode(
                                    "Tema actualitzat"
                            )
                                    + "&description="
                                    + encode(
                                    "Inclou exercicis"
                            ),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    200,
                    updatedCard.statusCode()
            );

            assertTrue(
                    updatedCard.body().contains(
                            "\"title\":"
                                    + "\"Tema actualitzat\""
                    )
            );

            assertTrue(
                    updatedCard.body().contains(
                            "\"description\":"
                                    + "\"Inclou exercicis\""
                    )
            );

            HttpResponse<String> movedCard =
                    configured.request(
                            "PUT",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/" + cardId
                                    + "/position",
                            true,
                            "targetColumnId="
                                    + targetColumnId
                                    + "&targetPosition=0",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    200,
                    movedCard.statusCode()
            );

            int targetColumnIndex =
                    movedCard.body().indexOf(
                            "\"id\":" + targetColumnId
                    );

            int movedCardIndex =
                    movedCard.body().indexOf(
                            "\"title\":"
                                    + "\"Tema actualitzat\""
                    );

            int followingColumnIndex =
                    movedCard.body().indexOf(
                            "\"id\":" + followingColumnId
                    );

            assertTrue(
                    targetColumnIndex >= 0
            );

            assertTrue(
                    movedCardIndex
                            > targetColumnIndex
            );

            assertTrue(
                    followingColumnIndex
                            > movedCardIndex
            );

            HttpResponse<String> deletedCard =
                    configured.request(
                            "DELETE",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/" + cardId,
                            true,
                            null,
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    204,
                    deletedCard.statusCode()
            );

            assertTrue(
                    deletedCard.body().isEmpty()
            );

            assertEquals(
                    "no-store",
                    header(
                            deletedCard,
                            "Cache-Control"
                    )
            );

            assertFalse(
                    header(
                            deletedCard,
                            "X-Request-Id"
                    ).isBlank()
            );

            HttpResponse<String> boardAfterDeletion =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    200,
                    boardAfterDeletion.statusCode()
            );

            assertFalse(
                    boardAfterDeletion.body().contains(
                            "\"title\":"
                                    + "\"Tema actualitzat\""
                    )
            );
        }
    }

    @Test
    void cardMutationsRejectInvalidPositionAndCsrf()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "card-api-validation.db"
                        )
        ) {
            HttpResponse<String> createdBoard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=" + encode("Aula"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdBoard.statusCode()
            );

            long[] boardIdentifiers =
                    identifiers(
                            createdBoard.body()
                    );

            long boardId =
                    boardIdentifiers[0];

            long columnId =
                    boardIdentifiers[1];

            HttpResponse<String> createdCard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns/"
                                    + columnId
                                    + "/cards",
                            true,
                            "title="
                                    + encode("Targeta")
                                    + "&description=",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdCard.statusCode()
            );

            long cardId =
                    identifiers(
                            createdCard.body()
                    )[0];

            HttpResponse<String> invalidPosition =
                    configured.request(
                            "PUT",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/" + cardId
                                    + "/position",
                            true,
                            "targetColumnId="
                                    + columnId
                                    + "&targetPosition=99",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    400,
                    invalidPosition.statusCode()
            );

            assertFalse(
                    header(
                            invalidPosition,
                            "X-Request-Id"
                    ).isBlank()
            );

            HttpResponse<String> alteredCsrf =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/" + cardId,
                            true,
                            "title="
                                    + encode("No debe cambiar")
                                    + "&description=",
                            "X".repeat(43),
                            "same-origin"
                    );

            assertEquals(
                    403,
                    alteredCsrf.statusCode()
            );

            HttpResponse<String> boardAfterFailures =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    200,
                    boardAfterFailures.statusCode()
            );

            assertTrue(
                    boardAfterFailures.body().contains(
                            "\"title\":\"Targeta\""
                    )
            );

            assertFalse(
                    boardAfterFailures.body().contains(
                            "\"title\":\"No debe cambiar\""
                    )
            );
        }
    }

    @Test
    void cardApiReturnsControlledNotFoundAndMethodNotAllowed()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "card-api-errors.db"
                        )
        ) {
            HttpResponse<String> createdBoard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=" + encode("Aula"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdBoard.statusCode()
            );

            long[] boardIdentifiers =
                    identifiers(
                            createdBoard.body()
                    );

            long boardId =
                    boardIdentifiers[0];

            long columnId =
                    boardIdentifiers[1];

            HttpResponse<String> missingCard =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/999",
                            true,
                            "title="
                                    + encode("No existe")
                                    + "&description=",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    404,
                    missingCard.statusCode()
            );

            assertFalse(
                    header(
                            missingCard,
                            "X-Request-Id"
                    ).isBlank()
            );

            HttpResponse<String> wrongCardMethod =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/999",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    405,
                    wrongCardMethod.statusCode()
            );

            assertEquals(
                    "PATCH, DELETE",
                    header(
                            wrongCardMethod,
                            "Allow"
                    )
            );

            HttpResponse<String> wrongPositionMethod =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/cards/999/position",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    405,
                    wrongPositionMethod.statusCode()
            );

            assertEquals(
                    "PUT",
                    header(
                            wrongPositionMethod,
                            "Allow"
                    )
            );

            HttpResponse<String> wrongCreateMethod =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns/"
                                    + columnId
                                    + "/cards",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    405,
                    wrongCreateMethod.statusCode()
            );

            assertEquals(
                    "POST",
                    header(
                            wrongCreateMethod,
                            "Allow"
                    )
            );
        }
    }

    @Test
    void mutationsRejectMissingAlteredAndCrossSiteTokens()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("csrf.db")
        ) {
            assertEquals(
                    403,
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=Aula",
                            null,
                            "same-origin"
                    ).statusCode()
            );

            assertEquals(
                    403,
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=Aula",
                            "X".repeat(43),
                            "same-origin"
                    ).statusCode()
            );

            assertEquals(
                    403,
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=Aula",
                            CSRF_TOKEN.value(),
                            "cross-site"
                    ).statusCode()
            );

            assertTrue(
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH,
                            true,
                            null,
                            null,
                            null
                    ).body().contains("\"boards\":[]")
            );
        }
    }

    @Test
    void creatingCardInMissingColumnReturnsControlledNotFound()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "missing-card-column.db"
                        )
        ) {
            HttpResponse<String> createdBoard =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH,
                            true,
                            "name=" + encode("Aula"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    201,
                    createdBoard.statusCode()
            );

            long boardId = identifiers(
                    createdBoard.body()
            )[0];

            HttpResponse<String> response =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH
                                    + "/" + boardId
                                    + "/columns/999/cards",
                            true,
                            "title="
                                    + encode("Impossible")
                                    + "&description=",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    404,
                    response.statusCode()
            );

            assertFalse(
                    header(
                            response,
                            "X-Request-Id"
                    ).isBlank()
            );
        }
    }

    @Test
    void addingColumnToMissingBoardReturnsControlledNotFound()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("missing-board.db")
        ) {
            HttpResponse<String> response =
                    configured.request(
                            "POST",
                            BoardApiHandler.PATH
                                    + "/999/columns",
                            true,
                            "name="
                                    + encode(
                                    "Impossible"
                            ),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(
                    404,
                    response.statusCode()
            );

            assertFalse(
                    header(
                            response,
                            "X-Request-Id"
                    ).isBlank()
            );
        }
    }

    @Test
    void apiRejectsWrongMethodsAndUnknownRoutes()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("methods.db")
        ) {
            HttpResponse<String> patchOrder =
                    configured.request(
                            "PATCH",
                            BoardApiHandler.PATH
                                    + "/1/columns/order",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    405,
                    patchOrder.statusCode()
            );
            assertEquals(
                    "PUT",
                    header(
                            patchOrder,
                            "Allow"
                    )
            );

            HttpResponse<String> putColumn =
                    configured.request(
                            "PUT",
                            BoardApiHandler.PATH
                                    + "/1/columns/1",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    405,
                    putColumn.statusCode()
            );
            assertEquals(
                    "PATCH",
                    header(
                            putColumn,
                            "Allow"
                    )
            );

            HttpResponse<String> unknownRoute =
                    configured.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/1/desconocido",
                            true,
                            null,
                            null,
                            null
                    );

            assertEquals(
                    404,
                    unknownRoute.statusCode()
            );
        }
    }

    private ConfiguredServer createServer(
            String databaseName
    ) throws Exception {
        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        SqliteConfig.from(
                                Map.of(
                                        "AULAFLOW_DB_PATH",
                                        temporaryDirectory
                                                .resolve(
                                                        databaseName
                                                )
                                                .toString()
                                ),
                                temporaryDirectory
                        )
                );

        new SqliteMigrator(connectionFactory).migrate();

        Administrator administrator =
                new SqliteAdministratorRepository(
                        connectionFactory
                ).create(
                        new AdministratorUsername(
                                "teacher"
                        ),
                        new PasswordVerifier("test")
                );

        InMemorySessionStore sessionStore =
                new InMemorySessionStore();

        Instant now =
                Instant.parse("2026-07-31T00:00:00Z");

        sessionStore.save(
                new AuthenticatedSession(
                        SESSION_ID,
                        administrator,
                        CSRF_TOKEN,
                        now,
                        now.plus(Duration.ofMinutes(30))
                )
        );

        AuthenticationService authenticationService =
                new AuthenticationService(
                        new UnusedAdministratorRepository(),
                        new UnusedPasswordHasher(),
                        sessionStore,
                        () -> SESSION_ID,
                        () -> CSRF_TOKEN,
                        new InMemoryLoginAttemptLimiter(),
                        Clock.fixed(now, ZoneOffset.UTC),
                        Duration.ofMinutes(30)
                );

        BoardService boardService =
                new BoardService(
                        new SqliteBoardRepository(
                                connectionFactory
                        )
                );

        CardService cardService =
                new CardService(
                        new SqliteCardRepository(
                                connectionFactory
                        )
                );

        LabelService labelService =
                new LabelService(
                        new SqliteLabelRepository(
                                connectionFactory
                        )
                );

        ChecklistService checklistService =
                new ChecklistService(
                        new SqliteChecklistItemRepository(
                                connectionFactory
                        )
                );

        AulaFlowHttpServer server =
                AulaFlowHttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1",
                                0
                        )
                );

        SessionCookie cookie =
                new SessionCookie(false);

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
        return new ConfiguredServer(server);
    }

    private static long[] identifiers(String json) {
        Matcher matcher = Pattern.compile(
                "\"id\":(\\d+)"
        ).matcher(json);

        long[] values = new long[5];
        int index = 0;

        while (matcher.find()) {
            values[index++] =
                    Long.parseLong(matcher.group(1));
        }

        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static String header(
            HttpResponse<?> response,
            String name
    ) {
        return response.headers()
                .firstValue(name)
                .orElseThrow();
    }

    private static final class ConfiguredServer
            implements AutoCloseable {

        private final AulaFlowHttpServer server;

        private ConfiguredServer(
                AulaFlowHttpServer server
        ) {
            this.server = server;
        }

        private HttpResponse<String> request(
                String method,
                String path,
                boolean authenticated,
                String body,
                String csrfToken,
                String fetchSite
        ) throws Exception {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(
                            URI.create(
                                    "http://127.0.0.1:"
                                            + server
                                            .getAddress()
                                            .getPort()
                                            + path
                            )
                    ).timeout(Duration.ofSeconds(3));

            if (authenticated) {
                builder.header(
                        "Cookie",
                        "AULAFLOW_SESSION="
                                + SESSION_ID.value()
                );
            }

            if (body != null) {
                builder.header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                );
            }

            if (csrfToken != null) {
                builder.header(
                        CsrfProtection.HEADER,
                        csrfToken
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
                            ? HttpRequest.BodyPublishers
                            .noBody()
                            : HttpRequest.BodyPublishers
                            .ofString(body)
            );

            try (
                    HttpClient client =
                            HttpClient.newBuilder()
                                    .followRedirects(
                                            HttpClient
                                            .Redirect.NEVER
                                    )
                                    .build()
            ) {
                return client.send(
                        builder.build(),
                        HttpResponse.BodyHandlers
                                .ofString()
                );
            }
        }

        @Override
        public void close() {
            server.close();
        }
    }

    private static final class
    UnusedAdministratorRepository
            implements AdministratorRepository {

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
            return Optional.empty();
        }
    }

    private static final class UnusedPasswordHasher
            implements PasswordHasher {

        @Override
        public PasswordVerifier hash(char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean verify(
                char[] password,
                PasswordVerifier verifier
        ) {
            return false;
        }

        @Override
        public PasswordVerifier createDummyVerifier() {
            return new PasswordVerifier("dummy");
        }
    }
}
