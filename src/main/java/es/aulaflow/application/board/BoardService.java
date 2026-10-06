package es.aulaflow.application.board;

import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class BoardService {

    private static final List<ColumnName> INITIAL_COLUMNS =
            List.of(
                    new ColumnName("Per fer"),
                    new ColumnName("En curs"),
                    new ColumnName("Fet")
            );

    private final BoardRepository repository;

    public BoardService(BoardRepository repository) {
        this.repository = Objects.requireNonNull(
                repository,
                "El repositorio de tableros no puede ser null."
        );
    }

    public BoardDetails createBoard(
            long ownerId,
            String rawName
    ) {
        validateOwner(ownerId);

        return repository.create(
                ownerId,
                new BoardName(rawName),
                INITIAL_COLUMNS
        );
    }

    public List<Board> listBoards(long ownerId) {
        validateOwner(ownerId);
        return repository.findAllByOwner(ownerId);
    }

    public BoardDetails getBoard(
            long ownerId,
            long rawBoardId
    ) {
        validateOwner(ownerId);

        return repository
                .findByIdAndOwner(
                        new BoardId(rawBoardId),
                        ownerId
                )
                .orElseThrow(
                        BoardNotFoundException::new
                );
    }

    public void renameBoard(
            long ownerId,
            long rawBoardId,
            String rawName
    ) {
        validateOwner(ownerId);

        boolean updated = repository.renameBoard(
                new BoardId(rawBoardId),
                ownerId,
                new BoardName(rawName)
        );

        if (!updated) {
            throw new BoardNotFoundException();
        }
    }

    public BoardColumn createColumn(
            long ownerId,
            long rawBoardId,
            String rawName
    ) {
        validateOwner(ownerId);

        return repository
                .addColumn(
                        new BoardId(rawBoardId),
                        ownerId,
                        new ColumnName(rawName)
                )
                .orElseThrow(
                        BoardNotFoundException::new
                );
    }

    public void renameColumn(
            long ownerId,
            long rawBoardId,
            long rawColumnId,
            String rawName
    ) {
        validateOwner(ownerId);

        boolean updated = repository.renameColumn(
                new BoardId(rawBoardId),
                new ColumnId(rawColumnId),
                ownerId,
                new ColumnName(rawName)
        );

        if (!updated) {
            throw new BoardNotFoundException();
        }
    }

    public void reorderColumns(
            long ownerId,
            long rawBoardId,
            List<Long> rawColumnIds
    ) {
        validateOwner(ownerId);

        Objects.requireNonNull(
                rawColumnIds,
                "El orden no puede ser null."
        );

        List<ColumnId> columnIds =
                rawColumnIds.stream()
                        .map(ColumnId::new)
                        .toList();

        if (
                new HashSet<>(columnIds).size()
                        != columnIds.size()
        ) {
            throw new InvalidColumnOrderException();
        }

        BoardRepository.ReorderResult result =
                repository.reorderColumns(
                        new BoardId(rawBoardId),
                        ownerId,
                        columnIds
                );

        switch (result) {
            case UPDATED -> {
            }
            case BOARD_NOT_FOUND ->
                    throw new BoardNotFoundException();
            case INVALID_ORDER ->
                    throw new InvalidColumnOrderException();
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
