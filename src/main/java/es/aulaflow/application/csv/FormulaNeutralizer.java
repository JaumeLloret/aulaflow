package es.aulaflow.application.csv;

import java.util.Objects;

/**
 * Neutralización reversible frente a inyección de fórmulas CSV
 * (CWE-1236), según la sección 9 del contrato AulaFlow Kanban CSV v1.
 */
public final class FormulaNeutralizer {

    private static final String DANGEROUS_PREFIXES =
            "=+-@";

    private FormulaNeutralizer() {
    }

    public static String protect(String raw) {
        Objects.requireNonNull(
                raw,
                "El valor no puede ser null."
        );

        if (raw.startsWith("'")) {
            return "'" + raw;
        }

        if (hasDangerousFirstSignificantCharacter(raw)) {
            return "'" + raw;
        }

        return raw;
    }

    public static String unprotect(String raw) {
        Objects.requireNonNull(
                raw,
                "El valor no puede ser null."
        );

        if (raw.startsWith("''")) {
            return raw.substring(1);
        }

        if (raw.startsWith("'")) {
            String rest = raw.substring(1);

            if (hasDangerousFirstSignificantCharacter(rest)) {
                return rest;
            }
        }

        return raw;
    }

    private static boolean hasDangerousFirstSignificantCharacter(
            String value
    ) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            if (character == ' ' || character == '\t') {
                continue;
            }

            return DANGEROUS_PREFIXES.indexOf(character)
                    >= 0;
        }

        return false;
    }
}
