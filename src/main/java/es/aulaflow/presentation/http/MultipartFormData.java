package es.aulaflow.presentation.http;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MultipartFormData(
        Map<String, String> fields,
        Optional<byte[]> file
) {

    public MultipartFormData {
        fields = Map.copyOf(
                Objects.requireNonNull(
                        fields,
                        "Los campos no pueden ser null."
                )
        );

        Objects.requireNonNull(
                file,
                "El archivo no puede ser null."
        );
    }
}
