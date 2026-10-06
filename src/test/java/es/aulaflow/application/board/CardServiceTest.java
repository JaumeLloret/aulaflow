package es.aulaflow.application.board;

import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CardServiceTest {

    private static Card stubCard(long id, long colId, int pos) {
        Instant now = Instant.EPOCH;
        return new Card(
                new CardId(id),
                new ColumnId(colId),
                new CardTitle("Tarjeta " + id),
                new CardDescription(""),
                pos,
                now,
                now
        );
    }

    @Test
    void createCardDelegatesToRepository() {
        Card expected = stubCard(1, 10, 0);
        StubCardRepository repo = new StubCardRepository(expected);
        CardService service = new CardService(repo);

        Card result = service.createCard(1, 1, 10, "Tarjeta 1", "");

        assertNotNull(result);
        assertEquals(expected.id(), result.id());
    }

    @Test
    void createCardRejectsInvalidOwner() {
        CardService service = new CardService(
                new StubCardRepository(stubCard(1, 1, 0))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createCard(0, 1, 1, "T", "")
        );
    }

    @Test
    void listCardsReturnsRepositoryResult() {
        Card c1 = stubCard(1, 10, 0);
        Card c2 = stubCard(2, 10, 1);
        StubCardRepository repo = new StubCardRepository(null);
        repo.cards = List.of(c1, c2);
        CardService service = new CardService(repo);

        List<Card> result = service.listCards(1, 1);

        assertEquals(2, result.size());
    }

    @Test
    void getCardThrowsWhenNotFound() {
        StubCardRepository repo = new StubCardRepository(null);
        repo.findResult = Optional.empty();
        CardService service = new CardService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.getCard(1, 1, 99)
        );
    }

    @Test
    void updateCardThrowsWhenNotFound() {
        StubCardRepository repo = new StubCardRepository(null);
        repo.updateResult = false;
        CardService service = new CardService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.updateCard(1, 1, 99, "T", "")
        );
    }

    @Test
    void deleteCardThrowsWhenNotFound() {
        StubCardRepository repo = new StubCardRepository(null);
        repo.deleteResult = false;
        CardService service = new CardService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.deleteCard(1, 1, 99)
        );
    }

    @Test
    void moveCardThrowsOnNotFound() {
        StubCardRepository repo = new StubCardRepository(null);
        repo.moveResult = CardMoveResult.NOT_FOUND;
        CardService service = new CardService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.moveCard(1, 1, 1, 1, 0)
        );
    }

    @Test
    void moveCardThrowsOnInvalidPosition() {
        StubCardRepository repo = new StubCardRepository(null);
        repo.moveResult = CardMoveResult.INVALID_POSITION;
        CardService service = new CardService(repo);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.moveCard(1, 1, 1, 1, 0)
        );
    }

    @Test
    void moveCardRejectsNegativePosition() {
        CardService service = new CardService(
                new StubCardRepository(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.moveCard(1, 1, 1, 1, -1)
        );
    }

    @Test
    void serviceRejectsNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new CardService(null)
        );
    }

    private static final class StubCardRepository
            implements CardRepository {

        private final Card createReturn;
        List<Card> cards = new ArrayList<>();
        Optional<Card> findResult;
        boolean updateResult = true;
        boolean deleteResult = true;
        CardMoveResult moveResult = CardMoveResult.MOVED;

        StubCardRepository(Card createReturn) {
            this.createReturn = createReturn;
            this.findResult = Optional.ofNullable(createReturn);
        }

        @Override
        public Card create(
                long ownerId,
                long boardId,
                ColumnId columnId,
                CardTitle title,
                CardDescription description
        ) {
            return createReturn;
        }

        @Override
        public List<Card> listByBoard(
                long ownerId,
                long boardId
        ) {
            return cards;
        }

        @Override
        public Optional<Card> findById(
                long ownerId,
                long boardId,
                CardId cardId
        ) {
            return findResult;
        }

        @Override
        public boolean update(
                long ownerId,
                long boardId,
                CardId cardId,
                CardTitle title,
                CardDescription description
        ) {
            return updateResult;
        }

        @Override
        public boolean delete(
                long ownerId,
                long boardId,
                CardId cardId
        ) {
            return deleteResult;
        }

        @Override
        public CardMoveResult move(
                long ownerId,
                long boardId,
                CardId cardId,
                ColumnId targetColumnId,
                int targetPosition
        ) {
            return moveResult;
        }
    }
}
