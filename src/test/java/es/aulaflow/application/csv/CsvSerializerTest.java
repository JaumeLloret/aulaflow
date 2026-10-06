package es.aulaflow.application.csv;

import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSerializerTest {

    private static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    @Test
    void serializesBoardWithColumnAndCard() {
        Board board = board(1L, "Tauler de classe");
        BoardColumn column =
                column(10L, 1L, "Per fer", 0);
        Card card = card(
                100L, 10L, "Estudiar", "Repassar temes", 0
        );

        byte[] bytes = CsvSerializer.serialize(
                board,
                List.of(column),
                List.of(card)
        );

        String text = withoutBom(bytes);
        String[] lines = text.split("\r\n", -1);

        assertEquals(HEADER, lines[0]);
        assertEquals(
                "1,BOARD,Tauler de classe,,,,,",
                lines[1]
        );
        assertEquals(
                "1,COLUMN,Tauler de classe,Per fer,0,,,",
                lines[2]
        );
        assertEquals(
                "1,CARD,Tauler de classe,Per fer,0,"
                        + "Estudiar,Repassar temes,0",
                lines[3]
        );
    }

    @Test
    void exportUsesCrlfLineEndingsAndUtf8Bom() {
        Board board = board(1L, "Tauler");
        byte[] bytes = CsvSerializer.serialize(
                board, List.of(), List.of()
        );

        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String text = withoutBom(bytes);
        assertTrue(text.contains("\r\n"));
        assertTrue(!text.replace("\r\n", "").contains("\n"));
    }

    @Test
    void quotesFieldsContainingCommaOrQuoteOrNewline() {
        Board board = board(
                1L, "Tauler, amb \"cites\"\ni salts"
        );

        byte[] bytes = CsvSerializer.serialize(
                board, List.of(), List.of()
        );

        String text = withoutBom(bytes);

        assertTrue(
                text.contains(
                        "\"Tauler, amb \"\"cites\"\""
                                + "\ni salts\""
                )
        );
    }

    @Test
    void protectsDangerousLeadingCharacters() {
        Board board = board(1L, "=cmd|calc");
        BoardColumn column =
                column(10L, 1L, "+SUM(A1:A2)", 0);
        Card card = card(
                100L, 10L, "-1+2", "@danger", 0
        );

        byte[] bytes = CsvSerializer.serialize(
                board,
                List.of(column),
                List.of(card)
        );

        String text = withoutBom(bytes);

        assertTrue(text.contains("'=cmd|calc"));
        assertTrue(text.contains("'+SUM(A1:A2)"));
        assertTrue(text.contains("'-1+2"));
        assertTrue(text.contains("'@danger"));
    }

    @Test
    void ordersColumnsAndCardsByPosition() {
        Board board = board(1L, "Tauler");
        BoardColumn firstColumn =
                column(10L, 1L, "Primera", 0);
        BoardColumn secondColumn =
                column(11L, 1L, "Segona", 1);

        Card cardB = card(
                101L, 10L, "Segona targeta", "", 1
        );
        Card cardA = card(
                100L, 10L, "Primera targeta", "", 0
        );
        Card cardC = card(
                102L, 11L, "Tercera targeta", "", 0
        );

        byte[] bytes = CsvSerializer.serialize(
                board,
                List.of(secondColumn, firstColumn),
                List.of(cardB, cardC, cardA)
        );

        String text = withoutBom(bytes);
        String[] lines = text.split("\r\n", -1);

        assertEquals(
                "1,COLUMN,Tauler,Primera,0,,,",
                lines[2]
        );
        assertEquals(
                "1,CARD,Tauler,Primera,0,"
                        + "Primera targeta,,0",
                lines[3]
        );
        assertEquals(
                "1,CARD,Tauler,Primera,0,"
                        + "Segona targeta,,1",
                lines[4]
        );
        assertEquals(
                "1,COLUMN,Tauler,Segona,1,,,",
                lines[5]
        );
        assertEquals(
                "1,CARD,Tauler,Segona,1,"
                        + "Tercera targeta,,0",
                lines[6]
        );
    }

    @Test
    void supportsColumnWithoutCards() {
        Board board = board(1L, "Tauler");
        BoardColumn empty =
                column(10L, 1L, "Buida", 0);

        byte[] bytes = CsvSerializer.serialize(
                board, List.of(empty), List.of()
        );

        String text = withoutBom(bytes);
        String[] lines = text.split("\r\n", -1);

        assertEquals(3, countNonEmpty(lines));
    }

    private static int countNonEmpty(String[] lines) {
        int count = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static String withoutBom(byte[] bytes) {
        byte[] withoutBom = new byte[bytes.length - 3];
        System.arraycopy(
                bytes, 3, withoutBom, 0, withoutBom.length
        );
        return new String(
                withoutBom, StandardCharsets.UTF_8
        );
    }

    private static Board board(long id, String name) {
        return new Board(
                new BoardId(id),
                1L,
                new BoardName(name),
                Instant.now()
        );
    }

    private static BoardColumn column(
            long id,
            long boardId,
            String name,
            int position
    ) {
        return new BoardColumn(
                new ColumnId(id),
                new BoardId(boardId),
                new ColumnName(name),
                position,
                Instant.now()
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
}
