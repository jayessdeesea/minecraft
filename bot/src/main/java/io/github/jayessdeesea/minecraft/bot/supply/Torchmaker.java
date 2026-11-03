package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier making torches for its squad's patrol base (docs/military-doctrine.md §14), from what
 * a squad carries: logs and cobblestone. Planks and sticks in the inventory's 2x2 grid; a crafting
 * table placed beside it; a furnace (8 cobblestone) crafted at the table and placed; a log smelted
 * into charcoal with a plank for fuel; then charcoal over a stick, in the 2x2 grid again: 4
 * torches. The table and furnace stay where they're put - a camp's kit.
 *
 * <p>An expect {@link Script}: each step's clicks go out with a stale state ID, so the server
 * answers with the whole window and {@link Inventory} stays exact, and the step waits for that
 * answer to show what it made - planks, a table in the world, the furnace's window - within a
 * deadline. A missed deadline, or an answer that rules the step out, fails the run loudly with
 * the step and what was expected; the grid goes back and the soldier waits {@link #COOLDOWN_TICKS}
 * before trying again. A cell that wouldn't take a table or furnace isn't tried again. Held items
 * go through a hotbar slot with no weapon or tool, so the soldier stays armed. Driven by the
 * soldier's tick and its connection's container updates; synchronized because those are two
 * threads.
 */
public final class Torchmaker {

    private static final Logger LOG = LogManager.getLogger(Torchmaker.class);

    /** The server answers a click, a placement or an open within a tick or two; this is the margin. */
    static final int ANSWER_TICKS = 40;
    /** A log smelts in 200 ticks; the margin covers the furnace lighting its fuel. */
    static final int SMELT_TICKS = 400;
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

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private Script script;
    private int cooldown;
    private String failure;
    private Position soldier;
    private WorldView world;
    private BlockPos table;
    private BlockPos furnace;
    /** Cells a table or furnace wouldn't go into, kept across attempts. */
    private final Set<BlockPos> blocked = new HashSet<>();
    private int windowId = -1;
    private int windowSlots;
    private int openedMenu = -1;
    private final Optional<ItemClass>[] furnaceSlots = emptyFurnace();
    private int previousHotbar = -1;

    public Torchmaker(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return script != null;
    }

    /** Why the last attempt failed, while the soldier waits out its cooldown. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Logs it needs before it can start, given what it already carries. */
    public static int logsNeeded(Inventory inventory) {
        int short_ = Math.max(0, planksWanted(inventory) - inventory.count(ItemClass.PLANKS));
        return 1 + (short_ + 3) / 4; // one to smelt, the rest at 4 planks a log
    }

    /** Whether it carries what torches take: the logs, and a furnace's cobblestone or a furnace. */
    public static boolean canMake(Inventory inventory) {
        return inventory.count(ItemClass.LOG) >= logsNeeded(inventory)
                && (has(inventory, ItemClass.FURNACE) || inventory.count(ItemClass.COBBLESTONE) >= FURNACE_COBBLESTONE);
    }

    /** Each tick: starts when {@code wanted} and it can, advances, and abandons a run no longer wanted. */
    public synchronized void tick(Position soldier, WorldView world, boolean wanted) {
        this.soldier = soldier;
        this.world = world;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            // Not if it carries torches already: its own tick can come round before the squad loop
            // has seen the last batch.
            if (wanted && cooldown == 0 && canMake(inventory) && inventory.count(ItemClass.TORCH) == 0) {
                LOG.info("{} makes torches - {}", name, inventory.summary());
                failure = null;
                script = new Script(name, steps());
            }
            return;
        }
        Script.Status status = wanted ? script.tick() : script.cancel("no longer wanted");
        switch (status) {
            case Script.Status.Running() -> {
            }
            case Script.Status.Done() -> {
                LOG.info("{} made torches - {}", name, inventory.summary());
                blocked.clear();
                finish(0);
            }
            case Script.Status.Failed(String step, String reason) -> giveUp(step, reason);
        }
    }

    private List<Step> steps() {
        int planks = planksWanted(inventory);
        return List.of(
                new Step("planks", () -> inventory.count(ItemClass.PLANKS) >= planks, () -> {
                    int logs = (planks - inventory.count(ItemClass.PLANKS) + 3) / 4;
                    int log = need(ItemClass.LOG);
                    session.click(0, 0, log, false);
                    for (int i = 0; i < logs; i++) {
                        session.click(0, 0, 1, true);
                    }
                    session.click(0, 0, log, false);
                    session.quickMove(0, 0, RESULT);
                }, new Expect(planks + " planks in the inventory", () -> inventory.count(ItemClass.PLANKS) >= planks), ANSWER_TICKS),
                new Step("sticks", () -> has(inventory, ItemClass.STICK), () -> {
                    int plank = need(ItemClass.PLANKS);
                    session.click(0, 0, plank, false);
                    session.click(0, 0, 1, true); // a plank over
                    session.click(0, 0, 3, true); // a plank: sticks
                    session.click(0, 0, plank, false);
                    session.quickMove(0, 0, RESULT);
                }, new Expect("sticks in the inventory", () -> has(inventory, ItemClass.STICK)), ANSWER_TICKS),
                new Step("craft a crafting table", () -> has(inventory, ItemClass.CRAFTING_TABLE) || has(inventory, ItemClass.FURNACE), () -> {
                    int plank = need(ItemClass.PLANKS);
                    session.click(0, 0, plank, false);
                    for (int grid = 1; grid <= 4; grid++) {
                        session.click(0, 0, grid, true);
                    }
                    session.click(0, 0, plank, false);
                    session.quickMove(0, 0, RESULT);
                    session.closeContainer(0); // anything left in the grid back to the inventory
                }, new Expect("a crafting table in the inventory", () -> has(inventory, ItemClass.CRAFTING_TABLE)), ANSWER_TICKS),
                new Step("place the crafting table", () -> has(inventory, ItemClass.FURNACE), () -> {
                    table = Woodcraft.placement(soldier, world, blocked);
                    hold(need(ItemClass.CRAFTING_TABLE));
                    session.useBlock(table.x(), table.y() - 1, table.z());
                }, new Expect("a crafting table in the world", () -> placed(table)), ANSWER_TICKS),
                new Step("open the crafting table", () -> has(inventory, ItemClass.FURNACE), () -> open(table),
                        new Expect("the crafting table's window", () -> opened(CRAFTING_MENU)), ANSWER_TICKS),
                new Step("craft a furnace", () -> has(inventory, ItemClass.FURNACE), () -> {
                    int cobblestone = need(ItemClass.COBBLESTONE) + 1; // the table's window puts inventory after its 10 slots
                    session.click(windowId, 0, cobblestone, false);
                    for (int slot : FURNACE_PATTERN) {
                        session.click(windowId, 0, slot, true);
                    }
                    session.click(windowId, 0, cobblestone, false);
                    session.quickMove(windowId, 0, RESULT);
                }, new Expect("a furnace in the inventory", () -> has(inventory, ItemClass.FURNACE)), ANSWER_TICKS),
                new Step("place the furnace", () -> {
                    close();
                    if (table != null) {
                        blocked.add(table);
                    }
                    furnace = Woodcraft.placement(soldier, world, blocked);
                    hold(need(ItemClass.FURNACE));
                    session.useBlock(furnace.x(), furnace.y() - 1, furnace.z());
                }, new Expect("a furnace in the world", () -> placed(furnace)), ANSWER_TICKS),
                new Step("open the furnace", () -> open(furnace),
                        new Expect("the furnace's window", () -> opened(FURNACE_MENU)), ANSWER_TICKS),
                new Step("load the furnace", () -> {
                    // The furnace's window puts inventory slot 9 at 3: window slot = inventory slot - 6.
                    int log = need(ItemClass.LOG) - 6;
                    session.click(windowId, 0, log, false);
                    session.click(windowId, 0, 0, true); // one log to smelt
                    session.click(windowId, 0, log, false);
                    int plank = need(ItemClass.PLANKS) - 6;
                    session.click(windowId, 0, plank, false);
                    session.click(windowId, 0, 1, true); // one plank to burn
                    session.click(windowId, 0, plank, false);
                }, new Expect("a log in the furnace", () -> furnaceSlots[0].equals(Optional.of(ItemClass.LOG))
                        || furnaceSlots[2].equals(Optional.of(ItemClass.COAL))), ANSWER_TICKS),
                new Step("smelt", () -> { },
                        new Expect("charcoal in the furnace", () -> furnaceSlots[2].equals(Optional.of(ItemClass.COAL))), SMELT_TICKS),
                new Step("take the charcoal", () -> session.quickMove(windowId, 0, 2),
                        new Expect("charcoal in the inventory", () -> has(inventory, ItemClass.COAL)), ANSWER_TICKS),
                new Step("craft torches", () -> {
                    close();
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
                }, new Expect("torches in the inventory", () -> has(inventory, ItemClass.TORCH)), ANSWER_TICKS));
    }

    public synchronized void onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int id, int menuType) -> {
                if (script != null) {
                    windowId = id;
                    openedMenu = menuType;
                    windowSlots = menuType == FURNACE_MENU ? FURNACE_SLOTS : TABLE_SLOTS;
                    Arrays.fill(furnaceSlots, Optional.empty());
                }
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
                if (id == windowId && script != null) {
                    script.cancel("the server closed the " + (windowSlots == FURNACE_SLOTS ? "furnace" : "crafting table"));
                }
            }
        }
    }

    private void apply(int slot, Optional<ItemSlot> item) {
        if (windowSlots == FURNACE_SLOTS && slot < FURNACE_SLOTS) {
            furnaceSlots[slot] = item.flatMap(i -> ItemClassRegistry.classOf(i.itemId()));
        } else if (slot >= windowSlots) {
            inventory.setWindowSlot(slot, windowSlots, item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null));
        }
    }

    /** Uses the block at {@code at}, after any window still open is closed. */
    private void open(BlockPos at) {
        close();
        openedMenu = -1;
        session.useBlock(at.x(), at.y(), at.z());
    }

    /** Whether the window it asked for is open; throws if a different one opened. */
    private boolean opened(int menu) {
        if (openedMenu >= 0 && openedMenu != menu) {
            throw new IllegalStateException("menu type " + openedMenu + " opened, not " + menu);
        }
        return openedMenu == menu;
    }

    /** Whether the block placed at {@code at} is there. One that never lands fails the step; {@link #giveUp} marks the cell. */
    private boolean placed(BlockPos at) {
        return world.isSolid(at.x(), at.y(), at.z());
    }

    private void close() {
        if (windowId >= 0) {
            session.closeContainer(windowId);
            windowId = -1;
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

    private int need(ItemClass itemClass) {
        int slot = inventory.slotOf(itemClass);
        if (slot < 0) {
            throw new IllegalStateException("no " + itemClass + " in the inventory - " + inventory.summary());
        }
        return slot;
    }

    private void giveUp(String step, String reason) {
        LOG.warn("{} gives up making torches at '{}' - {} - {}", name, step, reason, inventory.summary());
        failure = "can't make torches: " + step + ": " + reason;
        if (step.equals("place the crafting table") && table != null) {
            blocked.add(table);
        } else if (step.equals("place the furnace") && furnace != null) {
            blocked.add(furnace);
        }
        close();
        session.closeContainer(0);
        finish(COOLDOWN_TICKS);
    }

    private void finish(int cooldownTicks) {
        script = null;
        table = null;
        furnace = null;
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        cooldown = cooldownTicks;
    }

    private static int planksWanted(Inventory inventory) {
        return (has(inventory, ItemClass.CRAFTING_TABLE) || has(inventory, ItemClass.FURNACE) ? 0 : TABLE_PLANKS)
                + (has(inventory, ItemClass.STICK) ? 0 : STICK_PLANKS) + FUEL_PLANKS;
    }

    private static boolean has(Inventory inventory, ItemClass itemClass) {
        return inventory.slotOf(itemClass) >= 0;
    }

    @SuppressWarnings("unchecked")
    private static Optional<ItemClass>[] emptyFurnace() {
        Optional<ItemClass>[] slots = new Optional[FURNACE_SLOTS];
        Arrays.fill(slots, Optional.empty());
        return slots;
    }
}
