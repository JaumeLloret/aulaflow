package es.aulaflow.application.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvValidatorTest {

    private static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    @Test
    void validatesMinimalValidBoard() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
        );

        assertTrue(outcome.report().valid());
        assertTrue(outcome.plan().isPresent());
        assertEquals(
                "Tauler",
                outcome.plan().orElseThrow()
                        .boardName().value()
        );
        assertEquals(
                0,
                outcome.plan().orElseThrow().totalCards()
        );
    }

    @Test
    void zeroCardsIsValid() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Buida,0,,,\r\n"
        );

        assertTrue(outcome.report().valid());
    }

    @Test
    void validatesBoardColumnAndCard()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,CARD,Tauler,Per fer,0,"
                        + "Estudiar,Repassar,0\r\n"
        );

        assertTrue(outcome.report().valid());
        assertEquals(
                1,
                outcome.plan().orElseThrow().totalCards()
        );
    }

    @Test
    void rowOrderDoesNotMatter() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,CARD,Tauler,Per fer,0,"
                        + "Estudiar,,0\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
        );

        assertTrue(outcome.report().valid());
    }

    @Test
    void rejectsReorderedHeader() throws IOException {
        String reordered =
                "record_type,aulaflow_version,board_name,"
                        + "column_name,column_position,"
                        + "card_title,card_description,"
                        + "card_position";

        CsvValidationOutcome outcome = validate(
                reordered + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("invalid_header")
        );
    }

    @Test
    void rejectsExtraHeaderFieldStructurallyAtParseTime()
            throws IOException {
        String withExtra = HEADER + ",extra";

        InputStream input = new ByteArrayInputStream(
                (withExtra + "\r\n").getBytes(
                        StandardCharsets.UTF_8
                )
        );

        CsvParseException exception = org.junit.jupiter
                .api.Assertions.assertThrows(
                        CsvParseException.class,
                        () -> CsvParser.parse(input)
                );

        assertEquals(
                "invalid_field_count",
                exception.code()
        );
    }

    @Test
    void rejectsUnsupportedVersion() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "2,BOARD,Tauler,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("unsupported_version")
        );
    }

    @Test
    void rejectsUnknownRecordType() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,SPRINT,Tauler,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("invalid_record_type")
        );
    }

    @Test
    void rejectsMissingBoardRecord() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("missing_board_record")
        );
    }

    @Test
    void rejectsDuplicateBoardRecord()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,BOARD,Un altre,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode(
                                "duplicate_board_record"
                        )
        );
    }

    @Test
    void rejectsMissingColumnRecord() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
    }

    @Test
    void rejectsDuplicateColumnPosition()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,U,0,,,\r\n"
                        + "1,COLUMN,Tauler,Dos,0,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode(
                                "duplicate_column_position"
                        )
        );
    }

    @Test
    void rejectsNonContiguousColumnPositions()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,U,0,,,\r\n"
                        + "1,COLUMN,Tauler,Dos,2,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "non_contiguous_column_positions"
                )
        );
    }

    @Test
    void allowsRepeatedColumnNamesAtDifferentPositions()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Revisió,0,,,\r\n"
                        + "1,COLUMN,Tauler,Revisió,1,,,\r\n"
        );

        assertTrue(outcome.report().valid());
    }

    @Test
    void allowsIdenticalCardsAtDifferentPositions()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,CARD,Tauler,Per fer,0,"
                        + "Igual,Igual,0\r\n"
                        + "1,CARD,Tauler,Per fer,0,"
                        + "Igual,Igual,1\r\n"
        );

        assertTrue(outcome.report().valid());
        assertEquals(
                2,
                outcome.plan().orElseThrow().totalCards()
        );
    }

    @Test
    void rejectsDuplicateCardPositionWithinColumn()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,CARD,Tauler,Per fer,0,A,,0\r\n"
                        + "1,CARD,Tauler,Per fer,0,B,,0\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "duplicate_card_position"
                )
        );
    }

    @Test
    void rejectsUnknownColumnReference()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,CARD,Tauler,Fantasma,9,A,,0\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "unknown_column_reference"
                )
        );
    }

    @Test
    void rejectsColumnNameMismatch() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,0,,,\r\n"
                        + "1,CARD,Tauler,Nom incorrecte,0,"
                        + "A,,0\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "column_name_mismatch"
                )
        );
    }

    @Test
    void rejectsUnexpectedFieldOnBoardRow()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,Columna,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("unexpected_field")
        );
    }

    @Test
    void rejectsDomainValidationFailureOnBoardName()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,,,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "missing_required_field"
                )
        );
    }

    @Test
    void rejectsOverlongBoardNameAsDomainFailure()
            throws IOException {
        String tooLong = "x".repeat(101);

        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD," + tooLong + ",,,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().hasErrorCode(
                        "domain_validation_failed"
                )
        );
    }

    @Test
    void rejectsInvalidIntegerPosition()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,"
                        + "abc,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("invalid_integer")
        );
    }

    @Test
    void rejectsNegativePosition() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,Tauler,,,,,\r\n"
                        + "1,COLUMN,Tauler,Per fer,"
                        + "-1,,,\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report()
                        .hasErrorCode("negative_position")
        );
    }

    @Test
    void reportsSeveralErrorsAtOnce() throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,COLUMN,Tauler,Per fer,"
                        + "abc,,,\r\n"
                        + "1,CARD,Tauler,Per fer,0,,,0\r\n"
        );

        assertFalse(outcome.report().valid());
        assertTrue(
                outcome.report().errors().size() >= 3
        );
    }

    @Test
    void neutralizesFormulaInBoardNameBeforeValidating()
            throws IOException {
        CsvValidationOutcome outcome = validate(
                HEADER + "\r\n"
                        + "1,BOARD,'=1+1,,,,,\r\n"
                        + "1,COLUMN,'=1+1,Per fer,0,,,\r\n"
        );

        assertTrue(outcome.report().valid());
        assertEquals(
                "=1+1",
                outcome.plan().orElseThrow()
                        .boardName().value()
        );
    }

    private static CsvValidationOutcome validate(
            String csv
    ) throws IOException {
        InputStream input = new ByteArrayInputStream(
                csv.getBytes(StandardCharsets.UTF_8)
        );
        return CsvValidator.validate(
                CsvParser.parse(input)
        );
    }
}
