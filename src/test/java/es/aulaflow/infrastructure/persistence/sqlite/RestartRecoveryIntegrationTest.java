package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.auth.ProvisionInitialAdministrator;
import es.aulaflow.application.auth.StoredAdministrator;
import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.board.ChecklistService;
import es.aulaflow.application.board.LabelService;
import es.aulaflow.domain.board.Board;
import es.aulaflow.domain.board.BoardColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.ChecklistItem;
import es.aulaflow.domain.board.ChecklistProgress;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.identity.AdministratorUsername;
import es.aulaflow.infrastructure.security.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduce, con la misma composición que {@code AulaFlowApplication},
 * un reinicio real de AulaFlow: el segundo ciclo construye un grafo de
 * objetos completamente nuevo sobre el mismo archivo SQLite y no debe
 * depender de ninguna instancia, conexión o caché del primer ciclo.
 */
class RestartRecoveryIntegrationTest {

    private static final String ADMIN_USERNAME = "profesora.aulaflow";

    private static final char[] ADMIN_PASSWORD =
            "una-contrasenya-molt-llarga-1".toCharArray();

    @TempDir
    Path temporaryDirectory;

    @Test
    void recoversIdentityBoardsColumnsCardsAndOrderAfterRestart() {
        Path databasePath =
                temporaryDirectory.resolve(
                        "restart-recovery.db"
                );

        RecordedState expected =
                runFirstCycle(databasePath);

        verifySecondCycle(
                databasePath,
                expected
        );
    }

    /**
     * Ejecuta el primer ciclo de la aplicación: migra, aprovisiona el
     * administrador, crea el escenario completo y devuelve únicamente
     * los valores esperados. Ninguna referencia a los objetos de este
     * ciclo (conexión, repositorios, servicios) sale de este método.
     */
    private RecordedState runFirstCycle(Path databasePath) {
        SqliteConnectionFactory connectionFactory =
                openFactory(databasePath);

        new SqliteMigrator(connectionFactory).migrate();

        SqliteAdministratorRepository administratorRepository =
                new SqliteAdministratorRepository(
                        connectionFactory
                );

        assertFalse(administratorRepository.exists());

        boolean provisioned = new ProvisionInitialAdministrator(
                administratorRepository,
                new Pbkdf2PasswordHasher()
        ).execute(
                ADMIN_USERNAME,
                ADMIN_PASSWORD.clone()
        );

        assertTrue(provisioned);

        long ownerId = administratorRepository
                .findByUsername(
                        new AdministratorUsername(ADMIN_USERNAME)
                )
                .orElseThrow()
                .administrator()
                .id();

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        BoardDetails board = boardService.createBoard(
                ownerId,
                "Programació 2n DAM"
        );

        long boardId = board.board().id().value();

        List<BoardColumn> columns = board.columns();
        long todoColumnId = columns.get(0).id().value();
        long doingColumnId = columns.get(1).id().value();
        long doneColumnId = columns.get(2).id().value();

        Card firstCard = cardService.createCard(
                ownerId,
                boardId,
                todoColumnId,
                "Preparar enunciat de pràctiques",
                "Redactar l'enunciat de la pràctica de persistència."
        );

        Card secondCard = cardService.createCard(
                ownerId,
                boardId,
                todoColumnId,
                "Revisar migracions",
                "Comprovar que V001-V008 queden documentades."
        );

        cardService.createCard(
                ownerId,
                boardId,
                doingColumnId,
                "Corregir lliuraments",
                "Pendent de corregir el grup B."
        );

        cardService.updateCard(
                ownerId,
                boardId,
                firstCard.id().value(),
                "Preparar enunciat de pràctiques (v2)",
                "Redactar i revisar l'enunciat abans de publicar-lo."
        );

        cardService.moveCard(
                ownerId,
                boardId,
                secondCard.id().value(),
                todoColumnId,
                0
        );

        cardService.moveCard(
                ownerId,
                boardId,
                firstCard.id().value(),
                doneColumnId,
                0
        );

        boardService.reorderColumns(
                ownerId,
                boardId,
                List.of(
                        doneColumnId,
                        todoColumnId,
                        doingColumnId
                )
        );

        LabelService labelService = new LabelService(
                new SqliteLabelRepository(connectionFactory)
        );

        ChecklistService checklistService = new ChecklistService(
                new SqliteChecklistItemRepository(
                        connectionFactory
                )
        );

        long urgentLabelId = labelService.createLabel(
                ownerId,
                boardId,
                "Urgent 🔥",
                "red"
        ).id().value();

        long reviewLabelId = labelService.createLabel(
                ownerId,
                boardId,
                "Revisió",
                "blue"
        ).id().value();

        labelService.assignLabel(
                ownerId, boardId,
                firstCard.id().value(), urgentLabelId
        );

        labelService.assignLabel(
                ownerId, boardId,
                secondCard.id().value(), urgentLabelId
        );

        labelService.assignLabel(
                ownerId, boardId,
                firstCard.id().value(), reviewLabelId
        );

        long checklistFirstId = checklistService.createItem(
                ownerId, boardId, firstCard.id().value(),
                "Redactar l'enunciat"
        ).id().value();

        checklistService.createItem(
                ownerId, boardId, firstCard.id().value(),
                "Revisar exemples 📐"
        );

        long checklistThirdId = checklistService.createItem(
                ownerId, boardId, firstCard.id().value(),
                "Publicar al tauler"
        ).id().value();

        checklistService.setCompleted(
                ownerId, boardId, firstCard.id().value(),
                checklistFirstId, true
        );

        checklistService.moveItem(
                ownerId, boardId, firstCard.id().value(),
                checklistThirdId, 0
        );

        List<Card> cardsAfterFirstCycle =
                cardService.listCards(ownerId, boardId);

        return new RecordedState(
                ownerId,
                boardId,
                board.board().name().value(),
                List.of(
                        doneColumnId,
                        todoColumnId,
                        doingColumnId
                ),
                cardsAfterFirstCycle.stream()
                        .map(card -> RecordedCard.from(
                                card,
                                labelService.listLabelsForCards(
                                        ownerId,
                                        boardId,
                                        cardsAfterFirstCycle
                                                .stream()
                                                .map(Card::id)
                                                .toList()
                                ).getOrDefault(
                                        card.id().value(),
                                        List.of()
                                ),
                                checklistService.listItems(
                                        ownerId,
                                        boardId,
                                        card.id().value()
                                )
                        ))
                        .toList()
        );
    }

