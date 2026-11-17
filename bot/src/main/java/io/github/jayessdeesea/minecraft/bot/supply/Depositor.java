package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One soldier carrying gathered materials to the stockpile - the supply point's chest
 * (docs/military-doctrine.md §12): walk to it, open it, shift-click in the material it has a full
 * load of - and the rations it carries beyond its basic load ({@link Subsistence#BASIC_LOAD}), whole
 * stacks while the basic load stays in its pack - close it. Only that: a soldier quarrying stone keeps the logs it carries, because its
 * wooden pickaxe wears out in 59 blocks and the next one is made from them. What the chest holds
 * once it's done is reported ({@code stockpileSeen}), so the chain of command knows the stockpile
 * without anyone counting it by hand. Driven like {@link SupplyRun}, by the soldier's tick and its
 * connection's container updates; synchronized for the same reason.
 *
 * <p>An expect script (docs/architecture.md § Behavior modeling): the chest is open when its contents arrive,
 * and the load is in when the inventory, as the chest's window shows it, holds none of it. A chest
 * too full to take it all fails the run loudly. A trip that stops being wanted on the way - before
 * the chest is open - is interrupted.
 */
public final class Depositor {

    private static final Logger LOG = LogManager.getLogger(Depositor.class);

    static final int WALK_TIMEOUT_TICKS = 600;
    static final int COOLDOWN_TICKS = 200;

    private final String name;
    private final Inventory inventory;
    private final ChestWindow chest;
    private final Consumer<Stockpile> stockpileSeen;

    private Script script;
    private int cooldown;
    /** A stockpile its trips keep failing at - not reached, or not opened - is left longer each time. */
    private final Setbacks<Position> trips = new Setbacks<>(COOLDOWN_TICKS, 60L * COOLDOWN_TICKS);
    private long ticks;
    private Position at;
    private Position soldierAt;
    private boolean wanted;
    private boolean walking;
    private Set<ItemClass> load = Set.of();
    /** The rations a trip leaves in the pack once it has put its surplus in: the inventory's count of food, or fewer. */
    private int foodLeft = Integer.MAX_VALUE;
    /** What a draw takes out, and how many it wants to carry: empty on a trip to put a load in. */
    private Optional<ItemClass> drawing = Optional.empty();
    private int drawTo;

    public Depositor(String name, PlaySession session, Inventory inventory, Consumer<Stockpile> stockpileSeen) {
        this.session = session;
        this.name = name;
        this.inventory = inventory;
        this.chest = new ChestWindow(session, inventory);
        this.stockpileSeen = stockpileSeen;
    }

    /** Whether a trip under way is drawing {@code material} out. */
    public synchronized boolean drawing(ItemClass material) {
        return script != null && drawing.equals(Optional.of(material));
    }

    public synchronized boolean active() {
        return script != null;
    }

    /** Whether a trip under way is to a dump ({@link #tickDump}): {@link #standAt} is the dump's cell, not a chest's. */
    public synchronized boolean dumping() {
        return script != null && !dumping.isEmpty();
    }

    private static final String PUT_IN = "put the load in";
    /** The chest its last trip reached and opened, and that wouldn't take the load. */
    private Optional<Position> full = Optional.empty();

    /**
     * The chest that wouldn't take its load, if its last trip there ended so: reached, opened, and
     * the load still in the pack - a full chest. Cleared by a trip that puts its load in.
     */
    public synchronized Optional<Position> full() {
        return full;
    }

    /** Whether a soldier at {@code soldier} reaches the chest at {@code chest} to open it. */
    public static boolean withinReach(Position soldier, Position chest) {
        return ChestWindow.withinReach(soldier, chest);
    }

    /** Where the soldier should go while it carries its load in. */
    /** The chest its trip is to, or was last to. */
    public synchronized Optional<Position> chestAt() {
        return Optional.ofNullable(at);
    }

    /**
     * Where the soldier stands for its trip: by the chest, the whole trip - walking to it, opening it,
     * putting the load in - not only the walk. Live, with the walk over the soldier was steered to its
     * dig's stand mid-trip, walked off, and the load didn't go in, "8.3 blocks off".
     */
    public synchronized Optional<Position> standAt() {
        return script != null ? Optional.of(at) : Optional.empty();
    }

    /**
     * Each tick: starts a trip to {@code stockpile} when {@code wanted} (a full load, nothing near),
     * and advances one under way.
     */
    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return trips.stuck().map(s -> "can't reach the stockpile: " + s);
    }

    public synchronized void tick(Position soldier, Optional<Position> stockpile, boolean wanted) {
        tick(soldier, stockpile, wanted, Set.of());
    }

    /**
     * As {@link #tick(Position, Optional, boolean)}, putting in all it carries of {@code pooled} too -
     * raw ore that's the force's ({@link MetalPool}) - on a trip it makes for them or for its load.
     */
    public synchronized void tick(Position soldier, Optional<Position> stockpile, boolean wanted, Set<ItemClass> pooled) {
        tick(soldier, stockpile, wanted, pooled, inventory -> List.of());
    }

    /**
     * As {@link #tick(Position, Optional, boolean, Set)}, putting in too the slots {@code surplus}
     * finds past the soldier's load for its work (bot.craft.LoadPlan; operator, 2026-11-15: a third
     * sword, a worse pickaxe, an emerald to the depot) - on any trip it makes.
     */
    public synchronized void tick(Position soldier, Optional<Position> stockpile, boolean wanted, Set<ItemClass> pooled,
                                  java.util.function.Function<Inventory, List<Integer>> surplus) {
        if (script == null) {
            this.pooled = Set.copyOf(pooled);
            this.surplus = surplus;
        }
        this.soldierAt = soldier;
        this.wanted = wanted;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (stockpile.isEmpty()) {
            trips.noWork();
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || stockpile.isEmpty() || trips.barred(stockpile.get(), ticks)) {
                return;
            }
            at = stockpile.get();
            LOG.info("{} carries a load to the stockpile at {} - {}", name, at, inventory.summary());
            script = trip();
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                Stockpile seen = seen();
                LOG.info("{} stockpiled its load - the chest holds {} logs, {} cobblestone, {} rations, {} raw iron, {} raw copper", name,
                        seen.logs(), seen.stone(), seen.rations(), seen.rawIron(), seen.rawCopper());
                stockpileSeen.accept(seen);
                trips.worked(at);
                full = Optional.empty();
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                // Where it was, by this routine's last look, against the chest: a soldier within the chest's
                // reach by every other measure failed its walk five times running (backlog/keep-pace.md).
                LOG.warn("{} couldn't stockpile its load: {}: {} - last seen at {}, the chest at {}, {} blocks off", name, failed.step(),
                        failed.reason(), soldierAt, at, soldierAt == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f",
                                Math.sqrt(Math.pow(soldierAt.x() - (Math.floor(at.x()) + 0.5), 2) + Math.pow(soldierAt.y() - Math.floor(at.y()), 2)
                                        + Math.pow(soldierAt.z() - (Math.floor(at.z()) + 0.5), 2))));
                if (failed.step().equals(PUT_IN)) {
                    full = Optional.of(at);
                    // Opened and read, if full: what it holds is the stockpile's too. Live, after a
                    // restart every chest of the depots' rows was found full a failed trip at a time,
                    // and none of it counted toward the stockpile's cap.
                    stockpileSeen.accept(seen());
                }
                trips.failed(at, ticks, failed.step() + ": " + failed.reason());
                finish();
            }
        }
    }

    /**
     * Each tick: starts a trip to {@code stockpile} to draw {@code material} out when {@code wanted} -
     * the stacks of it shift-clicked across until the soldier carries {@code carry} or the chest has
     * no more - and advances one under way. One squad's surplus is another's supply: live, masons
     * stood idle for want of blocks while a soldier of the other squad carried 627 cobblestone.
     */
    private final PlaySession session;
    /** What a dump trip under way leaves: {@link #tickDump}. */
    private Set<ItemClass> dumping = Set.of();
    /** How near the dump its surplus is thrown down. */
    static final double DUMP_REACH = 1.5;
    /** What of each surplus kind a dump leaves on the soldier: one stack, for steps and plugs. */
    public static final int KEEP = 64;

    /**
     * Each tick: leaves {@code surplus} - what the stockpile has more than enough of - at {@code dump}
     * when {@code wanted}, every stack of it thrown down there, not carried to a chest (operator,
     * 2026-11-16: past 4,096 of cobblestone or dirt in a stockpile, drop it and leave it). A soldier
     * picks up what it walks over, so it's left at a dump off the walks, where it despawns.
     */
    public synchronized void tickDump(Position soldier, Optional<Position> dump, Set<ItemClass> surplus, boolean wanted) {
        this.soldierAt = soldier;
        this.wanted = wanted;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || dump.isEmpty() || surplus.stream().allMatch(c -> inventory.count(c) <= KEEP)) {
                return;
            }
            at = dump.get();
            dumping = Set.copyOf(surplus);
            LOG.info("{} leaves its surplus {} at the dump at {} - {}", name, dumping, at, inventory.summary());
            walking = true;
            script = new Script(name, List.of(
                    new Step("walk to the dump", () -> walking = true,
                            new Expect("at the dump", () -> Math.hypot(soldierAt.x() - at.x(), soldierAt.z() - at.z()) <= DUMP_REACH
                                    && Math.abs(soldierAt.y() - at.y()) <= 2), walkTicks()),
                    new Step("leave the surplus", this::throwSurplus,
                            new Expect("the surplus left", () -> dumping.stream().allMatch(c -> inventory.count(c) <= KEEP)),
                            ChestWindow.ANSWER_TICKS)),
                    List.of(new Guard("the dump is no longer wanted", () -> this.wanted || !walking)));
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                LOG.info("{} left its surplus at the dump - {}", name, inventory.summary());
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't leave its surplus at the dump: {}: {}", name, failed.step(), failed.reason());
                finish();
            }
        }
    }

    /** Throws down every stack of what it's dumping but the first of each kind. */
    private void throwSurplus() {
        walking = false;
        Set<ItemClass> kept = new java.util.HashSet<>();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            Optional<ItemClass> kind = inventory.itemAt(slot) >= 0 ? ItemClassRegistry.classOf(inventory.itemAt(slot)) : Optional.empty();
            if (kind.filter(dumping::contains).isPresent() && !kept.add(kind.get())) {
                session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            }
        }
    }

    public synchronized void tickDraw(Position soldier, Optional<Position> stockpile, ItemClass material, int carry, boolean wanted) {
        this.soldierAt = soldier;
        this.wanted = wanted;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (stockpile.isEmpty()) {
            trips.noWork();
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || stockpile.isEmpty() || trips.barred(stockpile.get(), ticks)) {
                return;
            }
            at = stockpile.get();
            drawing = Optional.of(material);
            drawTo = carry;
            LOG.info("{} goes to the stockpile at {} for {} {} - {}", name, at, carry, material, inventory.summary());
            script = drawTrip();
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                Stockpile seen = seen();
                LOG.info("{} drew {} from the stockpile, carrying {} - the chest holds {} logs, {} cobblestone, {} rations", name, material,
                        inventory.count(material), seen.logs(), seen.stone(), seen.rations());
                stockpileSeen.accept(seen);
                trips.worked(at);
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't draw {} from the stockpile: {}: {}", name, material, failed.step(), failed.reason());
                trips.failed(at, ticks, failed.step() + ": " + failed.reason());
                finish();
            }
        }
    }

    /**
     * How long the walk to the stockpile is given: as far as it is, at a walk. Live and in the squad
     * simulation, a soldier 150 blocks off failed every draw at a flat 30 seconds, and never got there.
     */
    private int walkTicks() {
        BlockPos chestAt = BlockPos.containing(at.x(), at.y(), at.z());
        return Digger.walkTicks(soldierAt, chestAt);
    }

    /** Whether a trip under way is to empty a furnace ({@link #tickEmpty}): {@link #standAt} is the furnace's. */
    public synchronized boolean emptying() {
        return script != null && emptyingFurnace;
    }

    private boolean emptyingFurnace;

    /**
     * Each tick: walks to the furnace at {@code furnace} when {@code wanted} and takes out what's in
     * its output and input - smelted, or left smelting by a soldier gone off - so it goes in the
     * chests, not stands there (docs/conventions.md, check the outcome: live (field300), 4 copper
     * ingots stood in the camp's furnace and 4 in an outpost depot's, nobody's). Its fuel is left.
     */
    public synchronized void tickEmpty(Position soldier, Optional<Position> furnace, boolean wanted) {
        this.soldierAt = soldier;
        this.wanted = wanted;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || furnace.isEmpty()) {
                return;
            }
            at = furnace.get();
            emptyingFurnace = true;
            LOG.info("{} empties the furnace at {} - {}", name, at, inventory.summary());
            walking = true;
            script = new Script(name, List.of(
                    new Step("walk to the furnace", () -> walking = true,
                            new Expect("the furnace in reach", () -> ChestWindow.withinReach(soldierAt, at)), walkTicks()),
                    new Step("open the furnace", () -> {
                        walking = false;
                        chest.open(at, true);
                    }, new Expect("the furnace's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                    new Step("take out what's in it", () -> {
                        chest.quickMove(ChestWindow.FURNACE_OUTPUT);
                        chest.quickMove(ChestWindow.FURNACE_INPUT);
                    }, new Expect("its output and input empty", () -> chest.countAt(ChestWindow.FURNACE_OUTPUT) == 0
                            && chest.countAt(ChestWindow.FURNACE_INPUT) == 0), ChestWindow.ANSWER_TICKS)),
                    List.of(new Guard("the trip is no longer wanted", () -> this.wanted || !walking)));
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                LOG.info("{} emptied the furnace at {} - {}", name, at, inventory.summary());
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't empty the furnace at {}: {}: {}", name, at, failed.step(), failed.reason());
                finish();
            }
        }
    }

    private Script drawTrip() {
        walking = true;
        return new Script(name, List.of(
                new Step("walk to the stockpile", () -> walking = true,
                        new Expect("the chest in reach", () -> ChestWindow.withinReach(soldierAt, at)), walkTicks()),
                new Step("open the chest", () -> {
                    walking = false;
                    chest.open(at);
                }, new Expect("the chest's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                new Step("take it out", this::take,
                        new Expect("carrying enough, or the chest out of it",
                                () -> inventory.count(drawing.orElseThrow()) >= drawTo || chest.chestCount(drawing.orElseThrow()) == 0),
                        ChestWindow.ANSWER_TICKS)),
                List.of(new Guard("the trip is no longer wanted", () -> wanted || !walking)));
    }

    /** Shift-clicks stacks of what it draws out of the chest until they'd make up what it wants to carry. */
    private void take() {
        ItemClass material = drawing.orElseThrow();
        int coming = inventory.count(material);
        List<Integer> items = chest.chestItems();
        for (int slot = 0; slot < items.size() && coming < drawTo; slot++) {
            if (ItemClassRegistry.classOf(items.get(slot)).equals(Optional.of(material))) {
                chest.quickMove(slot);
                coming += chest.countAt(slot);
            }
        }
    }

    private Script trip() {
        walking = true;
        return new Script(name, List.of(
                new Step("walk to the stockpile", () -> walking = true,
                        new Expect("the chest in reach", () -> ChestWindow.withinReach(soldierAt, at)), walkTicks()),
                new Step("open the chest", () -> {
                    walking = false;
                    chest.open(at);
                }, new Expect("the chest's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                new Step(PUT_IN, this::deposit,
                        new Expect("the load in the chest", () -> load.stream().allMatch(c -> inventory.count(c) == 0)
                                && inventory.count(ItemClass.FOOD) <= foodLeft && surplus.apply(inventory).isEmpty()),
                        ChestWindow.ANSWER_TICKS)),
                List.of(new Guard("the trip is no longer wanted", () -> wanted || !walking)));
    }

    /** The chest as it stands open. */
    private Stockpile seen() {
        return new Stockpile(chest.chestCount(ItemClass.LOG), chest.chestCount(ItemClass.COBBLESTONE), chest.chestCount(ItemClass.FOOD),
                chest.chestCount(ItemClass.RAW_IRON), chest.chestCount(ItemClass.RAW_COPPER), chest.chestCount(ItemClass.DIRT),
                chest.chestCount(ItemClass.IRON_INGOT), chest.chestCount(ItemClass.COPPER_INGOT));
    }

    /** Raw ore put in on this trip: the force's, every bit of it carried. */
    private Set<ItemClass> pooled = Set.of();
    /** What it carries past its load, put in on this trip too. */
    private java.util.function.Function<Inventory, List<Integer>> surplus = i -> List.of();

    /** Shift-clicks across every stack of the material there's a full load of, and the pooled ore. */
    private void deposit() {
        load = EnumSet.noneOf(ItemClass.class);
        load.addAll(pooled);
        if (inventory.count(ItemClass.LOG) >= Gatherer.CARRY_LOGS) {
            load.add(ItemClass.LOG);
        }
        if (inventory.count(ItemClass.COBBLESTONE) >= Gatherer.CARRY_STONE) {
            load.add(ItemClass.COBBLESTONE);
        }
        foodLeft = inventory.count(ItemClass.FOOD);
        for (int slot : surplus.apply(inventory)) {
            chest.quickMove(chest.windowSlotOf(slot));
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            Optional<ItemClass> itemClass = ItemClassRegistry.classOf(inventory.itemAt(slot));
            if (itemClass.isPresent() && load.contains(itemClass.get())) {
                chest.quickMove(chest.windowSlotOf(slot));
            } else if (itemClass.equals(Optional.of(ItemClass.FOOD)) && foodLeft - inventory.countAt(slot) >= Subsistence.BASIC_LOAD) {
                foodLeft -= inventory.countAt(slot);
                chest.quickMove(chest.windowSlotOf(slot));
            }
        }
    }

    /**
     * Whether {@code inventory} holds a stack of rations it can put in the stockpile and still carry
     * its basic load: bread beyond it is the squad's, drawn by whoever's short.
     */
    public static boolean surplusFood(Inventory inventory) {
        int food = inventory.count(ItemClass.FOOD);
        for (int slot = 0; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(ItemClass.FOOD))
                    && food - inventory.countAt(slot) >= Subsistence.BASIC_LOAD) {
                return true;
            }
        }
        return false;
    }

    public synchronized void onContainer(ContainerUpdate update) {
        if (script != null) {
            chest.onContainer(update);
        }
    }

    private void finish() {
        chest.close();
        script = null;
        walking = false;
        load = Set.of();
        foodLeft = Integer.MAX_VALUE;
        drawing = Optional.empty();
        dumping = Set.of();
        emptyingFurnace = false;
        cooldown = COOLDOWN_TICKS;
    }
}
