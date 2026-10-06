package es.aulaflow.application.csv;

import java.util.Objects;

public record CsvWarning(
        int recordNumber,
        String field,
        String code,
        String message
) {

    public CsvWarning {
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
