package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.Optional;

/**
 * Placing the block a soldier climbs on (a pillar step, {@code DeterministicSoldier}): cobblestone
 * into a spare hotbar slot, in hand, placed on the top face of the block below the cell. The
 * server takes the swap, the selection and the placement in the order they're sent, so all three
 * go in one tick. Once the soldier stops climbing, the slot it held before - its weapon - is
 * selected again. Driven by the soldier's tick.
 */
public final class Footing {

    /** Ticks without a placement before the weapon goes back in hand. */
    static final int REARM_TICKS = 20;

    private final PlaySession session;
    private final Inventory inventory;
    private int previousHotbar = -1;
    private int idleTicks;

    public Footing(PlaySession session, Inventory inventory) {
        this.session = session;
        this.inventory = inventory;
    }

    /** Places a block in {@code cell}, against the one under it. False when there's no cobblestone to place. */
    public synchronized boolean place(BlockPos cell) {
        return place(cell, ItemClass.COBBLESTONE);
    }

    /**
     * Places an item of {@code item} - cobblestone to climb on, a torch to light a patrol base - in
     * {@code cell}, on top of the block under it. False when there's none to place.
     */
    public synchronized boolean place(BlockPos cell, ItemClass item) {
        idleTicks = 0;
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        if (!ItemClassRegistry.classOf(inventory.itemAt(held)).equals(Optional.of(item))) {
            int slot = inventory.slotOf(item);
            if (slot < 0) {
                return false;
            }
            int hotbar = spareHotbarSlot();
            if (previousHotbar < 0) {
                previousHotbar = inventory.heldHotbarSlot();
            }
            if (slot != Inventory.HOTBAR_FIRST + hotbar) {
                session.swapWithHotbar(0, 0, slot, hotbar);
            }
            session.selectHotbarSlot(hotbar);
            inventory.selectHotbar(hotbar);
        }
        session.useBlockFace(cell.x(), cell.y() - 1, cell.z(), BlockFace.UP);
        return true;
    }

    /** Each tick: the weapon back in hand once the climbing has stopped. */
    public synchronized void tick() {
        if (previousHotbar >= 0 && ++idleTicks >= REARM_TICKS) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
    }

    private int spareHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> item = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (item.isEmpty() || item.get() != ItemClass.WEAPON && item.get() != ItemClass.PICKAXE) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon or a tool");
    }
}
