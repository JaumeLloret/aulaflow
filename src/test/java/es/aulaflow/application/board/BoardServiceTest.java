package es.aulaflow.application.board;

import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardServiceTest {

    @Test
    void createsBoardWithApprovedInitialColumns() {
        RecordingRepository repository =
                new RecordingRepository();

        BoardDetails created =
                new BoardService(repository)
                        .createBoard(
                                1,
                                "  Aula  "
                        );

        assertEquals("Aula", created.board().name().value());
        assertEquals(
                List.of("Per fer", "En curs", "Fet"),
                repository.initialColumns
                        .stream()
                        .map(ColumnName::value)
                        .toList()
        );
    }

    @Test
    void reportsMissingOrForeignResourcesUniformly() {
        RecordingRepository repository =
                new RecordingRepository();
        repository.boardExists = false;

        BoardService service =
                new BoardService(repository);

        assertThrows(
                BoardNotFoundException.class,
                () -> service.getBoard(1, 9)
        );
        assertThrows(
                BoardNotFoundException.class,
                () -> service.renameBoard(
                        1,
                        9,
                        "Altre"
                )
        );
        assertThrows(
                BoardNotFoundException.class,
                () -> service.createColumn(
                        1,
                        9,
                        "Nova"
                )
        );
    }

    @Test
    void rejectsDuplicateColumnIdentifiers() {
        BoardService service =
                new BoardService(
                        new RecordingRepository()
                );

        assertThrows(
                InvalidColumnOrderException.class,
                () -> service.reorderColumns(
                        1,
                        1,
                        List.of(1L, 1L, 2L)
                )
        );
    }

    @Test
    void translatesRepositoryOrderFailure() {
        RecordingRepository repository =
                new RecordingRepository();
        repository.reorderResult =
                BoardRepository.ReorderResult
                        .INVALID_ORDER;

        assertThrows(
                InvalidColumnOrderException.class,
                () -> new BoardService(repository)
                        .reorderColumns(
                                1,
                                1,
                                List.of(1L, 2L, 3L)
                        )
        );
    }

    private static final class RecordingRepository
            implements BoardRepository {

        private List<ColumnName> initialColumns =
                List.of();
        private boolean boardExists = true;
        private ReorderResult reorderResult =
                ReorderResult.UPDATED;

        @Override
        public BoardDetails create(
                long ownerId,
                BoardName name,
                List<ColumnName> initialColumns
        ) {
            this.initialColumns =
                    List.copyOf(initialColumns);

            Board board = new Board(
                    new BoardId(1),
                    ownerId,
                    name,
                    Instant.EPOCH
            );

            return new BoardDetails(
                    board,
                    java.util.stream.IntStream
                            .range(
                                    0,
                                    initialColumns.size()
                            )
                            .mapToObj(index ->
                                    new BoardColumn(
                                            new ColumnId(
                                                    index + 1
                                            ),
                                            board.id(),
                                            initialColumns.get(
                                                    index
                                            ),
                                            index,
                                            Instant.EPOCH
                                    )
                            )
                            .toList()
            );
        }

        @Override
        public List<Board> findAllByOwner(long ownerId) {
            return List.of();
        }

        @Override
        public Optional<BoardDetails> findByIdAndOwner(
                BoardId boardId,
                long ownerId
        ) {
            return boardExists
                    ? Optional.of(
                            create(
                                    ownerId,
                                    new BoardName("Aula"),
                                    List.of()
                            )
                    )
                    : Optional.empty();
        }

        @Override
        public boolean renameBoard(
                BoardId boardId,
                long ownerId,
                BoardName name
        ) {
            return boardExists;
        }

        @Override
        public Optional<BoardColumn> addColumn(
                BoardId boardId,
                long ownerId,
                ColumnName name
        ) {
            return boardExists
                    ? Optional.of(
                            new BoardColumn(
                                    new ColumnId(1),
                                    boardId,
                                    name,
                                    0,
                                    Instant.EPOCH
                            )
                    )
                    : Optional.empty();
        }

        @Override
        public boolean renameColumn(
                BoardId boardId,
                ColumnId columnId,
                long ownerId,
                ColumnName name
        ) {
            return boardExists;
        }

        @Override
        public ReorderResult reorderColumns(
                BoardId boardId,
                long ownerId,
                List<ColumnId> orderedColumnIds
        ) {
            return reorderResult;
        }
    }
}
