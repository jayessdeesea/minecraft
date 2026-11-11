package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.craft.Crafter.Outcome;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crafter against a server that answers as the real one does (FakeCraftingServer). */
class CrafterTest {

    private static final Position SOLDIER = new Position(0.5, 64, 0.5);

    private final FakeCraftingServer server = new FakeCraftingServer();
    private final Crafter crafter = new Crafter("Dana", server.session, server.inventory, new HashMap<>());
    private int tick;

    CrafterTest() {
        server.client = crafter::onContainer;
        server.give(Inventory.HOTBAR_FIRST, ItemClass.WEAPON, 1);
    }

    /** A workshop's table and furnace, standing within reach of the soldier. */
    private void workshop() {
        server.stations.put(new BlockPos(2, 64, 0), Workstation.CRAFTING_TABLE);
        server.stations.put(new BlockPos(-2, 64, 0), Workstation.FURNACE);
    }

    @Test
    void twentyTorchesAtAWorkshopTakeOneFurnaceLoadAndOneRoundOfTorches() {
        workshop();
        server.give(9, ItemClass.LOG, 7);

        Outcome outcome = run(Map.of(Recipe.TORCHES, 20), t -> false, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(20, server.count(ItemClass.TORCH));
        assertEquals(0, server.logged("opened CRAFTING_TABLE"), "planks, sticks and torches are all made in the grid");
        assertEquals(1, server.logged("opened FURNACE"));
        assertEquals(5, server.logged("crafted torches"), "five crafts, from one shift-click");
        assertEquals(0, server.logged("placed"), "nothing put down");
        assertTrue(tick > 5 * 200, "five smelts, 200 ticks each - nothing moved on early");
        assertEquals(0, server.inventory.heldHotbarSlot(), "its weapon back in hand");
    }

    @Test
    void aFurnaceLeftLoadedAndColdIsBurnedFreeForTheTorches() {
        workshop();
        server.leftSmelting(new BlockPos(-2, 64, 0), ItemClass.LOG, 2); // a squadmate's, its fuel spent
        server.give(9, ItemClass.LOG, 4);

        Outcome outcome = run(Map.of(Recipe.TORCHES, 8), t -> false, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertTrue(server.count(ItemClass.TORCH) >= 8, server.count(ItemClass.TORCH) + " torches");
    }

    @Test
    void aSquadmatesCopperLeftInTheFurnaceComesOutOnTheWayToTheTorches() {
        workshop();
        server.leftSmelting(new BlockPos(-2, 64, 0), ItemClass.RAW_COPPER, 1); // as live: someone's ore, cold
        server.give(9, ItemClass.LOG, 4); // no planks to spare: the copper comes out raw

        Outcome outcome = run(Map.of(Recipe.TORCHES, 8), t -> false, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertTrue(server.count(ItemClass.TORCH) >= 8, server.count(ItemClass.TORCH) + " torches");
        assertEquals(1, server.count(ItemClass.RAW_COPPER), "the copper taken out, not left in the way");
    }

    @Test
    void aSwordAndAPickaxeAtOneOpeningOfTheTable() {
        workshop();
        server.give(Inventory.HOTBAR_FIRST, ItemClass.LOG, 5); // bare-handed: the weapon slot holds logs

        Outcome outcome = run(Map.of(Recipe.WOODEN_SWORD, 1, Recipe.WOODEN_PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(1, server.count(ItemClass.WEAPON));
        assertEquals(1, server.count(ItemClass.PICKAXE));
        assertEquals(1, server.logged("opened CRAFTING_TABLE"));
    }

    @Test
    void aTableAlreadyStandingInReachIsUsed() {
        server.stations.put(new BlockPos(2, 64, 0), Workstation.CRAFTING_TABLE);
        server.give(9, ItemClass.LOG, 2);

        Outcome outcome = run(Map.of(Recipe.WOODEN_PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(0, server.logged("placed CRAFTING_TABLE"));
        assertEquals(1, server.logged("opened CRAFTING_TABLE"));
    }

    @Test
    void aFurnaceASquadmateHoldsIsLeftAloneAndItsOwnIsFreedOnceEmptied() {
        Map<BlockPos, String> claims = new HashMap<>();
        BlockPos theirs = new BlockPos(2, 64, 0);
        claims.put(theirs, "Eli");
        server.stations.put(theirs, Workstation.FURNACE);
        server.stations.put(new BlockPos(-2, 64, 0), Workstation.FURNACE);
        Crafter dana = new Crafter("Dana", server.session, server.inventory, claims);
        server.client = dana::onContainer;
        server.give(9, ItemClass.LOG, 4);

        Outcome outcome = Outcome.IDLE;
        for (tick = 0; tick < 3000 && outcome != Outcome.MADE && outcome != Outcome.FAILED; tick++) {
            server.tick();
            outcome = dana.tick(SOLDIER, server.world, Map.of(Recipe.TORCHES, 4), false);
        }

        assertEquals(Outcome.MADE, outcome, dana.failure().orElse(""));
        assertEquals(1, server.logged("opened FURNACE"), "the free one, not the squadmate's");
        assertEquals(Map.of(theirs, "Eli"), claims, "the squadmate's hold stands; its own, emptied, is let go");
    }

    @Test
    void withNoTableInReachItMakesNothingAndPutsNoTableDown() {
        server.give(9, ItemClass.LOG, 3);

        Outcome outcome = run(Map.of(Recipe.WOODEN_PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.IDLE, outcome);
        assertEquals(0, server.logged("placed"));
        assertEquals(Set.of(Workstation.CRAFTING_TABLE),
                crafter.plan(Map.of(Recipe.WOODEN_PICKAXE, 1), server.world, SOLDIER).missing(), "the workshop's to provide");
    }

    @Test
    void aHostileNearInterruptsWithoutBackoffAndTheNextRunPicksUpWhereItStopped() {
        workshop();
        server.give(9, ItemClass.LOG, 4);
        int[] threatAt = {-1};

        // A hostile turns up mid-smelt and goes away 100 ticks later.
        Outcome outcome = run(Map.of(Recipe.TORCHES, 8), t -> {
            if (threatAt[0] < 0 && server.logged("opened FURNACE") == 1 && server.count(ItemClass.COAL) == 0) {
                threatAt[0] = t + 50;
            }
            return threatAt[0] >= 0 && t >= threatAt[0] && t < threatAt[0] + 100;
        }, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(8, server.count(ItemClass.TORCH));
        assertEquals(2, server.logged("opened FURNACE"));
        assertEquals(Optional.empty(), crafter.failure(), "an interruption isn't a failure");
    }

    @Test
    void aSoldierWithNoWeaponFinishesItsSwordAtTheOpenTableBeforeTurningToFight() {
        workshop();
        server.give(Inventory.HOTBAR_FIRST, ItemClass.LOG, 2);

        Outcome outcome = run(Map.of(Recipe.WOODEN_SWORD, 1), t -> server.logged("opened CRAFTING_TABLE") > 0, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(1, server.count(ItemClass.WEAPON));
    }

    @Test
    void anArmedSoldierLeavesTheTableToFight() {
        workshop();
        server.give(9, ItemClass.LOG, 3);

        Outcome outcome = run(Map.of(Recipe.WOODEN_PICKAXE, 1), t -> server.logged("opened CRAFTING_TABLE") > 0, 400);

        assertEquals(Outcome.INTERRUPTED, outcome);
        assertEquals(0, server.count(ItemClass.PICKAXE));
    }

    @Test
    void aFurnaceThatNeverOpensFailsLoudlySayingWhatItWaitedFor() {
        workshop();
        server.give(9, ItemClass.LOG, 4);
        server.silent.add(Workstation.FURNACE);

        Outcome outcome = run(Map.of(Recipe.TORCHES, 4), t -> false, 400);

        assertEquals(Outcome.FAILED, outcome);
        assertEquals("can't make 4 torches: open the furnace: expected the furnace's window within " + Crafter.ANSWER_TICKS
                + " ticks; it didn't come", crafter.failure().orElseThrow());
    }

    /** Ticks until a run ends - made or failed, or interrupted when nothing restarts it. */
    private Outcome run(Map<Recipe, Integer> wanted, IntPredicate threatened, int ticks) {
        Outcome last = Outcome.IDLE;
        for (tick = 0; tick < ticks; tick++) {
            server.tick();
            Outcome outcome = crafter.tick(SOLDIER, server.world, wanted, threatened.test(tick));
            if (outcome == Outcome.MADE || outcome == Outcome.FAILED) {
                return outcome;
            }
            if (outcome != Outcome.IDLE) {
                last = outcome;
            }
        }
        return last;
    }
}
