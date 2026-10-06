package es.aulaflow.application.board;

import es.aulaflow.domain.board.CardId;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelId;
import es.aulaflow.domain.board.LabelName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LabelRepository {

    /**
     * Devuelve vacío cuando el tablero no existe o no pertenece al
     * propietario indicado. Lanza {@link DuplicateLabelNameException}
     * cuando ya existe una etiqueta con el mismo nombre normalizado
     * en el tablero.
     */
    Optional<Label> create(
            long ownerId,
            long boardId,
            LabelName name,
            LabelColor color
    );

    List<Label> listByBoard(
            long ownerId,
            long boardId
    );

    /**
     * Etiquetas asignadas a cada una de las tarjetas indicadas, en
     * una única consulta para evitar N+1 al cargar el detalle de un
     * tablero completo. Las tarjetas sin etiquetas no aparecen como
     * clave del mapa.
     */
    Map<Long, List<Label>> listByCards(
            long ownerId,
            long boardId,
            List<CardId> cardIds
    );

    boolean update(
            long ownerId,
            long boardId,
            LabelId labelId,
            LabelName name,
            LabelColor color
    );

    boolean delete(
            long ownerId,
            long boardId,
            LabelId labelId
    );

    AssignmentResult assign(
            long ownerId,
            long boardId,
            CardId cardId,
            LabelId labelId
    );

    boolean unassign(
            long ownerId,
            long boardId,
            CardId cardId,
            LabelId labelId
    );

    enum AssignmentResult {
        ASSIGNED,
        ALREADY_ASSIGNED,
        CARD_NOT_FOUND,
        LABEL_NOT_FOUND
    }
}
