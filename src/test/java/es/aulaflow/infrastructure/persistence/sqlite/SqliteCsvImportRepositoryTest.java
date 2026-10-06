package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.PlannedCard;
import es.aulaflow.application.csv.PlannedColumn;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.Card;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteCsvImportRepositoryTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void importsBoardColumnsAndCardsForOwner()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("import.db");

        insertAdministrator(connectionFactory);

        SqliteCsvImportRepository importRepository =
                new SqliteCsvImportRepository(
                        connectionFactory
                );

        CsvImportPlan plan = new CsvImportPlan(
                new BoardName("Tauler importat"),
                List.of(
                        new PlannedColumn(
                                new ColumnName("Per fer"),
                                0,
                                List.of(
                                        new PlannedCard(
                                                new CardTitle(
                                                        "Estudiar"
                                                ),
                                                new CardDescription(
                                                        "Repassar"
                                                ),
                                                0
                                        )
                                )
                        ),
                        new PlannedColumn(
                                new ColumnName("Buida"),
                                1,
                                List.of()
                        )
                )
        );

        BoardId boardId = importRepository.importPlan(
                OWNER_ID, plan
        );

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        BoardDetails details = boardService.getBoard(
                OWNER_ID, boardId.value()
        );

        assertEquals(
                "Tauler importat",
                details.board().name().value()
        );
        assertEquals(2, details.columns().size());
        assertEquals(
                "Per fer",
                details.columns().get(0).name().value()
        );
        assertEquals(
                "Buida",
                details.columns().get(1).name().value()
        );

        List<Card> cards = cardService.listCards(
                OWNER_ID, boardId.value()
        );

        assertEquals(1, cards.size());
        assertEquals(
                "Estudiar", cards.get(0).title().value()
        );
        assertEquals(
                details.columns().get(0).id(),
                cards.get(0).columnId()
        );
    }

    @Test
    void repeatingImportCreatesIndependentCopies()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("repeat.db");

        insertAdministrator(connectionFactory);

        SqliteCsvImportRepository importRepository =
                new SqliteCsvImportRepository(
                        connectionFactory
                );

        CsvImportPlan plan = new CsvImportPlan(
                new BoardName("Tauler repetit"),
                List.of(
                        new PlannedColumn(
                                new ColumnName("Columna"),
                                0,
                                List.of()
                        )
                )
        );

        BoardId first = importRepository.importPlan(
                OWNER_ID, plan
        );
        BoardId second = importRepository.importPlan(
                OWNER_ID, plan
        );

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        assertEquals(
                2,
                boardService.listBoards(OWNER_ID).size()
        );

        org.junit.jupiter.api.Assertions.assertNotEquals(
                first, second
        );
    }

    private SqliteConnectionFactory
    migratedConnectionFactory(
            String databaseName
    ) {
        Path databasePath =
                temporaryDirectory.resolve(databaseName);

        SqliteConfig config = SqliteConfig.from(
                Map.of(
                        "AULAFLOW_DB_PATH",
                        databasePath.toString()
                ),
                temporaryDirectory
        );

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(config);

        new SqliteMigrator(connectionFactory).migrate();

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
            statement.setLong(1, OWNER_ID);
            statement.setString(
                    2, "profesora.csv"
            );
            statement.setString(3, "placeholder");

            assertEquals(1, statement.executeUpdate());
        }
    }
}
