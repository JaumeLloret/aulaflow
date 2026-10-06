package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.CardMoveResult;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.application.board.CardNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqliteCardRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;
    private BoardRepository boardRepository;
    private CardRepository cardRepository;

    private long boardId;
    private long col1Id;
    private long col2Id;
    private static final long OWNER = 1L;
    private static final long OTHER_OWNER = 2L;

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
        SqliteMigrator migrator = new SqliteMigrator(factory);
        migrator.migrate();

        new SqliteAdministratorRepository(factory).create(
                new AdministratorUsername("admin"),
                new PasswordVerifier(
                        "pbkdf2-sha256$v1$600000$c2FsdA$ZGVyaXZlZA"
                )
        );

        boardRepository = new SqliteBoardRepository(factory);
        cardRepository = new SqliteCardRepository(factory);

        var details = boardRepository.create(
                OWNER,
                new BoardName("TestBoard"),
                List.of(
                        new ColumnName("Col1"),
                        new ColumnName("Col2")
                )
        );
        boardId = details.board().id().value();
        col1Id = details.columns().get(0).id().value();
        col2Id = details.columns().get(1).id().value();
    }

    @Test
    void createCardAtEndOfColumn() {
        Card c1 = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Primera"),
                new CardDescription("")
        );
        assertEquals(0, c1.position());

        Card c2 = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Segunda"),
                new CardDescription("")
        );
        assertEquals(1, c2.position());
    }

    @Test
    void listByBoardReturnsOrderedCards() {
        cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("A"), new CardDescription(""));
        cardRepository.create(OWNER, boardId,
                new ColumnId(col2Id), new CardTitle("B"), new CardDescription(""));
        cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("C"), new CardDescription(""));

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);

        assertEquals(3, cards.size());
        assertEquals(col1Id, cards.get(0).columnId().value());
        assertEquals("A", cards.get(0).title().value());
        assertEquals(col1Id, cards.get(1).columnId().value());
        assertEquals("C", cards.get(1).title().value());
        assertEquals(col2Id, cards.get(2).columnId().value());
    }

    @Test
    void findByIdReturnsCard() {
        Card created = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Buscar"),
                new CardDescription("desc")
        );

        Optional<Card> found = cardRepository.findById(
                OWNER, boardId, created.id()
        );

        assertTrue(found.isPresent());
        assertEquals("Buscar", found.get().title().value());
        assertEquals("desc", found.get().description().value());
    }

    @Test
    void findByIdRejectsOtherOwner() {
        Card created = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Privada"),
                new CardDescription("")
        );

        Optional<Card> found = cardRepository.findById(
                OTHER_OWNER, boardId, created.id()
        );

        assertTrue(found.isEmpty());
    }

    @Test
    void updateCardPersists() {
        Card card = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Original"),
                new CardDescription("desc original")
        );

        boolean updated = cardRepository.update(
                OWNER, boardId, card.id(),
                new CardTitle("Editada"),
                new CardDescription("desc nueva")
        );

        assertTrue(updated);

        Card rereaded = cardRepository
                .findById(OWNER, boardId, card.id())
                .orElseThrow();
        assertEquals("Editada", rereaded.title().value());
        assertEquals("desc nueva", rereaded.description().value());
    }

    @Test
    void updateRejectsOtherOwner() {
        Card card = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Meva"),
                new CardDescription("")
        );

        boolean updated = cardRepository.update(
                OTHER_OWNER, boardId, card.id(),
                new CardTitle("Hackeada"),
                new CardDescription("")
        );

        assertFalse(updated);
    }

    @Test
    void deleteCardCompactsPositions() {
        Card c0 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("0"), new CardDescription(""));
        Card c1 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("1"), new CardDescription(""));
        Card c2 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("2"), new CardDescription(""));

        boolean deleted = cardRepository.delete(
                OWNER, boardId, c1.id()
        );

        assertTrue(deleted);

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);
        assertEquals(2, cards.size());
        assertEquals(0, cards.get(0).position());
        assertEquals(1, cards.get(1).position());
    }

    @Test
    void deleteRejectsOtherOwner() {
        Card card = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Meva"),
                new CardDescription("")
        );

        boolean deleted = cardRepository.delete(
                OTHER_OWNER, boardId, card.id()
        );

        assertFalse(deleted);
    }

    @Test
    void moveSameColumnDown() {
        Card c0 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("0"), new CardDescription(""));
        Card c1 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("1"), new CardDescription(""));
        Card c2 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("2"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, c0.id(),
                new ColumnId(col1Id), 2
        );

        assertEquals(CardMoveResult.MOVED, result);

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);
        assertEquals("1", cards.get(0).title().value());
        assertEquals("2", cards.get(1).title().value());
        assertEquals("0", cards.get(2).title().value());
    }

    @Test
    void moveSameColumnUp() {
        cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("0"), new CardDescription(""));
        cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("1"), new CardDescription(""));
        Card c2 = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("2"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, c2.id(),
                new ColumnId(col1Id), 0
        );

        assertEquals(CardMoveResult.MOVED, result);

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);
        assertEquals("2", cards.get(0).title().value());
        assertEquals("0", cards.get(1).title().value());
        assertEquals("1", cards.get(2).title().value());
    }

    @Test
    void moveBetweenColumns() {
        Card c = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("X"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, c.id(),
                new ColumnId(col2Id), 0
        );

        assertEquals(CardMoveResult.MOVED, result);

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);
        assertEquals(1, cards.size());
        assertEquals(col2Id, cards.get(0).columnId().value());
        assertEquals(0, cards.get(0).position());
    }

    @Test
    void moveToEmptyColumn() {
        Card c = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("Sola"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, c.id(),
                new ColumnId(col2Id), 0
        );

        assertEquals(CardMoveResult.MOVED, result);

        Card moved = cardRepository
                .findById(OWNER, boardId, c.id())
                .orElseThrow();
        assertEquals(col2Id, moved.columnId().value());
        assertEquals(0, moved.position());
    }

    @Test
    void moveToBeginningOfNonEmptyColumn() {
        Card existing = cardRepository.create(OWNER, boardId,
                new ColumnId(col2Id), new CardTitle("Existing"), new CardDescription(""));
        Card moving = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("Moving"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, moving.id(),
                new ColumnId(col2Id), 0
        );

        assertEquals(CardMoveResult.MOVED, result);

        List<Card> cards = cardRepository.listByBoard(OWNER, boardId);
        List<Card> col2Cards = cards.stream()
                .filter(card -> card.columnId().value() == col2Id)
                .toList();

        assertEquals(2, col2Cards.size());
        assertEquals("Moving", col2Cards.get(0).title().value());
        assertEquals(0, col2Cards.get(0).position());
        assertEquals("Existing", col2Cards.get(1).title().value());
        assertEquals(1, col2Cards.get(1).position());
    }

    @Test
    void moveToEndOfNonEmptyColumn() {
        Card existing =
                cardRepository.create(
                        OWNER,
                        boardId,
                        new ColumnId(col2Id),
                        new CardTitle("Existing"),
                        new CardDescription("")
                );

        Card moving =
                cardRepository.create(
                        OWNER,
                        boardId,
                        new ColumnId(col1Id),
                        new CardTitle("Moving"),
                        new CardDescription("")
                );

        CardMoveResult result =
                cardRepository.move(
                        OWNER,
                        boardId,
                        moving.id(),
                        new ColumnId(col2Id),
                        1
                );

        assertEquals(
                CardMoveResult.MOVED,
                result
        );

        List<Card> targetCards =
                cardRepository
                        .listByBoard(
                                OWNER,
                                boardId
                        )
                        .stream()
                        .filter(
                                card ->
                                        card.columnId().value()
                                                == col2Id
                        )
                        .toList();

        assertEquals(
                2,
                targetCards.size()
        );

        assertEquals(
                existing.id(),
                targetCards.get(0).id()
        );
        assertEquals(
                0,
                targetCards.get(0).position()
        );

        assertEquals(
                moving.id(),
                targetCards.get(1).id()
        );
        assertEquals(
                1,
                targetCards.get(1).position()
        );
    }

    @Test
    void moveRejectsInvalidPosition() {
        Card c = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("T"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OWNER, boardId, c.id(),
                new ColumnId(col1Id), 5
        );

        assertEquals(CardMoveResult.INVALID_POSITION, result);
    }

    @Test
    void moveRejectsCardOfOtherOwner() {
        Card c = cardRepository.create(OWNER, boardId,
                new ColumnId(col1Id), new CardTitle("T"), new CardDescription(""));

        CardMoveResult result = cardRepository.move(
                OTHER_OWNER, boardId, c.id(),
                new ColumnId(col1Id), 0
        );

        assertEquals(CardMoveResult.NOT_FOUND, result);
    }

    @Test
    void createRejectsColumnOutsideOwnedBoard() {
        assertThrows(
                CardNotFoundException.class,
                () -> cardRepository.create(
                        OWNER,
                        boardId,
                        new ColumnId(999L),
                        new CardTitle("T"),
                        new CardDescription("")
                )
        );
    }

    @Test
    void persistenceAfterNewConnection() {
        Card created = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("Persistente"),
                new CardDescription("desc")
        );

        SqliteCardRepository freshRepo =
                new SqliteCardRepository(factory);
        Optional<Card> found = freshRepo.findById(
                OWNER, boardId, created.id()
        );

        assertTrue(found.isPresent());
        assertEquals("Persistente", found.get().title().value());
    }

    @Test
    void timestampsReadFromDatabase() {
        Card created = cardRepository.create(
                OWNER, boardId,
                new ColumnId(col1Id),
                new CardTitle("TS"),
                new CardDescription("")
        );

        assertNotNull(created.createdAt());
        assertNotNull(created.updatedAt());
        assertFalse(created.updatedAt().isBefore(created.createdAt()));
    }

    @Test
    void positionsAreContiguousAfterMove() {
        for (int i = 0; i < 4; i++) {
            cardRepository.create(OWNER, boardId,
                    new ColumnId(col1Id),
                    new CardTitle("Card " + i),
                    new CardDescription("")
            );
        }
        List<Card> before = cardRepository.listByBoard(OWNER, boardId);
        Card card = before.get(3);

        cardRepository.move(OWNER, boardId, card.id(),
                new ColumnId(col1Id), 0);

        List<Card> after = cardRepository.listByBoard(OWNER, boardId);
        for (int i = 0; i < after.size(); i++) {
            assertEquals(i, after.get(i).position(),
                    "La posición " + i + " debe ser contigua");
        }
    }
}
