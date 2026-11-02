package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One soldier carrying gathered materials to the stockpile - the supply point's chest
 * (docs/military-doctrine.md §12): walk to it, open it, shift-click in the material it has a full
 * load of, close it. Only that: a soldier quarrying stone keeps the logs it carries, because its
 * wooden pickaxe wears out in 59 blocks and the next one is made from them. What the chest holds once it's done is reported ({@code stockpileSeen}), so the chain of
 * command knows the stockpile without anyone counting it by hand. Driven like {@link SupplyRun}, by
 * the soldier's tick and its connection's container updates; synchronized for the same reason.
 *
 * <p>The chest's window shows the soldier's own inventory after the chest's slots; its changes
 * arrive only there while the chest is open, and are applied to {@link Inventory} as they come.
 */
public final class Depositor {

    private static final Logger LOG = LogManager.getLogger(Depositor.class);

    static final int WALK_TIMEOUT_TICKS = 600;
    static final int OPEN_TIMEOUT_TICKS = 40;
    static final int SETTLE_TICKS = 10;
    static final int COOLDOWN_TICKS = 200;
    /** Menu registry IDs 0-5 are generic_9x1 to generic_9x6. */
    private static final int GENERIC_9X6 = 5;

    private enum State { IDLE, WALKING, OPENING, SETTLING }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Consumer<Stockpile> stockpileSeen;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private Position chest;
    private int containerId = -1;
    private int containerSize;
    private int[] chestItems = new int[0];
    private int[] chestCounts = new int[0];

    public Depositor(String name, PlaySession session, Inventory inventory, Consumer<Stockpile> stockpileSeen) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.stockpileSeen = stockpileSeen;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /** Where the soldier should go while it carries its load in. */
    public synchronized Optional<Position> standAt() {
        return state == State.WALKING ? Optional.of(chest) : Optional.empty();
    }

    /**
     * Each tick: starts a trip to {@code stockpile} when {@code wanted} (a full load, nothing near),
     * and advances one under way. A trip that stops being wanted on the way is dropped.
     */
    public synchronized void tick(Position soldier, Optional<Position> stockpile, boolean wanted) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        switch (state) {
            case IDLE -> {
                if (wanted && cooldown == 0 && stockpile.isPresent()) {
                    chest = stockpile.get();
                    LOG.info("{} carries a load to the stockpile at {} - {}", name, chest, inventory.summary());
                    enter(State.WALKING);
                }
            }
            case WALKING -> {
                if (!wanted) {
                    finish();
                } else if (withinReach(soldier, chest)) {
                    session.useBlock((int) Math.floor(chest.x()), (int) Math.floor(chest.y()), (int) Math.floor(chest.z()));
                    enter(State.OPENING);
                } else if (ticksInState > WALK_TIMEOUT_TICKS) {
                    LOG.warn("{} couldn't reach the stockpile at {}", name, chest);
                    finish();
                }
            }
            case OPENING -> {
                if (ticksInState > OPEN_TIMEOUT_TICKS) {
                    LOG.warn("{}: the stockpile's chest didn't open", name);
                    finish();
                }
            }
            case SETTLING -> {
                if (ticksInState >= SETTLE_TICKS) {
                    session.closeContainer(containerId);
                    Stockpile seen = tally();
                    LOG.info("{} stockpiled its load - the chest holds {} logs, {} cobblestone", name, seen.logs(), seen.stone());
                    stockpileSeen.accept(seen);
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
                    session.closeContainer(id);
                    LOG.warn("{}: the stockpile opened menu type {}, not a chest", name, menuType);
                    finish();
                    return;
                }
                containerId = id;
                containerSize = (menuType + 1) * 9;
                chestItems = new int[containerSize];
                chestCounts = new int[containerSize];
            }
            case ContainerUpdate.Contents(int id, int stateId, var slots, boolean complete) -> {
                if (id != containerId) {
                    return;
                }
                for (int slot = 0; slot < slots.size(); slot++) {
                    apply(slot, slots.get(slot));
                }
                if (state == State.OPENING) {
                    deposit(slots, stateId);
                }
            }
            case ContainerUpdate.Slot(int id, int stateId, int slot, var item) -> {
                if (id == containerId) {
                    apply(slot, item);
                }
            }
            case ContainerUpdate.Closed(int id) -> {
                if (id == containerId && state == State.OPENING) {
                    finish();
                }
            }
        }
    }

    private void deposit(List<Optional<ItemSlot>> slots, int stateId) {
        boolean logs = inventory.count(ItemClass.LOG) >= Gatherer.CARRY_LOGS;
        boolean stone = inventory.count(ItemClass.COBBLESTONE) >= Gatherer.CARRY_STONE;
        for (int slot = containerSize; slot < slots.size(); slot++) {
            Optional<ItemClass> itemClass = slots.get(slot).flatMap(i -> ItemClassRegistry.classOf(i.itemId()));
            if ((logs && itemClass.equals(Optional.of(ItemClass.LOG))) || (stone && itemClass.equals(Optional.of(ItemClass.COBBLESTONE)))) {
                session.quickMove(containerId, stateId, slot);
            }
        }
        enter(State.SETTLING);
    }

    /** One slot of the chest's window: the chest's own slots are tallied, the rest are the soldier's inventory. */
    private void apply(int slot, Optional<ItemSlot> item) {
        if (slot < containerSize) {
            chestItems[slot] = item.map(ItemSlot::itemId).orElse(-1);
            chestCounts[slot] = item.map(ItemSlot::count).orElse(0);
        } else {
            inventory.setWindowSlot(slot, containerSize, item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null));
        }
    }

    private Stockpile tally() {
        int logs = 0;
        int stone = 0;
        for (int slot = 0; slot < containerSize; slot++) {
            Optional<ItemClass> itemClass = ItemClassRegistry.classOf(chestItems[slot]);
            if (itemClass.equals(Optional.of(ItemClass.LOG))) {
                logs += chestCounts[slot];
            } else if (itemClass.equals(Optional.of(ItemClass.COBBLESTONE))) {
                stone += chestCounts[slot];
            }
        }
        return new Stockpile(logs, stone);
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
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= SupplyRun.REACH;
    }
}
