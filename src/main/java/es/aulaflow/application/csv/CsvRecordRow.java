package es.aulaflow.application.csv;

import java.util.List;
import java.util.Objects;

public record CsvRecordRow(
        int recordNumber,
        List<String> fields
) {

    public CsvRecordRow {
        fields = List.copyOf(
                Objects.requireNonNull(
                        fields,
                        "Los campos no pueden ser null."
                )
        );
    }

    public String field(int index) {
        return fields.get(index);
    }
}
