package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.board.CardService;
import es.aulaflow.application.csv.CsvExportDocument;
import es.aulaflow.application.csv.CsvExportService;
import es.aulaflow.application.csv.CsvImportPreview;
import es.aulaflow.application.csv.CsvImportService;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.domain.board.Card;
import es.aulaflow.infrastructure.csv.InMemoryPendingImportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demuestra el round trip semántico exigido por la issue #19:
 * estado A -&gt; exportar -&gt; importar -&gt; estado B produce el
 * mismo CSV canónico, incluyendo Unicode, comas, comillas, saltos de
 * línea, fórmulas neutralizadas y apóstrofos, además de una columna
 * vacía. No compara IDs ni marcas de tiempo.
 */
class CsvRoundTripIntegrationTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportingAReimportedBoardProducesIdenticalCanonicalCsv()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("round-trip.db");

        insertAdministrator(connectionFactory);

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        CardService cardService = new CardService(
                new SqliteCardRepository(connectionFactory)
        );

        CsvExportService exportService =
                new CsvExportService(
                        boardService, cardService
                );

        CsvImportService importService =
                new CsvImportService(
                        new InMemoryPendingImportStore(),
                        new SqliteCsvImportRepository(
                                connectionFactory
                        )
                );

        BoardDetails original = boardService.createBoard(
                OWNER_ID, "Tauler amb «round trip» 🎓"
        );

        long boardId = original.board().id().value();

        long perFerColumnId =
                columnIdByName(original, "Per fer");
        long fetColumnId =
                columnIdByName(original, "Fet");

        cardService.createCard(
                OWNER_ID,
                boardId,
                perFerColumnId,
                "Tasca, amb \"cites\" i salt\nde línia",
                "Descripció amb emoji 🎓 i eñe"
        );

        cardService.createCard(
                OWNER_ID,
                boardId,
                perFerColumnId,
                "=1+1",
                "'text amb apòstrof inicial"
        );

        cardService.createCard(
                OWNER_ID,
                boardId,
                fetColumnId,
                "@cmd perillós",
                ""
        );

        // "En curs" se deja sin tarjetas a propósito para
        // comprobar el round trip de una columna vacía.

        CsvExportDocument exportedA = exportService.export(
                OWNER_ID, boardId
        );

        CsvImportPreview preview = importService.preview(
                new ByteArrayInputStream(
                        exportedA.content()
                ),
                "round-trip-session",
                OWNER_ID
        );

        assertEquals(
                true,
                preview.report().valid(),
                "El CSV exportado debe volver a validarse "
                        + "como correcto: "
                        + preview.report().errors()
        );

        BoardId importedBoardId = importService.confirm(
                preview.token().orElseThrow(),
                "round-trip-session",
                OWNER_ID
        );

        CsvExportDocument exportedB = exportService.export(
                OWNER_ID, importedBoardId.value()
        );

        assertArrayEquals(
                exportedA.content(),
                exportedB.content(),
                "Exportar el tablero reimportado debe "
                        + "producir el mismo CSV canónico."
        );

        BoardDetails reimported = boardService.getBoard(
                OWNER_ID, importedBoardId.value()
        );

        assertEquals(
                original.board().name().value(),
                reimported.board().name().value()
        );

        assertEquals(
                original.columns().stream()
                        .map(column ->
                                column.name().value()
                        )
                        .toList(),
                reimported.columns().stream()
                        .map(column ->
                                column.name().value()
                        )
                        .toList()
        );

        List<Card> reimportedCards =
                cardService.listCards(
                        OWNER_ID,
                        importedBoardId.value()
                );

        List<String> reimportedTitles = reimportedCards
                .stream()
                .sorted(
                        Comparator
                                .<Card>comparingLong(
                                        card -> card
                                                .columnId()
                                                .value()
                                )
                                .thenComparingInt(
                                        Card::position
                                )
                )
                .map(card -> card.title().value())
                .toList();

        assertEquals(
                List.of(
                        "Tasca, amb \"cites\" i salt"
                                + "\nde línia",
                        "=1+1",
                        "@cmd perillós"
                ),
                reimportedTitles
        );

        List<String> reimportedDescriptions =
                reimportedCards.stream()
                        .sorted(
                                Comparator
                                        .<Card>comparingLong(
                                                card -> card
                                                        .columnId()
                                                        .value()
                                        )
                                        .thenComparingInt(
                                                Card::position
                                        )
                        )
                        .map(card ->
                                card.description().value()
                        )
                        .toList();

        assertEquals(
                List.of(
                        "Descripció amb emoji 🎓 i eñe",
                        "'text amb apòstrof inicial",
                        ""
                ),
                reimportedDescriptions
        );
    }

    private static long columnIdByName(
            BoardDetails details,
            String name
    ) {
        return details.columns().stream()
                .filter(column ->
                        column.name().value().equals(name)
                )
                .findFirst()
                .orElseThrow()
                .id()
                .value();
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
                    2, "profesora.roundtrip"
            );
            statement.setString(3, "placeholder");

            assertEquals(1, statement.executeUpdate());
        }
    }
}
