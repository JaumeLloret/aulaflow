package es.aulaflow.application.board;

import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistItemId;
import es.aulaflow.domain.board.ChecklistItemText;
import es.aulaflow.domain.board.ChecklistProgress;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChecklistService {

    private final ChecklistItemRepository repository;

    public ChecklistService(
            ChecklistItemRepository repository
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "El repositorio de checklist no puede ser null."
        );
    }

    public ChecklistItem createItem(
            long ownerId,
            long boardId,
            long rawCardId,
            String rawText
    ) {
        validateOwner(ownerId);

        return repository
                .create(
                        ownerId,
                        boardId,
                        new CardId(rawCardId),
                        new ChecklistItemText(rawText)
                )
                .orElseThrow(CardNotFoundException::new);
    }

    public List<ChecklistItem> listItems(
            long ownerId,
            long boardId,
            long rawCardId
    ) {
        validateOwner(ownerId);

        return repository.listByCard(
                ownerId,
                boardId,
                new CardId(rawCardId)
        );
    }

    public Map<Long, List<ChecklistItem>> listItemsForCards(
            long ownerId,
            long boardId,
            List<CardId> cardIds
    ) {
        validateOwner(ownerId);
        return repository.listByCards(ownerId, boardId, cardIds);
    }

    public ChecklistProgress progress(
            long ownerId,
            long boardId,
            long rawCardId
    ) {
        return ChecklistProgress.of(
                listItems(ownerId, boardId, rawCardId)
        );
    }

    public void updateItemText(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawItemId,
            String rawText
    ) {
        validateOwner(ownerId);

        boolean updated = repository.updateText(
                ownerId,
                boardId,
                new CardId(rawCardId),
                new ChecklistItemId(rawItemId),
                new ChecklistItemText(rawText)
        );

        if (!updated) {
            throw new ChecklistItemNotFoundException();
        }
    }

    public void setCompleted(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawItemId,
            boolean completed
    ) {
        validateOwner(ownerId);

        boolean updated = repository.setCompleted(
                ownerId,
                boardId,
                new CardId(rawCardId),
                new ChecklistItemId(rawItemId),
                completed
        );

        if (!updated) {
            throw new ChecklistItemNotFoundException();
        }
    }

    public void moveItem(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawItemId,
            int targetPosition
    ) {
        validateOwner(ownerId);

        if (targetPosition < 0) {
            throw new IllegalArgumentException(
                    "La posición de destino debe ser mayor o "
                            + "igual que cero."
            );
        }

        ChecklistItemMoveResult result = repository.move(
                ownerId,
                boardId,
                new CardId(rawCardId),
                new ChecklistItemId(rawItemId),
                targetPosition
        );

        switch (result) {
            case MOVED -> {
            }
            case NOT_FOUND ->
                    throw new ChecklistItemNotFoundException();
            case INVALID_POSITION ->
                    throw new IllegalArgumentException(
                            "La posición de destino no es "
                                    + "válida para ese checklist."
                    );
        }
    }

    public void deleteItem(
            long ownerId,
            long boardId,
            long rawCardId,
            long rawItemId
    ) {
        validateOwner(ownerId);

        boolean deleted = repository.delete(
                ownerId,
                boardId,
                new CardId(rawCardId),
                new ChecklistItemId(rawItemId)
        );

        if (!deleted) {
            throw new ChecklistItemNotFoundException();
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
