package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.application.board.ChecklistItemMoveResult;
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
import es.aulaflow.infrastructure.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqliteChecklistItemRepositoryTest {

    private static final long OWNER = 1L;
    private static final long OTHER_OWNER = 2L;

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;
    private ChecklistItemRepository repository;
    private CardRepository cardRepository;

    private long boardId;
    private long cardId;

    @BeforeEach
    void setUp() {
        SqliteConfig config = SqliteConfig.from(
                Map.of(
                        "AULAFLOW_DB_PATH",
                        tempDir.resolve("test.db").toString()
                ),
                tempDir
        );
        factory = new SqliteConnectionFactory(config);
        new SqliteMigrator(factory).migrate();

        new SqliteAdministratorRepository(factory).create(
                new AdministratorUsername("admin"),
                new PasswordVerifier(
                        "pbkdf2-sha256$v1$600000$c2FsdA$ZGVyaXZlZA"
                )
        );

        BoardRepository boardRepository =
                new SqliteBoardRepository(factory);
        cardRepository = new SqliteCardRepository(factory);
        repository = new SqliteChecklistItemRepository(factory);

        BoardDetails details = boardRepository.create(
                OWNER,
                new BoardName("TestBoard"),
                List.of(new ColumnName("Col1"))
        );
        boardId = details.board().id().value();
        long columnId = details.columns().get(0).id().value();

        Card card = cardRepository.create(
                OWNER,
                boardId,
                new ColumnId(columnId),
                new CardTitle("Targeta"),
                new CardDescription("")
        );
        cardId = card.id().value();
    }

    @Test
    void createsItemAtEndOfChecklist() {
        ChecklistItem first = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow();

        ChecklistItem second = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Segon")
        ).orElseThrow();

        assertEquals(0, first.position());
        assertEquals(1, second.position());
        assertFalse(first.completed());
    }

    @Test
    void createReturnsEmptyWhenCardNotOwned() {
        var result = repository.create(
                OTHER_OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void listByCardReturnsOrderedItems() {
        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        );
        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Segon")
        );

        List<ChecklistItem> items = repository.listByCard(
                OWNER, boardId, new CardId(cardId)
        );

        assertEquals(
                List.of("Primer", "Segon"),
                items.stream()
                        .map(item -> item.text().value())
                        .toList()
        );
    }

    @Test
    void listByCardsBatchesMultipleCards() {
        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        );

        Map<Long, List<ChecklistItem>> byCards =
                repository.listByCards(
                        OWNER,
                        boardId,
                        List.of(new CardId(cardId))
                );

        assertEquals(1, byCards.get(cardId).size());
    }

    @Test
    void updatesTextAndCompletion() {
        long itemId = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        assertTrue(
                repository.updateText(
                        OWNER, boardId, new CardId(cardId),
                        new ChecklistItemId(itemId),
                        new ChecklistItemText("Actualitzat")
                )
        );

        assertTrue(
                repository.setCompleted(
                        OWNER, boardId, new CardId(cardId),
                        new ChecklistItemId(itemId), true
                )
        );

        ChecklistItem item = repository
                .listByCard(OWNER, boardId, new CardId(cardId))
                .getFirst();

        assertEquals("Actualitzat", item.text().value());
        assertTrue(item.completed());
    }

    @Test
    void updateRejectsOtherOwner() {
        long itemId = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        assertFalse(
                repository.updateText(
                        OTHER_OWNER, boardId,
                        new CardId(cardId),
                        new ChecklistItemId(itemId),
                        new ChecklistItemText("Hackejat")
                )
        );
    }

    @Test
    void deleteCompactsRemainingPositions() {
        long first = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Segon")
        );

        long third = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Tercer")
        ).orElseThrow().id().value();

        assertTrue(
                repository.delete(
                        OWNER, boardId, new CardId(cardId),
                        new ChecklistItemId(first)
                )
        );

        List<ChecklistItem> remaining = repository.listByCard(
                OWNER, boardId, new CardId(cardId)
        );

        assertEquals(
                List.of(0, 1),
                remaining.stream()
                        .map(ChecklistItem::position)
                        .toList()
        );

        assertEquals(
                third,
                remaining.get(1).id().value()
        );
    }

    @Test
    void moveReordersWithinCard() {
        long first = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Segon")
        );

        repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Tercer")
        );

        ChecklistItemMoveResult result = repository.move(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemId(first), 2
        );

        assertEquals(ChecklistItemMoveResult.MOVED, result);

        List<ChecklistItem> items = repository.listByCard(
                OWNER, boardId, new CardId(cardId)
        );

        assertEquals(
                List.of("Segon", "Tercer", "Primer"),
                items.stream()
                        .map(item -> item.text().value())
                        .toList()
        );

        assertEquals(
                List.of(0, 1, 2),
                items.stream()
                        .map(ChecklistItem::position)
                        .toList()
        );
    }

    @Test
    void moveRejectsInvalidPosition() {
        long only = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Únic")
        ).orElseThrow().id().value();

        assertEquals(
                ChecklistItemMoveResult.INVALID_POSITION,
                repository.move(
                        OWNER, boardId, new CardId(cardId),
                        new ChecklistItemId(only), 5
                )
        );
    }

    @Test
    void moveRejectsItemOfOtherOwner() {
        long only = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Únic")
        ).orElseThrow().id().value();

        assertEquals(
                ChecklistItemMoveResult.NOT_FOUND,
                repository.move(
                        OTHER_OWNER, boardId,
                        new CardId(cardId),
                        new ChecklistItemId(only), 0
                )
        );
    }

    @Test
    void reorderFailureMidTransactionLeavesNoPartialState()
            throws Exception {
        long first = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Primer")
        ).orElseThrow().id().value();

        long second = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Segon")
        ).orElseThrow().id().value();

        long third = repository.create(
                OWNER, boardId, new CardId(cardId),
                new ChecklistItemText("Tercer")
        ).orElseThrow().id().value();

        installFailureTrigger(third);

        assertThrows(
                PersistenceException.class,
                () -> repository.move(
                        OWNER, boardId, new CardId(cardId),
                        new ChecklistItemId(first), 2
                )
        );

        List<ChecklistItem> items = repository.listByCard(
                OWNER, boardId, new CardId(cardId)
        );

        assertEquals(
                List.of(first, second, third),
                items.stream()
                        .map(item -> item.id().value())
                        .toList(),
                "El orden original debe conservarse tras el "
                        + "fallo a mitad de la reordenación."
        );

        assertEquals(
                List.of(0, 1, 2),
                items.stream()
                        .map(ChecklistItem::position)
                        .toList(),
                "Las posiciones no deben quedar parciales "
                        + "tras el rollback."
        );
    }

    private void installFailureTrigger(long failingItemId)
            throws Exception {
        String triggerSql = """
                CREATE TRIGGER fail_checklist_final_position
                BEFORE UPDATE OF position ON checklist_items
                WHEN NEW.id = %d AND NEW.position = 1
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Fallo controlado de atomicidad'
                    );
                END
                """.formatted(failingItemId);

        try (
                Connection connection =
                        factory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(triggerSql);
        }
    }
}
