package es.aulaflow.presentation.csv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.csv.CsvImportPreview;
import es.aulaflow.application.csv.CsvImportService;
import es.aulaflow.application.csv.CsvValidationError;
import es.aulaflow.application.csv.CsvValidationReport;
import es.aulaflow.application.csv.CsvWarning;
import es.aulaflow.application.csv.InvalidImportTokenException;
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
import es.aulaflow.presentation.http.FormUrlEncodedParser;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpResponseWriter;
import es.aulaflow.presentation.http.JsonText;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * API de importación CSV en dos fases:
 * {@code POST /api/v1/boards/import/validate} (cuerpo CSV crudo) y
 * {@code POST /api/v1/boards/import} (confirmación con token).
 */
public final class CsvImportApiHandler
        implements HttpHandler {

    public static final String PATH =
            "/api/v1/boards/import";

    private static final String VALIDATE_PATH =
            PATH + "/validate";

    private final CsvImportService csvImportService;
    private final BoardService boardService;

    public CsvImportApiHandler(
            CsvImportService csvImportService,
            BoardService boardService
    ) {
        this.csvImportService = Objects.requireNonNull(
                csvImportService,
                "El servicio de importación no puede "
                        + "ser null."
        );

        this.boardService = Objects.requireNonNull(
                boardService,
                "El servicio de tableros no puede ser null."
        );
    }

    @Override
    public void handle(
            HttpExchange exchange
    ) throws IOException {
        AuthenticatedSession session =
                (AuthenticatedSession)
                        exchange.getAttribute(
                                AuthenticationRequiredHandler
                                        .SESSION_ATTRIBUTE
                        );

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (VALIDATE_PATH.equals(path)) {
            if (!"POST".equals(method)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange, "POST"
                );
                return;
            }

            handleValidate(exchange, session);
            return;
        }

        if (PATH.equals(path)) {
            if (!"POST".equals(method)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange, "POST"
                );
                return;
            }

            handleConfirm(exchange, session);
            return;
        }

        HttpErrorResponses.notFound(exchange);
    }

    private void handleValidate(
            HttpExchange exchange,
            AuthenticatedSession session
    ) throws IOException {
        if (
                !CsrfProtection.require(
                        exchange,
                        exchange.getRequestHeaders()
                                .getFirst(
                                        CsrfProtection.HEADER
                                )
                )
        ) {
            return;
        }

        CsvImportPreview preview;

        try (
                InputStream body =
                        exchange.getRequestBody()
        ) {
            preview = csvImportService.preview(
                    body,
                    session.id().value(),
                    session.administrator().id()
            );
        }

        int status;

        if (
                preview.report()
                        .hasErrorCode("file_too_large")
        ) {
            status = 413;
        } else {
            status = preview.report().valid() ? 200 : 400;
        }

        sendJson(
                exchange, status, previewJson(preview)
        );
    }

    private void handleConfirm(
            HttpExchange exchange,
            AuthenticatedSession session
    ) throws IOException {
        if (
                !CsrfProtection.require(
                        exchange,
                        exchange.getRequestHeaders()
                                .getFirst(
                                        CsrfProtection.HEADER
                                )
                )
        ) {
            return;
        }

        Map<String, String> fields;

        try {
            fields = FormUrlEncodedParser.parse(exchange);
        } catch (
                FormUrlEncodedParser.InvalidFormException
                        exception
        ) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        String token = fields.get("token");

        if (token == null || token.isBlank()) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        long ownerId = session.administrator().id();

        final BoardId boardId;

        try {
            boardId = csvImportService.confirm(
                    token, session.id().value(), ownerId
            );
        } catch (InvalidImportTokenException exception) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        Board board = boardService.listBoards(ownerId)
                .stream()
                .filter(candidate ->
                        candidate.id().value()
                                == boardId.value()
                )
                .findFirst()
                .orElseThrow();

        exchange.getResponseHeaders().set(
                "Location",
                "/api/v1/boards/" + boardId.value()
        );

        sendJson(
                exchange,
                201,
                """
                {"id":%d,"name":%s}
                """.formatted(
                        boardId.value(),
                        JsonText.quote(
                                board.name().value()
                        )
                ).strip()
        );
    }

    private static String previewJson(
            CsvImportPreview preview
    ) {
        CsvValidationReport report = preview.report();

        return """
                {"valid":%s,"version":1,"boardName":%s,"columns":%d,"cards":%d,"records":%d,"warnings":[%s],"errors":[%s],"token":%s}
                """.formatted(
                report.valid(),
                jsonStringOrNull(report.boardName()),
                report.columnCount(),
                report.cardCount(),
                report.totalRecords(),
                String.join(
                        ",",
                        report.warnings().stream()
                                .map(
                                        CsvImportApiHandler
                                                ::warningJson
                                )
                                .toList()
                ),
                String.join(
                        ",",
                        report.errors().stream()
                                .map(
                                        CsvImportApiHandler
                                                ::errorJson
                                )
                                .toList()
                ),
                preview.token()
                        .map(JsonText::quote)
                        .orElse("null")
        ).strip();
    }

    private static String errorJson(
            CsvValidationError error
    ) {
        return """
                {"record":%d,"field":%s,"code":%s,"message":%s}
                """.formatted(
                error.recordNumber(),
                JsonText.quote(error.field()),
                JsonText.quote(error.code()),
                JsonText.quote(error.message())
        ).strip();
    }

    private static String warningJson(CsvWarning warning) {
        return """
                {"record":%d,"field":%s,"code":%s,"message":%s}
                """.formatted(
                warning.recordNumber(),
                JsonText.quote(warning.field()),
                JsonText.quote(warning.code()),
                JsonText.quote(warning.message())
        ).strip();
    }

    private static String jsonStringOrNull(String value) {
        return value == null
                ? "null"
                : JsonText.quote(value);
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        HttpResponseWriter.sendJson(
                exchange, status, body
        );
    }
}
