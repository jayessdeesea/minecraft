package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * A soldier's fighting load by its kind of work, and what it carries past it (operator,
 * 2026-11-15; ATP 3-21.18: the fighting load on the soldier, the rest with the trains -
 * backlog/load-plans.md): the best of each kind kept up to its figure, the rest put in the depot
 * on its next trip there, so the pack has room for what the work digs. Pure: an inventory in,
 * the slots to put away out.
 *
 * @param weapons   melee weapons kept: a main and a spare
 * @param pickaxes  pickaxes kept
 * @param shovels   shovels kept
 * @param axes      axes kept
 * @param torches   torches kept
 */
public record LoadPlan(int weapons, int pickaxes, int shovels, int axes, int torches) {

    /** The keep's hands: two of each tool, a stack of torches (food's basic load is Subsistence's). */
    public static final LoadPlan FLATTENING = new LoadPlan(2, 2, 2, 2, 64);
    /** A mine's cutters: a third pickaxe for the stone that wears them fastest, one shovel, no axe. */
    public static final LoadPlan MINING = new LoadPlan(2, 3, 1, 0, 64);

    /**
     * The slots {@code inventory} holds past this load: the worst melee weapons, pickaxes, shovels
     * and axes past their figures - by rank, tier and speed - torches past theirs, a stack at a
     * time, and what's no part of any load: an item of no class (an emerald, a bone). Bows and
     * arrows are kept: the arrow supply waits on a chicken pen (backlog/load-plans.md). What's
     * hauled - cobblestone, logs, dirt, ore - is the depositor's loads, not this.
     */
    public List<Integer> surplus(Inventory inventory) {
        Set<Integer> out = new HashSet<>();
        // Weapons are swords and spears: an axe, though it ranks for melee, is counted as an axe.
        past(inventory, weapons, slot -> ItemClassRegistry.meleeRank(inventory.itemAt(slot)).isPresent()
                && ItemClassRegistry.axeSpeed(inventory.itemAt(slot)).isEmpty()
                ? ItemClassRegistry.meleeRank(inventory.itemAt(slot)).getAsInt() : Double.NaN, out);
        past(inventory, pickaxes, slot -> ItemClassRegistry.miningTier(inventory.itemAt(slot)).isPresent()
                ? ItemClassRegistry.miningTier(inventory.itemAt(slot)).getAsInt() : Double.NaN, out);
        past(inventory, shovels, slot -> ItemClassRegistry.shovelSpeed(inventory.itemAt(slot)).orElse(Double.NaN), out);
        past(inventory, axes, slot -> ItemClassRegistry.axeSpeed(inventory.itemAt(slot)).orElse(Double.NaN), out);
        // Torches past the load, the smallest stacks first, while what's left is still the load.
        int left = inventory.count(ItemClass.TORCH);
        List<Integer> torchSlots = new ArrayList<>();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.classOf(inventory.itemAt(slot)).filter(ItemClass.TORCH::equals).isPresent()) {
                torchSlots.add(slot);
            }
        }
        torchSlots.sort(Comparator.comparingInt(inventory::countAt));
        for (int slot : torchSlots) {
            if (left - inventory.countAt(slot) >= torches) {
                left -= inventory.countAt(slot);
                out.add(slot);
            }
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int item = inventory.itemAt(slot);
            if (item >= 0 && ItemClassRegistry.classOf(item).isEmpty() && ItemClassRegistry.meleeRank(item).isEmpty()
                    && ItemClassRegistry.axeSpeed(item).isEmpty()) {
                out.add(slot);
            }
        }
        List<Integer> sorted = new ArrayList<>(out);
        sorted.sort(Comparator.naturalOrder());
        return List.copyOf(sorted);
    }

    /** The slots of a kind - where {@code rank} is a number - past the best {@code keep} of them. */
    private static void past(Inventory inventory, int keep, ToDoubleFunction<Integer> rank, Set<Integer> out) {
        List<Integer> kind = new ArrayList<>();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (inventory.itemAt(slot) >= 0 && !Double.isNaN(rank.applyAsDouble(slot))) {
                kind.add(slot);
            }
        }
        kind.sort(Comparator.comparingDouble((Integer s) -> rank.applyAsDouble(s)).reversed());
        for (int i = keep; i < kind.size(); i++) {
            out.add(kind.get(i));
        }
    }
}
