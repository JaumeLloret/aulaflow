package es.aulaflow.application.auth;

import es.aulaflow.domain.identity.Administrator;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.auth.InMemoryLoginAttemptLimiter;
import es.aulaflow.infrastructure.auth.InMemorySessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {

    private static final String USERNAME =
            "teacher";

    private static final String PASSWORD =
            "fictional classroom password";

    private static final PasswordVerifier VERIFIER =
            new PasswordVerifier("test-verifier");

    private static final Instant START =
            Instant.parse("2026-07-30T20:00:00Z");

    private final Administrator administrator =
            new Administrator(
                    1L,
                    new AdministratorUsername(USERNAME)
            );

    private final InMemorySessionStore sessionStore =
            new InMemorySessionStore();

    private final MutableClock clock =
            new MutableClock(START);

    private final QueueSessionIdGenerator idGenerator =
            new QueueSessionIdGenerator();

    private AuthenticationService service;

    @BeforeEach
    void createService() {
        idGenerator.add(
                sessionId('A'),
                sessionId('B')
        );

        service = new AuthenticationService(
                new ExistingAdministratorRepository(
                        administrator,
                        VERIFIER
                ),
                new TestPasswordHasher(),
                sessionStore,
                idGenerator,
                () -> new CsrfToken(
                        "C".repeat(43)
                ),
                new InMemoryLoginAttemptLimiter(),
                clock,
                Duration.ofMinutes(30)
        );
    }

    @Test
    void correctCredentialsCreateServerSession() {
        char[] password =
                PASSWORD.toCharArray();

        AuthenticatedSession session =
                service
                        .login(
                                USERNAME,
                                password
                        )
                        .orElseThrow();

        assertEquals(
                sessionId('A'),
                session.id()
        );

        assertEquals(
                administrator,
                session.administrator()
        );

        assertEquals(
                new CsrfToken("C".repeat(43)),
                session.csrfToken()
        );

        assertEquals(
                START.plus(Duration.ofMinutes(30)),
                session.expiresAt()
        );

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    @Test
    void clientCannotChooseOrReuseSessionIdentifier() {
        AuthenticatedSession first =
                loginSuccessfully();

        AuthenticatedSession second =
                loginSuccessfully();

        assertNotEquals(
                first.id(),
                second.id()
        );
    }

    @Test
    void wrongPasswordDoesNotCreateSession() {
        char[] password =
                "wrong fictional password"
                        .toCharArray();

        assertTrue(
                service
                        .login(
                                USERNAME,
                                password
                        )
                        .isEmpty()
        );

        assertArrayEquals(
                new char[password.length],
                password
        );
    }

    @Test
    void unknownUserPerformsDummyVerification() {
        TestPasswordHasher passwordHasher =
                new TestPasswordHasher();

        AuthenticationService testService =
                new AuthenticationService(
                        new EmptyAdministratorRepository(),
                        passwordHasher,
                        sessionStore,
                        idGenerator,
                        () -> new CsrfToken(
                                "C".repeat(43)
                        ),
                        new InMemoryLoginAttemptLimiter(),
                        clock,
                        Duration.ofMinutes(30)
                );

        assertTrue(
                testService
                        .login(
                                "unknown",
                                PASSWORD.toCharArray()
                        )
                        .isEmpty()
        );

        assertEquals(
                1,
                passwordHasher.verifications
        );
    }

    @Test
    void validSessionExpiresUsingControlledClock() {
        AuthenticatedSession session =
                loginSuccessfully();

        assertTrue(
                service
                        .authenticate(
                                session.id().value()
                        )
                        .isPresent()
        );

        clock.advance(Duration.ofMinutes(30));

        assertTrue(
                service
                        .authenticate(
                                session.id().value()
                        )
                        .isEmpty()
        );

        assertTrue(
                sessionStore
                        .find(session.id())
                        .isEmpty()
        );
    }

    @Test
    void logoutInvalidatesSessionAndIsRepeatable() {
        AuthenticatedSession session =
                loginSuccessfully();

        service.logout(session.id().value());
        service.logout(session.id().value());

        assertTrue(
                service
                        .authenticate(
                                session.id().value()
                        )
                        .isEmpty()
        );
    }

    @Test
    void newSessionStoreSimulatesLogoutAfterRestart() {
        AuthenticatedSession session =
                loginSuccessfully();

        AuthenticationService restartedService =
                new AuthenticationService(
                        new ExistingAdministratorRepository(
                                administrator,
                                VERIFIER
                        ),
                        new TestPasswordHasher(),
                        new InMemorySessionStore(),
                        idGenerator,
                        () -> new CsrfToken(
                                "D".repeat(43)
                        ),
                        new InMemoryLoginAttemptLimiter(),
                        clock,
                        Duration.ofMinutes(30)
                );

        assertTrue(
                restartedService
                        .authenticate(
                                session.id().value()
                        )
                        .isEmpty()
        );
    }

    @Test
    void malformedSessionIdentifierIsRejected() {
        assertTrue(
                service
                        .authenticate("chosen-by-client")
                        .isEmpty()
        );
    }

    private AuthenticatedSession loginSuccessfully() {
        return service
                .login(
                        USERNAME,
                        PASSWORD.toCharArray()
                )
                .orElseThrow();
    }

    private static SessionId sessionId(char value) {
        return new SessionId(
                Character.toString(value)
                        .repeat(43)
        );
    }

    private static final class
    ExistingAdministratorRepository
            implements AdministratorRepository {

        private final StoredAdministrator stored;

        private ExistingAdministratorRepository(
                Administrator administrator,
                PasswordVerifier verifier
        ) {
            stored = new StoredAdministrator(
                    administrator,
                    verifier
            );
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Administrator create(
                AdministratorUsername username,
                PasswordVerifier passwordVerifier
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredAdministrator>
        findByUsername(
                AdministratorUsername username
        ) {
            if (
                    stored
                            .administrator()
                            .username()
                            .equals(username)
            ) {
                return Optional.of(stored);
            }

            return Optional.empty();
        }
    }

    private static final class
    EmptyAdministratorRepository
            implements AdministratorRepository {

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public Administrator create(
                AdministratorUsername username,
                PasswordVerifier passwordVerifier
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredAdministrator>
        findByUsername(
                AdministratorUsername username
        ) {
            return Optional.empty();
        }
    }

    private static final class TestPasswordHasher
            implements PasswordHasher {

        private int verifications;

        @Override
        public PasswordVerifier hash(char[] password) {
            return VERIFIER;
        }

        @Override
        public boolean verify(
                char[] password,
                PasswordVerifier passwordVerifier
        ) {
            verifications++;

            return Arrays.equals(
                    PASSWORD.toCharArray(),
                    password
            ) && passwordVerifier == VERIFIER;
        }

        @Override
        public PasswordVerifier createDummyVerifier() {
            return new PasswordVerifier(
                    "dummy-verifier"
            );
        }
    }

    private static final class
    QueueSessionIdGenerator
            implements SessionIdGenerator {

        private final Queue<SessionId> values =
                new ArrayDeque<>();

        private void add(SessionId... sessionIds) {
            values.addAll(
                    Arrays.asList(sessionIds)
            );
        }

        @Override
        public SessionId generate() {
            return values.remove();
        }
    }

    private static final class MutableClock
            extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
