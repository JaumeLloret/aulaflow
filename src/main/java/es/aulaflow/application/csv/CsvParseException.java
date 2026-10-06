package es.aulaflow.application.csv;

import java.util.Objects;

public final class CsvParseException extends RuntimeException {

    private final int recordNumber;
    private final String code;

    public CsvParseException(
            int recordNumber,
            String code,
            String message
    ) {
        super(message);

        this.recordNumber = recordNumber;

        this.code = Objects.requireNonNull(
                code,
                "El código de error no puede ser null."
        );
    }

    public int recordNumber() {
        return recordNumber;
    }

    public String code() {
        return code;
    }
}
