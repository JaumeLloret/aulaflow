package es.aulaflow.application.csv;

import es.aulaflow.domain.board.BoardId;
import es.aulaflow.infrastructure.csv.InMemoryPendingImportStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImportServiceTest {

    private static final String HEADER =
            "aulaflow_version,record_type,board_name,"
                    + "column_name,column_position,"
                    + "card_title,card_description,"
                    + "card_position";

    private static final String VALID_CSV =
            HEADER + "\r\n"
                    + "1,BOARD,Tauler,,,,,\r\n"
                    + "1,COLUMN,Tauler,Per fer,0,,,\r\n";

    private static final String INVALID_CSV =
            HEADER + "\r\n"
                    + "1,COLUMN,Tauler,Per fer,0,,,\r\n";

    @Test
    void previewOfValidCsvProducesTokenAndNoErrors()
            throws IOException {
        RecordingCsvImportRepository repository =
                new RecordingCsvImportRepository();
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                repository
        );

        CsvImportPreview preview = service.preview(
                inputStream(VALID_CSV), "session-1", 1L
        );

        assertTrue(preview.report().valid());
        assertTrue(preview.token().isPresent());
        assertTrue(repository.importedPlans.isEmpty());
    }

    @Test
    void previewOfInvalidCsvHasNoToken()
            throws IOException {
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                new RecordingCsvImportRepository()
        );

        CsvImportPreview preview = service.preview(
                inputStream(INVALID_CSV), "session-1", 1L
        );

        assertFalse(preview.report().valid());
        assertTrue(preview.token().isEmpty());
    }

    @Test
    void previewOfStructurallyBrokenCsvHasNoToken()
            throws IOException {
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                new RecordingCsvImportRepository()
        );

        CsvImportPreview preview = service.preview(
                inputStream("not,a,valid,header\r\n"),
                "session-1",
                1L
        );

        assertFalse(preview.report().valid());
        assertTrue(preview.token().isEmpty());
    }

    @Test
    void confirmWritesPlanAtomicallyAndReturnsBoardId()
            throws IOException {
        RecordingCsvImportRepository repository =
                new RecordingCsvImportRepository();
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                repository
        );

        CsvImportPreview preview = service.preview(
                inputStream(VALID_CSV), "session-1", 1L
        );

        BoardId boardId = service.confirm(
                preview.token().orElseThrow(),
                "session-1",
                1L
        );

        assertEquals(42L, boardId.value());
        assertEquals(
                1,
                repository.importedPlans.size()
        );
        assertEquals(
                "Tauler",
                repository.importedPlans.get(0)
                        .boardName().value()
        );
    }

    @Test
    void confirmRejectsUnknownToken() {
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                new RecordingCsvImportRepository()
        );

        assertThrows(
                InvalidImportTokenException.class,
                () -> service.confirm(
                        "unknown-token", "session-1", 1L
                )
        );
    }

    @Test
    void confirmRejectsTokenFromAnotherSession()
            throws IOException {
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                new RecordingCsvImportRepository()
        );

        CsvImportPreview preview = service.preview(
                inputStream(VALID_CSV), "session-1", 1L
        );

        assertThrows(
                InvalidImportTokenException.class,
                () -> service.confirm(
                        preview.token().orElseThrow(),
                        "session-2",
                        1L
                )
        );
    }

    @Test
    void confirmIsOneTimeUse() throws IOException {
        RecordingCsvImportRepository repository =
                new RecordingCsvImportRepository();
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                repository
        );

        CsvImportPreview preview = service.preview(
                inputStream(VALID_CSV), "session-1", 1L
        );

        String token = preview.token().orElseThrow();

        service.confirm(token, "session-1", 1L);

        assertThrows(
                InvalidImportTokenException.class,
                () -> service.confirm(
                        token, "session-1", 1L
                )
        );
    }

    @Test
    void previewEnforcesMaximumPendingImportsPerSession()
            throws IOException {
        CsvImportService service = new CsvImportService(
                new InMemoryPendingImportStore(),
                new RecordingCsvImportRepository()
        );

        for (
                int index = 0;
                index < CsvLimits
                        .MAX_PENDING_IMPORTS_PER_SESSION;
                index++
        ) {
            CsvImportPreview preview = service.preview(
                    inputStream(VALID_CSV),
                    "session-1",
                    1L
            );
            assertTrue(preview.token().isPresent());
        }

        CsvImportPreview exceeding = service.preview(
                inputStream(VALID_CSV), "session-1", 1L
        );

        assertFalse(exceeding.report().valid());
        assertTrue(
                exceeding.report().hasErrorCode(
                        "too_many_pending_imports"
                )
        );
        assertTrue(exceeding.token().isEmpty());
    }

    private static InputStream inputStream(String text) {
        return new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class RecordingCsvImportRepository
            implements CsvImportRepository {

        private final List<CsvImportPlan> importedPlans =
                new ArrayList<>();

        @Override
        public BoardId importPlan(
                long ownerId,
                CsvImportPlan plan
        ) {
            importedPlans.add(plan);
            return new BoardId(42L);
        }
    }
}
