package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * A bare-handed soldier making itself a wooden sword - doctrine's pre-combat checks
 * (docs/military-doctrine.md §11): find a log within {@link #SEARCH_RADIUS}, stand beside it, chop
 * two logs, craft planks, sticks and a crafting table in the inventory's 2x2 grid, place the table,
 * craft the sword in it, and hold it. About a minute. Driven by the soldier's tick ({@link #tick})
 * and its connection's container updates ({@link #onContainer}); synchronized because those are
 * two threads.
 *
 * <p>Every click goes out with a stale state ID on purpose: the server applies the click, then
 * resends the whole window, which keeps {@link Inventory} exact between phases. Each phase reads
 * the inventory afresh after {@link #SETTLE_TICKS}, so a phase never acts on a guess. Whatever
 * goes wrong - no tree in reach, a missing item, no answer from the table - the routine gives up,
 * puts anything left in the grid back ({@code closeContainer(0)}), and waits
 * {@link #COOLDOWN_TICKS} before trying again. It also gives up the moment it's no longer wanted
 * (a hostile near, the squad leader's leave withdrawn).
 */
public final class Woodcraft {

    private static final Logger LOG = LogManager.getLogger(Woodcraft.class);

    /** How far a soldier looks for a tree. */
    static final int SEARCH_RADIUS = 16;
    /** Logs from the stand cell's floor up to this many blocks above it are within reach. */
    static final int REACH_UP = 3;
    /** Two logs make eight planks: four for the table, two for sticks, two for the sword. */
    static final int LOGS_NEEDED = 2;
    /** A log by hand: hardness 2, so 3 seconds - with a margin, since the server times it. */
    static final int DIG_TICKS = 70;
    static final int PICKUP_TICKS = 20;
    static final int SETTLE_TICKS = 20;
    static final int WALK_TIMEOUT_TICKS = 400;
    static final int OPEN_TIMEOUT_TICKS = 40;
    static final int COOLDOWN_TICKS = 1200;
    /** Menu registry ID of the crafting table's window ({@code minecraft:crafting}). */
    static final int CRAFTING_MENU = 12;
    /** The inventory's own 2x2 grid: result 0, grid 1-4 (row by row). */
    private static final int RESULT = 0;
    /** The table's window: result and nine grid slots come before the inventory's. */
    private static final int TABLE_WINDOW_SLOTS = 10;

    /** Where to stand, and the log to chop from there. */
    record Tree(BlockPos stand, BlockPos log) {
    }

    private enum State { IDLE, WALKING, DIGGING, PICKING_UP, PLANKS, STICKS, TABLE, PLACE, OPEN, SWORD, EQUIP }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private Tree tree;
    private BlockPos table;
    /** Cells a table was tried in this run and didn't land - something the block data can't show (a squadmate) was there. */
    private final java.util.Set<BlockPos> blockedCells = new java.util.HashSet<>();
    private int windowId = -1;
    private int windowState;

    public Woodcraft(String name, PlaySession session, Inventory inventory, Runnable kitChanged) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.kitChanged = kitChanged;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /** Where the soldier should be standing while the routine runs, if it needs to be somewhere. */
    public synchronized Optional<Position> standAt() {
        return state == State.WALKING || state == State.DIGGING || state == State.PICKING_UP
                ? Optional.of(new Position(tree.stand().x() + 0.5, tree.stand().y(), tree.stand().z() + 0.5))
                : Optional.empty();
    }

    /**
     * Each tick: starts the routine when {@code wanted} (bare-handed, allowed, no hostile near) and
     * advances it; abandons it when no longer wanted.
     */
    public synchronized void tick(Position soldier, WorldView world, boolean wanted) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (state != State.IDLE && !wanted) {
            giveUp("no longer wanted");
            return;
        }
        switch (state) {
            case IDLE -> {
                if (wanted && cooldown == 0) {
                    gather(soldier, world);
                }
            }
            case WALKING -> {
                if (at(soldier, tree.stand())) {
                    session.startDigging(tree.log().x(), tree.log().y(), tree.log().z());
                    enter(State.DIGGING);
                } else if (ticksInState > WALK_TIMEOUT_TICKS) {
                    giveUp("couldn't reach the tree at " + tree.log());
                }
            }
            case DIGGING -> {
                if (ticksInState >= DIG_TICKS) {
                    session.finishDigging(tree.log().x(), tree.log().y(), tree.log().z());
                    enter(State.PICKING_UP);
                }
            }
            case PICKING_UP -> {
                if (ticksInState >= PICKUP_TICKS) {
                    gather(soldier, world);
                }
            }
            case PLANKS -> settled(() -> {
                int log = need(ItemClass.LOG);
                session.click(0, 0, log, false);   // every log onto the cursor,
                session.click(0, 0, 1, false);     // into the grid,
                session.quickMove(0, 0, RESULT);   // and craft them all into planks
                enter(State.STICKS);
            });
            case STICKS -> settled(() -> {
                int planks = need(ItemClass.PLANKS);
                session.click(0, 0, planks, false);
                session.click(0, 0, 1, true);      // one plank above
                session.click(0, 0, 3, true);      // another below: sticks
                session.click(0, 0, planks, false);
                session.quickMove(0, 0, RESULT);
                enter(State.TABLE);
            });
            case TABLE -> settled(() -> {
                int planks = need(ItemClass.PLANKS);
                session.click(0, 0, planks, false);
                for (int grid = 1; grid <= 4; grid++) {
                    session.click(0, 0, grid, true); // a plank in every square: a crafting table
                }
                session.click(0, 0, planks, false);
                session.quickMove(0, 0, RESULT);
                session.closeContainer(0);
                enter(State.PLACE);
            });
            case PLACE -> settled(() -> {
                int slot = need(ItemClass.CRAFTING_TABLE);
                table = placement(soldier, world, blockedCells);
                // Into hotbar slot 0, and hold that - saying so, rather than trusting which slot
                // the server thinks is held.
                if (slot != Inventory.HOTBAR_FIRST) {
                    session.swapWithHotbar(0, 0, slot, 0);
                }
                session.selectHotbarSlot(0);
                inventory.selectHotbar(0);
                session.useBlock(table.x(), table.y() - 1, table.z()); // the top of the ground there
                enter(State.OPEN);
                ticksInState = -SETTLE_TICKS; // let the table land before opening it
            });
            case OPEN -> {
                if (ticksInState == 0) {
                    if (!world.isSolid(table.x(), table.y(), table.z())) {
                        blockedCells.add(table);
                        if (blockedCells.size() >= 4) {
                            giveUp("no cell around would take the crafting table - " + inventory.summary());
                        } else {
                            LOG.info("{}: the crafting table didn't land at {} - trying beside it", name, table);
                            enter(State.PLACE);
                        }
                        return;
                    }
                    session.useBlock(table.x(), table.y(), table.z());
                } else if (ticksInState > OPEN_TIMEOUT_TICKS) {
                    giveUp("the crafting table at " + table + " didn't open");
                }
            }
            case SWORD -> settled(() -> {
                // The table's window: result 0, grid 1-9 (row by row), then inventory 9-44 as 10-45.
                int planks = need(ItemClass.PLANKS) + 1;
                int sticks = need(ItemClass.STICK) + 1;
                session.click(windowId, windowState, planks, false);
                session.click(windowId, 0, 2, true);  // the middle column, top:
                session.click(windowId, 0, 5, true);  // planks, planks,
                session.click(windowId, 0, planks, false);
                session.click(windowId, 0, sticks, false);
                session.click(windowId, 0, 8, true);  // and a stick for the hilt
                session.click(windowId, 0, sticks, false);
                session.quickMove(windowId, 0, RESULT);
                session.closeContainer(windowId);
                enter(State.EQUIP);
            });
            case EQUIP -> settled(() -> {
                if (inventory.slotOf(ItemClass.WEAPON) < 0) {
                    throw new IllegalStateException("no sword came out of the crafting table - " + inventory.summary());
                }
                for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
                    switch (step) {
                        case Quartermaster.Step.Wear(int slot) -> session.quickMove(0, 0, slot);
                        case Quartermaster.Step.Hold(int hotbarSlot) -> {
                            session.selectHotbarSlot(hotbarSlot);
                            inventory.selectHotbar(hotbarSlot);
                        }
                        case Quartermaster.Step.BringToHand(int slot, int hotbarSlot) -> session.swapWithHotbar(0, 0, slot, hotbarSlot);
                    }
                }
                LOG.info("{} made a wooden sword", name);
                kitChanged.run();
                finish();
            });
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int id, int menuType) -> {
                if (state != State.OPEN) {
                    return;
                }
                if (menuType != CRAFTING_MENU) {
                    session.closeContainer(id);
                    giveUp("opened menu type " + menuType + ", not a crafting table");
                    return;
                }
                windowId = id;
                enter(State.SWORD);
            }
            // While the table is open, the inventory's changes arrive only through its window.
            case ContainerUpdate.Contents(int id, int stateId, var slots, boolean complete) -> {
                if (id == windowId) {
                    windowState = stateId;
                    for (int slot = TABLE_WINDOW_SLOTS; slot < slots.size(); slot++) {
                        inventory.setWindowSlot(slot, TABLE_WINDOW_SLOTS, stack(slots.get(slot)));
                    }
                }
            }
            case ContainerUpdate.Slot(int id, int stateId, int slot, var item) -> {
                if (id == windowId) {
                    windowState = stateId;
                    inventory.setWindowSlot(slot, TABLE_WINDOW_SLOTS, stack(item));
                }
            }
            case ContainerUpdate.Closed(int id) -> {
                if (id == windowId && state == State.SWORD) {
                    giveUp("the server closed the crafting table");
                }
            }
        }
    }

    /** Chop the next log, or - with enough wood - start crafting. */
    private void gather(Position soldier, WorldView world) {
        if (inventory.count(ItemClass.LOG) >= LOGS_NEEDED) {
            LOG.info("{} has the wood - crafting", name);
            enter(State.PLANKS);
            return;
        }
        Optional<Tree> next = findTree(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()));
        if (next.isEmpty()) {
            giveUp("no tree within " + SEARCH_RADIUS + " blocks");
            return;
        }
        if (tree == null) {
            LOG.info("{} goes to chop wood at {}", name, next.get().log());
        }
        tree = next.get();
        enter(State.WALKING);
    }

    /**
     * The nearest log within {@link #SEARCH_RADIUS} of {@code from} that a soldier can chop: one
     * beside a cell it can stand in (solid below, room for its body), at most {@link #REACH_UP}
     * above that cell's floor. Nearest by the stand cell's distance.
     */
    static Optional<Tree> findTree(WorldView world, BlockPos from) {
        Tree best = null;
        double bestDistance = Double.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos stand = from.offset(dx, dy, dz);
                    if (!standable(world, stand)) {
                        continue;
                    }
                    for (int[] side : sides) {
                        for (int up = 0; up <= REACH_UP; up++) {
                            BlockPos log = stand.offset(side[0], up, side[1]);
                            if (world.isLog(log.x(), log.y(), log.z())) {
                                double distance = Math.hypot(dx, dz) + Math.abs(dy) + up * 0.1;
                                if (distance < bestDistance) {
                                    best = new Tree(stand, log);
                                    bestDistance = distance;
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean standable(WorldView world, BlockPos cell) {
        return world.isSolid(cell.x(), cell.y() - 1, cell.z()) && !world.isSolid(cell.x(), cell.y(), cell.z())
                && !world.isSolid(cell.x(), cell.y() + 1, cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z());
    }

    /** A player's body is 0.6 wide: the server won't place a block where it overlaps. */
    private static final double HALF_WIDTH = 0.3;

    /** Where to put the table: a free cell beside the soldier, clear of its body, with ground under it. */
    private static BlockPos placement(Position soldier, WorldView world, java.util.Set<BlockPos> blocked) {
        BlockPos at = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos cell = at.offset(side[0], 0, side[1]);
            boolean underBody = soldier.x() + HALF_WIDTH > cell.x() && soldier.x() - HALF_WIDTH < cell.x() + 1
                    && soldier.z() + HALF_WIDTH > cell.z() && soldier.z() - HALF_WIDTH < cell.z() + 1;
            if (!underBody && !blocked.contains(cell) && world.isSolid(cell.x(), cell.y() - 1, cell.z()) && !world.isSolid(cell.x(), cell.y(), cell.z())
                    && !world.isHazard(cell.x(), cell.y(), cell.z())) {
                return cell;
            }
        }
        throw new IllegalStateException("no free cell beside " + at + " to place a crafting table");
    }

    private static int[] stack(Optional<io.github.jayessdeesea.minecraft.protocol.ItemSlot> item) {
        return item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null);
    }

    private static boolean at(Position soldier, BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6
                && Math.abs(soldier.y() - cell.y()) < 0.6;
    }

    /** Runs {@code phase} once the inventory has settled after the last one; gives up if it throws. */
    private void settled(Runnable phase) {
        if (ticksInState < SETTLE_TICKS) {
            return;
        }
        try {
            phase.run();
        } catch (IllegalStateException e) {
            giveUp(e.getMessage());
        }
    }

    private int need(ItemClass itemClass) {
        int slot = inventory.slotOf(itemClass);
        if (slot < 0) {
            throw new IllegalStateException("no " + itemClass + " in the inventory");
        }
        return slot;
    }

    private void giveUp(String why) {
        LOG.warn("{} gives up making a sword ({}) - {}", name, state, why);
        if (windowId >= 0) {
            session.closeContainer(windowId);
        }
        session.closeContainer(0); // anything left in the 2x2 grid or on the cursor goes back
        finish();
    }

    private void finish() {
        blockedCells.clear();
        tree = null;
        table = null;
        windowId = -1;
        cooldown = COOLDOWN_TICKS;
        enter(State.IDLE);
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }
}
