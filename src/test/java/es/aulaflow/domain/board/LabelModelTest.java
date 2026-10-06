package es.aulaflow.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelModelTest {

    @Test
    void rejectsNonPositiveLabelId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelId(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelId(-1)
        );
    }

    @Test
    void trimsAndCollapsesWhitespaceInName() {
        assertEquals(
                "Urgent examen",
                new LabelName("  Urgent   examen  ").value()
        );
    }

    @Test
    void rejectsEmptyAndOversizedNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelName("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelName(
                        "x".repeat(
                                LabelName.MAXIMUM_CODE_POINTS + 1
                        )
                )
        );
    }

    @Test
    void normalizesNameForCaseInsensitiveCanonicalComparison() {
        LabelName first = new LabelName("URGENT");
        LabelName second = new LabelName("  urgent  ");

        assertEquals(
                first.normalized(),
                second.normalized()
        );
    }

    @Test
    void supportsUnicodeLowercaseExpansionWithinInternalLimit() {
        LabelName name = new LabelName(
                "İ".repeat(LabelName.MAXIMUM_CODE_POINTS)
        );

        assertEquals(
                LabelName.MAXIMUM_CODE_POINTS,
                name.value().codePointCount(
                        0,
                        name.value().length()
                )
        );

        assertEquals(
                LabelName.MAXIMUM_CODE_POINTS * 2,
                name.normalized().codePointCount(
                        0,
                        name.normalized().length()
                )
        );

        assertTrue(
                name.normalized().codePointCount(
                        0,
                        name.normalized().length()
                ) <= LabelName.MAXIMUM_NORMALIZED_CODE_POINTS
        );
    }

    @Test
    void acceptsKnownColorKeysCaseInsensitively() {
        assertEquals(
                LabelColor.RED,
                LabelColor.fromKey("RED")
        );

        assertEquals(
                LabelColor.BLUE,
                LabelColor.fromKey(" blue ")
        );
    }

    @Test
    void rejectsColorsOutsideTheClosedPalette() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LabelColor.fromKey("crimson")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> LabelColor.fromKey("#ff0000")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> LabelColor.fromKey(null)
        );
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Label(
                        new LabelId(1),
                        new BoardId(1),
                        new LabelName("Urgent"),
                        LabelColor.RED,
                        Instant.EPOCH.plusSeconds(10),
                        Instant.EPOCH
                )
        );
    }
}
