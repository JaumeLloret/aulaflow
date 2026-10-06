package es.aulaflow.application.csv;

import java.util.Objects;
import java.util.Optional;

public record CsvValidationOutcome(
        CsvValidationReport report,
        Optional<CsvImportPlan> plan
) {

    public CsvValidationOutcome {
        Objects.requireNonNull(
                report,
                "El informe no puede ser null."
        );

        Objects.requireNonNull(
                plan,
                "El plan no puede ser null."
        );

        if (report.valid() && plan.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un informe válido debe producir un plan."
            );
        }

        if (!report.valid() && plan.isPresent()) {
            throw new IllegalArgumentException(
                    "Un informe inválido no puede producir "
                            + "un plan."
            );
        }
    }
}