    /**
     * Ejecuta el segundo ciclo con una configuración, connection
     * factory, migrator, repositorios y servicios completamente
     * nuevos, y comprueba que el estado recuperado coincide
     * exactamente con lo registrado en el primer ciclo.
     */
    private void verifySecondCycle(
            Path databasePath,
            RecordedState expected
    ) {
        SqliteConnectionFactory connectionFactory =
                openFactory(databasePath);

        new SqliteMigrator(connectionFactory).migrate();

        SqliteAdministratorRepository administratorRepository =
                new SqliteAdministratorRepository(
                        connectionFactory
                );

        assertTrue(
                administratorRepository.exists(),
                "El administrador debe seguir existiendo "
                        + "tras el reinicio."
        );

        boolean provisionedAgain =
                new ProvisionInitialAdministrator(
                        administratorRepository,
                        new Pbkdf2PasswordHasher()
                ).execute(
                        ADMIN_USERNAME,
                        ADMIN_PASSWORD.clone()
                );

        assertFalse(
                provisionedAgain,
                "El administrador no debe aprovisionarse "
                        + "una segunda vez."
        );

        StoredAdministrator storedAdministrator =
                administratorRepository
                        .findByUsername(
                                new AdministratorUsername(
                                        ADMIN_USERNAME
                                )
                        )
                        .orElseThrow();

        assertEquals(
                expected.ownerId(),
                storedAdministrator.administrator().id()
        );

        PasswordVerifier recoveredVerifier =
                storedAdministrator.passwordVerifier();

        assertTrue(
                new Pbkdf2PasswordHasher().verify(
                        ADMIN_PASSWORD.clone(),
                        recoveredVerifier
                )
        );

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        LabelService labelService = new LabelService(
                new SqliteLabelRepository(connectionFactory)
        );

        ChecklistService checklistService = new ChecklistService(
                new SqliteChecklistItemRepository(
                        connectionFactory
                )
        );

        BoardDetails recoveredBoard = boardService.getBoard(
                expected.ownerId(),
                expected.boardId()
        );

        assertEquals(
                expected.boardName(),
                recoveredBoard.board().name().value()
        );

        assertEquals(
                expected.orderedColumnIds(),
                recoveredBoard.columns()
                        .stream()
                        .map(column -> column.id().value())
                        .toList(),
                "El orden de columnas debe sobrevivir al reinicio."
        );

        assertEquals(
                List.of(0, 1, 2),
                recoveredBoard.columns()
                        .stream()
                        .map(BoardColumn::position)
                        .toList()
        );

        List<Card> recoveredCards = cardService.listCards(
                expected.ownerId(),
                expected.boardId()
        );

        Map<Long, List<Label>> recoveredLabelsByCard =
                labelService.listLabelsForCards(
                        expected.ownerId(),
                        expected.boardId(),
                        recoveredCards.stream()
                                .map(Card::id)
                                .toList()
                );

        List<RecordedCard> recoveredRecordedCards =
                recoveredCards.stream()
                        .map(card -> RecordedCard.from(
                                card,
                                recoveredLabelsByCard
                                        .getOrDefault(
                                                card.id().value(),
                                                List.of()
                                        ),
                                checklistService.listItems(
                                        expected.ownerId(),
                                        expected.boardId(),
                                        card.id().value()
                                )
                        ))
                        .toList();

        assertEquals(
                expected.cards(),
                recoveredRecordedCards,
                "Identidad, título, descripción, columna, "
                        + "posición, etiquetas y checklist de "
                        + "cada tarjeta deben coincidir "
                        + "exactamente tras el reinicio."
        );

        assertNoDuplicatedOrMissingPositions(recoveredCards);

        Card firstCardAfterRestart = recoveredCards.stream()
                .filter(card ->
                        card.title().value().contains(
                                "enunciat"
                        )
                )
                .findFirst()
                .orElseThrow();

        ChecklistProgress progress = checklistService.progress(
                expected.ownerId(),
                expected.boardId(),
                firstCardAfterRestart.id().value()
        );

        assertEquals(3, progress.totalItems());
        assertEquals(1, progress.completedItems());
        assertEquals(33, progress.percentage());

        List<ChecklistItem> recoveredItems =
                checklistService.listItems(
                        expected.ownerId(),
                        expected.boardId(),
                        firstCardAfterRestart.id().value()
                );

        assertEquals(
                List.of(0, 1, 2),
                recoveredItems.stream()
                        .map(ChecklistItem::position)
                        .toList(),
                "Las posiciones del checklist deben quedar "
                        + "contiguas y sin duplicados tras el "
                        + "reinicio."
        );
    }

