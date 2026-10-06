package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.board.BoardService;
import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.PlannedCard;
import es.aulaflow.application.csv.PlannedColumn;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.CardDescription;
import es.aulaflow.domain.board.CardTitle;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Demuestra el rollback real de {@link SqliteCsvImportRepository}
 * mediante un trigger exclusivo de la base temporal de prueba que
 * provoca un fallo real de SQLite después de que la importación ya
 * ha escrito el tablero, una columna y una tarjeta. No se añade el
 * trigger a producción ni se simula el rollback con dobles de prueba.
 */
class SqliteCsvImportAtomicityTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void failureDuringSecondColumnRollsBackEntireImport()
            throws Exception {
        SqliteConnectionFactory connectionFactory =
                migratedConnectionFactory("atomicity.db");

        insertAdministrator(connectionFactory);

        installFailureTrigger(connectionFactory);

        SqliteCsvImportRepository importRepository =
                new SqliteCsvImportRepository(
                        connectionFactory
                );

        CsvImportPlan plan = new CsvImportPlan(
                new BoardName("Tauler amb fallada"),
                List.of(
                        new PlannedColumn(
                                new ColumnName("Primera"),
                                0,
                                List.of(
                                        new PlannedCard(
                                                new CardTitle(
                                                        "Targeta"
                                                ),
                                                new CardDescription(
                                                        ""
                                                ),
                                                0
                                        )
                                )
                        ),
                        new PlannedColumn(
                                new ColumnName(
                                        "PROVOCAR_FALLADA"
                                ),
                                1,
                                List.of()
                        )
                )
        );

        assertThrows(
                PersistenceException.class,
                () -> importRepository.importPlan(
                        OWNER_ID, plan
                )
        );

        assertEquals(
                0,
                countRows(connectionFactory, "boards"),
                "Un fallo a mitad de la importación no debe "
                        + "dejar ningún tablero."
        );

        assertEquals(
                0,
                countRows(
                        connectionFactory, "board_columns"
                ),
                "Un fallo a mitad de la importación no debe "
                        + "dejar ninguna columna."
        );

        assertEquals(
                0,
                countRows(connectionFactory, "cards"),
                "Un fallo a mitad de la importación no debe "
                        + "dejar ninguna tarjeta."
        );

        BoardService boardService = new BoardService(
                new SqliteBoardRepository(connectionFactory)
        );

        assertEquals(
                0,
                boardService.listBoards(OWNER_ID).size()
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
                    2, "profesora.rollback"
            );
            statement.setString(3, "placeholder");

            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void installFailureTrigger(
            SqliteConnectionFactory connectionFactory
    ) throws Exception {
        String triggerSql = """
                CREATE TRIGGER fail_second_column
                BEFORE INSERT ON board_columns
                WHEN NEW.name = 'PROVOCAR_FALLADA'
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Fallo controlado de importación CSV'
                    );
                END
                """;

        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(triggerSql);
        }
    }

    private static int countRows(
            SqliteConnectionFactory connectionFactory,
            String table
    ) throws Exception {
        try (
                Connection connection =
                        connectionFactory.openConnection();
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM "
                                        + table
                        )
        ) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
