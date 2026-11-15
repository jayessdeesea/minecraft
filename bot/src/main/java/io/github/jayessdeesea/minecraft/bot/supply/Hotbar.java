package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The hotbar laid out as a player keeps it (operator, 2026-11-15): the sword first, the pickaxe
 * second, food second to last, torches last - only what's switched to in a hurry. The slots
 * between are for the other quick tools - shovel, axe, hoe, bucket - and what a routine swaps in
 * for the moment, building blocks while it builds; anything else there - emeralds, ore, a spare
 * sword - goes to the main pack. Pure: the inventory in, the next swap out, one at a time.
 */
public final class Hotbar {

    /** The hotbar's fixed places, 0-8. */
    public static final int WEAPON = 0, PICKAXE = 1, FOOD = 7, TORCHES = 8;

    /** What may stand in the slots between: the quick tools. */
    static final Set<ItemClass> QUICK = EnumSet.of(ItemClass.SHOVEL, ItemClass.HOE, ItemClass.BUCKET, ItemClass.WATER_BUCKET);

    private Hotbar() {
    }

    /** One swap, as the window's: inventory {@code slot} with hotbar {@code hotbar} (0-8). */
    public record Swap(int slot, int hotbar) {
    }

    /**
     * The next swap toward the layout, or empty when it's kept: the best weapon to the first slot,
     * the best pickaxe to the second, the biggest stack of food to the second to last, of torches to
     * the last; then anything in the slots between that isn't a quick tool out to an empty slot of
     * the main pack.
     */
    public static Optional<Swap> next(Inventory inventory) {
        Optional<Swap> place = placed(inventory, WEAPON, slot -> ItemClassRegistry.meleeRank(inventory.itemAt(slot)).orElse(-1))
                .or(() -> placed(inventory, PICKAXE, slot -> ItemClassRegistry.miningTier(inventory.itemAt(slot)).orElse(-1)))
                .or(() -> placed(inventory, FOOD, slot -> is(inventory, slot, ItemClass.FOOD) ? inventory.countAt(slot) : -1))
                .or(() -> placed(inventory, TORCHES, slot -> is(inventory, slot, ItemClass.TORCH) ? inventory.countAt(slot) : -1));
        if (place.isPresent()) {
            return place;
        }
        for (int hotbar = PICKAXE + 1; hotbar < FOOD; hotbar++) {
            int slot = Inventory.HOTBAR_FIRST + hotbar;
            int item = inventory.itemAt(slot);
            if (item < 0 || quick(item)) {
                continue;
            }
            for (int main = Inventory.MAIN_FIRST; main < Inventory.HOTBAR_FIRST; main++) {
                if (inventory.itemAt(main) < 0) {
                    return Optional.of(new Swap(main, hotbar));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The swap that puts the best of what {@code rank} ranks (-1: not one) into {@code hotbar}, or
     * empty when it's there already or there's none carried.
     */
    private static Optional<Swap> placed(Inventory inventory, int hotbar, ToIntFunction<Integer> rank) {
        int best = -1, bestRank = -1;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int r = rank.applyAsInt(slot);
            if (r > bestRank) {
                best = slot;
                bestRank = r;
            }
        }
        int there = Inventory.HOTBAR_FIRST + hotbar;
        if (best < 0 || rank.applyAsInt(there) >= bestRank) {
            return Optional.empty();
        }
        return Optional.of(new Swap(best, hotbar));
    }

    private static boolean is(Inventory inventory, int slot, ItemClass itemClass) {
        return ItemClassRegistry.classOf(inventory.itemAt(slot)).filter(itemClass::equals).isPresent();
    }

    /** Whether {@code item} is a quick tool for the slots between: a shovel, axe, hoe or bucket. */
    static boolean quick(int item) {
        return ItemClassRegistry.classOf(item).filter(QUICK::contains).isPresent() || ItemClassRegistry.axeSpeed(item).isPresent();
    }
}
