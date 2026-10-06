package es.aulaflow.application.board;

import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;

import java.util.List;
import java.util.Optional;

public interface BoardRepository {

    BoardDetails create(
            long ownerId,
            BoardName name,
            List<ColumnName> initialColumns
    );

    List<Board> findAllByOwner(long ownerId);

    Optional<BoardDetails> findByIdAndOwner(
            BoardId boardId,
            long ownerId
    );

    boolean renameBoard(
            BoardId boardId,
            long ownerId,
            BoardName name
    );

    Optional<BoardColumn> addColumn(
            BoardId boardId,
            long ownerId,
            ColumnName name
    );

    boolean renameColumn(
            BoardId boardId,
            ColumnId columnId,
            long ownerId,
            ColumnName name
    );

    ReorderResult reorderColumns(
            BoardId boardId,
            long ownerId,
            List<ColumnId> orderedColumnIds
    );

    enum ReorderResult {
        UPDATED,
        BOARD_NOT_FOUND,
        INVALID_ORDER
    }
}
