package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan.Batch;
import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan.Session;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A soldier making what it wants from what it carries: a {@link CraftPlan} run as an expect
 * {@link Script}. Each batch is made in rounds - as many crafts at once as the stacks allow - and
 * each round is confirmed by more of the thing in the inventory, not by time passing. A table or
 * furnace already standing within reach is used, whoever put it there; one is placed only when
 * none is.
 *
 * <p>It runs under two guards. <b>Still wanted</b>: what it was asked for hasn't changed. <b>No
 * threat</b>: a hostile near interrupts it - the windows close and the soldier is free to fight
 * with what it has - except when it has no weapon, a weapon is part of the plan, and the table is
 * open: then the weapon is a click away, and it finishes it first. An interruption isn't a fault:
 * there's no backoff, and the next run picks up where this one stopped, since a fresh plan counts
 * what's already made - and what it left smelting in a furnace still in reach, which the next run
 * collects first: a furnace keeps smelting with nobody at it. A fault - an answer that doesn't come - fails loudly and backs off
 * {@link #COOLDOWN_TICKS}.
 *
 * <p>Every click goes out with a stale state ID, so the server answers with the whole window and
 * {@link Inventory} stays exact. Held items go through a hotbar slot with no weapon or tool, so the
 * soldier stays armed. Driven by the soldier's tick and its connection's container updates;
 * synchronized because those are two threads.
 */
public final class Crafter {

    private static final Logger LOG = LogManager.getLogger(Crafter.class);

    /** The server answers a click, a placement or an open within a tick or two; this is the margin. */
    static final int ANSWER_TICKS = 40;
    /** One smelt takes 200 ticks; the margin covers the furnace lighting its fuel. */
    static final int SMELT_TICKS = 300;
    static final int COOLDOWN_TICKS = 1200;
    /** Menu registry IDs: {@code minecraft:crafting} and {@code minecraft:furnace}. */
    static final int CRAFTING_MENU = 12;
    static final int FURNACE_MENU = 14;
    /** Slots before the inventory's in each window: a table's result and 3x3 grid; a furnace's in, fuel, out. */
    static final int TABLE_SLOTS = 10;
    static final int FURNACE_SLOTS = 3;
    private static final int RESULT = 0;
    private static final int FURNACE_IN = 0;
    private static final int FURNACE_FUEL = 1;
    private static final int FURNACE_OUT = 2;
    private static final int MAX_STACK = 64;
    /** How far a player can use a block from, eye to block centre (the player's block_interaction_range, 4.5). */
    static final double REACH = 4.5;
    private static final double EYE_HEIGHT = 1.62;
    /** A player's body is 0.6 wide: the server won't place a block where it overlaps. */
    private static final double HALF_WIDTH = 0.3;

    /** How a tick left things. */
    public enum Outcome { IDLE, WORKING, MADE, FAILED, INTERRUPTED }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private Script script;
    private CraftPlan plan;
    private Map<Recipe, Integer> wanted = Map.of();
    private Map<Recipe, Integer> asked = Map.of();
    private boolean threatened;
    private int cooldown;
    private String failure;
    private Position soldier;
    private WorldView world;
    /** Where a table or furnace was last tried; one that didn't land isn't tried again. */
    private BlockPos placing;
    private final Set<BlockPos> blocked = new HashSet<>();
    private int windowId = -1;
    private int windowSlots;
    private int openedMenu = -1;
    private boolean contentsSeen;
    @SuppressWarnings("unchecked")
    private final Optional<ItemSlot>[] furnace = new Optional[FURNACE_SLOTS];
    private int previousHotbar = -1;
    /** The furnace whose window is open. */
    private BlockPos furnaceAt;
    /** What it left smelting, by furnace: made by now or soon, though not yet in the inventory. */
    private final Map<BlockPos, Map<ItemClass, Integer>> smelting = new HashMap<>();

    public Crafter(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        Arrays.fill(furnace, Optional.empty());
    }

    public synchronized boolean active() {
        return script != null;
    }

    /** Why the last run failed, while the soldier waits out its cooldown. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** What the run under way makes, empty when none is. */
    public synchronized Map<Recipe, Integer> making() {
        return script == null ? Map.of() : wanted;
    }

    /** The plan for having {@code wanted}, from what {@code inventory} carries and the stations within reach of {@code soldier}. */
    public static CraftPlan plan(Map<Recipe, Integer> wanted, Inventory inventory, WorldView world, Position soldier) {
        return CraftPlan.of(wanted, Stock.of(inventory), stationsInReach(world, soldier));
    }

    private static Set<Workstation> stationsInReach(WorldView world, Position soldier) {
        Set<Workstation> inReach = EnumSet.noneOf(Workstation.class);
        for (Workstation station : Workstation.values()) {
            if (inReach(world, soldier, station).isPresent()) {
                inReach.add(station);
            }
        }
        return inReach;
    }

    /**
     * Each tick: starts making {@code wanted} - at least that many of what each recipe makes - when it can and
     * isn't {@code threatened}, and advances the run under way. Empty {@code wanted} asks for nothing.
     */
    public synchronized Outcome tick(Position soldier, WorldView world, Map<Recipe, Integer> wanted, boolean threatened) {
        this.soldier = soldier;
        this.world = world;
        this.asked = wanted;
        this.threatened = threatened;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (wanted.isEmpty() || threatened || cooldown > 0) {
                return Outcome.IDLE;
            }
            // What it left smelting in a furnace in reach counts as made: collected first.
            Map<BlockPos, Map<ItemClass, Integer>> collecting = new LinkedHashMap<>();
            smelting.forEach((at, items) -> {
                if (withinReach(soldier, at) && world.workstation(at.x(), at.y(), at.z()).equals(Optional.of(Workstation.FURNACE))) {
                    collecting.put(at, items);
                }
            });
            Map<ItemClass, Integer> collected = new EnumMap<>(ItemClass.class);
            collecting.values().forEach(items -> items.forEach((itemClass, count) -> collected.merge(itemClass, count, Integer::sum)));
            CraftPlan next = CraftPlan.of(wanted, Stock.of(inventory).plus(collected), stationsInReach(world, soldier));
            List<Step> steps = new ArrayList<>();
            collecting.keySet().forEach(at -> {
                steps.add(open(Workstation.FURNACE, FURNACE_MENU, Optional.of(at)));
                steps.add(collect(at));
            });
            if (next.feasible()) {
                steps.addAll(steps(next));
            }
            if (steps.isEmpty()) {
                return Outcome.IDLE;
            }
            plan = next;
            this.wanted = Map.copyOf(wanted);
            failure = null;
            LOG.info("{} crafts {} - {}{} - {}", name, describe(wanted), collecting.isEmpty() ? "" : "collecting " + collecting + " first, then ",
                    next.feasible() ? describe(next) : "short of " + next.short_(), inventory.summary());
            script = new Script(name, steps, List.of(
                    new Guard("no longer wanted", () -> this.asked.equals(this.wanted)),
                    new Guard("a hostile near", () -> !this.threatened || finishingWeapon())));
        }
        return switch (script.tick()) {
            case Script.Status.Running() -> Outcome.WORKING;
            case Script.Status.Done() -> {
                LOG.info("{} made {} - {}", name, describe(this.wanted), inventory.summary());
                blocked.clear();
                end(0);
                yield Outcome.MADE;
            }
            case Script.Status.Interrupted(String step, String guard) -> {
                end(0);
                yield Outcome.INTERRUPTED;
            }
            case Script.Status.Failed(String step, String reason) -> {
                LOG.warn("{} gives up crafting {} at '{}' - {} - {}", name, describe(this.wanted), step, reason, inventory.summary());
                failure = "can't make " + describe(this.wanted) + ": " + step + ": " + reason;
                if (step.startsWith("place") && placing != null) {
                    blocked.add(placing);
                }
                end(COOLDOWN_TICKS);
                yield Outcome.FAILED;
            }
        };
    }

    /** A weapon it lacks, at an open table: finished before it turns to fight. */
    private boolean finishingWeapon() {
        return plan.makes(ItemClass.WEAPON) && inventory.count(ItemClass.WEAPON) == 0 && openedMenu == CRAFTING_MENU && windowId >= 0;
    }

    private List<Step> steps(CraftPlan plan) {
        List<Step> steps = new ArrayList<>();
        for (Session visit : plan.sessions()) {
            switch (visit.station()) {
                case GRID -> visit.batches().forEach(b -> steps.add(craft(b, false)));
                case TABLE -> {
                    steps.add(place(Workstation.CRAFTING_TABLE, ItemClass.CRAFTING_TABLE));
                    steps.add(open(Workstation.CRAFTING_TABLE, CRAFTING_MENU, Optional.empty()));
                    visit.batches().forEach(b -> steps.add(craft(b, true)));
                }
                case FURNACE -> {
                    steps.add(place(Workstation.FURNACE, ItemClass.FURNACE));
                    steps.add(open(Workstation.FURNACE, FURNACE_MENU, Optional.empty()));
                    visit.batches().forEach(b -> steps.add(smelt(b)));
                }
            }
        }
        return steps;
    }

    /** A batch in rounds, each as many crafts at once as the stacks allow, each confirmed by more of what it makes. */
    private Step craft(Batch batch, boolean atTable) {
        Recipe recipe = batch.recipe();
        int[] made = {0};
        return Step.untilEach(recipe.name() + " x" + batch.yield(), () -> made[0] >= batch.yield(), () -> {
            int window;
            if (atTable) {
                window = windowId;
            } else {
                close(); // the grid's clicks go nowhere while another window is open
                window = 0;
            }
            int crafts = (batch.yield() - made[0] + recipe.yield() - 1) / recipe.yield();
            int before = inventory.count(recipe.makes());
            lay(window, atTable ? 3 : 2, recipe, crafts);
            session.quickMove(window, 0, RESULT);
            if (!atTable) {
                session.closeContainer(0); // anything left in the grid back to the inventory
            }
            return new Expect("more " + recipe.makes() + " in the inventory", () -> {
                int now = inventory.count(recipe.makes());
                if (now > before) {
                    made[0] += now - before;
                    return true;
                }
                return false;
            });
        }, ANSWER_TICKS);
    }

    /**
     * Lays {@code crafts} crafts' worth of {@code recipe} into the grid of {@code window}, as many as
     * one stack per cell allows: a cell holds one kind of item, so an oak plank can't go on a birch one.
     */
    private void lay(int window, int width, Recipe recipe, int crafts) {
        Map<Integer, ItemClass> cells = recipe.cells(width);
        int times = Math.min(crafts, MAX_STACK);
        Map<Integer, Integer> source = assign(cells, times);
        while (source == null && --times > 0) {
            source = assign(cells, times);
        }
        if (source == null) {
            throw new IllegalStateException("not enough in any one stack to lay out a " + recipe.name() + " - " + inventory.summary());
        }
        Map<Integer, List<Integer>> bySource = new LinkedHashMap<>();
        source.forEach((cell, slot) -> bySource.computeIfAbsent(slot, s -> new ArrayList<>()).add(cell));
        int each = times;
        bySource.forEach((slot, into) -> {
            int windowSlot = window == 0 ? slot : slot - Inventory.MAIN_FIRST + windowSlots;
            session.click(window, 0, windowSlot, false); // the stack onto the cursor,
            for (int cell : into) {
                for (int i = 0; i < each; i++) {
                    session.click(window, 0, cell, true); // one at a time into each of its cells,
                }
            }
            session.click(window, 0, windowSlot, false); // the rest back
        });
    }

    /** For each cell, an inventory slot of its class with {@code each} to spare for it - or null if they won't go round. */
    private Map<Integer, Integer> assign(Map<Integer, ItemClass> cells, int each) {
        Map<Integer, Integer> left = new HashMap<>();
        Map<Integer, Integer> source = new LinkedHashMap<>();
        for (Map.Entry<Integer, ItemClass> cell : cells.entrySet()) {
            Integer chosen = null;
            for (int slot = Inventory.MAIN_FIRST; slot < Inventory.HOTBAR_FIRST + 9; slot++) {
                if (ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(cell.getValue()))
                        && left.computeIfAbsent(slot, inventory::countAt) >= each
                        && (chosen == null || source.containsValue(slot))) {
                    chosen = slot;
                }
            }
            if (chosen == null) {
                return null;
            }
            source.put(cell.getKey(), chosen);
            left.merge(chosen, -each, Integer::sum);
        }
        return source;
    }

    /** Smelting a batch in one furnace: loaded, each smelt taken out as it's done. */
    private Step smelt(Batch batch) {
        Recipe recipe = batch.recipe();
        int[] made = {0};
        return Step.untilEach(recipe.name() + " x" + batch.yield(), () -> made[0] >= batch.yield(), () -> {
            if (furnace[FURNACE_OUT].isPresent()) {
                return takeOut(recipe.makes(), taken -> made[0] += taken);
            }
            if (furnace[FURNACE_IN].isEmpty()) {
                // What's loaded counts as smelting, for the soldier to come back for if it's called away.
                int loaded = load(recipe, batch.yield() - made[0]);
                smelting.computeIfAbsent(furnaceAt, a -> new EnumMap<>(ItemClass.class)).merge(recipe.makes(), loaded, Integer::sum);
                return new Expect("the furnace loaded", () -> furnace[FURNACE_IN].isPresent(), ANSWER_TICKS);
            }
            return smeltDone();
        }, SMELT_TICKS);
    }

    /** Collecting what was left smelting in the furnace at {@code at}: each smelt taken out as it's done. */
    private Step collect(BlockPos at) {
        return Step.untilEach("collect from the furnace at " + at, () -> !smelting.containsKey(at), () -> {
            Map<ItemClass, Integer> items = smelting.get(at);
            if (furnace[FURNACE_OUT].isPresent()) {
                return takeOut(items.keySet().iterator().next(), taken -> { });
            }
            if (furnace[FURNACE_IN].isEmpty()) {
                smelting.remove(at);
                throw new IllegalStateException("the furnace at " + at + " holds none of the " + describeItems(items) + " left smelting in it");
            }
            return smeltDone();
        }, SMELT_TICKS);
    }

    /** The furnace's result into the inventory; what came out is no longer smelting. */
    private Expect takeOut(ItemClass makes, java.util.function.IntConsumer onTaken) {
        int before = inventory.count(makes);
        BlockPos at = furnaceAt;
        session.quickMove(windowId, 0, FURNACE_OUT);
        return new Expect(makes + " out of the furnace", () -> {
            int now = inventory.count(makes);
            if (now <= before) {
                return false;
            }
            int taken = now - before;
            onTaken.accept(taken);
            Map<ItemClass, Integer> items = smelting.get(at);
            if (items != null) {
                items.computeIfPresent(makes, (c, n) -> n > taken ? n - taken : null);
                if (items.isEmpty()) {
                    smelting.remove(at);
                }
            }
            return true;
        }, ANSWER_TICKS);
    }

    /** The next smelt done: something in the result slot, or less left to smelt. */
    private Expect smeltDone() {
        int left = furnace[FURNACE_IN].get().count();
        return new Expect("a smelt done", () -> furnace[FURNACE_OUT].isPresent() || furnace[FURNACE_IN].map(i -> i.count() < left).orElse(true),
                SMELT_TICKS);
    }

    /** Up to {@code smelts} of the recipe's ingredient into the furnace, and planks enough to burn for them: how many went in. */
    private int load(Recipe recipe, int smelts) {
        ItemClass ingredient = recipe.key().values().iterator().next();
        int from = biggest(ingredient, -1);
        int count = Math.min(Math.min(smelts, MAX_STACK), inventory.countAt(from));
        int window = windowId;
        session.click(window, 0, from - Inventory.MAIN_FIRST + windowSlots, false);
        for (int i = 0; i < count; i++) {
            session.click(window, 0, FURNACE_IN, true);
        }
        session.click(window, 0, from - Inventory.MAIN_FIRST + windowSlots, false);
        // Fuel: planks of the kind already burning, topped up - or what's there taken out first.
        Optional<ItemSlot> burning = furnace[FURNACE_FUEL];
        int fuel = Recipe.fuelFor(count) - burning.map(ItemSlot::count).orElse(0);
        if (fuel <= 0) {
            return count;
        }
        int planks = biggest(ItemClass.PLANKS, burning.map(ItemSlot::itemId).orElse(-1));
        if (planks < 0) {
            session.quickMove(window, 0, FURNACE_FUEL);
            planks = biggest(ItemClass.PLANKS, -1);
            fuel = Recipe.fuelFor(count);
        }
        if (planks < 0 || inventory.countAt(planks) < fuel) {
            throw new IllegalStateException("not enough planks to burn for " + count + " smelts - " + inventory.summary());
        }
        session.click(window, 0, planks - Inventory.MAIN_FIRST + windowSlots, false);
        for (int i = 0; i < fuel; i++) {
            session.click(window, 0, FURNACE_FUEL, true);
        }
        session.click(window, 0, planks - Inventory.MAIN_FIRST + windowSlots, false);
        return count;
    }

    /** The inventory slot with the most of {@code itemClass} - of item {@code itemId}, unless -1 - or -1. */
    private int biggest(ItemClass itemClass, int itemId) {
        int best = -1;
        for (int slot = Inventory.MAIN_FIRST; slot < Inventory.HOTBAR_FIRST + 9; slot++) {
            if (ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(itemClass))
                    && (itemId < 0 || inventory.itemAt(slot) == itemId)
                    && (best < 0 || inventory.countAt(slot) > inventory.countAt(best))) {
                best = slot;
            }
        }
        return best;
    }

    /** A table or furnace put down beside the soldier - skipped when one is already within reach. */
    private Step place(Workstation station, ItemClass item) {
        String label = label(station);
        return new Step("place a " + label, () -> inReach(world, soldier, station).isPresent(), () -> {
            close();
            placing = placement(soldier, world, blocked);
            hold(need(item));
            session.useBlock(placing.x(), placing.y() - 1, placing.z()); // the top of the ground there
        }, new Expect("a " + label + " in the world", () -> placed(station)), ANSWER_TICKS);
    }

    private boolean placed(Workstation station) {
        return world.workstation(placing.x(), placing.y(), placing.z()).equals(Optional.of(station));
    }

    /** The table or furnace {@code which}, or the nearest in reach, opened: confirmed by its window and contents. */
    private Step open(Workstation station, int menu, Optional<BlockPos> which) {
        String label = label(station);
        return new Step("open the " + label, () -> {
            close();
            BlockPos at = which.or(() -> inReach(world, soldier, station))
                    .orElseThrow(() -> new IllegalStateException("no " + label + " within reach of " + soldier));
            furnaceAt = station == Workstation.FURNACE ? at : null;
            openedMenu = -1;
            contentsSeen = false;
            session.useBlock(at.x(), at.y(), at.z());
        }, new Expect("the " + label + "'s window", () -> {
            if (openedMenu >= 0 && openedMenu != menu) {
                throw new IllegalStateException("menu type " + openedMenu + " opened, not " + menu);
            }
            return openedMenu == menu && contentsSeen;
        }), ANSWER_TICKS);
    }

    public synchronized void onContainer(ContainerUpdate update) {
        switch (update) {
            case ContainerUpdate.Opened(int id, int menuType) -> {
                if (script != null) {
                    windowId = id;
                    openedMenu = menuType;
                    windowSlots = menuType == FURNACE_MENU ? FURNACE_SLOTS : TABLE_SLOTS;
                    contentsSeen = false;
                    Arrays.fill(furnace, Optional.empty());
                }
            }
            // While a table or furnace is open, the inventory's changes arrive only through its window.
            case ContainerUpdate.Contents(int id, int stateId, var slots, boolean complete) -> {
                if (id == windowId) {
                    for (int slot = 0; slot < slots.size(); slot++) {
                        apply(slot, slots.get(slot));
                    }
                    contentsSeen = true;
                }
            }
            case ContainerUpdate.Slot(int id, int stateId, int slot, var item) -> {
                if (id == windowId) {
                    apply(slot, item);
                }
            }
            case ContainerUpdate.Closed(int id) -> {
                if (id == windowId && script != null) {
                    windowId = -1;
                    openedMenu = -1;
                    script.cancel("the server closed the window");
                }
            }
        }
    }

    private void apply(int slot, Optional<ItemSlot> item) {
        if (windowSlots == FURNACE_SLOTS && slot < FURNACE_SLOTS) {
            furnace[slot] = item;
        } else if (slot >= windowSlots) {
            inventory.setWindowSlot(slot, windowSlots, item.map(i -> new int[]{i.itemId(), i.count()}).orElse(null));
        }
    }

    /** Whether the centre of {@code cell} is within reach of the soldier's eyes. */
    private static boolean withinReach(Position soldier, BlockPos cell) {
        double ex = cell.x() + 0.5 - soldier.x();
        double ey = cell.y() + 0.5 - (soldier.y() + EYE_HEIGHT);
        double ez = cell.z() + 0.5 - soldier.z();
        return ex * ex + ey * ey + ez * ez <= REACH * REACH;
    }

    /** The nearest {@code station} block whose centre is within reach of the soldier's eyes. */
    public static Optional<BlockPos> inReach(WorldView world, Position soldier, Workstation station) {
        BlockPos feet = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        int r = (int) Math.ceil(REACH);
        BlockPos best = null;
        double bestDistance = REACH * REACH;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos cell = feet.offset(dx, dy, dz);
                    double ex = cell.x() + 0.5 - soldier.x();
                    double ey = cell.y() + 0.5 - (soldier.y() + EYE_HEIGHT);
                    double ez = cell.z() + 0.5 - soldier.z();
                    double distance = ex * ex + ey * ey + ez * ez;
                    if (distance <= bestDistance && world.workstation(cell.x(), cell.y(), cell.z()).equals(Optional.of(station))) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Where to put a table or furnace: a free cell beside the soldier with ground under it, clear of
     * its body and anyone else's - the server won't place a block into a squadmate.
     */
    public static BlockPos placement(Position soldier, WorldView world, Set<BlockPos> blocked) {
        BlockPos at = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        List<Position> bodies = world.bodiesNear(soldier, 3);
        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            BlockPos cell = at.offset(side[0], 0, side[1]);
            boolean underBody = soldier.x() + HALF_WIDTH > cell.x() && soldier.x() - HALF_WIDTH < cell.x() + 1
                    && soldier.z() + HALF_WIDTH > cell.z() && soldier.z() - HALF_WIDTH < cell.z() + 1;
            boolean someoneThere = bodies.stream().anyMatch(b -> b.x() + HALF_WIDTH > cell.x() && b.x() - HALF_WIDTH < cell.x() + 1
                    && b.z() + HALF_WIDTH > cell.z() && b.z() - HALF_WIDTH < cell.z() + 1 && b.y() < cell.y() + 1 && b.y() + 1.8 > cell.y());
            if (!underBody && !someoneThere && !blocked.contains(cell) && world.isSolid(cell.x(), cell.y() - 1, cell.z())
                    && !world.isSolid(cell.x(), cell.y(), cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z())) {
                return cell;
            }
        }
        throw new IllegalStateException("no free cell beside " + at + " to put a block down - walls or squadmates all round");
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

    private void close() {
        if (windowId >= 0) {
            session.closeContainer(windowId);
            windowId = -1;
            openedMenu = -1;
        }
    }

    private void end(int cooldownTicks) {
        close();
        session.closeContainer(0); // anything left in the 2x2 grid or on the cursor goes back
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        script = null;
        plan = null;
        placing = null;
        cooldown = cooldownTicks;
    }

    private static String label(Workstation station) {
        return station == Workstation.CRAFTING_TABLE ? "crafting table" : "furnace";
    }

    static String describe(Map<Recipe, Integer> wanted) {
        return wanted.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().name()).collect(Collectors.joining(", "));
    }

    private static String describeItems(Map<ItemClass, Integer> items) {
        return items.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", "));
    }

    static String describe(CraftPlan plan) {
        return plan.sessions().stream().map(s -> s.station() + "[" + s.batches().stream()
                .map(b -> b.recipe().name() + " x" + b.yield()).collect(Collectors.joining(", ")) + "]").collect(Collectors.joining(" -> "));
    }
}
