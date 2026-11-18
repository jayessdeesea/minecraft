package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionLevelsTest {

    private static final ProductionLevels LEVELS = ProductionLevels.STANDING;

    @Test
    void theStandingLevelsAreTheOperators() {
        assertEquals(new ProductionLevels.StockLevel(64, 1024), LEVELS.of(Renewable.BREAD));
        assertEquals(new ProductionLevels.StockLevel(4, 64), LEVELS.of(Renewable.SAPLINGS));
        assertEquals(new ProductionLevels.StockLevel(64, 1024), LEVELS.of(Renewable.LOGS));
    }

    @Test
    void underItsLeastAKindIsShort() {
        Map<Renewable, Integer> stock = Map.of(Renewable.BREAD, 63, Renewable.LOGS, 64, Renewable.SAPLINGS, 3,
                Renewable.WHEAT, 500, Renewable.SEEDS, 500);
        assertEquals(Set.of(Renewable.BREAD, Renewable.SAPLINGS), LEVELS.short_(stock::get));
    }

    /** Between its least and its most, a kind is produced only if it's being produced: no flapping. */
    @Test
    void aKindBeingProducedStaysWantedUntilItsMost() {
        Map<Renewable, Integer> stock = Map.of(Renewable.BREAD, 500, Renewable.LOGS, 1024, Renewable.SAPLINGS, 10,
                Renewable.WHEAT, 500, Renewable.SEEDS, 500);
        assertEquals(Set.of(), LEVELS.wanted(stock::get, Set.of()), "nobody on it: nothing wanted");
        assertEquals(Set.of(Renewable.BREAD), LEVELS.wanted(stock::get, Set.of(Renewable.BREAD, Renewable.LOGS)),
                "bread under its most stays wanted; logs at their most don't");
    }

    /** Levels change with what's ahead - more bread before an external mission. */
    @Test
    void levelsAreRaisedByHandingOnOthers() {
        ProductionLevels mission = LEVELS.with(Renewable.BREAD, new ProductionLevels.StockLevel(512, 2048));
        assertEquals(512, mission.of(Renewable.BREAD).least());
        assertEquals(64, LEVELS.of(Renewable.BREAD).least(), "the standing levels unchanged");
    }

    @Test
    void aLeastOverItsMostIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionLevels.StockLevel(10, 5));
    }
}
