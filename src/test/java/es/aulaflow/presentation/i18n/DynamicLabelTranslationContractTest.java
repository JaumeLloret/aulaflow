package es.aulaflow.presentation.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicLabelTranslationContractTest {

    @Test
    void translatingLabelActionPreservesDynamicLabelName()
            throws IOException {
        String script = readResource(
                "web/assets/js/app.js"
        );

        assertTrue(
                script.contains(
                        ".card-label-toggle-form "
                                + "button[data-i18n]"
                ),
                "La traducción debe distinguir los botones que "
                        + "contienen un nombre de etiqueta dinámico."
        );

        assertTrue(
                script.contains("button.dataset.labelName"),
                "El nombre dinámico debe conservarse entre cambios "
                        + "de idioma."
        );

        assertTrue(
                script.contains(
                        "`${translatedAction}: ${labelName}`"
                ),
                "La acción traducida debe seguir acompañada por el "
                        + "nombre visible de la etiqueta."
        );
    }

    private static String readResource(String path)
            throws IOException {
        try (
                InputStream input = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(path)
        ) {
            if (input == null) {
                throw new IOException(
                        "No se ha encontrado el recurso: " + path
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
