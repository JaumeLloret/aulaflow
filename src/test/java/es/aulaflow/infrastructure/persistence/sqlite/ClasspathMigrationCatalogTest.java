package es.aulaflow.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClasspathMigrationCatalogTest {

    @Test
    void loadsDefaultManifestInDeclaredOrder() {
        List<SqliteMigration> migrations =
                ClasspathMigrationCatalog.loadDefault();

        assertEquals(
                12,
                migrations.size()
        );

        SqliteMigration migration =
                migrations.getFirst();

        assertEquals(
                "V001",
                migration.getVersion()
        );

        assertEquals(
                "create_schema_migrations",
                migration.getDescription()
        );

        assertEquals(
                "/db/migration/"
                        + "V001__create_schema_migrations.sql",
                migration.getResourcePath()
        );

        SqliteMigration administratorMigration =
                migrations.get(1);

        assertEquals(
                "V002",
                administratorMigration.getVersion()
        );

        assertEquals(
                "create_administrators",
                administratorMigration.getDescription()
        );

        assertEquals(
                List.of(
                        "V001",
                        "V002",
                        "V003",
                        "V004",
                        "V005",
                        "V006",
                        "V007",
                        "V008",
                        "V009",
                        "V010",
                        "V011",
                        "V012"
                ),
                migrations.stream()
                        .map(
                                SqliteMigration
                                        ::getVersion
                        )
                        .toList()
        );
    }

    @Test
    void rejectsManifestThatIsNotOrdered() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> ClasspathMigrationCatalog.load(
                                "/db/migration/catalog/"
                                        + "out-of-order.txt"
                        )
                );

        assertEquals(
                "El manifiesto de migraciones "
                        + "no está ordenado.",
                exception.getMessage()
        );
    }
}
