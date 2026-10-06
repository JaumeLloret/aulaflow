package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.csv.CsvImportPlan;
import es.aulaflow.application.csv.CsvImportRepository;
import es.aulaflow.application.csv.PlannedCard;
import es.aulaflow.application.csv.PlannedColumn;
import es.aulaflow.domain.board.BoardId;
import es.aulaflow.infrastructure.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Puerto de importación atómica del contrato AulaFlow Kanban CSV v1.
 * Abre una única conexión con {@code setAutoCommit(false)}, inserta
 * el tablero, sus columnas y sus tarjetas dentro de la misma
 * transacción, y confirma solo si todas las escrituras tienen éxito.
 * No reutiliza los repositorios de tablero o tarjeta existentes
 * porque cada uno de ellos abre su propia conexión y transacción, lo
 * que dejaría estados parciales ante un fallo a mitad de la
 * importación.
 */
public final class SqliteCsvImportRepository
        implements CsvImportRepository {

    private static final String INSERT_BOARD = """
            INSERT INTO boards (owner_id, name)
            VALUES (?, ?)
            """;

    private static final String INSERT_COLUMN = """
            INSERT INTO board_columns (
                board_id,
                name,
                position
            ) VALUES (?, ?, ?)
            """;

    private static final String INSERT_CARD = """
            INSERT INTO cards (
                column_id,
                title,
                description,
                position
            ) VALUES (?, ?, ?, ?)
            """;

    private final SqliteConnectionFactory connectionFactory;

    public SqliteCsvImportRepository(
            SqliteConnectionFactory connectionFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "La factoría de conexiones no puede ser null."
        );
    }

    @Override
    public BoardId importPlan(
            long ownerId,
            CsvImportPlan plan
    ) {
        Objects.requireNonNull(
                plan,
                "El plan de importación no puede ser null."
        );

        try (
                Connection connection =
                        connectionFactory.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                long boardId = insertBoard(
                        connection, ownerId, plan
                );

                for (PlannedColumn column : plan.columns()) {
                    long columnId = insertColumn(
                            connection, boardId, column
                    );

                    for (PlannedCard card : column.cards()) {
                        insertCard(
                                connection, columnId, card
                        );
                    }
                }

                connection.commit();

                return new BoardId(boardId);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static long insertBoard(
            Connection connection,
            long ownerId,
            CsvImportPlan plan
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_BOARD,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, ownerId);
            statement.setString(
                    2, plan.boardName().value()
            );
            statement.executeUpdate();

            return generatedId(
                    statement,
                    "SQLite no devolvió el identificador "
                            + "del tablero importado."
            );
        }
    }

    private static long insertColumn(
            Connection connection,
            long boardId,
            PlannedColumn column
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_COLUMN,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(1, boardId);
            statement.setString(2, column.name().value());
            statement.setInt(3, column.position());
            statement.executeUpdate();

            return generatedId(
                    statement,
                    "SQLite no devolvió el identificador "
                            + "de la columna importada."
            );
        }
    }

    private static void insertCard(
            Connection connection,
            long columnId,
            PlannedCard card
    ) throws SQLException {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                INSERT_CARD
                        )
        ) {
            statement.setLong(1, columnId);
            statement.setString(2, card.title().value());
            statement.setString(
                    3, card.description().value()
            );
            statement.setInt(4, card.position());
            statement.executeUpdate();
        }
    }

    private static long generatedId(
            PreparedStatement statement,
            String failureMessage
    ) throws SQLException {
        try (
                ResultSet keys =
                        statement.getGeneratedKeys()
        ) {
            if (!keys.next()) {
                throw new SQLException(failureMessage);
            }

            return keys.getLong(1);
        }
    }

    private static void rollback(
            Connection connection,
            SQLException original
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static PersistenceException persistenceFailure(
            SQLException exception
    ) {
        return new PersistenceException(
                "No se ha podido importar el tablero CSV.",
                exception
        );
    }
}
