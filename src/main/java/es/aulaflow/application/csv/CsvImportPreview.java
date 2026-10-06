package es.aulaflow.application.csv;

import java.util.Objects;
import java.util.Optional;

public record CsvImportPreview(
        CsvValidationReport report,
        Optional<String> token
) {

    public CsvImportPreview {
        Objects.requireNonNull(
                report,
                "El informe no puede ser null."
        );

        Objects.requireNonNull(
                token,
                "El token no puede ser null."
        );

        if (report.valid() && token.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un informe válido debe producir un token."
            );
        }

        if (!report.valid() && token.isPresent()) {
            throw new IllegalArgumentException(
                    "Un informe inválido no puede producir "
                            + "un token."
            );
        }
    }
}
