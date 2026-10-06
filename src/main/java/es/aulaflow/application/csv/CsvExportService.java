package es.aulaflow.application.csv;

import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;

import java.util.List;
import java.util.Objects;

/**
 * Exporta un tablero propio como documento CSV canónico del contrato
 * AulaFlow Kanban CSV v1. Reutiliza el modelo de tablero existente;
 * la comprobación de propiedad ocurre en {@link BoardService}, que
 * lanza {@code BoardNotFoundException} para un tablero inexistente o
 * ajeno.
 */
public final class CsvExportService {

    private final BoardService boardService;
    private final CardService cardService;

    public CsvExportService(
            BoardService boardService,
            CardService cardService
    ) {
        this.boardService = Objects.requireNonNull(
                boardService,
                "El servicio de tableros no puede ser null."
        );

        this.cardService = Objects.requireNonNull(
                cardService,
                "El servicio de tarjetas no puede ser null."
        );
    }

    public CsvExportDocument export(
            long ownerId,
            long boardId
    ) {
        BoardDetails details = boardService.getBoard(
                ownerId, boardId
        );

        List<Card> cards = cardService.listCards(
                ownerId, boardId
        );

        byte[] content = CsvSerializer.serialize(
                details.board(),
                details.columns(),
                cards
        );

        String filename =
                "aulaflow-board-" + boardId + ".csv";

        return new CsvExportDocument(filename, content);
    }
}
