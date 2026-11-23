package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The hotbar laid out as a player keeps it (operator, 2026-11-15, 2026-11-22): the tools of the trade a switch away,
 * never fetched from the pack while the work waits. The sword first, always - an attack comes at any time; the pickaxe
 * second, the shovel third, the axe fourth; a stack of blocks sixth - dirt before cobblestone - to stop lava or water
 * with at once; food second to last, torches last. The bow fifth (operator, 2026-11-22: the bow in the hotbar), a
 * quick tool there with none carried. The seventh slot is for a hoe or a bucket and what a
 * routine swaps in for the moment; anything else between - emeralds, ore, a spare sword - goes to the main pack.
 * Pure: the inventory in, the next swap out, one at a time.
 */
public final class Hotbar {

    /** The hotbar's fixed places, 0-8. */
    public static final int WEAPON = 0, PICKAXE = 1, SHOVEL = 2, AXE = 3, BOW = 4, BLOCKS = 5, FOOD = 7, TORCHES = 8;

    /** What may stand in the slots between: the quick tools. */
    static final Set<ItemClass> QUICK = EnumSet.of(ItemClass.SHOVEL, ItemClass.HOE, ItemClass.BUCKET, ItemClass.WATER_BUCKET,
            ItemClass.DIRT, ItemClass.COBBLESTONE);

    private Hotbar() {
    }

    /** One swap, as the window's: inventory {@code slot} with hotbar {@code hotbar} (0-8). */
    public record Swap(int slot, int hotbar) {
    }

    /**
     * The next swap toward the layout, or empty when it's kept: the best weapon to the first slot, the best pickaxe to
     * the second, the fastest shovel to the third, the fastest axe - but the one that's the weapon - to the fourth, the
     * biggest stack of dirt, or with none of cobblestone, to the sixth, the biggest stack of food to the second to last,
     * of torches to the last; then anything in the slots between that isn't a quick tool or a block out to an empty slot
     * of the main pack.
     */
    public static Optional<Swap> next(Inventory inventory) {
        Optional<Swap> place = placed(inventory, WEAPON, slot -> ItemClassRegistry.meleeRank(inventory.itemAt(slot)).orElse(-1))
                .or(() -> placed(inventory, PICKAXE, slot -> ItemClassRegistry.miningTier(inventory.itemAt(slot)).orElse(-1)))
                .or(() -> placed(inventory, SHOVEL, slot -> speed(ItemClassRegistry.shovelSpeed(inventory.itemAt(slot)))))
                // Never the axe in the weapon's slot: taken for the axe's, the weapon's slot would take it back, for ever.
                .or(() -> placed(inventory, AXE, slot -> slot == Inventory.HOTBAR_FIRST + WEAPON ? -1 : speed(ItemClassRegistry.axeSpeed(inventory.itemAt(slot)))))
                .or(() -> placed(inventory, BOW, slot -> bow(inventory.itemAt(slot)) ? 1 : -1))
                .or(() -> placed(inventory, BLOCKS, slot -> is(inventory, slot, ItemClass.DIRT) ? 1000 + inventory.countAt(slot)
                        : is(inventory, slot, ItemClass.COBBLESTONE) ? inventory.countAt(slot) : -1))
                .or(() -> placed(inventory, FOOD, slot -> is(inventory, slot, ItemClass.FOOD) ? inventory.countAt(slot) : -1))
                .or(() -> placed(inventory, TORCHES, slot -> is(inventory, slot, ItemClass.TORCH) ? inventory.countAt(slot) : -1));
        if (place.isPresent()) {
            return place;
        }
        for (int hotbar = PICKAXE + 1; hotbar < FOOD; hotbar++) {
            int slot = Inventory.HOTBAR_FIRST + hotbar;
            int item = inventory.itemAt(slot);
            if (item < 0 || quick(item) || hotbar == BOW && bow(item)) {
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

    /** A tool's speed as a rank, or -1 for none. */
    private static int speed(java.util.OptionalDouble speed) {
        return speed.isPresent() ? (int) Math.round(speed.getAsDouble() * 10) : -1;
    }

    private static boolean is(Inventory inventory, int slot, ItemClass itemClass) {
        return ItemClassRegistry.classOf(inventory.itemAt(slot)).filter(itemClass::equals).isPresent();
    }

    private static boolean bow(int item) {
        return ItemClassRegistry.nameOf(item).filter("bow"::equals).isPresent();
    }

    /** Whether {@code item} may stand in the slots between: a shovel, axe, hoe or bucket, or a block of dirt or cobblestone. */
    static boolean quick(int item) {
        return ItemClassRegistry.classOf(item).filter(QUICK::contains).isPresent() || ItemClassRegistry.axeSpeed(item).isPresent();
    }
}
