package es.aulaflow.application.csv;

import java.time.Instant;
import java.util.Optional;

/**
 * Almacén temporal de planes de importación validados, vivos solo en
 * memoria entre la previsualización y la confirmación. Cada plan está
 * ligado a una sesión, un propietario, un token opaco de un solo uso
 * y una expiración.
 */
public interface PendingImportStore {

    String save(
            String sessionId,
            long ownerId,
            CsvImportPlan plan,
            Instant now
    );

    Optional<CsvImportPlan> take(
            String token,
            String sessionId,
            long ownerId,
            Instant now
    );

    int countActive(String sessionId, Instant now);
}
