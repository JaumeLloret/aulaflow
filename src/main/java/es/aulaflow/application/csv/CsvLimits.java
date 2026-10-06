package es.aulaflow.application.csv;

import java.time.Duration;

public final class CsvLimits {

    public static final int MAX_UPLOAD_BYTES =
            1_048_576;

    public static final int MAX_LOGICAL_RECORDS =
            5_000;

    public static final int MAX_COLUMNS =
            100;

    public static final int MAX_CARDS =
            4_899;

    public static final int MAX_PENDING_IMPORTS_PER_SESSION =
            3;

    public static final Duration PENDING_IMPORT_TTL =
            Duration.ofMinutes(10);

    public static final int EXPECTED_FIELD_COUNT =
            8;

    private CsvLimits() {
    }
}
