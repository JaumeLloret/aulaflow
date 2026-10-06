package es.aulaflow.infrastructure.persistence.sqlite;

import es.aulaflow.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDefaultPathFromWorkingDirectory() {
        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory
                        .resolve("data/aulaflow.db")
                        .normalize(),
                config.getDatabasePath()
        );
    }

    @Test
    void resolvesConfiguredRelativePathFromWorkingDirectory() {
        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                "storage/test.sqlite"
                        ),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory
                        .resolve("storage/test.sqlite")
                        .normalize(),
                config.getDatabasePath()
        );
    }

    @Test
    void preservesConfiguredAbsolutePath() {
        Path absolutePath =
                temporaryDirectory
                        .resolve("custom.db")
                        .toAbsolutePath();

        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                absolutePath.toString()
                        ),
                        Path.of("ignored")
                );

        assertEquals(
                absolutePath.normalize(),
                config.getDatabasePath()
        );
    }

    @Test
    void usesDefaultPathForBlankValue() {
        SqliteConfig config =
                SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                "   "
                        ),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory
                        .resolve("data/aulaflow.db")
                        .normalize(),
                config.getDatabasePath()
        );
    }

    @Test
    void rejectsInvalidConfiguredPath() {
        assertThrows(
                PersistenceException.class,
                () -> SqliteConfig.from(
                        Map.of(
                                "AULAFLOW_DB_PATH",
                                "invalid\u0000path.db"
                        ),
                        temporaryDirectory
                )
        );
    }
}
