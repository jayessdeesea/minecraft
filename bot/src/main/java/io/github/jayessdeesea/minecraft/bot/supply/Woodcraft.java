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
import java.util.Set;

/**
 * A soldier making itself a wooden tool (docs/military-doctrine.md §11, §12): a sword when it's
 * bare-handed, a pickaxe when it's sent for stone. Chop the logs the tool needs (fewer if it carries
 * some), craft planks, sticks and a crafting table in the inventory's 2x2 grid, place the table,
 * craft the tool in it - and hold it, for a sword. About a minute. Driven by the soldier's tick ({@link #tick})
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

    /**
     * What it can make, and from what. The table's 3x3 grid slots are 1-9, row by row. Each needs a
     * table (4 planks) and sticks (2 planks make 4); a log makes 4 planks.
     */
    public enum Tool {
        /** Planks over planks over a stick, down the middle: 2 logs make the 8 planks. Held once made. */
        WOODEN_SWORD(2, new int[]{2, 5}, new int[]{8}, ItemClass.WEAPON, true),
        /** Three planks across the top, two sticks down the middle: 3 logs make the 9 planks. */
        WOODEN_PICKAXE(3, new int[]{1, 2, 3}, new int[]{5, 8}, ItemClass.PICKAXE, false);

        final int logs;
        final int[] plankSlots;
        final int[] stickSlots;
        final ItemClass makes;
        final boolean hold;

        Tool(int logs, int[] plankSlots, int[] stickSlots, ItemClass makes, boolean hold) {
            this.logs = logs;
            this.plankSlots = plankSlots;
            this.stickSlots = stickSlots;
            this.makes = makes;
            this.hold = hold;
        }
    }
    static final int SETTLE_TICKS = 20;
    static final int OPEN_TIMEOUT_TICKS = 40;
    static final int COOLDOWN_TICKS = 1200;
    /** Menu registry ID of the crafting table's window ({@code minecraft:crafting}). */
    static final int CRAFTING_MENU = 12;
    /** The inventory's own 2x2 grid: result 0, grid 1-4 (row by row). */
    private static final int RESULT = 0;
    /** The table's window: result and nine grid slots come before the inventory's. */
    private static final int TABLE_WINDOW_SLOTS = 10;

    private enum State { IDLE, CHOPPING, PLANKS, STICKS, TABLE, PLACE, OPEN, TOOL, EQUIP }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private Digger digger;
    private Tool tool;
    private String failure;
    private BlockPos table;
    /** Cells a table was tried in this run and didn't land - something the block data can't show (a squadmate) was there. */
    private final java.util.Set<BlockPos> blockedCells = new java.util.HashSet<>();
    private int windowId = -1;
    private int windowState;

    private final Set<BlockPos> claims;

    public Woodcraft(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Runnable kitChanged) {
        this.claims = claims;
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.kitChanged = kitChanged;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /**
     * Why the last attempt failed, while the soldier waits out its cooldown - so it can say it
     * can't make what it needs rather than look idle. Empty after a success or once it tries again.
     */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** The tool being made, while {@link #active}. */
    public synchronized Tool tool() {
        return tool;
    }

    /** Where the soldier should be standing while the routine runs, if it needs to be somewhere. */
    public synchronized Optional<Position> standAt() {
        return state == State.CHOPPING ? Optional.of(digger.standAt()) : Optional.empty();
    }

    /**
     * Each tick: starts making {@code wanted} - empty when the soldier needs nothing, or isn't free to
     * make it (a hostile near, the squad leader's leave withdrawn) - and advances it; abandons a tool
     * no longer wanted.
     */
    public synchronized void tick(Position soldier, WorldView world, Optional<Tool> wanted) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (state != State.IDLE && !wanted.equals(Optional.of(tool))) {
            if (inventory.slotOf(tool.makes) >= 0 && (state == State.EQUIP || state == State.TOOL)) {
                LOG.info("{} made a {}", name, tool); // made - no longer wanted only because it now exists
                kitChanged.run();
                finish();
            } else {
                giveUp("no longer wanted");
            }
            return;
        }
        switch (state) {
            case IDLE -> {
                if (wanted.isPresent() && cooldown == 0) {
                    tool = wanted.get();
                    failure = null;
                    gather(soldier, world);
                }
            }
            case CHOPPING -> {
                switch (digger.tick(soldier, world)) {
                    case Digger.Progress.Working working -> {
                    }
                    case Digger.Progress.Done done -> {
                        digger.release();
                        gather(soldier, world);
                    }
                    case Digger.Progress.Failed(String why) -> giveUp(why);
                }
            }
            case PLANKS -> settled(() -> {
                int log = need(ItemClass.LOG);
                session.click(0, 0, log, false);       // the logs onto the cursor,
                for (int i = 0; i < tool.logs; i++) {
                    session.click(0, 0, 1, true);      // as many as the tool needs into the grid,
                }
                session.click(0, 0, log, false);       // the rest back - gathered wood stays wood -
                session.quickMove(0, 0, RESULT);       // and craft them into planks
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
                if (inventory.slotOf(ItemClass.CRAFTING_TABLE) >= 0) {
                    enter(State.PLACE); // one left over from an earlier attempt
                    return;
                }
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
            case TOOL -> settled(() -> {
                // The table's window: result 0, grid 1-9 (row by row), then inventory 9-44 as 10-45.
                int planks = need(ItemClass.PLANKS) + 1;
                int sticks = need(ItemClass.STICK) + 1;
                session.click(windowId, windowState, planks, false);
                for (int slot : tool.plankSlots) {
                    session.click(windowId, 0, slot, true);
                }
                session.click(windowId, 0, planks, false);
                session.click(windowId, 0, sticks, false);
                for (int slot : tool.stickSlots) {
                    session.click(windowId, 0, slot, true);
                }
                session.click(windowId, 0, sticks, false);
                session.quickMove(windowId, 0, RESULT);
                session.closeContainer(windowId);
                enter(State.EQUIP);
            });
            case EQUIP -> settled(() -> {
                if (inventory.slotOf(tool.makes) < 0) {
                    throw new IllegalStateException("no " + tool + " came out of the crafting table - " + inventory.summary());
                }
                if (!tool.hold) {
                    LOG.info("{} made a {}", name, tool);
                    kitChanged.run();
                    finish();
                    return;
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
                LOG.info("{} made a {}", name, tool);
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
                enter(State.TOOL);
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
                if (id == windowId && state == State.TOOL) {
                    giveUp("the server closed the crafting table");
                }
            }
        }
    }

    /** Chop the next log, or - with enough wood - start crafting. */
    private void gather(Position soldier, WorldView world) {
        if (inventory.count(ItemClass.LOG) >= tool.logs) {
            LOG.info("{} has the wood - crafting a {}", name, tool);
            enter(State.PLANKS);
            return;
        }
        Optional<Digger.Spot> next = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims,
                Digger.Target.LOG);
        if (next.isEmpty()) {
            giveUp("no tree within " + Digger.SEARCH_RADIUS + " blocks");
            return;
        }
        if (digger == null) {
            LOG.info("{} goes to chop wood for a {} at {}", name, tool, next.get().block());
        }
        digger = new Digger(session, inventory, claims, next.get());
        enter(State.CHOPPING);
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
        LOG.warn("{} gives up making a {} ({}) - {}", name, tool, state, why);
        failure = "can't make a " + tool.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ": " + why;
        if (windowId >= 0) {
            session.closeContainer(windowId);
        }
        session.closeContainer(0); // anything left in the 2x2 grid or on the cursor goes back
        finish();
    }

    private void finish() {
        blockedCells.clear();
        if (digger != null) {
            digger.release();
        }
        digger = null;
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
