package es.aulaflow.presentation.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TranslationCatalogContractTest {

    private static final String VALENCIAN_CATALOG_PATH =
            "/web/assets/i18n/ca.json";

    private static final String SPANISH_CATALOG_PATH =
            "/web/assets/i18n/es.json";

    private static final String INDEX_PATH =
            "/web/index.html";

    private static final List<String> PAGE_PATHS =
            List.of(
                    INDEX_PATH,
                    "/web/login.html",
                    "/web/account.html"
            );

    private static final Pattern ENTRY_PATTERN =
            Pattern.compile(
                    "^\\s*\"([a-z0-9.]+)\""
                            + "\\s*:\\s*"
                            + "\"(?:\\\\.|[^\"\\\\])*\""
                            + "(,?)\\s*$"
            );

    private static final Pattern DATA_I18N_PATTERN =
            Pattern.compile(
                    "data-i18n\\s*=\\s*"
                            + "\"([a-z0-9.]+)\""
            );

    @Test
    void valencianCatalogUsesSupportedFlatFormat()
            throws IOException {
        Set<String> keys =
                readCatalogKeys(
                        VALENCIAN_CATALOG_PATH
                );

        assertFalse(keys.isEmpty());
    }

    @Test
    void spanishCatalogUsesSupportedFlatFormat()
            throws IOException {
        Set<String> keys =
                readCatalogKeys(
                        SPANISH_CATALOG_PATH
                );

        assertFalse(keys.isEmpty());
    }

    @Test
    void catalogsContainExactlyTheSameKeys()
            throws IOException {
        Set<String> valencianKeys =
                readCatalogKeys(
                        VALENCIAN_CATALOG_PATH
                );

        Set<String> spanishKeys =
                readCatalogKeys(
                        SPANISH_CATALOG_PATH
                );

        assertEquals(
                valencianKeys,
                spanishKeys,
                "Los catálogos deben contener exactamente "
                        + "las mismas claves."
        );
    }

    @Test
    void htmlTranslationKeysExistInCatalog()
            throws IOException {
        Set<String> catalogKeys =
                readCatalogKeys(
                        VALENCIAN_CATALOG_PATH
                );

        Set<String> htmlKeys =
                new TreeSet<>();

        for (String pagePath : PAGE_PATHS) {
            Matcher matcher =
                    DATA_I18N_PATTERN.matcher(
                            loadResource(pagePath)
                    );

            while (matcher.find()) {
                htmlKeys.add(
                        matcher.group(1)
                );
            }
        }

        assertFalse(
                htmlKeys.isEmpty(),
                "El documento HTML debe contener "
                        + "claves data-i18n."
        );

        Set<String> missingKeys =
                new TreeSet<>(htmlKeys);

        missingKeys.removeAll(catalogKeys);

        assertTrue(
                missingKeys.isEmpty(),
                "El HTML utiliza claves que no existen "
                        + "en los catálogos: "
                        + missingKeys
        );
    }

    private static Set<String> readCatalogKeys(
            String resourcePath
    ) throws IOException {
        List<String> lines =
                loadResource(resourcePath)
                        .lines()
                        .filter(line -> !line.isBlank())
                        .toList();

        assertTrue(
                lines.size() >= 3,
                "El catálogo debe contener un objeto JSON "
                        + "no vacío: "
                        + resourcePath
        );

        assertEquals(
                "{",
                lines.getFirst().trim(),
                "El catálogo debe comenzar con una llave "
                        + "de apertura."
        );

        assertEquals(
                "}",
                lines
                        .getLast()
                        .trim(),
                "El catálogo debe terminar con una llave "
                        + "de cierre."
        );

        List<String> entryLines =
                lines.subList(
                        1,
                        lines.size() - 1
                );

        List<String> keys =
                new ArrayList<>();

        for (
                int index = 0;
                index < entryLines.size();
                index++
        ) {
            String line =
                    entryLines.get(index);

            Matcher matcher =
                    ENTRY_PATTERN.matcher(line);

            assertTrue(
                    matcher.matches(),
                    "Entrada no válida en "
                            + resourcePath
                            + ": "
                            + line
            );

            boolean isLastEntry =
                    index == entryLines.size() - 1;

            String comma =
                    matcher.group(2);

            assertEquals(
                    !isLastEntry,
                    ",".equals(comma),
                    "La separación por comas no es válida en "
                            + resourcePath
                            + ": "
                            + line
            );

            keys.add(
                    matcher.group(1)
            );
        }

        Set<String> uniqueKeys =
                new HashSet<>(keys);

        assertEquals(
                keys.size(),
                uniqueKeys.size(),
                "El catálogo contiene claves duplicadas: "
                        + resourcePath
        );

        return new TreeSet<>(uniqueKeys);
    }

    private static String loadResource(
            String resourcePath
    ) throws IOException {
        try (
                InputStream inputStream =
                        TranslationCatalogContractTest
                                .class
                                .getResourceAsStream(
                                        resourcePath
                                )
        ) {
            assertNotNull(
                    inputStream,
                    "No se ha encontrado el catálogo: "
                            + resourcePath
            );

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
