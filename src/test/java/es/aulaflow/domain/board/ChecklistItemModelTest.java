package es.aulaflow.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecklistItemModelTest {

    @Test
    void rejectsNonPositiveItemId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChecklistItemId(0)
        );
    }

    @Test
    void trimsText() {
        assertEquals(
                "Revisar exercici 3",
                new ChecklistItemText(
                        "  Revisar exercici 3  "
                ).value()
        );
    }

    @Test
    void rejectsEmptyAndOversizedText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChecklistItemText("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChecklistItemText(
                        "x".repeat(
                                ChecklistItemText
                                        .MAXIMUM_CODE_POINTS
                                        + 1
                        )
                )
        );
    }

    @Test
    void rejectsNegativePosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> item(-1, false)
        );
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChecklistItem(
                        new ChecklistItemId(1),
                        new CardId(1),
                        new ChecklistItemText("Text"),
                        false,
                        0,
                        Instant.EPOCH.plusSeconds(10),
                        Instant.EPOCH
                )
        );
    }

    @Test
    void progressIsZeroForEmptyChecklist() {
        ChecklistProgress progress =
                ChecklistProgress.of(List.of());

        assertEquals(0, progress.totalItems());
        assertEquals(0, progress.completedItems());
        assertEquals(0, progress.percentage());
    }

    @Test
    void progressIsPartialWhenSomeItemsAreCompleted() {
        ChecklistProgress progress = ChecklistProgress.of(
                List.of(
                        item(0, true),
                        item(1, false),
                        item(2, false)
                )
        );

        assertEquals(3, progress.totalItems());
        assertEquals(1, progress.completedItems());
        assertEquals(33, progress.percentage());
    }

    @Test
    void progressIsCompleteWhenAllItemsAreCompleted() {
        ChecklistProgress progress = ChecklistProgress.of(
                List.of(
                        item(0, true),
                        item(1, true)
                )
        );

        assertEquals(100, progress.percentage());
    }

    @Test
    void rejectsCompletedItemsGreaterThanTotal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChecklistProgress(1, 2)
        );
    }

    private static ChecklistItem item(
            int position,
            boolean completed
    ) {
        return new ChecklistItem(
                new ChecklistItemId(1),
                new CardId(1),
                new ChecklistItemText("Text"),
                completed,
                position,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
