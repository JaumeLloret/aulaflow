package es.aulaflow.presentation.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LanguageSelectorContractTest {

    private static final String INDEX_PATH =
            "/web/index.html";

    private static final String SCRIPT_PATH =
            "/web/assets/js/app.js";

    private static final String CATALOG_DIRECTORY =
            "/web/assets/i18n/";

    private static final Pattern SELECTOR_PATTERN =
            Pattern.compile(
                    "(?s)"
                            + "<select\\b"
                            + "(?=[^>]*\\bid\\s*=\\s*"
                            + "\"language-selector\")"
                            + "(?=[^>]*\\bdisabled\\b)"
                            + "[^>]*>"
                            + "(.*?)"
                            + "</select>"
            );

    private static final Pattern OPTION_VALUE_PATTERN =
            Pattern.compile(
                    "(?s)"
                            + "<option\\b"
                            + "[^>]*\\bvalue\\s*=\\s*"
                            + "\"([a-z]{2})\""
                            + "[^>]*>"
            );

    private static final Pattern DEFAULT_OPTION_PATTERN =
            Pattern.compile(
                    "(?s)"
                            + "<option\\b"
                            + "(?=[^>]*\\bvalue\\s*=\\s*\"ca\")"
                            + "(?=[^>]*\\bselected\\b)"
                            + "[^>]*>"
            );

    @Test
    void selectorStartsDisabledWithValencianSelected()
            throws IOException {
        String html =
                loadResource(INDEX_PATH);

        Matcher selectorMatcher =
                SELECTOR_PATTERN.matcher(html);

        assertTrue(
                selectorMatcher.find(),
                "El HTML debe contener el selector "
                        + "de idioma desactivado inicialmente."
        );

        String selectorContent =
                selectorMatcher.group(1);

        assertTrue(
                DEFAULT_OPTION_PATTERN
                        .matcher(selectorContent)
                        .find(),
                "El valenciano debe ser la opción "
                        + "inicial del selector."
        );
    }

    @Test
    void everySelectorLanguageHasACatalog()
            throws IOException {
        String html =
                loadResource(INDEX_PATH);

        Matcher selectorMatcher =
                SELECTOR_PATTERN.matcher(html);

        assertTrue(
                selectorMatcher.find(),
                "No se ha encontrado el selector "
                        + "de idioma."
        );

        Matcher optionMatcher =
                OPTION_VALUE_PATTERN.matcher(
                        selectorMatcher.group(1)
                );

        Set<String> languages =
                new TreeSet<>();

        while (optionMatcher.find()) {
            languages.add(
                    optionMatcher.group(1)
            );
        }

        assertEquals(
                Set.of(
                        "ca",
                        "es"
                ),
                languages,
                "El selector debe ofrecer exactamente "
                        + "los idiomas soportados."
        );

        for (String language : languages) {
            assertNotNull(
                    LanguageSelectorContractTest
                            .class
                            .getResource(
                                    CATALOG_DIRECTORY
                                            + language
                                            + ".json"
                            ),
                    "No existe el catálogo del idioma: "
                            + language
            );
        }
    }

    @Test
    void scriptDefinesPersistenceAndFallbackContract()
            throws IOException {
        String script =
                loadResource(SCRIPT_PATH);

        assertTrue(
                script.contains(
                        "const DEFAULT_LANGUAGE"
                ),
                "El script debe declarar un idioma "
                        + "predeterminado."
        );

        assertTrue(
                script.contains(
                        "\"ca\";"
                ),
                "El valenciano debe actuar como "
                        + "idioma predeterminado."
        );

        assertTrue(
                script.contains(
                        "\"aulaflow.language\""
                ),
                "El script debe declarar una clave "
                        + "de almacenamiento propia."
        );

        assertTrue(
                script.contains(
                        "localStorage.getItem"
                ),
                "El script debe leer la preferencia "
                        + "almacenada."
        );

        assertTrue(
                script.contains(
                        "localStorage.setItem"
                ),
                "El script debe guardar la preferencia."
        );

        assertTrue(
                script.contains(
                        "applyLanguageWithFallback"
                ),
                "El script debe definir una estrategia "
                        + "de idioma de reserva."
        );

        assertTrue(
                script.contains(
                        "SUPPORTED_LANGUAGES.has"
                ),
                "El idioma debe validarse antes "
                        + "de utilizarse."
        );
    }

    private static String loadResource(
            String resourcePath
    ) throws IOException {
        try (
                InputStream inputStream =
                        LanguageSelectorContractTest
                                .class
                                .getResourceAsStream(
                                        resourcePath
                                )
        ) {
            assertNotNull(
                    inputStream,
                    "No se ha encontrado el recurso: "
                            + resourcePath
            );

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
