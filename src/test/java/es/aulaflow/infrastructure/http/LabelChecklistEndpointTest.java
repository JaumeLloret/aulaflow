package es.aulaflow.infrastructure.http;

import es.aulaflow.application.auth.AdministratorRepository;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.AuthenticationService;
import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.application.auth.PasswordHasher;
import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.auth.SessionId;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelChecklistEndpointTest {

    private static final SessionId SESSION_ID =
            new SessionId("S".repeat(43));

    private static final CsrfToken CSRF_TOKEN =
            new CsrfToken("C".repeat(43));

    private static final long OWNER = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsRenamesAndDeletesLabelOverApi()
            throws Exception {
        try (
                Fixture fixture = createFixture("labels-api.db")
        ) {
            HttpResponse<String> created = fixture.server.request(
                    "POST",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("Urgent")
                            + "&color=red",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, created.statusCode());
            assertTrue(
                    created.body().contains(
                            "\"name\":\"Urgent\""
                    )
            );
            assertTrue(
                    created.body().contains("\"color\":\"red\"")
            );

            long labelId = extractIdBeforeField(
                    created.body(),
                    "\"name\":\"Urgent\""
            );

            HttpResponse<String> updated = fixture.server.request(
                    "PATCH",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels/" + labelId,
                    true,
                    "name=" + encode("Molt urgent")
                            + "&color=purple",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, updated.statusCode());
            assertTrue(
                    updated.body().contains(
                            "\"name\":\"Molt urgent\""
                    )
            );

            HttpResponse<String> deleted = fixture.server.request(
                    "DELETE",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels/" + labelId,
                    true,
                    null,
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(204, deleted.statusCode());

            HttpResponse<String> board = fixture.server.request(
                    "GET",
                    BoardApiHandler.PATH + "/" + fixture.boardId,
                    true,
                    null,
                    null,
                    null
            );

            assertFalse(
                    board.body().contains("Molt urgent")
            );
        }
    }

    @Test
    void rejectsDuplicateLabelNameInSameBoard()
            throws Exception {
        try (
                Fixture fixture = createFixture("labels-dup.db")
        ) {
            fixture.server.request(
                    "POST",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("Urgent") + "&color=red",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            HttpResponse<String> duplicate = fixture.server.request(
                    "POST",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("urgent") + "&color=blue",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(400, duplicate.statusCode());
        }
    }

    @Test
    void rejectsInvalidColorKey() throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-color.db")
        ) {
            HttpResponse<String> response = fixture.server.request(
                    "POST",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("Urgent")
                            + "&color=crimson",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void assignsAndUnassignsLabelToCard() throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-assign.db")
        ) {
            long labelId = createLabel(
                    fixture, "Urgent", "red"
            );

            HttpResponse<String> assigned = fixture.server.request(
                    "PUT",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/cards/" + fixture.cardId
                            + "/labels/" + labelId,
                    true,
                    "",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, assigned.statusCode());
            assertTrue(
                    assigned.body().contains(
                            "\"name\":\"Urgent\""
                    )
            );

            HttpResponse<String> repeated = fixture.server.request(
                    "PUT",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/cards/" + fixture.cardId
                            + "/labels/" + labelId,
                    true,
                    "",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(
                    200,
                    repeated.statusCode(),
                    "Asignar dos veces debe ser idempotente."
            );

            HttpResponse<String> unassigned =
                    fixture.server.request(
                            "DELETE",
                            BoardApiHandler.PATH
                                    + "/" + fixture.boardId
                                    + "/cards/" + fixture.cardId
                                    + "/labels/" + labelId,
                            true,
                            null,
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(204, unassigned.statusCode());
        }
    }

    @Test
    void rejectsAssigningLabelFromAnotherBoard()
            throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-foreign.db")
        ) {
            BoardDetails otherBoard =
                    fixture.boardRepository.create(
                            OWNER,
                            new BoardName("Altre tauler"),
                            List.of()
                    );

            long foreignLabelId = fixture.labelService
                    .createLabel(
                            OWNER,
                            otherBoard.board().id().value(),
                            "Foreign",
                            "red"
                    )
                    .id()
                    .value();

            HttpResponse<String> response = fixture.server.request(
                    "PUT",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/cards/" + fixture.cardId
                            + "/labels/" + foreignLabelId,
                    true,
                    "",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void labelCreationRequiresCsrfToken() throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-csrf.db")
        ) {
            HttpResponse<String> response = fixture.server.request(
                    "POST",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("Urgent") + "&color=red",
                    null,
                    "same-origin"
            );

            assertEquals(403, response.statusCode());
        }
    }

    @Test
    void createsEditsTogglesMovesAndDeletesChecklistItem()
            throws Exception {
        try (
                Fixture fixture =
                        createFixture("checklist-api.db")
        ) {
            String basePath = BoardApiHandler.PATH
                    + "/" + fixture.boardId
                    + "/cards/" + fixture.cardId
                    + "/checklist-items";

            HttpResponse<String> firstCreated =
                    fixture.server.request(
                            "POST",
                            basePath,
                            true,
                            "text=" + encode("Primer"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(201, firstCreated.statusCode());

            long firstId = extractIdBeforeField(
                    firstCreated.body(),
                    "\"text\":\"Primer\""
            );

            HttpResponse<String> secondCreated =
                    fixture.server.request(
                            "POST",
                            basePath,
                            true,
                            "text=" + encode("Segon"),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            long secondId = extractIdBeforeField(
                    secondCreated.body(),
                    "\"text\":\"Segon\""
            );

            HttpResponse<String> edited = fixture.server.request(
                    "PATCH",
                    basePath + "/" + firstId,
                    true,
                    "text=" + encode("Primer actualitzat"),
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, edited.statusCode());
            assertTrue(
                    edited.body().contains(
                            "Primer actualitzat"
                    )
            );

            HttpResponse<String> toggled = fixture.server.request(
                    "PUT",
                    basePath + "/" + firstId + "/completed",
                    true,
                    "completed=true",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, toggled.statusCode());
            assertTrue(
                    toggled.body().contains(
                            "\"completed\":true"
                    )
            );

            HttpResponse<String> moved = fixture.server.request(
                    "PUT",
                    basePath + "/" + firstId + "/position",
                    true,
                    "targetPosition=1",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(200, moved.statusCode());

            HttpResponse<String> board = fixture.server.request(
                    "GET",
                    BoardApiHandler.PATH + "/" + fixture.boardId,
                    true,
                    null,
                    null,
                    null
            );

            assertTrue(
                    board.body().contains(
                            "\"completedItems\":1"
                    )
            );

            HttpResponse<String> deleted = fixture.server.request(
                    "DELETE",
                    basePath + "/" + secondId,
                    true,
                    null,
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(204, deleted.statusCode());

            HttpResponse<String> boardAfterDelete =
                    fixture.server.request(
                            "GET",
                            BoardApiHandler.PATH
                                    + "/" + fixture.boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertTrue(
                    boardAfterDelete.body().contains(
                            "\"totalItems\":1"
                    )
            );
        }
    }

    @Test
    void rejectsInvalidChecklistPosition() throws Exception {
        try (
                Fixture fixture =
                        createFixture("checklist-invalid.db")
        ) {
            String basePath = BoardApiHandler.PATH
                    + "/" + fixture.boardId
                    + "/cards/" + fixture.cardId
                    + "/checklist-items";

            HttpResponse<String> created = fixture.server.request(
                    "POST",
                    basePath,
                    true,
                    "text=" + encode("Unic"),
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            long itemId = extractIdBeforeField(
                    created.body(),
                    "\"text\":\"Unic\""
            );

            HttpResponse<String> response = fixture.server.request(
                    "PUT",
                    basePath + "/" + itemId + "/position",
                    true,
                    "targetPosition=5",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void htmlEscapesLabelAndChecklistTextContent()
            throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-escape.db")
        ) {
            fixture.server.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + fixture.boardId
                            + "/labels",
                    true,
                    "name=" + encode("<b>Urgent</b>")
                            + "&color=red&_csrf="
                            + CSRF_TOKEN.value(),
                    null,
                    "same-origin"
            );

            fixture.server.request(
                    "POST",
                    BoardHtmlHandler.PATH + "/" + fixture.boardId
                            + "/cards/" + fixture.cardId
                            + "/checklist-items",
                    true,
                    "text=" + encode("<script>x</script>")
                            + "&_csrf=" + CSRF_TOKEN.value(),
                    null,
                    "same-origin"
            );

            HttpResponse<String> boardPage = fixture.server.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + fixture.boardId,
                    true,
                    null,
                    null,
                    null
            );

            assertFalse(
                    boardPage.body().contains("<b>Urgent</b>"),
                    "El nombre de la etiqueta debe escaparse."
            );

            assertFalse(
                    boardPage.body().contains(
                            "<script>x</script>"
                    ),
                    "El texto del elemento debe escaparse."
            );

            assertTrue(
                    boardPage.body().contains(
                            "&lt;b&gt;Urgent&lt;/b&gt;"
                    )
            );
        }
    }

    @Test
    void labelToggleButtonsHaveAccessibleActionNameWithoutJavaScript()
            throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-accessible-name.db")
        ) {
            long labelId = createLabel(
                    fixture, "Urgent", "red"
            );

            fixture.server.request(
                    "PUT",
                    BoardApiHandler.PATH + "/" + fixture.boardId
                            + "/cards/" + fixture.cardId
                            + "/labels/" + labelId,
                    true,
                    "",
                    CSRF_TOKEN.value(),
                    "same-origin"
            );

            HttpResponse<String> boardPage = fixture.server.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + fixture.boardId,
                    true,
                    null,
                    null,
                    null
            );

            String html = boardPage.body();

            assertTrue(
                    html.contains("Retirar: Urgent"),
                    "El botón de retirar debe mostrar el verbo de "
                            + "la acción junto al nombre de la "
                            + "etiqueta incluso sin JavaScript. "
                            + "HTML: " + html
            );

            long otherLabelId = createLabel(
                    fixture, "Revisió", "blue"
            );

            HttpResponse<String> boardPageAfterCreate =
                    fixture.server.request(
                            "GET",
                            BoardHtmlHandler.PATH
                                    + "/" + fixture.boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertTrue(
                    boardPageAfterCreate.body().contains(
                            "Assignar: Revisió"
                    ),
                    "El botón de asignar debe mostrar el verbo de "
                            + "la acción junto al nombre de la "
                            + "etiqueta incluso sin JavaScript."
            );

            assertTrue(otherLabelId > 0);
        }
    }

    @Test
    void htmlCreatesLabelAndChecklistItem() throws Exception {
        try (
                Fixture fixture =
                        createFixture("labels-html.db")
        ) {
            HttpResponse<String> labelCreated =
                    fixture.server.request(
                            "POST",
                            BoardHtmlHandler.PATH
                                    + "/" + fixture.boardId
                                    + "/labels",
                            true,
                            "name=" + encode("Urgent")
                                    + "&color=red&_csrf="
                                    + CSRF_TOKEN.value(),
                            null,
                            "same-origin"
                    );

            assertEquals(303, labelCreated.statusCode());

            HttpResponse<String> boardPage = fixture.server.request(
                    "GET",
                    BoardHtmlHandler.PATH + "/" + fixture.boardId,
                    true,
                    null,
                    null,
                    null
            );

            assertTrue(boardPage.body().contains("Urgent"));
            assertTrue(
                    boardPage.body().contains("label-chip--red")
            );

            HttpResponse<String> itemCreated =
                    fixture.server.request(
                            "POST",
                            BoardHtmlHandler.PATH
                                    + "/" + fixture.boardId
                                    + "/cards/" + fixture.cardId
                                    + "/checklist-items",
                            true,
                            "text=" + encode("Revisar")
                                    + "&_csrf="
                                    + CSRF_TOKEN.value(),
                            null,
                            "same-origin"
                    );

            assertEquals(303, itemCreated.statusCode());

            HttpResponse<String> boardPageWithItem =
                    fixture.server.request(
                            "GET",
                            BoardHtmlHandler.PATH
                                    + "/" + fixture.boardId,
                            true,
                            null,
                            null,
                            null
                    );

            assertTrue(
                    boardPageWithItem.body()
                            .contains("Revisar")
            );
        }
    }

    private long createLabel(
            Fixture fixture,
            String name,
            String color
    ) throws Exception {
        HttpResponse<String> response = fixture.server.request(
                "POST",
                BoardApiHandler.PATH + "/" + fixture.boardId
                        + "/labels",
                true,
                "name=" + encode(name) + "&color=" + color,
                CSRF_TOKEN.value(),
                "same-origin"
        );

        return extractIdBeforeField(
                response.body(),
                "\"name\":\"" + name + "\""
        );
    }

    private static long extractIdBeforeField(
            String json,
            String fieldFragment
    ) {
        Matcher matcher = Pattern.compile(
                "\"id\":(\\d+),"
                        + Pattern.quote(fieldFragment)
        ).matcher(json);

        assertTrue(
                matcher.find(),
                "No se encontró el fragmento esperado en: "
                        + json
        );

        return Long.parseLong(matcher.group(1));
    }

    private Fixture createFixture(String databaseName)
            throws Exception {
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
                        new AdministratorUsername("teacher"),
                        new PasswordVerifier("test")
                );

        InMemorySessionStore sessionStore =
                new InMemorySessionStore();

        Instant now = Instant.parse("2026-08-02T00:00:00Z");

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

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        LabelService labelService = new LabelService(
                new SqliteLabelRepository(connectionFactory)
        );

        ChecklistService checklistService = new ChecklistService(
                new SqliteChecklistItemRepository(
                        connectionFactory
                )
        );

        BoardDetails board = boardService.createBoard(
                OWNER,
                "Tauler de prova"
        );

        long boardId = board.board().id().value();
        long columnId = board.columns().get(0).id().value();

        long cardId = cardService.createCard(
                OWNER,
                boardId,
                columnId,
                "Targeta de prova",
                ""
        ).id().value();

        AulaFlowHttpServer server = AulaFlowHttpServer.create(
                new InetSocketAddress("127.0.0.1", 0)
        );

        SessionCookie cookie = new SessionCookie(false);

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

        return new Fixture(
                new ConfiguredServer(server),
                boardId,
                cardId,
                new SqliteBoardRepository(connectionFactory),
                labelService
        );
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private record Fixture(
            ConfiguredServer server,
            long boardId,
            long cardId,
            BoardRepository boardRepository,
            LabelService labelService
    ) implements AutoCloseable {

        @Override
        public void close() {
            server.close();
        }
    }

    private static final class ConfiguredServer
            implements AutoCloseable {

        private final AulaFlowHttpServer server;

        private ConfiguredServer(AulaFlowHttpServer server) {
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
                                            + server.getAddress()
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
                builder.header("Sec-Fetch-Site", fetchSite);
            }

            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers
                            .ofString(body)
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
            server.close();
        }
    }

    private static final class UnusedAdministratorRepository
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
        public Optional<StoredAdministrator> findByUsername(
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
                PasswordVerifier passwordVerifier
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PasswordVerifier createDummyVerifier() {
            return new PasswordVerifier("dummy");
        }
    }
}
