package es.aulaflow.presentation.http.staticcontent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticContentTypesTest {

    @Test
    void identifiesHtmlContentType() {
        assertEquals(
                "text/html; charset=utf-8",
                StaticContentTypes.forResourcePath(
                        "/web/index.html"
                )
        );
    }

    @Test
    void identifiesCssContentType() {
        assertEquals(
                "text/css; charset=utf-8",
                StaticContentTypes.forResourcePath(
                        "/web/assets/css/app.css"
                )
        );
    }

    @Test
    void identifiesJavaScriptContentType() {
        assertEquals(
                "text/javascript; charset=utf-8",
                StaticContentTypes.forResourcePath(
                        "/web/assets/js/app.js"
                )
        );
    }

    @Test
    void identifiesJsonContentType() {
        assertEquals(
                "application/json; charset=utf-8",
                StaticContentTypes.forResourcePath(
                        "/web/i18n/ca.json"
                )
        );
    }

    @Test
    void usesBinaryContentTypeForUnknownExtension() {
        assertEquals(
                "application/octet-stream",
                StaticContentTypes.forResourcePath(
                        "/web/assets/aulaflow.data"
                )
        );
    }

    @Test
    void rejectsNullResourcePath() {
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> StaticContentTypes
                                .forResourcePath(null)
                );

        assertEquals(
                "La ruta del recurso no puede ser null.",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBlankResourcePath() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StaticContentTypes
                                .forResourcePath("   ")
                );

        assertEquals(
                "La ruta del recurso no puede estar vacía.",
                exception.getMessage()
        );
    }
}
