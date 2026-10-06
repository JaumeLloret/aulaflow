package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.application.board.ChecklistItemRepository;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistItemId;
import es.aulaflow.domain.board.ChecklistItemText;
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

final class SqliteChecklistDeleteAfterReorderTest {

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

        ChecklistItemRepository checklistRepository =
                new SqliteChecklistItemRepository(
                        connectionFactory
                );

        BoardDetails board = boardRepository.create(
                OWNER_ID,
                new BoardName("Tauler"),
                List.of(new ColumnName("Pendent"))
        );

        long boardId = board.board().id().value();
        long columnId =
                board.columns().getFirst().id().value();

        Card card = cardRepository.create(
                OWNER_ID,
                boardId,
                new ColumnId(columnId),
                new CardTitle("Targeta"),
                new CardDescription("")
        );

        CardId cardId = card.id();

        long firstId = checklistRepository.create(
                OWNER_ID,
                boardId,
                cardId,
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        long secondId = checklistRepository.create(
                OWNER_ID,
                boardId,
                cardId,
                new ChecklistItemText("Segon")
        ).orElseThrow().id().value();

        long thirdId = checklistRepository.create(
                OWNER_ID,
                boardId,
                cardId,
                new ChecklistItemText("Tercer")
        ).orElseThrow().id().value();

        checklistRepository.move(
                OWNER_ID,
                boardId,
                cardId,
                new ChecklistItemId(firstId),
                2
        );

        assertEquals(
                List.of(secondId, thirdId, firstId),
                ids(checklistRepository.listByCard(
                        OWNER_ID,
                        boardId,
                        cardId
                ))
        );

        assertTrue(
                checklistRepository.delete(
                        OWNER_ID,
                        boardId,
                        cardId,
                        new ChecklistItemId(secondId)
                )
        );

        List<ChecklistItem> remaining =
                checklistRepository.listByCard(
                        OWNER_ID,
                        boardId,
                        cardId
                );

        assertEquals(
                List.of(thirdId, firstId),
                ids(remaining)
        );

        assertEquals(
                List.of(0, 1),
                remaining.stream()
                        .map(ChecklistItem::position)
                        .toList()
        );
    }

    private SqliteConnectionFactory connectionFactory() {
        return new SqliteConnectionFactory(
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                temporaryDirectory
                                        .resolve("checklist-delete.db")
                                        .toString()
                        ),
                        temporaryDirectory
                )
        );
    }

    private static List<Long> ids(
            List<ChecklistItem> items
    ) {
        return items.stream()
                .map(item -> item.id().value())
                .toList();
    }
}
