package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.SessionId;
import es.aulaflow.application.auth.SessionIdGenerator;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class SecureRandomSessionIdGenerator
        implements SessionIdGenerator {

    static final int RANDOM_BYTES = 32;

    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom;

    public SecureRandomSessionIdGenerator() {
        this(new SecureRandom());
    }

    SecureRandomSessionIdGenerator(
            SecureRandom secureRandom
    ) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "El generador aleatorio no puede ser null."
        );
    }

    @Override
    public SessionId generate() {
        byte[] randomBytes =
                new byte[RANDOM_BYTES];

        secureRandom.nextBytes(randomBytes);

        return new SessionId(
                ENCODER.encodeToString(
                        randomBytes
                )
        );
    }
}
