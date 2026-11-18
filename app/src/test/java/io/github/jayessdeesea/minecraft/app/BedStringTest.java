package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The string a soldier draws from the depot for a bed (operator, 2026-11-18: beds, for a safe respawn). */
class BedStringTest {

    private final Inventory inventory = new Inventory();
    private int nextSlot = Inventory.MAIN_FIRST;

    private BedStringTest carrying(String item, int count) {
        int id = IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of(item))).findFirst().orElseThrow();
        inventory.setSlot(nextSlot++, new int[]{id, count});
        return this;
    }

    @Test
    void aSoldierWithNothingWantsTwelveString() {
        assertEquals(12, SoldierRunner.bedString(inventory));
    }

    @Test
    void woolAndStringCarriedCountTowardTheBed() {
        carrying("white_wool", 1).carrying("string", 5);
        assertEquals(3, SoldierRunner.bedString(inventory), "two wool short, eight string, five carried");
    }

    @Test
    void aSoldierWithABedWantsNone() {
        carrying("white_bed", 1);
        assertEquals(0, SoldierRunner.bedString(inventory));
    }
}
