package es.aulaflow.infrastructure.csv;

import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.CsvLimits;
import es.aulaflow.application.csv.PendingImportStore;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Almacén en memoria de planes de importación pendientes. Sigue el
 * mismo patrón que {@code InMemorySessionStore} e
 * {@code InMemoryLoginAttemptLimiter}: un {@link ConcurrentMap}
 * protegido, sin hilo de limpieza en segundo plano, con limpieza
 * perezosa de entradas caducadas en el momento de acceder.
 */
public final class InMemoryPendingImportStore
        implements PendingImportStore {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    private final ConcurrentMap<String, Entry> pendingImports =
            new ConcurrentHashMap<>();

    public InMemoryPendingImportStore() {
        this(new SecureRandom());
    }

    InMemoryPendingImportStore(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "La fuente aleatoria no puede ser null."
        );
    }

    @Override
    public String save(
            String sessionId,
            long ownerId,
            CsvImportPlan plan,
            Instant now
    ) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(now);

        removeExpired(now);

        String token = generateToken();

        pendingImports.put(
                token,
                new Entry(
                        sessionId,
                        ownerId,
                        plan,
                        now.plus(
                                CsvLimits.PENDING_IMPORT_TTL
                        )
                )
        );

        return token;
    }

    @Override
    public Optional<CsvImportPlan> take(
            String token,
            String sessionId,
            long ownerId,
            Instant now
    ) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(now);

        removeExpired(now);

        Entry entry = pendingImports.get(token);

        if (entry == null) {
            return Optional.empty();
        }

        if (
                !entry.sessionId().equals(sessionId)
                        || entry.ownerId() != ownerId
        ) {
            return Optional.empty();
        }

        if (!pendingImports.remove(token, entry)) {
            return Optional.empty();
        }

        return Optional.of(entry.plan());
    }

    @Override
    public int countActive(String sessionId, Instant now) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(now);

        removeExpired(now);

        return (int) pendingImports.values()
                .stream()
                .filter(entry ->
                        entry.sessionId().equals(sessionId)
                )
                .count();
    }

    private void removeExpired(Instant now) {
        pendingImports.entrySet().removeIf(
                mapEntry -> !now.isBefore(
                        mapEntry.getValue().expiresAt()
                )
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private record Entry(
            String sessionId,
            long ownerId,
            CsvImportPlan plan,
            Instant expiresAt
    ) {
    }
}
