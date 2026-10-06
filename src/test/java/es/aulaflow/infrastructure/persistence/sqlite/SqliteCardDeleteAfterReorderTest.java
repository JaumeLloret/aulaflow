package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.domain.identity.AdministratorUsername;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduce, para tarjetas, el mismo defecto ya detectado y corregido
 * para elementos de checklist ({@code SqliteChecklistDeleteAfterReorderTest}):
 * compactar posiciones con una única sentencia
 * {@code SET position = position - 1 WHERE position > ?} puede violar
 * {@code UNIQUE(column_id, position)} cuando el orden de {@code rowid}
 * ya no coincide con el orden de {@code position} tras una
 * reordenación previa.
 */
final class SqliteCardDeleteAfterReorderTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void deleteAfterReorderCompactsWithoutUniqueViolation() {
        SqliteConnectionFactory connectionFactory =
                connectionFactory();

        new SqliteMigrator(connectionFactory).migrate();

        new SqliteAdministratorRepository(
                connectionFactory
        ).create(
                new AdministratorUsername("admin"),
                new PasswordVerifier("test")
        );

        BoardRepository boardRepository =
                new SqliteBoardRepository(connectionFactory);

        CardRepository cardRepository =
                new SqliteCardRepository(connectionFactory);

        BoardDetails board = boardRepository.create(
                OWNER_ID,
                new BoardName("Tauler"),
                List.of(new ColumnName("Pendent"))
        );

        long boardId = board.board().id().value();
        long columnId =
                board.columns().getFirst().id().value();

        long firstId = cardRepository.create(
                OWNER_ID,
                boardId,
                new ColumnId(columnId),
                new CardTitle("Primera"),
                new CardDescription("")
        ).id().value();

        long secondId = cardRepository.create(
                OWNER_ID,
                boardId,
                new ColumnId(columnId),
                new CardTitle("Segona"),
                new CardDescription("")
        ).id().value();

        long thirdId = cardRepository.create(
                OWNER_ID,
                boardId,
                new ColumnId(columnId),
                new CardTitle("Tercera"),
                new CardDescription("")
        ).id().value();

        // Mueve la tarjeta central al final: el orden de rowid
        // (creación) deja de coincidir con el orden de position.
        cardRepository.move(
                OWNER_ID,
                boardId,
                new CardId(secondId),
                new ColumnId(columnId),
                2
        );

        assertEquals(
                List.of(firstId, thirdId, secondId),
                ids(cardRepository.listByBoard(
                        OWNER_ID,
                        boardId
                ))
        );

        assertTrue(
                cardRepository.delete(
                        OWNER_ID,
                        boardId,
                        new CardId(firstId)
                ),
                "El borrado no debe fallar por una violación "
                        + "de restricción durante la compactación."
        );

        List<Card> remaining =
                cardRepository.listByBoard(
                        OWNER_ID,
                        boardId
                );

        assertEquals(
                List.of(thirdId, secondId),
                ids(remaining)
        );

        assertEquals(
                List.of(0, 1),
                remaining.stream()
                        .map(Card::position)
                        .toList()
        );
    }

    private SqliteConnectionFactory connectionFactory() {
        return new SqliteConnectionFactory(
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                temporaryDirectory
                                        .resolve("card-delete.db")
                                        .toString()
                        ),
                        temporaryDirectory
                )
        );
    }

    private static List<Long> ids(List<Card> cards) {
        return cards.stream()
                .map(card -> card.id().value())
                .toList();
    }
}
