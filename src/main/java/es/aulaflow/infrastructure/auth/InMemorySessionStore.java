package es.aulaflow.infrastructure.auth;

import es.aulaflow.application.auth.AuthenticatedSession;
import es.aulaflow.application.auth.SessionId;
import es.aulaflow.application.auth.SessionStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemorySessionStore
        implements SessionStore {

    private final ConcurrentMap
            <SessionId, AuthenticatedSession> sessions =
            new ConcurrentHashMap<>();

    @Override
    public void save(AuthenticatedSession session) {
        Objects.requireNonNull(
                session,
                "La sesión no puede ser null."
        );

        sessions.put(
                session.id(),
                session
        );
    }

    @Override
    public Optional<AuthenticatedSession> find(
            SessionId sessionId
    ) {
        Objects.requireNonNull(
                sessionId,
                "El identificador no puede ser null."
        );

        return Optional.ofNullable(
                sessions.get(sessionId)
        );
    }

    @Override
    public void remove(SessionId sessionId) {
        Objects.requireNonNull(
                sessionId,
                "El identificador no puede ser null."
        );

        sessions.remove(sessionId);
    }
}
