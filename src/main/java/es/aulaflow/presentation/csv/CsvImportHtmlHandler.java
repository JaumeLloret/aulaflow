package es.aulaflow.presentation.csv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.csv.CsvImportPreview;
import es.aulaflow.application.csv.CsvImportService;
import es.aulaflow.application.csv.CsvLimits;
import es.aulaflow.application.csv.InvalidImportTokenException;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
import es.aulaflow.presentation.board.BoardHtmlHandler;
import es.aulaflow.presentation.http.FormUrlEncodedParser;
import es.aulaflow.presentation.http.HtmlResponses;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.MultipartFormData;
import es.aulaflow.presentation.http.MultipartFormDataParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Interfaz HTML de importación CSV en dos fases:
 * {@code GET /boards/import} (formulario),
 * {@code POST /boards/import/preview} (multipart, valida y guarda un
 * plan temporal) y {@code POST /boards/import/confirm}
 * (form-urlencoded, confirma con el token).
 */
public final class CsvImportHtmlHandler
        implements HttpHandler {

    public static final String PATH = "/boards/import";

    private static final String PREVIEW_PATH =
            PATH + "/preview";

    private static final String CONFIRM_PATH =
            PATH + "/confirm";

    private static final Set<String> MULTIPART_FIELDS =
            Set.of("_csrf", "file");

    private static final int MAX_MULTIPART_BODY_BYTES =
            CsvLimits.MAX_UPLOAD_BYTES + 8_192;

    private final CsvImportService csvImportService;

    public CsvImportHtmlHandler(
            CsvImportService csvImportService
    ) {
        this.csvImportService = Objects.requireNonNull(
                csvImportService,
                "El servicio de importación no puede "
                        + "ser null."
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

        if (PATH.equals(path)) {
            if (!"GET".equals(method)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange, "GET"
                );
                return;
            }

            HtmlResponses.send(
                    exchange,
                    200,
                    CsvPages.importForm(
                            session.csrfToken()
                    )
            );
            return;
        }

        if (PREVIEW_PATH.equals(path)) {
            if (!"POST".equals(method)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange, "POST"
                );
                return;
            }

            handlePreview(exchange, session);
            return;
        }

        if (CONFIRM_PATH.equals(path)) {
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

    private void handlePreview(
            HttpExchange exchange,
            AuthenticatedSession session
    ) throws IOException {
        MultipartFormData form;

        try {
            form = MultipartFormDataParser.parse(
                    exchange,
                    MULTIPART_FIELDS,
                    MAX_MULTIPART_BODY_BYTES
            );
        } catch (
                MultipartFormDataParser
                        .MultipartTooLargeException
                        exception
        ) {
            HtmlResponses.send(
                    exchange,
                    413,
                    CsvPages.tooLarge(session.csrfToken())
            );
            return;
        } catch (
                MultipartFormDataParser
                        .InvalidMultipartException
                        exception
        ) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        if (
                !CsrfProtection.require(
                        exchange,
                        form.fields().get(
                                CsrfProtection.FORM_FIELD
                        )
                )
        ) {
            return;
        }

        Optional<byte[]> file = form.file();

        if (file.isEmpty()) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        CsvImportPreview preview =
                csvImportService.preview(
                        new ByteArrayInputStream(
                                file.orElseThrow()
                        ),
                        session.id().value(),
                        session.administrator().id()
                );

        if (
                preview.report()
                        .hasErrorCode("file_too_large")
        ) {
            HtmlResponses.send(
                    exchange,
                    413,
                    CsvPages.tooLarge(session.csrfToken())
            );
            return;
        }

        int status = preview.report().valid() ? 200 : 400;

        HtmlResponses.send(
                exchange,
                status,
                CsvPages.previewResult(
                        preview.report(),
                        preview.token(),
                        session.csrfToken()
                )
        );
    }

    private void handleConfirm(
            HttpExchange exchange,
            AuthenticatedSession session
    ) throws IOException {
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

        if (
                !CsrfProtection.require(
                        exchange,
                        fields.get(
                                CsrfProtection.FORM_FIELD
                        )
                )
        ) {
            return;
        }

        String token = fields.get("token");

        if (token == null || token.isBlank()) {
            HttpErrorResponses.badRequest(exchange);
            return;
        }

        try {
            BoardId boardId = csvImportService.confirm(
                    token,
                    session.id().value(),
                    session.administrator().id()
            );

            HtmlResponses.redirect(
                    exchange,
                    BoardHtmlHandler.PATH + "/"
                            + boardId.value()
            );
        } catch (InvalidImportTokenException exception) {
            HttpErrorResponses.badRequest(exchange);
        }
    }
}
