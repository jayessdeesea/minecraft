package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositorTest {

    private static final Position CHEST = new Position(0, 64, 0);
    private static final Position BESIDE = new Position(1.5, 64, 0.5);
    private static final Position FAR = new Position(20, 64, 0);

    private final FakeChestServer server = new FakeChestServer();
    private final List<Stockpile> seen = new ArrayList<>();
    private final Depositor depositor = new Depositor("Bob", server.session, server.inventory, seen::add);

    DepositorTest() {
        server.connect(depositor::onContainer);
    }

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    private void run(int ticks, Position soldier, boolean wanted) {
        for (int i = 0; i < ticks; i++) {
            server.tick();
            depositor.tick(soldier, Optional.of(CHEST), wanted);
        }
    }

    /** Past halfway to where it's going, a trip is a reason to finish it (field618: Mia walked back with her load). */
    @Test
    void aTripPastHalfwaySaysSo() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        server.inventory.setSlot(11, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        java.util.Set<ItemClass> surplus = java.util.Set.of(ItemClass.COBBLESTONE);
        for (int i = 0; i < 3; i++) {
            server.tick();
            depositor.tickDump(FAR, Optional.of(BESIDE), surplus, true);
        }
        assertTrue(depositor.dumping());
        assertFalse(depositor.pastHalfway(), "setting off");
        depositor.tickDump(new Position(16, 64, 0), Optional.of(BESIDE), surplus, true);
        assertFalse(depositor.pastHalfway(), "a quarter of the way");
        depositor.tickDump(new Position(10, 64, 0), Optional.of(BESIDE), surplus, true);
        assertTrue(depositor.pastHalfway(), "past halfway: finished, wanted or not");
    }

    @Test
    void leavesItsSurplusAtTheDumpButAStackOfEach() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        server.inventory.setSlot(11, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        server.inventory.setSlot(12, new int[]{itemOf(ItemClass.DIRT), 30});
        server.inventory.setSlot(13, new int[]{itemOf(ItemClass.COBBLESTONE), 12});
        server.inventory.setSlot(14, new int[]{itemOf(ItemClass.LOG), 5});
        java.util.Set<ItemClass> surplus = java.util.Set.of(ItemClass.COBBLESTONE, ItemClass.DIRT);

        for (int i = 0; i < 3; i++) {
            server.tick();
            depositor.tickDump(FAR, Optional.of(BESIDE), surplus, true);
        }
        assertTrue(depositor.dumping());
        assertEquals(Optional.of(BESIDE), depositor.standAt(), "it stands at the dump itself");
        for (int i = 0; i < 5; i++) {
            server.tick();
            depositor.tickDump(BESIDE, Optional.of(BESIDE), surplus, true);
        }

        assertFalse(depositor.active());
        assertEquals(64, server.inventory.count(ItemClass.COBBLESTONE), "one stack kept for steps");
        assertEquals(30, server.inventory.count(ItemClass.DIRT), "a kind's first stack is kept");
        assertEquals(5, server.inventory.count(ItemClass.LOG), "nothing else thrown");
        assertEquals(2, server.thrown.size());
        assertEquals(List.of(), seen, "no chest opened");
    }

    @Test
    void drawsStacksOfAMaterialOutUntilItCarriesEnough() {
        server.chestItems[0] = itemOf(ItemClass.COBBLESTONE);
        server.chestCounts[0] = 64;
        server.chestItems[1] = itemOf(ItemClass.LOG);
        server.chestCounts[1] = 10;
        server.chestItems[2] = itemOf(ItemClass.COBBLESTONE);
        server.chestCounts[2] = 64;

        for (int i = 0; i < 3; i++) {
            server.tick();
            depositor.tickDraw(FAR, Optional.of(CHEST), ItemClass.COBBLESTONE, 40, true);
        }
        assertEquals(Optional.of(CHEST), depositor.standAt());
        for (int i = 0; i < 10; i++) {
            server.tick();
            depositor.tickDraw(BESIDE, Optional.of(CHEST), ItemClass.COBBLESTONE, 40, true);
        }

        assertFalse(depositor.active());
        assertEquals(64, server.inventory.count(ItemClass.COBBLESTONE), "one stack makes up the 40");
        assertEquals(0, server.inventory.count(ItemClass.LOG), "nothing else taken");
        assertEquals(List.of(new Stockpile(10, 64, 0)), seen);
    }

    @Test
    void walksOverAndPutsInTheFullLoadOnlyThenReportsTheStockpile() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 20});
        server.inventory.setSlot(11, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE - 20});
        server.inventory.setSlot(12, new int[]{itemOf(ItemClass.LOG), 3}); // pickaxe wood: kept
        server.chestItems[0] = itemOf(ItemClass.LOG);
        server.chestCounts[0] = 10;

        run(3, FAR, true);
        assertEquals(Optional.of(CHEST), depositor.standAt());
        run(10, BESIDE, true);

        assertFalse(depositor.active());
        int kept = server.inventory.count(ItemClass.COBBLESTONE);
        assertTrue(kept >= Depositor.ROUTE_BLOCKS && kept < Gatherer.CARRY_STONE, "a stack kept back to bridge and pillar with: " + kept);
        assertEquals(3, server.inventory.count(ItemClass.LOG));
        assertEquals(List.of(new Stockpile(10, Gatherer.CARRY_STONE - kept, 0)), seen);
    }

    @Test
    void aTripNoLongerWantedOnTheWayIsDropped() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE});

        run(2, FAR, true);
        assertTrue(depositor.active());
        run(1, FAR, false);

        assertFalse(depositor.active());
        assertEquals(List.of(), server.log);
    }

    @Test
    void aChestTooFullToTakeTheLoadFailsLoudly() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE});
        for (int slot = 0; slot < FakeChestServer.CHEST_SLOTS; slot++) {
            server.chestItems[slot] = itemOf(ItemClass.LOG);
            server.chestCounts[slot] = 64;
        }

        run(ChestWindow.ANSWER_TICKS + 5, BESIDE, true);

        assertFalse(depositor.active());
        assertEquals(List.of(new Stockpile(FakeChestServer.CHEST_SLOTS * 64, 0, 0)), seen, "what the full chest holds, read when opened");
        assertEquals(Gatherer.CARRY_STONE, server.inventory.count(ItemClass.COBBLESTONE));
        assertTrue(depositor.full().isPresent(), "and it says which chest was full, for the depot's next");
    }

    /**
     * A trip that put nothing in isn't made again while the soldier carries the same (field693, Kate: farm to chest and
     * back every 20 s with a load none of which was the chest's). With something new carried, it goes again.
     */
    @Test
    void aTripThatPutNothingInIsNotMadeAgainWithTheSameLoad() {
        server.inventory.setSlot(12, new int[]{itemOf(ItemClass.LOG), 3}); // pickaxe wood: kept, so nothing to put in

        run(3, FAR, true);
        run(10, BESIDE, true);
        assertFalse(depositor.active(), "the first trip made, and over");
        int opened = seen.size();

        run(10, BESIDE, true);
        assertFalse(depositor.active(), "not set off again");
        assertEquals(opened, seen.size(), "nor the chest opened again");

        // Nor at once with a load that differs by what it has used since - a log fewer (field701, Ivy: two cobblestone
        // lighter each time, and back to the full chest): the chest that took nothing is left alone a while.
        server.inventory.setSlot(12, new int[]{itemOf(ItemClass.LOG), 2});
        run(Depositor.COOLDOWN_TICKS / 2, BESIDE, true);
        assertFalse(depositor.active(), "not back to that chest straight away");
        assertEquals(opened, seen.size());

        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE});
        for (int i = 0; i < 400 && !depositor.active(); i++) {
            run(1, FAR, true);
        }
        assertTrue(depositor.active(), "a new load: a new trip");
    }

    /**
     * A paver's cobblestone is its work's: told to keep it, a trip puts none of it in, however much it carries (in the
     * squad simulation one drew a chest's 627 and put 625 back, round and round; live, 60 drawn, one paved, 59 put back).
     */
    @Test
    void aPaversCobblestoneIsNotPutIn() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        server.inventory.setSlot(11, new int[]{itemOf(ItemClass.COBBLESTONE), 64});
        depositor.keepsStone(true);

        run(3, FAR, true);
        run(10, BESIDE, true);

        assertEquals(128, server.inventory.count(ItemClass.COBBLESTONE), "all of it still carried");
    }

    /** Saplings are taken out of a chest birch first, then spruce, then the rest; anything else in the chest's order (operator, 2026-11-25). */
    @Test
    void saplingsAreTakenOutBirchFirst() {
        int oak = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.idOf("oak_sapling").orElseThrow();
        int spruce = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.idOf("spruce_sapling").orElseThrow();
        int birch = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.idOf("birch_sapling").orElseThrow();
        int dirt = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.idOf("dirt").orElseThrow();
        java.util.List<Integer> chest = java.util.List.of(oak, dirt, spruce, birch, -1, birch);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(3, 5, 2, 0),
                Depositor.takeOrder(chest, io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.SAPLING));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(1),
                Depositor.takeOrder(chest, io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.DIRT));
    }
}
