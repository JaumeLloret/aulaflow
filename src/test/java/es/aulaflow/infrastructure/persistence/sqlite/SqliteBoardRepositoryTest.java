package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnId;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.domain.identity.AdministratorUsername;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteBoardRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsBoardAndInitialColumnsAtomically() {
        SqliteBoardRepository repository =
                createRepository("create.db");

        BoardDetails created = repository.create(
                1,
                new BoardName("Projecte"),
                initialColumns()
        );

        assertEquals("Projecte", created.board().name().value());
        assertEquals(
                List.of("Per fer", "En curs", "Fet"),
                created.columns()
                        .stream()
                        .map(column ->
                                column.name().value()
                        )
                        .toList()
        );
        assertEquals(
                List.of(0, 1, 2),
                created.columns()
                        .stream()
                        .map(BoardColumn::position)
                        .toList()
        );
    }

    @Test
    void persistsAndListsOnlyOwnersBoards() {
        String databaseName = "persistent.db";
        SqliteBoardRepository repository =
                createRepository(databaseName);

        BoardDetails created = repository.create(
                1,
                new BoardName("Persistit"),
                initialColumns()
        );

        SqliteBoardRepository reopened =
                openRepository(databaseName);

        assertEquals(
                List.of("Persistit"),
                reopened.findAllByOwner(1)
                        .stream()
                        .map(board ->
                                board.name().value()
                        )
                        .toList()
        );
        assertTrue(
                reopened.findByIdAndOwner(
                        created.board().id(),
                        1
                ).isPresent()
        );
        assertTrue(reopened.findAllByOwner(2).isEmpty());
        assertTrue(
                reopened.findByIdAndOwner(
                        created.board().id(),
                        2
                ).isEmpty()
        );
    }

    @Test
    void renamesBoardAndColumnsWithinOwnerBoundary() {
        SqliteBoardRepository repository =
                createRepository("rename.db");

        BoardDetails created = repository.create(
                1,
                new BoardName("Inicial"),
                initialColumns()
        );

        assertTrue(
                repository.renameBoard(
                        created.board().id(),
                        1,
                        new BoardName("Renombrat")
                )
        );

        ColumnId columnId =
                created.columns().getFirst().id();

        assertTrue(
                repository.renameColumn(
                        created.board().id(),
                        columnId,
                        1,
                        new ColumnName("Pendent")
                )
        );

        assertFalse(
                repository.renameBoard(
                        created.board().id(),
                        2,
                        new BoardName("Alié")
                )
        );

        BoardDetails reloaded =
                repository
                        .findByIdAndOwner(
                                created.board().id(),
                                1
                        )
                        .orElseThrow();

        assertEquals(
                "Renombrat",
                reloaded.board().name().value()
        );
        assertEquals(
                "Pendent",
                reloaded.columns().getFirst()
                        .name().value()
        );
    }

    @Test
    void appendsAndReordersColumnsPersistently() {
        SqliteBoardRepository repository =
                createRepository("order.db");

        BoardDetails created = repository.create(
                1,
                new BoardName("Ordre"),
                initialColumns()
        );

        BoardColumn extra = repository
                .addColumn(
                        created.board().id(),
                        1,
                        new ColumnName("Revisió")
                )
                .orElseThrow();

        List<ColumnId> reverseOrder =
                List.of(
                        extra.id(),
                        created.columns().get(2).id(),
                        created.columns().get(1).id(),
                        created.columns().get(0).id()
                );

        assertEquals(
                BoardRepository.ReorderResult.UPDATED,
                repository.reorderColumns(
                        created.board().id(),
                        1,
                        reverseOrder
                )
        );

        assertEquals(
                reverseOrder,
                repository
                        .findByIdAndOwner(
                                created.board().id(),
                                1
                        )
                        .orElseThrow()
                        .columns()
                        .stream()
                        .map(BoardColumn::id)
                        .toList()
        );
    }

    @Test
    void refusesToAddColumnsOutsideOwnerBoundary() {
        SqliteBoardRepository repository =
                createRepository("column-boundary.db");

        BoardDetails created = repository.create(
                1,
                new BoardName("Tauler protegit"),
                initialColumns()
        );

        assertTrue(
                repository.addColumn(
                        new BoardId(999),
                        1,
                        new ColumnName("No existeix")
                ).isEmpty()
        );

        assertTrue(
                repository.addColumn(
                        created.board().id(),
                        2,
                        new ColumnName("No pertany")
                ).isEmpty()
        );

        BoardDetails reloaded = repository
                .findByIdAndOwner(
                        created.board().id(),
                        1
                )
                .orElseThrow();

        assertEquals(
                3,
                reloaded.columns().size()
        );
    }

    @Test
    void invalidReorderRollsBackWithoutChangingOrder() {
        SqliteBoardRepository repository =
                createRepository("rollback.db");

        BoardDetails created = repository.create(
                1,
                new BoardName("Rollback"),
                initialColumns()
        );

        List<ColumnId> originalOrder =
                created.columns()
                        .stream()
                        .map(BoardColumn::id)
                        .toList();

        assertEquals(
                BoardRepository.ReorderResult.INVALID_ORDER,
                repository.reorderColumns(
                        created.board().id(),
                        1,
                        originalOrder.subList(0, 2)
                )
        );

        assertEquals(
                originalOrder,
                repository
                        .findByIdAndOwner(
                                created.board().id(),
                                1
                        )
                        .orElseThrow()
                        .columns()
                        .stream()
                        .map(BoardColumn::id)
                        .toList()
        );
    }

    private SqliteBoardRepository createRepository(
            String databaseName
    ) {
        SqliteConnectionFactory connectionFactory =
                connectionFactory(databaseName);

        new SqliteMigrator(connectionFactory).migrate();

        new SqliteAdministratorRepository(
                connectionFactory
        ).create(
                new AdministratorUsername("teacher"),
                new PasswordVerifier(
                        "pbkdf2-sha256$v1$600000$"
                                + "c2FsdA$ZGVyaXZlZA"
                )
        );

        return new SqliteBoardRepository(
                connectionFactory
        );
    }

    private SqliteBoardRepository openRepository(
            String databaseName
    ) {
        return new SqliteBoardRepository(
                connectionFactory(databaseName)
        );
    }

    private SqliteConnectionFactory connectionFactory(
            String databaseName
    ) {
        SqliteConfig config = SqliteConfig.from(
                Map.of(
                        "AULAFLOW_DB_PATH",
                        temporaryDirectory
                                .resolve(databaseName)
                                .toString()
                ),
                temporaryDirectory
        );

        return new SqliteConnectionFactory(config);
    }

    private static List<ColumnName> initialColumns() {
        return List.of(
                new ColumnName("Per fer"),
                new ColumnName("En curs"),
                new ColumnName("Fet")
        );
    }
}
