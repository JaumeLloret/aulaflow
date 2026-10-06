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
import es.aulaflow.application.csv.CsvImportService;
import es.aulaflow.application.csv.CsvLimits;
import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import es.aulaflow.infrastructure.csv.InMemoryPendingImportStore;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteAdministratorRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteBoardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCardRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConnectionFactory;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteCsvImportRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteChecklistItemRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteLabelRepository;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteMigrator;
import es.aulaflow.presentation.auth.ApiAuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
import es.aulaflow.presentation.auth.SessionCookie;
import es.aulaflow.presentation.board.BoardApiHandler;
import es.aulaflow.presentation.board.BoardHtmlHandler;
import es.aulaflow.presentation.csv.CsvImportApiHandler;
import es.aulaflow.presentation.csv.CsvImportHtmlHandler;
import es.aulaflow.presentation.http.HttpHandlerPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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

class CsvImportEndpointTest {

    private static final SessionId SESSION_ID =
            new SessionId("S".repeat(43));

    private static final CsrfToken CSRF_TOKEN =
            new CsrfToken("C".repeat(43));

    private static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    private static final String VALID_CSV =
            HEADER + "\r\n"
                    + "1,BOARD,Tauler CSV,,,,,\r\n"
                    + "1,COLUMN,Tauler CSV,Per fer,0,,,\r\n"
                    + "1,CARD,Tauler CSV,Per fer,0,"
                    + "Estudiar,Repassar,0\r\n";

