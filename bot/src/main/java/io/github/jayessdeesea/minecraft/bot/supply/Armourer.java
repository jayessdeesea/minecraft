package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.craft.Stock;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A soldier making its own kit (docs/military-doctrine.md §11, §12): the weapon, tools and armour
 * it's asked for - by the equipment ladder ({@link io.github.jayessdeesea.minecraft.bot.craft.Ladder})
 * or by an order that needs a pickaxe - several at one opening of the table when it's asked for
 * several. It digs what the plan is short of and it can dig itself - logs by hand, cobblestone
 * and iron ore with a good enough pickaxe - then a {@link Crafter} makes the rest, and whatever
 * was made is put on: armour worn, the best weapon in hand. Driven by the soldier's tick
 * ({@link #tick}) and its connection's container updates ({@link #onContainer}); synchronized
 * because those are two threads.
 *
 * <p>A hostile near pauses it, digging or crafting - the soldier fights with what it has, and
 * picks up where it left off once clear. The exception is a first weapon, finished first when the
 * table is already open (see {@link Crafter}). Something it can't find or reach to dig is a fault:
 * it says why ({@link #failure}) and waits {@link #COOLDOWN_TICKS} before looking again - unless
 * the materials turn up meanwhile (a squadmate hands some over, §13).
 */
public final class Armourer {

    private static final Logger LOG = LogManager.getLogger(Armourer.class);

    static final int COOLDOWN_TICKS = 1200;

    /** What it digs for each material it can fetch itself, in the order it fetches them (the classes' order). */
    private static final Map<ItemClass, Digger.Target> DUG = new EnumMap<>(Map.of(
            ItemClass.LOG, Digger.Target.LOG, ItemClass.COBBLESTONE, Digger.Target.STONE, ItemClass.RAW_IRON, Digger.Target.IRON_ORE,
            ItemClass.RAW_COPPER, Digger.Target.COPPER_ORE));

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;
    private final Set<BlockPos> claims;
    private final Crafter crafter;

    private Digger digger;
    private Set<Recipe> making = Set.of();
    private int cooldown;
    private String failure;

    public Armourer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Map<BlockPos, String> furnaces,
                    Runnable kitChanged) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.kitChanged = kitChanged;
        this.crafter = new Crafter(name, session, inventory, furnaces);
        this.fitter = new Fitter(name, session, inventory, furnaces);
    }

    /** Fits out the workshop it's sent to, when a fixture it needs isn't standing there. */
    private final Fitter fitter;

    public synchronized boolean active() {
        return digger != null || crafter.active() || fitter.active();
    }

    /** Where to look while it places a workshop's fixture. */
    public synchronized Optional<Position> lookAt() {
        return fitter.lookAt();
    }

    /**
     * Why the last attempt failed, while the soldier waits out its cooldown - so it can say it
     * can't make what it needs rather than look idle.
     */
    public synchronized Optional<String> failure() {
        return cooldown > 0 && failure != null ? Optional.of(failure) : crafter.failure();
    }

    /** What's being made, while {@link #active}. */
    public synchronized Set<Recipe> making() {
        return active() ? making : Set.of();
    }

    /**
     * Where the soldier should be standing while the routine runs, if it needs to be somewhere:
     * beside what it digs, or where it began crafting - its table and furnace are within reach of
     * there, and a window closes on a player who walks away from it.
     */
    public synchronized Optional<Position> standAt() {
        if (digger != null) {
            return Optional.of(digger.standAt());
        }
        if (crafter.active()) {
            return Optional.ofNullable(craftingAt);
        }
        return Optional.ofNullable(toStation);
    }

    /** Beside a table or furnace standing nearby, while the soldier walks to it to use it. */
    private Position toStation;

    /**
     * The tables and furnaces it may use within {@link Digger#SEARCH_RADIUS} - in reach, or a walk
     * away: what it needn't make again.
     */
    public synchronized Set<Workstation> stationsNear(WorldView world, Position soldier) {
        Set<Workstation> near = java.util.EnumSet.noneOf(Workstation.class);
        for (Workstation station : Workstation.values()) {
            crafter.nearestUsable(world, soldier, station, Digger.SEARCH_RADIUS).ifPresent(at -> near.add(station));
        }
        return near;
    }

    /** Where the crafter's run under way began. */
    private Position craftingAt;

    /** Logs it's short of to make {@code wanted} from what it carries, with no table to hand. */
    public static int logsShort(Inventory inventory, Set<Recipe> wanted) {
        Map<Recipe, Integer> once = new LinkedHashMap<>();
        wanted.forEach(r -> once.put(r, 1));
        return CraftPlan.of(once, Stock.of(inventory), Set.of()).short_().getOrDefault(ItemClass.LOG, 0);
    }

    /**
     * Whether the soldier could fetch {@code material} itself: logs always - a tree that can't be
     * found is the dig's fault, said when it happens - and stone or ore when its pickaxe is good
     * enough for it and some lies within reach of {@code soldier}.
     */
    public static boolean canDig(ItemClass material, Inventory inventory, WorldView world, Position soldier, Set<BlockPos> claims) {
        Digger.Target target = DUG.get(material);
        if (target == null || !target.canDig(inventory)) {
            return false;
        }
        return target == Digger.Target.LOG
                || Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims, target).isPresent();
    }

    /**
     * Each tick: makes {@code wanted} - empty when the soldier needs nothing, or isn't free to make
     * it - digging first for what it's short of. {@code threatened} pauses it.
     */
    public synchronized void tick(Position soldier, WorldView world, Set<Recipe> wanted, boolean threatened) {
        tick(soldier, world, wanted, threatened, Optional.empty());
    }

    /**
     * As {@link #tick(Position, WorldView, Set, boolean)}, with the {@code workshop} the engineer
     * sited for the soldier: where it goes to make what needs a table or furnace it has none of
     * nearby, and fits out with one if none stands there yet. A soldier never puts one down anywhere
     * else (backlog/sites.md).
     */
    public synchronized void tick(Position soldier, WorldView world, Set<Recipe> wanted, boolean threatened, Optional<Workshop> workshop) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!wanted.equals(making)) {
            stopDigging();
            making = Set.copyOf(wanted);
        }
        boolean fitting = false;
        try {
            fitting = tickMaking(soldier, world, threatened, workshop);
        } finally {
            if (!fitting) {
                fitter.stop(soldier, world);
            }
        }
    }

    /** The making itself, after {@link #tick}'s bookkeeping: whether it fitted out the workshop this tick. */
    private boolean tickMaking(Position soldier, WorldView world, boolean threatened, Optional<Workshop> workshop) {
        Map<Recipe, Integer> items = once(making);
        if (!crafter.active()) {
            craftingAt = soldier;
        }
        if (crafter.active() || making.isEmpty()) {
            if (making.isEmpty() && !fitter.active()) {
                toStation = null; // nothing wanted: no errand to walk on
            }
            made(crafter.tick(soldier, world, items, threatened));
            return false;
        }
        if (threatened) {
            stopDigging(); // back to it once clear
            return false;
        }
        // Planned with the tables and furnaces a walk away as well as those in reach: one standing
        // nearby is gone to and used, not made again.
        CraftPlan plan = CraftPlan.of(items, Stock.of(inventory), stationsNear(world, soldier));
        if (plan.nothingToDo()) {
            return false;
        }
        Map<ItemClass, Integer> short_ = plan.short_();
        if (short_.isEmpty()) {
            stopDigging();
            if (!plan.missing().isEmpty()) {
                if (cooldown == 0 || fitter.active()) {
                    fitOut(soldier, world, plan.missing(), workshop);
                    return true;
                }
                return false;
            }
            if (crafter.plan(items, world, soldier).feasible()) {
                toStation = null;
                made(crafter.tick(soldier, world, items, false));
            } else if (cooldown == 0) {
                walkToStation(soldier, world);
            }
            return false;
        }
        Optional<ItemClass> next = DUG.keySet().stream().filter(short_::containsKey).findFirst();
        if (!short_.keySet().stream().allMatch(DUG::containsKey) || next.isEmpty()) {
            giveUp("short of " + short_ + " - " + inventory.summary());
        } else if (digger != null || cooldown == 0) {
            dig(soldier, world, DUG.get(next.get()), short_.get(next.get()));
        }
    
        return false;
    }

    /** Walks to beside the nearest usable station a walk away - a furnace first, the scarcer - and not yet in reach. */
    private void walkToStation(Position soldier, WorldView world) {
        for (Workstation station : List.of(Workstation.FURNACE, Workstation.CRAFTING_TABLE)) {
            Optional<BlockPos> at = crafter.nearestUsable(world, soldier, station, Digger.SEARCH_RADIUS);
            if (at.isEmpty() || crafter.usableInReach(world, soldier, station)) {
                continue;
            }
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                BlockPos cell = at.get().offset(side[0], 0, side[1]);
                if (Digger.standable(world, cell)) {
                    if (toStation == null) {
                        LOG.info("{} walks to the {} at {} for {}", name, station, at.get(), describe(making));
                    }
                    toStation = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    return;
                }
            }
        }
        // Nothing to walk to after all - the station went, or there's nowhere to stand by it.
        giveUp("no table or furnace to use within " + Digger.SEARCH_RADIUS + " blocks, and not the makings of one");
    }

    public synchronized void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
        fitter.onContainer(update);
    }

    /** To the workshop, and its missing fixtures fitted there: none sited is said, loudly. */
    private void fitOut(Position soldier, WorldView world, Set<Workstation> missing, Optional<Workshop> workshop) {
        if (workshop.isEmpty()) {
            giveUp("no " + missing + " within " + Digger.SEARCH_RADIUS + " blocks, and no workshop sited to make it at");
            return;
        }
        Workshop w = workshop.get();
        Position stand = new Position(w.stand().x() + 0.5, w.stand().y(), w.stand().z() + 0.5);
        if (Math.hypot(soldier.x() - stand.x(), soldier.z() - stand.z()) > WORKSHOP_TOLERANCE || Math.abs(soldier.y() - stand.y()) > 1) {
            if (!stand.equals(toStation)) {
                LOG.info("{} goes to the workshop at {} for {}", name, w.stand(), describe(making));
            }
            toStation = stand;
            return;
        }
        toStation = stand;
        if (!fitter.tick(soldier, world, w, missing, false)) {
            fitter.failure().ifPresent(this::giveUp);
        }
    }

    /** How near the workshop's stand counts as there: within reach of both fixtures. */
    static final double WORKSHOP_TOLERANCE = 0.8;

    private void made(Crafter.Outcome outcome) {
        if (outcome != Crafter.Outcome.MADE) {
            return;
        }
        Quartermaster.equip(inventory).forEach(step -> Quartermaster.perform(step, session, inventory));
        LOG.info("{} made {}", name, describe(making));
        failure = null;
        kitChanged.run();
    }

    /** The next block of {@code target}, toward the {@code count} it's short of. */
    private void dig(Position soldier, WorldView world, Digger.Target target, int count) {
        if (digger == null) {
            if (!target.canDig(inventory)) {
                giveUp("no pickaxe good enough to dig " + target + " - " + inventory.summary());
                return;
            }
            Optional<Digger.Spot> spot = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims, target);
            if (spot.isEmpty()) {
                giveUp("no " + target + " within " + Digger.SEARCH_RADIUS + " blocks");
                return;
            }
            LOG.info("{} goes to dig {} for {} - {} short - at {}", name, target, describe(making), count, spot.get().block());
            digger = new Digger(name, session, inventory, claims, spot.get());
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working _ -> {
            }
            case Digger.Progress.Done _ -> stopDigging(); // the next tick plans again from what's carried now
            case Digger.Progress.Failed(String why) -> giveUp(why);
        }
    }

    private void stopDigging() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    private void giveUp(String why) {
        LOG.warn("{} gives up making {} - {}", name, describe(making), why);
        failure = "can't make " + describe(making) + ": " + why;
        stopDigging();
        toStation = null; // nowhere to go for it now
        cooldown = COOLDOWN_TICKS;
    }

    private static String describe(Set<Recipe> recipes) {
        return recipes.stream().map(Recipe::name).sorted().collect(Collectors.joining(" and "));
    }

    /**
     * How many of what each recipe makes: one tool or piece of kit; bread, all the wheat carried
     * makes, on top of the food already carried - its target is the class, so one asked for was
     * "some food", and live a soldier with a loaf in its pack carried 22 wheat for days.
     */
    private Map<Recipe, Integer> once(Set<Recipe> recipes) {
        Map<Recipe, Integer> items = new LinkedHashMap<>();
        recipes.forEach(r -> items.put(r, r == Recipe.BREAD
                ? inventory.count(ItemClass.FOOD) + Math.max(1, inventory.count(ItemClass.WHEAT) / 3) : 1));
        return items;
    }
}
