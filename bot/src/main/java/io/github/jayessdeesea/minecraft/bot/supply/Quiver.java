package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

/**
 * The bow in hand while a soldier shoots, and whatever it held before back in hand after: the
 * draw is the server's Use Item with the bow held, the shot its release
 * ({@code DeterministicSoldier#drawing}).
 */
public final class Quiver {

    private final PlaySession session;
    private final Inventory inventory;
    private int previousHotbar = -1;

    public Quiver(PlaySession session, Inventory inventory) {
        this.session = session;
        this.inventory = inventory;
    }

    /** Puts the bow in hand. False if none is carried. */
    public synchronized boolean ready() {
        int slot = inventory.slotOfNamed("bow");
        if (slot < 0) {
            return false;
        }
        if (previousHotbar < 0) {
            previousHotbar = inventory.heldHotbarSlot();
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : SPARE_HOTBAR;
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
        return true;
    }

    /** What was in hand before the bow, back in hand. */
    public synchronized void stow() {
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
    }

    /** The hotbar slot a bow from the main inventory goes into: the last, clear of the sword in the first. */
    static final int SPARE_HOTBAR = 7;
}
