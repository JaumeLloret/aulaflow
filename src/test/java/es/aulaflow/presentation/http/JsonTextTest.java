package es.aulaflow.presentation.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTextTest {

    @Test
    void escapesCharactersThatWouldBreakJson() {
        String originalText =
                "quote: \" slash: \\ line:\n tab:\t";

        String expectedJsonText =
                "\"quote: \\\" slash: \\\\ "
                        + "line:\\n tab:\\t\"";

        assertEquals(
                expectedJsonText,
                JsonText.quote(originalText)
        );
    }
}
