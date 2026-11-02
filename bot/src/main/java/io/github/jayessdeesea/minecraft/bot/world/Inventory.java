package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Kit;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One soldier's own inventory window, kept current from the server's updates, reduced to the
 * {@link Kit} its LACE report carries. Slots are numbered the vanilla way (5-8 armour, 36-44
 * hotbar). Synchronized: its connection's thread applies the server's updates while the soldier's
 * tick thread reads it to resupply ({@code bot.supply}). {@link #kit} is an immutable value safe to
 * hand to any other thread.
 *
 * <p>Takes plain item IDs rather than {@code protocol} types so {@code bot} keeps no dependency on
 * how the wire describes a stack; the composition root translates.
 */
public final class Inventory {

    public static final int SLOTS = 46;
    public static final int ARMOUR_FIRST = 5;
    public static final int ARMOUR_LAST = 8;
    public static final int MAIN_FIRST = 9;
    public static final int HOTBAR_FIRST = 36;
    public static final int HOTBAR_LAST = 44;

    private final int[] itemIds = new int[SLOTS];
    private final int[] counts = new int[SLOTS];
    private int heldHotbarSlot;

    public Inventory() {
        Arrays.fill(itemIds, -1);
    }

    /** Every slot at once; slots past the end of {@code items} (an incomplete update) are left as they were. */
    public synchronized Kit replaceAll(List<int[]> items) {
        for (int slot = 0; slot < Math.min(items.size(), SLOTS); slot++) {
            set(slot, items.get(slot));
        }
        return kit();
    }

    /** One slot: {@code item} is {itemId, count}, or null for empty. */
    public synchronized Kit setSlot(int slot, int[] item) {
        if (slot >= 0 && slot < SLOTS) {
            set(slot, item);
        }
        return kit();
    }

    /**
     * A slot of an open container's window that shows this inventory: after the container's own
     * {@code containerSize} slots come the main inventory (9-35 here) and then the hotbar (36-44).
     * The server doesn't repeat these changes for this window when the container closes, so they
     * must be applied as they arrive. The container's own slots are ignored.
     */
    public synchronized Kit setWindowSlot(int windowSlot, int containerSize, int[] item) {
        if (windowSlot >= containerSize) {
            setSlot(MAIN_FIRST + windowSlot - containerSize, item);
        }
        return kit();
    }

    /** The item ID in {@code slot}, or -1 if it's empty. */
    public synchronized int itemAt(int slot) {
        return itemIds[slot];
    }

    public synchronized int heldHotbarSlot() {
        return heldHotbarSlot;
    }

    public synchronized Kit selectHotbar(int hotbarSlot) {
        heldHotbarSlot = hotbarSlot;
        return kit();
    }

    private void set(int slot, int[] item) {
        itemIds[slot] = item == null ? -1 : item[0];
        counts[slot] = item == null ? 0 : item[1];
    }

    public synchronized Kit kit() {
        int held = itemIds[HOTBAR_FIRST + heldHotbarSlot];
        Optional<String> weapon = ItemClassRegistry.classOf(held).filter(c -> c == ItemClassRegistry.ItemClass.WEAPON)
                .flatMap(c -> ItemClassRegistry.nameOf(held));
        int arrows = 0;
        int armour = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            Optional<ItemClassRegistry.ItemClass> itemClass = ItemClassRegistry.classOf(itemIds[slot]);
            if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.ARROWS))) {
                arrows += counts[slot];
            } else if (slot >= ARMOUR_FIRST && slot <= ARMOUR_LAST && itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.ARMOUR))) {
                armour++;
            }
        }
        return new Kit(weapon, arrows, armour);
    }
}
