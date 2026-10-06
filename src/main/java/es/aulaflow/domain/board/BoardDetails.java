package es.aulaflow.domain.board;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record BoardDetails(
        Board board,
        List<BoardColumn> columns
) {

    public BoardDetails {
        Objects.requireNonNull(
                board,
                "El tablero no puede ser null."
        );

        columns = List.copyOf(
                Objects.requireNonNull(
                        columns,
                        "Las columnas no pueden ser null."
                )
        );

        List<BoardColumn> ordered =
                columns.stream()
                        .sorted(
                                Comparator.comparingInt(
                                        BoardColumn::position
                                )
                        )
                        .toList();

        for (int index = 0; index < ordered.size(); index++) {
            BoardColumn column = ordered.get(index);

            if (
                    !column.boardId().equals(board.id())
                            || column.position() != index
            ) {
                throw new IllegalArgumentException(
                        "Las columnas deben pertenecer al tablero y tener posiciones contiguas."
                );
            }
        }

        columns = ordered;
    }
}
