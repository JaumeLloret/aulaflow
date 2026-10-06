package es.aulaflow.application.auth;

import java.util.Optional;

public interface SessionStore {

    void save(AuthenticatedSession session);

    Optional<AuthenticatedSession> find(SessionId sessionId);

    void remove(SessionId sessionId);
}
