package es.aulaflow.infrastructure.csv;

import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.CsvLimits;
import es.aulaflow.application.csv.PendingImportStore;
import es.aulaflow.application.csv.PlannedColumn;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPendingImportStoreTest {

    private static final String SESSION_A = "session-a";
    private static final String SESSION_B = "session-b";
    private static final long OWNER_A = 1L;
    private static final long OWNER_B = 2L;
    private static final Instant NOW =
            Instant.parse("2026-08-03T10:00:00Z");

    @Test
    void savesAndRetrievesPlanForSameSessionAndOwner() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        CsvImportPlan plan = plan("Tauler");

        String token = store.save(
                SESSION_A, OWNER_A, plan, NOW
        );

        Optional<CsvImportPlan> retrieved = store.take(
                token, SESSION_A, OWNER_A, NOW
        );

        assertTrue(retrieved.isPresent());
        assertEquals(
                "Tauler",
                retrieved.orElseThrow()
                        .boardName().value()
        );
    }

    @Test
    void tokenIsOpaqueAndUnique() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String tokenOne = store.save(
                SESSION_A, OWNER_A, plan("Un"), NOW
        );
        String tokenTwo = store.save(
                SESSION_A, OWNER_A, plan("Dos"), NOW
        );

        assertNotEquals(tokenOne, tokenTwo);
        assertTrue(tokenOne.length() >= 32);
    }

    @Test
    void tokenCanOnlyBeUsedOnce() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_A, OWNER_A, plan("Tauler"), NOW
        );

        assertTrue(
                store.take(
                        token, SESSION_A, OWNER_A, NOW
                ).isPresent()
        );

        assertTrue(
                store.take(
                        token, SESSION_A, OWNER_A, NOW
                ).isEmpty()
        );
    }

    @Test
    void tokenIsRejectedFromAnotherSession() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_A, OWNER_A, plan("Tauler"), NOW
        );

        assertTrue(
                store.take(
                        token, SESSION_B, OWNER_A, NOW
                ).isEmpty()
        );
    }

    @Test
    void tokenIsRejectedForAnotherOwner() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_A, OWNER_A, plan("Tauler"), NOW
        );

        assertTrue(
                store.take(
                        token, SESSION_A, OWNER_B, NOW
                ).isEmpty()
        );
    }

    @Test
    void tokenExpiresAfterTtl() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_A, OWNER_A, plan("Tauler"), NOW
        );

        Instant afterTtl = NOW.plus(
                CsvLimits.PENDING_IMPORT_TTL
        );

        assertTrue(
                store.take(
                        token, SESSION_A, OWNER_A, afterTtl
                ).isEmpty()
        );
    }

    @Test
    void tokenRemainsValidJustBeforeTtl() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_A, OWNER_A, plan("Tauler"), NOW
        );

        Instant justBefore = NOW.plus(
                CsvLimits.PENDING_IMPORT_TTL
        ).minusSeconds(1);

        assertTrue(
                store.take(
                        token, SESSION_A, OWNER_A,
                        justBefore
                ).isPresent()
        );
    }

    @Test
    void countsOnlyActiveImportsForSession() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        store.save(SESSION_A, OWNER_A, plan("Un"), NOW);
        store.save(SESSION_A, OWNER_A, plan("Dos"), NOW);
        store.save(SESSION_B, OWNER_B, plan("Tres"), NOW);

        assertEquals(
                2,
                store.countActive(SESSION_A, NOW)
        );
        assertEquals(
                1,
                store.countActive(SESSION_B, NOW)
        );
    }

    @Test
    void countActiveExcludesExpiredEntries() {
        PendingImportStore store =
                new InMemoryPendingImportStore();

        store.save(SESSION_A, OWNER_A, plan("Un"), NOW);

        Instant afterTtl = NOW.plus(
                CsvLimits.PENDING_IMPORT_TTL
        );

        assertEquals(
                0,
                store.countActive(SESSION_A, afterTtl)
        );
    }

    private static CsvImportPlan plan(String boardName) {
        return new CsvImportPlan(
                new BoardName(boardName),
                List.of(
                        new PlannedColumn(
                                new ColumnName("Per fer"),
                                0,
                                List.of()
                        )
                )
        );
    }
}
