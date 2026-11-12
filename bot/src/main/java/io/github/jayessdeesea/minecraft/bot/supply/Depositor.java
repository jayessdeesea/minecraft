package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
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

    /** Whether a soldier at {@code soldier} reaches the chest at {@code chest} to open it. */
    public static boolean withinReach(Position soldier, Position chest) {
        return ChestWindow.withinReach(soldier, chest);
    }

    /** Where the soldier should go while it carries its load in. */
    public synchronized Optional<Position> standAt() {
        return script != null && walking ? Optional.of(at) : Optional.empty();
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
                Stockpile seen = new Stockpile(chest.chestCount(ItemClass.LOG), chest.chestCount(ItemClass.COBBLESTONE), chest.chestCount(ItemClass.FOOD));
                LOG.info("{} stockpiled its load - the chest holds {} logs, {} cobblestone, {} rations", name, seen.logs(), seen.stone(), seen.rations());
                stockpileSeen.accept(seen);
                trips.worked(at);
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't stockpile its load: {}: {}", name, failed.step(), failed.reason());
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
                Stockpile seen = new Stockpile(chest.chestCount(ItemClass.LOG), chest.chestCount(ItemClass.COBBLESTONE), chest.chestCount(ItemClass.FOOD));
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

    private Script drawTrip() {
        walking = true;
        return new Script(name, List.of(
                new Step("walk to the stockpile", () -> walking = true,
                        new Expect("the chest in reach", () -> ChestWindow.withinReach(soldierAt, at)), WALK_TIMEOUT_TICKS),
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
                        new Expect("the chest in reach", () -> ChestWindow.withinReach(soldierAt, at)), WALK_TIMEOUT_TICKS),
                new Step("open the chest", () -> {
                    walking = false;
                    chest.open(at);
                }, new Expect("the chest's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                new Step("put the load in", this::deposit,
                        new Expect("the load in the chest", () -> load.stream().allMatch(c -> inventory.count(c) == 0)
                                && inventory.count(ItemClass.FOOD) <= foodLeft),
                        ChestWindow.ANSWER_TICKS)),
                List.of(new Guard("the trip is no longer wanted", () -> wanted || !walking)));
    }

    /** Shift-clicks across every stack of the material there's a full load of. */
    private void deposit() {
        load = EnumSet.noneOf(ItemClass.class);
        if (inventory.count(ItemClass.LOG) >= Gatherer.CARRY_LOGS) {
            load.add(ItemClass.LOG);
        }
        if (inventory.count(ItemClass.COBBLESTONE) >= Gatherer.CARRY_STONE) {
            load.add(ItemClass.COBBLESTONE);
        }
        foodLeft = inventory.count(ItemClass.FOOD);
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
        cooldown = COOLDOWN_TICKS;
    }
}
