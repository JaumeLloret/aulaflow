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
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistProgress;
import es.aulaflow.domain.board.Label;
import es.aulaflow.presentation.auth.AuthenticationRequiredHandler;
import es.aulaflow.presentation.auth.CsrfProtection;
import es.aulaflow.presentation.http.FormUrlEncodedParser;
import es.aulaflow.presentation.http.HttpErrorResponses;
import es.aulaflow.presentation.http.HttpResponseWriter;
import es.aulaflow.presentation.http.JsonText;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoardApiHandler
        implements HttpHandler {

    public static final String PATH = "/api/v1/boards";

    private final BoardService boardService;
    private final CardService cardService;
    private final LabelService labelService;
    private final ChecklistService checklistService;
    private final CsvExportService csvExportService;

    public BoardApiHandler(
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
        long ownerId = session.administrator().id();
        String path =
                exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (PATH.equals(path)) {
                collection(
                        exchange,
                        method,
                        session,
                        ownerId
                );
                return;
            }

            String[] segments =
                    path.substring(PATH.length() + 1)
                            .split("/");
            long boardId = positiveId(segments[0]);

            if (
                    segments.length == 1
                            && "GET".equals(method)
            ) {
                BoardDetails details =
                        boardService.getBoard(
                                ownerId,
                                boardId
                        );
                sendJson(
                        exchange,
                        200,
                        boardJsonWithCards(
                                details,
                                ownerId,
                                boardId
                        )
                );
                return;
            }

            if (
                    segments.length == 2
                            && "export.csv"
                                    .equals(segments[1])
                            && "GET".equals(method)
            ) {
                exportCsv(exchange, ownerId, boardId);
                return;
            }

            if (!isKnownRoute(segments)) {
                HttpErrorResponses.notFound(exchange);
                return;
            }

            if (!isMutation(method, segments)) {
                HttpErrorResponses.methodNotAllowed(
                        exchange,
                        allowedMethods(segments)
                );
                return;
            }

            if (
                    !CsrfProtection.require(
                            exchange,
                            exchange
                                    .getRequestHeaders()
                                    .getFirst(
                                            CsrfProtection
                                                    .HEADER
                                    )
                    )
            ) {
                return;
            }

            Map<String, String> fields =
                    "DELETE".equals(method)
                            ? Map.of()
                            : FormUrlEncodedParser
                            .parse(exchange);

            mutate(
                    exchange,
                    ownerId,
                    boardId,
                    method,
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

    private void collection(
            HttpExchange exchange,
            String method,
            AuthenticatedSession session,
            long ownerId
    ) throws IOException {
        if ("GET".equals(method)) {
            List<Board> boards =
                    boardService.listBoards(ownerId);

            sendJson(
                    exchange,
                    200,
                    boardsJson(boards)
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

        if (
                !CsrfProtection.require(
                        exchange,
                        exchange
                                .getRequestHeaders()
                                .getFirst(
                                        CsrfProtection.HEADER
                                )
                )
        ) {
            return;
        }

        Map<String, String> fields =
                FormUrlEncodedParser.parse(exchange);
        BoardDetails created =
                boardService.createBoard(
                        ownerId,
                        required(fields, "name")
                );

        exchange.getResponseHeaders().set(
                "Location",
                PATH + "/"
                        + created.board().id().value()
        );

        sendJson(
                exchange,
                201,
                boardJsonWithCards(
                        created,
                        ownerId,
                        created.board().id().value()
                )
        );
    }

    private void mutate(
            HttpExchange exchange,
            long ownerId,
            long boardId,
            String method,
            String[] segments,
            Map<String, String> fields
    ) throws IOException {
        if (
                segments.length == 1
                        && "PATCH".equals(method)
        ) {
            boardService.renameBoard(
                    ownerId,
                    boardId,
                    required(fields, "name")
            );
        } else if (
                segments.length == 2
                        && "columns".equals(segments[1])
                        && "POST".equals(method)
        ) {
            boardService.createColumn(
                    ownerId,
                    boardId,
                    required(fields, "name")
            );
        } else if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && "order".equals(segments[2])
                        && "PUT".equals(method)
        ) {
            boardService.reorderColumns(
                    ownerId,
                    boardId,
                    BoardHtmlHandler.parseOrder(
                            required(fields, "order")
                    )
            );
        } else if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && "PATCH".equals(method)
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
                        && "POST".equals(method)
        ) {
            long colId = positiveId(segments[2]);
            Card created = cardService.createCard(
                    ownerId,
                    boardId,
                    colId,
                    required(fields, "title"),
                    fields.getOrDefault("description", "")
            );
            sendJson(exchange, 201, cardJson(created));
            return;
        } else if (
                segments.length == 3
                        && "cards".equals(segments[1])
                        && "PATCH".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            cardService.updateCard(
                    ownerId,
                    boardId,
                    cardId,
                    required(fields, "title"),
                    fields.getOrDefault("description", "")
            );
            sendJson(
                    exchange,
                    200,
                    cardJsonWithDetails(
                            ownerId,
                            boardId,
                            cardId
                    )
            );
            return;
        } else if (
                segments.length == 3
                        && "cards".equals(segments[1])
                        && "DELETE".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            cardService.deleteCard(ownerId, boardId, cardId);
            sendNoContent(exchange);
            return;
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "position".equals(segments[3])
                        && "PUT".equals(method)
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
                        && "POST".equals(method)
        ) {
            labelService.createLabel(
                    ownerId,
                    boardId,
                    required(fields, "name"),
                    required(fields, "color")
            );
        } else if (
                segments.length == 3
                        && "labels".equals(segments[1])
                        && "PATCH".equals(method)
        ) {
            labelService.updateLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    required(fields, "name"),
                    required(fields, "color")
            );
        } else if (
                segments.length == 3
                        && "labels".equals(segments[1])
                        && "DELETE".equals(method)
        ) {
            labelService.deleteLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2])
            );
            sendNoContent(exchange);
            return;
        } else if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
                        && "PUT".equals(method)
        ) {
            labelService.assignLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4])
            );
        } else if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
                        && "DELETE".equals(method)
        ) {
            labelService.unassignLabel(
                    ownerId,
                    boardId,
                    positiveId(segments[2]),
                    positiveId(segments[4])
            );
            sendNoContent(exchange);
            return;
        } else if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "POST".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            ChecklistItem created =
                    checklistService.createItem(
                            ownerId,
                            boardId,
                            cardId,
                            required(fields, "text")
                    );
            sendJson(
                    exchange,
                    201,
                    checklistItemJson(created)
            );
            return;
        } else if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "PATCH".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            long itemId = positiveId(segments[4]);
            checklistService.updateItemText(
                    ownerId,
                    boardId,
                    cardId,
                    itemId,
                    required(fields, "text")
            );
            sendJson(
                    exchange,
                    200,
                    checklistItemJson(
                            singleItem(
                                    ownerId,
                                    boardId,
                                    cardId,
                                    itemId
                            )
                    )
            );
            return;
        } else if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "DELETE".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            long itemId = positiveId(segments[4]);
            checklistService.deleteItem(
                    ownerId,
                    boardId,
                    cardId,
                    itemId
            );
            sendNoContent(exchange);
            return;
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "completed".equals(segments[5])
                        && "PUT".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            long itemId = positiveId(segments[4]);
            checklistService.setCompleted(
                    ownerId,
                    boardId,
                    cardId,
                    itemId,
                    parseBoolean(
                            required(fields, "completed")
                    )
            );
            sendJson(
                    exchange,
                    200,
                    checklistItemJson(
                            singleItem(
                                    ownerId,
                                    boardId,
                                    cardId,
                                    itemId
                            )
                    )
            );
            return;
        } else if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "position".equals(segments[5])
                        && "PUT".equals(method)
        ) {
            long cardId = positiveId(segments[2]);
            long itemId = positiveId(segments[4]);
            int targetPosition = Integer.parseInt(
                    required(fields, "targetPosition")
            );
            checklistService.moveItem(
                    ownerId,
                    boardId,
                    cardId,
                    itemId,
                    targetPosition
            );
        } else {
            HttpErrorResponses.notFound(exchange);
            return;
        }

        BoardDetails details =
                boardService.getBoard(ownerId, boardId);
        sendJson(
                exchange,
                200,
                boardJsonWithCards(details, ownerId, boardId)
        );
    }

    private ChecklistItem singleItem(
            long ownerId,
            long boardId,
            long cardId,
            long itemId
    ) {
        return checklistService
                .listItems(ownerId, boardId, cardId)
                .stream()
                .filter(item -> item.id().value() == itemId)
                .findFirst()
                .orElseThrow(
                        ChecklistItemNotFoundException::new
                );
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }

        if ("false".equals(value)) {
            return false;
        }

        throw new IllegalArgumentException(
                "El valor de completed no es válido."
        );
    }

    private static boolean isKnownRoute(
            String[] segments
    ) {
        if (segments.length == 1) return true;
        if (
                segments.length == 2
                        && "export.csv"
                                .equals(segments[1])
        ) return true;
        if (
                segments.length == 2
                        && (
                        "columns".equals(segments[1])
                                || "labels"
                                .equals(segments[1])
                )
        ) return true;
        if (
                segments.length == 3
                        && (
                        "columns".equals(segments[1])
                                || "cards"
                                .equals(segments[1])
                                || "labels"
                                .equals(segments[1])
                )
        ) return true;
        if (
                segments.length == 4
                        && "columns".equals(segments[1])
                        && "cards".equals(segments[3])
        ) return true;
        if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && (
                        "position".equals(segments[3])
                                || "checklist-items"
                                .equals(segments[3])
                )
        ) return true;
        if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && (
                        "labels".equals(segments[3])
                                || "checklist-items"
                                .equals(segments[3])
                )
        ) return true;
        if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && (
                        "completed".equals(segments[5])
                                || "position"
                                .equals(segments[5])
                )
        ) return true;
        return false;
    }

    private static boolean isMutation(
            String method,
            String[] segments
    ) {
        if (
                segments.length == 1
                        && "PATCH".equals(method)
        ) return true;
        if (
                segments.length == 2
                        && (
                        "columns".equals(segments[1])
                                || "labels"
                                .equals(segments[1])
                )
                        && "POST".equals(method)
        ) return true;
        if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && "order".equals(segments[2])
                        && "PUT".equals(method)
        ) return true;
        if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && !"order".equals(segments[2])
                        && "PATCH".equals(method)
        ) return true;
        if (
                segments.length == 3
                        && "labels".equals(segments[1])
                        && (
                        "PATCH".equals(method)
                                || "DELETE".equals(method)
                )
        ) return true;
        if (
                segments.length == 4
                        && "columns".equals(segments[1])
                        && "cards".equals(segments[3])
                        && "POST".equals(method)
        ) return true;
        if (
                segments.length == 3
                        && "cards".equals(segments[1])
                        && (
                        "PATCH".equals(method)
                                || "DELETE".equals(method)
                )
        ) return true;
        if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "position".equals(segments[3])
                        && "PUT".equals(method)
        ) return true;
        if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && "POST".equals(method)
        ) return true;
        if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
                        && (
                        "PUT".equals(method)
                                || "DELETE".equals(method)
                )
        ) return true;
        if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && (
                        "PATCH".equals(method)
                                || "DELETE".equals(method)
                )
        ) return true;
        if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
                        && (
                        "completed".equals(segments[5])
                                || "position"
                                .equals(segments[5])
                )
                        && "PUT".equals(method)
        ) return true;
        return false;
    }

    private static String allowedMethods(
            String[] segments
    ) {
        if (segments.length == 1) return "GET, PATCH";
        if (
                segments.length == 2
                        && "export.csv"
                                .equals(segments[1])
        ) return "GET";
        if (
                segments.length == 2
                        && (
                        "columns".equals(segments[1])
                                || "labels"
                                .equals(segments[1])
                )
        ) return "POST";
        if (
                segments.length == 3
                        && "columns".equals(segments[1])
                        && "order".equals(segments[2])
        ) return "PUT";
        if (
                segments.length == 3
                        && "columns".equals(segments[1])
        ) return "PATCH";
        if (
                segments.length == 3
                        && "labels".equals(segments[1])
        ) return "PATCH, DELETE";
        if (
                segments.length == 4
                        && "columns".equals(segments[1])
                        && "cards".equals(segments[3])
        ) return "POST";
        if (
                segments.length == 3
                        && "cards".equals(segments[1])
        ) return "PATCH, DELETE";
        if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "position".equals(segments[3])
        ) return "PUT";
        if (
                segments.length == 4
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
        ) return "POST";
        if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "labels".equals(segments[3])
        ) return "PUT, DELETE";
        if (
                segments.length == 5
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
        ) return "PATCH, DELETE";
        if (
                segments.length == 6
                        && "cards".equals(segments[1])
                        && "checklist-items"
                                .equals(segments[3])
        ) return "PUT";
        return "GET";
    }

    private String boardJsonWithCards(
            BoardDetails details,
            long ownerId,
            long boardId
    ) {
        List<Card> allCards =
                cardService.listCards(ownerId, boardId);

        List<CardId> cardIds = allCards.stream()
                .map(Card::id)
                .toList();

        List<Label> boardLabels =
                labelService.listLabels(ownerId, boardId);

        Map<Long, List<Label>> labelsByCard =
                labelService.listLabelsForCards(
                        ownerId,
                        boardId,
                        cardIds
                );

        Map<Long, List<ChecklistItem>> itemsByCard =
                checklistService.listItemsForCards(
                        ownerId,
                        boardId,
                        cardIds
                );

        return """
                {"id":%d,"name":%s,"labels":[%s],"columns":[%s]}
                """.formatted(
                details.board().id().value(),
                JsonText.quote(
                        details.board().name().value()
                ),
                String.join(
                        ",",
                        boardLabels.stream()
                                .map(
                                        BoardApiHandler
                                                ::labelJson
                                )
                                .toList()
                ),
                String.join(
                        ",",
                        details.columns()
                                .stream()
                                .map(col -> {
                                    List<Card> colCards =
                                            allCards.stream()
                                                    .filter(c ->
                                                            c.columnId().value()
                                                                    == col.id().value()
                                                    )
                                                    .toList();
                                    return columnWithCardsJson(
                                            col,
                                            colCards,
                                            labelsByCard,
                                            itemsByCard
                                    );
                                })
                                .toList()
                )
        ).strip();
    }

    private String cardJsonWithDetails(
            long ownerId,
            long boardId,
            long cardId
    ) {
        Card card = cardService.getCard(
                ownerId,
                boardId,
                cardId
        );

        List<Label> labels = labelService
                .listLabelsForCards(
                        ownerId,
                        boardId,
                        List.of(card.id())
                )
                .getOrDefault(cardId, List.of());

        List<ChecklistItem> items = checklistService
                .listItemsForCards(
                        ownerId,
                        boardId,
                        List.of(card.id())
                )
                .getOrDefault(cardId, List.of());

        return cardJson(card, labels, items);
    }

    private static String columnWithCardsJson(
            BoardColumn column,
            List<Card> cards,
            Map<Long, List<Label>> labelsByCard,
            Map<Long, List<ChecklistItem>> itemsByCard
    ) {
        return """
                {"id":%d,"name":%s,"position":%d,"cards":[%s]}
                """.formatted(
                column.id().value(),
                JsonText.quote(
                        column.name().value()
                ),
                column.position(),
                String.join(
                        ",",
                        cards.stream()
                                .map(card -> cardJson(
                                        card,
                                        labelsByCard
                                                .getOrDefault(
                                                        card.id().value(),
                                                        List.of()
                                                ),
                                        itemsByCard
                                                .getOrDefault(
                                                        card.id().value(),
                                                        List.of()
                                                )
                                ))
                                .toList()
                )
        ).strip();
    }

    private static String cardJson(Card card) {
        return cardJson(card, List.of(), List.of());
    }

    private static String cardJson(
            Card card,
            List<Label> labels,
            List<ChecklistItem> items
    ) {
        ChecklistProgress progress =
                ChecklistProgress.of(items);

        return """
                {"id":%d,"title":%s,"description":%s,"position":%d,"createdAt":%s,"updatedAt":%s,"labels":[%s],"checklist":{"items":[%s],"totalItems":%d,"completedItems":%d,"percentage":%d}}
                """.formatted(
                card.id().value(),
                JsonText.quote(card.title().value()),
                JsonText.quote(card.description().value()),
                card.position(),
                JsonText.quote(card.createdAt().toString()),
                JsonText.quote(card.updatedAt().toString()),
                String.join(
                        ",",
                        labels.stream()
                                .map(
                                        BoardApiHandler
                                                ::labelJson
                                )
                                .toList()
                ),
                String.join(
                        ",",
                        items.stream()
                                .map(
                                        BoardApiHandler
                                                ::checklistItemJson
                                )
                                .toList()
                ),
                progress.totalItems(),
                progress.completedItems(),
                progress.percentage()
        ).strip();
    }

    private static String labelJson(Label label) {
        return """
                {"id":%d,"name":%s,"color":%s}
                """.formatted(
                label.id().value(),
                JsonText.quote(label.name().value()),
                JsonText.quote(label.color().key())
        ).strip();
    }

    private static String checklistItemJson(
            ChecklistItem item
    ) {
        return """
                {"id":%d,"text":%s,"completed":%s,"position":%d}
                """.formatted(
                item.id().value(),
                JsonText.quote(item.text().value()),
                item.completed(),
                item.position()
        ).strip();
    }

    private static String boardsJson(
            List<Board> boards
    ) {
        return """
                {"boards":[%s]}
                """.formatted(
                String.join(
                        ",",
                        boards.stream()
                                .map(
                                        BoardApiHandler
                                                ::boardSummaryJson
                                )
                                .toList()
                )
        ).strip();
    }

    private static String boardSummaryJson(Board board) {
        return """
                {"id":%d,"name":%s}
                """.formatted(
                board.id().value(),
                JsonText.quote(
                        board.name().value()
                )
        ).strip();
    }

    private void exportCsv(
            HttpExchange exchange,
            long ownerId,
            long boardId
    ) throws IOException {
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

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );
        HttpResponseWriter.sendJson(
                exchange,
                status,
                body
        );
    }

    private static void sendNoContent(
            HttpExchange exchange
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );
        HttpResponseWriter.sendNoContent(exchange);
    }

    private static long positiveId(String value) {
        long id = Long.parseLong(value);
        if (id < 1) {
            throw new IllegalArgumentException();
        }
        return id;
    }

    private static String required(
            Map<String, String> fields,
            String name
    ) {
        String value = fields.get(name);
        if (value == null) {
            throw new IllegalArgumentException();
        }
        return value;
    }
}
