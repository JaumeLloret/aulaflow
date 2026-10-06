package es.aulaflow.application.csv;

import java.util.Objects;

public record CsvExportDocument(
        String filename,
        byte[] content
) {

    public CsvExportDocument {
        Objects.requireNonNull(
                filename,
                "El nombre de archivo no puede ser null."
        );

        if (filename.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre de archivo no puede "
                            + "estar vacío."
            );
        }

        Objects.requireNonNull(
                content,
                "El contenido no puede ser null."
        );

        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
