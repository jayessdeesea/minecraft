package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * What a soldier at the supply point takes and how it puts on what it has - pure decisions over
 * item IDs, so they're tested without a server; {@link #perform} sends each step.
 * docs/military-doctrine.md §4 (Class II and Class V).
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

        /** Swap the piece in {@code slot} for the worse one worn in {@code armourSlot}, which goes where it was. */
        record Replace(int slot, int armourSlot) implements Step {
        }

        /** Swap {@code slot} into the off hand: a shield goes where it's raised from. */
        record ToOffHand(int slot) implements Step {
        }
    }

    /** The swap button a container click uses for the off hand, as the swap-hands key does. */
    private static final int OFF_HAND_BUTTON = 40;

    /**
     * Sends {@code step} through {@code session}. State ID 0 is almost certainly stale: the server
     * then resends the whole inventory, which confirms what the soldier is wearing and holding.
     */
    public static void perform(Step step, PlaySession session, Inventory inventory) {
        switch (step) {
            case Step.Wear(int slot) -> session.quickMove(0, 0, slot);
            case Step.Hold(int hotbarSlot) -> {
                session.selectHotbarSlot(hotbarSlot);
                inventory.selectHotbar(hotbarSlot);
            }
            case Step.BringToHand(int slot, int hotbarSlot) -> session.swapWithHotbar(0, 0, slot, hotbarSlot);
            case Step.Replace(int slot, int armourSlot) -> {
                session.click(0, 0, slot, false);       // the better piece onto the cursor,
                session.click(0, 0, armourSlot, false); // swapped for the one worn,
                session.click(0, 0, slot, false);       // which goes where the better one was
            }
            case Step.ToOffHand(int slot) -> session.swapWithHotbar(0, 0, slot, OFF_HAND_BUTTON);
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

    /**
     * Wear the best carried piece for each armour slot that's empty or holds a worse one, put a
     * carried shield in the off hand, then hold the best melee weapon.
     */
    public static List<Step> equip(Inventory inventory) {
        List<Step> steps = new ArrayList<>();
        // For each armour slot, the carried piece that beats what's worn there, the best of them.
        Map<Integer, Integer> wear = new TreeMap<>();
        Map<Integer, Integer> grade = new HashMap<>();
        for (int slot = Inventory.ARMOUR_FIRST; slot <= Inventory.ARMOUR_LAST; slot++) {
            int worn = inventory.itemAt(slot);
            grade.put(slot, worn < 0 ? -1 : ItemClassRegistry.armourGrade(worn).orElse(Integer.MAX_VALUE));
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            OptionalInt fits = ItemClassRegistry.armourSlot(inventory.itemAt(slot));
            int better = ItemClassRegistry.armourGrade(inventory.itemAt(slot)).orElse(-1);
            if (fits.isPresent() && better > grade.get(fits.getAsInt())) {
                grade.put(fits.getAsInt(), better);
                wear.put(fits.getAsInt(), slot);
            }
        }
        wear.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e -> steps.add(inventory.itemAt(e.getKey()) < 0
                ? new Step.Wear(e.getValue()) : new Step.Replace(e.getValue(), e.getKey())));
        boolean shieldOn = ItemClassRegistry.classOf(inventory.itemAt(Inventory.OFF_HAND)).equals(Optional.of(ItemClass.SHIELD));
        for (int slot = Inventory.MAIN_FIRST; !shieldOn && slot <= Inventory.HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(ItemClass.SHIELD))) {
                steps.add(new Step.ToOffHand(slot));
                shieldOn = true;
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
