package es.aulaflow.domain.board;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record LabelName(String value) {

    public static final int MAXIMUM_CODE_POINTS = 40;

    /**
     * Una conversión Unicode a minúsculas puede expandir un punto de
     * código visible en varios puntos de código internos. Este límite
     * protege la representación canónica sin reducir el máximo visible.
     */
    public static final int MAXIMUM_NORMALIZED_CODE_POINTS =
            MAXIMUM_CODE_POINTS * 3;

    private static final Pattern WHITESPACE_RUN =
            Pattern.compile("\\s+");

    public LabelName {
        Objects.requireNonNull(
                value,
                "El nombre de la etiqueta no puede ser null."
        );

        value = collapseWhitespace(value.trim());

        int codePoints = codePointCount(value);

        if (
                codePoints < 1
                        || codePoints > MAXIMUM_CODE_POINTS
        ) {
            throw new IllegalArgumentException(
                    "El nombre de la etiqueta debe tener entre 1 y "
                            + MAXIMUM_CODE_POINTS
                            + " caracteres."
            );
        }

        String normalized = normalize(value);

        if (
                codePointCount(normalized)
                        > MAXIMUM_NORMALIZED_CODE_POINTS
        ) {
            throw new IllegalArgumentException(
                    "La representación normalizada del nombre "
                            + "es demasiado larga."
            );
        }
    }

    /**
     * Forma canónica usada para comprobar unicidad dentro de un
     * tablero: espacios colapsados, minúsculas invariantes de locale y
     * NFC final, para no depender de la colación de SQLite y conservar
     * equivalencias canónicas después de una posible expansión Unicode.
     */
    public String normalized() {
        return normalize(value);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(
                text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFC
        );
    }

    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String collapseWhitespace(String text) {
        return WHITESPACE_RUN.matcher(text).replaceAll(" ");
    }
}
