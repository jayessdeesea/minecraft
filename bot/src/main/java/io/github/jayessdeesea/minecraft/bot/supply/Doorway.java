package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * One soldier opening a doorway out of the squad's patrol base (docs/military-doctrine.md §14):
 * breaking the wall blocks in the way, from inside, where it stands. Which blocks is
 * {@code command.PatrolBase}'s; this only breaks them. With a pickaxe a cobblestone wall block takes
 * 1.5 seconds; bare-handed, 10 - the server's break time, with a margin, since it times every dig.
 * Each break is checked against the block data. Driven by the soldier's tick.
 */
public final class Doorway {

    private static final Logger LOG = LogManager.getLogger(Doorway.class);

    /** Cobblestone, hardness 2: a wooden pickaxe (speed 2) breaks it in 30 ticks. */
    static final int PICKAXE_TICKS = 40;
    /** By hand, the wrong tool: speed 1 over hardness 2, at a hundredth a tick - 200 ticks. */
    static final int HAND_TICKS = 220;
    static final int SETTLE_TICKS = 6;
    static final int ATTEMPTS = 2;

    private enum State { IDLE, TO_HAND, DIGGING }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private State state = State.IDLE;
    private int ticksInState;
    private BlockPos target;
    private int digTicks;
    private int attempts;
    private int previousHotbar = -1;

    public Doorway(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Each tick, breaking the first of {@code cells} still solid; true once none is. */
    public synchronized boolean tick(WorldView world, List<BlockPos> cells) {
        ticksInState++;
        Optional<BlockPos> next = cells.stream().filter(c -> world.isSolid(c.x(), c.y(), c.z())).findFirst();
        if (next.isEmpty()) {
            stop();
            return true;
        }
        switch (state) {
            case IDLE -> {
                target = next.get();
                attempts = 0;
                int pickaxe = inventory.slotOf(ItemClass.PICKAXE);
                if (pickaxe >= 0 && pickaxe != Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()) {
                    previousHotbar = inventory.heldHotbarSlot();
                    int hotbar = pickaxe >= Inventory.HOTBAR_FIRST ? pickaxe - Inventory.HOTBAR_FIRST : spareHotbarSlot();
                    if (pickaxe < Inventory.HOTBAR_FIRST) {
                        session.swapWithHotbar(0, 0, pickaxe, hotbar);
                    }
                    session.selectHotbarSlot(hotbar);
                    inventory.selectHotbar(hotbar);
                }
                enter(State.TO_HAND);
            }
            case TO_HAND -> {
                if (ticksInState >= SETTLE_TICKS) {
                    dig(next.get());
                }
            }
            case DIGGING -> {
                if (ticksInState >= digTicks) {
                    session.finishDigging(target.x(), target.y(), target.z());
                    attempts++;
                    enter(State.TO_HAND); // settle, then the next solid cell - or this one again
                    if (attempts > ATTEMPTS && next.get().equals(target)) {
                        LOG.warn("{} couldn't break the wall at {}", name, target);
                    }
                }
            }
        }
        return false;
    }

    private void dig(BlockPos cell) {
        if (!cell.equals(target)) {
            target = cell;
            attempts = 0;
        }
        boolean pickaxe = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()))
                .equals(Optional.of(ItemClass.PICKAXE));
        digTicks = pickaxe ? PICKAXE_TICKS : HAND_TICKS;
        session.startDigging(cell.x(), cell.y(), cell.z());
        enter(State.DIGGING);
    }

    /** Stops: whatever was in hand before goes back. */
    public synchronized void stop() {
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        state = State.IDLE;
        target = null;
    }

    private int spareHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon");
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }
}
