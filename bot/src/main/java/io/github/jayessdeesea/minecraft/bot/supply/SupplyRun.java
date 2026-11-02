package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One soldier drawing kit from the supply point's chest: open it, take what
 * {@link Quartermaster#take} picks, close it, then put the kit on ({@link Quartermaster#equip}).
 * Driven by the soldier's tick ({@link #tick}) and by its connection's container updates
 * ({@link #onContainer}); synchronized because those are two threads.
 *
 * <p>The server applies each shift-click itself, so the clicks go out back to back without waiting
 * for answers. Their effect on the soldier's own slots arrives as updates to the chest's window,
 * which {@link Inventory#setWindowSlot} applies - the server doesn't repeat them for the inventory
 * window afterwards. Equipping waits {@link #SETTLE_TICKS} after closing for those to land.
 *
 * <p>After a run - kit drawn, chest empty, or no answer within {@link #OPEN_TIMEOUT_TICKS} - the
 * soldier waits {@link #COOLDOWN_TICKS} before trying again, so an empty chest isn't hammered.
 */
public final class SupplyRun {

    private static final Logger LOG = LogManager.getLogger(SupplyRun.class);

    /** Close enough to use the chest: vanilla's reach is about 4.5 blocks from the eyes. */
    public static final double REACH = 3.5;
    static final int OPEN_TIMEOUT_TICKS = 40;
    static final int SETTLE_TICKS = 10;
    static final int COOLDOWN_TICKS = 600;
    /** Menu registry IDs 0-5 are generic_9x1 to generic_9x6: chests, barrels, double chests. */
    private static final int GENERIC_9X6 = 5;

    private enum State { IDLE, OPENING, SETTLING }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private int containerId = -1;
    private int containerSize;

    public SupplyRun(String name, PlaySession session, Inventory inventory, Runnable kitChanged) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.kitChanged = kitChanged;
    }

    /**
     * Each tick: starts a run when the soldier stands within {@link #REACH} of {@code supplyPoint}
     * and {@code wanted} (it's short of kit and free to draw it), and advances a run in progress.
     */
    public synchronized void tick(Position soldier, Optional<Position> supplyPoint, boolean wanted) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        switch (state) {
            case IDLE -> {
                if (wanted && cooldown == 0 && supplyPoint.isPresent() && withinReach(soldier, supplyPoint.get())) {
                    Position chest = supplyPoint.get();
                    LOG.info("{} draws kit at the supply point {}", name, chest);
                    session.useBlock((int) Math.floor(chest.x()), (int) Math.floor(chest.y()), (int) Math.floor(chest.z()));
                    enter(State.OPENING);
                }
            }
            case OPENING -> {
                if (ticksInState > OPEN_TIMEOUT_TICKS) {
                    LOG.warn("{}: the supply point's chest didn't open - is it there, and in reach?", name);
                    finish();
                }
            }
            case SETTLING -> {
                if (ticksInState >= SETTLE_TICKS) {
                    equip();
                    finish();
                }
            }
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int id, int menuType) -> {
                if (state != State.OPENING) {
                    return;
                }
                if (menuType < 0 || menuType > GENERIC_9X6) {
                    LOG.warn("{}: the supply point opened menu type {}, not a chest - closing it", name, menuType);
                    session.closeContainer(id);
                    finish();
                    return;
                }
                containerId = id;
                containerSize = (menuType + 1) * 9;
            }
            case ContainerUpdate.Contents(int id, int stateId, var slots, boolean complete) -> {
                if (id != containerId) {
                    return;
                }
                for (int slot = containerSize; slot < slots.size(); slot++) {
                    inventory.setWindowSlot(slot, containerSize, stack(slots.get(slot)));
                }
                if (state == State.OPENING) {
                    takeFrom(slots, stateId);
                }
                kitChanged.run();
            }
            case ContainerUpdate.Slot(int id, int stateId, int slot, var item) -> {
                if (id == containerId) {
                    inventory.setWindowSlot(slot, containerSize, stack(item));
                    kitChanged.run();
                }
            }
            case ContainerUpdate.Closed(int id) -> {
                if (id == containerId && state == State.OPENING) {
                    LOG.warn("{}: the server closed the supply chest before anything was taken", name);
                    finish();
                }
            }
        }
    }

    private void takeFrom(List<Optional<ItemSlot>> slots, int stateId) {
        List<Integer> chest = new ArrayList<>(containerSize);
        for (int slot = 0; slot < Math.min(containerSize, slots.size()); slot++) { // short if a stack didn't decode
            chest.add(slots.get(slot).map(ItemSlot::itemId).orElse(-1));
        }
        List<Integer> take = Quartermaster.take(chest, inventory);
        LOG.info("{} takes chest slot(s) {} of {}", name, take, chest);
        for (int slot : take) {
            session.quickMove(containerId, stateId, slot);
        }
        session.closeContainer(containerId);
        enter(State.SETTLING);
    }

    private void equip() {
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            LOG.info("{} {}", name, step);
            switch (step) {
                // State ID 0 is almost certainly stale: the server then resends the whole
                // inventory, which confirms what the soldier is wearing and holding.
                case Quartermaster.Step.Wear(int slot) -> session.quickMove(0, 0, slot);
                case Quartermaster.Step.Hold(int hotbarSlot) -> {
                    session.selectHotbarSlot(hotbarSlot);
                    inventory.selectHotbar(hotbarSlot);
                }
                case Quartermaster.Step.BringToHand(int slot, int hotbarSlot) -> session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
        kitChanged.run();
    }

    private void finish() {
        containerId = -1;
        cooldown = COOLDOWN_TICKS;
        enter(State.IDLE);
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }

    private static boolean withinReach(Position soldier, Position chest) {
        double dx = soldier.x() - (Math.floor(chest.x()) + 0.5);
        double dy = soldier.y() - Math.floor(chest.y());
        double dz = soldier.z() - (Math.floor(chest.z()) + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= REACH;
    }

    private static int[] stack(Optional<ItemSlot> item) {
        return item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null);
    }
}
