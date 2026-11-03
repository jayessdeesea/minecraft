package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier making torches for its squad's patrol base (docs/military-doctrine.md §14), from what
 * a squad carries: logs and cobblestone. Planks and sticks in the inventory's 2x2 grid; a crafting
 * table placed beside it; a furnace (8 cobblestone) crafted at the table and placed; a log smelted
 * into charcoal with a plank for fuel; then charcoal over a stick, in the 2x2 grid again: 4
 * torches. The table and furnace stay where they're put - a camp's kit.
 *
 * <p>Like {@link Woodcraft}: every click goes out with a stale state ID, so the server answers with
 * the whole window and {@link Inventory} stays exact; each phase reads the inventory afresh after
 * {@link #SETTLE_TICKS}; anything unexpected gives up, puts the grid back and waits
 * {@link #COOLDOWN_TICKS}. Held items go through a hotbar slot with no weapon or tool, so the
 * soldier stays armed. Driven by the soldier's tick and its connection's container updates;
 * synchronized because those are two threads.
 */
public final class Torchmaker {

    private static final Logger LOG = LogManager.getLogger(Torchmaker.class);

    static final int SETTLE_TICKS = 20;
    static final int OPEN_TIMEOUT_TICKS = 40;
    /** A log smelts in 200 ticks; the margin covers the furnace lighting its fuel. */
    static final int SMELT_TIMEOUT_TICKS = 400;
    static final int COOLDOWN_TICKS = 1200;
    /** Menu registry IDs: {@code minecraft:crafting} and {@code minecraft:furnace}. */
    static final int CRAFTING_MENU = 12;
    static final int FURNACE_MENU = 14;
    /** A furnace's own slots - ingredient 0, fuel 1, result 2 - come before the inventory's. */
    static final int FURNACE_SLOTS = 3;
    static final int TABLE_SLOTS = 10;
    /** The 2x2 grid's slots: result 0, then 1 2 over 3 4. */
    private static final int RESULT = 0;
    /** Planks it uses: 4 for a table, 2 for the sticks, 1 to fuel the furnace. */
    static final int TABLE_PLANKS = 4;
    static final int STICK_PLANKS = 2;
    static final int FUEL_PLANKS = 1;
    static final int FURNACE_COBBLESTONE = 8;
    /** The furnace's 3x3 recipe: cobblestone in every slot but the centre. */
    private static final int[] FURNACE_PATTERN = {1, 2, 3, 4, 6, 7, 8, 9};

    private enum State { IDLE, PLANKS, STICKS, TABLE_ITEM, PLACE_TABLE, OPEN_TABLE, FURNACE_ITEM, PLACE_FURNACE, OPEN_FURNACE, SMELT, WAIT, TORCHES }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private String failure;
    private BlockPos table;
    private BlockPos furnace;
    private final Set<BlockPos> blocked = new HashSet<>();
    private int windowId = -1;
    private int windowSlots;
    private boolean charcoalReady;
    private int previousHotbar = -1;

    public Torchmaker(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /** Why the last attempt failed, while the soldier waits out its cooldown. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Logs it needs before it can start, given what it already carries. */
    public static int logsNeeded(Inventory inventory) {
        int planks = (inventory.slotOf(ItemClass.CRAFTING_TABLE) >= 0 ? 0 : TABLE_PLANKS)
                + (inventory.count(ItemClass.STICK) > 0 ? 0 : STICK_PLANKS) + FUEL_PLANKS;
        int short_ = Math.max(0, planks - inventory.count(ItemClass.PLANKS));
        return 1 + (short_ + 3) / 4; // one to smelt, the rest at 4 planks a log
    }

    /** Whether it carries what torches take: the logs, and a furnace's cobblestone or a furnace. */
    public static boolean canMake(Inventory inventory) {
        return inventory.count(ItemClass.LOG) >= logsNeeded(inventory)
                && (inventory.slotOf(ItemClass.FURNACE) >= 0 || inventory.count(ItemClass.COBBLESTONE) >= FURNACE_COBBLESTONE);
    }

    /** Each tick: starts when {@code wanted} and it can, advances, and abandons a run no longer wanted. */
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
                // Not if it carries torches already: its own tick can come round before the squad
                // loop has seen the last batch.
                if (wanted && cooldown == 0 && canMake(inventory) && inventory.count(ItemClass.TORCH) == 0) {
                    LOG.info("{} makes torches - {}", name, inventory.summary());
                    failure = null;
                    blocked.clear();
                    enter(State.PLANKS);
                }
            }
            case PLANKS -> settled(() -> {
                int planksWanted = (inventory.slotOf(ItemClass.CRAFTING_TABLE) >= 0 ? 0 : TABLE_PLANKS)
                        + (inventory.count(ItemClass.STICK) > 0 ? 0 : STICK_PLANKS) + FUEL_PLANKS;
                int logs = (Math.max(0, planksWanted - inventory.count(ItemClass.PLANKS)) + 3) / 4;
                if (logs > 0) {
                    int log = need(ItemClass.LOG);
                    session.click(0, 0, log, false);
                    for (int i = 0; i < logs; i++) {
                        session.click(0, 0, 1, true);
                    }
                    session.click(0, 0, log, false);
                    session.quickMove(0, 0, RESULT);
                }
                enter(State.STICKS);
            });
            case STICKS -> settled(() -> {
                if (inventory.count(ItemClass.STICK) == 0) {
                    int planks = need(ItemClass.PLANKS);
                    session.click(0, 0, planks, false);
                    session.click(0, 0, 1, true); // a plank over
                    session.click(0, 0, 3, true); // a plank: sticks
                    session.click(0, 0, planks, false);
                    session.quickMove(0, 0, RESULT);
                }
                enter(State.TABLE_ITEM);
            });
            case TABLE_ITEM -> settled(() -> {
                if (inventory.slotOf(ItemClass.CRAFTING_TABLE) < 0 && inventory.slotOf(ItemClass.FURNACE) < 0) {
                    int planks = need(ItemClass.PLANKS);
                    session.click(0, 0, planks, false);
                    for (int grid = 1; grid <= 4; grid++) {
                        session.click(0, 0, grid, true);
                    }
                    session.click(0, 0, planks, false);
                    session.quickMove(0, 0, RESULT);
                }
                session.closeContainer(0);
                enter(inventory.slotOf(ItemClass.FURNACE) >= 0 ? State.PLACE_FURNACE : State.PLACE_TABLE);
            });
            case PLACE_TABLE -> settled(() -> {
                table = Woodcraft.placement(soldier, world, blocked);
                hold(need(ItemClass.CRAFTING_TABLE));
                session.useBlock(table.x(), table.y() - 1, table.z());
                enter(State.OPEN_TABLE);
                ticksInState = -SETTLE_TICKS;
            });
            case OPEN_TABLE -> open(world, table, State.PLACE_TABLE, "crafting table");
            case FURNACE_ITEM -> settled(() -> {
                int cobblestone = need(ItemClass.COBBLESTONE) + 1; // the table's window puts inventory after its 10 slots
                session.click(windowId, 0, cobblestone, false);
                for (int slot : FURNACE_PATTERN) {
                    session.click(windowId, 0, slot, true);
                }
                session.click(windowId, 0, cobblestone, false);
                session.quickMove(windowId, 0, RESULT);
                // Closed, but its ID kept: the server's answer to these clicks - the furnace into the
                // inventory - still arrives through this window, and isn't repeated after it closes.
                session.closeContainer(windowId);
                blocked.add(table);
                enter(State.PLACE_FURNACE);
            });
            case PLACE_FURNACE -> settled(() -> {
                furnace = Woodcraft.placement(soldier, world, blocked);
                hold(need(ItemClass.FURNACE));
                session.useBlock(furnace.x(), furnace.y() - 1, furnace.z());
                enter(State.OPEN_FURNACE);
                ticksInState = -SETTLE_TICKS;
            });
            case OPEN_FURNACE -> open(world, furnace, State.PLACE_FURNACE, "furnace");
            case SMELT -> settled(() -> {
                // The furnace's window puts inventory slot 9 at 3: window slot = inventory slot - 6.
                int log = need(ItemClass.LOG) - 6;
                session.click(windowId, 0, log, false);
                session.click(windowId, 0, 0, true); // one log to smelt
                session.click(windowId, 0, log, false);
                int planks = need(ItemClass.PLANKS) - 6;
                session.click(windowId, 0, planks, false);
                session.click(windowId, 0, 1, true); // one plank to burn
                session.click(windowId, 0, planks, false);
                charcoalReady = false;
                enter(State.WAIT);
            });
            case WAIT -> {
                if (charcoalReady) {
                    session.quickMove(windowId, 0, 2);
                    session.closeContainer(windowId); // its ID kept, as for the table: the charcoal arrives through it
                    enter(State.TORCHES);
                } else if (ticksInState > SMELT_TIMEOUT_TICKS) {
                    giveUp("no charcoal came out of the furnace");
                }
            }
            case TORCHES -> settled(() -> {
                int coal = need(ItemClass.COAL);
                session.click(0, 0, coal, false);
                session.click(0, 0, 1, true); // charcoal over
                session.click(0, 0, coal, false);
                int sticks = need(ItemClass.STICK);
                session.click(0, 0, sticks, false);
                session.click(0, 0, 3, true); // a stick: torches
                session.click(0, 0, sticks, false);
                session.quickMove(0, 0, RESULT);
                session.closeContainer(0);
                LOG.info("{} made torches", name);
                finish(0);
            });
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int id, int menuType) -> {
                int expected = state == State.OPEN_TABLE ? CRAFTING_MENU : state == State.OPEN_FURNACE ? FURNACE_MENU : -1;
                if (expected < 0) {
                    return;
                }
                if (menuType != expected) {
                    session.closeContainer(id);
                    giveUp("opened menu type " + menuType + ", not " + expected);
                    return;
                }
                windowId = id;
                windowSlots = menuType == CRAFTING_MENU ? TABLE_SLOTS : FURNACE_SLOTS;
                enter(menuType == CRAFTING_MENU ? State.FURNACE_ITEM : State.SMELT);
            }
            // While a table or furnace is open, the inventory's changes arrive only through its window.
            case ContainerUpdate.Contents(int id, int stateId, var slots, boolean complete) -> {
                if (id == windowId) {
                    for (int slot = 0; slot < slots.size(); slot++) {
                        apply(slot, slots.get(slot));
                    }
                }
            }
            case ContainerUpdate.Slot(int id, int stateId, int slot, var item) -> {
                if (id == windowId) {
                    apply(slot, item);
                }
            }
            case ContainerUpdate.Closed(int id) -> {
                if (id == windowId && state != State.IDLE) {
                    giveUp("the server closed the " + (windowSlots == FURNACE_SLOTS ? "furnace" : "crafting table"));
                }
            }
        }
    }

    private void apply(int slot, Optional<ItemSlot> item) {
        if (windowSlots == FURNACE_SLOTS && slot == 2) {
            charcoalReady = item.map(i -> ItemClassRegistry.classOf(i.itemId()).equals(Optional.of(ItemClass.COAL))).orElse(false);
        } else if (slot >= windowSlots) {
            inventory.setWindowSlot(slot, windowSlots, item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null));
        }
    }

    /** Opens the block placed at {@code at} once it's there; places again beside it if it didn't land. */
    private void open(WorldView world, BlockPos at, State placeAgain, String what) {
        if (ticksInState == 0) {
            if (!world.isSolid(at.x(), at.y(), at.z())) {
                blocked.add(at);
                if (blocked.size() >= 6) {
                    giveUp("no cell around would take the " + what);
                } else {
                    enter(placeAgain);
                }
                return;
            }
            session.useBlock(at.x(), at.y(), at.z());
        } else if (ticksInState > OPEN_TIMEOUT_TICKS) {
            giveUp("the " + what + " at " + at + " didn't open");
        }
    }

    /** Puts the item in {@code slot} in hand through a hotbar slot with no weapon or tool. */
    private void hold(int slot) {
        int hotbar = 8;
        for (; hotbar > 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON && held.get() != ItemClass.PICKAXE) {
                break;
            }
        }
        if (previousHotbar < 0) {
            previousHotbar = inventory.heldHotbarSlot();
        }
        if (slot != Inventory.HOTBAR_FIRST + hotbar) {
            session.swapWithHotbar(0, 0, slot, hotbar);
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }

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
            throw new IllegalStateException("no " + itemClass + " in the inventory - " + inventory.summary());
        }
        return slot;
    }

    private void giveUp(String why) {
        LOG.warn("{} gives up making torches ({}) - {}", name, state, why);
        failure = "can't make torches: " + why;
        if (windowId >= 0) {
            session.closeContainer(windowId);
        }
        session.closeContainer(0);
        finish(COOLDOWN_TICKS);
    }

    private void finish(int cooldownTicks) {
        windowId = -1;
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        cooldown = cooldownTicks;
        enter(State.IDLE);
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }
}
