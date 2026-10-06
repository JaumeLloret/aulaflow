package es.aulaflow.domain.board;

import java.util.Locale;

/**
 * Paleta cerrada de colores admitidos para una etiqueta. Ninguna
 * cadena CSS ni color arbitrario proporcionado por el usuario se
 * almacena ni se interpola: solo estas claves estables.
 */
public enum LabelColor {

    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
    GRAY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LabelColor fromKey(String key) {
        if (key != null) {
            String normalized =
                    key.trim().toLowerCase(Locale.ROOT);

            for (LabelColor color : values()) {
                if (color.key().equals(normalized)) {
                    return color;
                }
            }
        }

        throw new IllegalArgumentException(
                "El color de la etiqueta no pertenece a la "
                        + "paleta admitida."
        );
    }
}
