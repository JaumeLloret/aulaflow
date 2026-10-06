package es.aulaflow.application.csv;

import java.util.List;
import java.util.Objects;

public record CsvDocument(
        List<CsvRecordRow> rows
) {

    public CsvDocument {
        rows = List.copyOf(
                Objects.requireNonNull(
                        rows,
                        "Las filas no pueden ser null."
                )
        );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un documento CSV debe tener al menos una fila."
            );
        }
    }

    public CsvRecordRow header() {
        return rows.getFirst();
    }

    public List<CsvRecordRow> dataRows() {
        return rows.subList(1, rows.size());
    }
}
