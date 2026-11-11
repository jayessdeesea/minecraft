package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A chest's window as one soldier sees it: the chest's slots, then the soldier's own inventory.
 * While it's open the inventory's changes arrive only here, so they're applied to {@link Inventory}
 * as they come. Shared by {@link SupplyRun} and {@link Depositor}; not thread-safe - its owner
 * synchronizes, since updates arrive on the connection's thread.
 */
final class ChestWindow {

    /** Close enough to use a chest: vanilla's reach is about 4.5 blocks from the eyes. */
    static final double REACH = 3.5;
    /** For the server to answer: the window opened, a click applied. */
    static final int ANSWER_TICKS = 40;
    /** Menu registry IDs 0-5 are generic_9x1 to generic_9x6: chests, barrels, double chests. */
    private static final int GENERIC_9X6 = 5;

    private final PlaySession session;
    private final Inventory inventory;

    private boolean opening;
    private int id = -1;
    private int size;
    private int stateId;
    private int[] items = new int[0];
    private int[] counts = new int[0];
    private boolean contentsSeen;
    private String gone;

    ChestWindow(PlaySession session, Inventory inventory) {
        this.session = session;
        this.inventory = inventory;
    }

    /** Asks the server to open the chest at {@code chest}; {@link #isOpen} says when it has. */
    void open(Position chest) {
        id = -1;
        contentsSeen = false;
        gone = null;
        opening = true;
        session.useBlock((int) Math.floor(chest.x()), (int) Math.floor(chest.y()), (int) Math.floor(chest.z()));
    }

    /**
     * Whether the chest is open with its contents in.
     *
     * @throws IllegalStateException if something else opened, or the server closed it
     */
    boolean isOpen() {
        if (gone != null) {
            throw new IllegalStateException(gone);
        }
        return id >= 0 && contentsSeen;
    }

    /** Closes the chest, if it's open. */
    void close() {
        if (id >= 0) {
            session.closeContainer(id);
        }
        id = -1;
        opening = false;
    }

    /** Shift-clicks chest slot or window slot {@code slot} across. */
    void quickMove(int slot) {
        session.quickMove(id, stateId, slot);
    }

    /** The window slot showing inventory slot {@code inventorySlot}. */
    int windowSlotOf(int inventorySlot) {
        return inventorySlot - Inventory.MAIN_FIRST + size;
    }

    /** The chest's own slots' item IDs, -1 for empty. */
    List<Integer> chestItems() {
        List<Integer> chest = new ArrayList<>(size);
        for (int item : items) {
            chest.add(item);
        }
        return chest;
    }

    /** How many items the chest's slot {@code slot} holds. */
    int countAt(int slot) {
        return counts[slot];
    }

    /** How many of {@code itemClass} the chest holds. */
    int chestCount(ItemClass itemClass) {
        int total = 0;
        for (int slot = 0; slot < size; slot++) {
            if (ItemClassRegistry.classOf(items[slot]).equals(Optional.of(itemClass))) {
                total += counts[slot];
            }
        }
        return total;
    }

    /** Whether the update was this window's - and, if so, applies it. */
    boolean onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int opened, int menuType) -> {
                if (!opening) {
                    return false;
                }
                opening = false;
                if (menuType < 0 || menuType > GENERIC_9X6) {
                    session.closeContainer(opened);
                    gone = "menu type " + menuType + " opened, not a chest";
                    return true;
                }
                id = opened;
                size = (menuType + 1) * 9;
                items = new int[size];
                counts = new int[size];
                Arrays.fill(items, -1);
                return true;
            }
            case ContainerUpdate.Contents(int window, int state, var slots, boolean _) -> {
                if (window != id || id < 0) {
                    return false;
                }
                stateId = state;
                for (int slot = 0; slot < slots.size(); slot++) {
                    apply(slot, slots.get(slot));
                }
                contentsSeen = true;
                return true;
            }
            case ContainerUpdate.Slot(int window, int state, int slot, var item) -> {
                if (window != id || id < 0) {
                    return false;
                }
                stateId = state;
                apply(slot, item);
                return true;
            }
            case ContainerUpdate.Closed(int window) -> {
                if (window != id || id < 0) {
                    return false;
                }
                id = -1;
                gone = "the server closed the chest";
                return true;
            }
        }
    }

    private void apply(int slot, Optional<ItemSlot> item) {
        if (slot < size) {
            items[slot] = item.map(ItemSlot::itemId).orElse(-1);
            counts[slot] = item.map(ItemSlot::count).orElse(0);
        } else {
            inventory.setWindowSlot(slot, size, item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null));
        }
    }

    static boolean withinReach(Position soldier, Position chest) {
        double dx = soldier.x() - (Math.floor(chest.x()) + 0.5);
        double dy = soldier.y() - Math.floor(chest.y());
        double dz = soldier.z() - (Math.floor(chest.z()) + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= REACH;
    }
}
