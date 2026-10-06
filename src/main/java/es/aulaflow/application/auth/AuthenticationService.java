package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class AuthenticationService {

    public static final Duration DEFAULT_SESSION_DURATION =
            Duration.ofMinutes(30);

    private static final String INVALID_ATTEMPT_KEY =
            "<invalid>";

    private final AdministratorRepository administratorRepository;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;
    private final SessionIdGenerator sessionIdGenerator;
    private final CsrfTokenGenerator csrfTokenGenerator;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final Clock clock;
    private final Duration sessionDuration;
    private final PasswordVerifier dummyVerifier;

    public AuthenticationService(
            AdministratorRepository administratorRepository,
            PasswordHasher passwordHasher,
            SessionStore sessionStore,
            SessionIdGenerator sessionIdGenerator,
            CsrfTokenGenerator csrfTokenGenerator,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this(
                administratorRepository,
                passwordHasher,
                sessionStore,
                sessionIdGenerator,
                csrfTokenGenerator,
                loginAttemptLimiter,
                Clock.systemUTC(),
                DEFAULT_SESSION_DURATION
        );
    }

    public AuthenticationService(
            AdministratorRepository administratorRepository,
            PasswordHasher passwordHasher,
            SessionStore sessionStore,
            SessionIdGenerator sessionIdGenerator,
            CsrfTokenGenerator csrfTokenGenerator,
            LoginAttemptLimiter loginAttemptLimiter,
            Clock clock,
            Duration sessionDuration
    ) {
        this.administratorRepository =
                Objects.requireNonNull(
                        administratorRepository,
                        "El repositorio no puede ser null."
                );

        this.passwordHasher =
                Objects.requireNonNull(
                        passwordHasher,
                        "El derivador no puede ser null."
                );

        this.sessionStore =
                Objects.requireNonNull(
                        sessionStore,
                        "El almacén de sesiones no puede ser null."
                );

        this.sessionIdGenerator =
                Objects.requireNonNull(
                        sessionIdGenerator,
                        "El generador de sesiones no puede ser null."
                );

        this.csrfTokenGenerator =
                Objects.requireNonNull(
                        csrfTokenGenerator,
                        "El generador CSRF no puede ser null."
                );

        this.loginAttemptLimiter =
                Objects.requireNonNull(
                        loginAttemptLimiter,
                        "El limitador de intentos no puede ser null."
                );

        this.clock = Objects.requireNonNull(
                clock,
                "El reloj no puede ser null."
        );

        this.sessionDuration =
                Objects.requireNonNull(
                        sessionDuration,
                        "La duración no puede ser null."
                );

        if (
                sessionDuration.isZero()
                        || sessionDuration.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "La duración de sesión debe ser positiva."
            );
        }

        this.dummyVerifier =
                passwordHasher.createDummyVerifier();
    }

    public Optional<AuthenticatedSession> login(
            String rawUsername,
            char[] password
    ) {
        Objects.requireNonNull(
                password,
                "La contraseña no puede ser null."
        );

        Instant now = clock.instant();

        String attemptKey =
                normalizeAttemptKey(rawUsername);

        if (
                loginAttemptLimiter.isBlocked(
                        attemptKey,
                        now
                )
        ) {
            Arrays.fill(password, '\0');
            return Optional.empty();
        }

        char[] normalizedPassword = null;

        try {
            AdministratorUsername username;

            try {
                username =
                        new AdministratorUsername(
                                rawUsername
                        );
            } catch (RuntimeException exception) {
                performDummyVerification(password);
                recordFailure(attemptKey, now);
                return Optional.empty();
            }

            try {
                normalizedPassword =
                        PasswordPolicy
                                .normalizeAndValidate(
                                        password
                                );
            } catch (RuntimeException exception) {
                performDummyVerification(password);
                recordFailure(attemptKey, now);
                return Optional.empty();
            }

            Optional<StoredAdministrator>
                    storedAdministrator =
                    administratorRepository
                            .findByUsername(username);

            PasswordVerifier verifier =
                    storedAdministrator
                            .map(
                                    StoredAdministrator
                                            ::passwordVerifier
                            )
                            .orElse(dummyVerifier);

            boolean passwordMatches =
                    passwordHasher.verify(
                            normalizedPassword,
                            verifier
                    );

            if (
                    storedAdministrator.isEmpty()
                            || !passwordMatches
            ) {
                recordFailure(attemptKey, now);
                return Optional.empty();
            }

            loginAttemptLimiter.reset(attemptKey);

            Administrator administrator =
                    storedAdministrator
                            .orElseThrow()
                            .administrator();

            AuthenticatedSession session =
                    new AuthenticatedSession(
                            sessionIdGenerator
                                    .generate(),
                            administrator,
                            csrfTokenGenerator
                                    .generate(),
                            now,
                            now.plus(sessionDuration)
                    );

            sessionStore.save(session);

            return Optional.of(session);
        } finally {
            if (normalizedPassword != null) {
                Arrays.fill(
                        normalizedPassword,
                        '\0'
                );
            }

            Arrays.fill(password, '\0');
        }
    }

    public Optional<Administrator> authenticate(
            String rawSessionId
    ) {
        return authenticateSession(rawSessionId)
                .map(
                        AuthenticatedSession
                                ::administrator
                );
    }

    public Optional<AuthenticatedSession>
    authenticateSession(
            String rawSessionId
    ) {
        final SessionId sessionId;

        try {
            sessionId = new SessionId(rawSessionId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        Optional<AuthenticatedSession> session =
                sessionStore.find(sessionId);

        if (session.isEmpty()) {
            return Optional.empty();
        }

        AuthenticatedSession authenticatedSession =
                session.orElseThrow();

        if (
                authenticatedSession.isExpiredAt(
                        clock.instant()
                )
        ) {
            sessionStore.remove(sessionId);
            return Optional.empty();
        }

        return Optional.of(authenticatedSession);
    }

    public void logout(String rawSessionId) {
        final SessionId sessionId;

        try {
            sessionId = new SessionId(rawSessionId);
        } catch (RuntimeException exception) {
            return;
        }

        sessionStore.remove(sessionId);
    }

    private void performDummyVerification(
            char[] password
    ) {
        passwordHasher.verify(
                password,
                dummyVerifier
        );
    }

    private void recordFailure(
            String attemptKey,
            Instant now
    ) {
        loginAttemptLimiter.recordFailure(
                attemptKey,
                now
        );
    }

    private static String normalizeAttemptKey(
            String rawUsername
    ) {
        if (
                rawUsername == null
                        || rawUsername.isBlank()
        ) {
            return INVALID_ATTEMPT_KEY;
        }

        String normalized =
                rawUsername
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.length() > 64) {
            return INVALID_ATTEMPT_KEY;
        }

        return normalized;
    }
}
