package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DutyYieldTest {

    private static DutyOrder order(DutyOrder.Duty duty) {
        return new DutyOrder("Dana", duty, Optional.empty(), Optional.empty());
    }

    /** A baker its batch while a batch's wheat is stored, fewer for less, none for none. */
    @Test
    void aBakerShouldBakeItsBatchWhileWheatIsStored() {
        DutyOrder baker = order(DutyOrder.Duty.BAKER).making(Optional.empty(), Optional.empty(), 8);
        assertEquals(8, DutyYield.expected(baker, 544));
        assertEquals(5, DutyYield.expected(baker, 15));
        assertEquals(0, DutyYield.expected(baker, 2), "no loaf's wheat: nothing to bake, not behind");
    }

    /** A harvester a sweep once a sweep's worth is ripe; a woodcutter its logs, a planter a sapling, while there's ground to work. */
    @Test
    void theOthersShouldYieldWhileTheirGroundHasWork() {
        assertEquals(DutyYield.SWEEP, DutyYield.expected(order(DutyOrder.Duty.HARVESTER), 30));
        assertEquals(0, DutyYield.expected(order(DutyOrder.Duty.HARVESTER), DutyYield.SWEEP - 1), "it waits for a sweep's worth");
        assertEquals(DutyYield.LOGS, DutyYield.expected(order(DutyOrder.Duty.WOODCUTTER), 1));
        assertEquals(0, DutyYield.expected(order(DutyOrder.Duty.WOODCUTTER), 0));
        assertEquals(1, DutyYield.expected(order(DutyOrder.Duty.PLANTER), 1));
        assertEquals(0, DutyYield.expected(order(DutyOrder.Duty.PLANTER), 0));
    }
}
