package es.aulaflow.application.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CsvValidationReport(
        boolean valid,
        String boardName,
        int columnCount,
        int cardCount,
        int totalRecords,
        List<CsvValidationError> errors,
        List<CsvWarning> warnings
) {

    public CsvValidationReport {
        errors = List.copyOf(
                Objects.requireNonNull(
                        errors,
                        "Los errores no pueden ser null."
                )
        );

        warnings = List.copyOf(
                Objects.requireNonNull(
                        warnings,
                        "Los avisos no pueden ser null."
                )
        );

        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un informe válido no puede contener errores."
            );
        }

        if (!valid && errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un informe inválido debe contener al menos un error."
            );
        }
    }

    public static CsvValidationReport singleFatalError(
            int recordNumber,
            String code,
            String message
    ) {
        return new CsvValidationReport(
                false,
                null,
                0,
                0,
                0,
                List.of(
                        new CsvValidationError(
                                recordNumber,
                                "",
                                code,
                                message
                        )
                ),
                List.of()
        );
    }

    public boolean hasErrorCode(String code) {
        return errors.stream()
                .anyMatch(error -> error.code().equals(code));
    }

    public CsvValidationReport withError(
            CsvValidationError error
    ) {
        List<CsvValidationError> combined =
                new ArrayList<>(errors);
        combined.add(error);

        return new CsvValidationReport(
                false,
                boardName,
                columnCount,
                cardCount,
                totalRecords,
                combined,
                warnings
        );
    }
}
