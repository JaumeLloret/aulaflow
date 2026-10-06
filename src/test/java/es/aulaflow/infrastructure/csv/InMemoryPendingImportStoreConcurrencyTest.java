package es.aulaflow.infrastructure.csv;

import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.PlannedColumn;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPendingImportStoreConcurrencyTest {

    private static final String SESSION_ID = "session-a";
    private static final long OWNER_ID = 1L;
    private static final Instant NOW =
            Instant.parse("2026-08-03T10:00:00Z");

    @Test
    void tokenCanOnlyBeTakenOnceUnderConcurrentAccess()
            throws Exception {
        InMemoryPendingImportStore store =
                new InMemoryPendingImportStore();

        String token = store.save(
                SESSION_ID,
                OWNER_ID,
                plan(),
                NOW
        );

        int contenders = 64;
        CountDownLatch ready =
                new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> results =
                new ArrayList<>();

        try (
                ExecutorService executor =
                        Executors
                                .newVirtualThreadPerTaskExecutor()
        ) {
            for (int index = 0;
                 index < contenders;
                 index++) {
                results.add(
                        executor.submit(() -> {
                            ready.countDown();
                            assertTrue(
                                    start.await(
                                            5,
                                            TimeUnit.SECONDS
                                    )
                            );

                            return store.take(
                                    token,
                                    SESSION_ID,
                                    OWNER_ID,
                                    NOW
                            ).isPresent();
                        })
                );
            }

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS)
            );
            start.countDown();

            long successfulTakes = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulTakes++;
                }
            }

            assertEquals(1, successfulTakes);
        }
    }

    private static CsvImportPlan plan() {
        return new CsvImportPlan(
                new BoardName("Tauler"),
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
