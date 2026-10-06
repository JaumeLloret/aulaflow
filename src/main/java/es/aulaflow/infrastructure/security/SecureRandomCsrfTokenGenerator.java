package es.aulaflow.infrastructure.security;

import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.application.auth.CsrfTokenGenerator;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class SecureRandomCsrfTokenGenerator
        implements CsrfTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureRandomCsrfTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomCsrfTokenGenerator(
            SecureRandom secureRandom
    ) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "La fuente aleatoria no puede ser null."
        );
    }

    @Override
    public CsrfToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return new CsrfToken(
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes)
        );
    }
}
