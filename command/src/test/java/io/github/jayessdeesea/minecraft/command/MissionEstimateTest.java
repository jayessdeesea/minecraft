package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionEstimateTest {

    /** The keep's bill: a tool a 132 blocks, a log for four handles, two logs a chest of the slab's stone. */
    @Test
    void theKeepsBillIsItsToolsChestsAndRations() {
        MissionEstimate keep = MissionEstimate.keep(2_640, 4_096, 2_000, 8, 10, 100, 1_000);

        assertEquals(20, keep.bill().get(0).count(), "2,640 blocks over 132 uses: 20 tools");
        assertEquals(2, keep.bill().get(2).count(), "3,096 cobblestone to keep over 1,728 a chest: 2 chests");
        assertEquals(5 + 4, keep.logs(), "5 logs of handles, 4 of chests");
        assertEquals(3.4, keep.days(), 0.05, "6,736 blocks and cells at 2,000 a day");
        assertEquals(270, keep.bill().get(4).count(), "3.37 days of 8 builders at 10 rations");
        assertTrue(keep.feasible());
    }

    /** Short of logs above the reorder point, it starts with a preparation: the depot stocked first. */
    @Test
    void shortOfLogsItPreparesFirst() {
        MissionEstimate keep = MissionEstimate.keep(2_640, 4_096, 2_000, 8, 10, 3, 1_000);

        assertFalse(keep.feasible());
        assertEquals("6 logs", keep.short_().getFirst().toString(), "9 wanted, 3 to spare");
        assertTrue(keep.line().endsWith("PREPARE first: 6 logs"), keep.line());
    }

    /**
     * Ore no builder's pickaxe gets the drop from is dig left, not done (operator, 2026-11-15: the
     * emerald still stood): with no iron pickaxe in the force, the bill has one and the shortfall
     * the iron it lacks.
     */
    @Test
    void oreSetAsideWantsAnIronPickaxeFirst() {
        MissionEstimate keep = MissionEstimate.keep(0, 0, 2_000, 8, 10, 100, 1_000, 3, false, 1);

        assertFalse(keep.feasible());
        assertTrue(keep.bill().stream().anyMatch(l -> l.what().equals("iron pickaxe")), keep.line());
        assertEquals("2 iron", keep.short_().getFirst().toString(), "3 for the pickaxe, 1 on hand");
        assertTrue(keep.line().contains("3 ore set aside for an iron pickaxe"), keep.line());

        MissionEstimate armed = MissionEstimate.keep(0, 0, 2_000, 8, 10, 100, 1_000, 3, true, 0);
        assertTrue(armed.feasible(), "the iron pickaxe made: only its holder's walk to the ore");
        assertTrue(armed.line().contains("3 ore set aside for the iron pickaxe"), armed.line());
    }

    @Test
    void withNoRateMeasuredItSaysSo() {
        assertTrue(MissionEstimate.keep(100, 0, 0, 8, 10, 100, 0).line().contains("no rate measured yet"));
    }
}
