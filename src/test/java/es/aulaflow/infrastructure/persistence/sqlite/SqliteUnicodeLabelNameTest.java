package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.application.auth.PasswordVerifier;
import es.aulaflow.application.board.BoardRepository;
import es.aulaflow.application.board.LabelRepository;
import es.aulaflow.domain.board.BoardDetails;
import es.aulaflow.domain.board.BoardName;
import es.aulaflow.domain.board.ColumnName;
import es.aulaflow.domain.board.Label;
import es.aulaflow.domain.board.LabelColor;
import es.aulaflow.domain.board.LabelName;
import es.aulaflow.domain.identity.AdministratorUsername;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SqliteUnicodeLabelNameTest {

    private static final long OWNER_ID = 1L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsValidNameWhoseLowercaseFormExpands() {
        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(
                        SqliteConfig.from(
                                Map.of(
                                        "AULAFLOW_DB_PATH",
                                        temporaryDirectory
                                                .resolve("unicode-label.db")
                                                .toString()
                                ),
                                temporaryDirectory
                        )
                );

        new SqliteMigrator(connectionFactory).migrate();

        new SqliteAdministratorRepository(
                connectionFactory
        ).create(
                new AdministratorUsername("admin"),
                new PasswordVerifier("test")
        );

        BoardRepository boardRepository =
                new SqliteBoardRepository(connectionFactory);

        BoardDetails board = boardRepository.create(
                OWNER_ID,
                new BoardName("Tauler"),
                List.of(new ColumnName("Pendent"))
        );

        LabelRepository labelRepository =
                new SqliteLabelRepository(connectionFactory);

        String visibleName =
                "İ".repeat(LabelName.MAXIMUM_CODE_POINTS);

        Label created = labelRepository.create(
                OWNER_ID,
                board.board().id().value(),
                new LabelName(visibleName),
                LabelColor.BLUE
        ).orElseThrow();

        assertEquals(visibleName, created.name().value());

        assertEquals(
                List.of(visibleName),
                labelRepository.listByBoard(
                        OWNER_ID,
                        board.board().id().value()
                ).stream()
                        .map(label -> label.name().value())
                        .toList()
        );
    }
}
