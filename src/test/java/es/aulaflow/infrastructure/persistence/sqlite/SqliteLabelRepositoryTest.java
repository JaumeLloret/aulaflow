package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.CardRepository;
import es.aulaflow.application.board.DuplicateLabelNameException;
import es.aulaflow.application.board.LabelRepository;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelId;
import es.aulaflow.domain.board.LabelName;
import es.aulaflow.domain.identity.AdministratorUsername;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqliteLabelRepositoryTest {

    private static final long OWNER = 1L;
    private static final long OTHER_OWNER = 2L;

    @TempDir
    Path tempDir;

    private SqliteConnectionFactory factory;
    private LabelRepository labelRepository;
    private CardRepository cardRepository;

    private long boardId;
    private long columnId;
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
        labelRepository = new SqliteLabelRepository(factory);

        BoardDetails details = boardRepository.create(
                OWNER,
                new BoardName("TestBoard"),
                List.of(new ColumnName("Col1"))
        );
        boardId = details.board().id().value();
        columnId = details.columns().get(0).id().value();

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
    void createsAndReadsLabel() {
        Label label = labelRepository.create(
                OWNER,
                boardId,
                new LabelName("Urgent"),
                LabelColor.RED
        ).orElseThrow();

        assertEquals("Urgent", label.name().value());
        assertEquals(LabelColor.RED, label.color());
        assertEquals(boardId, label.boardId().value());
    }

    @Test
    void createReturnsEmptyWhenBoardNotOwned() {
        Optional<Label> result = labelRepository.create(
                OTHER_OWNER,
                boardId,
                new LabelName("Urgent"),
                LabelColor.RED
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsDuplicateNormalizedNameInSameBoard() {
        labelRepository.create(
                OWNER,
                boardId,
                new LabelName("Urgent"),
                LabelColor.RED
        );

        assertThrows(
                DuplicateLabelNameException.class,
                () -> labelRepository.create(
                        OWNER,
                        boardId,
                        new LabelName("  URGENT  "),
                        LabelColor.BLUE
                )
        );
    }

    @Test
    void allowsSameNameInDifferentBoards() {
        BoardRepository boardRepository =
                new SqliteBoardRepository(factory);

        long otherBoardId = boardRepository.create(
                OWNER,
                new BoardName("OtherBoard"),
                List.of(new ColumnName("Col1"))
        ).board().id().value();

        labelRepository.create(
                OWNER,
                boardId,
                new LabelName("Urgent"),
                LabelColor.RED
        );

        Optional<Label> secondLabel = labelRepository.create(
                OWNER,
                otherBoardId,
                new LabelName("Urgent"),
                LabelColor.RED
        );

        assertTrue(secondLabel.isPresent());
    }

    @Test
    void listsLabelsOrderedByCreation() {
        labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        );
        labelRepository.create(
                OWNER, boardId,
                new LabelName("Review"), LabelColor.BLUE
        );

        List<Label> labels =
                labelRepository.listByBoard(OWNER, boardId);

        assertEquals(
                List.of("Urgent", "Review"),
                labels.stream()
                        .map(label -> label.name().value())
                        .toList()
        );
    }

    @Test
    void listByBoardReturnsEmptyForForeignBoard() {
        labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        );

        assertTrue(
                labelRepository
                        .listByBoard(OTHER_OWNER, boardId)
                        .isEmpty()
        );
    }

    @Test
    void updatesNameAndColor() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        boolean updated = labelRepository.update(
                OWNER,
                boardId,
                new LabelId(labelId),
                new LabelName("Molt urgent"),
                LabelColor.PURPLE
        );

        assertTrue(updated);

        Label label = labelRepository
                .listByBoard(OWNER, boardId)
                .getFirst();

        assertEquals("Molt urgent", label.name().value());
        assertEquals(LabelColor.PURPLE, label.color());
    }

    @Test
    void updateRejectsOtherOwner() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        assertFalse(
                labelRepository.update(
                        OTHER_OWNER,
                        boardId,
                        new LabelId(labelId),
                        new LabelName("Hackejat"),
                        LabelColor.GRAY
                )
        );
    }

    @Test
    void deletesLabelWithoutRemovingCard() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        assertTrue(
                labelRepository.delete(
                        OWNER, boardId, new LabelId(labelId)
                )
        );

        assertTrue(
                labelRepository
                        .listByBoard(OWNER, boardId)
                        .isEmpty()
        );

        assertTrue(
                cardRepository
                        .findById(
                                OWNER,
                                boardId,
                                new CardId(cardId)
                        )
                        .isPresent()
        );
    }

    @Test
    void assignAndListByCards() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        LabelRepository.AssignmentResult result =
                labelRepository.assign(
                        OWNER,
                        boardId,
                        new CardId(cardId),
                        new LabelId(labelId)
                );

        assertEquals(
                LabelRepository.AssignmentResult.ASSIGNED,
                result
        );

        Map<Long, List<Label>> byCards =
                labelRepository.listByCards(
                        OWNER,
                        boardId,
                        List.of(new CardId(cardId))
                );

        assertEquals(
                List.of("Urgent"),
                byCards.get(cardId).stream()
                        .map(label -> label.name().value())
                        .toList()
        );
    }

    @Test
    void assignIsIdempotent() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        labelRepository.assign(
                OWNER, boardId,
                new CardId(cardId), new LabelId(labelId)
        );

        LabelRepository.AssignmentResult secondAttempt =
                labelRepository.assign(
                        OWNER, boardId,
                        new CardId(cardId), new LabelId(labelId)
                );

        assertEquals(
                LabelRepository.AssignmentResult
                        .ALREADY_ASSIGNED,
                secondAttempt
        );

        assertEquals(
                1,
                labelRepository.listByCards(
                        OWNER,
                        boardId,
                        List.of(new CardId(cardId))
                ).get(cardId).size()
        );
    }

    @Test
    void assignRejectsLabelFromAnotherBoard() {
        BoardRepository boardRepository =
                new SqliteBoardRepository(factory);

        long otherBoardId = boardRepository.create(
                OWNER,
                new BoardName("OtherBoard"),
                List.of(new ColumnName("Col1"))
        ).board().id().value();

        long foreignLabelId = labelRepository.create(
                OWNER, otherBoardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        LabelRepository.AssignmentResult result =
                labelRepository.assign(
                        OWNER,
                        boardId,
                        new CardId(cardId),
                        new LabelId(foreignLabelId)
                );

        assertEquals(
                LabelRepository.AssignmentResult.LABEL_NOT_FOUND,
                result
        );
    }

    @Test
    void assignRejectsCardOfOtherOwner() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        LabelRepository.AssignmentResult result =
                labelRepository.assign(
                        OTHER_OWNER,
                        boardId,
                        new CardId(cardId),
                        new LabelId(labelId)
                );

        assertEquals(
                LabelRepository.AssignmentResult.CARD_NOT_FOUND,
                result
        );
    }

    @Test
    void unassignRemovesAssignment() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        labelRepository.assign(
                OWNER, boardId,
                new CardId(cardId), new LabelId(labelId)
        );

        assertTrue(
                labelRepository.unassign(
                        OWNER,
                        boardId,
                        new CardId(cardId),
                        new LabelId(labelId)
                )
        );

        assertTrue(
                labelRepository.listByCards(
                        OWNER,
                        boardId,
                        List.of(new CardId(cardId))
                ).isEmpty()
        );
    }

    @Test
    void unassignReturnsFalseWhenNotAssigned() {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        assertFalse(
                labelRepository.unassign(
                        OWNER,
                        boardId,
                        new CardId(cardId),
                        new LabelId(labelId)
                )
        );
    }

    @Test
    void deletingCardRemovesItsAssignments()
            throws Exception {
        long labelId = labelRepository.create(
                OWNER, boardId,
                new LabelName("Urgent"), LabelColor.RED
        ).orElseThrow().id().value();

        labelRepository.assign(
                OWNER, boardId,
                new CardId(cardId), new LabelId(labelId)
        );

        cardRepository.delete(
                OWNER, boardId, new CardId(cardId)
        );

        try (
                java.sql.Connection connection =
                        factory.openConnection();
                java.sql.Statement statement =
                        connection.createStatement();
                java.sql.ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM card_labels"
                        )
        ) {
            resultSet.next();
            assertEquals(0, resultSet.getInt(1));
        }
    }
}
