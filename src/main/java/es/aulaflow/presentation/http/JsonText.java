package es.aulaflow.presentation.http;

import java.util.Objects;

public final class JsonText {

    private JsonText() {
    }

    public static String quote(String value) {
        Objects.requireNonNull(
                value,
                "El texto JSON no puede ser null."
        );

        StringBuilder escaped =
                new StringBuilder(value.length() + 2);

        escaped.append('"');

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");

                default -> {
                    if (character < 0x20) {
                        escaped.append(
                                "\\u%04x".formatted(
                                        (int) character
                                )
                        );
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }

        escaped.append('"');

        return escaped.toString();
    }
}
