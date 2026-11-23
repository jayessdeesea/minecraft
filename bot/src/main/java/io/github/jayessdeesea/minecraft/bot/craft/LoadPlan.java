package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
    /**
     * Surface work near the base - flattening, cutting, farming - is the safest there is, so its load is
     * lean for room to work in (operator, 2026-11-17): always a sword, but one; armour worn, never spare;
     * a stack of torches; a spare of each digging tool, which wears out mid-dig.
     */
    public static final LoadPlan FLATTENING = new LoadPlan(1, 2, 2, 2, 64);
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
        // Weapons are swords and spears: an axe, though it ranks for melee, is counted as an axe. Each
        // slot read once: the inventory is the connection's to change between two reads - live, a
        // slot read as a weapon, then as nothing, and the tick failed on its missing rank.
        past(inventory, weapons, slot -> {
            int item = inventory.itemAt(slot);
            OptionalInt rank = ItemClassRegistry.meleeRank(item);
            return rank.isPresent() && ItemClassRegistry.axeSpeed(item).isEmpty() ? rank.getAsInt() : Double.NaN;
        }, out);
        past(inventory, pickaxes, slot -> {
            OptionalInt tier = ItemClassRegistry.miningTier(inventory.itemAt(slot));
            return tier.isPresent() ? tier.getAsInt() : Double.NaN;
        }, out);
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
            // And armour carried, not worn - the set it wears is in its armour slots: live (field374), Bob
            // carried 5 spare pieces among 9, his pack near full, and walked to the depot every 70 s with
            // them, each trip putting in nothing of them.
            if (ItemClassRegistry.classOf(inventory.itemAt(slot)).filter(k -> k == ItemClass.ARMOUR).isPresent()) {
                out.add(slot);
            }
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int item = inventory.itemAt(slot);
            // And minerals - redstone, lapis, emerald, gold, diamonds: the stockpile's, worth carrying
            // back over any dirt (operator, 2026-11-15), no part of a working load.
            Optional<ItemClass> kind = item >= 0 ? ItemClassRegistry.classOf(item) : Optional.empty();
            if (item >= 0 && (kind.filter(k -> k == ItemClass.MINERAL || k == ItemClass.DIAMOND).isPresent()
                    || kind.isEmpty() && ItemClassRegistry.meleeRank(item).isEmpty() && ItemClassRegistry.axeSpeed(item).isEmpty())) {
                out.add(slot);
            }
        }
        List<Integer> sorted = new ArrayList<>(out);
        sorted.sort(Comparator.naturalOrder());
        return List.copyOf(sorted);
    }

    /**
     * Whether what {@code recipe} makes would be put away as soon as made: a melee weapon no better than the best carried,
     * with this load's weapons carried already. The kit ladder's spare sword is such a thing on a load of one: live
     * (field692, field693), surface workers put the spare in the chest, dug stone and made it again, trip after trip. A
     * better weapon is made - it's the worse one that's put away.
     */
    public boolean putsAway(Recipe recipe, Inventory inventory) {
        // A sword: a bow is a weapon too, and no melee one - it has no rank to ask for.
        return recipe.makes() == ItemClass.WEAPON && inventory.meleeWeapons() >= weapons
                && recipe.item().filter(item -> item.endsWith("_sword")).map(ItemClassRegistry::meleeRank).orElse(Integer.MAX_VALUE) <= Ladder.bestMelee(inventory);
    }

    /** The slots of a kind - where {@code rank} is a number - past the best {@code keep} of them. */
    private static void past(Inventory inventory, int keep, ToDoubleFunction<Integer> rank, Set<Integer> out) {
        // Each slot ranked once, then sorted on that: ranked again in the sort, a slot changed by the
        // connection mid-sort would break the comparison's contract.
        java.util.Map<Integer, Double> ranked = new java.util.HashMap<>();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            double r = inventory.itemAt(slot) >= 0 ? rank.applyAsDouble(slot) : Double.NaN;
            if (!Double.isNaN(r)) {
                ranked.put(slot, r);
            }
        }
        List<Integer> kind = new ArrayList<>(ranked.keySet());
        kind.sort(Comparator.comparingDouble((Integer s) -> ranked.get(s)).reversed());
        for (int i = keep; i < kind.size(); i++) {
            out.add(kind.get(i));
        }
    }
}
