package es.aulaflow.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteBackupConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDefaultDirectoryFromWorkingDirectory() {
        SqliteBackupConfig config =
                SqliteBackupConfig.from(
                        Map.of(),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory.resolve("backups").normalize(),
                config.getBackupDirectory()
        );
    }

    @Test
    void resolvesConfiguredRelativeDirectory() {
        SqliteBackupConfig config =
                SqliteBackupConfig.from(
                        Map.of(
                                SqliteBackupConfig.BACKUP_DIRECTORY_VARIABLE,
                                "operations/backups"
                        ),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory
                        .resolve("operations/backups")
                        .normalize(),
                config.getBackupDirectory()
        );
    }

    @Test
    void resolvesFileOnlyInsideBackupDirectory() {
        SqliteBackupConfig config =
                SqliteBackupConfig.from(
                        Map.of(),
                        temporaryDirectory
                );

        assertEquals(
                temporaryDirectory
                        .resolve("backups/aulaflow-test.db")
                        .normalize(),
                config.resolveBackupFile("aulaflow-test.db")
        );
    }

    @Test
    void rejectsPathTraversalAndNonDatabaseNames() {
        SqliteBackupConfig config =
                SqliteBackupConfig.from(
                        Map.of(),
                        temporaryDirectory
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> config.resolveBackupFile("../outside.db")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> config.resolveBackupFile("notes.txt")
        );
    }
}
