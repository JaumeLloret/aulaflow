package es.aulaflow.application.board;

import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistItemId;
import es.aulaflow.domain.board.ChecklistItemText;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChecklistItemRepository {

    /**
     * Devuelve vacío cuando la tarjeta no existe o no pertenece al
     * propietario indicado. El elemento se añade siempre al final.
     */
    Optional<ChecklistItem> create(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemText text
    );

    List<ChecklistItem> listByCard(
            long ownerId,
            long boardId,
            CardId cardId
    );

    /**
     * Elementos de cada una de las tarjetas indicadas, en una única
     * consulta para evitar N+1 al cargar el detalle de un tablero
     * completo. Las tarjetas sin elementos no aparecen como clave.
     */
    Map<Long, List<ChecklistItem>> listByCards(
            long ownerId,
            long boardId,
            List<CardId> cardIds
    );

    boolean updateText(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            ChecklistItemText text
    );

    boolean setCompleted(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            boolean completed
    );

    ChecklistItemMoveResult move(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId,
            int targetPosition
    );

    boolean delete(
            long ownerId,
            long boardId,
            CardId cardId,
            ChecklistItemId itemId
    );
}
