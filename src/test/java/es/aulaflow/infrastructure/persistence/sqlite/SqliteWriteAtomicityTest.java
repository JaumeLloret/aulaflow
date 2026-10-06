package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.Card;
import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprueba el rollback de una operación real de
 * {@link SqliteCardRepository}. Un trigger exclusivo de la base
 * temporal de prueba provoca un fallo después de que el movimiento
 * haya comenzado, sin añadir hooks ni comportamientos especiales al
 * código de producción.
 */
class SqliteWriteAtomicityTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void cardRepositoryRollsBackPartiallyStartedMove()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory(
                        "repository-atomicity.db"
                );

        insertAdministrator(connectionFactory);

        BoardService boardService =
                new BoardService(
                        new SqliteBoardRepository(
                                connectionFactory
                        )
                );

        CardService cardService =
                new CardService(
                        new SqliteCardRepository(
                                connectionFactory
                        )
                );

        BoardDetails board =
                boardService.createBoard(
                        OWNER_ID,
                        "Tauler atomicitat"
                );

        long boardId =
                board.board().id().value();

        long sourceColumnId =
                board.columns().get(0).id().value();

        long targetColumnId =
                board.columns().get(1).id().value();

        Card movingCard =
                cardService.createCard(
                        OWNER_ID,
                        boardId,
                        sourceColumnId,
                        "Targeta que es mourà",
                        ""
                );

        Card sourceCard =
                cardService.createCard(
                        OWNER_ID,
                        boardId,
                        sourceColumnId,
                        "Targeta que queda a l'origen",
                        ""
                );

        Card targetCard =
                cardService.createCard(
                        OWNER_ID,
                        boardId,
                        targetColumnId,
                        "Targeta existent al destí",
                        ""
                );

        installFailureTrigger(
                connectionFactory,
                movingCard.id().value()
        );

        assertThrows(
                PersistenceException.class,
                () -> cardService.moveCard(
                        OWNER_ID,
                        boardId,
                        movingCard.id().value(),
                        targetColumnId,
                        0
                )
        );

        List<Card> recoveredCards =
                cardService.listCards(
                        OWNER_ID,
                        boardId
                );

        List<Card> sourceCards =
                cardsInColumn(
                        recoveredCards,
                        sourceColumnId
                );

        List<Card> targetCards =
                cardsInColumn(
                        recoveredCards,
                        targetColumnId
                );

        assertEquals(
                List.of(
                        movingCard.id(),
                        sourceCard.id()
                ),
                sourceCards.stream()
                        .map(Card::id)
                        .toList(),
                "Las tarjetas de origen deben conservar "
                        + "el orden anterior al fallo."
        );

        assertEquals(
                List.of(0, 1),
                sourceCards.stream()
                        .map(Card::position)
                        .toList(),
                "Las posiciones de origen deben restaurarse "
                        + "completamente."
        );

        assertEquals(
                List.of(targetCard.id()),
                targetCards.stream()
                        .map(Card::id)
                        .toList(),
                "La columna destino no debe conservar "
                        + "la tarjeta cuyo movimiento falló."
        );

        assertEquals(
                List.of(0),
                targetCards.stream()
                        .map(Card::position)
                        .toList(),
                "La columna destino debe conservar su "
                        + "posición original."
        );

        assertEquals(
                3,
                recoveredCards.size(),
                "El fallo no debe duplicar ni eliminar tarjetas."
        );
    }

    private SqliteConnectionFactory
    migratedConnectionFactory(
            String databaseName
    ) {
        Path databasePath =
                temporaryDirectory.resolve(
                        databaseName
                );

        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                databasePath.toString()
                        ),
                        temporaryDirectory
                );

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        config
                );

        new SqliteMigrator(
                connectionFactory
        ).migrate();

        return connectionFactory;
    }

    private static void insertAdministrator(
            SqliteConnectionFactory connectionFactory
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("""
                                INSERT INTO administrators (
                                    id,
                                    username,
                                    password_verifier
                                ) VALUES (?, ?, ?)
                                """)
        ) {
            statement.setLong(
                    1,
                    OWNER_ID
            );

            statement.setString(
                    2,
                    "profesora.atomicitat"
            );

            statement.setString(
                    3,
                    "placeholder"
            );

            assertEquals(
                    1,
                    statement.executeUpdate()
            );
        }
    }

    private static void installFailureTrigger(
            SqliteConnectionFactory connectionFactory,
            long movingCardId
    ) throws Exception {
        String triggerSql = """
                CREATE TRIGGER fail_card_final_position
                BEFORE UPDATE OF position ON cards
                WHEN OLD.id = %d
                  AND NEW.position = 0
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Fallo controlado de atomicidad'
                    );
                END
                """.formatted(movingCardId);

        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(triggerSql);
        }
    }

    private static List<Card> cardsInColumn(
            List<Card> cards,
            long columnId
    ) {
        return cards.stream()
                .filter(
                        card ->
                                card.columnId().value()
                                        == columnId
                )
                .toList();
    }
}
