package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockLevelsTest {

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    private static Inventory saplings(int... stacks) {
        Inventory inventory = new Inventory();
        for (int i = 0; i < stacks.length; i++) {
            inventory.setSlot(Inventory.MAIN_FIRST + i, new int[]{item(ItemClass.SAPLING), stacks[i]});
        }
        return inventory;
    }

    private static final Map<ItemClass, Integer> KEEP_16 = Map.of(ItemClass.SAPLING, 16);

    /** Past what the pack keeps, saplings go into a depot under its most - the smallest stacks first. */
    @Test
    void whatThePackDoesntKeepIsStoredUnderTheMost() {
        Inventory inventory = saplings(16, 40, 30);

        assertEquals(List.of(Inventory.MAIN_FIRST, Inventory.MAIN_FIRST + 2),
                StockLevels.toStore(inventory, KEEP_16, kind -> 0), "the 16 and the 30, the 40 kept: 16 or more left");
    }

    /** Not past the most: a depot holding 240 of 256 takes the 16 that fill it, no hoard beyond. */
    @Test
    void nothingIsStoredPastTheMost() {
        Inventory inventory = saplings(16, 40, 30);

        assertEquals(List.of(Inventory.MAIN_FIRST), StockLevels.toStore(inventory, KEEP_16, kind -> 240));
        assertEquals(List.of(), StockLevels.toStore(inventory, KEEP_16, kind -> 256));
    }

    /** A reserve run short is a trip of its own; between the reserve and the most, it only rides along. */
    @Test
    void aShortReserveIsATripAndAFullOneIsNot() {
        Inventory inventory = saplings(16, 40);

        assertTrue(StockLevels.reserveShort(inventory, KEEP_16, kind -> 10));
        assertFalse(StockLevels.reserveShort(inventory, KEEP_16, kind -> 64), "the reserve's full: it rides along");
        assertFalse(StockLevels.reserveShort(saplings(16, 4), KEEP_16, kind -> 10), "four isn't worth the walk");
        assertTrue(StockLevels.reserveShort(saplings(16, 4), KEEP_16, kind -> 62), "but fills a reserve two short");
    }

    /** The pack throws a kind away only once the depot has its most of it: before then it's stored. */
    @Test
    void thePackThrowsOnlyWhatTheDepotHasItsMostOf() {
        Map<ItemClass, Integer> kept = Map.of(ItemClass.SAPLING, 16, ItemClass.COBBLESTONE, 320);

        assertEquals(Integer.MAX_VALUE, StockLevels.throwPast(kept, kind -> 100).get(ItemClass.SAPLING));
        assertEquals(16, StockLevels.throwPast(kept, kind -> 256).get(ItemClass.SAPLING));
        assertEquals(Integer.MAX_VALUE, StockLevels.throwPast(kept, kind -> 0).get(ItemClass.COBBLESTONE), "under 256: stored");
        assertEquals(320, StockLevels.throwPast(kept, kind -> 256).get(ItemClass.COBBLESTONE));
        assertEquals(5, StockLevels.throwPast(Map.of(ItemClass.STICK, 5), kind -> 0).get(ItemClass.STICK), "no level: its keep");
    }

    /** Diamonds and iron have no most: never at it, however many the depot holds (operator, 2026-11-17). */
    @Test
    void oreAndDiamondsAreNeverAtTheirMost() {
        assertFalse(StockLevels.atMost(ItemClass.DIAMOND, kind -> 1_000_000));
        assertFalse(StockLevels.atMost(ItemClass.RAW_IRON, kind -> 1_000_000));
        assertTrue(StockLevels.atMost(ItemClass.RAW_COPPER, kind -> 256), "copper's most is 256, as saplings'");
    }
}
