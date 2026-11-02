package io.github.jayessdeesea.minecraft.protocol;

import java.util.List;
import java.util.Optional;

/**
 * A container window other than the player's own inventory (container 0, see
 * {@link InventoryUpdate}) - a chest the player opened. A single chest's slots are 0-26 for the
 * chest, then 27-53 the player's main inventory and 54-62 its hotbar.
 */
public sealed interface ContainerUpdate {

    /** The server opened container {@code containerId}, of registry menu type {@code menuType}. */
    record Opened(int containerId, int menuType) implements ContainerUpdate {
    }

    /** Every slot of the container, as {@link InventoryUpdate.Contents}; {@code stateId} goes back with clicks. */
    record Contents(int containerId, int stateId, List<Optional<ItemSlot>> slots, boolean complete) implements ContainerUpdate {
    }

    /** One slot of the container changed. */
    record Slot(int containerId, int stateId, int slot, Optional<ItemSlot> item) implements ContainerUpdate {
    }

    /** The server closed the container. */
    record Closed(int containerId) implements ContainerUpdate {
    }
}
