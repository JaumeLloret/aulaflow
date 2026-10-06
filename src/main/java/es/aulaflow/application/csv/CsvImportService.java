package es.aulaflow.application.csv;

import es.aulaflow.domain.board.BoardId;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Orquesta la importación en dos fases: previsualización (validar y
 * guardar un plan temporal) y confirmación (recuperar el plan y
 * escribirlo de forma atómica). No escribe nada durante la
 * previsualización.
 */
public final class CsvImportService {

    private final PendingImportStore pendingImportStore;
    private final CsvImportRepository csvImportRepository;
    private final Clock clock;

    public CsvImportService(
            PendingImportStore pendingImportStore,
            CsvImportRepository csvImportRepository
    ) {
        this(
                pendingImportStore,
                csvImportRepository,
                Clock.systemUTC()
        );
    }

    public CsvImportService(
            PendingImportStore pendingImportStore,
            CsvImportRepository csvImportRepository,
            Clock clock
    ) {
        this.pendingImportStore = Objects.requireNonNull(
                pendingImportStore,
                "El almacén de planes no puede ser null."
        );

        this.csvImportRepository = Objects.requireNonNull(
                csvImportRepository,
                "El repositorio de importación no "
                        + "puede ser null."
        );

        this.clock = Objects.requireNonNull(
                clock,
                "El reloj no puede ser null."
        );
    }

    public CsvImportPreview preview(
            InputStream csvBytes,
            String sessionId,
            long ownerId
    ) throws IOException {
        Objects.requireNonNull(csvBytes);
        Objects.requireNonNull(sessionId);

        CsvValidationOutcome outcome;

        try {
            CsvDocument document =
                    CsvParser.parse(csvBytes);
            outcome = CsvValidator.validate(document);
        } catch (CsvParseException exception) {
            CsvValidationReport report =
                    CsvValidationReport.singleFatalError(
                            exception.recordNumber(),
                            exception.code(),
                            exception.getMessage()
                    );

            return new CsvImportPreview(
                    report, Optional.empty()
            );
        }

        if (!outcome.report().valid()) {
            return new CsvImportPreview(
                    outcome.report(), Optional.empty()
            );
        }

        Instant now = clock.instant();

        if (
                pendingImportStore.countActive(
                        sessionId, now
                ) >= CsvLimits
                        .MAX_PENDING_IMPORTS_PER_SESSION
        ) {
            CsvValidationReport limited =
                    outcome.report().withError(
                            new CsvValidationError(
                                    0,
                                    "",
                                    "too_many_pending_imports",
                                    "Se ha alcanzado el "
                                            + "número máximo de "
                                            + "importaciones "
                                            + "pendientes para "
                                            + "esta sesión."
                            )
                    );

            return new CsvImportPreview(
                    limited, Optional.empty()
            );
        }

        String token = pendingImportStore.save(
                sessionId,
                ownerId,
                outcome.plan().orElseThrow(),
                now
        );

        return new CsvImportPreview(
                outcome.report(), Optional.of(token)
        );
    }

    public BoardId confirm(
            String token,
            String sessionId,
            long ownerId
    ) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(sessionId);

        Instant now = clock.instant();

        CsvImportPlan plan = pendingImportStore.take(
                        token, sessionId, ownerId, now
                )
                .orElseThrow(
                        InvalidImportTokenException::new
                );

        return csvImportRepository.importPlan(
                ownerId, plan
        );
    }
}
