package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Ladder;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.craft.Stock;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A soldier making its own kit (docs/military-doctrine.md §11, §12): the weapon, tools and armour
 * it's asked for - by the equipment ladder ({@link Ladder})
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
    /** Spots it couldn't dig - not walked to, or the block not taken - left for longer each time. */
    private final Setbacks<BlockPos> spots = new Setbacks<>(COOLDOWN_TICKS, 20L * COOLDOWN_TICKS);
    private long ticks;

    /**
     * Whether it may dig what its kit is short of itself. Not a soldier on its order's work (operator,
     * 2026-11-18: a farmer doesn't start chopping trees because it has no hoe): it says what it's short of
     * instead, and draws it from the stockpile the force's producers fill (backlog/soldier-order.md).
     */
    private volatile boolean digsItsMakings = true;

    /** Lets it dig its own makings, or has it say what it's short of instead. */
    public void digsItsMakings(boolean digs) {
        this.digsItsMakings = digs;
    }

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
        // Its walk to a table, a furnace or the workshop too: not under way, the next routine to start
        // took its hands, its errand was dropped as no longer wanted, and taken up again when they were
        // free - live (field390), Hal set off for the mine's workshop for a copper chestplate 103 times
        // in a night, every 2 s, and never got there.
        return digger != null || crafter.active() || fitter.active() || toStation != null;
    }

    /** Whether a run making {@code recipe} is under way. */
    public synchronized boolean making(Recipe recipe) {
        return crafter.active() && making.contains(recipe);
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

    /** The cell of {@link #toStation}, null with none. */
    private BlockPos toStationCell() {
        return toStation == null ? null : BlockPos.containing(toStation.x(), toStation.y(), toStation.z());
    }

    /** Cells the flood for a station's reachable sides looks at: the 16-block search round the soldier. */
    static final int STATION_REACH_BUDGET = 8_000;

    /**
     * The tables and furnaces it may use within {@link Digger#SEARCH_RADIUS} - in reach, or a walk
     * away: what it needn't make again.
     */
    public synchronized Set<Workstation> stationsNear(WorldView world, Position soldier) {
        Set<Workstation> near = EnumSet.noneOf(Workstation.class);
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
        tick(soldier, world, wanted, threatened, workshop, Optional.empty());
    }

    /**
     * As above, at {@code table} only, if its orders name one - a baker's, its duty order's (command.DutyOrder): no other
     * table is used, and one beyond the search's reach is walked to by its {@code workshop}'s stand.
     */
    public synchronized void tick(Position soldier, WorldView world, Set<Recipe> wanted, boolean threatened, Optional<Workshop> workshop,
                                  Optional<BlockPos> table) {
        crafter.workAt(table);
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!wanted.equals(making)) {
            stopDigging();
            making = Set.copyOf(wanted);
            kitWanted.keySet().retainAll(making);
        }
        boolean fitting = false;
        try {
            fitting = tickMaking(soldier, world, threatened, workshop);
        } finally {
            if (!fitting) {
                fitter.stop(soldier, world);
            }
        }
        // A walk to a station that gets no nearer is given up, its hands let go: held for the walk, they'd
        // otherwise be held for good. No nearer, not not there: live (field393), Dana walking down the
        // mine's 92 steps to its base was given up at 90 s, 50 blocks nearer every leg.
        // Its errand let go for a tick or two and taken up again is the same errand, its time no nearer
        // still counted: live (field426), Bob's walk to the farm's workshop was dropped and begun again 91
        // times in two minutes, 6 above its stand, and never given up.
        if (toStation == null) {
            if (++errandGone > ERRAND_GONE_TICKS) {
                errandTo = null;
            }
        } else {
            errandGone = 0;
            double off = Math.sqrt(Math.pow(soldier.x() - toStation.x(), 2) + Math.pow(soldier.y() - toStation.y(), 2)
                    + Math.pow(soldier.z() - toStation.z(), 2));
            if (!toStation.equals(errandTo) || off < errandNearest - ERRAND_GAIN) {
                errandTo = toStation;
                errandNearest = off;
                errandFrom = ticks;
            } else if (ticks - errandFrom > ERRAND_TICKS && digger == null && !crafter.active() && !fitter.active()) {
                giveUp("no nearer " + BlockPos.containing(toStation.x(), toStation.y(), toStation.z()) + " in " + ERRAND_TICKS / 20 + " s, "
                        + Math.round(off) + " off");
            }
        }
    }

    /** The station walk under way, the nearest it's been, and the tick it last got nearer. */
    private Position errandTo;
    private double errandNearest;
    private long errandFrom;
    /** Ticks since its errand was let go; past {@link #ERRAND_GONE_TICKS} one begun again is a new one. */
    private int errandGone;
    static final int ERRAND_GONE_TICKS = 100;
    /** How long a walk to a station is given to get {@link #ERRAND_GAIN} nearer. */
    static final int ERRAND_TICKS = 1200;
    static final double ERRAND_GAIN = 2;

    /** The making itself, after {@link #tick}'s bookkeeping: whether it fitted out the workshop this tick. */
    private boolean tickMaking(Position soldier, WorldView world, boolean threatened, Optional<Workshop> workshop) {
        // Asked for once a run: what it asks is counted from what's carried, and a run moves what's
        // carried - live, a loaf made raised the bread asked for, and the run was dropped as no
        // longer wanted, 518 times over.
        Map<Recipe, Integer> wanted = crafter.active() && asking != null && making.containsAll(asking.keySet()) ? asking : once(making);
        // What it can't make for want of what it can't dig - a bucket's iron - set aside, the rest made: live (field508-512),
        // Olga, the chunk farm's tender with 56 logs, gave up its bucket, chest and fence together, short of 3 raw iron.
        Map<Recipe, Integer> items = makeable(wanted, world, soldier);
        asking = items;
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
                walkToStation(soldier, world, workshop, plan);
            }
            return false;
        }
        Optional<ItemClass> next = DUG.keySet().stream().filter(short_::containsKey).findFirst();
        if (!short_.keySet().stream().allMatch(DUG::containsKey) || next.isEmpty() || !digsItsMakings) {
            // Said once a cooldown, not each tick: live (field450), 2168 lines in six minutes.
            if (cooldown == 0) {
                giveUp("short of " + short_ + " - " + inventory.summary());
            }
        } else if (digger != null || cooldown == 0) {
            dig(soldier, world, DUG.get(next.get()), short_.get(next.get()));
        }
    
        return false;
    }

    /** {@code wanted} but what's short of something it won't dig ({@link #DUG}, and only while it digs its makings): all, if none can be made. */
    private Map<Recipe, Integer> makeable(Map<Recipe, Integer> wanted, WorldView world, Position soldier) {
        Map<Recipe, Integer> can = new java.util.LinkedHashMap<>(wanted);
        can.keySet().removeIf(r -> CraftPlan.of(Map.of(r, wanted.get(r)), Stock.of(inventory), stationsNear(world, soldier)).short_().keySet()
                .stream().anyMatch(k -> !digsItsMakings || !DUG.containsKey(k)));
        return can.isEmpty() ? wanted : can;
    }

    /**
     * Walks to beside the nearest usable station a walk away that {@code plan} makes something at - a
     * furnace first, the scarcer - and not yet in reach. Only one it needs: live (field332), Bob,
     * making copper ingots, walked to a table beside the mine head, and once in its reach went on to
     * the workshop instead, and back to the table again out of it - five minutes between the two.
     */
    private void walkToStation(Position soldier, WorldView world, Optional<Workshop> workshop, CraftPlan plan) {
        Set<Workstation> needed = needed(plan);
        // Only a side it can walk to: live (field348), a table left in a cave at y=16 by a workshop sited
        // there was the nearest, and Ivy, at the mine's bottom below it, stood a minute with no route.
        // Flooded only when a new station is chosen: the walk to one already chosen asks every tick.
        java.util.concurrent.atomic.AtomicReference<Set<BlockPos>> walkable = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Predicate<BlockPos> reaches = cell -> {
            if (cell.equals(toStationCell())) {
                return true;
            }
            if (walkable.get() == null) {
                walkable.set(Pathfinder.reachable(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()),
                        Digger.SEARCH_RADIUS + Clearer.REACH_MARGIN, STATION_REACH_BUDGET));
            }
            return walkable.get().contains(cell);
        };
        for (Workstation station : List.of(Workstation.FURNACE, Workstation.CRAFTING_TABLE)) {
            if (!needed.contains(station)) {
                continue;
            }
            Optional<BlockPos> at = crafter.nearestUsable(world, soldier, station, Digger.SEARCH_RADIUS);
            if (at.isEmpty() || crafter.usableInReach(world, soldier, station)) {
                continue;
            }
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                BlockPos cell = at.get().offset(side[0], 0, side[1]);
                if (Digger.standable(world, cell) && reaches.test(cell)) {
                    if (toStation == null) {
                        LOG.info("{} walks to the {} at {} for {}", name, station, at.get(), describe(making));
                    }
                    toStation = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    return;
                }
            }
        }
        // Nothing to walk to after all - the station went, or there's nowhere to stand by it: the
        // workshop, if one's sited. Live, a farmer gave up its bread by a table it had no stand by,
        // with the camp's workshop a walk off.
        Optional<Position> shopStand = workshop.map(w -> new Position(w.stand().x() + 0.5, w.stand().y(), w.stand().z() + 0.5));
        boolean atShop = shopStand.filter(st -> Math.hypot(soldier.x() - st.x(), soldier.z() - st.z()) <= WORKSHOP_TOLERANCE
                && Math.abs(soldier.y() - st.y()) <= 1).isPresent();
        if (workshop.isPresent() && !atShop) {
            Workshop w = workshop.get();
            Position stand = shopStand.get();
            if (!stand.equals(toStation)) {
                LOG.info("{} goes to the workshop at {} for {}", name, w.stand(), describe(making));
            }
            toStation = stand;
            return;
        }
        giveUp("no table or furnace to use within " + Digger.SEARCH_RADIUS + " blocks, and not the makings of one");
    }

    /** The stations {@code plan}'s sessions are made at. */
    static Set<Workstation> needed(CraftPlan plan) {
        Set<Workstation> needed = EnumSet.noneOf(Workstation.class);
        for (CraftPlan.Session session : plan.sessions()) {
            switch (session.station()) {
                case TABLE -> needed.add(Workstation.CRAFTING_TABLE);
                case FURNACE -> needed.add(Workstation.FURNACE);
                case GRID -> {
                }
            }
        }
        return needed;
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
    public static final double WORKSHOP_TOLERANCE = 0.8;

    private void made(Crafter.Outcome outcome) {
        if (outcome != Crafter.Outcome.MADE) {
            return;
        }
        Quartermaster.equip(inventory).forEach(step -> Quartermaster.perform(step, session, inventory));
        LOG.info("{} made {}", name, describe(making));
        failure = null;
        kitChanged.run();
    }

    /**
     * The nearest block of {@code target} a soldier at {@code feet} can walk to a stand beside -
     * nearest first, then wider, as a gatherer looks (live, soldiers at a camp cleared of trees went
     * without a pickaxe for an hour for want of a log 16 blocks off) - and only one it can reach:
     * live, builders left the keep for iron ore seen 40 blocks off through solid rock, and copper ore
     * under a blocked stair, and each stood 20 seconds at "no route" before it gave up, every few
     * minutes, all day.
     */
    static Optional<Digger.Spot> spotFor(WorldView world, BlockPos feet, Set<BlockPos> excluded, Digger.Target target) {
        int widest = Gatherer.WIDER[Gatherer.WIDER.length - 1];
        Set<BlockPos> reachable = Pathfinder.reachable(world, feet, widest + Clearer.REACH_MARGIN, Clearer.REACH_BUDGET);
        for (int radius : Gatherer.WIDER) {
            // A tree or ore from wherever it can walk to, at any height: ore lies down the mine's stairs,
            // forty blocks under a soldier on the keep. Live, five of eight gave up a shield for want of
            // one ingot, "no IRON_ORE within 48 blocks", with ore beside the stairs they walked each night.
            boolean anyHeight = target == Digger.Target.LOG || target == Digger.Target.IRON_ORE || target == Digger.Target.COPPER_ORE;
            Optional<Digger.Spot> spot = anyHeight ? Digger.findSpotAmong(world, feet, excluded, target, radius, reachable)
                    : Digger.findSpot(world, feet, excluded, target, radius, reachable::contains);
            if (spot.isPresent()) {
                return spot;
            }
        }
        return Optional.empty();
    }

    /** The next block of {@code target}, toward the {@code count} it's short of. */
    private void dig(Position soldier, WorldView world, Digger.Target target, int count) {
        if (digger == null) {
            if (!target.canDig(inventory)) {
                giveUp("no pickaxe good enough to dig " + target + " - " + inventory.summary());
                return;
            }
            Set<BlockPos> excluded = spots.barredNow(ticks);
            excluded.addAll(claims);
            Optional<Digger.Spot> spot = spotFor(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), excluded, target);
            if (spot.isEmpty()) {
                giveUp("no " + target + " within " + Gatherer.WIDER[Gatherer.WIDER.length - 1] + " blocks");
                return;
            }
            LOG.info("{} goes to dig {} for {} - {} short - at {}", name, target, describe(making), count, spot.get().block());
            digger = new Digger(name, session, inventory, claims, spot.get(), Digger.walkTicks(soldier, spot.get().stand()));
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working _ -> {
            }
            case Digger.Progress.Done _ -> {
                spots.worked(digger.spot().block());
                stopDigging(); // the next tick plans again from what's carried now
            }
            case Digger.Progress.Failed(String why) -> {
                // Another spot next time: live, a soldier walked at one log it couldn't reach 213 times.
                spots.failed(digger.spot().block(), ticks, why);
                giveUp(why);
            }
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
     * How many of what each recipe makes: a tool or piece of kit, one more than was carried when it
     * was first asked for, kept while it's asked - a spare is a second of one held, and counted again
     * after each one made it would be a third, and a fourth; bread, all the wheat carried
     * makes, on top of the food already carried - its target is the class, so one asked for was
     * "some food", and live a soldier with a loaf in its pack carried 22 wheat for days.
     */
    private Map<Recipe, Integer> once(Set<Recipe> recipes) {
        Map<Recipe, Integer> items = new LinkedHashMap<>();
        // Ingots, as bread is baked: all the raw ore carried, smelted - the smelter's batch.
        recipes.forEach(r -> items.put(r, r == Recipe.BREAD
                ? inventory.count(ItemClass.FOOD) + inventory.count(ItemClass.WHEAT) / 3
                : r == Recipe.COPPER_INGOT ? inventory.count(ItemClass.COPPER_INGOT) + inventory.count(ItemClass.RAW_COPPER)
                : r == Recipe.IRON_INGOT ? inventory.count(ItemClass.IRON_INGOT) + inventory.count(ItemClass.RAW_IRON)
                : kitWanted.computeIfAbsent(r, k -> Stock.of(inventory).count(k) + 1)));
        return items;
    }

    /** Each piece of kit asked for, and how many of it to carry: set when first asked, dropped once it isn't. */
    private final Map<Recipe, Integer> kitWanted = new java.util.HashMap<>();

    /** What the run under way was asked for, kept for its length - null before the first. */
    private Map<Recipe, Integer> asking;
}
