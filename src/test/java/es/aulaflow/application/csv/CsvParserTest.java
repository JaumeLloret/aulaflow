package es.aulaflow.application.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvParserTest {

    private static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    @Test
    void parsesMinimalDocumentWithBoardAndColumn()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler,,,,,\r\n"
                + "1,COLUMN,Tauler,Per fer,0,,,\r\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(3, document.rows().size());
        assertEquals(
                List.of(
                        "1", "BOARD", "Tauler",
                        "", "", "", "", ""
                ),
                document.rows().get(1).fields()
        );
    }

    @Test
    void acceptsOptionalUtf8Bom() throws IOException {
        byte[] bom = {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
        };
        byte[] body = (HEADER + "\r\n"
                + "1,BOARD,Tauler,,,,,\r\n")
                .getBytes(StandardCharsets.UTF_8);

        byte[] combined =
                new byte[bom.length + body.length];
        System.arraycopy(
                bom, 0, combined, 0, bom.length
        );
        System.arraycopy(
                body, 0, combined, bom.length,
                body.length
        );

        CsvDocument document = CsvParser.parse(
                new ByteArrayInputStream(combined)
        );

        assertEquals(
                "aulaflow_version",
                document.header().field(0)
        );
    }

    @Test
    void acceptsLineFeedWithoutCarriageReturn()
            throws IOException {
        String csv = HEADER + "\n"
                + "1,BOARD,Tauler,,,,,\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(2, document.rows().size());
    }

    @Test
    void preservesCommaInsideQuotedField()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,\"Tauler, amb coma\",,,,,\r\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(
                "Tauler, amb coma",
                document.rows().get(1).field(2)
        );
    }

    @Test
    void preservesEscapedQuoteInsideQuotedField()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,\"Tauler \"\"citat\"\"\",,,,,\r\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(
                "Tauler \"citat\"",
                document.rows().get(1).field(2)
        );
    }

    @Test
    void preservesInternalLineBreakInsideQuotedField()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,\"Línia 1\nLínia 2\",,,,,\r\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(
                "Línia 1\nLínia 2",
                document.rows().get(1).field(2)
        );
    }

    @Test
    void parsesUnicodeValencianCastilianAndEmoji()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler català 🎓,,,,,\r\n"
                + "1,COLUMN,Tauler català 🎓,"
                + "Revisión ñoño,0,,,\r\n";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(
                "Tauler català 🎓",
                document.rows().get(1).field(2)
        );
        assertEquals(
                "Revisión ñoño",
                document.rows().get(2).field(3)
        );
    }

    @Test
    void lastRowWithoutTrailingLineBreakIsParsed()
            throws IOException {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler,,,,,";

        CsvDocument document =
                CsvParser.parse(inputStream(csv));

        assertEquals(2, document.rows().size());
        assertEquals(
                "Tauler",
                document.rows().get(1).field(2)
        );
    }

    @Test
    void rejectsInvalidUtf8Bytes() {
        byte[] invalid = {
                (byte) 0xFF, (byte) 0xFE, (byte) 0x00
        };

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(
                        new ByteArrayInputStream(invalid)
                )
        );

        assertEquals("invalid_utf8", exception.code());
    }

    @Test
    void rejectsUnterminatedQuote() {
        String csv = HEADER + "\r\n"
                + "1,BOARD,\"Tauler sense tancar,,,,,\r\n";

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(csv))
        );

        assertEquals(
                "unterminated_quote",
                exception.code()
        );
    }

    @Test
    void rejectsCharacterAfterClosingQuote() {
        String csv = HEADER + "\r\n"
                + "1,BOARD,\"Tauler\"extra,,,,,\r\n";

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(csv))
        );

        assertEquals(
                "malformed_quote",
                exception.code()
        );
    }

    @Test
    void rejectsTooManyFields() {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler,,,,,,extra\r\n";

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(csv))
        );

        assertEquals(
                "invalid_field_count",
                exception.code()
        );
        assertEquals(2, exception.recordNumber());
    }

    @Test
    void rejectsTooFewFields() {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler,,,,\r\n";

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(csv))
        );

        assertEquals(
                "invalid_field_count",
                exception.code()
        );
    }

    @Test
    void rejectsNulCharacter() {
        String csv = HEADER + "\r\n"
                + "1,BOARD,Tauler" + '\u0000' + "dolent,,,,,\r\n";

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(csv))
        );

        assertEquals(
                "nul_character",
                exception.code()
        );
    }

    @Test
    void rejectsEmptyFile() {
        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(inputStream(""))
        );

        assertEquals(
                "invalid_header",
                exception.code()
        );
    }

    @Test
    void parsesDocumentWithOnlyHeader()
            throws IOException {
        CsvDocument document = CsvParser.parse(
                inputStream(HEADER + "\r\n")
        );

        assertEquals(1, document.rows().size());
    }

    @Test
    void rejectsUploadLargerThanMaximum() {
        StringBuilder oversized = new StringBuilder(
                HEADER + "\r\n"
        );

        String hugeField =
                "x".repeat(
                        CsvLimits.MAX_UPLOAD_BYTES + 10
                );

        oversized.append("1,BOARD,")
                .append(hugeField)
                .append(",,,,,\r\n");

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(
                        inputStream(oversized.toString())
                )
        );

        assertEquals(
                "file_too_large",
                exception.code()
        );
    }

    @Test
    void rejectsTooManyLogicalRecords() {
        StringBuilder huge = new StringBuilder(
                HEADER + "\r\n"
        );

        for (
                int index = 0;
                index <= CsvLimits.MAX_LOGICAL_RECORDS;
                index++
        ) {
            huge.append(
                    "1,COLUMN,Tauler,Columna,"
                            + index + ",,,\r\n"
            );
        }

        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> CsvParser.parse(
                        inputStream(huge.toString())
                )
        );

        assertEquals(
                "too_many_records",
                exception.code()
        );
    }

    private static InputStream inputStream(String text) {
        return new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }
}
