package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BakerDutyTest {

    private static final Position CHEST = new Position(3.5, 64, 1.5);
    private static final Position TABLE = new Position(1.5, 64, -1.5);

    private static DutyOrder baker(Optional<Position> source, int batch) {
        return new DutyOrder("Dana", DutyOrder.Duty.BAKER, Optional.empty(), Optional.of(CHEST)).making(source, Optional.of(TABLE), batch);
    }

    /** Its draw and bake by its order's batch: short of a batch's wheat it draws, a batch carried it bakes. */
    @Test
    void aBakerDrawsShortOfItsOrdersBatchAndBakesABatch() {
        DutyOrder four = baker(Optional.of(CHEST), 4);
        assertEquals(12, BakerDuty.batchWheat(four));
        assertTrue(BakerDuty.draws(four, 11));
        assertFalse(BakerDuty.draws(four, 12), "a batch carried: it bakes, not draws");
        assertFalse(BakerDuty.bakes(four, 11, 64));
        assertTrue(BakerDuty.bakes(four, 12, 64));
        assertTrue(BakerDuty.bakes(four, 3, 2), "a loaf's, running out of rations itself");
        assertEquals(60, BakerDuty.drawTo(four), "whole batches to a stack");
        assertEquals(48, BakerDuty.drawTo(baker(Optional.of(CHEST), Subsistence.BAKE_BATCH)));
    }

    /** No chest named, no draw: the soldier picks no chest of its own. */
    @Test
    void aBakerWithNoSourceNamedDrawsNothing() {
        assertFalse(BakerDuty.draws(baker(Optional.empty(), 8), 0));
        assertFalse(BakerDuty.draws(new DutyOrder("Alice", DutyOrder.Duty.HARVESTER, Optional.empty(), Optional.of(CHEST)), 0));
    }

    /** An order with no batch has no draw: asked for one, it's said, not guessed. */
    @Test
    void anOrderWithNoBatchHasNoDraw() {
        assertThrows(IllegalArgumentException.class, () -> BakerDuty.drawTo(baker(Optional.of(CHEST), 0)));
    }
}
