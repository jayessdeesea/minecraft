package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    /** A spell's routes summed: how many, what each took a step planned - its median and quartiles - and the totals. */
    @Test
    void aSpellsRoutesAreSummedAsTicksAStepPlanned() {
        Ledger ledger = new Ledger();
        ledger.route(10, 50, 0);
        ledger.route(20, 100, 1000);
        ledger.route(10, 80, 2000);
        ledger.route(4, 20, 3000);

        List<String> lines = ledger.summary();

        assertEquals(1, lines.size(), "" + lines);
        assertTrue(lines.getFirst().startsWith("route: 4, 5.00 a unit planned (median; quartiles 5.00-8.00), 44 planned, 250 taken"), lines.getFirst());
    }

    /** A route planned at no steps measures nothing. */
    @Test
    void nothingPlannedIsNoRate() {
        assertTrue(Ledger.rates(List.of(new double[]{0, 30})).isEmpty());
    }

    /** Routes walked at the simulation's rate are no divergence; twice as slow, one - named with its figures. */
    @Test
    void aRateFarOffTheSimulationsIsADivergence() {
        Ledger near = new Ledger();
        near.route(100, 540, 0);
        assertTrue(near.fidelity().isEmpty(), "" + near.fidelity());

        Ledger slow = new Ledger();
        slow.route(100, 1000, 0);
        assertEquals("route: 10.00 ticks a unit over 1, the simulation's 4.90 - 2.0x off", slow.fidelity().orElseThrow());
        Ledger slowFarm = new Ledger();
        slowFarm.record("harvest", 10, 1000, 0);
        assertEquals("harvest: 100.00 ticks a unit over 1, the simulation's 24.50 - 4.1x off", slowFarm.fidelity().orElseThrow());
    }
}
