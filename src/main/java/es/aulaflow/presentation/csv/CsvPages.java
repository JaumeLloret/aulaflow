package es.aulaflow.presentation.csv;

import es.aulaflow.application.auth.CsrfToken;
import es.aulaflow.application.csv.CsvValidationError;
import es.aulaflow.application.csv.CsvValidationReport;
import es.aulaflow.application.csv.CsvWarning;

import java.util.List;
import java.util.Optional;

/**
 * Páginas HTML de importación CSV: HTML semántico, resumen de
 * errores, sin depender del color, con foco visible y funcionamiento
 * completo sin JavaScript.
 */
final class CsvPages {

    private CsvPages() {
    }

    static String importForm(CsrfToken csrfToken) {
        String content = """
                <nav aria-label="Navegació de taulers">
                  <a href="/boards" data-i18n="csv.import.back">Tornar als taulers</a>
                </nav>
                <section class="boards-panel" aria-labelledby="csv-import-title">
                  <h1 id="csv-import-title" data-i18n="csv.import.title">
                    Importar un tauler des de CSV
                  </h1>
                  <p data-i18n="csv.import.description">
                    Puja un fitxer AulaFlow Kanban CSV v1 per a crear un tauler nou.
                  </p>
                  <p data-i18n="csv.import.limits">
                    Mida màxima 1 MiB. Codificació UTF-8.
                  </p>
                  <p data-i18n="csv.import.copy.notice">
                    Cada importació crea sempre un tauler nou i independent; repetir-la crea una còpia.
                  </p>
                  <p data-i18n="csv.import.scope.notice">
                    El contracte CSV v1 no inclou etiquetes ni checklists.
                  </p>
                  <p data-i18n="csv.import.contract.link">
                    Consulta el contracte AulaFlow Kanban CSV v1 (a la documentació del projecte) per a l'encapçalament exacte i un exemple.
                  </p>
                  <form method="post" action="/boards/import/preview"
                        enctype="multipart/form-data" class="board-form">
                    %s
                    <label for="csv-file" data-i18n="csv.import.file.label">
                      Fitxer CSV
                    </label>
                    <input id="csv-file" name="file" type="file"
                           accept=".csv,text/csv" required>
                    <button type="submit" data-i18n="csv.import.submit">
                      Validar CSV
                    </button>
                  </form>
                </section>
                """.formatted(csrfField(csrfToken));

        return layout(
                "Importar CSV · AulaFlow", content, csrfToken
        );
    }

    static String previewResult(
            CsvValidationReport report,
            Optional<String> token,
            CsrfToken csrfToken
    ) {
        StringBuilder summary = new StringBuilder();

        summary.append(
                "<section class=\"boards-panel\" "
                        + "aria-labelledby="
                        + "\"csv-summary-title\">"
        );

        summary.append(
                "<h1 id=\"csv-summary-title\" "
                        + "data-i18n=\"csv.import.summary"
                        + ".title\">Resum de la validació"
                        + "</h1>"
        );

        summary.append("<dl>");

        if (report.boardName() != null) {
            summary.append(
                    definitionRow(
                            "csv.import.summary.board",
                            "Tauler",
                            escape(report.boardName())
                    )
            );
        }

        summary.append(
                definitionRow(
                        "csv.import.summary.columns",
                        "Columnes",
                        String.valueOf(
                                report.columnCount()
                        )
                )
        );

        summary.append(
                definitionRow(
                        "csv.import.summary.cards",
                        "Targetes",
                        String.valueOf(
                                report.cardCount()
                        )
                )
        );

        summary.append(
                definitionRow(
                        "csv.import.summary.records",
                        "Registres",
                        String.valueOf(
                                report.totalRecords()
                        )
                )
        );

        summary.append("</dl>");
        summary.append("</section>");

        summary.append(
                issuesSection(
                        "csv.import.errors.title",
                        "Errors",
                        "csv-errors",
                        report.errors().stream()
                                .map(
                                        CsvPages
                                                ::errorText
                                )
                                .toList()
                )
        );

        summary.append(
                issuesSection(
                        "csv.import.warnings.title",
                        "Avisos",
                        "csv-warnings",
                        report.warnings().stream()
                                .map(
                                        CsvPages
                                                ::warningText
                                )
                                .toList()
                )
        );

        if (token.isPresent()) {
            summary.append("""
                    <form method="post" action="/boards/import/confirm"
                          class="board-form">
                      %s
                      <input type="hidden" name="token" value="%s">
                      <button type="submit" data-i18n="csv.import.confirm.submit">
                        Importar com a tauler nou
                      </button>
                    </form>
                    """.formatted(
                    csrfField(csrfToken),
                    escapeAttribute(token.orElseThrow())
            ));
        }

        return layout(
                "Importar CSV · AulaFlow",
                summary.toString(),
                csrfToken
        );
    }

