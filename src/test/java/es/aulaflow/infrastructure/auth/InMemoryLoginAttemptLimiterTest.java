package es.aulaflow.infrastructure.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLoginAttemptLimiterTest {

    private static final String ATTEMPT_KEY =
            "teacher";

    private static final Instant START =
            Instant.parse("2026-07-30T20:00:00Z");

    private final InMemoryLoginAttemptLimiter limiter =
            new InMemoryLoginAttemptLimiter();

    @Test
    void blocksAfterFiveFailuresInsideWindow() {
        for (
                int attempt = 0;
                attempt
                        < InMemoryLoginAttemptLimiter
                        .MAXIMUM_FAILURES;
                attempt++
        ) {
            assertFalse(
                    limiter.isBlocked(
                            ATTEMPT_KEY,
                            START
                    )
            );

            limiter.recordFailure(
                    ATTEMPT_KEY,
                    START
            );
        }

        assertTrue(
                limiter.isBlocked(
                        ATTEMPT_KEY,
                        START
                )
        );
    }

    @Test
    void allowsAttemptsAfterWindowExpires() {
        for (
                int attempt = 0;
                attempt
                        < InMemoryLoginAttemptLimiter
                        .MAXIMUM_FAILURES;
                attempt++
        ) {
            limiter.recordFailure(
                    ATTEMPT_KEY,
                    START
            );
        }

        assertFalse(
                limiter.isBlocked(
                        ATTEMPT_KEY,
                        START.plus(
                                InMemoryLoginAttemptLimiter
                                        .WINDOW
                        )
                )
        );
    }

    @Test
    void successfulLoginResetsFailures() {
        limiter.recordFailure(
                ATTEMPT_KEY,
                START
        );

        limiter.reset(ATTEMPT_KEY);

        assertFalse(
                limiter.isBlocked(
                        ATTEMPT_KEY,
                        START
                )
        );
    }
}
