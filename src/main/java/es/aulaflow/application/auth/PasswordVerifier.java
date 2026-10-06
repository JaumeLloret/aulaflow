package es.aulaflow.application.auth;

import java.util.Objects;

public final class PasswordVerifier {

    private final String encodedValue;

    public PasswordVerifier(String encodedValue) {
        Objects.requireNonNull(
                encodedValue,
                "El verificador de contraseña no puede ser null."
        );

        if (encodedValue.isBlank()) {
            throw new IllegalArgumentException(
                    "El verificador de contraseña no puede estar vacío."
            );
        }

        this.encodedValue = encodedValue;
    }

    public String encodedValue() {
        return encodedValue;
    }

    @Override
    public String toString() {
        return "PasswordVerifier[protected]";
    }
}
