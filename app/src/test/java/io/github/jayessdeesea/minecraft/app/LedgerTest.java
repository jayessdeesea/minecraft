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
}
