package io.github.jayessdeesea.minecraft.protocol;

import java.util.List;
import java.util.Optional;

/**
 * A change to this player's own inventory window (container 0): its slots are numbered the
 * vanilla way - 0 crafting result, 1-4 crafting grid, 5-8 armour (head to feet), 9-35 main, 36-44
 * hotbar, 45 off hand. Other containers are never opened by a soldier and aren't reported.
 */
public sealed interface InventoryUpdate {

    /**
     * Every slot at once. If a stack carried a component this client can't decode, the slots
     * after it couldn't be read: {@code slots} then stops there and {@code complete} is false.
     */
    record Contents(List<Optional<ItemSlot>> slots, boolean complete) implements InventoryUpdate {
    }

    /** One slot changed. */
    record Slot(int slot, Optional<ItemSlot> item) implements InventoryUpdate {
    }

    /** The selected hotbar slot, 0-8. */
    record HeldSlot(int hotbarSlot) implements InventoryUpdate {
    }
}
