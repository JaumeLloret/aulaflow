package es.aulaflow.presentation.csv;

import es.aulaflow.application.auth.CsrfToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvPagesContractTest {

    private static final CsrfToken CSRF_TOKEN =
            new CsrfToken("a".repeat(43));

    @Test
    void importPageIncludesLanguageSelector() {
        String html = CsvPages.importForm(CSRF_TOKEN);

        assertTrue(
                html.contains("id=\"language-selector\"")
        );
        assertTrue(
                html.contains(
                        "data-i18n=\"language.selector.label\""
                )
        );
        assertTrue(
                html.contains(
                        "value=\"ca\" data-i18n=\"language.ca\""
                )
        );
        assertTrue(
                html.contains(
                        "value=\"es\" data-i18n=\"language.es\""
                )
        );
        assertTrue(
                html.contains(
                        "data-i18n=\"csv.import.document.title\""
                )
        );
    }
}
