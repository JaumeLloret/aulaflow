package es.aulaflow.infrastructure.auth;

import es.aulaflow.application.auth.LoginAttemptLimiter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryLoginAttemptLimiter
        implements LoginAttemptLimiter {

    public static final int MAXIMUM_FAILURES = 5;

    public static final Duration WINDOW =
            Duration.ofMinutes(1);

    private final ConcurrentMap<String, AttemptState>
            attempts =
            new ConcurrentHashMap<>();

    @Override
    public boolean isBlocked(
            String attemptKey,
            Instant instant
    ) {
        validate(attemptKey, instant);

        AttemptState state =
                attempts.get(attemptKey);

        if (state == null) {
            return false;
        }

        if (state.windowExpiredAt(instant)) {
            attempts.remove(
                    attemptKey,
                    state
            );

            return false;
        }

        return state.failures()
                >= MAXIMUM_FAILURES;
    }

    @Override
    public void recordFailure(
            String attemptKey,
            Instant instant
    ) {
        validate(attemptKey, instant);

        attempts.compute(
                attemptKey,
                (key, state) -> {
                    if (
                            state == null
                                    || state
                                    .windowExpiredAt(
                                            instant
                                    )
                    ) {
                        return new AttemptState(
                                1,
                                instant
                        );
                    }

                    return new AttemptState(
                            state.failures() + 1,
                            state.windowStartedAt()
                    );
                }
        );
    }

    @Override
    public void reset(String attemptKey) {
        Objects.requireNonNull(
                attemptKey,
                "La clave de intento no puede ser null."
        );

        attempts.remove(attemptKey);
    }

    private static void validate(
            String attemptKey,
            Instant instant
    ) {
        Objects.requireNonNull(
                attemptKey,
                "La clave de intento no puede ser null."
        );

        Objects.requireNonNull(
                instant,
                "El instante no puede ser null."
        );
    }

    private record AttemptState(
            int failures,
            Instant windowStartedAt
    ) {

        private boolean windowExpiredAt(
                Instant instant
        ) {
            return !instant.isBefore(
                    windowStartedAt.plus(WINDOW)
            );
        }
    }
}
