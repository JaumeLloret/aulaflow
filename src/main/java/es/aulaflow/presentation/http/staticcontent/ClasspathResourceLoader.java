package es.aulaflow.presentation.http.staticcontent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

final class ClasspathResourceLoader {

    Optional<byte[]> load(String resourcePath) throws IOException {
        validatePath(resourcePath);

        try (
                InputStream resourceStream =
                        ClasspathResourceLoader.class
                                .getResourceAsStream(resourcePath)
        ) {
            if (resourceStream == null) {
                return Optional.empty();
            }

            return Optional.of(
                    resourceStream.readAllBytes()
            );
        }
    }

    private static void validatePath(String resourcePath) {
        Objects.requireNonNull(
                resourcePath,
                "La ruta del recurso no puede ser null."
        );

        if (resourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "La ruta del recurso no puede estar vacía."
            );
        }

        if (!resourcePath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "La ruta del recurso debe comenzar por '/'. "
                            + "Valor recibido: "
                            + resourcePath
            );
        }
    }
}