    static String tooLarge(CsrfToken csrfToken) {
        String content = """
                <section class="boards-panel" aria-labelledby="csv-too-large-title">
                  <h1 id="csv-too-large-title" data-i18n="csv.import.toolarge.title">
                    Fitxer massa gran
                  </h1>
                  <p data-i18n="csv.import.toolarge.description">
                    El fitxer CSV supera la mida màxima permesa d'1 MiB.
                  </p>
                  <p>
                    <a href="/boards/import" data-i18n="csv.import.back">
                      Tornar als taulers
                    </a>
                  </p>
                </section>
                """;

        return layout(
                "Importar CSV · AulaFlow", content, csrfToken
        );
    }

    private static String issuesSection(
            String i18nKey,
            String fallbackTitle,
            String listId,
            List<String> items
    ) {
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder();

        section.append(
                "<section class=\"boards-panel\" "
                        + "aria-labelledby=\""
                        + listId + "-title\">"
        );

        section.append(
                "<h2 id=\"" + listId + "-title\" "
                        + "data-i18n=\"" + i18nKey + "\">"
                        + escape(fallbackTitle)
                        + "</h2>"
        );

        section.append("<ul>");

        for (String item : items) {
            section.append(
                    "<li>" + escape(item) + "</li>"
            );
        }

        section.append("</ul>");
        section.append("</section>");

        return section.toString();
    }

    private static String errorText(
            CsvValidationError error
    ) {
        return "Registre " + error.recordNumber() + ": "
                + error.message()
                + " (" + error.code() + ")";
    }

    private static String warningText(CsvWarning warning) {
        return "Registre " + warning.recordNumber() + ": "
                + warning.message()
                + " (" + warning.code() + ")";
    }

    private static String definitionRow(
            String i18nKey,
            String fallbackLabel,
            String value
    ) {
        return "<dt data-i18n=\"" + i18nKey + "\">"
                + escape(fallbackLabel) + "</dt>"
                + "<dd>" + escape(value) + "</dd>";
    }

    private static String layout(
            String title,
            String content,
            CsrfToken csrfToken
    ) {
        return """
                <!DOCTYPE html>
                <html lang="ca">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title data-i18n="csv.import.document.title">%s</title>
                  <link rel="stylesheet" href="/assets/css/app.css">
                </head>
                <body>
                  <header class="site-header site-header--compact">
                    <a class="brand-link" href="/" data-i18n="app.name">AulaFlow</a>
                    <nav aria-label="Navegació principal">
                      <a href="/boards" data-i18n="nav.boards">Taulers</a>
                      <a href="/account" data-i18n="nav.account">Compte</a>
                    </nav>
                    <div class="language-control">
                      <label for="language-selector" data-i18n="language.selector.label">
                        Idioma
                      </label>
                      <select id="language-selector" name="language" disabled>
                        <option value="ca" data-i18n="language.ca" selected>Valencià</option>
                        <option value="es" data-i18n="language.es">Castellà</option>
                      </select>
                    </div>
                    <form method="post" action="/logout">
                      %s
                      <button type="submit" class="secondary-button"
                              data-i18n="logout.submit">Tancar sessió</button>
                    </form>
                  </header>
                  <main class="boards-layout">%s</main>
                  <script src="/assets/js/app.js" defer></script>
                </body>
                </html>
                """.formatted(
                escape(title),
                csrfField(csrfToken),
                content
        );
    }

    private static String csrfField(CsrfToken token) {
        return """
                <input type="hidden" name="_csrf" value="%s">
                """.formatted(token.value());
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeAttribute(String value) {
        return escape(value)
                .replace("\n", "&#10;")
                .replace("\r", "&#13;");
    }
}
