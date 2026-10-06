package es.aulaflow.application.board;

import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnId;

import java.util.List;
import java.util.Optional;

public interface CardRepository {

    Card create(
            long ownerId,
            long boardId,
            ColumnId columnId,
            CardTitle title,
            CardDescription description
    );

    List<Card> listByBoard(
            long ownerId,
            long boardId
    );

    Optional<Card> findById(
            long ownerId,
            long boardId,
            CardId cardId
    );

    boolean update(
            long ownerId,
            long boardId,
            CardId cardId,
            CardTitle title,
            CardDescription description
    );

    boolean delete(
            long ownerId,
            long boardId,
            CardId cardId
    );

    CardMoveResult move(
            long ownerId,
            long boardId,
            CardId cardId,
            ColumnId targetColumnId,
            int targetPosition
    );
}
