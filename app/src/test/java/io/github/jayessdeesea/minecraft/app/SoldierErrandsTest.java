package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoldierErrandsTest {

    private static final BlockPos DEPOT = new BlockPos(-50, 76, 47), FARM = new BlockPos(13, 64, 29), OTHER_FARM = new BlockPos(29, 64, 29);
    private static final Map<BlockPos, Map<ItemClass, Integer>> STOCK = Map.of(
            DEPOT, Map.of(ItemClass.SEEDS, 100, ItemClass.LOG, 30),
            FARM, Map.of(ItemClass.SEEDS, 200, ItemClass.WHEAT, 40),
            OTHER_FARM, Map.of(ItemClass.SEEDS, 50));

    /** Seed counted apart: the general stockpile's without the farms', a farm's chest its own - each to its own 256. */
    @Test
    void seedIsCountedByTheChestItsBoundFor() {
        Set<BlockPos> seen = Set.of(DEPOT, FARM, OTHER_FARM), farms = Set.of(FARM, OTHER_FARM);

        assertEquals(100, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.empty()), "the general stockpile's");
        assertEquals(100, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.of(DEPOT)));
        assertEquals(200, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.of(FARM)), "that farm's chest alone");
        assertEquals(40, SoldierErrands.stocked(STOCK, seen, ItemClass.WHEAT, farms, Optional.of(DEPOT)), "any other kind, every chest's");
        assertEquals(100, SoldierErrands.stocked(STOCK, Set.of(DEPOT), ItemClass.SEEDS, farms, Optional.of(FARM)) + 100,
                "a farm's chest not seen holds none of the count");
    }

    /**
     * A trip of its own to stock the depot is made only with the squad leader's leave or on a duty whose yield is the
     * depot's (field696: a tree farm's one digger, 33 trips in a quarter of an hour for the seeds it carried, nothing put in).
     */
    @Test
    void aStockTripWantsLeaveOrADuty() {
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.stocks(false, false), "on a task, no duty: it digs");
        org.junit.jupiter.api.Assertions.assertTrue(SoldierErrands.stocks(true, false), "its squad holding");
        org.junit.jupiter.api.Assertions.assertTrue(SoldierErrands.stocks(false, true), "a harvester or a baker");
    }

    /**
     * A digger's trip to the chest takes what's no part of digging with it, whatever the depot holds of it (operator,
     * 2026-11-22: she doesn't need saplings or seeds when she's digging): its seeds and saplings go in; its dirt and
     * pickaxe don't, and nobody else's seeds do.
     */
    @Test
    void aDiggersTripTakesItsSeedsAndSaplingsWithIt() {
        io.github.jayessdeesea.minecraft.bot.world.Inventory pack = new io.github.jayessdeesea.minecraft.bot.world.Inventory();
        java.util.function.Function<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, Integer> item = kind ->
                java.util.stream.IntStream.range(0, 4096).filter(id -> io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.classOf(id).equals(java.util.Optional.of(kind))).findFirst().orElseThrow();
        int first = io.github.jayessdeesea.minecraft.bot.world.Inventory.MAIN_FIRST;
        pack.setSlot(first, new int[]{item.apply(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.SEEDS), 64});
        pack.setSlot(first + 1, new int[]{item.apply(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.DIRT), 64});
        pack.setSlot(first + 2, new int[]{item.apply(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.SAPLING), 31});
        pack.setSlot(first + 3, new int[]{item.apply(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.PICKAXE), 1});

        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(first + 5, first, first + 2), SoldierErrands.tripSlots(true, false, pack, java.util.List.of(first + 5)),
                "its load's surplus, then its seeds and saplings");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(first + 5), SoldierErrands.tripSlots(false, false, pack, java.util.List.of(first + 5)), "no digger: its load's surplus only");
    }

    /** A stand by a chest another soldier is in is taken; the asker's own, and one a block off, aren't (field698: ten at one cell). */
    @Test
    void aStandSomebodyElseIsInIsTaken() {
        io.github.jayessdeesea.minecraft.command.Position cell = new io.github.jayessdeesea.minecraft.command.Position(38.5, 67, 86.5);
        io.github.jayessdeesea.minecraft.command.Position me = new io.github.jayessdeesea.minecraft.command.Position(38.4, 67, 86.6);
        io.github.jayessdeesea.minecraft.command.Position bob = new io.github.jayessdeesea.minecraft.command.Position(38.7, 67, 86.2);
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.standTaken(cell, me, java.util.List.of(me)), "its own stand");
        org.junit.jupiter.api.Assertions.assertTrue(SoldierErrands.standTaken(cell, me, java.util.List.of(me, bob)), "Bob in it");
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.standTaken(cell, me, java.util.List.of(me,
                new io.github.jayessdeesea.minecraft.command.Position(40.0, 67, 86.5))), "Bob a block and a half off");
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.standTaken(cell, me, java.util.List.of(me,
                new io.github.jayessdeesea.minecraft.command.Position(38.5, 70, 86.5))), "Bob on the level above");
    }

    /**
     * The stores hold no more than 4,096 cobblestone and 4,096 dirt between every chest (operator, 2026-11-23: way too
     * much cobblestone; a max of 4,096 for both). Live, 33,342: the most of 256 was a chest's, not the stores'.
     */
    @Test
    void theStoresAreCappedInCobblestoneAndDirt() {
        io.github.jayessdeesea.minecraft.command.Stockpile under = new io.github.jayessdeesea.minecraft.command.Stockpile(0, 4095, 0, 0, 0, 4095);
        io.github.jayessdeesea.minecraft.command.Stockpile at = new io.github.jayessdeesea.minecraft.command.Stockpile(9999, 4096, 0, 0, 0, 4096);

        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.COBBLESTONE, java.util.Optional.of(under)));
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.DIRT, java.util.Optional.of(under)));
        org.junit.jupiter.api.Assertions.assertTrue(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.COBBLESTONE, java.util.Optional.of(at)));
        org.junit.jupiter.api.Assertions.assertTrue(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.DIRT, java.util.Optional.of(at)));
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.LOG, java.util.Optional.of(at)), "logs aren't capped here");
        org.junit.jupiter.api.Assertions.assertFalse(SoldierErrands.capped(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.COBBLESTONE, java.util.Optional.empty()), "no stores seen: nothing known to be full");
    }

    /** What a soldier draws for its work: the first its chests hold - seeds, then saplings while it plants, then a scout's food. */
    @Test
    void aSoldiersWorkDrawsItsSeedsItsSaplingsOrItsLureWhatTheChestsHold() {
        java.util.function.Predicate<ItemClass> all = k -> true, noSeeds = k -> k != ItemClass.SEEDS;
        assertEquals(Optional.of(new SoldierErrands.Makings(ItemClass.SEEDS, SoldierErrands.SEEDS_DRAWN)),
                SoldierErrands.makingsDrawn(true, true, Optional.of(ItemClass.WHEAT), all));
        assertEquals(Optional.of(new SoldierErrands.Makings(ItemClass.SAPLING, SoldierErrands.SAPLINGS_DRAWN)),
                SoldierErrands.makingsDrawn(true, true, Optional.empty(), noSeeds), "no seeds in any chest: its saplings");
        assertEquals(Optional.of(new SoldierErrands.Makings(ItemClass.WHEAT, ScoutMissions.FOOD_DRAWN)),
                SoldierErrands.makingsDrawn(false, false, Optional.of(ItemClass.WHEAT), all));
        assertEquals(Optional.empty(), SoldierErrands.makingsDrawn(false, true, Optional.empty(), k -> false), "no chest holds saplings: nothing");
        assertEquals(Optional.empty(), SoldierErrands.makingsDrawn(false, false, Optional.empty(), all), "short of nothing: nothing");
    }
}
