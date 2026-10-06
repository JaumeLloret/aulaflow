package es.aulaflow.application.board;

import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;

import java.util.List;
import java.util.Objects;

public final class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = Objects.requireNonNull(
                cardRepository,
                "El repositorio de tarjetas no puede ser null."
        );
    }

    public Card createCard(
            long ownerId,
            long boardId,
            long columnId,
            String rawTitle,
            String rawDescription
    ) {
        validateOwner(ownerId);

        return cardRepository.create(
                ownerId,
                boardId,
                new ColumnId(columnId),
                new CardTitle(rawTitle),
                new CardDescription(rawDescription)
        );
    }

    public List<Card> listCards(
            long ownerId,
            long boardId
    ) {
        validateOwner(ownerId);
        return cardRepository.listByBoard(ownerId, boardId);
    }

    public Card getCard(
            long ownerId,
            long boardId,
            long cardId
    ) {
        validateOwner(ownerId);

        return cardRepository
                .findById(
                        ownerId,
                        boardId,
                        new CardId(cardId)
                )
                .orElseThrow(CardNotFoundException::new);
    }

    public void updateCard(
            long ownerId,
            long boardId,
            long cardId,
            String rawTitle,
            String rawDescription
    ) {
        validateOwner(ownerId);

        boolean updated = cardRepository.update(
                ownerId,
                boardId,
                new CardId(cardId),
                new CardTitle(rawTitle),
                new CardDescription(rawDescription)
        );

        if (!updated) {
            throw new CardNotFoundException();
        }
    }

    public void deleteCard(
            long ownerId,
            long boardId,
            long cardId
    ) {
        validateOwner(ownerId);

        boolean deleted = cardRepository.delete(
                ownerId,
                boardId,
                new CardId(cardId)
        );

        if (!deleted) {
            throw new CardNotFoundException();
        }
    }

    public void moveCard(
            long ownerId,
            long boardId,
            long cardId,
            long targetColumnId,
            int targetPosition
    ) {
        validateOwner(ownerId);

        if (targetPosition < 0) {
            throw new IllegalArgumentException(
                    "La posición de destino debe ser mayor o igual que cero."
            );
        }

        CardMoveResult result = cardRepository.move(
                ownerId,
                boardId,
                new CardId(cardId),
                new ColumnId(targetColumnId),
                targetPosition
        );

        switch (result) {
            case MOVED -> {
            }
            case NOT_FOUND ->
                    throw new CardNotFoundException();
            case INVALID_POSITION ->
                    throw new IllegalArgumentException(
                            "La posición de destino no es válida para esa columna."
                    );
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
