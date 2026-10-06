package es.aulaflow.application.board;

import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelId;
import es.aulaflow.domain.board.LabelName;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LabelServiceTest {

    private static Label stubLabel(long id) {
        Instant now = Instant.EPOCH;
        return new Label(
                new LabelId(id),
                new BoardId(1),
                new LabelName("Urgent"),
                LabelColor.RED,
                now,
                now
        );
    }

    @Test
    void createLabelDelegatesToRepository() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));

        LabelService service = new LabelService(repo);

        Label result = service.createLabel(
                1, 1, "Urgent", "red"
        );

        assertEquals(1, result.id().value());
    }

    @Test
    void createLabelThrowsWhenBoardNotOwned() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.createResult = Optional.empty();

        LabelService service = new LabelService(repo);

        assertThrows(
                BoardNotFoundException.class,
                () -> service.createLabel(1, 1, "Urgent", "red")
        );
    }

    @Test
    void createLabelRejectsInvalidOwner() {
        LabelService service = new LabelService(
                new StubLabelRepository(stubLabel(1))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createLabel(0, 1, "Urgent", "red")
        );
    }

    @Test
    void createLabelRejectsInvalidColor() {
        LabelService service = new LabelService(
                new StubLabelRepository(stubLabel(1))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createLabel(1, 1, "Urgent", "crimson")
        );
    }

    @Test
    void updateLabelThrowsWhenNotFound() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.updateResult = false;

        LabelService service = new LabelService(repo);

        assertThrows(
                LabelNotFoundException.class,
                () -> service.updateLabel(
                        1, 1, 99, "Urgent", "red"
                )
        );
    }

    @Test
    void deleteLabelThrowsWhenNotFound() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.deleteResult = false;

        LabelService service = new LabelService(repo);

        assertThrows(
                LabelNotFoundException.class,
                () -> service.deleteLabel(1, 1, 99)
        );
    }

    @Test
    void assignLabelAcceptsAlreadyAssignedAsSuccess() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.assignResult =
                LabelRepository.AssignmentResult
                        .ALREADY_ASSIGNED;

        LabelService service = new LabelService(repo);

        service.assignLabel(1, 1, 1, 1);
    }

    @Test
    void assignLabelThrowsWhenCardNotFound() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.assignResult =
                LabelRepository.AssignmentResult
                        .CARD_NOT_FOUND;

        LabelService service = new LabelService(repo);

        assertThrows(
                CardNotFoundException.class,
                () -> service.assignLabel(1, 1, 99, 1)
        );
    }

    @Test
    void assignLabelThrowsWhenLabelNotFound() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.assignResult =
                LabelRepository.AssignmentResult
                        .LABEL_NOT_FOUND;

        LabelService service = new LabelService(repo);

        assertThrows(
                LabelNotFoundException.class,
                () -> service.assignLabel(1, 1, 1, 99)
        );
    }

    @Test
    void unassignLabelThrowsWhenNotFound() {
        StubLabelRepository repo =
                new StubLabelRepository(stubLabel(1));
        repo.unassignResult = false;

        LabelService service = new LabelService(repo);

        assertThrows(
                LabelNotFoundException.class,
                () -> service.unassignLabel(1, 1, 1, 99)
        );
    }

    @Test
    void serviceRejectsNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new LabelService(null)
        );
    }

    private static final class StubLabelRepository
            implements LabelRepository {

        Optional<Label> createResult;
        boolean updateResult = true;
        boolean deleteResult = true;
        boolean unassignResult = true;
        AssignmentResult assignResult = AssignmentResult.ASSIGNED;

        StubLabelRepository(Label createReturn) {
            this.createResult = Optional.ofNullable(createReturn);
        }

        @Override
        public Optional<Label> create(
                long ownerId,
                long boardId,
                LabelName name,
                LabelColor color
        ) {
            return createResult;
        }

        @Override
        public List<Label> listByBoard(
                long ownerId,
                long boardId
        ) {
            return List.of();
        }

        @Override
        public Map<Long, List<Label>> listByCards(
                long ownerId,
                long boardId,
                List<CardId> cardIds
        ) {
            return Map.of();
        }

        @Override
        public boolean update(
                long ownerId,
                long boardId,
                LabelId labelId,
                LabelName name,
                LabelColor color
        ) {
            return updateResult;
        }

        @Override
        public boolean delete(
                long ownerId,
                long boardId,
                LabelId labelId
        ) {
            return deleteResult;
        }

        @Override
        public AssignmentResult assign(
                long ownerId,
                long boardId,
                CardId cardId,
                LabelId labelId
        ) {
            return assignResult;
        }

        @Override
        public boolean unassign(
                long ownerId,
                long boardId,
                CardId cardId,
                LabelId labelId
        ) {
            return unassignResult;
        }
    }
}
