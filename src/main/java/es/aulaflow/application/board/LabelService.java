package es.aulaflow.application.board;

import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelId;
import es.aulaflow.domain.board.LabelName;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LabelService {

    private final LabelRepository repository;

    public LabelService(LabelRepository repository) {
        this.repository = Objects.requireNonNull(
                repository,
                "El repositorio de etiquetas no puede ser null."
        );
    }

    public Label createLabel(
            long ownerId,
            long boardId,
            String rawName,
            String rawColor
    ) {
        validateOwner(ownerId);

        return repository
                .create(
                        ownerId,
                        boardId,
                        new LabelName(rawName),
                        LabelColor.fromKey(rawColor)
                )
                .orElseThrow(BoardNotFoundException::new);
    }

    public List<Label> listLabels(
            long ownerId,
            long boardId
    ) {
        validateOwner(ownerId);
        return repository.listByBoard(ownerId, boardId);
    }

    public Map<Long, List<Label>> listLabelsForCards(
            long ownerId,
            long boardId,
            List<CardId> cardIds
    ) {
        validateOwner(ownerId);
        return repository.listByCards(
                ownerId,
                boardId,
                cardIds
        );
    }

    public void updateLabel(
            long ownerId,
            long boardId,
            long rawLabelId,
            String rawName,
            String rawColor
    ) {
        validateOwner(ownerId);

        boolean updated = repository.update(
                ownerId,
                boardId,
                new LabelId(rawLabelId),
                new LabelName(rawName),
                LabelColor.fromKey(rawColor)
        );

        if (!updated) {
            throw new LabelNotFoundException();
        }
    }

    public void deleteLabel(
            long ownerId,
            long boardId,
            long rawLabelId
    ) {
        validateOwner(ownerId);

        boolean deleted = repository.delete(
                ownerId,
                boardId,
                new LabelId(rawLabelId)
        );

        if (!deleted) {
            throw new LabelNotFoundException();
        }
    }

    public void assignLabel(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawLabelId
    ) {
        validateOwner(ownerId);

        LabelRepository.AssignmentResult result =
                repository.assign(
                        ownerId,
                        boardId,
                        new CardId(rawCardId),
                        new LabelId(rawLabelId)
                );

        switch (result) {
            case ASSIGNED, ALREADY_ASSIGNED -> {
            }
            case CARD_NOT_FOUND ->
                    throw new CardNotFoundException();
            case LABEL_NOT_FOUND ->
                    throw new LabelNotFoundException();
        }
    }

    public void unassignLabel(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawLabelId
    ) {
        validateOwner(ownerId);

        boolean removed = repository.unassign(
                ownerId,
                boardId,
                new CardId(rawCardId),
                new LabelId(rawLabelId)
        );

        if (!removed) {
            throw new LabelNotFoundException();
        }
    }

    private static void validateOwner(long ownerId) {
        if (ownerId < 1) {
            throw new IllegalArgumentException(
                    "El propietario debe ser positivo."
            );
        }
    }
}
