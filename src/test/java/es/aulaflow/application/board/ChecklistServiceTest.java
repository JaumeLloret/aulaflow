package es.aulaflow.application.board;

import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistItemId;
import es.aulaflow.domain.board.ChecklistItemText;
import es.aulaflow.domain.board.ChecklistProgress;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChecklistServiceTest {

    private static ChecklistItem stubItem(
            long id,
            int position,
            boolean completed
    ) {
        Instant now = Instant.EPOCH;
        return new ChecklistItem(
                new ChecklistItemId(id),
                new CardId(1),
                new ChecklistItemText("Element " + id),
                completed,
                position,
                now,
                now
        );
    }

    @Test
    void createItemDelegatesToRepository() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        ChecklistService service = new ChecklistService(repo);

        ChecklistItem result =
                service.createItem(1, 1, 1, "Element 1");

        assertEquals(1, result.id().value());
    }

    @Test
    void createItemThrowsWhenCardNotFound() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.createResult = Optional.empty();
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.createItem(1, 1, 99, "Element")
        );
    }

    @Test
    void progressIsDerivedFromListedItems() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.items = List.of(
                stubItem(1, 0, true),
                stubItem(2, 1, false)
        );
        ChecklistService service = new ChecklistService(repo);

        ChecklistProgress progress =
                service.progress(1, 1, 1);

        assertEquals(2, progress.totalItems());
        assertEquals(1, progress.completedItems());
    }

    @Test
    void updateItemTextThrowsWhenNotFound() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.updateResult = false;
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                ChecklistItemNotFoundException.class,
                () -> service.updateItemText(1, 1, 1, 99, "T")
        );
    }

    @Test
    void setCompletedThrowsWhenNotFound() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.setCompletedResult = false;
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                ChecklistItemNotFoundException.class,
                () -> service.setCompleted(1, 1, 1, 99, true)
        );
    }

    @Test
    void moveItemRejectsNegativePosition() {
        ChecklistService service = new ChecklistService(
                new StubRepository(stubItem(1, 0, false))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.moveItem(1, 1, 1, 1, -1)
        );
    }

    @Test
    void moveItemThrowsOnNotFound() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.moveResult = ChecklistItemMoveResult.NOT_FOUND;
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                ChecklistItemNotFoundException.class,
                () -> service.moveItem(1, 1, 1, 99, 0)
        );
    }

    @Test
    void moveItemThrowsOnInvalidPosition() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.moveResult =
                ChecklistItemMoveResult.INVALID_POSITION;
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.moveItem(1, 1, 1, 1, 5)
        );
    }

    @Test
    void deleteItemThrowsWhenNotFound() {
        StubRepository repo =
                new StubRepository(stubItem(1, 0, false));
        repo.deleteResult = false;
        ChecklistService service = new ChecklistService(repo);

        assertThrows(
                ChecklistItemNotFoundException.class,
                () -> service.deleteItem(1, 1, 1, 99)
        );
    }

    @Test
    void serviceRejectsNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new ChecklistService(null)
        );
    }

    private static final class StubRepository
            implements ChecklistItemRepository {

        Optional<ChecklistItem> createResult;
        List<ChecklistItem> items = List.of();
        boolean updateResult = true;
        boolean setCompletedResult = true;
        boolean deleteResult = true;
        ChecklistItemMoveResult moveResult =
                ChecklistItemMoveResult.MOVED;

        StubRepository(ChecklistItem createReturn) {
            this.createResult =
                    Optional.ofNullable(createReturn);
        }

        @Override
        public Optional<ChecklistItem> create(
                long ownerId,
                long boardId,
                CardId cardId,
                ChecklistItemText text
        ) {
            return createResult;
        }

        @Override
        public List<ChecklistItem> listByCard(
                long ownerId,
                long boardId,
                CardId cardId
        ) {
            return items;
        }

        @Override
        public Map<Long, List<ChecklistItem>> listByCards(
                long ownerId,
                long boardId,
                List<CardId> cardIds
        ) {
            return Map.of();
        }

        @Override
        public boolean updateText(
                long ownerId,
                long boardId,
                CardId cardId,
                ChecklistItemId itemId,
                ChecklistItemText text
        ) {
            return updateResult;
        }

        @Override
        public boolean setCompleted(
                long ownerId,
                long boardId,
                CardId cardId,
                ChecklistItemId itemId,
                boolean completed
        ) {
            return setCompletedResult;
        }

        @Override
        public ChecklistItemMoveResult move(
                long ownerId,
                long boardId,
                CardId cardId,
                ChecklistItemId itemId,
                int targetPosition
        ) {
            return moveResult;
        }

        @Override
        public boolean delete(
                long ownerId,
                long boardId,
                CardId cardId,
                ChecklistItemId itemId
        ) {
            return deleteResult;
        }
    }
}
