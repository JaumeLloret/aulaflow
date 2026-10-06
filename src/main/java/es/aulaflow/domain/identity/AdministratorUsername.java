package es.aulaflow.domain.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record AdministratorUsername(String value) {

    private static final int MINIMUM_LENGTH = 3;
    private static final int MAXIMUM_LENGTH = 64;

    private static final Pattern VALID_PATTERN =
            Pattern.compile(
                    "[a-z0-9._-]{"
                            + MINIMUM_LENGTH
                            + ","
                            + MAXIMUM_LENGTH
                            + "}"
            );

    public AdministratorUsername {
        Objects.requireNonNull(
                value,
                "El nombre del administrador no puede ser null."
        );

        value = value
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El nombre del administrador debe tener entre "
                            + MINIMUM_LENGTH
                            + " y "
                            + MAXIMUM_LENGTH
                            + " caracteres y utilizar únicamente "
                            + "letras minúsculas, números, punto, "
                            + "guion o guion bajo."
            );
        }
    }
}
