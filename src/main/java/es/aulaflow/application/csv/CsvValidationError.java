package es.aulaflow.application.csv;

import java.util.Objects;

public record CsvValidationError(
        int recordNumber,
        String field,
        String code,
        String message
) {

    public CsvValidationError {
        Objects.requireNonNull(
                field,
                "El campo no puede ser null."
        );

        Objects.requireNonNull(
                code,
                "El código no puede ser null."
        );

        Objects.requireNonNull(
                message,
                "El mensaje no puede ser null."
        );
    }
}
