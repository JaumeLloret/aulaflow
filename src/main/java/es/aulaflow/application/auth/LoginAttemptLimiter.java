package es.aulaflow.application.auth;

import java.time.Instant;

public interface LoginAttemptLimiter {

    boolean isBlocked(
            String attemptKey,
            Instant instant
    );

    void recordFailure(
            String attemptKey,
            Instant instant
    );

    void reset(String attemptKey);
}
