package es.aulaflow.application.csv;

import es.aulaflow.application.board.BoardNotFoundException;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardMoveResult;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.application.board.CardService;
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvExportServiceTest {

    private static final long OWNER_ID = 1L;

    @Test
    void exportsBoardAsCanonicalCsvWithSafeFilename() {
        Board board = new Board(
                new BoardId(7L),
                OWNER_ID,
                new BoardName("Tauler exportat"),
                Instant.now()
        );

        BoardColumn column = new BoardColumn(
                new ColumnId(70L),
                board.id(),
                new ColumnName("Per fer"),
                0,
                Instant.now()
        );

        Card card = card(
                700L, 70L, "Estudiar", "", 0
        );

        FakeBoardRepository boardRepository =
                new FakeBoardRepository(
                        new BoardDetails(
                                board, List.of(column)
                        )
                );

        FakeCardRepository cardRepository =
                new FakeCardRepository(List.of(card));

        CsvExportService exportService =
                new CsvExportService(
                        new BoardService(boardRepository),
                        new CardService(cardRepository)
                );

        CsvExportDocument document = exportService.export(
                OWNER_ID, 7L
        );

        assertEquals(
                "aulaflow-board-7.csv",
                document.filename()
        );

        String text = new String(
                document.content(),
                StandardCharsets.UTF_8
        );

        assertEquals(
                (byte) 0xEF,
                document.content()[0]
        );
        assertEquals(true, text.contains("Tauler exportat"));
        assertEquals(true, text.contains("Estudiar"));
    }

    @Test
    void rejectsExportOfForeignOrMissingBoard() {
        FakeBoardRepository boardRepository =
                new FakeBoardRepository(null);
        FakeCardRepository cardRepository =
                new FakeCardRepository(List.of());

        CsvExportService exportService =
                new CsvExportService(
                        new BoardService(boardRepository),
                        new CardService(cardRepository)
                );

        assertThrows(
                BoardNotFoundException.class,
                () -> exportService.export(OWNER_ID, 999L)
        );
    }

    private static Card card(
            long id,
            long columnId,
            String title,
            String description,
            int position
    ) {
        Instant now = Instant.now();
        return new Card(
                new CardId(id),
                new ColumnId(columnId),
                new CardTitle(title),
                new CardDescription(description),
                position,
                now,
                now
        );
    }

    private static final class FakeBoardRepository
            implements BoardRepository {

        private final BoardDetails details;

        private FakeBoardRepository(BoardDetails details) {
            this.details = details;
        }

        @Override
        public BoardDetails create(
                long ownerId,
                BoardName name,
                List<ColumnName> initialColumns
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Board> findAllByOwner(long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BoardDetails> findByIdAndOwner(
                BoardId boardId,
                long ownerId
        ) {
            if (
                    details == null
                            || details.board().id().value()
                            != boardId.value()
                            || details.board().ownerId()
                            != ownerId
            ) {
                return Optional.empty();
            }

            return Optional.of(details);
        }

        @Override
        public boolean renameBoard(
                BoardId boardId,
                long ownerId,
                BoardName name
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BoardColumn> addColumn(
                BoardId boardId,
                long ownerId,
                ColumnName name
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean renameColumn(
                BoardId boardId,
                ColumnId columnId,
                long ownerId,
                ColumnName name
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReorderResult reorderColumns(
                BoardId boardId,
                long ownerId,
                List<ColumnId> orderedColumnIds
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeCardRepository
            implements CardRepository {

        private final List<Card> cards;

        private FakeCardRepository(List<Card> cards) {
            this.cards = cards;
        }

        @Override
        public Card create(
                long ownerId,
                long boardId,
                ColumnId columnId,
                CardTitle title,
                CardDescription description
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Card> listByBoard(
                long ownerId,
                long boardId
        ) {
            return cards;
        }

        @Override
        public Optional<Card> findById(
                long ownerId,
                long boardId,
                CardId cardId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(
                long ownerId,
                long boardId,
                CardId cardId,
                CardTitle title,
                CardDescription description
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(
                long ownerId,
                long boardId,
                CardId cardId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CardMoveResult move(
                long ownerId,
                long boardId,
                CardId cardId,
                ColumnId targetColumnId,
                int targetPosition
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
