package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadPlanTest {

    private static List<Integer> ids(java.util.function.IntPredicate is) {
        return IntStream.range(0, 4096).filter(is).boxed().toList();
    }

    /**
     * Flattening (operator, 2026-11-15): two swords kept - the best two - and a third to the depot;
     * two pickaxes and the worst of three put away; an emerald, no part of any load, put away; the
     * cobblestone it's hauling isn't this plan's.
     */
    @Test
    void flatteningKeepsTwoOfEachAndPutsTheRestAndTheEmeraldAway() {
        List<Integer> swords = ids(id -> ItemClassRegistry.meleeRank(id).isPresent() && ItemClassRegistry.axeSpeed(id).isEmpty()).stream()
                .sorted(Comparator.comparingInt(id -> ItemClassRegistry.meleeRank(id).getAsInt())).toList();
        List<Integer> pickaxes = ids(id -> ItemClassRegistry.miningTier(id).isPresent()).stream()
                .sorted(Comparator.comparingInt(id -> ItemClassRegistry.miningTier(id).getAsInt())).toList();
        int emerald = ids(id -> id > 0 && ItemClassRegistry.classOf(id).isEmpty() && ItemClassRegistry.meleeRank(id).isEmpty()
                && ItemClassRegistry.axeSpeed(id).isEmpty()).getFirst();
        int cobblestone = ids(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.COBBLESTONE))).getFirst();
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{swords.getFirst(), 1});      // the worst sword
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{swords.getLast(), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{swords.get(swords.size() / 2), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 3, new int[]{pickaxes.getFirst(), 1}); // the worst pickaxe
        inventory.setSlot(Inventory.MAIN_FIRST + 4, new int[]{pickaxes.getLast(), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 5, new int[]{pickaxes.get(pickaxes.size() / 2), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 6, new int[]{emerald, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 7, new int[]{cobblestone, 64});

        List<Integer> surplus = LoadPlan.FLATTENING.surplus(inventory);

        assertEquals(List.of(Inventory.MAIN_FIRST, Inventory.MAIN_FIRST + 2, Inventory.MAIN_FIRST + 3, Inventory.MAIN_FIRST + 6), surplus,
                "all but the best sword - one, on the surface by the base - the worst pickaxe and the emerald");
    }

    /** Minerals and diamonds go to the depot: the stockpile's, not a working load's (operator, 2026-11-15). */
    @Test
    void mineralsGoToTheDepot() {
        int mineral = ids(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.MINERAL))).getFirst();
        int diamond = ids(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.DIAMOND))).getFirst();
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{mineral, 5});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{diamond, 1});

        assertEquals(List.of(Inventory.MAIN_FIRST, Inventory.MAIN_FIRST + 1), LoadPlan.FLATTENING.surplus(inventory));
    }

    /** Two swords and two axes are two weapons and two axes, not four weapons of which two go. */
    @Test
    void axesAreCountedAsAxesNotAgainstTheSwords() {
        int sword = ids(id -> ItemClassRegistry.meleeRank(id).isPresent() && ItemClassRegistry.axeSpeed(id).isEmpty()).getFirst();
        int axe = ids(id -> ItemClassRegistry.axeSpeed(id).isPresent()).getFirst();
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{sword, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{sword, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{axe, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 3, new int[]{axe, 1});

        assertEquals(List.of(), new LoadPlan(2, 2, 2, 2, 64).surplus(inventory), "all four kept");
    }

    /** Mining keeps a third pickaxe, and no axe; torches past a stack go to the depot. */
    @Test
    void miningKeepsThreePickaxesAndNoAxeAndAStackOfTorches() {
        int pickaxe = ids(id -> ItemClassRegistry.miningTier(id).isPresent()).getFirst();
        int axe = ids(id -> ItemClassRegistry.axeSpeed(id).isPresent()).getFirst();
        int torch = ids(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.TORCH))).getFirst();
        Inventory inventory = new Inventory();
        for (int i = 0; i < 3; i++) {
            inventory.setSlot(Inventory.MAIN_FIRST + i, new int[]{pickaxe, 1});
        }
        inventory.setSlot(Inventory.MAIN_FIRST + 3, new int[]{axe, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 4, new int[]{torch, 64});
        inventory.setSlot(Inventory.MAIN_FIRST + 5, new int[]{torch, 20});

        List<Integer> surplus = LoadPlan.MINING.surplus(inventory);

        assertTrue(surplus.contains(Inventory.MAIN_FIRST + 3), "the axe: no part of a miner's load");
        assertTrue(surplus.contains(Inventory.MAIN_FIRST + 5), "the 20 torches past the stack");
        assertTrue(IntStream.range(0, 3).noneMatch(i -> surplus.contains(Inventory.MAIN_FIRST + i)), "three pickaxes kept");
        assertTrue(!surplus.contains(Inventory.MAIN_FIRST + 4), "the stack of torches kept");
    }
}
