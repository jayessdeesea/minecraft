package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.craft.Crafter.Outcome;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crafter against a server that answers as the real one does (FakeCraftingServer). */
class CrafterTest {

    private static final Position SOLDIER = new Position(0.5, 64, 0.5);

    private final FakeCraftingServer server = new FakeCraftingServer();
    private final Crafter crafter = new Crafter("Dana", server.session, server.inventory);
    private int tick;

    CrafterTest() {
        server.client = crafter::onContainer;
        server.give(Inventory.HOTBAR_FIRST, ItemClass.WEAPON, 1);
    }

    @Test
    void twentyTorchesTakeOneTableOneFurnaceLoadAndOneRoundOfTorches() {
        server.give(9, ItemClass.LOG, 8).give(10, ItemClass.COBBLESTONE, 8);

        Outcome outcome = run(Map.of(ItemClass.TORCH, 20), t -> false, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(20, server.count(ItemClass.TORCH));
        assertEquals(1, server.logged("opened CRAFTING_TABLE"));
        assertEquals(1, server.logged("opened FURNACE"));
        assertEquals(5, server.logged("crafted torches"), "five crafts, from one shift-click");
        assertEquals(1, server.logged("placed CRAFTING_TABLE"));
        assertTrue(tick > 5 * 200, "five smelts, 200 ticks each - nothing moved on early");
        assertEquals(0, server.inventory.heldHotbarSlot(), "its weapon back in hand");
    }

    @Test
    void aSwordAndAPickaxeAtOneOpeningOfTheTable() {
        server.give(Inventory.HOTBAR_FIRST, ItemClass.LOG, 5); // bare-handed: the weapon slot holds logs

        Outcome outcome = run(Map.of(ItemClass.WEAPON, 1, ItemClass.PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(1, server.count(ItemClass.WEAPON));
        assertEquals(1, server.count(ItemClass.PICKAXE));
        assertEquals(1, server.logged("opened CRAFTING_TABLE"));
    }

    @Test
    void aTableAlreadyStandingInReachIsUsed() {
        server.stations.put(new BlockPos(2, 64, 0), Workstation.CRAFTING_TABLE);
        server.give(9, ItemClass.LOG, 2);

        Outcome outcome = run(Map.of(ItemClass.PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(0, server.logged("placed CRAFTING_TABLE"));
        assertEquals(1, server.logged("opened CRAFTING_TABLE"));
    }

    @Test
    void aTableIsPutDownClearOfSquadmates() {
        server.bodies.add(new Position(1.5, 64, 0.5)); // in the first cell it would try
        server.give(9, ItemClass.LOG, 3);

        Outcome outcome = run(Map.of(ItemClass.PICKAXE, 1), t -> false, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(java.util.Optional.empty(), java.util.Optional.ofNullable(server.stations.get(new BlockPos(1, 64, 0))));
    }

    @Test
    void aHostileNearInterruptsWithoutBackoffAndTheNextRunPicksUpWhereItStopped() {
        server.give(9, ItemClass.LOG, 4).give(10, ItemClass.COBBLESTONE, 8);
        int[] threatAt = {-1};

        // A hostile turns up mid-smelt and goes away 100 ticks later.
        Outcome outcome = run(Map.of(ItemClass.TORCH, 8), t -> {
            if (threatAt[0] < 0 && server.logged("opened FURNACE") == 1 && server.count(ItemClass.COAL) == 0) {
                threatAt[0] = t + 50;
            }
            return threatAt[0] >= 0 && t >= threatAt[0] && t < threatAt[0] + 100;
        }, 3000);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(8, server.count(ItemClass.TORCH));
        assertEquals(1, server.logged("placed FURNACE"), "the furnace it left is the one it comes back to");
        assertEquals(2, server.logged("opened FURNACE"));
        assertEquals(java.util.Optional.empty(), crafter.failure(), "an interruption isn't a failure");
    }

    @Test
    void aSoldierWithNoWeaponFinishesItsSwordAtTheOpenTableBeforeTurningToFight() {
        server.give(Inventory.HOTBAR_FIRST, ItemClass.LOG, 2);

        Outcome outcome = run(Map.of(ItemClass.WEAPON, 1), t -> server.logged("opened CRAFTING_TABLE") > 0, 400);

        assertEquals(Outcome.MADE, outcome, crafter.failure().orElse(""));
        assertEquals(1, server.count(ItemClass.WEAPON));
    }

    @Test
    void anArmedSoldierLeavesTheTableToFight() {
        server.give(9, ItemClass.LOG, 3);

        Outcome outcome = run(Map.of(ItemClass.PICKAXE, 1), t -> server.logged("opened CRAFTING_TABLE") > 0, 400);

        assertEquals(Outcome.INTERRUPTED, outcome);
        assertEquals(0, server.count(ItemClass.PICKAXE));
    }

    @Test
    void aFurnaceThatNeverOpensFailsLoudlySayingWhatItWaitedFor() {
        server.give(9, ItemClass.LOG, 4).give(10, ItemClass.COBBLESTONE, 8);
        server.silent.add(Workstation.FURNACE);

        Outcome outcome = run(Map.of(ItemClass.TORCH, 4), t -> false, 400);

        assertEquals(Outcome.FAILED, outcome);
        assertEquals("can't make 4 TORCH: open the furnace: expected the furnace's window within " + Crafter.ANSWER_TICKS
                + " ticks; it didn't come", crafter.failure().orElseThrow());
    }

    /** Ticks until a run ends - made or failed, or interrupted when nothing restarts it. */
    private Outcome run(Map<ItemClass, Integer> wanted, IntPredicate threatened, int ticks) {
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
