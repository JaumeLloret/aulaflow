package es.aulaflow.application.csv;

import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.domain.csv.CsvRecordType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validador de negocio del contrato AulaFlow Kanban CSV v1. Acumula
 * todos los errores posibles en vez de detenerse en el primero, para
 * que la persona usuaria pueda corregir el archivo de una sola vez.
 * No depende del orden físico de las filas: valida referencias de
 * forma cruzada tras una primera pasada de recolección.
 */
public final class CsvValidator {

    private static final List<String> EXPECTED_HEADER =
            List.of(
                    "aulaflow_version", "record_type",
                    "board_name", "column_name",
                    "column_position", "card_title",
                    "card_description", "card_position"
            );

    private static final int VERSION = 0;
    private static final int RECORD_TYPE = 1;
    private static final int BOARD_NAME = 2;
    private static final int COLUMN_NAME = 3;
    private static final int COLUMN_POSITION = 4;
    private static final int CARD_TITLE = 5;
    private static final int CARD_DESCRIPTION = 6;
    private static final int CARD_POSITION = 7;

    private CsvValidator() {
    }

    public static CsvValidationOutcome validate(
            CsvDocument document
    ) {
        Objects.requireNonNull(
                document,
                "El documento no puede ser null."
        );

        List<CsvValidationError> errors = new ArrayList<>();
        List<CsvWarning> warnings = new ArrayList<>();

        validateHeader(document.header(), errors);

        List<ParsedBoard> boards = new ArrayList<>();
        List<ParsedColumn> columns = new ArrayList<>();
        List<ParsedCard> cards = new ArrayList<>();

        for (CsvRecordRow row : document.dataRows()) {
            collectRow(row, boards, columns, cards, errors);
        }

        Optional<ParsedBoard> board =
                boards.isEmpty()
                        ? Optional.empty()
                        : Optional.of(boards.getFirst());

        if (board.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "board_name",
                            "missing_board_record",
                            "El archivo CSV no contiene "
                                    + "ningún registro BOARD."
                    )
            );
        }

        if (columns.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "column_position",
                            "missing_column_record",
                            "El archivo CSV no contiene "
                                    + "ningún registro COLUMN."
                    )
            );
        }

        crossValidateBoardNames(
                board, columns, cards, errors
        );

        crossValidateColumnPositions(columns, errors);

        crossValidateCardPositions(columns, cards, errors);

        crossValidateCardReferences(
                columns, cards, errors
        );

        if (columns.size() > CsvLimits.MAX_COLUMNS) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "column_position",
                            "too_many_columns",
                            "El archivo supera el número "
                                    + "máximo de columnas "
                                    + "permitidas."
                    )
            );
        }

        if (cards.size() > CsvLimits.MAX_CARDS) {
            errors.add(
                    new CsvValidationError(
                            0,
                            "card_position",
                            "too_many_cards",
                            "El archivo supera el número "
                                    + "máximo de tarjetas "
                                    + "permitidas."
                    )
            );
        }

        boolean valid = errors.isEmpty();

        String boardNameSummary = board
                .map(ParsedBoard::rawName)
                .orElse(null);

        CsvValidationReport report = new CsvValidationReport(
                valid,
                boardNameSummary,
                columns.size(),
                cards.size(),
                document.rows().size(),
                errors,
                warnings
        );

        if (!valid) {
            return new CsvValidationOutcome(
                    report,
                    Optional.empty()
            );
        }

        CsvImportPlan plan = buildPlan(
                board.orElseThrow(),
                columns,
                cards
        );

        return new CsvValidationOutcome(
                report,
                Optional.of(plan)
        );
    }

    private static void validateHeader(
            CsvRecordRow headerRow,
            List<CsvValidationError> errors
    ) {
        if (!EXPECTED_HEADER.equals(headerRow.fields())) {
            errors.add(
                    new CsvValidationError(
                            headerRow.recordNumber(),
                            "header",
                            "invalid_header",
                            "El encabezado del archivo CSV "
                                    + "no coincide con el "
                                    + "contrato AulaFlow "
                                    + "Kanban CSV v1."
                    )
            );
        }
    }

    private static void collectRow(
            CsvRecordRow row,
            List<ParsedBoard> boards,
            List<ParsedColumn> columns,
            List<ParsedCard> cards,
            List<CsvValidationError> errors
    ) {
        String version = row.field(VERSION);

        if (!"1".equals(version)) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "aulaflow_version",
                            "unsupported_version",
                            "El registro " + row.recordNumber()
                                    + " declara una versión "
                                    + "no admitida."
                    )
            );
            return;
        }

        String rawType = row.field(RECORD_TYPE);
        CsvRecordType type = parseRecordType(rawType);

        if (type == null) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "record_type",
                            "invalid_record_type",
                            "El registro " + row.recordNumber()
                                    + " declara un tipo de "
                                    + "registro desconocido."
                    )
            );
            return;
        }

        switch (type) {
            case BOARD ->
                    collectBoard(row, boards, errors);
            case COLUMN ->
                    collectColumn(row, columns, errors);
            case CARD ->
                    collectCard(row, cards, errors);
        }
    }

    private static CsvRecordType parseRecordType(
            String rawType
    ) {
        try {
            return CsvRecordType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void collectBoard(
            CsvRecordRow row,
            List<ParsedBoard> boards,
            List<CsvValidationError> errors
    ) {
        if (!boards.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "record_type",
                            "duplicate_board_record",
                            "El archivo CSV contiene más "
                                    + "de un registro BOARD."
                    )
            );
            return;
        }

        checkBlank(
                row, COLUMN_NAME, "column_name", errors
        );
        checkBlank(
                row, COLUMN_POSITION, "column_position",
                errors
        );
        checkBlank(
                row, CARD_TITLE, "card_title", errors
        );
        checkBlank(
                row, CARD_DESCRIPTION, "card_description",
                errors
        );
        checkBlank(
                row, CARD_POSITION, "card_position", errors
        );

        String rawBoardName = row.field(BOARD_NAME);

        if (rawBoardName.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "board_name",
                            "missing_required_field",
                            "El registro " + row.recordNumber()
                                    + " no indica el nombre "
                                    + "del tablero."
                    )
            );
            return;
        }

        String semanticName =
                FormulaNeutralizer.unprotect(rawBoardName);

        try {
            BoardName boardName =
                    new BoardName(semanticName);

            boards.add(
                    new ParsedBoard(
                            row.recordNumber(),
                            rawBoardName,
                            boardName
                    )
            );
        } catch (IllegalArgumentException exception) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "board_name",
                            "domain_validation_failed",
                            exception.getMessage()
                    )
            );
        }
    }

    private static void collectColumn(
            CsvRecordRow row,
            List<ParsedColumn> columns,
            List<CsvValidationError> errors
    ) {
        checkBlank(
                row, CARD_TITLE, "card_title", errors
        );
        checkBlank(
                row, CARD_DESCRIPTION, "card_description",
                errors
        );
        checkBlank(
                row, CARD_POSITION, "card_position", errors
        );

        String rawColumnName = row.field(COLUMN_NAME);

        ColumnName columnName;

        if (rawColumnName.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "column_name",
                            "missing_required_field",
                            "El registro " + row.recordNumber()
                                    + " no indica el nombre "
                                    + "de la columna."
                    )
            );
            return;
        }

        String semanticName =
                FormulaNeutralizer.unprotect(rawColumnName);

        try {
            columnName = new ColumnName(semanticName);
        } catch (IllegalArgumentException exception) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "column_name",
                            "domain_validation_failed",
                            exception.getMessage()
                    )
            );
            return;
        }

        Optional<Integer> position = parsePosition(
                row,
                COLUMN_POSITION,
                "column_position",
                "missing_column_position",
                errors
        );

        if (position.isEmpty()) {
            return;
        }

        columns.add(
                new ParsedColumn(
                        row.recordNumber(),
                        row.field(BOARD_NAME),
                        columnName,
                        position.orElseThrow()
                )
        );
    }

    private static void collectCard(
            CsvRecordRow row,
            List<ParsedCard> cards,
            List<CsvValidationError> errors
    ) {
        String rawTitle = row.field(CARD_TITLE);

        CardTitle title;

        if (rawTitle.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "card_title",
                            "missing_required_field",
                            "El registro " + row.recordNumber()
                                    + " no indica el título "
                                    + "de la tarjeta."
                    )
            );
            return;
        }

        try {
            title = new CardTitle(
                    FormulaNeutralizer.unprotect(rawTitle)
            );
        } catch (IllegalArgumentException exception) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "card_title",
                            "domain_validation_failed",
                            exception.getMessage()
                    )
            );
            return;
        }

        CardDescription description;

        try {
            description = new CardDescription(
                    FormulaNeutralizer.unprotect(
                            row.field(CARD_DESCRIPTION)
                    )
            );
        } catch (IllegalArgumentException exception) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            "card_description",
                            "domain_validation_failed",
                            exception.getMessage()
                    )
            );
            return;
        }

        Optional<Integer> columnPosition = parsePosition(
                row,
                COLUMN_POSITION,
                "column_position",
                "missing_column_position",
                errors
        );

        Optional<Integer> position = parsePosition(
                row,
                CARD_POSITION,
                "card_position",
                "missing_card_position",
                errors
        );

        if (columnPosition.isEmpty() || position.isEmpty()) {
            return;
        }

        cards.add(
                new ParsedCard(
                        row.recordNumber(),
                        row.field(BOARD_NAME),
                        row.field(COLUMN_NAME),
                        columnPosition.orElseThrow(),
                        title,
                        description,
                        position.orElseThrow()
                )
        );
    }

    private static Optional<Integer> parsePosition(
            CsvRecordRow row,
            int fieldIndex,
            String fieldName,
            String missingCode,
            List<CsvValidationError> errors
    ) {
        String raw = row.field(fieldIndex);

        if (raw.isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            fieldName,
                            missingCode,
                            "El registro " + row.recordNumber()
                                    + " no indica " + fieldName
                                    + "."
                    )
            );
            return Optional.empty();
        }

        int value;

        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            fieldName,
                            "invalid_integer",
                            "El registro " + row.recordNumber()
                                    + " contiene un valor no "
                                    + "entero en " + fieldName
                                    + "."
                    )
            );
            return Optional.empty();
        }

        if (value < 0) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            fieldName,
                            "negative_position",
                            "El registro " + row.recordNumber()
                                    + " contiene una posición "
                                    + "negativa en " + fieldName
                                    + "."
                    )
            );
            return Optional.empty();
        }

        return Optional.of(value);
    }

    private static void checkBlank(
            CsvRecordRow row,
            int fieldIndex,
            String fieldName,
            List<CsvValidationError> errors
    ) {
        if (!row.field(fieldIndex).isEmpty()) {
            errors.add(
                    new CsvValidationError(
                            row.recordNumber(),
                            fieldName,
                            "unexpected_field",
                            "El registro " + row.recordNumber()
                                    + " no debe rellenar "
                                    + fieldName + "."
                    )
            );
        }
    }

    private static void crossValidateBoardNames(
            Optional<ParsedBoard> board,
            List<ParsedColumn> columns,
            List<ParsedCard> cards,
            List<CsvValidationError> errors
    ) {
        if (board.isEmpty()) {
            return;
        }

        String expected = board.orElseThrow().rawName();

        for (ParsedColumn column : columns) {
            if (!expected.equals(column.rawBoardName())) {
                errors.add(
                        new CsvValidationError(
                                column.recordNumber(),
                                "board_name",
                                "board_name_mismatch",
                                "El registro "
                                        + column.recordNumber()
                                        + " referencia un "
                                        + "board_name distinto "
                                        + "al del registro "
                                        + "BOARD."
                        )
                );
            }
        }

        for (ParsedCard card : cards) {
            if (!expected.equals(card.rawBoardName())) {
                errors.add(
                        new CsvValidationError(
                                card.recordNumber(),
                                "board_name",
                                "board_name_mismatch",
                                "El registro "
                                        + card.recordNumber()
                                        + " referencia un "
                                        + "board_name distinto "
                                        + "al del registro "
                                        + "BOARD."
                        )
                );
            }
        }
    }

    private static void crossValidateColumnPositions(
            List<ParsedColumn> columns,
            List<CsvValidationError> errors
    ) {
        for (int i = 0; i < columns.size(); i++) {
            for (int j = i + 1; j < columns.size(); j++) {
                if (
                        columns.get(i).position()
                                == columns.get(j).position()
                ) {
                    errors.add(
                            new CsvValidationError(
                                    columns.get(j)
                                            .recordNumber(),
                                    "column_position",
                                    "duplicate_column_position",
                                    "El registro "
                                            + columns.get(j)
                                            .recordNumber()
                                            + " repite una "
                                            + "posición de "
                                            + "columna ya "
                                            + "utilizada."
                            )
                    );
                }
            }
        }

        List<Integer> distinctPositions = columns.stream()
                .map(ParsedColumn::position)
                .distinct()
                .sorted()
                .toList();

        for (int expected = 0;
             expected < distinctPositions.size();
             expected++) {
            if (
                    !distinctPositions.get(expected)
                            .equals(expected)
            ) {
                errors.add(
                        new CsvValidationError(
                                0,
                                "column_position",
                                "non_contiguous_column_positions",
                                "Las posiciones de columna no "
                                        + "son contiguas desde "
                                        + "0."
                        )
                );
                break;
            }
        }
    }

    private static void crossValidateCardPositions(
            List<ParsedColumn> columns,
            List<ParsedCard> cards,
            List<CsvValidationError> errors
    ) {
        List<Integer> declaredColumnPositions = columns
                .stream()
                .map(ParsedColumn::position)
                .distinct()
                .toList();

        for (int columnPosition : declaredColumnPositions) {
            List<ParsedCard> cardsInColumn = cards.stream()
                    .filter(card ->
                            card.columnPosition()
                                    == columnPosition
                    )
                    .toList();

            for (int i = 0;
                 i < cardsInColumn.size();
                 i++) {
                for (int j = i + 1;
                     j < cardsInColumn.size();
                     j++) {
                    if (
                            cardsInColumn.get(i).position()
                                    == cardsInColumn.get(j)
                                    .position()
                    ) {
                        errors.add(
                                new CsvValidationError(
                                        cardsInColumn.get(j)
                                                .recordNumber(),
                                        "card_position",
                                        "duplicate_card_position",
                                        "El registro "
                                                + cardsInColumn
                                                .get(j)
                                                .recordNumber()
                                                + " repite una "
                                                + "posición de "
                                                + "tarjeta ya "
                                                + "utilizada en "
                                                + "su columna."
                                )
                        );
                    }
                }
            }

            List<Integer> distinctCardPositions =
                    cardsInColumn.stream()
                            .map(ParsedCard::position)
                            .distinct()
                            .sorted()
                            .toList();

            for (int expected = 0;
                 expected < distinctCardPositions.size();
                 expected++) {
                if (
                        !distinctCardPositions.get(expected)
                                .equals(expected)
                ) {
                    errors.add(
                            new CsvValidationError(
                                    0,
                                    "card_position",
                                    "non_contiguous_card_positions",
                                    "Las posiciones de tarjeta "
                                            + "de la columna en "
                                            + "posición "
                                            + columnPosition
                                            + " no son "
                                            + "contiguas desde "
                                            + "0."
                            )
                    );
                    break;
                }
            }
        }
    }

    private static void crossValidateCardReferences(
            List<ParsedColumn> columns,
            List<ParsedCard> cards,
            List<CsvValidationError> errors
    ) {
        for (ParsedCard card : cards) {
            Optional<ParsedColumn> referenced = columns
                    .stream()
                    .filter(column ->
                            column.position()
                                    == card.columnPosition()
                    )
                    .findFirst();

            if (referenced.isEmpty()) {
                errors.add(
                        new CsvValidationError(
                                card.recordNumber(),
                                "column_position",
                                "unknown_column_reference",
                                "El registro "
                                        + card.recordNumber()
                                        + " referencia una "
                                        + "columna que no "
                                        + "existe."
                        )
                );
                continue;
            }

            String expectedName = FormulaNeutralizer
                    .unprotect(card.rawColumnName());

            if (
                    !referenced.orElseThrow()
                            .name()
                            .value()
                            .equals(expectedName)
            ) {
                errors.add(
                        new CsvValidationError(
                                card.recordNumber(),
                                "column_name",
                                "column_name_mismatch",
                                "El registro "
                                        + card.recordNumber()
                                        + " indica un "
                                        + "column_name que no "
                                        + "coincide con la "
                                        + "columna referenciada."
                        )
                );
            }
        }
    }

    private static CsvImportPlan buildPlan(
            ParsedBoard board,
            List<ParsedColumn> columns,
            List<ParsedCard> cards
    ) {
        List<PlannedColumn> plannedColumns = columns
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                ParsedColumn::position
                        )
                )
                .map(column -> new PlannedColumn(
                        column.name(),
                        column.position(),
                        cards.stream()
                                .filter(card ->
                                        card.columnPosition()
                                                == column
                                                .position()
                                )
                                .sorted(
                                        Comparator
                                                .comparingInt(
                                                        ParsedCard
                                                                ::position
                                                )
                                )
                                .map(card -> new PlannedCard(
                                        card.title(),
                                        card.description(),
                                        card.position()
                                ))
                                .toList()
                ))
                .toList();

        return new CsvImportPlan(
                board.boardName(),
                plannedColumns
        );
    }

    private record ParsedBoard(
            int recordNumber,
            String rawName,
            BoardName boardName
    ) {
    }

    private record ParsedColumn(
            int recordNumber,
            String rawBoardName,
            ColumnName name,
            int position
    ) {
    }

    private record ParsedCard(
            int recordNumber,
            String rawBoardName,
            String rawColumnName,
            int columnPosition,
            CardTitle title,
            CardDescription description,
            int position
    ) {
    }
}
