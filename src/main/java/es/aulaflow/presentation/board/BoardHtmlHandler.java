package es.aulaflow.presentation.board;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.board.BoardNotFoundException;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardNotFoundException;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistItemNotFoundException;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.DuplicateLabelNameException;
import es.aulaflow.application.board.InvalidColumnOrderException;
import es.aulaflow.application.board.LabelNotFoundException;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.application.csv.CsvExportDocument;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
import es.aulaflow.presentation.http.FormUrlEncodedParser;
import es.aulaflow.presentation.http.HtmlResponses;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpResponseWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoardHtmlHandler
        implements HttpHandler {

    public static final String PATH = "/boards";

    private final BoardService boardService;
    private final CardService cardService;
    private final LabelService labelService;
    private final ChecklistService checklistService;
    private final CsvExportService csvExportService;

    public BoardHtmlHandler(
            BoardService boardService,
            CardService cardService,
            LabelService labelService,
            ChecklistService checklistService,
            CsvExportService csvExportService
    ) {
        this.boardService = Objects.requireNonNull(
                boardService
        );
        this.cardService = Objects.requireNonNull(
                cardService
        );
        this.labelService = Objects.requireNonNull(
                labelService
        );
        this.checklistService = Objects.requireNonNull(
                checklistService
        );
        this.csvExportService = Objects.requireNonNull(
                csvExportService
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

        String path =
                exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (PATH.equals(path)) {
                handleCollection(
                        exchange,
                        method,
                        session
                );
                return;
            }

            String[] segments =
                    path.substring(PATH.length() + 1)
                            .split("/");

            if (
                    segments.length < 1
                            || segments[0].isBlank()
            ) {
                HttpErrorResponses.notFound(exchange);
                return;
            }

            long boardId = positiveId(segments[0]);

            if (
                    segments.length == 1
                            && "GET".equals(method)
            ) {
                showBoard(
                        exchange,
                        session,
                        boardId
                );
                return;
            }

            if (
                    segments.length == 2
                            && "export.csv"
                                    .equals(segments[1])
            ) {
                if (!"GET".equals(method)) {
                    HttpErrorResponses.methodNotAllowed(
                            exchange, "GET"
                    );
                    return;
                }

                exportCsv(exchange, session, boardId);
                return;
            }

            if (!"POST".equals(method)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange,
                        segments.length == 1
                                ? "GET"
                                : "POST"
                );
                return;
            }

            Map<String, String> fields =
                    FormUrlEncodedParser.parse(exchange);

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

            mutate(
                    exchange,
                    session,
                    boardId,
                    segments,
                    fields
            );
        } catch (
                IllegalArgumentException
                        | InvalidColumnOrderException
                        | DuplicateLabelNameException
                        | FormUrlEncodedParser
                        .InvalidFormException exception
        ) {
            HttpErrorResponses.badRequest(exchange);
        } catch (
                BoardNotFoundException
                        | CardNotFoundException
                        | LabelNotFoundException
                        | ChecklistItemNotFoundException
                        exception
        ) {
            HttpErrorResponses.notFound(exchange);
        }
    }

    private void handleCollection(
            HttpExchange exchange,
            String method,
            AuthenticatedSession session
    ) throws IOException {
        if ("GET".equals(method)) {
            HtmlResponses.send(
                    exchange,
                    200,
                    BoardPages.list(
                            boardService.listBoards(
                                    session
                                            .administrator()
                                            .id()
                            ),
                            session.csrfToken()
                    )
            );
            return;
        }

        if (!"POST".equals(method)) {
            HttpErrorResponses.methodNotAllowed(
                    exchange,
                    "GET, POST"
            );
            return;
        }

        Map<String, String> fields =
                FormUrlEncodedParser.parse(exchange);

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

        var board = boardService.createBoard(
                session.administrator().id(),
                required(fields, "name")
        );

        HtmlResponses.redirect(
                exchange,
                PATH + "/"
                        + board.board().id().value()
        );
    }

    private void exportCsv(
            HttpExchange exchange,
            AuthenticatedSession session,
            long boardId
    ) throws IOException {
        long ownerId = session.administrator().id();

        CsvExportDocument document =
                csvExportService.export(ownerId, boardId);

        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        exchange.getResponseHeaders().set(
                "Content-Disposition",
                "attachment; filename=\""
                        + document.filename() + "\""
        );

        HttpResponseWriter.sendBytes(
                exchange,
                200,
                "text/csv; charset=UTF-8",
                document.content()
        );
    }

    private void showBoard(
            HttpExchange exchange,
            AuthenticatedSession session,
            long boardId
    ) throws IOException {
        long ownerId = session.administrator().id();
        BoardDetails details =
                boardService.getBoard(ownerId, boardId);
        List<Card> cards =
                cardService.listCards(ownerId, boardId);

        List<es.aulaflow.domain.board.CardId> cardIds =
                cards.stream()
                        .map(Card::id)
                        .toList();

        HtmlResponses.send(
                exchange,
                200,
                BoardPages.details(
                        details,
                        cards,
                        labelService.listLabels(
                                ownerId,
                                boardId
                        ),
                        labelService.listLabelsForCards(
                                ownerId,
                                boardId,
                                cardIds
                        ),
                        checklistService.listItemsForCards(
                                ownerId,
                                boardId,
                                cardIds
                        ),
                        session.csrfToken()
                )
        );
    }

    private void mutate(
            HttpExchange exchange,
            AuthenticatedSession session,
            long boardId,
            String[] segments,
            Map<String, String> fields
    ) throws IOException {
        long ownerId =
                session.administrator().id();

        if (
                segments.length == 2
                        && "rename".equals(segments[1])
        ) {
            boardService.renameBoard(
                    ownerId,
                    boardId,
                    required(fields, "name")
            );
        } else if (
                segments.length == 2
                        && "columns".equals(segments[1])
        ) {
            boardService.createColumn(
                    ownerId,
                    boardId,
                    required(fields, "name")
            );
        } else if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && "reorder".equals(segments[2])
        ) {
            boardService.reorderColumns(
                    ownerId,
                    boardId,
                    parseOrder(
                            required(fields, "order")
                    )
            );
        } else if (
                segments.length == 4
                        && "columns".equals(segments[1])
                        && "rename".equals(segments[3])
        ) {
            boardService.renameColumn(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    required(fields, "name")
            );
        } else if (
                segments.length == 4
                        && "columns".equals(segments[1])
                        && "cards".equals(segments[3])
        ) {
            long colId = positiveId(segments[2]);
            cardService.createCard(
                    ownerId,
                    boardId,
                    colId,
                    required(fields, "title"),
                    fields.getOrDefault("description", "")
            );
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "edit".equals(segments[3])
        ) {
            long cardId = positiveId(segments[2]);
            cardService.updateCard(
                    ownerId,
                    boardId,
                    cardId,
                    required(fields, "title"),
                    fields.getOrDefault("description", "")
            );
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "delete".equals(segments[3])
        ) {
            long cardId = positiveId(segments[2]);
            cardService.deleteCard(ownerId, boardId, cardId);
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "move".equals(segments[3])
        ) {
            long cardId = positiveId(segments[2]);
            long targetColumnId = positiveId(
                    required(fields, "targetColumnId")
            );
            int targetPosition = Integer.parseInt(
                    required(fields, "targetPosition")
            );
            cardService.moveCard(
                    ownerId,
                    boardId,
                    cardId,
                    targetColumnId,
                    targetPosition
            );
        } else if (
                segments.length == 2
                        && "labels".equals(segments[1])
        ) {
            labelService.createLabel(
                    ownerId,
                    boardId,
                    required(fields, "name"),
                    required(fields, "color")
            );
        } else if (
                segments.length == 4
                        && "labels".equals(segments[1])
                        && "edit".equals(segments[3])
        ) {
            labelService.updateLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    required(fields, "name"),
                    required(fields, "color")
            );
        } else if (
                segments.length == 4
                        && "labels".equals(segments[1])
                        && "delete".equals(segments[3])
        ) {
            labelService.deleteLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2])
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
                        && "assign".equals(segments[5])
        ) {
            labelService.assignLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4])
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
                        && "unassign".equals(segments[5])
        ) {
            labelService.unassignLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4])
            );
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
        ) {
            long cardId = positiveId(segments[2]);
            checklistService.createItem(
                    ownerId,
                    boardId,
                    cardId,
                    required(fields, "text")
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "edit".equals(segments[5])
        ) {
            checklistService.updateItemText(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4]),
                    required(fields, "text")
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "toggle".equals(segments[5])
        ) {
            checklistService.setCompleted(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4]),
                    "true".equals(
                            required(fields, "completed")
                    )
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "move".equals(segments[5])
        ) {
            checklistService.moveItem(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4]),
                    Integer.parseInt(
                            required(fields, "targetPosition")
                    )
            );
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "delete".equals(segments[5])
        ) {
            checklistService.deleteItem(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4])
            );
        } else {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        HtmlResponses.redirect(
                exchange,
                PATH + "/" + boardId
        );
    }

    static List<Long> parseOrder(String rawOrder) {
        if (rawOrder.isBlank()) {
            throw new IllegalArgumentException(
                    "El orden no puede estar vacío."
            );
        }

        return Arrays.stream(rawOrder.split(",", -1))
                .map(BoardHtmlHandler::positiveId)
                .toList();
    }

    private static long positiveId(String rawId) {
        long id = Long.parseLong(rawId);
        if (id < 1) {
            throw new IllegalArgumentException(
                    "El identificador debe ser positivo."
            );
        }
        return id;
    }

    private static String required(
            Map<String, String> fields,
            String name
    ) {
        String value = fields.get(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Falta un campo obligatorio."
            );
        }
        return value;
    }
}
