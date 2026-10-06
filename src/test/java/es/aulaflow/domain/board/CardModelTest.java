package es.aulaflow.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CardModelTest {

    @Test
    void cardIdRejectsZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardId(0)
        );
    }

    @Test
    void cardIdRejectsNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardId(-1)
        );
    }

    @Test
    void cardIdAcceptsPositive() {
        assertEquals(1L, new CardId(1).value());
    }

    @Test
    void cardTitleRejectsNull() {
        assertThrows(
                NullPointerException.class,
                () -> new CardTitle(null)
        );
    }

    @Test
    void cardTitleRejectsEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardTitle("")
        );
    }

    @Test
    void cardTitleRejectsBlank() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardTitle("   ")
        );
    }

    @Test
    void cardTitleTrims() {
        assertEquals(
                "Hola",
                new CardTitle("  Hola  ").value()
        );
    }

    @Test
    void cardTitleRejects161CodePoints() {
        String longTitle = "a".repeat(161);
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardTitle(longTitle)
        );
    }

    @Test
    void cardTitleAccepts160CodePoints() {
        String maxTitle = "a".repeat(160);
        assertEquals(160,
                new CardTitle(maxTitle).value()
                        .codePointCount(0, maxTitle.length())
        );
    }

    @Test
    void cardTitleAcceptsUnicodeCodePoints() {
        // Emoji counts as 2 Java chars but 1 code point
        String emoji = "😀"; // 😀
        assertEquals(
                emoji,
                new CardTitle(emoji).value()
        );
    }

    @Test
    void cardDescriptionRejectsNull() {
        assertThrows(
                NullPointerException.class,
                () -> new CardDescription(null)
        );
    }

    @Test
    void cardDescriptionAllowsEmpty() {
        assertEquals(
                "",
                new CardDescription("").value()
        );
    }

    @Test
    void cardDescriptionNormalizesCarriageReturn() {
        assertEquals(
                "a\nb",
                new CardDescription("a\r\nb").value()
        );
    }

    @Test
    void cardDescriptionNormalizesLoneCr() {
        assertEquals(
                "a\nb",
                new CardDescription("a\rb").value()
        );
    }

    @Test
    void cardDescriptionRejects4001CodePoints() {
        String tooLong = "a".repeat(4001);
        assertThrows(
                IllegalArgumentException.class,
                () -> new CardDescription(tooLong)
        );
    }

    @Test
    void cardDescriptionAccepts4000CodePoints() {
        String maxDesc = "a".repeat(4000);
        assertEquals(
                maxDesc,
                new CardDescription(maxDesc).value()
        );
    }

    @Test
    void cardRejectsNullId() {
        assertThrows(
                NullPointerException.class,
                () -> new Card(
                        null,
                        new ColumnId(1),
                        new CardTitle("T"),
                        new CardDescription(""),
                        0,
                        Instant.EPOCH,
                        Instant.EPOCH
                )
        );
    }

    @Test
    void cardRejectsNegativePosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Card(
                        new CardId(1),
                        new ColumnId(1),
                        new CardTitle("T"),
                        new CardDescription(""),
                        -1,
                        Instant.EPOCH,
                        Instant.EPOCH
                )
        );
    }

    @Test
    void cardRejectsUpdatedAtBeforeCreatedAt() {
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        Instant before = now.minusSeconds(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new Card(
                        new CardId(1),
                        new ColumnId(1),
                        new CardTitle("T"),
                        new CardDescription(""),
                        0,
                        now,
                        before
                )
        );
    }

    @Test
    void cardIsImmutable() {
        Instant now = Instant.EPOCH;
        Card card = new Card(
                new CardId(1),
                new ColumnId(2),
                new CardTitle("Título"),
                new CardDescription("Desc"),
                3,
                now,
                now
        );

        assertEquals(1L, card.id().value());
        assertEquals(2L, card.columnId().value());
        assertEquals("Título", card.title().value());
        assertEquals("Desc", card.description().value());
        assertEquals(3, card.position());
        assertEquals(now, card.createdAt());
        assertEquals(now, card.updatedAt());
    }
}
