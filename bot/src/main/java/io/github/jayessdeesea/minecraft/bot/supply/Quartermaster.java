package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What a soldier at the supply point takes and how it puts it on - pure decisions over item IDs,
 * so they're tested without a server; {@link SupplyRun} carries them out. docs/military-doctrine.md
 * §4 (Class II and Class V).
 *
 * <p>Takes only what fills a gap: the best melee weapon if it has none in reach, and one piece of
 * armour for each empty armour slot. Nothing else - a supply point is shared, and a soldier that
 * empties it leaves the next one bare.
 */
public final class Quartermaster {

    private Quartermaster() {
    }

    /** One step of putting the gear on, in order. */
    public sealed interface Step {
        /** Shift-click inventory slot {@code slot} (container 0): armour goes onto the body. */
        record Wear(int slot) implements Step {
        }

        /** Hold what's in hotbar slot {@code hotbarSlot}. */
        record Hold(int hotbarSlot) implements Step {
        }

        /** Swap inventory slot {@code slot} into hotbar slot {@code hotbarSlot}, then hold it. */
        record BringToHand(int slot, int hotbarSlot) implements Step {
        }
    }

    /**
     * The chest slots to take, given the chest's items ({@code chest.get(i)} is the item ID in
     * slot {@code i}, or -1) and what the soldier already has.
     */
    public static List<Integer> take(List<Integer> chest, Inventory inventory) {
        List<Integer> take = new ArrayList<>();
        if (bestWeaponSlot(inventory).isEmpty()) {
            int best = -1;
            int bestRank = -1;
            for (int slot = 0; slot < chest.size(); slot++) {
                OptionalInt rank = ItemClassRegistry.meleeRank(chest.get(slot));
                if (rank.isPresent() && rank.getAsInt() > bestRank) {
                    best = slot;
                    bestRank = rank.getAsInt();
                }
            }
            if (best >= 0) {
                take.add(best);
            }
        }
        Set<Integer> covered = new HashSet<>();
        for (int slot = Inventory.ARMOUR_FIRST; slot <= Inventory.ARMOUR_LAST; slot++) {
            if (inventory.itemAt(slot) >= 0) {
                covered.add(slot);
            }
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            ItemClassRegistry.armourSlot(inventory.itemAt(slot)).ifPresent(covered::add); // carried, not worn yet
        }
        for (int slot = 0; slot < chest.size(); slot++) {
            OptionalInt fits = ItemClassRegistry.armourSlot(chest.get(slot));
            if (fits.isPresent() && covered.add(fits.getAsInt())) {
                take.add(slot);
            }
        }
        return take;
    }

    /** Wear every carried piece that fills an empty armour slot, then hold the best melee weapon. */
    public static List<Step> equip(Inventory inventory) {
        List<Step> steps = new ArrayList<>();
        Set<Integer> filled = new HashSet<>();
        for (int slot = Inventory.ARMOUR_FIRST; slot <= Inventory.ARMOUR_LAST; slot++) {
            if (inventory.itemAt(slot) >= 0) {
                filled.add(slot);
            }
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            OptionalInt fits = ItemClassRegistry.armourSlot(inventory.itemAt(slot));
            if (fits.isPresent() && filled.add(fits.getAsInt())) {
                steps.add(new Step.Wear(slot));
            }
        }
        OptionalInt weapon = bestWeaponSlot(inventory);
        if (weapon.isPresent()) {
            int slot = weapon.getAsInt();
            if (slot >= Inventory.HOTBAR_FIRST) {
                if (slot - Inventory.HOTBAR_FIRST != inventory.heldHotbarSlot()) {
                    steps.add(new Step.Hold(slot - Inventory.HOTBAR_FIRST));
                }
            } else {
                steps.add(new Step.BringToHand(slot, inventory.heldHotbarSlot()));
            }
        }
        return steps;
    }

    private static OptionalInt bestWeaponSlot(Inventory inventory) {
        int best = -1;
        int bestRank = -1;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            OptionalInt rank = ItemClassRegistry.meleeRank(inventory.itemAt(slot));
            if (rank.isPresent() && rank.getAsInt() > bestRank) {
                best = slot;
                bestRank = rank.getAsInt();
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }
}
