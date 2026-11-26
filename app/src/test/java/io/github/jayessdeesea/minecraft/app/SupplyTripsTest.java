package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.LoadPlan;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The value decisions of the depositor's turn, each on pure inputs (app.SupplyTrips). */
class SupplyTripsTest {

    private static Inventory holding(ItemClass kind, int count) {
        Inventory pack = new Inventory();
        int id = java.util.stream.IntStream.range(0, 4096).filter(i -> ItemClassRegistry.classOf(i).equals(Optional.of(kind))).findFirst().orElseThrow();
        pack.setSlot(Inventory.MAIN_FIRST, new int[]{id, count});
        return pack;
    }

    /** A builder on the keep puts its depot's chest up once it carries {@link SoldierRunner#DEPOT_LOGS} logs, and no one else does. */
    @Test
    void theDepotsChestWaitsForALoadOfLogsOnTheKeep() {
        assertTrue(SupplyTrips.depotLogs(true, holding(ItemClass.LOG, SoldierRunner.DEPOT_LOGS)));
        assertFalse(SupplyTrips.depotLogs(true, holding(ItemClass.LOG, SoldierRunner.DEPOT_LOGS - 1)));
        assertFalse(SupplyTrips.depotLogs(false, holding(ItemClass.LOG, 64)));
    }

    /** A soldier with no dump order is not undumped, whatever it carries. */
    @Test
    void noDutyIsNeverUndumped() {
        assertFalse(SupplyTrips.undumped(Optional.empty(), holding(ItemClass.WHEAT, 64)));
    }

    /** A full load dumps only past what the depositor keeps of a surplus kind. */
    @Test
    void aFullLoadDumpsWhatIsHeldPastTheKeep() {
        Set<ItemClass> cobble = Set.of(ItemClass.COBBLESTONE);
        assertTrue(SupplyTrips.dumpNow(true, cobble, holding(ItemClass.COBBLESTONE, 65)));
        assertFalse(SupplyTrips.dumpNow(true, cobble, holding(ItemClass.COBBLESTONE, 64)), "the keep itself stays");
        assertFalse(SupplyTrips.dumpNow(false, cobble, holding(ItemClass.COBBLESTONE, 65)), "not on a part load");
        assertFalse(SupplyTrips.dumpNow(true, Set.of(), holding(ItemClass.COBBLESTONE, 65)), "no surplus kind");
        assertFalse(SupplyTrips.dumpNow(true, Set.of(ItemClass.DIRT), holding(ItemClass.COBBLESTONE, 65)), "another kind's");
    }

    /** The chest is fitted at no shop, and at a mine only for the stash. */
    @Test
    void noShopFitsNoChest() {
        Inventory pack = holding(ItemClass.CHEST, 1);
        assertFalse(SupplyTrips.fitStore(false, false, pack, true, true, true, true, true, Optional.empty(), () -> false, false, true, () -> false));
        assertFalse(SupplyTrips.fitStore(true, false, pack, true, true, true, true, true, Optional.empty(), () -> false, false, true, () -> false));
    }

    /** Down a mine by night: below the floor less the gallery's depth, and not for the stash. */
    @Test
    void aMinerStaysDownForTheNightBelowTheFloor() {
        assertFalse(SupplyTrips.downForTheNight(null, 0, 3, false), "no night floor");
        assertTrue(SupplyTrips.downForTheNight(40, 36.5, 3, false));
        assertFalse(SupplyTrips.downForTheNight(40, 37, 3, false), "at the gallery's depth");
        assertFalse(SupplyTrips.downForTheNight(40, 36.5, 3, true), "the stash's ore goes up");
    }

    /** What the pack keeps is its work's: a planter's first, then the keep's or the paver's, a miner's, a harvester's, everyone's. */
    @Test
    void thePackKeepsByWork() {
        assertSame(SoldierErrands.PLANTER_CARRIED, SupplyTrips.packKept(true, true, true, true));
        assertSame(SoldierErrands.KEEP_PACK, SupplyTrips.packKept(false, true, true, true));
        assertSame(SoldierErrands.MINE_CARRIED, SupplyTrips.packKept(false, false, true, true));
        assertSame(SoldierErrands.HARVESTER_CARRIED, SupplyTrips.packKept(false, false, false, true));
        assertSame(SoldierErrands.CARRIED, SupplyTrips.packKept(false, false, false, false));
    }

    /** Ore pooled is the metal pool's stock less what the depot has its most of, and the stash's kinds when the soldier works one. */
    @Test
    void thePoolIsTheMetalStockPlusTheStash() {
        Set<ItemClass> stock = Set.of(ItemClass.RAW_IRON, ItemClass.RAW_COPPER);
        assertEquals(stock, SupplyTrips.pooled(stock, false, k -> 0));
        assertEquals(Set.of(ItemClass.RAW_IRON, ItemClass.RAW_COPPER, ItemClass.DIAMOND), SupplyTrips.pooled(stock, true, k -> 0));
        assertEquals(Set.of(ItemClass.RAW_IRON), SupplyTrips.pooled(stock, false, k -> k == ItemClass.RAW_COPPER ? 1_000_000 : 0), "copper past 256");
    }

    /** A surplus trip needs the upgrade allowed, and a few slots of surplus besides. */
    @Test
    void aSurplusTripNeedsLeaveAndSlots() {
        Inventory empty = new Inventory();
        assertFalse(SupplyTrips.surplusTrip(true, Optional.of(LoadPlan.FLATTENING), empty));
        assertFalse(SupplyTrips.surplusTrip(false, Optional.of(LoadPlan.FLATTENING), empty));
        assertFalse(SupplyTrips.surplusTrip(true, Optional.empty(), empty));
    }

    /** What the stores hold, as the slots to store ask: nothing of a kind capped, no sapling unless it plants. */
    @Test
    void theStoredCountIsCappedAndSaplingsAreAPlantersOnly() {
        assertEquals(7, SupplyTrips.stored(ItemClass.LOG, Optional.empty(), false, () -> 7));
        assertEquals(0, SupplyTrips.stored(ItemClass.SAPLING, Optional.empty(), false, () -> 7));
        assertEquals(7, SupplyTrips.stored(ItemClass.SAPLING, Optional.empty(), true, () -> 7));
    }

    /** A paver's cobblestone is its work's: never among the slots put in. */
    @Test
    void aPaversCobblestoneIsNeverPutIn() {
        Inventory pack = holding(ItemClass.COBBLESTONE, 64);
        assertFalse(SupplyTrips.slots(false, false, true, true, pack, Optional.empty(), SoldierErrands.KEEP_PACK, k -> 0).contains(Inventory.MAIN_FIRST));
        assertEquals(List.of(), SupplyTrips.slots(false, false, true, false, pack, Optional.empty(), SoldierErrands.KEEP_PACK, k -> 0));
    }
}
