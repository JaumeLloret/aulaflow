package es.aulaflow.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardModelTest {

    @Test
    void trimsNamesAndAcceptsUnicode() {
        assertEquals(
                "Projecte d’aula",
                new BoardName(
                        "  Projecte d’aula  "
                ).value()
        );

        assertEquals(
                "Revisió 🧪",
                new ColumnName(
                        " Revisió 🧪 "
                ).value()
        );
    }

    @Test
    void rejectsEmptyAndOversizedNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardName("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnName(
                        "x".repeat(81)
                )
        );
    }

    @Test
    void rejectsNonPositiveIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardId(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ColumnId(-1)
        );
    }

    @Test
    void detailsRequireContiguousOrderedColumns() {
        Board board = board();

        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardDetails(
                        board,
                        List.of(
                                column(
                                        board.id(),
                                        1,
                                        1
                                )
                        )
                )
        );
    }

    @Test
    void detailsExposeColumnsOrderedByPosition() {
        Board board = board();

        BoardDetails details = new BoardDetails(
                board,
                List.of(
                        column(board.id(), 2, 1),
                        column(board.id(), 1, 0)
                )
        );

        assertEquals(
                List.of(1L, 2L),
                details.columns()
                        .stream()
                        .map(column ->
                                column.id().value()
                        )
                        .toList()
        );
    }

    private static Board board() {
        return new Board(
                new BoardId(1),
                1,
                new BoardName("Aula"),
                Instant.EPOCH
        );
    }

    private static BoardColumn column(
            BoardId boardId,
            long id,
            int position
    ) {
        return new BoardColumn(
                new ColumnId(id),
                boardId,
                new ColumnName("Columna " + id),
                position,
                Instant.EPOCH
        );
    }
}
