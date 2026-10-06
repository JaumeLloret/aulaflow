package es.aulaflow.operations;

import es.aulaflow.infrastructure.persistence.sqlite.SqliteBackupConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteBackupService;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConfig;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteConnectionFactory;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteDatabaseValidator;
import es.aulaflow.infrastructure.persistence.sqlite.SqliteRestoreService;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class AulaFlowMaintenance {

    private AulaFlowMaintenance() {
    }

    public static void main(String[] args) {
        int exitCode = run(
                args,
                System.out,
                System.err
        );

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            String[] args,
            PrintStream output,
            PrintStream errorOutput
    ) {
        if (args == null || args.length == 0) {
            showUsage(errorOutput);
            return 2;
        }

        try {
            SqliteConfig sqliteConfig =
                    SqliteConfig.fromEnvironment();

            SqliteBackupConfig backupConfig =
                    SqliteBackupConfig.fromEnvironment();

            SqliteDatabaseValidator validator =
                    new SqliteDatabaseValidator();

            return switch (args[0]) {
                case "backup" -> backup(
                        args,
                        sqliteConfig,
                        backupConfig,
                        validator,
                        output,
                        errorOutput
                );
                case "validate" -> validate(
                        args,
                        backupConfig,
                        validator,
                        output,
                        errorOutput
                );
                case "restore" -> restore(
                        args,
                        sqliteConfig,
                        backupConfig,
                        validator,
                        output,
                        errorOutput
                );
                case "list" -> list(
                        args,
                        backupConfig,
                        output,
                        errorOutput
                );
                default -> {
                    showUsage(errorOutput);
                    yield 2;
                }
            };
        } catch (RuntimeException exception) {
            errorOutput.println(
                    "Operación no completada: "
                            + safeMessage(exception)
            );
            return 1;
        }
    }

    private static int backup(
            String[] args,
            SqliteConfig sqliteConfig,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator,
            PrintStream output,
            PrintStream errorOutput
    ) {
        if (args.length != 1) {
            showUsage(errorOutput);
            return 2;
        }

        SqliteConnectionFactory connectionFactory =
                new SqliteConnectionFactory(sqliteConfig);

        SqliteBackupService backupService =
                new SqliteBackupService(
                        sqliteConfig,
                        connectionFactory,
                        backupConfig,
                        validator
                );

        Path backupPath = backupService.createBackup();

        output.println(
                "Backup creado y validado: "
                        + backupPath.getFileName()
        );

        return 0;
    }

    private static int validate(
            String[] args,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator,
            PrintStream output,
            PrintStream errorOutput
    ) {
        if (args.length != 2) {
            showUsage(errorOutput);
            return 2;
        }

        Path backupPath =
                backupConfig.resolveBackupFile(args[1]);

        validator.validate(backupPath);

        output.println(
                "Backup válido: " + backupPath.getFileName()
        );

        return 0;
    }

    private static int restore(
            String[] args,
            SqliteConfig sqliteConfig,
            SqliteBackupConfig backupConfig,
            SqliteDatabaseValidator validator,
            PrintStream output,
            PrintStream errorOutput
    ) {
        if (args.length != 2) {
            showUsage(errorOutput);
            return 2;
        }

        SqliteRestoreService restoreService =
                new SqliteRestoreService(
                        sqliteConfig,
                        backupConfig,
                        validator
                );

        restoreService.restore(args[1]);

        output.println(
                "Restauración completada desde: "
                        + Path.of(args[1]).getFileName()
        );

        return 0;
    }

    private static int list(
            String[] args,
            SqliteBackupConfig backupConfig,
            PrintStream output,
            PrintStream errorOutput
    ) {
        if (args.length != 1) {
            showUsage(errorOutput);
            return 2;
        }

        Path backupDirectory =
                backupConfig.getBackupDirectory();

        if (!Files.isDirectory(backupDirectory)) {
            output.println("No hay backups disponibles.");
            return 0;
        }

        try (
                var paths = Files.list(backupDirectory)
        ) {
            List<Path> backups = paths
                    .filter(Files::isRegularFile)
                    .filter(
                            path -> path
                                    .getFileName()
                                    .toString()
                                    .endsWith(".db")
                    )
                    .sorted(
                            Comparator
                                    .comparing(
                                            (Path path) -> path
                                                    .getFileName()
                                                    .toString()
                                    )
                                    .reversed()
                    )
                    .toList();

            if (backups.isEmpty()) {
                output.println("No hay backups disponibles.");
                return 0;
            }

            for (Path backup : backups) {
                output.println(backup.getFileName());
            }

            return 0;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No se ha podido listar los backups.",
                    exception
            );
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "error interno de mantenimiento.";
        }

        return message;
    }

    private static void showUsage(PrintStream output) {
        output.println("Uso:");
        output.println("  backup");
        output.println("  list");
        output.println("  validate <archivo.db>");
        output.println("  restore <archivo.db>");
    }
}
