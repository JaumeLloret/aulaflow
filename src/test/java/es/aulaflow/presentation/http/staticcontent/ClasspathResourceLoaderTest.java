package es.aulaflow.presentation.http.staticcontent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathResourceLoaderTest {

    private final ClasspathResourceLoader resourceLoader =
            new ClasspathResourceLoader();

    @Test
    void loadsAnExistingClasspathResource() throws IOException {
        Optional<byte[]> result =
                resourceLoader.load("/web/index.html");

        assertTrue(result.isPresent());

        String html = new String(
                result.orElseThrow(),
                StandardCharsets.UTF_8
        );

        assertTrue(
                html.contains("<!DOCTYPE html>")
        );
    }

    @Test
    void returnsEmptyWhenClasspathResourceDoesNotExist()
            throws IOException {
        Optional<byte[]> result =
                resourceLoader.load("/web/missing.html");

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsNullResourcePath() {
        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> resourceLoader.load(null)
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
                        () -> resourceLoader.load("   ")
                );

        assertEquals(
                "La ruta del recurso no puede estar vacía.",
                exception.getMessage()
        );
    }

    @Test
    void rejectsResourcePathWithoutLeadingSlash() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resourceLoader.load("web/index.html")
                );

        assertEquals(
                "La ruta del recurso debe comenzar por '/'. "
                        + "Valor recibido: "
                        + "web/index.html",
                exception.getMessage()
        );
    }
}
