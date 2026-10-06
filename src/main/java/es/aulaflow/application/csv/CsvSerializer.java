package es.aulaflow.application.csv;

import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.Card;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Serializador canónico del contrato AulaFlow Kanban CSV v1. Produce
 * siempre el orden canónico (BOARD, COLUMN 0 con sus CARD, COLUMN 1
 * con sus CARD, ...), con BOM UTF-8 y saltos de línea CRLF.
 */
public final class CsvSerializer {

    static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    private static final byte[] UTF8_BOM =
            {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvSerializer() {
    }

    public static byte[] serialize(
            Board board,
            List<BoardColumn> columns,
            List<Card> cards
    ) {
        Objects.requireNonNull(
                board,
                "El tablero no puede ser null."
        );

        Objects.requireNonNull(
                columns,
                "Las columnas no pueden ser null."
        );

        Objects.requireNonNull(
                cards,
                "Las tarjetas no pueden ser null."
        );

        List<BoardColumn> orderedColumns =
                columns.stream()
                        .sorted(
                                Comparator.comparingInt(
                                        BoardColumn::position
                                )
                        )
                        .toList();

        String boardName = FormulaNeutralizer.protect(
                board.name().value()
        );

        StringBuilder csv = new StringBuilder();
        csv.append(HEADER).append("\r\n");

        csv.append(
                row(
                        "1", "BOARD", boardName,
                        "", "", "", "", ""
                )
        );

        for (BoardColumn column : orderedColumns) {
            String columnName = FormulaNeutralizer.protect(
                    column.name().value()
            );

            csv.append(
                    row(
                            "1", "COLUMN", boardName,
                            columnName,
                            String.valueOf(
                                    column.position()
                            ),
                            "", "", ""
                    )
            );

            List<Card> columnCards = cards.stream()
                    .filter(card ->
                            card.columnId()
                                    .equals(column.id())
                    )
                    .sorted(
                            Comparator.comparingInt(
                                    Card::position
                            )
                    )
                    .toList();

            for (Card card : columnCards) {
                String title = FormulaNeutralizer.protect(
                        card.title().value()
                );

                String description =
                        FormulaNeutralizer.protect(
                                card.description().value()
                        );

                csv.append(
                        row(
                                "1", "CARD", boardName,
                                columnName,
                                String.valueOf(
                                        column.position()
                                ),
                                title,
                                description,
                                String.valueOf(
                                        card.position()
                                )
                        )
                );
            }
        }

        byte[] body = csv.toString()
                .getBytes(StandardCharsets.UTF_8);

        byte[] result =
                new byte[UTF8_BOM.length + body.length];

        System.arraycopy(
                UTF8_BOM, 0, result, 0, UTF8_BOM.length
        );

        System.arraycopy(
                body, 0, result, UTF8_BOM.length,
                body.length
        );

        return result;
    }

    private static String row(String... fields) {
        StringBuilder line = new StringBuilder();

        for (int index = 0;
             index < fields.length;
             index++) {
            if (index > 0) {
                line.append(',');
            }

            line.append(escape(fields[index]));
        }

        line.append("\r\n");

        return line.toString();
    }

    private static String escape(String raw) {
        boolean needsQuoting =
                raw.indexOf(',') >= 0
                        || raw.indexOf('"') >= 0
                        || raw.indexOf('\n') >= 0
                        || raw.indexOf('\r') >= 0;

        if (!needsQuoting) {
            return raw;
        }

        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
}