    private static final String INVALID_CSV =
            HEADER + "\r\n"
                    + "1,COLUMN,Tauler CSV,Per fer,0,,,\r\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void htmlImportFormRequiresAuthentication()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("html-auth.db")
        ) {
            assertEquals(
                    303,
                    configured.get(
                            CsvImportHtmlHandler.PATH,
                            false
                    ).statusCode()
            );
        }
    }

    @Test
    void apiValidateRequiresAuthentication()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-auth.db")
        ) {
            HttpResponse<String> response =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            false,
                            VALID_CSV,
                            null
                    );

            assertEquals(401, response.statusCode());
            assertTrue(
                    response.body().contains(
                            "AUTHENTICATION_REQUIRED"
                    )
            );
        }
    }

    @Test
    void htmlImportFormShowsCsrfHiddenField()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("html-form.db")
        ) {
            HttpResponse<String> response = configured.get(
                    CsvImportHtmlHandler.PATH, true
            );

            assertEquals(200, response.statusCode());
            assertTrue(
                    response.body()
                            .contains("name=\"_csrf\"")
            );
            assertTrue(
                    response.body().contains(
                            CSRF_TOKEN.value()
                    )
            );
            assertTrue(
                    response.body().contains(
                            "name=\"file\""
                    )
            );
        }
    }

    @Test
    void htmlPreviewValidCsvReturnsTokenAndSummary()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("html-preview-ok.db")
        ) {
            HttpResponse<String> response =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            VALID_CSV.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "same-origin"
                    );

            assertEquals(200, response.statusCode());
            assertTrue(
                    response.body().contains(
                            "name=\"token\""
                    )
            );
            assertTrue(
                    response.body()
                            .contains("Tauler CSV")
            );
        }
    }

    @Test
    void htmlPreviewInvalidCsvReturns400WithoutToken()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("html-preview-bad.db")
        ) {
            HttpResponse<String> response =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            INVALID_CSV.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "same-origin"
                    );

            assertEquals(400, response.statusCode());
            assertFalse(
                    response.body().contains(
                            "name=\"token\""
                    )
            );
        }
    }

    @Test
    void htmlPreviewMissingFileReturns400()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-nofile.db"
                        )
        ) {
            byte[] body = multipartBodyWithoutFile(
                    "BOUNDARY", CSRF_TOKEN.value()
            );

            HttpResponse<String> response =
                    configured.postRawMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            "BOUNDARY",
                            body,
                            "same-origin"
                    );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void htmlPreviewDuplicateFileFieldReturns400()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-dupfile.db"
                        )
        ) {
            byte[] body = multipartBodyWithDuplicateFile(
                    "BOUNDARY",
                    CSRF_TOKEN.value(),
                    VALID_CSV.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            HttpResponse<String> response =
                    configured.postRawMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            "BOUNDARY",
                            body,
                            "same-origin"
                    );

            assertEquals(400, response.statusCode());
        }
    }

    @Test
    void htmlPreviewOversizedMultipartReturns413()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-413.db"
                        )
        ) {
            byte[] hugeFile = new byte[
                    CsvLimits.MAX_UPLOAD_BYTES + 50_000
                    ];

            HttpResponse<String> response =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            hugeFile,
                            "same-origin"
                    );

            assertEquals(413, response.statusCode());
        }
    }

    @Test
    void htmlPreviewFileOverCsvLimitButUnderMultipartCapReturns413()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-413b.db"
                        )
        ) {
            byte[] slightlyOversizedFile = new byte[
                    CsvLimits.MAX_UPLOAD_BYTES + 1
                    ];

            HttpResponse<String> response =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            slightlyOversizedFile,
                            "same-origin"
                    );

            assertEquals(413, response.statusCode());
        }
    }

    @Test
    void htmlPreviewInvalidCsrfReturns403()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-csrf.db"
                        )
        ) {
            HttpResponse<String> response =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            "wrong-token-wrong-token-"
                                    + "wrong-token-wrong12",
                            VALID_CSV.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "same-origin"
                    );

            assertEquals(403, response.statusCode());
        }
    }

    @Test
    void htmlConfirmCreatesBoardAndRedirects()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-confirm-ok.db"
                        )
        ) {
            HttpResponse<String> preview =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            VALID_CSV.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "same-origin"
                    );

            String token = extractToken(preview.body());

            HttpResponse<String> confirm =
                    configured.post(
                            CsvImportHtmlHandler.PATH
                                    + "/confirm",
                            true,
                            "_csrf="
                                    + encode(
                                    CSRF_TOKEN.value()
                            ) + "&token="
                                    + encode(token),
                            null,
                            "same-origin"
                    );

            assertEquals(303, confirm.statusCode());

            String location = header(confirm, "Location");
            assertTrue(
                    location.startsWith("/boards/")
            );

            HttpResponse<String> board = configured.get(
                    location, true
            );

            assertEquals(200, board.statusCode());
            assertTrue(
                    board.body().contains("Tauler CSV")
            );
            assertTrue(
                    board.body().contains("Estudiar")
            );
        }
    }

    @Test
    void htmlConfirmWithUnknownTokenReturns400()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-confirm-bad.db"
                        )
        ) {
            HttpResponse<String> confirm =
                    configured.post(
                            CsvImportHtmlHandler.PATH
                                    + "/confirm",
                            true,
                            "_csrf="
                                    + encode(
                                    CSRF_TOKEN.value()
                            ) + "&token=unknown-token",
                            null,
                            "same-origin"
                    );

            assertEquals(400, confirm.statusCode());
        }
    }

    @Test
    void htmlImportPathRejectsPostWithAllowGet()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-import-405.db"
                        )
        ) {
            HttpResponse<String> response =
                    configured.post(
                            CsvImportHtmlHandler.PATH,
                            true,
                            "",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(405, response.statusCode());
            assertEquals(
                    "GET",
                    header(response, "Allow")
            );
        }
    }

    @Test
    void htmlPreviewPathRejectsGetWithAllowPost()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-preview-405.db"
                        )
        ) {
            HttpResponse<String> response =
                    configured.get(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true
                    );

            assertEquals(405, response.statusCode());
            assertEquals(
                    "POST",
                    header(response, "Allow")
            );
        }
    }

    @Test
    void htmlImportUnknownSubpathReturns404()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "html-import-404.db"
                        )
        ) {
            HttpResponse<String> response = configured.get(
                    CsvImportHtmlHandler.PATH
                            + "/unknown",
                    true
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void apiValidateValidCsvReturnsTokenInJson()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-validate-ok.db")
        ) {
            HttpResponse<String> response =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            VALID_CSV,
                            CSRF_TOKEN.value()
                    );

            assertEquals(200, response.statusCode());
            assertTrue(
                    response.body()
                            .contains("\"valid\":true")
            );
            assertTrue(
                    response.body()
                            .contains("\"token\":\"")
            );
        }
    }

    @Test
    void apiValidateInvalidCsvReturns400Json()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "api-validate-bad.db"
                        )
        ) {
            HttpResponse<String> response =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            INVALID_CSV,
                            CSRF_TOKEN.value()
                    );

            assertEquals(400, response.statusCode());
            assertTrue(
                    response.body()
                            .contains("\"valid\":false")
            );
            assertTrue(
                    response.body()
                            .contains("\"token\":null")
            );
        }
    }

    @Test
    void apiValidateOversizedCsvReturns413()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "api-validate-413.db"
                        )
        ) {
            String huge = HEADER + "\r\n"
                    + "1,BOARD,"
                    + "x".repeat(
                    CsvLimits.MAX_UPLOAD_BYTES + 10
            ) + ",,,,,\r\n";

            HttpResponse<String> response =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            huge,
                            CSRF_TOKEN.value()
                    );

            assertEquals(413, response.statusCode());
        }
    }

    @Test
    void apiValidateInvalidCsrfReturns403()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer(
                                "api-validate-csrf.db"
                        )
        ) {
            HttpResponse<String> response =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            VALID_CSV,
                            "wrong-token-wrong-token-"
                                    + "wrong-token-wrong12"
                    );

            assertEquals(403, response.statusCode());
        }
    }

    @Test
    void apiConfirmCreatesBoardWithLocationAnd201()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-confirm-ok.db")
        ) {
            HttpResponse<String> validate =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            VALID_CSV,
                            CSRF_TOKEN.value()
                    );

            String token = extractJsonToken(
                    validate.body()
            );

            HttpResponse<String> confirm =
                    configured.post(
                            "/api/v1/boards/import",
                            true,
                            "token=" + encode(token),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(201, confirm.statusCode());
            assertTrue(
                    header(confirm, "Location")
                            .startsWith(
                                    "/api/v1/boards/"
                            )
            );
            assertTrue(
                    confirm.body()
                            .contains("Tauler CSV")
            );
        }
    }

    @Test
    void apiConfirmWithUnknownTokenReturns400()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-confirm-bad.db")
        ) {
            HttpResponse<String> confirm =
                    configured.post(
                            "/api/v1/boards/import",
                            true,
                            "token=unknown-token",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(400, confirm.statusCode());
        }
    }

    @Test
    void apiValidatePathRejectsGetWithAllowPost()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-import-405.db")
        ) {
            HttpResponse<String> response = configured.get(
                    "/api/v1/boards/import/validate", true
            );

            assertEquals(405, response.statusCode());
            assertEquals(
                    "POST",
                    header(response, "Allow")
            );
        }
    }

    @Test
    void apiImportUnknownSubpathReturns404()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-import-404.db")
        ) {
            HttpResponse<String> response = configured.get(
                    "/api/v1/boards/import/unknown", true
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void exportCsvDownloadsAttachmentWithSafeHeaders()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("export-ok.db")
        ) {
            HttpResponse<String> preview =
                    configured.postMultipart(
                            CsvImportHtmlHandler.PATH
                                    + "/preview",
                            true,
                            CSRF_TOKEN.value(),
                            VALID_CSV.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "same-origin"
                    );

            String token = extractToken(preview.body());

            HttpResponse<String> confirm =
                    configured.post(
                            CsvImportHtmlHandler.PATH
                                    + "/confirm",
                            true,
                            "_csrf="
                                    + encode(
                                    CSRF_TOKEN.value()
                            ) + "&token="
                                    + encode(token),
                            null,
                            "same-origin"
                    );

            String location = header(confirm, "Location");

            HttpResponse<String> export = configured.get(
                    location + "/export.csv", true
            );

            assertEquals(200, export.statusCode());
            assertEquals(
                    "text/csv; charset=UTF-8",
                    header(export, "Content-Type")
            );
            assertEquals(
                    "no-store",
                    header(export, "Cache-Control")
            );
            assertTrue(
                    header(
                            export, "Content-Disposition"
                    ).startsWith(
                            "attachment; filename=\""
                                    + "aulaflow-board-"
                    )
            );
            assertTrue(
                    export.body().contains("Tauler CSV")
            );
            assertTrue(
                    export.body().contains("Estudiar")
            );
        }
    }

    @Test
    void exportCsvOfForeignOrMissingBoardReturns404()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("export-404.db")
        ) {
            HttpResponse<String> response = configured.get(
                    "/boards/999999/export.csv", true
            );

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void exportCsvRejectsPostWithAllowGet()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("export-405.db")
        ) {
            HttpResponse<String> response =
                    configured.post(
                            "/boards/999999/export.csv",
                            true,
                            "",
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            assertEquals(405, response.statusCode());
            assertEquals(
                    "GET",
                    header(response, "Allow")
            );
        }
    }

    @Test
    void apiExportCsvDownloadsAttachment()
            throws Exception {
        try (
                ConfiguredServer configured =
                        createServer("api-export-ok.db")
        ) {
            HttpResponse<String> preview =
                    configured.postCsv(
                            "/api/v1/boards/import/validate",
                            true,
                            VALID_CSV,
                            CSRF_TOKEN.value()
                    );

            String token = extractJsonToken(
                    preview.body()
            );

            HttpResponse<String> confirm =
                    configured.post(
                            "/api/v1/boards/import",
                            true,
                            "token=" + encode(token),
                            CSRF_TOKEN.value(),
                            "same-origin"
                    );

            long boardId = Long.parseLong(
                    header(confirm, "Location")
                            .substring(
                                    "/api/v1/boards/"
                                            .length()
                            )
            );

            HttpResponse<String> export = configured.get(
                    "/api/v1/boards/" + boardId
                            + "/export.csv",
                    true
            );

            assertEquals(200, export.statusCode());
            assertEquals(
                    "text/csv; charset=UTF-8",
                    header(export, "Content-Type")
            );
            assertTrue(
                    header(
                            export, "Content-Disposition"
                    ).contains(
                            "aulaflow-board-" + boardId
                    )
            );
        }
    }

    private static String extractToken(String html) {
        Matcher matcher = Pattern.compile(
                "name=\"token\" value=\"([^\"]+)\""
        ).matcher(html);

        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String extractJsonToken(String json) {
        Matcher matcher = Pattern.compile(
                "\"token\":\"([^\"]+)\""
        ).matcher(json);

        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static byte[] multipartBodyWithoutFile(
            String boundary,
            String csrfToken
    ) throws Exception {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeTextPart(out, boundary, "_csrf", csrfToken);
        writeClosing(out, boundary);

        return out.toByteArray();
    }

    private static byte[] multipartBodyWithDuplicateFile(
            String boundary,
            String csrfToken,
            byte[] fileBytes
    ) throws Exception {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeTextPart(out, boundary, "_csrf", csrfToken);
        writeFilePart(out, boundary, fileBytes);
        writeFilePart(out, boundary, fileBytes);
        writeClosing(out, boundary);

        return out.toByteArray();
    }

    private static byte[] multipartBody(
            String boundary,
            String csrfToken,
            byte[] fileBytes
    ) throws Exception {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeTextPart(out, boundary, "_csrf", csrfToken);
        writeFilePart(out, boundary, fileBytes);
        writeClosing(out, boundary);

        return out.toByteArray();
    }

    private static void writeTextPart(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String value
    ) throws Exception {
        out.write(
                ("--" + boundary + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII)
        );
        out.write(
                ("Content-Disposition: form-data; "
                        + "name=\"" + name + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII)
        );
        out.write(
                value.getBytes(StandardCharsets.UTF_8)
        );
        out.write(
                "\r\n".getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void writeFilePart(
            ByteArrayOutputStream out,
            String boundary,
            byte[] fileBytes
    ) throws Exception {
        out.write(
                ("--" + boundary + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII)
        );
        out.write(
                ("Content-Disposition: form-data; "
                        + "name=\"file\"; "
                        + "filename=\"board.csv\"\r\n"
                        + "Content-Type: text/csv\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII)
        );
        out.write(fileBytes);
        out.write(
                "\r\n".getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void writeClosing(
            ByteArrayOutputStream out,
            String boundary
    ) throws Exception {
        out.write(
                ("--" + boundary + "--\r\n")
                        .getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value, StandardCharsets.UTF_8
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

    private ConfiguredServer createServer(
            String databaseName
    ) throws Exception {
        Path databasePath =
                temporaryDirectory.resolve(databaseName);

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        SqliteConfig.from(
                                Map.of(
                                        "AULAFLOW_DB_PATH",
                                        databasePath
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
                                "profesora.csv"
                        ),
                        new PasswordVerifier("test")
                );

        InMemorySessionStore sessionStore =
                new InMemorySessionStore();

        Instant now =
                Instant.parse("2026-08-03T00:00:00Z");

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
                new SqliteBoardRepository(
                        connectionFactory
                )
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        CsvExportService csvExportService =
                new CsvExportService(
                        boardService, cardService
                );

        CsvImportService csvImportService =
                new CsvImportService(
                        new InMemoryPendingImportStore(),
                        new SqliteCsvImportRepository(
                                connectionFactory
                        )
                );

        AulaFlowHttpServer server =
                AulaFlowHttpServer.create(
                        new InetSocketAddress(
                                "127.0.0.1", 0
                        )
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
                                        new LabelService(
                                                new SqliteLabelRepository(
                                                        connectionFactory
                                                )
                                        ),
                                        new ChecklistService(
                                                new SqliteChecklistItemRepository(
                                                        connectionFactory
                                                )
                                        ),
                                        csvExportService
                                )
                        )
                )
        );

        server.registerContext(
                CsvImportHtmlHandler.PATH,
                HttpHandlerPipeline.standard(
                        new AuthenticationRequiredHandler(
                                CsvImportHtmlHandler.PATH,
                                true,
                                authenticationService,
                                cookie,
                                new CsvImportHtmlHandler(
                                        csvImportService
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
                                        new LabelService(
                                                new SqliteLabelRepository(
                                                        connectionFactory
                                                )
                                        ),
                                        new ChecklistService(
                                                new SqliteChecklistItemRepository(
                                                        connectionFactory
                                                )
                                        ),
                                        csvExportService
                                )
                        )
                )
        );

        server.registerContext(
                CsvImportApiHandler.PATH,
                HttpHandlerPipeline.standard(
                        new ApiAuthenticationRequiredHandler(
                                CsvImportApiHandler.PATH,
                                authenticationService,
                                cookie,
                                new CsvImportApiHandler(
                                        csvImportService,
                                        boardService
                                )
                        )
                )
        );

        server.start();
        return new ConfiguredServer(server);
    }

    private static final class ConfiguredServer
            implements AutoCloseable {

        private final AulaFlowHttpServer server;

        private ConfiguredServer(
                AulaFlowHttpServer server
        ) {
            this.server = server;
        }

        private HttpResponse<String> get(
                String path,
                boolean authenticated
        ) throws Exception {
            return send(
                    "GET",
                    path,
                    authenticated,
                    null,
                    null,
                    null,
                    null
            );
        }

        private HttpResponse<String> post(
                String path,
                boolean authenticated,
                String urlEncodedBody,
                String csrfToken,
                String fetchSite
        ) throws Exception {
            return send(
                    "POST",
                    path,
                    authenticated,
                    "application/x-www-form-urlencoded",
                    urlEncodedBody.getBytes(
                            StandardCharsets.UTF_8
                    ),
                    csrfToken,
                    fetchSite
            );
        }

        private HttpResponse<String> postCsv(
                String path,
                boolean authenticated,
                String csvBody,
                String csrfToken
        ) throws Exception {
            return send(
                    "POST",
                    path,
                    authenticated,
                    "text/csv; charset=UTF-8",
                    csvBody.getBytes(
                            StandardCharsets.UTF_8
                    ),
                    csrfToken,
                    "same-origin"
            );
        }

        private HttpResponse<String> postMultipart(
                String path,
                boolean authenticated,
                String csrfToken,
                byte[] fileBytes,
                String fetchSite
        ) throws Exception {
            byte[] body = multipartBody(
                    "AULAFLOWBOUNDARY",
                    csrfToken,
                    fileBytes
            );

            return postRawMultipart(
                    path,
                    authenticated,
                    "AULAFLOWBOUNDARY",
                    body,
                    fetchSite
            );
        }

        private HttpResponse<String> postRawMultipart(
                String path,
                boolean authenticated,
                String boundary,
                byte[] body,
                String fetchSite
        ) throws Exception {
            return send(
                    "POST",
                    path,
                    authenticated,
                    "multipart/form-data; boundary="
                            + boundary,
                    body,
                    null,
                    fetchSite
            );
        }

        private HttpResponse<String> send(
                String method,
                String path,
                boolean authenticated,
                String contentType,
                byte[] body,
                String csrfHeaderToken,
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
                    ).timeout(Duration.ofSeconds(10));

            if (authenticated) {
                builder.header(
                        "Cookie",
                        "AULAFLOW_SESSION="
                                + SESSION_ID.value()
                );
            }

            if (contentType != null) {
                builder.header(
                        "Content-Type", contentType
                );
            }

            if (csrfHeaderToken != null) {
                builder.header(
                        CsrfProtection.HEADER,
                        csrfHeaderToken
                );
            }

            if (fetchSite != null) {
                builder.header(
                        "Sec-Fetch-Site", fetchSite
                );
            }

            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers
                            .noBody()
                            : HttpRequest.BodyPublishers
                            .ofByteArray(body)
            );

            try (
                    HttpClient client =
                            HttpClient.newBuilder()
                                    .followRedirects(
                                            HttpClient
                                                    .Redirect
                                                    .NEVER
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