    private static void assertNoDuplicatedOrMissingPositions(
            List<Card> cards
    ) {
        Map<Long, List<Integer>> positionsByColumn =
                new java.util.HashMap<>();

        for (Card card : cards) {
            positionsByColumn
                    .computeIfAbsent(
                            card.columnId().value(),
                            key -> new java.util.ArrayList<>()
                    )
                    .add(card.position());
        }

        positionsByColumn.forEach((columnId, positions) -> {
            List<Integer> sorted =
                    positions.stream().sorted().toList();

            List<Integer> contiguous =
                    java.util.stream.IntStream
                            .range(0, positions.size())
                            .boxed()
                            .toList();

            assertEquals(
                    contiguous,
                    sorted,
                    "Las posiciones de la columna " + columnId
                            + " deben ser contiguas y sin huecos "
                            + "ni duplicados tras el reinicio."
            );
        });
    }

    private SqliteConnectionFactory openFactory(
            Path databasePath
    ) {
        SqliteConfig config = SqliteConfig.from(
                Map.of(
                        "AULAFLOW_DB_PATH",
                        databasePath.toString()
                ),
                temporaryDirectory
        );

        return new SqliteConnectionFactory(config);
    }

    private record RecordedState(
            long ownerId,
            long boardId,
            String boardName,
            List<Long> orderedColumnIds,
            List<RecordedCard> cards
    ) {
    }

    private record RecordedCard(
            long id,
            long columnId,
            String title,
            String description,
            int position,
            List<Long> labelIds,
            List<RecordedChecklistItem> checklistItems
    ) {
        static RecordedCard from(
                Card card,
                List<Label> labels,
                List<ChecklistItem> checklistItems
        ) {
            return new RecordedCard(
                    card.id().value(),
                    card.columnId().value(),
                    card.title().value(),
                    card.description().value(),
                    card.position(),
                    labels.stream()
                            .map(label -> label.id().value())
                            .sorted()
                            .toList(),
                    checklistItems.stream()
                            .map(RecordedChecklistItem::from)
                            .toList()
            );
        }
    }

    private record RecordedChecklistItem(
            long id,
            String text,
            boolean completed,
            int position
    ) {
        static RecordedChecklistItem from(ChecklistItem item) {
            return new RecordedChecklistItem(
                    item.id().value(),
                    item.text().value(),
                    item.completed(),
                    item.position()
            );
        }
    }
}
