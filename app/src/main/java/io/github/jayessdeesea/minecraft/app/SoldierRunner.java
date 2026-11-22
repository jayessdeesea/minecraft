package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Ladder;
import io.github.jayessdeesea.minecraft.bot.craft.LoadPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import static io.github.jayessdeesea.minecraft.app.SoldierErrands.*;
import static io.github.jayessdeesea.minecraft.app.SiteGeometry.*;
import static io.github.jayessdeesea.minecraft.app.Slots.setSlot;
import static io.github.jayessdeesea.minecraft.app.Slots.turn;
import io.github.jayessdeesea.minecraft.bot.craft.WorkLoad;
import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.engineer.Stairway;
import io.github.jayessdeesea.minecraft.bot.engineer.AccessRoute;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.nav.RouteEffort;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Armourer;
import io.github.jayessdeesea.minecraft.bot.supply.Breacher;
import io.github.jayessdeesea.minecraft.bot.supply.Builder;
import io.github.jayessdeesea.minecraft.bot.supply.Clearer;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.supply.StockLevels;
import io.github.jayessdeesea.minecraft.bot.supply.Farmer;
import io.github.jayessdeesea.minecraft.bot.supply.Filler;
import io.github.jayessdeesea.minecraft.bot.supply.Fitter;
import io.github.jayessdeesea.minecraft.bot.supply.Footing;
import io.github.jayessdeesea.minecraft.bot.supply.Forester;
import io.github.jayessdeesea.minecraft.bot.supply.Gatherer;
import io.github.jayessdeesea.minecraft.bot.supply.Handover;
import io.github.jayessdeesea.minecraft.bot.supply.Hunter;
import io.github.jayessdeesea.minecraft.bot.supply.Lamplighter;
import io.github.jayessdeesea.minecraft.bot.supply.Lighting;
import io.github.jayessdeesea.minecraft.bot.supply.Mason;
import io.github.jayessdeesea.minecraft.bot.supply.Miner;
import io.github.jayessdeesea.minecraft.bot.supply.Perimeter;
import io.github.jayessdeesea.minecraft.bot.supply.Quartermaster;
import io.github.jayessdeesea.minecraft.bot.supply.Quiver;
import io.github.jayessdeesea.minecraft.bot.supply.MetalPool;
import io.github.jayessdeesea.minecraft.bot.supply.Pack;
import io.github.jayessdeesea.minecraft.bot.supply.WayOut;
import io.github.jayessdeesea.minecraft.bot.supply.Rations;
import io.github.jayessdeesea.minecraft.bot.supply.ResourceSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Scavenger;
import io.github.jayessdeesea.minecraft.bot.supply.Setbacks;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.supply.SupplyRun;
import io.github.jayessdeesea.minecraft.bot.supply.Torchmaker;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Escort;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Formations;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.Groundwork;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Logstat;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Site;
import io.github.jayessdeesea.minecraft.command.Supply;
import io.github.jayessdeesea.minecraft.command.MissionEstimate;
import io.github.jayessdeesea.minecraft.command.LevelPace;
import io.github.jayessdeesea.minecraft.command.LinkUp;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.ReactToContact;
import io.github.jayessdeesea.minecraft.command.Recovery;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.Resupply;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.TaskRegister;
import io.github.jayessdeesea.minecraft.command.Tasking;
import io.github.jayessdeesea.minecraft.command.DangerAreas;
import io.github.jayessdeesea.minecraft.command.FarmDuties;
import io.github.jayessdeesea.minecraft.command.WoodDuties;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.WorkOrders;
import io.github.jayessdeesea.minecraft.protocol.ChatMessage;
import io.github.jayessdeesea.minecraft.protocol.HealthUpdate;
import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The composition root's actual job: connect each named Soldier below, bridge their live
 * {@code ChunkUpdate}s into one shared {@link ChunkCache} and their entity spawns/removals into one
 * shared {@link EntityTracker}, seed each one's real spawn position
 * from the server's own {@code PlayerPosition} confirmation, and run each one's tick loop - plus one
 * squad loop that keeps every soldier's formation slot current ({@link Formations}).
 *
 * <p>The squad's {@link SquadOrder} comes from the {@link ChainOfCommand} (platoon leader and squad
 * leader, both LLM-backed). Every second the squad loop hands the squad's SITREPs up to it and
 * re-derives each soldier's slot from the standing order and where the point man actually is; every
 * few seconds it logs the squad's SITREPs.
 *
 * <p>Who connects, and in which squad, is the {@link Roster}. Each squad has its own formation,
 * link-up and pace control; the world model is shared by all of them.
 *
 * <p>All soldiers share one {@link ChunkCache} - docs/architecture.md's \"Behavior modeling\" section built its
 * reference counting specifically for this: the block data is one shared ground truth regardless
 * of which soldier's connection loaded it, and a chunk both soldiers can see stays cached until
 * *both* have unloaded it. Each soldier still gets its own connection, tick-loop thread, and
 * {@link DeterministicSoldier} instance - only the world model is shared. Entities work the same
 * way: every connection feeds one {@link EntityTracker}, and each soldier ticks against one
 * {@link WorldModel} combining both, so a hostile seen by Bob's connection makes Alice back away too.
 *
 * <p>Each soldier also reconnects on its own if its connection dies unexpectedly - see {@code
 * PlaySessionListeners.onDisconnect} and {@link #onDisconnected}. A reconnect re-seeds a brand new
 * {@link DeterministicSoldier} from wherever the server places that username on rejoining, rather
 * than trying to resume in-flight state - simplest thing that satisfies "don't hang forever on a
 * silently-dead connection," not a full resume/backoff policy.
 */
@Component
public class SoldierRunner implements CommandLineRunner, DisposableBean {

    private static final Logger LOG = LogManager.getLogger(SoldierRunner.class);

    // Hardcoded pending a real deployment need for external config - matches the dev server DEVELOPMENT.md's own setup instructions put on localhost.
    private static final String HOST = "localhost";
    private static final int PORT = 25565;

    private static final long TICK_PERIOD_MILLIS = 50; // 20 Hz, matching Minecraft's own tick rate
    private static final long STATUS_LOG_PERIOD_SECONDS = 5;

    /** One soldier: its username and squad. */
    private record SoldierDefinition(String name, String squad) {
    }

    /** Every soldier of every squad, squad by squad in formation order; the first carries the radio. */
    private final List<SoldierDefinition> soldierDefinitions;
    private final Roster roster;
    /** Whether to stand still while a live experiment runs on this machine ({@link ExperimentLock}). */
    private final boolean yieldToExperiments;
    /** What the experiment lock said at the last squad cycle - squad loop only. */
    private ExperimentLock.State lastLock = ExperimentLock.State.FREE;

    /** How far above or below the depot the flatten's grove may lie. */
    static final int GROVE_RISE = 4;
    private static final long SQUAD_PERIOD_MILLIS = 1000;
    /** Squad cycles in a row a soldier is stranded before a squadmate is sent: about 20 seconds. */
    static final int STRANDED_REPORTS = 20;

    /** Currently connected soldiers by name - written by connect/disconnect, read by the squad loop. */
    private final Map<String, DeterministicSoldier> activeSoldiers = new ConcurrentHashMap<>();

    /**
     * A squad's patrol base as its last check found it - squad loop writes, anyone reads.
     *
     * @param allInside every soldier of the squad on the base's floor
     * @param sealed    no way into the room (meaningful once all are inside)
     * @param lit       no floor cell a monster could spawn on
     */
    public record BaseReport(boolean allInside, boolean sealed, boolean lit) {
    }

    private final Map<String, BaseReport> baseReports = new ConcurrentHashMap<>();

    /** How {@code squad}'s patrol base stands, if it has one: for the squad simulation to read. */
    Optional<BaseReport> baseReport(String squad) {
        return Optional.ofNullable(baseReports.get(squad));
    }
    /** The overworld's time of day, followed from the radio operator's connection. */
    private final OverworldClock overworldClock = new OverworldClock();

    /** Set as the app shuts down: a connection closing then is no reason to reconnect. */
    private volatile boolean stopping;
    /** Every loop and connection running, so shutting down stops them all. */
    private final Set<ScheduledExecutorService> schedulers = ConcurrentHashMap.newKeySet();
    private final Set<PlaySession> sessions = ConcurrentHashMap.newKeySet();

    /** Stops every loop and closes every connection, so the soldiers leave the server. */
    @Override
    public void destroy() {
        stopping = true;
        schedulers.forEach(ScheduledExecutorService::shutdownNow);
        sessions.forEach(PlaySession::close);
    }

    /** The squad's patrol base, while it has one laid out. */
    public Optional<BaseReport> base(String squad) {
        return Optional.ofNullable(baseReports.get(squad));
    }

    /** Who is connected now, by username. */
    public Set<String> connected() {
        return Set.copyOf(activeSoldiers.keySet());
    }

    /** Where {@code soldier} is now, while connected. */
    /** Hostiles any soldier can see within {@code radius} of {@code center} - for a live scenario's judge. */
    /** How many blocks of {@code squad}'s wall are still to place, while it has one laid out - for scenario checks. */
    public OptionalInt wallLeft(String squad) {
        SquadState state = squadStates.get(squad);
        return state == null || state.wall == null || state.wallLeft < 0 ? OptionalInt.empty()
                : OptionalInt.of(state.wallLeft);
    }

    public int hostilesNear(Position center, double radius) {
        return world.nearbyHostiles(center, radius).size();
    }

    /** What a connected soldier last reported doing - for a live scenario's judge. */
    public Optional<Report> report(String soldier) {
        return Optional.ofNullable(activeSoldiers.get(soldier)).map(s -> s.status().report());
    }

    /** What a connected soldier last reported carrying, if it has - for a live scenario's judge. */
    public Optional<Kit> kit(String soldier) {
        return Optional.ofNullable(activeSoldiers.get(soldier)).flatMap(s -> s.status().condition()).flatMap(c -> c.kit());
    }

    public Optional<Position> position(String soldier) {
        return Optional.ofNullable(activeSoldiers.get(soldier)).map(s -> s.status().position());
    }
    private final ChainOfCommand chainOfCommand;
    private final Map<String, SquadState> squadStates = new HashMap<>(); // squad thread only
    /** Each connected soldier's kit-making routine, by name - set on join, read by the squad loop. */
    private final Map<String, Armourer> armourers = new ConcurrentHashMap<>();
    private final Map<String, Torchmaker> torchmakers = new ConcurrentHashMap<>();
    /** Whether each soldier's squad leader lets it stop to rearm, with its squad out of contact - squad loop writes, tick reads. */
    private final Map<String, Boolean> rearmAllowed = new ConcurrentHashMap<>();
    /**
     * Whether each soldier may climb the equipment ladder past a weapon: allowed to rearm, and its
     * squad not ordered to a patrol base - shelter comes first, and leaving the site to dig iron
     * isn't building it. Squad loop writes, tick reads.
     */
    private final Map<String, Boolean> upgradeAllowed = new ConcurrentHashMap<>();
    /** Cobblestone each soldier keeps for its share of its squad's patrol base still to build - squad loop writes, tick reads. */
    private final Map<String, Integer> stoneSetAside = new ConcurrentHashMap<>();
    /** Each furnace a soldier is smelting in, and who: a furnace's contents are whoever's takes them. */
    private final Map<BlockPos, String> furnaces = new ConcurrentHashMap<>();
    /** Logs and stand cells a soldier is working, shared so soldiers pick different trees. */
    private final Set<BlockPos> treeClaims = ConcurrentHashMap.newKeySet();
    /** How long a site's dig, as scanned, serves every squad's cycle before it's scanned again. */
    static final long SITE_CLEAR_MILLIS = 1_000;

    /** The site's dig as last scanned, by what was kept out of it: its ground, the exceptions, when, the cells. */
    private final Map<Set<Cell>, Object[]> siteClears = new LinkedHashMap<>();

    /**
     * {@code Clearing.toClear} of {@code ground}'s dig with {@code except} kept out, scanned once a
     * {@link #SITE_CLEAR_MILLIS} for every squad's cycle. Commit 496's profile: four squads each
     * scanned the big site - 33 by 129 columns from the dig's bottom to the top of the world, a
     * million cells - twice a cycle, and it was most of the garrison's CPU.
     */
    private List<BlockPos> siteClear(Flatten ground, Set<Cell> except) {
        long now = clockwork.millis();
        synchronized (siteClears) {
            Object[] seen = siteClears.get(except);
            if (seen != null && seen[0].equals(ground) && now - (long) seen[1] < SITE_CLEAR_MILLIS) {
                @SuppressWarnings("unchecked")
                List<BlockPos> cells = (List<BlockPos>) seen[2];
                return cells;
            }
        }
        // The site and its benched edge (clearing rule 6): each row of the ring round it, d out, dug down
        // to d over the floor, so the edge is a walkable slope - live, soldiers put steps in the edge
        // column to climb the hillside the site was cut into, each one a level of its own.
        List<BlockPos> cells = new ArrayList<>(Clearing.toClear(world, ground.dig(), except));
        for (int d = 1; d <= Flatten.BENCH; d++) {
            for (Box row : ground.bench(d)) {
                cells.addAll(Clearing.toClear(world, row, except));
            }
        }
        cells = List.copyOf(cells);
        synchronized (siteClears) {
            siteClears.put(Set.copyOf(except), new Object[]{ground, now, cells});
            // Only the last few kept: one for each squad's two scans.
            while (siteClears.size() > 8) {
                siteClears.remove(siteClears.keySet().iterator().next());
            }
        }
        return cells;
    }

    /** Each soldier's last step put down - its cell, how many times in a row, since when - its own tick's. */
    private final Map<String, Object[]> lastStep = new ConcurrentHashMap<>();
    /** How many times in a row a step goes into one cell before it's taken as refused. */
    static final int STEP_TRIES = 8;
    /** How long a refused step's cell is left alone. */
    static final long STEP_REFUSED_MILLIS = 10_000;

    /**
     * Whether {@code name}'s step at {@code cell} is one the server keeps refusing - put there
     * {@link #STEP_TRIES} times running - and so not put again for a while: the soldier stands,
     * and the stuck alarm and the recovery drill take it from there. Live, a builder put one step
     * every tick for four minutes, 700 times, standing in the cell it was putting it in.
     */
    /**
     * Whether another soldier's body is in {@code cell}, so the server won't place a block there: the
     * step waits for it to move, not sent to be refused. Live (field333), Bob pillared up into the
     * cell Alice stood in at the hill's top, and his step was refused and read as the model's fault -
     * "a refused step at air". Said once each time the one in the way changes.
     */
    private boolean bodyIn(String name, BlockPos cell) {
        Optional<String> in = activeSoldiers.entrySet().stream().filter(e -> !e.getKey().equals(name))
                .filter(e -> overlaps(e.getValue().status().position(), cell)).map(Map.Entry::getKey).findFirst();
        String was = bodyInTheWay.get(name);
        if (in.isPresent() && !in.get().equals(was)) {
            LOG.info("[{}] its step at {} {} {} waits for {} to move out of it", name, cell.x(), cell.y(), cell.z(), in.get());
        }
        if (in.isPresent()) {
            bodyInTheWay.put(name, in.get());
        } else {
            bodyInTheWay.remove(name);
        }
        return in.isPresent();
    }

    /** Whose body each soldier's step last waited on, by name. */
    private final Map<String, String> bodyInTheWay = new ConcurrentHashMap<>();

    private boolean refusedStep(String name, BlockPos cell) {
        long now = clockwork.millis();
        Object[] last = lastStep.get(name);
        if (last != null && last[0].equals(cell)) {
            int tries = (int) last[1] + 1;
            if (tries == STEP_TRIES) {
                // With the world model's block there and under it, as state ids (the server's reports
                // name them): live, a step refused every 10 s in the dig went into a cell neither air nor
                // anything a shovel, pickaxe, axe or hoe takes, and the probes couldn't name it.
                String there = chunkCache.describeAt(cell.x(), cell.y(), cell.z());
                LOG.warn("[{}] its step at {} {} {} keeps being refused - left for {} s; the model has {} there, {} under it", name,
                        cell.x(), cell.y(), cell.z(), STEP_REFUSED_MILLIS / 1000, there, chunkCache.describeAt(cell.x(), cell.y() - 1, cell.z()));
                blocksThatStopped("a refused step", there);
            }
            if (tries >= STEP_TRIES && now - (long) last[2] < STEP_REFUSED_MILLIS) {
                lastStep.put(name, new Object[]{cell, tries, last[2]});
                return true;
            }
            lastStep.put(name, new Object[]{cell, tries >= STEP_TRIES ? 1 : tries, tries >= STEP_TRIES ? now : last[2]});
            return false;
        }
        lastStep.put(name, new Object[]{cell, 1, now});
        return false;
    }

    /** Which soldier hunts which animal, by entity: one hunter to an animal - the soldiers' hunters share it. */
    private final Map<Integer, String> huntClaims = new ConcurrentHashMap<>();
    /**
     * The cells a lamplighter is putting a torch in, shared so soldiers light different ones. Not
     * {@link #treeClaims}: those say where nobody digs - a staircase's walkway, a base's walls - and
     * live, holding torches to them left the mine's stairs never lit.
     */
    private final Set<BlockPos> torchClaims = ConcurrentHashMap.newKeySet();
    /** Each connected soldier's trips to the stockpile, by name. */
    private final Map<String, Depositor> depositors = new ConcurrentHashMap<>();
    /** Each connected soldier's handing of logs to a squadmate, by name. */
    private final Map<String, Handover> handovers = new ConcurrentHashMap<>();
    /** Each connected soldier's building, by name. */
    private final Map<String, Builder> builders = new ConcurrentHashMap<>();
    /** Each soldier's builder for an {@code Order.Place}, apart from its patrol base's. */
    private final Map<String, Builder> placers = new ConcurrentHashMap<>();
    /** The cells of the {@code Order.Place} a soldier has - squad loop writes, its tick reads. */
    private final Map<String, List<BlockPos>> placing = new ConcurrentHashMap<>();
    /** How the soldier's {@code Order.Place} went at its last tick - absent before the first. */
    private final Map<String, Builder.Progress> placeProgress = new ConcurrentHashMap<>();
    /** A soldier's part in building its squad's patrol base. */
    private record Fortify(Position stand, List<BlockPos> cells, boolean allInside, boolean makeTorches, boolean placeTorch) {
    }
    /** What each soldier is building, while its squad builds a patrol base - squad loop writes, tick reads. */
    private final Map<String, Fortify> fortifying = new ConcurrentHashMap<>();
    /** Torches each soldier carries, and whether it could make some - tick writes, squad loop reads. */
    private final Map<String, Integer> torchesCarried = new ConcurrentHashMap<>();
    private final Map<String, Boolean> canMakeTorches = new ConcurrentHashMap<>();
    /** The doorway a soldier is cutting out of its squad's base, and where it stands to - squad loop writes, tick reads. */
    private record Opening(Position stand, List<BlockPos> cells) {
    }
    private final Map<String, Opening> opening = new ConcurrentHashMap<>();
    /** Logs each soldier is short of for a tool it can't chop wood for - tick writes, squad loop reads; absent when none. */
    private final Map<String, Integer> logsWanted = new ConcurrentHashMap<>();
    /** What each soldier is to hand over, by giver - squad loop writes, tick reads; absent when nothing. */
    private final Map<String, CrossLevel.Transfer> deliveries = new ConcurrentHashMap<>();
    /** Each connected soldier's gathering routine, by name. */
    private final Map<String, Gatherer> gatherers = new ConcurrentHashMap<>();
    /** Each connected soldier's farm work, by name. */
    private final Map<String, Farmer> farmers = new ConcurrentHashMap<>();
    /** Arrows a soldier with a bow makes up to, when it carries flint and feathers. */
    private static final int ARROWS_WANTED = 32;
    /** Each connected soldier's picking up of what lies near, by name. */
    private final Map<String, Scavenger> scavengers = new ConcurrentHashMap<>();
    /** Each connected soldier's wall building, by name. */
    private final Map<String, Mason> masons = new ConcurrentHashMap<>();
    private final Map<String, Clearer> clearers = new ConcurrentHashMap<>();
    private final Map<String, Filler> fillers = new ConcurrentHashMap<>();
    /**
     * Each soldier afloat, where it was when it last moved and the tick: one that hasn't moved in
     * {@link #STUCK_IN_WATER_TICKS} is stuck in the water - live, two soldiers floated in a flooded
     * shaft for hours, reporting the wood they meant to gather.
     */
    private final Map<String, double[]> afloatSince = new ConcurrentHashMap<>();
    static final int STUCK_IN_WATER_TICKS = 600;
    /** How far it must have moved to count as getting somewhere. */
    static final double STUCK_IN_WATER_MOVE = 1.5;
    /** How far round itself a soldier stuck in water plugs the water's sources. */
    static final int SEAL_REACH = 4;
    /** The cells of each soldier's {@code Order.Fill} - squad loop writes, tick reads. */
    private final Map<String, List<BlockPos>> filling = new ConcurrentHashMap<>();
    /** The dirt in its squad's closed wall each soldier replaces with stone - squad loop writes, tick reads. */
    private final Map<String, List<BlockPos>> repointing = new ConcurrentHashMap<>();
    /** The blocks of each soldier's {@code Order.Dig} - squad loop writes, tick reads. */
    private final Map<String, List<BlockPos>> clearing = new ConcurrentHashMap<>();
    /** Each soldier's dig's box - squad loop writes, tick reads: the keep's builders stand only in theirs. */
    private final Map<String, Box> clearingStrip = new ConcurrentHashMap<>();
    /** Keep builders in contact, or out of it less than {@link #KEEP_FIGHT_MILLIS}: their strips offer the level nothing - squad loops. */
    private final Set<String> keepFighting = ConcurrentHashMap.newKeySet();
    /** When each keep builder was last in contact - squad loops. */
    private final Map<String, Long> keepFoughtAt = new ConcurrentHashMap<>();
    /** How long past its last contact a builder's strip stays out of the site's level. */
    static final long KEEP_FIGHT_MILLIS = 30_000;
    /** Where each keep builder last fought - squad loops. */
    private final Map<String, Position> keepFightingAt = new ConcurrentHashMap<>();
    /** How near a fight the keep's ground is nobody's: past the reach of a soldier's eyes on a hostile. */
    static final double KEEP_FIGHT_CLEAR = 12;
    /** Each soldier's best pickaxe's mining tier - its tick writes, the squad loop reads. */
    private final Map<String, Integer> pickaxeTiers = new ConcurrentHashMap<>();
    /** Soldiers whose dig is a tunnel - the recovery drill's stair up, cut cell by cell under rock. Squad loop writes, tick reads. */
    private final Set<String> clearingTunnel = ConcurrentHashMap.newKeySet();
    /** The blocks of cover a clearer has taken, and where it stands for each: shared, so each takes another. */
    private final Set<BlockPos> clearClaims = ConcurrentHashMap.newKeySet();
    /** The wall cells each soldier's squad still has to fill, while its objective is FORTIFY - squad loop writes, tick reads. */
    private final Map<String, List<BlockPos>> walling = new ConcurrentHashMap<>();
    /** The centre of the wall each soldier's squad builds - squad loop writes, tick reads. */
    private final Map<String, BlockPos> wallCentres = new ConcurrentHashMap<>();
    /**
     * Wall cells a mason has taken - its own set, apart from the claims every dig skips, which hold
     * the whole wall so nobody quarries it.
     */
    private final Set<BlockPos> wallClaims = ConcurrentHashMap.newKeySet();
    /** Each wall's layout, by its centre's x and z - shared by every squad building it. */
    private final Map<String, List<BlockPos>> walls = new ConcurrentHashMap<>();
    /** Each wall's layout on disk: laid out again after a restart, it would go up on top of itself. */
    private final WallStore wallStore;
    /** The camp's farm, kept on disk like the wall: sited again after a restart, it moved to another pond. */
    /** The chests found full, kept on disk: live, after every restart the crew found each again by a failed trip. */
    private final WallStore chestStore;
    /**
     * The steps the soldiers have built and not yet taken down, kept on disk: lost at a restart, every
     * step left standing read as ground - live, the dirt tower's leftover steps at y=90-91 set the
     * flatten's level back up to them after every restart, and builders put more up to reach them.
     */
    private final WallStore stepStore;
    /**
     * The camp's mine - its staircase, as its head and its last step's feet - kept on disk like the
     * wall. After a restart the same staircase is cut on down, not sited afresh: live, sited again
     * from wherever the squad stood, thirty-odd staircases were started round the mine head, until
     * no new one could be cut between them. And its cells are kept out of the camp's ground work
     * whatever the squads' tasks: with no squad on MINE, the shaft was no squad's, and the camp's
     * fill would have poured dirt down it.
     */
    private final WallStore mineStore;
    /** The camp's mine, as last saved - null until loaded, empty for none. Squad loop only. */
    private Optional<Staircase> campStairs;
    /** The camp the squads build up, x and z - null for none. */
    private final int[] camp;
    /** The camps moved from, kept on disk (SiteGeometry.oldCamps): their chests salvaged into the camp's (backlog/camp-to-sea-level.md). */
    private final List<BlockPos> oldCamps;
    /** Squad-loop cycles between measures of the foothold: about ten seconds. */
    private static final int FOOTHOLD_EVERY = 10;
    private int footholdTicks;
    /** Steps cut down the deepest staircase so far, and whether one reached its bottom - tick threads write. */
    private final AtomicInteger stairsCut = new AtomicInteger();
    private final AtomicBoolean stairsDone = new AtomicBoolean();

    /**
     * The camp measured against the lodgment's phases (docs/military-doctrine.md §17): what's lit,
     * growing, dug and carried - empty until the camp's ground has loaded.
     */
    /** An in-game day on the clock: 24,000 ticks at 50 ms. */
    static final long DAY_MILLIS = 24_000L * 50;
    /** First figures for the days making more takes (backlog/maturity-model.md): wheat's ripening, a grove's cut and the wood party's run. */
    static final double RATIONS_LEAD_DAYS = 1, LOGS_LEAD_DAYS = 1;
    /** Each class's measured burn - the main loop's. */
    private final Supply.Burn rationsBurn, logsBurn; // kept across restarts (BurnStore)
    private final BurnStore burns;
    /** The keep's work left as measured, for its rate - the main loop's. */
    private final Supply.Burn keepWorkBurn = new Supply.Burn(DAY_MILLIS);
    /** The keep's estimate as last worked out, and whether it was feasible when last logged - the main loop's. */
    private volatile MissionEstimate keepEstimate;
    private Boolean keepFeasible;
    /** Class IV as last measured, for the platoon sergeant's wood party - the main loop writes, squad loops read. */
    private volatile Supply logsSupply;
    /** Class I as last measured, for the platoon sergeant's farmers - the main loop writes, squad loops read. */
    private volatile Supply rationsSupply;
    /** Each class's state as last logged - the main loop's. */
    private final Map<String, Supply.State> supplyStates = new HashMap<>();

    /**
     * The force's classes of supply in days, at the camp (command.Supply; backlog/maturity-model.md):
     * Class I, the rations every soldier carries and the stockpile holds; Class IV, the logs. Upkeep is
     * measured from their falls; nothing is committed yet - the mission estimates are to say what is.
     */
    private List<Supply> measureSupply() {
        List<Kit> kits = activeSoldiers.values().stream().map(DeterministicSoldier::status)
                .flatMap(st -> st.condition().flatMap(Condition::kit).stream()).toList();
        Optional<Stockpile> pile = chainOfCommand.stockpile();
        long now = clockwork.millis();
        int rations = kits.stream().mapToInt(Kit::rations).sum() + pile.map(Stockpile::rations).orElse(0);
        int logs = kits.stream().mapToInt(Kit::logs).sum() + pile.map(Stockpile::logs).orElse(0);
        rationsBurn.stock(afterActionReview.errands.unconsumed("food"), now); // the burn what's used up, not the stock's falls
        logsBurn.stock(afterActionReview.errands.unconsumed("log"), now);
        burns.save();
        // The keep's estimate (command.MissionEstimate): its work at the rate measured from that work's
        // falls, its rations at the force's measured burn a soldier, its logs against the stock above
        // Class IV's reorder point - and the logs it draws a day are Class IV's committed burn.
        Supply logsWas = logsSupply;
        double logsReorder = logsWas == null ? 0 : logsWas.reorderPoint();
        double committedLogs = 0;
        if (!keepDigLeftBySquad.isEmpty()) {
            int digLeft = keepDigLeftBySquad.values().stream().mapToInt(Integer::intValue).sum();
            boolean fillKnown = keepFillLeftBySquad.values().stream().anyMatch(n -> n >= 0);
            int fillLeft = fillKnown ? keepFillLeftBySquad.values().stream().mapToInt(Integer::intValue).filter(n -> n >= 0).sum()
                    : (int) chainOfCommand.flatten().slab().volume();
            keepWorkBurn.stock(digLeft + fillLeft, now);
            int crew = keepCrews.values().stream().mapToInt(List::size).sum();
            int stone = kits.stream().mapToInt(Kit::stone).sum() + pile.map(Stockpile::stone).orElse(0);
            int iron = soldierIron.values().stream().mapToInt(Integer::intValue).sum() + pile.map(Stockpile::rawIron).orElse(0);
            MissionEstimate keep = MissionEstimate.keep(digLeft, fillLeft, keepWorkBurn.perDay(), Math.max(1, crew),
                    rationsBurn.perDay() / Math.max(1, kits.size()), (int) Math.floor(logs - logsReorder), stone,
                    keepOreAside(), ironPickaxe(), iron);
            keepEstimate = keep;
            if (keepFeasible == null || keepFeasible != keep.feasible()) {
                keepFeasible = keep.feasible();
                LOG.info("[Estimate] {}", keep.line());
            }
            committedLogs = Double.isInfinite(keep.days()) || keep.days() <= 0 ? 0 : keep.logs() / keep.days();
        }
        Supply logsNow = new Supply("Class IV (logs)", logs, logsBurn.perDay(), committedLogs, LOGS_LEAD_DAYS);
        Supply rationsNow = new Supply("Class I (rations)", rations, rationsBurn.perDay(), 0, RATIONS_LEAD_DAYS);
        logsSupply = logsNow;
        rationsSupply = rationsNow;
        return List.of(rationsNow, logsNow);
    }

    /** Ore on the keep's site no builder's pickaxe gets the drop from, by squad - each squad's loop writes its own. */
    private final Map<String, Integer> keepOreAsideBySquad = new ConcurrentHashMap<>();

    /** Raw iron and iron ingots each soldier carries - its tick writes, the main loop reads. */
    private final Map<String, Integer> soldierIron = new ConcurrentHashMap<>();

    /** The keep's ore set aside: the whole site's, so every squad's count is the same; the largest. */
    private int keepOreAside() {
        return keepOreAsideBySquad.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Whether any soldier carries an iron pickaxe or better. */
    private boolean ironPickaxe() {
        return pickaxeTiers.values().stream().anyMatch(t -> t >= IRON_TIER);
    }

    /** An iron pickaxe's tier (bot.world.Hardness): wood 0, stone 1, iron 2. */
    private static final int IRON_TIER = 2;

    /** The camp's level as last measured, for the basis of issue - the main loop writes, ticks read; SHELTER's ceiling before. */
    private volatile Optional<Site.Level> campLevel = Optional.empty();

    /** Each site's line as last logged, by site - the main loop's. */
    private final Map<String, String> siteLines = new HashMap<>();

    /**
     * The force's sites and how far each is built up (command.Site; backlog/maturity-model.md): the
     * camp from its foothold; the keep, by its depot, its dig and its slab; the mine base, by its stairs
     * and its landing's workshop - what can be measured of each today.
     */
    private List<Site> measureSites(Foothold foothold) {
        Site campSite = Site.camp(foothold);
        campLevel = campSite.level();
        List<Site> sites = new ArrayList<>(List.of(campSite));
        boolean keepWorked = !keepDigLeftBySquad.isEmpty();
        if (keepWorked) {
            int digLeft = keepDigLeftBySquad.values().stream().mapToInt(Integer::intValue).sum();
            int fillLeft = keepFillLeftBySquad.values().stream().mapToInt(Integer::intValue).filter(n -> n >= 0).sum();
            boolean fillKnown = keepFillLeftBySquad.values().stream().anyMatch(n -> n >= 0);
            Workshop depot = keepWorkshop;
            boolean chest = depot != null && depot.chestStands(world);
            int logs = keepLogsCarried.values().stream().mapToInt(Integer::intValue).sum()
                    + chainOfCommand.stockpile().map(Stockpile::logs).orElse(0);
            // Flat: the next of the squads' farm and grove ground off its level is the next to flatten (Flatten.next).
            if (digLeft == 0 && keepOreAside() == 0) {
                chainOfCommand.onFlattened(b -> groundNear((b.minX() + b.maxX()) / 2, (b.minZ() + b.maxZ()) / 2));
            }
            sites.add(new Site(Site.Kind.KEEP, "the keep", List.of(
                    new Site.Standard(Site.Level.AIRHEAD, "the depot stocked", chest && logs >= Keep.LOGS_WANTED,
                            (chest ? "its chest stands" : "no chest standing") + ", " + logs + " of " + Keep.LOGS_WANTED + " logs"),
                    new Site.Standard(Site.Level.BUILDUP, "the ground flattened", digLeft == 0 && keepOreAside() == 0,
                            digLeft + " blocks to dig" + (keepOreAside() > 0 ? ", " + keepOreAside() + " ore set aside for "
                                    + (ironPickaxe() ? "the iron pickaxe" : "an iron pickaxe, which nobody carries") : "")),
                    new Site.Standard(Site.Level.BUILDUP, "the slab laid", fillKnown && fillLeft == 0,
                            fillKnown ? fillLeft + " cells to fill" : "not begun: the ground first"))));
        }
        // The mine the squads work - the keep's, sited by a squad - else the camp's.
        Optional<Staircase> stairs = squadStairs.values().stream().findFirst().or(this::campStairs);
        if (stairs.isPresent()) {
            Workshop landing = Workshop.mine(stairs.get());
            boolean fitted = landing.stands(world, Workstation.CRAFTING_TABLE) && landing.stands(world, Workstation.FURNACE);
            boolean cut = stairsDone.get() || stairs.get().stepsCut(world) == stairs.get().steps();
            sites.add(new Site(Site.Kind.MINE_BASE, "the mine base", List.of(
                    new Site.Standard(Site.Level.SHELTER, "the stairs cut down", cut, cut ? "down to y=" + stairs.get().bottom()
                            : stairs.get().stepsCut(world) + " of " + stairs.get().steps() + " steps"),
                    new Site.Standard(Site.Level.AIRHEAD, "the landing's table and furnace", fitted, fitted ? "both stand" : "not both standing"))));
        }
        return List.copyOf(sites);
    }

    private Optional<Foothold> measureFoothold() {
        List<SoldierStatus> everyone = activeSoldiers.values().stream().map(DeterministicSoldier::status).toList();
        if (everyone.isEmpty()) {
            return Optional.empty();
        }
        // The camp's ground found once and kept: found again from where the squads are, it moves
        // onto a hut roof one time and back the next, and every measure moves with it.
        // Or set: a camp put at a height on purpose - the operator's at sea level - has its ground there, not read off a
        // surface. Live (field539-540), the camp heaped with spoil to y=71 read its ground at y=69, and its upkeep took the
        // heaps for the ground it keeps level.
        if (campGround == null && camp.length > 2) {
            campGround = new BlockPos(camp[0], camp[2], camp[1]);
        }
        if (campGround == null) {
            // Near the surface, found from above, not near where the squads are: live, restarted at
            // night with every soldier down the mine, the camp's ground was read off a cave floor at
            // y=28, and the camp's workshop sited there with no chest.
            int[] surfaces = IntStream.rangeClosed(-WALL_RADIUS, WALL_RADIUS).boxed()
                    .flatMap(dx -> IntStream.rangeClosed(-WALL_RADIUS, WALL_RADIUS)
                            .mapToObj(dz -> TerrainSurveyor.surface(world, camp[0] + dx, camp[1] + dz, Keep.SKY_TOP)))
                    .flatMap(Optional::stream).mapToInt(BlockPos::y).sorted().toArray();
            if (surfaces.length == 0) {
                return Optional.empty();
            }
            campGround = typicalGround(camp[0], camp[1], surfaces[surfaces.length / 2]).orElse(null);
            if (campGround == null) {
                return Optional.empty();
            }
            LOG.info("[Foothold] the camp's ground is {}", campGround);
        }
        BlockPos at = campGround;
        foragers.lookOver(world, campGround, clockwork.millis(), chainOfCommand::onRipeWheat);
        List<Foothold.Standard> standards = new ArrayList<>();
        // The cells SECURE lights, no others: one measure for the standard and the task that meets it.
        int dark = offTheWall(Lighting.darkCells(world, at, SECURE_RADIUS)).size();
        // Torches are made from logs: with none carried and no tree in sight, lighting waits on wood
        // grown first - the lookup done for the platoon leader, which otherwise sends squads to SECURE with nothing to make torches from.
        int logs = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(Kit::logs).orElse(0)).sum();
        int torches = torchesCarried.values().stream().mapToInt(Integer::intValue).sum();
        boolean noWood = dark > 0 && torches == 0 && logs < TORCH_LOGS && !chainOfCommand.woodSeen();
        standards.add(new Foothold.Standard(2, "the camp's ground lit", dark == 0,
                dark + " walkable cells within " + SECURE_RADIUS + " blocks still dark"
                        + (noWood ? "; no logs to make torches from and no trees in sight - grow them first" : ""),
                noWood ? "TREE_FARM" : "SECURE"));
        // The camp's wall as kept on disk, whether or not a squad is ordered to it this run - for the masons
        // and the ground work, not a standard: the wall is no longer one of the camp's (operator, 2026-11-20).
        wallStore.keys().stream().filter(k -> Math.hypot(Integer.parseInt(k.split(",")[0]) - camp[0],
                        Integer.parseInt(k.split(",")[1]) - camp[1]) <= WALL_RADIUS)
                .forEach(k -> walls.computeIfAbsent(k, key -> wallStore.load(key).orElseThrow()));
        // The one the world shows most built, of the layouts round the camp: one was laid out for each place FORTIFY was ever ordered near it.
        // Of two as built, the more of stone: and that's the one the squads finish, so the standard
        // and the work are one wall. Live, two closed layouts round the camp were counted by turns,
        // and the masons repointed the one with 23 dirt blocks while the camp's read 1.
        Optional<Map.Entry<String, List<BlockPos>>> best = walls.entrySet().stream()
                .filter(e -> Math.hypot(Integer.parseInt(e.getKey().split(",")[0]) - camp[0], Integer.parseInt(e.getKey().split(",")[1]) - camp[1])
                        <= WALL_RADIUS)
                .min(Comparator.<Map.Entry<String, List<BlockPos>>>comparingInt(e -> Perimeter.open(world, e.getValue()).size())
                        .thenComparingLong(e -> e.getValue().stream().filter(c -> world.isSoil(c.x(), c.y(), c.z())).count())
                        .thenComparing(Map.Entry::getKey));
        campWallKey = best.map(Map.Entry::getKey).orElse(null);
        List<BlockPos> wall = best.map(Map.Entry::getValue).orElse(null);
        int wallLeft = wall == null ? -1 : Perimeter.open(world, wall).size();
        // Of stone: an enderman picks a dirt block out of a wall and opens a hole (backlog/building-guide.md).
        Set<BlockPos> wallCells = wall == null ? Set.of() : Set.copyOf(wall);
        long wallSoft = wall == null ? 0 : wall.stream().filter(c -> softInWall(c, camp[0], camp[1], wallCells)).count();
        campWalled = wall == null || wallLeft == 0 && wallSoft == 0;
        long hungry =everyone.stream().filter(s -> s.condition().map(SoldierErrands::wantsFood).orElse(false)).count();
        // The camp's farm (CampFarm), with its chunk farm.
        FarmPlan keptFarm = campFarms.kept(world, at, camp[0] + "," + camp[1], roster.squads().stream().mapToInt(sq -> sq.soldiers().size()).sum(),
                true); // now, its bucket made as its farmer's tool (operator, 2026-11-20: the high-density farms as soon as can be)
        campFarm = keptFarm;
        Optional<FarmPlan> farm = Optional.ofNullable(keptFarm);
        long growing = farm.map(f -> f.plot().stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).isPresent()).count()).orElse(0L);
        // Safety first: food is buildup, behind the wall and the weapons (the operator's). Unless
        // the force's survival is at stake - most of it hungry with nothing to eat, or one starving
        // one hit from death - then food stands beside the wall, and the platoon leader does what it takes.
        // Live, both squads walled the camp while all eight soldiers went hungry, health falling to 1.
        boolean starving = hungry * 2 > everyone.size() || everyone.stream().anyMatch(CrossLevel::critical);
        int foodPhase = starving ? 2 : 3;
        // Met when the farm grows and the force eats: wheat planted today feeds nobody for days.
        standards.add(new Foothold.Standard(foodPhase, "food", growing >= FARM_GROWING && !starving,
                // Where: a coordinate written out is one the model copies, and here it should - live,
                // told only a count, it sent a squad to farm 50 blocks off, where the count never sees it.
                (farm.isEmpty() ? "no ground to farm by water near the camp" : growing + " of " + farm.get().plot().size()
                        + " cells of the camp's farm ground, by the water at x=" + farm.get().water().x() + " z=" + farm.get().water().z()
                        + ", growing wheat")
                        + "; " + hungry + " of " + everyone.size() + " soldiers hungry with nothing to eat", "FARM", farm.map(f -> new Position(f.water().x() + 0.5, f.water().y(), f.water().z() + 0.5))));
        Optional<TreeFarm> grove = campTrees.grove(world, at, campFarm, clockwork.millis()).or(() -> TreeFarm.site(world, at, CAMP_RADIUS)); // close (CampTrees)
        // Right after food (operator, 2026-11-14: tree farms just after the wheat farms - a camp goes through a lot of wood): the camp's grove and the keep's together.
        TreeFarm keepTrees = campTrees.chunk().isPresent() ? null : keepGrove; // not walked 200 blocks to while the camp has its own
        long trees = grove.map(g -> g.plot().stream().filter(c -> TreeFarm.planted(world, c)).count()).orElse(0L)
                + (keepTrees == null ? 0 : keepTrees.plot().stream().filter(c -> TreeFarm.planted(world, c)).count());
        // Every cell the groves hold, 16 at the least and 96 at the most (operator, 2026-11-20: grow more trees; 48 met in field593): a squad on TREE_FARM till they're full.
        long wanted = Math.min(TREES_MOST, Math.max(TREES_GROWING, grove.map(g -> g.plot().size()).orElse(0) + (keepTrees == null ? 0 : keepTrees.plot().size())));
        Optional<BlockPos> bare = Stream.concat(grove.stream(), Stream.ofNullable(keepTrees)).flatMap(g -> g.plot().stream()).filter(c -> !TreeFarm.planted(world, c)).findFirst(); // a cell, not the centre - the camp's grove is the camp's
        standards.add(new Foothold.Standard(3, "wood that grows back", trees >= wanted, trees + " of " + wanted + " saplings or trees planted by the camp and the keep's grove",
                "TREE_FARM", bare.map(c -> new Position(c.x() + 0.5, c.y(), c.z() + 0.5)))); // where: live (field570), sent to the camp's dug centre, "no open dirt or grass"
        // The camp's mine as the world shows it, as well as a cutter at work this run: live, after a restart with no MINE order, a mine cut to the bottom read "no mine yet".
        Optional<Staircase> campMine = campStairs();
        int cutNow = Math.max(stairsCut.get(), campMine.map(s -> s.stepsCut(world)).orElse(0));
        boolean mined = stairsDone.get() || campMine.map(s -> s.stepsCut(world) == s.steps()).orElse(false);
        // Once seen cut to the bottom, it stays cut: with nobody down there its deep chunks unload,
        // and counted from what's loaded the mine read 43 steps down, then met, then 43 again - live,
        // "stone and iron" held 35% of a day it was met all of.
        if (mined) {
            stairsDone.set(true);
        }
        standards.add(new Foothold.Standard(3, "stone and iron", mined,
                cutNow == 0 ? "no mine yet" : mined ? "a mine cut down to the iron" : "a mine " + cutNow + " steps down",
                "MINE"));
        // Last of the buildup: food, then wood, then stone and iron come first. Its floor paved at its own height (the operator's: not cut to sea level), once it's walled:
        // FORTIFY's last work, the soil inside replaced with stone.
        int unpaved = floorToPave().size();
        standards.add(new Foothold.Standard(3, "the camp's floor paved", unpaved == 0,
                unpaved + " cells of the floor inside the wall still soil, to pave with stone", "FORTIFY"));
        int stoneSword = ItemClassRegistry.meleeRank("stone_sword");
        everyone.forEach(s -> cutOff.update(s, clockwork.millis())); // the cut off reported, not counted on (CutOff)
        List<String> away = everyone.stream().map(SoldierStatus::name).filter(n -> cutOff.cutOff(n, clockwork.millis())).toList();
        everyone.stream().filter(s -> away.contains(s.name())).forEach(s -> worldRecords.record(chunkCache::stateIdAt, s, clockwork.millis()));
        long armed = everyone.stream().filter(s -> !away.contains(s.name())).filter(s -> cutOff.armed(s.name(), s.condition().flatMap(c -> c.kit())
                .flatMap(k -> k.weapon()).filter(w -> !w.equals("bow")).map(w -> {
                    try {
                        return ItemClassRegistry.meleeRank(w) >= stoneSword;
                    } catch (IllegalArgumentException notMelee) {
                        return false;
                    }
                }).orElse(false), clockwork.millis())).count();
        // A weapon each is shelter's other half: even a wooden sword wins most fights a fist loses.
        standards.add(new Foothold.Standard(2, "every soldier armed", armed == everyone.size() - away.size(), armed + " of "
                + (everyone.size() - away.size()) + " carry a stone sword or better" + (away.isEmpty() ? "" : ", " + away + " cut off"), "STONE"));
        int armour = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(k -> k.armour()).orElse(0)).sum();
        standards.add(new Foothold.Standard(4, "every soldier armoured",
                armour == everyone.size() * Kit.ARMOUR_SLOTS, armour + " of " + everyone.size() * Kit.ARMOUR_SLOTS + " armour slots filled (copper or iron, from ore)",
                "MINE"));
        measureRoutes(at);
        sweepWanted(wall, farm.orElse(null));
        return Optional.of(new Foothold(new Position(at.x(), at.y(), at.z()), List.copyOf(standards), supplyPoint.or(() -> chestOf(campWorkshop)).or(() -> chestOf(keepWorkshop))));
    }

    /**
     * The ground round ({@code cx}, {@code cz}): the ground most of it stands on, not whatever's tallest at its centre -
     * where the squads have built hut on hut, a roof would be its ground, the wall laid round a roof
     * and the light measured on one. The typical height of the ground across the camp, then the
     * standing cell nearest the centre at about that height.
     */
    private Optional<BlockPos> typicalGround(int cx, int cz, int nearY) {
        int[] camp = {cx, cz};
        List<BlockPos> columns = new ArrayList<>();
        for (int dx = -WALL_RADIUS; dx <= WALL_RADIUS; dx++) {
            for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
                Staircase.headAt(world, camp[0] + dx, camp[1] + dz, nearY).ifPresent(columns::add);
            }
        }
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        int typical = columns.stream().mapToInt(BlockPos::y).sorted().toArray()[columns.size() / 2];
        return columns.stream().filter(c -> Math.abs(c.y() - typical) <= 1)
                .min(Comparator.comparingInt(c -> Math.abs(c.x() - camp[0]) + Math.abs(c.z() - camp[1])));
    }

    /** A SECURE plan this close to done, unchanged for {@link #NEAR_DONE_MILLIS}, is done. */
    static final int NEAR_DONE_TORCHES = 2;
    static final long NEAR_DONE_MILLIS = 120_000;

    /** How long after a staircase couldn't be sited it's looked for again: the water or lava may have gone. */
    static final long STAIRS_RETRY_MILLIS = 60_000;
    /** How far above or below its objective a soldier may be for its squad's survey to be taken from it. */
    static final int SURVEY_FROM_HEIGHT = 16;
    /**
     * The ground's height at the column nearest ({@code x}, {@code z}) within {@link #GROUND_NEAR} it
     * can be read at, from the sky down - a site's own column may be water, or a hazard at its top.
     * Empty when none round it reads.
     */
    private OptionalInt groundNear(int x, int z) {
        for (int r = 0; r <= GROUND_NEAR; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !world.isLoaded(x + dx, z + dz)) {
                        continue;
                    }
                    Optional<BlockPos> at = TerrainSurveyor.surface(world, x + dx, z + dz, Keep.SKY_TOP);
                    if (at.isPresent()) {
                        return OptionalInt.of(at.get().y());
                    }
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Ticks to dig column ({@code x}, {@code z}) from {@code from} up to {@code to}: each block's break time
     * with the crew's usual kit - a stone pickaxe, stone shovel and axe, on the ground ({@link BreakTime}) -
     * and a few ticks a block for the stand and the pickup.
     */
    private int columnTicks(int x, int z, int from, int to) {
        int ticks = 0;
        for (int y = from; y <= to; y++) {
            if (!world.solidHere(x, y, z)) {
                continue;
            }
            ticks += BLOCK_HANDLING_TICKS + Math.min(Clearing.MAX_DIG_TICKS,
                    world.hardness(x, y, z).map(h -> h.ticks(Hardness.STONE_TIER, STONE_TOOL_SPEED)).orElse(0));
        }
        return ticks;
    }

    /** Ticks a block costs past its break: the stand, the swing's start and the pickup. */
    static final int BLOCK_HANDLING_TICKS = 4;
    /** A stone shovel's or axe's speed. */
    static final double STONE_TOOL_SPEED = 4;

    /** How far round a site its ground is looked for. */
    static final int GROUND_NEAR = 8;
    /** How far under the ground at its site a kept mine's head may be before it reads sited from below. */
    static final int BURIED_HEAD = 16;

    /** How long a soldier is first kept off a task it stalled on (the squad's register's finding). */
    static final long RELIEVED_MILLIS = 5 * 60_000;

    /** How far round the camp its tree farm is looked for. */
    private static final int CAMP_RADIUS = 24;

    /** Farm cells a farmer couldn't work, passed over by every farmer for a while. */
    private final Setbacks<BlockPos> farmCellsFailed =
            new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS);
    /** How many cells the flood that finds a farm's walkable ground may expand: the ground within some 50 blocks. */
    private static final int FARM_REACH_BUDGET = 40_000;
    /** The camp's farm ground by water, as the foothold last measured it: kept by squads short of food. */
    private volatile FarmPlan campFarm;
    /** The camp's tree chunk, within 64 blocks: its grove (operator, 2026-11-21: the renewables close by). */
    private final CampTrees campTrees = new CampTrees();
    /** The camp's farm, sited, grown and kept. */
    private final CampFarm campFarms;
    /** Whether a farmer keeping a farm carries a bucket: the camp's chunk farm is taken in then. */
    private volatile boolean bucketOnFarm;
    /** The camp's ground, found once. */
    private BlockPos campGround;
    /** Farm cells growing wheat for the camp's food to count as met. */
    private static final int FARM_GROWING = 12;
    /** Trees growing for wood to be no worry (operator, 2026-11-14: with 16 or 32 growing, zero problems with wood), and the most asked. */
    private static final int TREES_GROWING = 16, TREES_MOST = 96, SEEDS_DRAWN = 64; // seeds a farmer out of them draws
    /** How far out from a FORTIFY objective its wall stands. */
    static final int WALL_RADIUS = 8;
    /** How far above and below the camp's centre its ground is kept from digging. */
    private static final int CAMP_GROUND_DEPTH = 6;
    /** Fewer blocks than this carried, and a mason goes and digs dirt for the wall. */
    static final int WALL_BLOCKS_LOW = 4;
    /** A mason digging for the wall digs this many before it goes back to building. */
    static final int WALL_BLOCKS_LOAD = 32;
    /** Masons digging a load of dirt for the wall - squad loop only. */
    private final Set<String> diggingForWall = new HashSet<>();
    /** How close behind the cutter, across and up the stairs, the rest of a squad mining at night keeps. */
    static final double MINE_FOLLOW = 3;
    /** How often, hands free, a soldier checks it's wearing its best and holding its weapon: the server's answer has time to come back. */
    static final int STAND_TO_TICKS = 40;
    /** Each connected soldier's hunting, by name. */
    private final Map<String, Hunter> hunters = new ConcurrentHashMap<>();
    /** Soldiers free to hunt when hungry: by day, out of contact, not walling in a base - squad loop writes, tick reads. */
    private final Set<String> huntAllowed = ConcurrentHashMap.newKeySet();
    /** Where each soldier looks for game: round its squad's objective, so a run of hunts doesn't carry it off. */
    private final Map<String, Position> huntGrounds = new ConcurrentHashMap<>();

    /** Each connected soldier's tree planting, by name. */
    private final Map<String, Forester> foresters = new ConcurrentHashMap<>();

    /** Whether {@code transfer}'s giver couldn't get to its receiver lately (Handover.barred). */
    private boolean barredHandover(CrossLevel.Transfer transfer) {
        Handover giver = handovers.get(transfer.giver());
        return giver != null && giver.barred(transfer.receiver());
    }
    /** The tree farm each soldier plants, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, TreeFarm> planting = new ConcurrentHashMap<>();
    /** Each connected soldier's staircase cutting, by name. */
    private final Map<String, Miner> miners = new ConcurrentHashMap<>();
    /** The day's deaths and idlers, reviewed at each dawn. */
    private final AfterActionReview afterActionReview = new AfterActionReview();
    /** Where each soldier's food goes: sampled each tick, reviewed at dawn. */
    private final FoodLedger foodLedger = new FoodLedger();
    /** Cutters wanting a tool, sent to their mine's landing to make it - tick writes, squad loop reads. */
    /** Soldiers walking a PATROL, and the leg of its round each is on - squad loop writes, tick reads. */
    private final Map<String, Integer> patrolLegs = new ConcurrentHashMap<>();
    /** How far out from a PATROL objective its round goes: just inside the wall line. */
    static final int PATROL_RADIUS = WALL_RADIUS - 2;
    /** Where a soldier walks to fit its workshop's chest, while it does - tick writes, squad loop reads. */
    private final Map<String, Optional<Position>> storeWalks = new ConcurrentHashMap<>();

    /** Masons out of blocks sent to draw cobblestone from the stockpile - squad loop writes, tick reads. */
    private final Set<String> drawingStone = ConcurrentHashMap.newKeySet();
    /** Each soldier's fitting of its workshop's chest, by name. */
    private final Map<String, Fitter> storeFitters = new ConcurrentHashMap<>();
    /** Soldiers short of food with no means of their own to get any: wheat, game in sight. Tick writes, squad loop reads. */
    private final Set<String> foodless = ConcurrentHashMap.newKeySet();
    /** The foragers, and the camp's ripe wheat they forage (app.Foragers). */
    private final Foragers foragers = new Foragers();
    /** Whether the camp's stockpile can be got at, and where it's moved to if not (app.StockpileWatch). */
    private final StockpileWatch stockpileWatch;
    /** The workshop each soldier crafts at (backlog/sites.md): its squad's, or the mine base below ground. */
    private final Map<String, Workshop> workshops = new ConcurrentHashMap<>();
    /** The camp's own workshop, at its ground: every squad near the camp crafts there. */
    private volatile Workshop campWorkshop;
    /** The keep's workshop, at its depot, shared by every squad building it - squad loop only. */
    private volatile Workshop keepWorkshop;
    /**
     * The flatten's other depots, along its edge (Flatten.depots): where its builders put their
     * loads in, the nearest of them or the main one - squad loops write, ticks read. Draws are from
     * the main depot, whose chests hold the stores.
     */
    private volatile List<Workshop> keepOutposts = List.of();

    /** The flatten's depot nearest {@code at}: the main one or an outpost. */
    private Workshop nearestDepot(Position at) {
        return nearestOf(keepWorkshop, keepOutposts, at);
    }
    /** The depot ground last said to have nowhere for the workshop, so it's said once - squad loops. */
    private volatile BlockPos keepWorkshopNowhere;
    /**
     * Free slots at which a keep builder's pack is a full load, to unload at the depot: room left to
     * work with (operator, 2026-11-14: soldiers keep their packs open so they can move things around).
     * A tool made at a table passes through planks, sticks and the table itself; in the squad
     * simulation a builder with two slots free ran out of pack halfway through a sword.
     */
    static final int KEEP_FULL_AT = 4;

    /** What of the depot's kinds each surface chest held when it was last open, as {@link #chestsSeen}. */
    private final Map<BlockPos, Map<ItemClassRegistry.ItemClass, Integer>> chestStock = new ConcurrentHashMap<>();

    /** How many of {@code kind} the stockpile's surface chests hold, by their last look. */
    /** What the chests seen hold of {@code kind}, for a load going to {@code deposit} - a farm's chest has its own seed (SoldierErrands.stocked). */
    private int stockedAt(ItemClassRegistry.ItemClass kind, Optional<Position> deposit) {
        return SoldierErrands.stocked(chestStock, chestsSeen.keySet(), kind, campFarms.chests(), deposit.map(d -> BlockPos.containing(d.x(), d.y(), d.z())));
    }
    /** Chests a depot is laid out for: the dig's stone for the slab, its ore, wood and spare tools. */
    static final int DEPOT_CHESTS = 24;
    /** The cells of the keep's depot's chests, in the order they're filled - squad loops write, ticks read. */
    private volatile List<BlockPos> keepChests = List.of();
    /** Which of {@link #keepChests} is being filled. */
    private volatile int keepChestAt;

    /**
     * A keep builder's load wouldn't go into the depot's chest at {@code full}
     * ({@link Depositor#full}). The next of the depot's chests is the one to fit and fill - once, for
     * the chest being filled. Live, the depot's first chest filled with one builder's load, and every
     * builder after it walked to it, failed, and walked back.
     */
    /**
     * A workshop's chest that wouldn't take a load it reached and opened: its chest is the
     * next of its row ({@link Workshop#chestCells}), fitted by the next soldier there with a full
     * pack, as the depot's is. Live, the camp's one chest filled and five soldiers of two squads
     * walked their loads to it and back, one failed trip after another.
     */
    private void workshopChestFull(Workshop shop, Position full) {
        BlockPos cell = BlockPos.containing(full.x(), full.y(), full.z());
        // A chest that isn't standing isn't full: a load that didn't go in is the chest gone, to be put up again.
        if (!shop.chest().equals(Optional.of(cell)) || !shop.chestStands(world)) {
            return;
        }
        // Once, when it's found full: the row is laid out from the chest it has, so its "next" can be
        // one already full - the first of it not known full is. Live, each depositor's tick said it
        // again, twenty a second, naming a full chest as the next.
        if (!fullChests.add(cell)) {
            return;
        }
        saveFullChests();
        List<BlockPos> row = shop.chestCells(world, chestClaims(), DEPOT_CHESTS);
        Optional<BlockPos> free = row.stream().filter(c -> !fullChests.contains(c)).findFirst();
        if (free.isEmpty()) {
            LOG.warn("[Workshop] the chest at {} is full - and every chest of its row, {} of them", cell, row.size());
            return;
        }
        Workshop next = shop.withChest(free.get());
        // The camp's: the common stockpile, re-read by every squad loop at the camp. A squad's own, its squad loop's to change: noted here, moved on there (nextChestIfFull).
        if (shop.equals(campWorkshop)) {
            campWorkshop = next;
        }
        LOG.info("[Workshop] the chest at {} is full - the next goes up at {} ({} of {})", cell, free.get(), row.indexOf(free.get()) + 1, row.size());
    }

    /** Workshop chests found full - ticks add, squad loops read; kept on disk, and one a load went into since taken off. */
    private final Set<BlockPos> fullChests = ConcurrentHashMap.newKeySet();
    /** The key the full chests are kept under. */
    private static final String FULL_CHESTS = "full";
    /** Chests on the ground to flatten seen empty: kept on disk, so a restart doesn't look in each again. */
    private static final String SALVAGED = "salvaged";
    private final Set<BlockPos> salvaged = ConcurrentHashMap.newKeySet();

    /** The full chests to disk, as they are now. */
    private void saveFullChests() {
        // One write at a time: soldiers' ticks find chests full on threads of their own.
        synchronized (chestStore) {
            chestStore.save(FULL_CHESTS, List.copyOf(fullChests));
        }
    }

    /**
     * Cells the model took as a block on a soldier's word (DeterministicSoldier.takeSnag), when - every soldier's,
     * since the model is shared - and cells taken back from it, when.
     */
    private final Map<BlockPos, Long> snags = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> snagsTakenBack = new ConcurrentHashMap<>();
    /** How long a snag taken back isn't marked again: what stops the body there isn't a block. */
    static final long SNAG_TAKEN_BACK_MILLIS = 120_000;

    /**
     * A snag that wouldn't break - the server never answered the break, so the block the model took on a soldier's
     * word isn't there - taken back, whichever breaker tried it (backlog/world-model-stale.md). Live (field443), Bob
     * had 45 cells round him made snags in a night and Pete failed one 46 times: only the doorway's breaker on the
     * newest snag ever took one back, and the rest walled them in.
     */
    private Optional<BlockPos> takeBackSnag(String name, Breacher breacher) {
        Optional<BlockPos> failed = breacher.takeFailure().filter(snags::containsKey);
        failed.ifPresent(cell -> {
            snags.remove(cell);
            snagsTakenBack.put(cell, clockwork.millis());
            chunkCache.setBlock(cell.x(), cell.y(), cell.z(), 0);
            LOG.warn("[World] {}'s snag at {} wouldn't break - not a block; the model takes it back", name, cell);
        });
        return failed;
    }

    /** What each surface chest held when a load last went in: the stockpile is all of them, not one. */
    private final Map<BlockPos, Stockpile> chestsSeen = new ConcurrentHashMap<>();

    /** How far off a depot's stand its dump is: clear of its chests, which are within reach of it. */
    static final int DUMP_OFF = Workshop.CHEST_REACH + 2;

    /** The nearest stockpile chest, not to be salvaged, last seen holding {@code kind} - to {@code from}. */
    private Optional<Position> chestHolding(ItemClass kind, Position from) {
        return chestStock.entrySet().stream()
                .filter(e -> chestsSeen.containsKey(e.getKey()) && !condemned.contains(e.getKey())
                        && e.getValue().getOrDefault(kind, 0) > 0)
                .map(e -> new Position(e.getKey().x() + 0.5, e.getKey().y(), e.getKey().z() + 0.5))
                .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x() - from.x(), p.z() - from.z())));
    }

    /** The nearest surface chest last seen holding {@code metal} - raw ore or an ingot - to {@code from}. */
    private Optional<Position> metalChest(ItemClass metal, Position from) {
        java.util.function.ToIntFunction<Stockpile> count = switch (metal) {
            case RAW_IRON -> Stockpile::rawIron;
            case RAW_COPPER -> Stockpile::rawCopper;
            case IRON_INGOT -> Stockpile::ironIngots;
            case COPPER_INGOT -> Stockpile::copperIngots;
            case LOG -> Stockpile::logs;
            case COBBLESTONE -> Stockpile::stone;
            default -> throw new IllegalArgumentException(metal + " isn't drawn from the chest holding it");
        };
        // A chest the world no longer shows is forgotten, not walked to: live (field324), a draw went
        // seven times to a mine's old stash at y=24 that wasn't there, and failed each time.
        forgetChestsGone(chestsSeen, world);
        return chestsSeen.entrySet().stream().filter(e -> count.applyAsInt(e.getValue()) > 0)
                .map(e -> new Position(e.getKey().x() + 0.5, e.getKey().y(), e.getKey().z() + 0.5))
                .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x() - from.x(), p.z() - from.z())));
    }

    /** Where keep builders threw what their packs shed, and when - tick threads write, scavengers read. */
    private final Map<Position, Long> thrownAt = new ConcurrentHashMap<>();
    /** How long thrown things lie: a dropped stack despawns in five minutes. */
    static final long THROWN_LIES_MILLIS = 300_000;
    /** How near a throw an item is the throw's. */
    static final double THROWN_NEAR = 3;

    /**
     * Whether an item at {@code item} lies where a builder threw what its pack shed: left, not fetched
     * by a squadmate's scavenger and carried back to be shed again.
     */
    private boolean thrownNear(Position item) {
        long now = clockwork.millis();
        thrownAt.values().removeIf(at -> now - at > THROWN_LIES_MILLIS);
        return thrownAt.keySet().stream().anyMatch(p -> Math.hypot(p.x() - item.x(), p.z() - item.z()) <= THROWN_NEAR
                && Math.abs(p.y() - item.y()) <= THROWN_NEAR);
    }

    /** Whether an item at {@code item} lies at a depot's dump: left there, not picked up again. */
    private boolean atADump(Position item) {
        Workshop keep = keepWorkshop;
        if (keep == null) {
            return false;
        }
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(keep), keepOutposts.stream()).anyMatch(depot -> Math.hypot(item.x() - (depot.stand().x() - DUMP_OFF + 0.5),
                item.z() - (depot.stand().z() + 0.5)) <= DUMP_LEAVE);
    }

    /** How near a dump what lies there is left by the scavengers. */
    static final double DUMP_LEAVE = 4;

    /** {@code shop}, its chest the next of its row while the one it has is full. */
    private Workshop nextChestIfFull(Workshop shop) {
        if (shop == null || shop.chest().filter(fullChests::contains).isEmpty()) {
            return shop;
        }
        List<BlockPos> row = shop.chestCells(world, chestClaims(), DEPOT_CHESTS);
        return row.stream().filter(c -> !fullChests.contains(c)).findFirst().map(shop::withChest).orElse(shop);
    }

    private void depotChestFull(Position full) {
        // Known full, whatever comes next: no load is taken to it again (the depositor's notFull). A chest that never
        // stood isn't (live, field480: one Dana couldn't put up was logged full and then forgotten).
        BlockPos cell = BlockPos.containing(full.x(), full.y(), full.z());
        if ((!world.isLoaded(cell.x(), cell.z()) || world.isChest(cell.x(), cell.y(), cell.z())) && fullChests.add(cell)) {
            saveFullChests();
        }
        synchronized (keepPlanLock) {
            List<BlockPos> cells = keepChests;
            int at = keepChestAt;
            if (at + 1 < cells.size() && cells.get(at).equals(BlockPos.containing(full.x(), full.y(), full.z()))) {
                keepChestAt = at + 1;
                LOG.info("[Keep] the depot's chest at {} is full - the next goes up at {} ({} of {})", cells.get(at), cells.get(at + 1), at + 2,
                        cells.size());
            }
        }
    }
    /** Whether no wall is going up round the camp - none laid out, or closed and all stone: foothold check writes, squad loop reads. */
    private volatile boolean campWalled = true;
    /**
     * The steps the soldiers have built and not yet taken down - dirt or cobblestone, wherever a
     * route or a pillar put one - ticks write, squad loops read and prune what's been dug. Known as
     * scaffolding by where it went, not by its block: a dirt step reads as the ground's dirt.
     */
    private final Set<BlockPos> scaffolding = ConcurrentHashMap.newKeySet();
    /** When each step in {@link #scaffolding} was put down - ticks write, squad loops read and prune. */
    private final Map<BlockPos, Long> stepPlacedAt = new ConcurrentHashMap<>();
    /** How long a step put down is kept on the record though it reads open: the server's word on it may be on its way. */
    static final long STEP_FRESH_MILLIS = 20_000;

    /**
     * The steps taken down: not solid, and not put down in the last {@link #STEP_FRESH_MILLIS} - a step
     * the server hasn't confirmed yet reads open for a moment, and dropped then it lands as ground.
     */
    private void pruneSteps() {
        long now = clockwork.millis();
        long fresh = now - STEP_FRESH_MILLIS;
        scaffolding.removeIf(c -> !world.isSolid(c.x(), c.y(), c.z()) && stepPlacedAt.getOrDefault(c, Long.MIN_VALUE) < fresh);
        stepPlacedAt.keySet().removeIf(c -> !scaffolding.contains(c));
        // To disk when it's changed, a few seconds apart at most: a step a moment old is worth losing.
        Set<BlockPos> steps = Set.copyOf(scaffolding);
        if (!steps.equals(stepsSaved) && now - stepsSavedAt >= STEPS_SAVE_MILLIS) {
            stepStore.save(STEPS, List.copyOf(steps));
            stepsSaved = steps;
            stepsSavedAt = now;
        }
    }

    /** The key the steps are kept under, and how often at most they're written. */
    private static final String STEPS = "placed";
    static final long STEPS_SAVE_MILLIS = 5_000;
    /** The steps as last written, and when - the squad loop's own. */
    private volatile Set<BlockPos> stepsSaved = Set.of();
    private volatile long stepsSavedAt = Long.MIN_VALUE / 2;
    /** How near the camp an objective is for its squad to craft at the camp's workshop. */
    private static final int CAMP_WORKSHOP_RANGE = 48;
    /** Ticks to each soldier's next stand-to check. */
    private final Map<String, Integer> standTo = new ConcurrentHashMap<>();
    /** Soldiers stood still by day: an alarm each spell. */
    private final IdleWatch idleWatch = new IdleWatch(), withoutWork = new IdleWatch("without work");
    private final PoliceCall policeCall = new PoliceCall(); // one without work, or stood a minute on a CANTCO, takes back a step left
    private volatile List<BlockPos> campCut = List.of(); // the camp's ground over its level, as last measured: dirt from it first (CutFirst)
    private final Ledger ledger = new Ledger(); // what each act was planned to take against what it took, logged as [Ledger]
    private final CutOff cutOff = new CutOff(); // soldiers stuck at one spot with no route: reported, not counted on
    private WorldRecords worldRecords; // the ground round a soldier stuck a minute, kept for the simulation
    /** Soldiers whose last position wasn't sent for a value that isn't finite: said once a spell - ticks write. */
    private final Set<String> badPositionLogged = ConcurrentHashMap.newKeySet();

    /** Soldiers on top of one another - every connection's tick. */
    private final CrowdWatch crowdWatch = new CrowdWatch();
    /**
     * Each squad down its mine for the night, by its stairs' head height: below it, a soldier
     * waiting is hiding, as one in a patrol base is - not stuck. Written by the squad loop, read by
     * each soldier's tick.
     */
    private final Map<String, Integer> nightBelow = new ConcurrentHashMap<>();
    /** Each soldier's last seconds, read out when it dies. */
    private final Map<String, FlightRecorder> recorders = new ConcurrentHashMap<>();
    /** Deaths since the app started, by the server's death message key: what kills soldiers most. */
    private final Map<String, Integer> casualties = new ConcurrentSkipListMap<>();
    /** The staircase a soldier cuts, for the one soldier of a MINE squad cutting it - squad loop writes, tick reads. */
    private final Map<String, Staircase> cutting = new ConcurrentHashMap<>();
    /** Each soldier of a MINE squad, which wants a pickaxe for it - squad loop writes, tick reads. */
    private final Set<String> mining = ConcurrentHashMap.newKeySet();
    /** How the staircase went on the cutter's last tick - tick writes, the report reads. */
    private final Map<String, Miner.Progress> stairsProgress = new ConcurrentHashMap<>();
    /** Each connected soldier's lighting work, by name. */
    private final Map<String, Lamplighter> lamplighters = new ConcurrentHashMap<>();
    /** The torches each soldier's squad still has to put down, while its objective is SECURE - squad loop writes, tick reads. */
    private final Map<String, List<BlockPos>> lighting = new ConcurrentHashMap<>();
    /** The farm each soldier keeps, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, FarmPlan> farming = new ConcurrentHashMap<>();
    /**
     * Why each soldier ordered to farm has no farm: no ground by water at its site - squad loop
     * writes, tick reads. Said, so its squad leader sites it again: live, a farmer stood a day at a
     * site with no water near it, reporting it was in position.
     */
    private final Map<String, String> farmless = new ConcurrentHashMap<>();
    /** Soldiers already said to keep the camp's farm for a dry site - squad loop only. */
    private final Set<String> farmlessLogged = ConcurrentHashMap.newKeySet();
    /** The key of the camp's wall its standard measures: the squads at the camp work this one - measure writes, squad loop reads. */
    private volatile String campWallKey;
    /** What each soldier's squad is gathering, and around where - squad loop writes, tick reads; absent when nothing. */
    private final Map<String, Objective> gathering = new ConcurrentHashMap<>();
    /** No hostile this close to a soldier, nor any squadmate in contact, before it stops to make a sword. */
    private static final double REARM_CLEAR_RADIUS = 16;
    /** How far from the platoon leader's farm objective the plot's water may be, across and up or down. */
    private static final int FARM_SITE_RADIUS = 8;
    /** How far from its hut a soldier short of blocks digs dirt: clear of the walls and the ground under them. */
    private static final int SOIL_OFFSET = 6;
    /** How far round a TREE_FARM objective saplings go. */
    static final int TREE_FARM_RADIUS = 8;
    /** How far round a SECURE objective the ground is lit. */
    static final int SECURE_RADIUS = 12;
    /** Logs a soldier making torches from scratch uses: planks, sticks, a table, charcoal and its fuel. */
    private static final int TORCH_LOGS = 4;
    /** Cobblestone for the furnace that makes the charcoal. */
    private static final int TORCH_STONE = 8;
    /** Torches a soldier makes at a time for lighting ground: two logs' charcoal. */
    static final int LIGHTING_TORCHES = 8;
    /** A soldier being handed logs goes for dropped items this close - where a throw lands. */
    private static final double PICKUP_RADIUS = 4;

    private final OperatorOrders operatorOrders;
    /** The chest soldiers draw kit from, if the operator set one up (docs/military-doctrine.md §4). */
    private final Optional<Position> supplyPoint;

    public SoldierRunner(ChainOfCommand chainOfCommand, OperatorOrders operatorOrders,
                         @Value("${minecraft.supply.point}") String supplyPoint, Roster roster,
                         @Value("${minecraft.yield-to-experiments}") boolean yieldToExperiments,
                         @Value("${minecraft.camp}") String camp,
                         @Value("${minecraft.state-dir}") String stateDir) {
        this.roster = roster;
        this.wallStore = new WallStore(Path.of(stateDir));
        this.burns = new BurnStore(Path.of(stateDir), DAY_MILLIS);
        this.rationsBurn = burns.rations();
        this.logsBurn = burns.logs();
        this.campFarms = new CampFarm(new WallStore(Path.of(stateDir), "farm"), FARM_REACH_BUDGET);
        this.mineStore = new WallStore(Path.of(stateDir), "stairs");
        this.chestStore = new WallStore(Path.of(stateDir), "chests");
        this.dangerAreas = new DangerAreaBook(chestStore);
        this.storesBook = new StoresBook(chestStore);
        chestsSeen.putAll(storesBook.chests()); // every chest as last seen, not only those opened since
        storesBook.kept().ifPresent(chainOfCommand::onStores); // as last seen, till a chest is opened
        (this.phaseBook = new PhaseBook(chestStore)).restoreInto(chainOfCommand); (this.woodsBook = new WoodsBook(chestStore)).restoreInto(chainOfCommand);
        this.stepStore = new WallStore(Path.of(stateDir), "steps");
        this.worldRecords = new WorldRecords(Path.of(stateDir));
        stepStore.load(STEPS).ifPresent(scaffolding::addAll);
        chestStore.load(FULL_CHESTS).ifPresent(fullChests::addAll);
        chestStore.load(SALVAGED).ifPresent(salvaged::addAll);
        chestStore.load(DEAD_WELLS).ifPresent(deadWells::addAll);
        chestStore.load(MINES_TRIED).ifPresent(tried -> chainOfCommand.setMinesTried(tried.stream()
                .map(c -> new Position(c.x(), c.y(), c.z())).toList()));
        chestStore.load(MINE_SITE).filter(l -> l.size() == 1).map(List::getFirst)
                .ifPresent(c -> chainOfCommand.setKeepMine(new Position(c.x(), c.y(), c.z())));
        // A kept mine whose well is dead - found dried up or blocked before a restart - moves on at once:
        // live (field382), the night shift was sent back down the mine field381 had found dried up.
        // (The constructor's own chainOfCommand: the field isn't set yet.)
        Position kept = chainOfCommand.keepMine();
        if (deadWells.stream().anyMatch(w -> w.x() == (int) Math.floor(kept.x()) && w.z() == (int) Math.floor(kept.z()))) {
            chainOfCommand.onMineDriedUp((int) Math.floor(kept.x()), (int) Math.floor(kept.z())).ifPresent(site -> {
                chestStore.save(MINE_SITE, List.of(BlockPos.containing(site.x(), site.y(), site.z())));
                chestStore.save(MINES_TRIED, chainOfCommand.minesTried().stream().map(p -> BlockPos.containing(p.x(), p.y(), p.z())).toList());
            });
        }
        this.planBook = new PlanBook(Path.of(stateDir));
        this.scoreboard = new Scoreboard(Path.of(stateDir));
        this.camp = camp.isBlank() ? null : Arrays.stream(camp.split(",")).mapToInt(v -> Integer.parseInt(v.strip())).toArray();
        if (this.camp != null && this.camp.length != 2 && this.camp.length != 3) {
            throw new IllegalArgumentException("minecraft.camp is x,z or x,z,ground - not " + camp);
        }
        this.oldCamps = oldCamps(chestStore, this.camp);
        this.stockpileWatch = new StockpileWatch(chestStore);
        this.yieldToExperiments = yieldToExperiments;
        this.soldierDefinitions = roster.squads().stream()
                .flatMap(squad -> squad.soldiers().stream().map(name -> new SoldierDefinition(name, squad.name())))
                .toList();
        this.chainOfCommand = chainOfCommand;
        this.operatorOrders = operatorOrders;
        this.supplyPoint = Resupply.supplyPoint(supplyPoint);
        this.clockwork = Clockwork.REAL;
        this.connector = (name, listeners) -> MinecraftClient.join(HOST, PORT, name, listeners);
    }

    /** Where a soldier's connection comes from: the server, or a simulation's fake one. */
    interface Connector {
        CompletableFuture<PlaySession> join(String name, PlaySessionListeners listeners);
    }

    /** The clock and threads the soldiers run on. */
    private Clockwork clockwork;
    private Connector connector;
    /** Every periodic task's faults: none dies of one. Read off whichever clock is set. */
    private final Faults faults = new Faults(() -> clockwork.millis());

    /** Runs on {@code clockwork}'s time and threads, connecting through {@code connector}: a simulation's. */
    SoldierRunner simulated(Clockwork clockwork, Connector connector) {
        this.clockwork = clockwork;
        this.connector = connector;
        return this;
    }

    private final ChunkCache chunkCache = new ChunkCache();
    private final EntityTracker entityTracker = new EntityTracker();
    private final WorldModel world = new WorldModel(chunkCache, entityTracker);

    @Override
    public void run(String... args) {
        reconnects = clockwork.scheduler("reconnect");
        schedulers.add(reconnects);
        for (SoldierDefinition definition : soldierDefinitions) {
            connect(definition);
        }
        ScheduledExecutorService squad = clockwork.scheduler("squad");
        schedulers.add(squad);
        squad.scheduleAtFixedRate(loudly("the squad loop", this::leadSquad), SQUAD_PERIOD_MILLIS, SQUAD_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        // The net and the radio drain on their own, keep or no keep: a radio line waits for nothing else.
        squad.scheduleAtFixedRate(loudly("the net", () -> drainNet(clockwork.millis())), NET_DRAIN_MILLIS, NET_DRAIN_MILLIS, TimeUnit.MILLISECONDS);
        squad.scheduleAtFixedRate(loudly("the SITREP log", this::logSquadSitrep), STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reports up, orders down: pass every squad's SITREPs to the chain of command, then hand every
     * present soldier its slot under its squad's standing order (the deterministic half of squad
     * leading - {@link Formations} and the drills).
     */
    private void leadSquad() {
        record Present(String squad, List<DeterministicSoldier> soldiers, List<SoldierStatus> statuses, Optional<Position> principalAt) {
        }
        if (yieldingToAnExperiment()) {
            return;
        }
        // The force's staircases' steps kept open: no route builds in them (WorldView.noBuild). Live,
        // soldiers crossing a mine shaft's open top put their steps into its stairs, and those left
        // there blocked the way up - miners on them stranded below.
        faults.run("the stairs kept open", () -> {
            Set<BlockPos> stairsOpen = new HashSet<>();
            java.util.function.Consumer<Staircase> keep = st -> st.cuts().stream().filter(c -> c.tunnel() < 0)
                    .forEach(c -> stairsOpen.addAll(c.cells()));
            squadStates.values().forEach(st -> Optional.ofNullable(st.stairs).ifPresent(keep));
            squadStates.values().forEach(st -> Optional.ofNullable(st.second).ifPresent(keep));
            campStairs().ifPresent(keep);
            world.keepOpen(stairsOpen);
        });
        if (camp != null && footholdTicks++ % FOOTHOLD_EVERY == 0) {
            // Its own request: a measure that fails costs the measure, not the squads' orders.
            faults.run("the foothold's measure", () -> measureFoothold().ifPresent(f -> {
                chainOfCommand.onFoothold(f);
                afterActionReview.measured(f);
                List<Site> sites = measureSites(f);
                afterActionReview.sites(sites);
                List<Supply> supplies = measureSupply();
                phaseBook.measure(chainOfCommand, f, supplies, activeSoldiers.size(), dangerAreas.near(f.camp().x(), f.camp().z(), clockwork.millis()), clockwork.millis());
                afterActionReview.supplies(supplies);
                afterActionReview.estimates(Optional.ofNullable(keepEstimate).stream().toList());
                supplies.forEach(supply -> {
                    if (supplyStates.put(supply.name(), supply.state()) != supply.state()) {
                        LOG.info("[Supply] {}", supply.line());
                    }
                });
                sites.forEach(site -> {
                    String line = site.line();
                    if (!line.equals(siteLines.put(site.name(), line))) {
                        LOG.info("[Sites] {}", line);
                    }
                });
            }));
        }
        // Dusk: the ore taken so far, for the night's yield at dawn (nightsMining).
        chainOfCommand.time().ifPresent(t -> {
            if (t.dark() && !wasDark) {
                oreAtDusk = miners.values().stream().mapToInt(Miner::oreTaken).sum();
            }
            wasDark = t.dark();
        });
        chainOfCommand.time().flatMap(t -> {
                    List<SoldierStatus> all = activeSoldiers.values().stream().map(DeterministicSoldier::status).toList();
                    int hungry = (int) all.stream().filter(s -> s.condition().map(SoldierErrands::wantsFood).orElse(false)).count();
                    return afterActionReview.time(t.dark(), chainOfCommand.foothold(), hungry, all.size(), clockwork.millis(),
                            routes.stream().map(RouteEffort::line).toList(),
                            squadStates.values().stream().mapToInt(st -> st.register.takeChurned()).sum());
                }).ifPresent(dawn -> {
                    nightsMining();
                    LOG.info("[AAR] {}\n  {}\n  {}\n  {}\n  {}", dawn.review(), String.join("\n  ", chainOfCommand.requestReview()),
                            String.join("\n  ", registerReview()), String.join("\n  ", faults.review()), String.join("\n  ", foodLedger.review()));
                    LOG.info("[Scoreboard] {}", scoreboard.add(dawn.score()));
                });
        List<Present> present = new ArrayList<>();
        for (Roster.Squad squad : roster.squads()) {
            List<DeterministicSoldier> soldiers = presentSoldiers(squad.name());
            if (soldiers.isEmpty()) {
                continue;
            }
            SquadState state = squadStates.computeIfAbsent(squad.name(), name -> new SquadState());
            // Replacements (respawned soldiers) go to the rear and rejoin by link-up, so the first formed soldier is the point man.
            // The soldiers' own reports: a replacement's CANTCO, before link-up relabels it, says it's stranded.
            state.reported = soldiers.stream().map(DeterministicSoldier::status).toList();
            List<SoldierStatus> statuses = state.linkUp.reconstitute(state.reported);
            Set<String> linking = statuses.stream().filter(s -> s.report() instanceof Report.LinkingUp)
                    .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
            Set<String> wasLinking = state.linkingUp;
            linking.stream().filter(name -> !wasLinking.contains(name))
                    .forEach(name -> LOG.info("[{} -> {}] LINKING UP after respawn", name, squad.name()));
            wasLinking.stream().filter(name -> !linking.contains(name))
                    .forEach(name -> LOG.info("[{} -> {}] LINKED UP", name, squad.name()));
            state.linkingUp = linking;
            Optional<Position> principalAt = chainOfCommand.escort(squad.name())
                    .flatMap(principal -> entityTracker.positionOf(OperatorOrders.offlineUuid(principal)));
            present.add(new Present(squad.name(), soldiers, statuses, principalAt));
        }
        chainOfCommand.onSitreps(present.stream().map(p -> {
            SquadState state = squadStates.get(p.squad());
            // A soldier the recovery drill has is shown to the squad leader as Recovering, at the rear:
            // shown its CANTCO, the squad leader reported the mission one, and the platoon leader spent the
            // squad's days moving it to "resolve movement issues".
            List<SoldierStatus> shown = state.recovery.asRecovering(p.statuses());
            // Surveyed from where the squad is: the soldier at its middle height, so one stranded up a
            // tower - respawned on a block over spawn - doesn't show the squad no ground at all; and
            // never one being recovered: live, the survey from one in a canopy, or in a hole under the
            // mine's landing, showed the squad 0 of 81 cells reachable, and it reported its task CANTCO.
            // With an objective, from the one of them nearest it: live (field336), a squad released to the
            // camp with three of its four still down the mine was surveyed from y=6, shown the camp's
            // nearest reachable ground in the caves under it, and the one already up top had no route there.
            List<Position> formed = shown.stream().filter(s -> !(s.report() instanceof Report.Recovering))
                    .map(SoldierStatus::position).sorted(Comparator.comparingDouble(Position::y)).toList();
            Optional<Position> toward = chainOfCommand.objective(p.squad()).map(Objective::location);
            // Only one near the objective's height, though: one up a tower over it shows no ground at all.
            Position pointMan = formed.isEmpty() ? shown.getFirst().position()
                    : toward.flatMap(o -> formed.stream().filter(f -> Math.abs(f.y() - o.y()) <= SURVEY_FROM_HEIGHT)
                    .min(Comparator.comparingDouble(f -> Math.hypot(f.x() - o.x(), f.z() - o.z()))))
                    .orElse(formed.get(formed.size() / 2));
            // Climbing counts when the point man carries cobblestone to pillar with.
            int climbBlocks = shown.get(0).condition().flatMap(c -> c.kit()).map(k -> k.stone()).orElse(0);
            boolean stoneSteps = shown.get(0).condition().flatMap(c -> c.kit()).map(k -> k.pickaxe()).orElse(false);
            boolean holdingBase = state.allInside && state.breaches != null && state.breaches.isEmpty();
            return new ChainOfCommand.Sitrep(p.squad(), shown, () -> TerrainSurveyor.survey(world, pointMan, climbBlocks, stoneSteps,
                    chainOfCommand.objective(p.squad()).map(Objective::location)), p.principalAt(),
                    () -> ResourceSurveyor.survey(world, pointMan, chainOfCommand::nearSpentWood), holdingBase,
                    holdingBase ? OptionalInt.of(0) : state.baseBlocksLeft, progress(p.squad(), state, p.statuses()),
                    Optional.of(classOne(p.squad(), state, p.statuses())), classFour(p.squad(), state, p.statuses()));
        }).toList());
        faults.run("cross-levelling across squads", () -> crossRations(present.stream().filter(p -> p.statuses().stream()
                .noneMatch(s -> s.report() instanceof Report.InContact)).flatMap(p -> p.statuses().stream()).toList()));
        // Each squad on its own: one squad's fault doesn't leave the other without orders. And each
        // of its units - its main body, and each detachment its soldiers' plans make - led as a squad.
        for (Present p : present) {
            List<Unit> units = new ArrayList<>();
            faults.run(p.squad() + "'s task organisation", () -> units.addAll(organise(p.squad(), p.soldiers(), p.statuses(),
                    chainOfCommand.currentOrder(p.squad()), p.principalAt().isPresent())));
            for (Unit u : units) {
                SquadState state = squadStates.computeIfAbsent(u.name(), name -> new SquadState());
                if (Plan.detachment(u.name())) {
                    Set<String> members = u.statuses().stream().map(SoldierStatus::name).collect(Collectors.toSet());
                    state.reported = squadStates.get(p.squad()).reported.stream().filter(s -> members.contains(s.name())).toList();
                }
                faults.run(u.name() + "'s orders", () -> chainOfCommand.currentOrder(p.squad()).ifPresent(order ->
                        orderSquad(u.name(), state, u.soldiers(), u.statuses(), u.order(order), Plan.detachment(u.name()) ? Optional.empty() : p.principalAt())));
                // Not one holding where it was sent, or on a job of its own: upkeep is for one with nothing to do.
                if (u.directed().isEmpty()) {
                    faults.run(u.name() + "'s camp upkeep", () -> campUpkeep(u.name(), u.statuses()));
                }
                faults.run(u.name() + "'s register", () -> register(u.name(), state, u.statuses()));
            }
        }
    }

    /** Rations given across squads and detachments this command cycle, by giver: {@link #crossRations}. */
    private volatile Map<String, CrossLevel.Transfer> acrossSquads = Map.of();

    /**
     * Rations across squads, as a squad of its own would share them (the operator's: across squads if
     * need be): for a soldier running low that no squadmate's sharing with, from the nearest soldier
     * of the force out of contact - {@code outOfContact} - with rations to spare and no hand-over of
     * its own squad's. Who's busy is as the units paired last cycle.
     */
    private void crossRations(List<SoldierStatus> outOfContact) {
        Set<String> busy = new HashSet<>();
        squadStates.values().forEach(st -> st.crossLeveling.forEach(t -> {
            busy.add(t.giver());
            busy.add(t.receiver());
        }));
        Map<String, CrossLevel.Transfer> was = acrossSquads;
        Map<String, CrossLevel.Transfer> now = new HashMap<>();
        // Nor a giver at a receiver it couldn't get to (Handover.barred): another giver is looked for instead.
        // Nobody the recovery drill has, giving or given: where it's stuck nobody gets to, as for its own squad's
        // hand-overs. Live (field428), Pete - the whole crew of Delta's second level - walked at Olga, stranded in
        // the water at y=-3, fourteen times in the night, half a minute each, and the level stood.
        List<SoldierStatus> able = outOfContact.stream().filter(s -> !recovering.contains(s.name())).toList();
        PlatoonSergeant.crossRations(able, busy, was.values().stream().filter(t -> !barredHandover(t)).toList())
                .stream().filter(t -> !barredHandover(t)).forEach(t -> now.put(t.giver(), t));
        now.values().stream().filter(t -> !was.containsValue(t))
                .forEach(t -> LOG.info("[{} -> {}] CROSS-LEVEL across squads: {} rations", t.giver(), t.receiver(), t.count()));
        acrossSquads = Map.copyOf(now);
    }

    /**
     * A squad's unit: its main body, or a detachment its soldiers' plans make - led as a squad is, to
     * {@code objective}. {@code directed} is the directive a detachment of one is on.
     */
    private record Unit(String name, List<DeterministicSoldier> soldiers, List<SoldierStatus> statuses,
                        Optional<Objective> objective, Optional<Directive> directed, boolean roled) {

        /** How far above or below a point a soldier with a role sent there may end up: the map's height. */
        static final double ANY_HEIGHT = 384;

        /** The squad leader's order as this unit takes it: a detachment goes to its own objective, in file, close. */
        SquadOrder order(SquadOrder squad) {
            if (objective.isEmpty()) {
                return squad;
            }
            Position at = objective.get().location();
            // One with a role sent to a point first goes to the ground over or under it: a miner sent to its mine at its depth.
            boolean anyHeight = directed.filter(d -> d instanceof Directive.GoTo).isPresent() && roled;
            return new SquadOrder(at, Formation.FILE, Interval.TIGHT,
                    anyHeight ? ANY_HEIGHT : squad.verticalTolerance(), squad.riskFor(statuses.getFirst().name()), squad.soldierRisk(), squad.rearm(), squad.intent());
        }
    }

    /** Each detachment's objective, by its unit's name - squad loop writes, connections' threads read. */
    private final Map<String, Objective> detached = new ConcurrentHashMap<>();
    /** Each soldier's unit, by its name: its squad's for one in the main body - squad loop writes, connections' threads read. */
    private final Map<String, String> unitOf = new ConcurrentHashMap<>();
    /** The walks from the camp's centre to the sites its squads use, as last measured - squad loop only. */
    private List<RouteEffort> routes = List.of();

    /**
     * How hard the camp is to get about: the walk on foot from its centre to the workshop, the farm
     * and the mine head, against a straight walk on level ground - steps, jumps up and drops down.
     * Logged as it changes: the operator found the camp awkward to cross while every standard said
     * its ground was level, the ground work having levelled only the disc inside its wall.
     */
    private void measureRoutes(BlockPos centre) {
        List<RouteEffort> measured = new ArrayList<>();
        Optional.ofNullable(campWorkshop).ifPresent(w -> measured.add(RouteEffort.of(
                "workshop", world, centre, w.stand())));
        Optional.ofNullable(campFarm).filter(f -> !f.plot().isEmpty()).ifPresent(f -> measured.add(
                RouteEffort.of("farm", world, centre, f.plot().getFirst().offset(0, 1, 0))));
        campStairs().ifPresent(s -> measured.add(RouteEffort.of("mine head", world, centre, s.head())));
        if (!measured.equals(routes)) {
            LOG.info("[Routes] from the camp's centre: {}", measured.isEmpty() ? "no sites yet"
                    : String.join("; ", measured.stream().map(RouteEffort::line).toList()));
        }
        routes = List.copyOf(measured);
    }

    /** Every soldier's standing plan, kept on disk. */
    private final PlanBook planBook;
    /** The garrison's score, a line each dawn, kept on disk. */
    private final Scoreboard scoreboard;

    /**
     * Task organisation: the squad leader's plan changes made, once per order; each directive the
     * soldier or the world shows done let go; then each soldier in its unit by its plan - main body,
     * a role's detachment, or one on a directive. Escorting, the whole squad is the escort.
     */
    private List<Unit> organise(String squad, List<DeterministicSoldier> soldiers, List<SoldierStatus> statuses,
                                Optional<SquadOrder> order, boolean escorting) {
        SquadState main = squadStates.get(squad);
        List<String> names = statuses.stream().map(SoldierStatus::name).toList();
        if (order.isPresent() && order.get() != main.planned) {
            main.planned = order.get();
            for (Plan.Change given : order.get().changes()) {
                // An order lately timed out for this soldier, given again: refused while its squad leader is shown why.
                Optional<Directive> refused = given.directives()
                        .flatMap(ds -> ds.stream().filter(d -> main.workOrders.barred(given.soldier(), d, clockwork.millis())).findFirst());
                if (refused.isPresent()) {
                    LOG.warn("[{} -> {}] REFUSED: {} - it timed out lately", given.soldier(), squad, refused.get().describe());
                    if (given.role().isEmpty()) {
                        continue;
                    }
                }
                Plan.Change change = refused.isEmpty() ? given
                        : new Plan.Change(given.soldier(), given.role(), given.site(), Optional.empty());
                Plan after = planBook.of(change.soldier()).with(change);
                if (planBook.put(change.soldier(), after)) {
                    LOG.info("[{} -> {}] TASKED: {}", squad, change.soldier(), after.describe());
                }
                // One site a role in a squad: a detachment works one place.
                if (change.role().isPresent() && change.site().isPresent()) {
                    for (String other : names) {
                        Plan theirs = planBook.of(other);
                        if (!other.equals(change.soldier()) && theirs.role() == after.role() && !theirs.site().equals(after.site())) {
                            planBook.put(other, new Plan(theirs.role(), after.site(), theirs.directives()));
                            LOG.info("[{} -> {}] TASKED: {} - the {}s' site moved", squad, other, planBook.of(other).describe(),
                                    after.role().name().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        for (SoldierStatus s : statuses) {
            Plan plan = planBook.of(s.name());
            while (plan.current().isPresent() && (plan.currentDoneBy(s)
                    || plan.current().get() instanceof Directive.Level level && levelWork(s, level).isEmpty())) {
                LOG.info("[{} -> {}] DONE: {}", s.name(), squad, plan.current().get().describe());
                plan = plan.done();
                planBook.put(s.name(), plan);
            }
        }
        // Work orders followed: one that's stalled, or run past its allowance, is taken off the soldier's plan.
        long now = clockwork.millis();
        Map<String, Directive> current = new HashMap<>();
        Map<String, Integer> left = new HashMap<>();
        Set<String> fighting = new HashSet<>();
        for (SoldierStatus s : statuses) {
            planBook.of(s.name()).current().ifPresent(d -> {
                current.put(s.name(), d);
                left.put(s.name(), d instanceof Directive.Level level ? levelLeft(level) : d.leftFor(s));
            });
            if (s.report() instanceof Report.InContact) {
                fighting.add(s.name());
            }
        }
        for (WorkOrders.TimedOut t : main.workOrders.update(now, current, left, fighting)) {
            LOG.warn("[{} -> {}] TIMED OUT: {} - {}", t.soldier(), squad, t.directive().describe(), t.why());
            planBook.put(t.soldier(), planBook.of(t.soldier()).done());
        }
        Map<String, String> standing = new HashMap<>();
        names.forEach(n -> main.workOrders.standing(n, now).ifPresent(w -> standing.put(n, w)));
        chainOfCommand.receivePlans(squad, planBook.of(names), standing);
        String intent = order.map(SquadOrder::intent).orElse("");
        Map<String, List<Integer>> members = new LinkedHashMap<>();
        members.put(squad, new ArrayList<>());
        for (int i = 0; i < statuses.size(); i++) {
            // By night only miners work on, underground: every other role comes in with its squad,
            // which shelters. Live, two farmers working farms 40 blocks out were shot by skeletons.
            Plan plan = planBook.of(names.get(i));
            boolean inForTheNight = chainOfCommand.sheltering() && plan.role() != Role.MINER && plan.current().isEmpty();
            // And by day on the keep, its miners are builders: the whole squad mines by night, so its
            // mine has its shift. Live, a miner kept the camp alone all day while its squad dug the keep.
            boolean minerOnTheKeep = !chainOfCommand.sheltering() && plan.role() == Role.MINER && plan.current().isEmpty()
                    && taskOf(squad, Objective.Task.Keep.class).isPresent();
            String wanted = escorting || inForTheNight || minerOnTheKeep ? squad : plan.unit(squad, names.get(i));
            // One the recovery drill has stays in its unit till it's out: each unit's drill is its own, and a move
            // begins it afresh. Live (field393-408), Eli, stranded at y=-43, was dealt between Bravo and Bravo/miner at
            // every dusk and dawn, his stair up laid again each time and never cut (operator, 2026-11-17: one
            // fetcher, not the squad - kept in one unit first).
            String held = unitOf.get(names.get(i));
            String unit = recovering.contains(names.get(i)) && held != null && !held.equals(wanted) ? held : wanted;
            if (!unit.equals(wanted) && heldInUnit.add(names.get(i))) {
                LOG.info("[{} -> {}] stays in {} while the recovery drill has it, not {}", names.get(i), squad, unit, wanted);
            } else if (unit.equals(wanted)) {
                heldInUnit.remove(names.get(i));
            }
            members.computeIfAbsent(unit, u -> new ArrayList<>()).add(i);
            String was = unitOf.put(names.get(i), unit);
            if (!unit.equals(was == null ? squad : was)) {
                LOG.info("[{} -> {}] now in {}", names.get(i), squad, unit);
                // Off camp upkeep's ground work: its new unit gives it its work.
                stopUpkeep(names.get(i));
            }
        }
        // By name, not by place: link-up puts a respawned soldier at the back of the statuses, not of
        // the soldiers. Live, a unit handed the soldier at its member's place in the other order left
        // the squad's orders failing every tick until the replacement linked up.
        Map<String, DeterministicSoldier> soldierNamed = new HashMap<>();
        soldiers.forEach(s -> soldierNamed.put(s.status().name(), s));
        List<Unit> units = new ArrayList<>();
        members.forEach((unit, at) -> {
            if (at.isEmpty()) {
                return;
            }
            Plan first = planBook.of(names.get(at.getFirst()));
            Optional<Objective> objective = Plan.detachment(unit) ? first.objective(intent) : Optional.empty();
            objective.ifPresentOrElse(o -> detached.put(unit, o), () -> detached.remove(unit));
            units.add(new Unit(unit, at.stream().map(i -> soldierNamed.get(names.get(i))).toList(), at.stream().map(statuses::get).toList(), objective,
                    Plan.detachment(unit) ? first.current() : Optional.empty(), first.role() != Role.RIFLEMAN));
        });
        // A detachment nobody's in any more is stood down, and what it held let go.
        List<String> gone = squadStates.keySet().stream()
                .filter(u -> Plan.detachment(u) && Plan.squadOf(u).equals(squad) && !members.containsKey(u)).toList();
        for (String u : gone) {
            LOG.info("[{}] {} stood down", squad, u);
            squadStates.remove(u);
            detached.remove(u);
            baseFootprints.remove(u);
            baseReports.remove(u);
            nightBelow.remove(u);
        }
        return units;
    }

    /** Where a level's ground is dug from: this far above the height it's made level at. */
    static final int LEVEL_ABOVE = 12;
    /** And filled from: this far below. */
    static final int LEVEL_BELOW = 4;

    /**
     * What's left of {@code level} for {@code soldier}: everything over its height dug first, then
     * every hole under it filled - dirt fetched first by one carrying no blocks. Empty once the
     * world shows it level. Never the cells of a hut, a workshop, the wall or the mine.
     */
    private Optional<Order> levelWork(SoldierStatus soldier, Directive.Level level) {
        Set<Cell> except = groundExcept();
        Box dig = new Box(level.minX(), level.y(), level.minZ(), level.maxX(), level.y() + LEVEL_ABOVE, level.maxZ());
        if (!Clearing.toClear(world, dig, except).isEmpty()) {
            return Optional.of(new Order.Dig(dig, except));
        }
        Box fill = new Box(level.minX(), level.y() - LEVEL_BELOW, level.minZ(), level.maxX(), level.y() - 1, level.maxZ());
        if (Clearing.toFill(world, fill, except).isEmpty()) {
            return Optional.empty();
        }
        int blocks = soldier.condition().flatMap(c -> c.kit()).map(k -> k.stone() + k.dirt()).orElse(0);
        if (blocks == 0) {
            int r = Directive.FETCH_RADIUS;
            Position at = level.centre();
            return Optional.of(new Order.Gather(Resource.SOIL, new Box((int) at.x() - r, level.y() - 8, (int) at.z() - r,
                            (int) at.x() + r, level.y() + 4, (int) at.z() + r), WALL_BLOCKS_LOAD));
        }
        return Optional.of(new Order.Fill(fill, except));
    }

    /** How many cells of {@code level} are still to dig or fill, as the world shows. */
    private int levelLeft(Directive.Level level) {
        Set<Cell> except = groundExcept();
        return Clearing.toClear(world, new Box(level.minX(), level.y(), level.minZ(), level.maxX(), level.y() + LEVEL_ABOVE, level.maxZ()), except).size()
                + Clearing.toFill(world, new Box(level.minX(), level.y() - LEVEL_BELOW, level.minZ(), level.maxX(), level.y() - 1, level.maxZ()), except).size();
    }

    /** The objective {@code unit} works to: a detachment's own, or its squad's from the platoon leader. */
    private Optional<Objective> objectiveOf(String unit) {
        return Plan.detachment(unit) ? Optional.ofNullable(detached.get(unit)) : chainOfCommand.objective(unit);
    }

    /** {@code unit}'s objective, if its task is a {@code kind}. */
    private Optional<Objective> taskOf(String unit, Class<? extends Objective.Task> kind) {
        return objectiveOf(unit).filter(o -> kind.isInstance(o.task()));
    }

    /** The unit {@code soldier} of {@code squad} was last put in. */
    private String unitOf(String soldier, String squad) {
        return unitOf.getOrDefault(soldier, squad);
    }

    /**
     * The squad's Class I, as the platoon sergeant rates it ({@link PlatoonSergeant#classOne}), logged
     * when its rating changes.
     */
    private Subsistence.Status classOne(String squad, SquadState state, List<SoldierStatus> statuses) {
        Set<String> shared = state.crossLeveling.stream().filter(t -> t.item() == CrossLevel.Item.RATIONS)
                .map(CrossLevel.Transfer::receiver).collect(Collectors.toSet());
        FarmPlan farm = campFarm;
        // By night only: by day one with no food says so, for the platoon leader's FORAGE (field500: all 16 short, every squad AMBER).
        boolean farmGrowing = chainOfCommand.time().map(TimeOfDay::dark).orElse(false) && farm != null && farm.plot().stream().anyMatch(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).isPresent());
        Subsistence.Status status = PlatoonSergeant.classOne(statuses, Set.copyOf(foodless), shared, farmGrowing);
        if (state.classOne == null || state.classOne.rating() != status.rating()) {
            LOG.info("[{}] {}", squad, status.line());
        }
        state.classOne = status;
        return status;
    }

    /**
     * The squad's Class IV, construction materials, as a request to the platoon leader when its task wants
     * logs it hasn't got: on the keep, {@link PlatoonSergeant#classFour} from the logs its soldiers carry, the
     * stockpile's, and whether the keep's chest stands. Said in the log when it starts and when it's met.
     */
    private Optional<String> classFour(String squad, SquadState state, List<SoldierStatus> statuses) {
        boolean onKeep = taskOf(squad, Objective.Task.Keep.class).isPresent();
        Optional<String> request = !onKeep ? Optional.empty()
                : PlatoonSergeant.classFour(Logstat.of(statuses).logs(), chainOfCommand.stockpile().map(Stockpile::logs).orElse(0),
                        keepWorkshop != null && keepWorkshop.chestStands(world));
        if (!request.equals(state.classFour)) {
            request.ifPresentOrElse(line -> LOG.warn("[{}] {}", squad, line), () -> {
                if (onKeep) {
                    LOG.info("[{}] Class IV: the keep's builders have their logs", squad);
                }
            });
            state.classFour = request;
        }
        return request;
    }

    /**
     * The squad's surface workshop, sited by the engineer ({@code bot.engineer.Workshop}): the
     * camp's, for an objective within {@link #CAMP_WORKSHOP_RANGE} of it, else a hasty one at the
     * objective's ground - where the squad stands with no objective. Sited again only when that ground
     * moves, or the stand stops being standable. Never in its patrol base: {@code hut}'s cells are left alone.
     */
    private void siteWorkshop(String squad, SquadState state, List<SoldierStatus> statuses, List<BlockPos> hut) {
        // On the keep, the keep's workshop: one for every squad on it, off the slab at its depot.
        if (taskOf(squad, Objective.Task.Keep.class).isPresent()) {
            Workshop keep = keepWorkshop;
            // Sited again too while it has no cell for a chest and none stands: a depot is a stockpile.
            Position depotAt = chainOfCommand.flatten().depot();
            // And when the ground to flatten has moved away from it: a new site's depot is its own.
            boolean moved = keep != null && Math.hypot(keep.stand().x() + 0.5 - depotAt.x(), keep.stand().z() + 0.5 - depotAt.z()) > DEPOT_MOVE;
            if (keep == null || moved || !Pathfinder.standable(world, keep.stand()) || keep.chest().isEmpty() && !keep.stands(world, Workstation.CRAFTING_TABLE)) {
                Workshop was = keep;
                // On the depot's surface, from high over any hill there down - not the first cell with
                // ground under it near the slab's height, which under a hill is a cave's floor or a
                // tunnel's: live, that was a one-wide tunnel into the hill west of the keep, and both
                // squads said "nowhere" every second for hours while their builders walked 50 blocks to a stray table for bread.
                int depotX = (int) Math.floor(depotAt.x()), depotZ = (int) Math.floor(depotAt.z());
                BlockPos depot = TerrainSurveyor.surface(world, depotX, depotZ, (int) depotAt.y() + 60)
                        .orElseGet(() -> TerrainSurveyor.groundAt(world, BlockPos.containing(depotAt.x() + 0.5, depotAt.y(), depotAt.z() + 0.5)));
                keep = Workshop.hasty(world, depot, new HashSet<>(treeClaims)).orElse(null);
                if (keep == null) {
                    if (!depot.equals(keepWorkshopNowhere)) {
                        LOG.warn("[{}] nowhere at the keep's depot {} to site its workshop", squad, depot);
                        keepWorkshopNowhere = depot;
                    }
                } else {
                    if (!keep.equals(was)) {
                        LOG.info("[{}] the keep's workshop: stand {}, table {}, furnace {}, chest {}", squad, keep.stand(), keep.table(), keep.furnace(),
                                keep.chest().map(Object::toString).orElse("no room for one"));
                    }
                    keepWorkshopNowhere = null;
                }
                keepWorkshop = keep;
                // A depot is chests enough for what the dig turns out, not one (operator, 2026-11-14): their cells laid out round its stand, filled one after another.
                keepChests = keep == null ? List.of() : keep.chestCells(world, chestClaims(), DEPOT_CHESTS);
                keepChestAt = 0;
                if (keep != null) {
                    LOG.info("[{}] the keep's depot: {} chest(s) laid out at {}", squad, keepChests.size(), keepChests);
                }
            }
            // Sought again while there's none, as its ground is cleared: once, it stayed none all day.
            if (keep != null && (!keepGroveSought || keepGrove == null && clockwork.millis() - keepGroveSoughtAt >= GROVE_RESITE_MILLIS)) {
                keepGroveSought = true;
                keepGroveSoughtAt = clockwork.millis();
                keepGrove = siteKeepGrove(keep).orElse(null);
                if (keepGrove == null) {
                    LOG.warn("[{}] nowhere near the keep's depot to plant its grove yet", squad);
                } else {
                    LOG.info("[{}] the keep's grove round {}: {} cells, a sapling every {} blocks", squad, keepGrove.centre(),
                            keepGrove.plot().size(), TreeFarm.SPACING);
                }
            }
            // The chest being filled now: the next cell once the last is full.
            List<BlockPos> depotCells = keepChests;
            if (keep != null && keepChestAt < depotCells.size() && !keep.chest().equals(Optional.of(depotCells.get(keepChestAt)))) {
                keep = keep.withChest(depotCells.get(keepChestAt));
                keepWorkshop = keep;
            }
            // The camp's too, if no squad there has sited it since a restart: its chest is the common
            // stockpile a keep builder draws from until the keep's own chest stands. Live, both squads
            // on the keep left it unsited, and with it every draw.
            if (campWorkshop == null && campGround != null) {
                Workshop camp = Workshop.hasty(world, stockpileWatch.ground().orElse(campGround), new HashSet<>(treeClaims)).orElse(null);
                if (camp != null) {
                    LOG.info("[{}] the camp's workshop, for its stockpile: stand {}, chest {}", squad, camp.stand(), camp.chest());
                }
                campWorkshop = camp;
            }
            // Its outposts, the depots along its edge but the main one: each kept while it stands where its depot is, its chest the next of its row once one's full.
            List<Workshop> outposts = new ArrayList<>();
            for (Position p : chainOfCommand.flatten().depots()) {
                if (Math.hypot(p.x() - depotAt.x(), p.z() - depotAt.z()) < 1) {
                    continue;
                }
                Workshop outpost = keepOutposts.stream()
                        .filter(w -> Math.hypot(w.stand().x() + 0.5 - p.x(), w.stand().z() + 0.5 - p.z()) <= DEPOT_MOVE
                                && Pathfinder.standable(world, w.stand())).findFirst().orElseGet(() -> {
                            BlockPos at = TerrainSurveyor.surface(world, (int) Math.floor(p.x()), (int) Math.floor(p.z()), (int) p.y() + 60)
                                    .orElseGet(() -> TerrainSurveyor.groundAt(world, BlockPos.containing(p.x() + 0.5, p.y(), p.z() + 0.5)));
                            Workshop sited = Workshop.hasty(world, at, new HashSet<>(treeClaims)).orElse(null);
                            if (sited != null) {
                                LOG.info("[{}] an outpost depot of the flatten's: stand {}, chest {}", squad, sited.stand(),
                                        sited.chest().map(Object::toString).orElse("no room for one"));
                            }
                            return sited;
                        });
                if (outpost != null) {
                    outposts.add(nextChestIfFull(outpost));
                }
            }
            keepOutposts = List.copyOf(outposts);
            state.workshop = keep;
            state.workshopFor = null;
            return;
        }
        // A MINE objective's height is the depth to reach: its workshop is at the stairs' head, and
        // before they're sited on the ground at its x and z - never at the depth. Live (field346), a
        // mine not yet sited had its workshop in a cave at y=16, and depositors stood over it.
        Optional<Objective> objective = objectiveOf(squad);
        Position at;
        if (state.stairs != null) {
            at = new Position(state.stairs.head().x(), state.stairs.head().y(), state.stairs.head().z());
        } else if (objective.filter(Objective::mine).isPresent()) {
            Position o = objective.get().location();
            OptionalInt g = groundNear((int) Math.floor(o.x()), (int) Math.floor(o.z()));
            if (g.isEmpty()) {
                state.workshop = null;
                state.workshopFor = null;
                return;
            }
            at = new Position(o.x(), g.getAsInt(), o.z());
        } else {
            at = objective.map(Objective::location).orElse(statuses.getFirst().position());
        }
        Set<BlockPos> claimed = new HashSet<>(treeClaims);
        claimed.addAll(hut);
        // Clear of the shaft: its 3x3 is dug out from under anything put down beside its head.
        if (state.stairs != null) {
            BlockPos head = state.stairs.head();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        claimed.add(head.offset(dx, dy, dz));
                    }
                }
            }
        }
        if (state.wall != null) {
            claimed.addAll(state.wall);
        }
        if (campGround != null && Math.hypot(at.x() - campGround.x(), at.z() - campGround.z()) <= CAMP_WORKSHOP_RANGE) {
            Workshop camp = campWorkshop;
            if (camp == null || !Workshop.usable(world, camp.stand())) {
                camp = Workshop.hasty(world, stockpileWatch.ground().orElse(campGround), claimed).orElse(null);
                if (camp == null) {
                    LOG.warn("[{}] nowhere at the camp's ground {} to site its workshop", squad, campGround);
                } else {
                    LOG.info("[{}] the camp's workshop: stand {}, table {}, furnace {}", squad, camp.stand(), camp.table(), camp.furnace());
                }
                campWorkshop = camp;
            }
            state.workshop = camp;
            state.workshopFor = campGround;
            return;
        }
        BlockPos ground = TerrainSurveyor.groundAt(world, BlockPos.containing(at.x(), at.y(), at.z()));
        if (ground.equals(state.workshopFor) && state.workshop != null
                && Pathfinder.standable(world, state.workshop.stand())) {
            return;
        }
        state.workshopFor = ground;
        state.workshop = Workshop.hasty(world, ground, claimed).orElse(null);
        if (state.workshop == null) {
            LOG.warn("[{}] nowhere near {} to site a workshop", squad, ground);
        } else {
            LOG.info("[{}] a hasty workshop at {}: stand {}, table {}, furnace {}", squad, ground, state.workshop.stand(),
                    state.workshop.table(), state.workshop.furnace());
        }
    }

    /**
     * Where a soldier at {@code at} crafts: the mine base once its landing is cut, for one down the
     * stairs; else its squad's. By night a mining squad crafts only at the mine base: the top is
     * the dark, and nobody goes up the stairs for a tool.
     */
    private Workshop workshopOf(SquadState state, Position at) {
        if (state.stairs == null) {
            return state.workshop;
        }
        // The mine's base only for a soldier at the mine - under the ground, or by its head: live
        // (field390-393), Eli on the surface 70 blocks off was sent by night to his squad's dried-up mine's
        // base at y=-48 for a chestplate, walked down through the caves, and was stranded at y=-43.
        BlockPos head = state.stairs.head();
        if (!underground(at) && Math.hypot(at.x() - head.x(), at.z() - head.z()) > MINE_SHOP_NEAR) {
            return state.workshop;
        }
        boolean landing = state.stairs.landingCut(world);
        if (landing && at.y() < head.y() - MINE_BELOW) {
            return Workshop.mine(state.stairs);
        }
        boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
        return dark ? (landing ? Workshop.mine(state.stairs) : null) : state.workshop;
    }

    private final SurfaceCache surfaces = new SurfaceCache();
    /** Every block a soldier puts above sea level, said by what put it (operator, 2026-11-21: audit it). */
    private final PlacementAudit placementAudit = new PlacementAudit(() -> clockwork.millis());
    /** Whether {@code at} is under the ground: the column's surface, a second or two old, more than {@link #UNDERGROUND} over it. */
    private boolean underground(Position at) {
        return surfaces.underground(world, at, UNDERGROUND, clockwork.millis());
    }

    /** How far across from a mine's head a soldier on the surface still crafts at its base: by the head. */
    static final double MINE_SHOP_NEAR = 32;

    /**
     * Where {@code soldier}'s loads go and are drawn from: the supply point the operator set up, else
     * its workshop's chest once it stands (backlog/sites.md).
     */
    private Optional<Position> stockpileFor(String soldier) {
        // Never a chest being salvaged: loads put into it would be drawn out again, and it never empties.
        return stockpileOrSalvaged(soldier).filter(p -> !condemned.contains(BlockPos.containing(p.x(), p.y(), p.z())));
    }

    /** {@code soldier}'s depositor's CANTCO, its chest the camp's noted (StockpileWatch): enough of them, the stockpile's sited anew. */
    private String stockpileUnreached(String soldier, Position at, Depositor depositor) {
        Optional<Position> camps = chestOf(campWorkshop);
        depositor.chestAt().filter(c -> camps.filter(c::equals).isPresent()).map(c -> BlockPos.containing(c.x(), c.y(), c.z()))
                .flatMap(chest -> stockpileWatch.unreached(soldier, at, chest, clockwork.millis()).map(ground -> {
                    LOG.warn("[Stockpile] the camp's chest at {} can't be got at - a new stockpile at {}, the old one emptied into it", chest, ground);
                    campWorkshop = null;
                    return ground;
                }));
        return depositor.stuck().orElseThrow();
    }

    private Optional<Position> stockpileOrSalvaged(String soldier) {
        if (supplyPoint.isPresent()) {
            return supplyPoint;
        }
        // On the flatten's crew, its depot - whatever its squad's own task: live, the builders of the
        // squads sent to it from the mine carried every load to the camp's chest 70 blocks off, 53 of 92, while the depot stood at the site's edge.
        if (keepHands.contains(soldier)) {
            Optional<Position> depot = chestOf(keepWorkshop);
            if (depot.isPresent()) {
                return depot;
            }
        }
        // A mine's chest is its stash of ore (stashOre), not a stockpile drawn from.
        Workshop own = workshops.get(soldier);
        return stockpileOf(own != null && mineOfChest(chestCentre(own)).isPresent() ? Optional.empty() : chestOf(own), chestOf(campWorkshop));
    }

    /** Where {@code w}'s chest stands, if it has one standing. */
    private Optional<Position> chestOf(Workshop w) {
        return w == null || !w.chestStands(world) ? Optional.empty()
                : Optional.of(new Position(w.chest().get().x() + 0.5, w.chest().get().y(), w.chest().get().z() + 0.5));
    }

    /**
     * Camp upkeep for a soldier with nothing else to do: by day, out of contact and near the camp,
     * it works on the camp's ground as FORTIFY's first phases do - the trees over it cut, its high
     * ground dug down, its hollows filled - so an idle squad flattens the camp rather than stand in
     * it. Live, three soldiers stood a day in a lit camp with their task all but done. A soldier
     * with any other work handed to it keeps that.
     */
    /** How far above or below the camp's ground a soldier is still at the camp, for its upkeep. */
    static final int CAMP_VERTICAL = 6;
    /** How far over the camp's ground its leaves are still the camp's to cut. */
    static final int LEAVES_OVER_CAMP = 5;

    /** Health a soldier keeps the camp by night with, besides a weapon: half its hearts. */
    static final double NIGHT_UPKEEP_HEALTH = 10;

    /** {@code soldier} off the camp's upkeep, if it was on it, and its route dropped. */
    private void stopUpkeep(String soldier) {
        Optional.of(soldier).filter(upkeeping::remove).ifPresent(n -> route(n, null));
    }

    private void campUpkeep(String squad, List<SoldierStatus> statuses) {
        boolean daylight = !chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
        boolean contact = statuses.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        // By night too in a camp that's lit: nothing spawns on lit ground, so a squad inside the wall
        // line keeps working. Live, squads stood every night at their slots, a lit camp round them.
        boolean litCamp = campGround != null && !daylight
                && Lighting.dark(world, campGround, SECURE_RADIUS) <= NIGHT_UPKEEP_DARK;
        // Not a squad sent to its patrol base for the night: its place is inside.
        boolean sheltering = !daylight && taskOf(squad, Objective.Task.PatrolBase.class).isPresent();
        if (campGround == null || !daylight && !litCamp || contact || sheltering) {
            statuses.forEach(s -> {
                stopUpkeep(s.name());
            });
            return;
        }
        List<SoldierStatus> idle = new ArrayList<>();
        for (SoldierStatus s : statuses) {
            String n = s.name();
            boolean otherWork = (clearing.containsKey(n) || filling.containsKey(n) || groundGather.containsKey(n)) && !upkeeping.contains(n)
                    || walling.containsKey(n) || repointing.containsKey(n)
                    || farming.containsKey(n) && (Optional.ofNullable(farmers.get(n)).map(f -> f.active()).orElse(false)
                            || Farmer.hasWork(world, farming.get(n), scaffolding))
                    // Lighting is work only while it's lighting: live, a squad handed a light plan of
                    // one cell nobody could reach stood about rather than keep the camp.
                    || lighting.containsKey(n) && (Optional.ofNullable(lamplighters.get(n)).map(l -> l.active()).orElse(false)
                    // Torches carried, or the makings: on its way to put them down or make them. Live,
                    // a torchmaker walking to the workshop was handed the upkeep's dig, its clearer
                    // took the hands, and it set out for the workshop again every few ticks.
                    || torchesCarried.getOrDefault(n, 0) > 0 || canMakeTorches.getOrDefault(n, false))
                    || planting.containsKey(n) || cutting.containsKey(n)
                    || gathering.containsKey(n) && !groundGather.containsKey(n)
                    && clockwork.millis() - gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE / 2) > FOUND_NOTHING_MILLIS
                    || patrolLegs.containsKey(n);
            // At the camp, not under it: live, one at the mine's landing 48 blocks down was handed the
            // camp's dig, wall and lights all night, out of reach, each stalled or taken back in seconds.
            boolean near = Math.hypot(s.position().x() - campGround.x(), s.position().z() - campGround.z())
                    <= (daylight ? SECURE_RADIUS * 2 : WALL_RADIUS) && Math.abs(s.position().y() - campGround.y()) <= CAMP_VERTICAL;
            // By night only the fit: lit ground spawns nothing, but phantoms come down out of the sky on anyone under
            // it. Live, an unarmed soldier at 4 health digging the camp by night was killed by one.
            boolean fit = daylight || s.condition().filter(c -> c.health() >= NIGHT_UPKEEP_HEALTH
                    && c.kit().flatMap(Kit::weapon).isPresent()).isPresent();
            if (otherWork || !near || !fit) {
                stopUpkeep(n);
            } else {
                idle.add(s);
            }
        }
        if (idle.isEmpty()) {
            return;
        }
        // Shared with every squad's soldiers keeping the camp, in name order: live (field487), four squads' dug one box.
        List<SoldierStatus> crew = Stream.concat(idle.stream(), upkeeping.stream().filter(n -> idle.stream().noneMatch(s -> s.name().equals(n)))
                .map(activeSoldiers::get).filter(java.util.Objects::nonNull).map(DeterministicSoldier::status)).sorted(Comparator.comparing(SoldierStatus::name)).toList();
        Map<String, Order> work = groundwork(squad, squadStates.get(squad), campGround, crew);
        for (SoldierStatus s : idle) {
            Order order = work.get(s.name());
            if (order == null) {
                stopUpkeep(s.name());
                continue;
            }
            if (upkeeping.add(s.name())) {
                LOG.info("[{}] {} has nothing else to do - keeps the camp: {}", squad, s.name(), order);
            }
            route(s.name(), order);
        }
    }

    /**
     * The ground work round {@code feet}: the engineer's dig box over the camp's ground and fill box
     * under it (command.Groundwork), what's left in each logged as it changes, and each of
     * {@code hands}'s concrete command from the squad's drill (command.Tasking). Never the cells of
     * a hut, a workshop, the wall or the mine.
     */
    private Map<String, Order> groundwork(String squad, SquadState state, BlockPos feet, List<SoldierStatus> hands) {
        Set<Cell> except = groundExcept();
        Cell at = new Cell(feet.x(), feet.y(), feet.z());
        Box dig = Groundwork.dig(at, state.groundRadius); // out a ring at a time once level (CampGround), dug only past the wall line
        if (state.groundRadius > WALL_RADIUS - 1) {
            except.addAll(CampGround.kept(dig, Groundwork.dig(at, WALL_RADIUS - 1), campFarm, TreeFarm.site(world, feet, CAMP_RADIUS).map(g -> g.plot().stream().filter(c -> TreeFarm.planted(world, c)).toList()).orElse(List.of())));
        }
        // Inside the wall line, as the dig is: the wall's own footing is the masons' (Perimeter.open).
        Box fill = Groundwork.fill(at, WALL_RADIUS - 1);
        // The camp's mine's shaft - the 3x3 columns round its head, where the night shift cuts it - is
        // never ground work, by day or night, whoever's on it: live, a day's fill with no squad on MINE would have poured dirt down it.
        if (camp != null) {
            List<int[]> heads = new ArrayList<>();
            heads.add(new int[]{camp[0] + ChainOfCommand.MINE_HEAD_DX, camp[1] + ChainOfCommand.MINE_HEAD_DZ});
            campStairs().ifPresent(s -> heads.add(new int[]{s.head().x(), s.head().z()}));
            for (int[] h : heads) {
                for (int x = h[0] - 1; x <= h[0] + 1; x++) {
                    for (int z = h[1] - 1; z <= h[1] + 1; z++) {
                        for (int y = fill.minY(); y <= dig.maxY(); y++) {
                            except.add(new Cell(x, y, z));
                        }
                    }
                }
            }
        }
        // Cobblestone over the floor, and the steps the soldiers built, only while no wall is going
        // up: the masons' steps inside it, and dug out from under them the wall stood unfinished in
        // the squad simulation. A step is known by where it went, not its block - dirt before
        // cobblestone, and read as the ground's dirt once the server's update replaces the stand-in.
        pruneSteps();
        if (!campWalled) {
            for (int x = dig.minX(); x <= dig.maxX(); x++) {
                for (int z = dig.minZ(); z <= dig.maxZ(); z++) {
                    for (int y = dig.minY(); y <= dig.maxY(); y++) {
                        if (world.isCobblestone(x, y, z)) {
                            except.add(new Cell(x, y, z));
                        }
                    }
                }
            }
            scaffolding.forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        }
        // Leaves high over the camp aren't its ground work: their trunk is, and cut, they decay on their
        // own. Live, six soldiers stood under a canopy 11 blocks up with no log under it, a dig of twelve leaves between them, for minutes.
        for (int x = dig.minX(); x <= dig.maxX(); x++) {
            for (int z = dig.minZ(); z <= dig.maxZ(); z++) {
                for (int y = feet.y() + LEAVES_OVER_CAMP + 1; y <= dig.maxY(); y++) {
                    if (world.leaves(x, y, z).isPresent()) {
                        except.add(new Cell(x, y, z));
                    }
                }
            }
        }
        List<BlockPos> toDig = Clearing.toClear(world, dig, except);
        campCut = toDig;
        Box inner = Groundwork.dig(at, WALL_RADIUS - 1); // what's left inside the wall line: the levelling the wall and the rest wait on, the rings never
        int digLeft = (int) toDig.stream().filter(b -> inner.contains(b.x(), b.y(), b.z())).count();
        List<BlockPos> toFill = digLeft == 0 ? Clearing.toFill(world, fill, except) : List.of();
        int fillLeft = digLeft > 0 ? -1 : toFill.size();
        String said = "ground work round " + feet + ": " + toDig.size() + " blocks to dig (" + dig + ")"
                + (fillLeft < 0 ? "" : ", " + fillLeft + " cells to fill (" + fill + ")");
        if (!said.equals(state.groundSaid)) {
            LOG.info("[{}] {}", squad, said);
            state.groundSaid = said;
        }
        state.digLeft = digLeft;
        state.fillLeft = fillLeft;
        state.groundRadius = CampGround.grown(state.groundRadius, toDig.size(), fillLeft, CAMP_RADIUS, squad);
        // Split where the work is, not the whole site (live in the squad simulation, a pit in one half of the camp left the soldier
        // given the other half nothing to fill), a hand to a strip and the spares to other work (SiteGeometry.shareGround).
        Map<String, Order> orders = shareGround(state, hands, at, state.groundRadius + 1, bounds(toDig), bounds(toFill), except, toFill.isEmpty() ? toDig.size() : digLeft, clockwork.millis());
        state.fetchingDirt.clear();
        orders.forEach((n, o) -> {
            if (o instanceof Order.Gather) {
                state.fetchingDirt.add(n);
            }
        });
        return orders;
    }

    /**
     * The keep's work (command.Keep): everything over its slab's bottom dug out but the slab's own
     * stone, then its slab and the hollows under it filled with cobblestone, a strip each
     * (command.Tasking). A soldier short of cobblestone draws it from the stockpile - the mine's,
     * by way of the miners' loads - while the chest was last seen holding some; with none there,
     * it's free for other work, and the keep waits on the mine.
     */
    private Map<String, Order> keepwork(String squad, SquadState state, List<SoldierStatus> hands) {
        Flatten ground = chainOfCommand.flatten();
        Set<Cell> kept = groundExcept();
        Set<Cell> digExcept = new HashSet<>(kept);
        Box slab = ground.slab();
        for (int x = slab.minX(); x <= slab.maxX(); x++) {
            for (int z = slab.minZ(); z <= slab.maxZ(); z++) {
                for (int y = slab.minY(); y <= slab.maxY(); y++) {
                    // The slab's own cobblestone, and rock, which is as good: kept. Live in the squad
                    // simulation, a block laid was the dig's again, and dug and laid in turn.
                    // A soil site's top keeps its soil instead, and its rock is dug for dirt to go in.
                    if (ground.soil() ? world.isSoil(x, y, z) : world.isStone(x, y, z) || world.isCobblestone(x, y, z)) {
                        digExcept.add(new Cell(x, y, z));
                    }
                }
            }
        }
        // Salvage (backlog/salvage-and-pack.md): a chest standing on the ground to flatten - the old depot's,
        // the site moved onto it - is emptied into the depot before it's dug; till it's seen empty it stands
        // on a pillar of the ground under it and round it, so its salvager can still reach it. Dug, a chest spills what it holds on the ground.
        Set<BlockPos> condemnedNow = condemned(ground, hands);
        for (BlockPos c : condemnedNow) {
            if (emptied(c)) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = ground.bottom(); y <= c.y(); y++) {
                        digExcept.add(new Cell(c.x() + dx, y, c.z() + dz));
                    }
                }
            }
        }
        // Each squad on the keep its own share of it, west to east in the roster's order: two squads
        // splitting the whole site each, every strip was two soldiers'.
        List<String> onKeep = squadStates.keySet().stream().filter(s -> taskOf(s, Objective.Task.Keep.class).isPresent()).sorted().toList();
        int share = Math.max(0, onKeep.indexOf(squad));
        // Shares of the site with its bench round it, so the ring is some squad's ground too.
        Box siteDig = ground.dig();
        Box digShare = new Box(siteDig.minX() - Flatten.BENCH, siteDig.minY(), siteDig.minZ() - Flatten.BENCH,
                siteDig.maxX() + Flatten.BENCH, siteDig.maxY(), siteDig.maxZ() + Flatten.BENCH)
                .strips(Math.max(1, onKeep.size())).get(share);
        Box fillShare = ground.fill().strips(Math.max(1, onKeep.size())).get(share);
        // Level by level, strip by strip: each soldier's floor is two under the highest ground left in
        // and round its strip (Tasking.keepDig), every squad's share counted - the seam between two squads' strips is an edge like any other.
        // Ore whose drop its strip's builder can't get - emerald with a stone pickaxe - is set aside,
        // with the ground under it, till that builder carries the pickaxe for it (operator,
        // 2026-11-13: left for a tool of the right strength). Left in the dig it would be its strip's
        // top for good, and in lock step hold the whole site at its level. Set aside, the site goes on
        // down past it; with the pickaxe it's the strip's top again and is cleared like any block.
        // Set aside only what nobody on the site can take: the best pickaxe among every squad's hands,
        // not this squad's, nor the one whose rectangle it's in - one answer for every cycle, whoever
        // holds what. Ore one builder can't take and another can stays in the dig, and the ore errand
        // sends the one who can. Live, the set-aside followed each squad and each plan's holders, the
        // site's highest ground with it, and the level went 90, 83, 89, 83 every second, dealt out again each time.
        Set<String> siteHands = new HashSet<>();
        keepHandsAt.values().forEach(m -> siteHands.addAll(m.keySet()));
        hands.forEach(h -> siteHands.add(h.name()));
        int squadsBest = siteHands.stream().mapToInt(n -> pickaxeTiers.getOrDefault(n, Hardness.HAND)).max().orElse(Hardness.HAND);
        Set<Cell> oreAside = new HashSet<>();
        // And ore left in the dig that the one holding its rectangle can't take: the ore errand's,
        // for the nearest who can - live, an emerald in a builder's rectangle stood, set aside for
        // nobody since another squad's builder had the pickaxe, and the one who had it never sent.
        Set<Cell> beyondHolder = new HashSet<>();
        KeepPlan held = keepPlan;
        for (BlockPos c : siteClear(ground, digExcept)) {
            if (!world.isOre(c.x(), c.y(), c.z())) {
                continue;
            }
            Optional<Hardness> hardness = world.hardness(c.x(), c.y(), c.z()).filter(h -> h.tool() == Hardness.Tool.PICKAXE);
            if (hardness.filter(h -> !h.drops(squadsBest)).isPresent()) {
                oreAside.add(new Cell(c.x(), c.y(), c.z()));
            } else if (held != null && hardness.isPresent()) {
                held.boxes().entrySet().stream()
                        .filter(e -> e.getValue().stream().anyMatch(b -> c.x() >= b.minX() && c.x() <= b.maxX() && c.z() >= b.minZ() && c.z() <= b.maxZ()))
                        .findFirst()
                        .filter(e -> !hardness.get().drops(pickaxeTiers.getOrDefault(e.getKey(), Hardness.HAND)))
                        .ifPresent(e -> beyondHolder.add(new Cell(c.x(), c.y(), c.z())));
            }
        }
        keepOreBeyondHolder.put(squad, Set.copyOf(beyondHolder));
        if (!oreAside.equals(state.keepOreAside)) {
            if (!oreAside.isEmpty()) {
                LOG.info("[{}] the keep sets aside {} ore block(s) its builders' pickaxes won't get the drop from - {} - till one carries a better",
                        squad, oreAside.size(), oreAside.stream().limit(4).map(c -> c.x() + " " + c.y() + " " + c.z()).toList());
            }
            state.keepOreAside = oreAside;
        }
        keepOreAsideBySquad.put(squad, oreAside.size());
        digExcept.addAll(oreAside);
        List<BlockPos> wholeDig = siteClear(ground, digExcept);
        List<BlockPos> toDig = wholeDig.stream().filter(c -> c.x() >= digShare.minX() && c.x() <= digShare.maxX()
                && c.z() >= digShare.minZ() && c.z() <= digShare.maxZ()).toList();
        // The ground's tops, not the soldiers' own steps: a step put down to climb from the floor to
        // a strip on the rim is no ground, and counted as the site's highest it held every builder
        // at its level for one block - live, the level went 71, 72, 73, 72 and 70, 77, 70, 71, a
        // step each time, with eight standing by. A step over the level is still dug, by the one
        // whose rectangle it stands in (foremansNet), so none is left high.
        pruneSteps();
        Map<Long, Integer> tops = new HashMap<>();
        Map<Long, Integer> stepTops = new HashMap<>();
        // A tree's logs and leaves aren't ground either: they don't set the level, but like a step
        // they're dug with the level under them, and once the ground is gone they're the level - live,
        // a tree's last two blocks at y=78 stood over a finished keep with nobody given them.
        wholeDig.forEach(c -> (scaffolding.contains(c) || world.isLog(c.x(), c.y(), c.z()) || world.leaves(c.x(), c.y(), c.z()).isPresent()
                ? stepTops : tops).merge(((long) c.x() << 32) ^ (c.z() & 0xffffffffL), c.y(), Math::max));
        // A column whose chunk nobody has loaded is unknown, not dug: its last known top stands for it,
        // or the level drops when the chunk goes and comes back when it's seen again. Live, the big
        // site's east edge is the first column of a chunk mostly off it, loaded and let go as builders
        // moved, and the level went 86, 87, 86 a minute, dealt out again each time.
        synchronized (keepColumnTops) {
            for (int x = ground.minX() - Flatten.BENCH; x <= ground.maxX() + Flatten.BENCH; x++) {
                for (int z = ground.minZ() - Flatten.BENCH; z <= ground.maxZ() + Flatten.BENCH; z++) {
                    long key = ((long) x << 32) ^ (z & 0xffffffffL);
                    if (world.isLoaded(x, z)) {
                        Integer now = tops.get(key);
                        if (now == null) {
                            keepColumnTops.remove(key);
                        } else {
                            keepColumnTops.put(key, now);
                        }
                    } else {
                        Integer known = keepColumnTops.get(key);
                        if (known != null) {
                            tops.merge(key, known, Math::max);
                        }
                    }
                }
            }
        }
        java.util.function.IntBinaryOperator top = (x, z) -> tops.getOrDefault(((long) x << 32) ^ (z & 0xffffffffL), Integer.MIN_VALUE);
        java.util.function.IntBinaryOperator stepTop = (x, z) -> stepTops.getOrDefault(((long) x << 32) ^ (z & 0xffffffffL), Integer.MIN_VALUE);
        int layer = tops.values().stream().mapToInt(Integer::intValue).max()
                .orElse(stepTops.values().stream().mapToInt(Integer::intValue).max().orElse(ground.bottom()));
        // Not built up for now (operator, 2026-11-15): taken down to its ground and left. A soil site's top
        // is: the gaps its rock left, filled with dirt.
        Box soilShare = slab.strips(Math.max(1, onKeep.size())).get(share);
        List<BlockPos> toFill = !toDig.isEmpty() ? List.of() : ground.soil() ? Clearing.toFill(world, soilShare, kept)
                : Keep.FILL ? Clearing.toFill(world, fillShare, kept) : List.of();
        state.keepDigLeft = toDig.size();
        keepDigLeftBySquad.put(squad, toDig.size());
        state.keepFillLeft = toDig.isEmpty() ? toFill.size() : -1;
        keepFillLeftBySquad.put(squad, state.keepFillLeft);
        String said = "the keep: " + toDig.size() + " blocks to dig" + (toDig.isEmpty() ? ", " + toFill.size() + " cells to fill"
                : ", its top y=" + toDig.stream().mapToInt(BlockPos::y).max().orElse(ground.bottom()) + ", the keep's y=" + layer) + " in its share " + digShare;
        if (!said.equals(state.keepSaid)) {
            LOG.info("[{}] {}", squad, said);
            state.keepSaid = said;
        }
        // Food first: a builder hungry with nothing to eat is left to its own feeding - the farm, the
        // hunt, a squadmate's share - and comes back when it's fed. Live, with every soldier on the
        // keep, the camp's farm went unkept and a builder worked the hilltop at food 1 with none; and
        // held off for carrying fewer than 10 rations at food 20, two builders dug the camp's ground while their strips stood.
        List<Tasking.Hand> crew = hands.stream()
                .filter(h -> h.condition().map(c -> !Subsistence.mustFeed(c.food(), c.kit().map(Kit::rations).orElse(0))).orElse(true))
                .map(h -> new Tasking.Hand(h.name(),
                h.condition().flatMap(c -> c.kit()).map(Kit::stone).orElse(0))).toList();
        // Each soldier a fixed range of x and z, its strip of the squad's share, from its floor up
        // (operator, 2026-11-13: you get this x range and this z range, top down) - not cut from
        // where work is left: a strip that narrow left no stand beside its last block.
        Map<String, Order> orders = new HashMap<>(!toDig.isEmpty() ? Map.of()
                : ground.soil() ? Tasking.soil(hands.stream().map(h -> new Tasking.Hand(h.name(),
                        h.condition().flatMap(c -> c.kit()).map(Kit::dirt).orElse(0))).toList(), bounds(toFill), soilBorrow(ground), kept, state.fetchingDirt)
                : Tasking.keep(crew, Optional.empty(), bounds(toFill), kept));
        if (toDig.isEmpty() && ground.soil()) {
            state.fetchingDirt.clear();
            orders.forEach((n, o) -> {
                if (o instanceof Order.Gather) {
                    state.fetchingDirt.add(n);
                }
            });
        }
        if (!toDig.isEmpty()) {
            orders.putAll(foremansNet(squad, onKeep, hands, crew, top, stepTop, layer, digExcept));
        } else {
            keepCrews.remove(squad);
        }
        // A pillar's stock: a builder on the keep's dig short of blocks to climb on fetches a load of
        // dirt from a borrow pit off its squad's side of the keep. Live, a builder sent to cut its
        // strip's top 15 blocks up carried one block of dirt - its steps had used the rest, and the hilltop it dug was stone - and stood under it.
        // North for the first share, east for the second - and when its fetchers find nothing left there,
        // the next side round: live, two builders spent a day at a pit dug out, "nothing left to gather".
        // Never west, the depot's side: a pit there is dug out of the depot's ground, and in the squad
        // simulation the first load of dirt took the floor from under the workshop's stand.
        long now = clockwork.millis();
        int[] sidesNow = share == 0 ? PIT_SIDES_FIRST : PIT_SIDES_SECOND;
        int sideNow = sidesNow[Math.floorMod(keepPitShift.getOrDefault(squad, 0), sidesNow.length)];
        // Off each side's own edge, halfway along it: a long site's pit is beside it, not its length away.
        // Live, a pit put past a 129-long site's half-span from its middle sent a builder 72 blocks east.
        Position mid = ground.centre();
        int midX = (int) Math.floor(mid.x()), midZ = (int) Math.floor(mid.z());
        int halfX = (ground.maxX() - ground.minX()) / 2 + 1, halfZ = (ground.maxZ() - ground.minZ()) / 2 + 1;
        // Past the bench, so a pit never holes the slope (Flatten.BENCH).
        int edgeNow = (sideNow == 0 || sideNow == 1 ? halfX : halfZ) + Flatten.BENCH + Groundwork.BORROW_GAP + 3;
        Position pitNow = new Position(midX + (sideNow == 0 ? edgeNow : sideNow == 1 ? -edgeNow : 0), 0,
                midZ + (sideNow == 2 ? edgeNow : sideNow == 3 ? -edgeNow : 0));
        // Only a fetcher at the pit says it's dug out: one stuck far off finds nothing anywhere. Live,
        // a builder shut in by the farm moved its squad's pit round the keep every minute.
        // And not again until its fetchers have had a minute at the new one: a gatherer that finds
        // nothing says so every tick, and live the pit went round the keep every second.
        boolean dugOut = now - keepPitMovedAt.getOrDefault(squad, Long.MIN_VALUE / 2) >= PIT_MOVE_MILLIS
                && hands.stream().filter(h -> keepFetching.contains(h.name()))
                .filter(h -> Math.hypot(h.position().x() - pitNow.x(), h.position().z() - pitNow.z()) <= PIT_NEAR)
                .map(SoldierStatus::name)
                .anyMatch(n -> now - gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE / 2) < PIT_MOVE_MILLIS
                        && gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE / 2) > keepPitMovedAt.getOrDefault(squad, Long.MIN_VALUE / 2));
        if (dugOut) {
            int moved = keepPitShift.merge(squad, 1, Integer::sum);
            keepPitMovedAt.put(squad, now);
            LOG.info("[{}] the keep's borrow pit is dug out - the next is off another side of the keep ({})", squad, moved);
        }
        int[] sidesInTurn = share == 0 ? PIT_SIDES_FIRST : PIT_SIDES_SECOND;
        int side = sidesInTurn[Math.floorMod(keepPitShift.getOrDefault(squad, 0), sidesInTurn.length)];
        int sideHalf = side == 0 || side == 1 ? halfX : halfZ;
        int edge = sideHalf + Flatten.BENCH + Groundwork.BORROW_GAP + 3;
        int pitX = midX + (side == 0 ? edge : side == 1 ? -edge : 0);
        int pitZ = midZ + (side == 2 ? edge : side == 3 ? -edge : 0);
        // Its surface, from high over the hill down: live, a guess at y=68 put the pit under a hill,
        // where no soil has an open top, and every builder's fetch found none all day.
        Optional<BlockPos> pitGround = TerrainSurveyor.surface(world, pitX, pitZ, ground.floor() + 60);
        if (pitGround.isEmpty()) {
            LOG.warn("[{}] no ground at x={} z={} for the keep's borrow pit - its builders fetch no dirt", squad, pitX, pitZ);
        }
        for (SoldierStatus h : hands) {
            int blocks = h.condition().flatMap(c -> c.kit()).map(k -> k.dirt() + k.stone()).orElse(0);
            // A builder waiting for ground restocks too: live (field354), Bob, on top of his own steps at the
            // hilltop with none left, lost his rectangle the moment his draw was set, and the draw with it.
            boolean digging = orders.get(h.name()) instanceof Order.Dig;
            if (!digging && !keepClimbDrawing.contains(h.name()) && blocks >= Tasking.LOW) {
                keepFetching.remove(h.name());
                continue;
            }
            if (!digging && !chainOfCommand.stores().map(st -> st.stone() >= Tasking.LOAD).orElse(false)) {
                keepFetching.remove(h.name());
                keepClimbDrawing.remove(h.name());
                continue;
            }
            if (digging && pitGround.isEmpty()) {
                keepFetching.remove(h.name());
                keepClimbDrawing.remove(h.name());
                continue;
            }
            boolean fetching = keepFetching.contains(h.name()) || keepClimbDrawing.contains(h.name()) ? blocks < Tasking.LOAD : blocks < Tasking.LOW;
            // The dig's own cobblestone first, from the depot: it climbs as well as dirt, and the depots
            // hold thousands of it. Live (field347), every pit round the keep was dug out in turn and
            // builders stood a minute at a time finding no dirt, with the depots' rows full of stone.
            // Counted over every surface chest: the stockpile is the one chest last looked in, and live
            // (field349-352) that read under a load all night, and no builder ever drew.
            boolean depotStone = chainOfCommand.stores().map(st -> st.stone() >= Tasking.LOAD).orElse(false);
            if (fetching && depotStone) {
                if (keepClimbDrawing.add(h.name())) {
                    LOG.info("[{}] {} carries {} blocks to climb on - draws a load of cobblestone from the depot first", squad, h.name(), blocks);
                }
                keepFetching.remove(h.name());
            } else if (fetching) {
                keepClimbDrawing.remove(h.name());
                if (keepFetching.add(h.name())) {
                    LOG.info("[{}] {} carries {} blocks to climb on - fetches a load of dirt first", squad, h.name(), blocks);
                }
                orders.put(h.name(), new Order.Gather(Resource.SOIL,
                        Groundwork.borrowPit(new Cell(midX, pitGround.get().y(), midZ), sideHalf + 1 + Flatten.BENCH, side), Tasking.LOAD));
            } else {
                keepFetching.remove(h.name());
                keepClimbDrawing.remove(h.name());
            }
        }
        boolean stocked = chainOfCommand.stockpile().map(st -> st.stone() > 0).orElse(false);
        for (Tasking.Hand hand : crew) {
            // Short for the fill: to the stockpile for a load, and kept at it until it carries one.
            boolean short_ = toDig.isEmpty() && !toFill.isEmpty() && !orders.containsKey(hand.name());
            if (short_ && stocked && stockpileFor(hand.name()).isPresent()) {
                if (keepDrawing.add(hand.name())) {
                    LOG.info("[{}] {} goes to the stockpile for cobblestone for the keep", squad, hand.name());
                }
            } else if (!short_ || hand.blocks() >= WALL_BLOCKS_LOAD) {
                keepDrawing.remove(hand.name());
            }
        }
        // The haul road (operator, 2026-11-15; docs/military-doctrine.md § 17, rule 5): one carved way
        // from the depot to the keep's floor, planned again every half minute as the floor goes down;
        // while any of it is uncut, the builder nearest it cuts it, and has no rectangle meanwhile.
        // Every squad's hands on the keep, so the nearest of all of them cuts it, whichever squad's cycle runs.
        Map<String, Position> handsOn = new HashMap<>();
        hands.stream().filter(h -> !keepFighting.contains(h.name()) && !farming.containsKey(h.name()))
                .forEach(h -> handsOn.put(h.name(), h.position()));
        keepRoadHands.put(squad, Map.copyOf(handsOn));
        synchronized (keepPlanLock) {
            List<LevelPlan.Builder> everyone = new ArrayList<>();
            keepRoadHands.values().forEach(m -> m.forEach((n2, p2) -> everyone.add(new LevelPlan.Builder(n2, p2))));
            everyone.sort(Comparator.comparing(LevelPlan.Builder::name));
            haulRoad(everyone, clockwork.millis());
        }
        AccessRoute road = keepRoad;
        String roadCutter = keepRoadCutter;
        if (road != null && roadCutter != null && !road.toCut().isEmpty() && handsOn.containsKey(roadCutter)) {
            orders.put(roadCutter, roadDig(road));
        }
        // Ore set aside for a better pickaxe, at or over the level - in reach on its pillar, or all
        // that's left: taken by the keep's nearest hand who carries one, whatever its squad. Live, the
        // keep's emerald was in one squad's share and the one iron pickaxe in another's, and it stood.
        Map<String, Position> on = new HashMap<>();
        hands.forEach(h -> on.put(h.name(), h.position()));
        keepHandsAt.put(squad, Map.copyOf(on));
        KeepPlan levelled = keepPlan;
        int level = levelled != null && levelled.level() != Integer.MIN_VALUE && !keepCrews.isEmpty() ? levelled.level() : ground.bottom();
        for (SoldierStatus h : hands) {
            Optional<Cell> errand = oreErrand(h.name(), level);
            if (errand.isPresent()) {
                Cell c = errand.get();
                Set<Cell> open = new HashSet<>(digExcept);
                open.remove(c);
                if (!(orders.get(h.name()) instanceof Order.Dig d && d.box().minX() == c.x() && d.box().minY() == c.y() && d.box().minZ() == c.z())) {
                    LOG.info("[{}] {} takes the ore set aside at {} {} {} - it carries the pickaxe for it", squad, h.name(), c.x(), c.y(), c.z());
                }
                orders.put(h.name(), new Order.Dig(new Box(c.x(), c.y(), c.z(), c.x(), c.y(), c.z()), open));
            }
        }
        if (toDig.isEmpty() && !toFill.isEmpty() && orders.isEmpty() && !stocked && !"waiting".equals(state.keepWaiting)) {
            LOG.warn("[{}] the keep waits on cobblestone: {} cells to fill, none carried, none in the stockpile", squad, toFill.size());
            state.keepWaiting = "waiting";
        } else if (!orders.isEmpty()) {
            state.keepWaiting = null;
        }
        return orders;
    }

    /** The keep's haul road as last planned, when, and who cuts it - guarded by {@link #keepPlanLock}. */
    private volatile AccessRoute keepRoad;
    private long keepRoadAt = Long.MIN_VALUE / 2;
    private volatile String keepRoadCutter;

    /** Each squad's hands free for the haul road, by squad - the squad loop's. */
    private final Map<String, Map<String, Position>> keepRoadHands = new ConcurrentHashMap<>();

    /** How often the haul road is planned again: the floor it leads to goes down as the keep is dug. */
    static final long ROAD_PLAN_MILLIS = 30_000;

    /**
     * The keep's haul road: planned from the depot's stand to a standing cell on the keep's floor
     * ({@link AccessRoute}), out of the keep's dig till its last cell, every {@link #ROAD_PLAN_MILLIS};
     * its cutter the builder on hand nearest its start while any of it is left to cut, nobody once
     * it's cut. Said when it changes.
     */
    private void haulRoad(List<LevelPlan.Builder> onHand, long now) {
        Workshop depot = keepWorkshop;
        if (depot == null || onHand.isEmpty()) {
            keepRoadCutter = null;
            return;
        }
        if (now - keepRoadAt >= ROAD_PLAN_MILLIS || keepRoad == null) {
            keepRoadAt = now;
            Flatten ground = chainOfCommand.flatten();
            java.util.function.Predicate<BlockPos> inKeep = c -> ground.contains(c.x(), c.z());
            Optional<AccessRoute> planned = AccessRoute.plan(world, depot.stand(), c -> inKeep.test(c) && Pathfinder.standable(world, c), inKeep);
            AccessRoute was = keepRoad;
            keepRoad = planned.orElse(null);
            if (planned.isEmpty() && was != null) {
                LOG.warn("[Keep] no haul road from the depot at {} to the keep's floor within reach of the search", depot.stand());
            } else if (planned.isPresent() && (was == null || !was.feet().equals(planned.get().feet()))) {
                LOG.info("[Keep] the haul road: {} steps from the depot at {} to {}, {} block(s) to cut, {} floor(s) to put down",
                        planned.get().feet().size(), depot.stand(), planned.get().feet().getLast(), planned.get().toCut().size(),
                        planned.get().floors().size());
            }
        }
        AccessRoute road = keepRoad;
        // Kept by the one cutting it while it's on hand: picked afresh as the nearest each cycle, the
        // cutter changed every second or two as the builders moved, each change was a new crew for
        // the level, and the level was shared out again every second (live: Carol, Gus, Hal, Carol...).
        String was = keepRoadCutter;
        String cutter = road == null || road.toCut().isEmpty() ? null
                : was != null && onHand.stream().anyMatch(b -> b.name().equals(was)) && !inWoodParty(was) ? was
                : onHand.stream().filter(b -> !inWoodParty(b.name()))
                .min(Comparator.comparingDouble(b -> Math.hypot(b.at().x() - road.feet().getFirst().x(), b.at().z() - road.feet().getFirst().z())))
                .map(LevelPlan.Builder::name).orElse(null);
        if (!Objects.equals(cutter, keepRoadCutter) && cutter != null) {
            LOG.info("[Keep] {} cuts the haul road: {} block(s) left", cutter, road.toCut().size());
        }
        keepRoadCutter = cutter;
    }

    /** Each squad's hands on the keep and where they stand, as its last keep cycle saw them - the squad loop's. */
    private final Map<String, Map<String, Position>> keepHandsAt = new ConcurrentHashMap<>();

    /** Soldiers whose dig or fill is the keep's, not the camp's - squad loop writes, tick reads. */
    private final Set<String> keepHands = ConcurrentHashMap.newKeySet();

    /**
     * The keep's foreman's plan, run each squad cycle for {@code squad} (operator, 2026-11-14): the
     * site comes down in lock step, one level at a time - {@code layer}, the highest ground left
     * anywhere on it - and at the start of each level the foreman shares what's left of it out among
     * the builders on hand, every squad's, a rectangle of x and z each ({@link LevelPlan}); with less
     * work than builders, the nearest get it and the rest wait where they stand. Then the plan is
     * held: each builder keeps its rectangles till they're cleared or due, when they go to the
     * nearest builder who's done, as a builder's do when it's off the crew; one come onto the level
     * late takes half of the most that's left of one builder's. Nothing is kept from level to
     * level. The foreman - the first builder of the first squad on the keep - says each builder's
     * rectangle on the net ({@code /msg}), so the plan reads in the chat as it does in the log.
     */
    private Map<String, Order> foremansNet(String squad, List<String> onKeep, List<SoldierStatus> hands, List<Tasking.Hand> crew,
                                           java.util.function.IntBinaryOperator top, java.util.function.IntBinaryOperator stepTop,
                                           int layer, Set<Cell> except) {
        long now = clockwork.millis();
        // The night isn't the dig's: its level's clock stops while it's dark, and starts again at dawn.
        // Live, a level stood over a night at 74 blocks and read 10% of its best.
        boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
        synchronized (keepPlanLock) {
            if (dark && keepDarkSince == Long.MIN_VALUE) {
                keepDarkSince = now;
            } else if (!dark && keepDarkSince != Long.MIN_VALUE) {
                keepLevelAt += now - keepDarkSince;
                keepDarkSince = Long.MIN_VALUE;
            }
        }
        Set<String> inCrew = crew.stream().map(Tasking.Hand::name).collect(Collectors.toSet());
        // On hand: on the keep, fed, not fighting - and not off keeping the farm. Live, a level's last
        // three blocks went to a builder who walked 80 blocks to the farm, and seven stood by for her.
        keepCrews.put(squad, hands.stream().filter(h -> inCrew.contains(h.name()) && !keepFighting.contains(h.name())
                        && !farming.containsKey(h.name()))
                .map(h -> new LevelPlan.Builder(h.name(), h.position())).toList());
        // The squad leader's appointment: the first builder of the first squad on the keep, again whenever that one is off it.
        if (!onKeep.isEmpty() && squad.equals(onKeep.getFirst()) && !crew.isEmpty() && !inCrew.contains(keepForeman)) {
            keepForeman = crew.getFirst().name();
            LOG.info("[{}] {} is the keep's foreman", squad, keepForeman);
        }
        hands.forEach(h -> keepLogsCarried.put(h.name(), h.condition().flatMap(c -> c.kit()).map(Kit::logs).orElse(0)));
        KeepPlan plan;
        synchronized (keepPlanLock) {
            List<LevelPlan.Builder> onHand = onKeep.stream().flatMap(u -> keepCrews.getOrDefault(u, List.of()).stream())
                    .sorted(Comparator.comparing(LevelPlan.Builder::name)).toList();
            woodParty(onKeep, onHand, now);
            // The haul road's cutter has no rectangle while it cuts it (keepwork).
            String roadCutter = keepRoadCutter;
            keepSmelter = smelter(onHand, keepPlan);
            String smelterNow = keepSmelter;
            List<LevelPlan.Builder> all = onHand.stream().filter(b -> !inWoodParty(b.name()) && !b.name().equals(roadCutter)
                    && !b.name().equals(smelterNow)).toList();
            Set<String> names = all.stream().map(LevelPlan.Builder::name).collect(Collectors.toUnmodifiableSet());
            // The site and its bench round it: the ring's ground is the level's too, highest first.
            Box dug = chainOfCommand.flatten().dig();
            Box site = new Box(dug.minX() - Flatten.BENCH, dug.minY(), dug.minZ() - Flatten.BENCH,
                    dug.maxX() + Flatten.BENCH, dug.maxY(), dug.maxZ() + Flatten.BENCH);
            // Ground under a fight is nobody's until it's over: not shared out - in the squad
            // simulation the fighter's rectangle went to its squadmate, who walked into the fight -
            // and not counted for the level, or the site would wait on it. When the fight's done its
            // ground is the highest again, and the level comes back up to it.
            List<Position> fights = keepFightingAt.entrySet().stream().filter(e -> keepFighting.contains(e.getKey()))
                    .map(Map.Entry::getValue).toList();
            // And a column's top as the world has it now, where it's seen: the site's scan is up to a
            // second old and each squad's its own. Read off the scan, a builder stood that second after
            // each rectangle it finished ("clearer idle, nothing to cut", an eighth of the crew's
            // ticks); read live for the level's columns but not for the level, the level went 79, 78,
            // 79, 78 in seconds, a squad with an older scan dealing it again each time.
            // A column whose top was cut since the scan is followed down to what's left of it, not
            // dropped: the scan is up to a second old, and live (field294) a dirt tower in the bench's
            // outer rows, cut a block at a time, dropped out at each cut - the level fell from y=88 to
            // 74, was dealt out, and came back up at the next scan, six times in a minute.
            java.util.function.IntBinaryOperator calmAll = (x, z) -> {
                int t = top.applyAsInt(x, z);
                // A top since put on the step record - the scan caught it before it was - is followed down too.
                if (t == Integer.MIN_VALUE || !world.isLoaded(x, z) || world.isSolid(x, t, z) && !scaffolding.contains(new BlockPos(x, t, z))) {
                    return t;
                }
                int row = Math.max(0, Math.max(Math.max(dug.minX() - x, x - dug.maxX()), Math.max(dug.minZ() - z, z - dug.maxZ())));
                int lowest = Math.max(dug.minY() + row, t - LIVE_TOP_DROP);
                // Never onto the force's own step: it's no ground. Live (field329), the follow-down landed on
                // the step a climber had just put up, read it as the column's top, and raised the level to it
                // - "live 86, over last's a step true" - every few seconds on the hilltop.
                for (int y = t - 1; y >= lowest; y--) {
                    if (world.isSolid(x, y, z) && !scaffolding.contains(new BlockPos(x, y, z))) {
                        return y;
                    }
                }
                return Integer.MIN_VALUE;
            };
            // Ground under a fight counts for the level - the highest block first, never one skipped (the
            // clearing rules) - but isn't dealt out: live (field358), a fight at the bench's corner dropped
            // the level from 100 to 97 and back three times in two minutes, the crew dealt lower ground each time.
            java.util.function.IntBinaryOperator calm = (x, z) ->
                    fights.stream().anyMatch(f -> Math.hypot(f.x() - (x + 0.5), f.z() - (z + 0.5)) <= KEEP_FIGHT_CLEAR)
                            ? Integer.MIN_VALUE : calmAll.applyAsInt(x, z);
            java.util.function.IntBinaryOperator calmStep = (x, z) -> {
                int t = stepTop.applyAsInt(x, z);
                return fights.stream().anyMatch(f -> Math.hypot(f.x() - (x + 0.5), f.z() - (z + 0.5)) <= KEEP_FIGHT_CLEAR)
                        || t != Integer.MIN_VALUE && world.isLoaded(x, z) && !world.isSolid(x, t, z) ? Integer.MIN_VALUE : t;
            };
            int level = Integer.MIN_VALUE;
            for (int x = site.minX(); x <= site.maxX(); x++) {
                for (int z = site.minZ(); z <= site.maxZ(); z++) {
                    level = Math.max(level, calmAll.applyAsInt(x, z));
                }
            }
            // Only steps left: the level is theirs, so they're taken down.
            for (int x = site.minX(); x <= site.maxX() && level == Integer.MIN_VALUE; x++) {
                for (int z = site.minZ(); z <= site.maxZ(); z++) {
                    level = Math.max(level, calmStep.applyAsInt(x, z));
                }
            }
            List<LevelPlan.Column> columns = new ArrayList<>();
            for (int x = site.minX(); x <= site.maxX() && level != Integer.MIN_VALUE; x++) {
                for (int z = site.minZ(); z <= site.maxZ(); z++) {
                    // The level's ground, and a step standing at or over it, shared out with it.
                    if (calm.applyAsInt(x, z) == level || calmStep.applyAsInt(x, z) >= level) {
                        columns.add(new LevelPlan.Column(x, z));
                    }
                }
            }
            plan = keepPlan;
            if (plan != null && plan.level() != level && plan.level() != Integer.MIN_VALUE) {
                Map<String, LevelPace.Tally> digs = new HashMap<>();
                Set.copyOf(keepPace.keySet()).forEach(n -> Optional.ofNullable(keepPace.remove(n)).ifPresent(t -> digs.put(n, t)));
                Map<String, Map<String, Integer>> spent = new HashMap<>();
                Set.copyOf(keepSpent.keySet()).forEach(n -> Optional.ofNullable(keepSpent.remove(n)).ifPresent(m -> spent.put(n, Map.copyOf(m))));
                LevelPace pace = new LevelPace(plan.level(), now - keepLevelAt, digs, plan.crew().size(), spent);
                keepRun = keepRun.plus(pace);
                // Who stood with a rectangle and nothing its clearer would cut (backlog/keep-pace.md),
                // a tenth of its ticks or more, and what it held at the level's end.
                KeepPlan ended = plan;
                spent.forEach((n, m) -> {
                    int ticks = m.values().stream().mapToInt(Integer::intValue).sum();
                    int idle = m.getOrDefault("clearer idle, nothing to cut", 0);
                    if (ticks > 0 && idle * 10 >= ticks) {
                        LOG.info("[Keep] {} had nothing to cut {}% of y={} ({} ticks), holding {}, its cover {} cell(s)", n, 100 * idle / ticks,
                                ended.level(), idle, boxesSaid(ended.boxes().getOrDefault(n, List.of())),
                                clearing.getOrDefault(n, List.of()).size());
                    }
                });
                if (pace.slow()) {
                    LOG.warn("[Keep] pace: {}", pace.summary());
                } else {
                    LOG.info("[Keep] pace: {}", pace.summary());
                }
                if (keepRun.slow()) {
                    LOG.warn("[Keep] pace: {}", keepRun.summary());
                } else {
                    LOG.info("[Keep] pace: {}", keepRun.summary());
                }
            }
            // A level that rises again - ground come back over one dug - says which columns raised it and
            // whether they're seen or remembered: backlog/keep-pace.md's flicker in the bench's outer rows.
            // With each column, its top in this scan, in the last cycle's, and as read live - so a rise is told
            // apart: a column the last scan didn't have (the scan's), one it had and the live read dropped
            // (the live check's), or ground really come back (both had it lower).
            if (plan != null && plan.level() != Integer.MIN_VALUE && level > plan.level()) {
                Map<Long, Integer> last = lastScanTops;
                // The columns that raised it - at the new level - not the level's first few, which may be its steps.
                int risen = level;
                LOG.info("[Keep] the level rose from y={} to y={}: {}", plan.level(), level, columns.stream()
                        .filter(c -> calm.applyAsInt(c.x(), c.z()) == risen).limit(4).map(c -> {
                            long key = ((long) c.x() << 32) ^ (c.z() & 0xffffffffL);
                            Integer was = last.get(key);
                            // And whether the blocks that came into the scan are steps on the record: the step
                            // over last cycle's top, and the top now.
                            int topNow = top.applyAsInt(c.x(), c.z());
                            String steps = was == null || topNow <= was ? ""
                                    : ", over last's a step " + scaffolding.contains(new BlockPos(c.x(), was + 1, c.z()))
                                    + ", top a step " + scaffolding.contains(new BlockPos(c.x(), topNow, c.z()));
                            return c.x() + " " + c.z() + (world.isLoaded(c.x(), c.z()) ? " seen" : " remembered")
                                    + " (scan " + topNow + ", last cycle's " + (was == null ? "none" : was)
                                    + ", live " + calm.applyAsInt(c.x(), c.z()) + steps + ")";
                        }).toList());
            }
            Map<Long, Integer> scanned = new HashMap<>();
            for (int x = site.minX(); x <= site.maxX(); x++) {
                for (int z = site.minZ(); z <= site.maxZ(); z++) {
                    int t = top.applyAsInt(x, z);
                    if (t != Integer.MIN_VALUE) {
                        scanned.put(((long) x << 32) ^ (z & 0xffffffffL), t);
                    }
                }
            }
            lastScanTops = scanned;
            if (plan == null || plan.level() != level) {
                keepLevelAt = now;
                // The crew its blocks want, for the squads sent (backlog/flatten-campaign.md): a standard
                // share a builder, the whole force for a full floor.
                // And the levels just under it: a hilltop's last few blocks a level over a wide one
                // let every squad go but two, and called them back one by one a level later - live, a
                // level of 1,038 blocks was dealt to the two on it, with the rest at the camp.
                if (level != Integer.MIN_VALUE) {
                    int ahead = 0;
                    for (int x = site.minX(); x <= site.maxX(); x++) {
                        for (int z = site.minZ(); z <= site.maxZ(); z++) {
                            if (calm.applyAsInt(x, z) >= level - CREW_LOOK_AHEAD) {
                                ahead++;
                            }
                        }
                    }
                    // Never fewer than one squad (operator, 2026-11-17: one squad on it by day, two or three collecting
                    // resources if necessary): a level's last blocks at the bench's edge, sized to one builder
                    // or none, let every squad go elsewhere and the level stood. Live (field395-414), y=90 stood a day
                    // at 520, 55, 12, 6 blocks left - "0 of 0 builders" for hours - and the next level waited on it.
                    chainOfCommand.onKeepLevel(level, columns.size());
                    chainOfCommand.onFlattenCrew(Math.max(MIN_KEEP_BUILDERS, (Math.max(columns.size(), ahead) + BLOCKS_A_BUILDER - 1)
                            / BLOCKS_A_BUILDER));
                }
            }
            // The foreman's protocol (operator, 2026-11-15): each builder is given one or more touching
            // rectangles at the level and holds them till they're cleared - its completion - or till
            // their time is up, when they're given to someone else. Nothing is dealt again while it's
            // held: live, the level was shared out again every half minute and each time a builder
            // finished, and builders walked from lane to lane and never finished one.
            java.util.function.Predicate<Box> hasWork = b -> columns.stream()
                    .anyMatch(c -> c.x() >= b.minX() && c.x() <= b.maxX() && c.z() >= b.minZ() && c.z() <= b.maxZ());
            // Ground left that nobody holds - its holders all gone off the crew with nobody to take it,
            // at nightfall say: the level dealt again, fresh. Live, the night took the whole crew off,
            // the last rectangles were handed round till their last holder went too, and at dawn the
            // level's 269 blocks were nobody's for half an hour.
            KeepPlan dealtPlan = plan;
            boolean unheld = dealtPlan != null && dealtPlan.level() == level && !columns.isEmpty() && !all.isEmpty()
                    && dealtPlan.boxes().entrySet().stream().noneMatch(e -> names.contains(e.getKey()) && e.getValue().stream().anyMatch(hasWork));
            if (plan == null || plan.level() != level || unheld) {
                // Supplies between levels, not in the middle of one (operator, 2026-11-15): one low on
                // rations with food in the stockpile, or with no pickaxe, sits this level out to get
                // them, and is dealt in at the next. Off on an errand, its rectangles stood till they timed out.
                boolean foodStocked = chainOfCommand.stockpile().map(st -> st.rations() > 0).orElse(false);
                Set<String> resupplying = hands.stream().filter(h -> h.condition().flatMap(c -> c.kit())
                                .filter(k -> foodStocked && Subsistence.low(k.rations()) || !k.pickaxe()).isPresent())
                        .map(SoldierStatus::name).collect(Collectors.toSet());
                List<LevelPlan.Builder> dealtIn = all.stream().filter(b -> !resupplying.contains(b.name())).toList();
                if (dealtIn.isEmpty()) {
                    dealtIn = all;
                }
                if (!resupplying.isEmpty() && dealtIn != all) {
                    LOG.info("[Keep] level y={}: {} sit(s) it out to resupply", level, resupplying.stream().sorted().toList());
                }
                // Shared by the time each column takes to dig, not its count (operator, 2026-11-17).
                int digLevel = level;
                java.util.function.ToIntFunction<LevelPlan.Column> digTicks = c -> columnTicks(c.x(), c.z(), digLevel, calm.applyAsInt(c.x(), c.z()));
                Map<String, List<Box>> boxes = level == Integer.MIN_VALUE ? Map.of()
                        : LevelPlan.plan(level, siteCeiling(site, level, calm, calmStep), columns, dealtIn, digTicks);
                plan = new KeepPlan(level, names, boxes, now);
                keepPlan = plan;
                keepDue.clear();
                keepDealt.clear();
                keepDealt.addAll(boxes.keySet());
                Map<String, Position> dealtAt = all.stream().collect(Collectors.toMap(LevelPlan.Builder::name, LevelPlan.Builder::at));
                int dealtLevel = level;
                boxes.forEach((n, bs) -> keepDue.put(n, now + dueMillis(bs, columns, climb(dealtAt.get(n), dealtLevel))));
                LOG.info("[Keep] level y={}: {} block(s) left, {} of {} builder(s) on it - {}", level, columns.size(), boxes.size(), all.size(),
                        boxes.entrySet().stream().map(e -> e.getKey() + " " + boxesSaid(e.getValue())).toList());
                String chief = keepForeman;
                if (chief != null) {
                    for (LevelPlan.Builder b : all) {
                        sayAssignment(chief, b.name(), level, boxes.getOrDefault(b.name(), List.of()));
                    }
                }
            } else {
                Map<String, List<Box>> boxes = new HashMap<>(plan.boxes());
                Set<String> given = new HashSet<>();
                List<Map.Entry<String, List<Box>>> orphans = new ArrayList<>();
                // Off the crew - fighting, fed, gone: its rectangles to someone else.
                for (String holder : List.copyOf(boxes.keySet())) {
                    if (!names.contains(holder)) {
                        orphans.add(Map.entry(holder, boxes.remove(holder)));
                        LOG.info("[Keep] {} is off the crew - its {} to someone else", holder, boxesSaid(orphans.getLast().getValue()));
                    }
                }
                // Done: logged once, and free for what times out.
                for (Map.Entry<String, List<Box>> e : List.copyOf(boxes.entrySet())) {
                    if (e.getValue().stream().noneMatch(hasWork) && keepDue.remove(e.getKey()) != null) {
                        LOG.info("[Keep] {} done with {} at y={}", e.getKey(), boxesSaid(e.getValue()), level);
                    }
                }
                // Timed out: given to someone else.
                for (Map.Entry<String, List<Box>> e : List.copyOf(boxes.entrySet())) {
                    Long due = keepDue.get(e.getKey());
                    if (due != null && now > due && e.getValue().stream().anyMatch(hasWork)) {
                        orphans.add(Map.entry(e.getKey(), boxes.remove(e.getKey())));
                        keepDue.remove(e.getKey());
                    }
                }
                Map<String, Position> at = all.stream().collect(Collectors.toMap(LevelPlan.Builder::name, LevelPlan.Builder::at));
                for (Map.Entry<String, List<Box>> orphan : orphans) {
                    List<Box> work = orphan.getValue().stream().filter(hasWork).toList();
                    if (work.isEmpty()) {
                        continue;
                    }
                    Box first = work.getFirst();
                    double cx = (first.minX() + first.maxX()) / 2.0 + 0.5, cz = (first.minZ() + first.maxZ()) / 2.0 + 0.5;
                    // The nearest builder with nothing left to do, not the one it's taken from.
                    Optional<String> taker = all.stream().map(LevelPlan.Builder::name)
                            .filter(n -> !n.equals(orphan.getKey()) && !given.contains(n)
                                    && boxes.getOrDefault(n, List.of()).stream().noneMatch(hasWork))
                            .min(Comparator.comparingDouble(n -> Math.hypot(at.get(n).x() - cx, at.get(n).z() - cz)));
                    String to = taker.orElse(names.contains(orphan.getKey()) ? orphan.getKey() : null);
                    if (to == null) {
                        // Nobody free, and its holder gone: the nearest of the crew takes it on as well.
                        to = all.stream().map(LevelPlan.Builder::name)
                                .min(Comparator.comparingDouble(n -> Math.hypot(at.get(n).x() - cx, at.get(n).z() - cz))).orElse(null);
                    }
                    if (to == null) {
                        continue;
                    }
                    List<Box> mine = new ArrayList<>(boxes.getOrDefault(to, List.of()).stream().filter(hasWork).toList());
                    mine.addAll(work);
                    boxes.put(to, List.copyOf(mine));
                    keepDue.put(to, now + dueMillis(mine, columns, climb(at.get(to), level)));
                    keepDealt.add(to);
                    given.add(to);
                    LOG.info("[Keep] {}'s {} {} - given to {}", orphan.getKey(), boxesSaid(work),
                            names.contains(orphan.getKey()) ? "timed out" : "left behind", to);
                }
                // One with nothing to cut - given nothing yet this level (come onto it once it was dealt, a
                // squad called back to a wide level, or not there when it was), or done with its own -
                // takes half of the most that's left of one builder's, split between them (operator,
                // 2026-11-16: do a better job assigning). Live, a builder on hand but not dealt in at the
                // level's start stood the level out, and "done, waiting for the level" was 10-35% of the
                // crew's ticks while one builder had 50 digs left and two had 3.
                for (LevelPlan.Builder newcomer : all) {
                    if (given.contains(newcomer.name()) || boxes.getOrDefault(newcomer.name(), List.of()).stream().anyMatch(hasWork)) {
                        continue;
                    }
                    boolean doneWithItsOwn = keepDealt.contains(newcomer.name());
                    Optional<Map.Entry<String, List<LevelPlan.Column>>> most = boxes.entrySet().stream()
                            .map(e -> Map.entry(e.getKey(), columns.stream().filter(c -> e.getValue().stream().anyMatch(b -> c.x() >= b.minX()
                                    && c.x() <= b.maxX() && c.z() >= b.minZ() && c.z() <= b.maxZ())).toList()))
                            .max(Comparator.comparingInt(e -> e.getValue().size()));
                    if (most.isEmpty() || most.get().getValue().size() < SPLIT_AT) {
                        continue;
                    }
                    String holder = most.get().getKey();
                    int splitLevel = level;
                    Map<String, List<Box>> split = LevelPlan.plan(level, siteCeiling(site, level, calm, calmStep), most.get().getValue(),
                            List.of(new LevelPlan.Builder(holder, at.get(holder)), newcomer),
                            c -> columnTicks(c.x(), c.z(), splitLevel, calm.applyAsInt(c.x(), c.z())));
                    if (!split.containsKey(newcomer.name()) || !split.containsKey(holder)) {
                        continue;
                    }
                    boxes.put(holder, split.get(holder));
                    boxes.put(newcomer.name(), split.get(newcomer.name()));
                    keepDue.put(holder, now + dueMillis(split.get(holder), columns, climb(at.get(holder), level)));
                    keepDue.put(newcomer.name(), now + dueMillis(split.get(newcomer.name()), columns, climb(newcomer.at(), level)));
                    keepDealt.add(newcomer.name());
                    given.add(holder);
                    given.add(newcomer.name());
                    LOG.info("[Keep] {} {}: {}'s split, {} - {} keeps {}", newcomer.name(),
                            doneWithItsOwn ? "is done with its own" : "comes onto the level", holder,
                            boxesSaid(split.get(newcomer.name())), holder, boxesSaid(split.get(holder)));
                }
                plan = new KeepPlan(level, names, Map.copyOf(boxes), plan.at());
                keepPlan = plan;
                String chief = keepForeman;
                if (chief != null) {
                    for (String n : given) {
                        sayAssignment(chief, n, level, boxes.getOrDefault(n, List.of()));
                    }
                }
            }
        }
        Map<String, Order> orders = new HashMap<>();
        TreeFarm grove = keepGrove;
        for (SoldierStatus h : hands) {
            if (!inCrew.contains(h.name())) {
                continue;
            }
            // The grove's ground to clear, while it has none: dug open to a sapling's headroom - logs and
            // leaves alike, a neighbour's canopy too, the highest first - not gathered from, which takes
            // the logs and leaves the leaves over it (operator, 2026-11-17: the lumberjack might have to
            // clear existing wood, which might be what's blocking the sky).
            BlockPos toClear = keepGroveToClear;
            if (keepParty.cutters().contains(h.name()) && grove == null && toClear != null) {
                // To the top of the tallest crown over the plot, not a sapling's headroom only: a trunk
                // cut through at seven over the ground leaves its top standing, and its leaves.
                int crown = toClear.y() + TreeFarm.HEADROOM;
                for (int x = toClear.x() - TREE_FARM_RADIUS; x <= toClear.x() + TREE_FARM_RADIUS; x++) {
                    for (int z = toClear.z() - TREE_FARM_RADIUS; z <= toClear.z() + TREE_FARM_RADIUS; z++) {
                        for (int y = toClear.y() + TREE_TALLEST; y > crown; y--) {
                            if (world.isLog(x, y, z) || world.leaves(x, y, z).isPresent()) {
                                crown = y;
                                break;
                            }
                        }
                    }
                }
                orders.put(h.name(), new Order.Dig(new Box(toClear.x() - TREE_FARM_RADIUS, toClear.y(), toClear.z() - TREE_FARM_RADIUS,
                        toClear.x() + TREE_FARM_RADIUS, crown, toClear.z() + TREE_FARM_RADIUS), except));
                continue;
            }
            // A tree at a time, felled whole as the level is dug - highest first, steps put up the trunk to
            // reach, every log and its crown - then the steps dug out, so nothing's left to stunt what grows
            // there next (operator, 2026-11-17: gather all the wood on a tree; remove the scaffolding you
            // added). Live, cutters took the logs in reach and walked on, the tops and the leaves left.
            if (keepParty.cutters().contains(h.name())) {
                Optional<Order> felled = fellingDig(h.name(), keepWoodAt(onKeep, grove), except);
                if (felled.isPresent()) {
                    orders.put(h.name(), felled.get());
                    continue;
                }
            }
            if (keepParty.cutters().contains(h.name())) {
                Position trees = keepWoodAt(onKeep, grove);
                int tx = (int) Math.floor(trees.x()), ty = (int) Math.floor(trees.y()), tz = (int) Math.floor(trees.z());
                orders.put(h.name(), new Order.Gather(Resource.WOOD, new Box(tx - 4, ty - 4, tz - 4, tx + 4, ty + 4, tz + 4), PlatoonSergeant.WOOD_LOAD));
                continue;
            }
            if (keepParty.planter().filter(h.name()::equals).isPresent() && grove != null) {
                // Its forester stands where its next sapling goes; between saplings it waits at the grove.
                orders.put(h.name(), new Order.MoveTo(grove.centre().x() + 0.5, grove.centre().y(), grove.centre().z() + 0.5,
                        TREE_FARM_RADIUS, 6, Risk.LOW, false));
                continue;
            }
            // The first of its rectangles with ground still in it: one done, the next.
            KeepPlan dealt = plan;
            List<Box> mine = plan.boxes().getOrDefault(h.name(), List.of()).stream()
                    .filter(b -> keepColumnsLeft(dealt.level(), b, top, stepTop)).toList();
            if (!mine.isEmpty()) {
                orders.put(h.name(), new Order.Dig(mine.getFirst(), except));
            } else {
                // No rectangle at this level - less work than builders, or it's in a fight: it waits
                // where it stands, on the site, for the next plan. Left to "nothing else to do", live,
                // builders walked 60 blocks to the camp's upkeep and were there when the level moved.
                orders.put(h.name(), new Order.MoveTo(h.position().x(), h.position().y(), h.position().z(), 3, 3, Risk.LOW, false));
            }
        }
        return orders;
    }

    /**
     * The set-aside ore {@code hand} is to take: at or over {@code level}, its drop got with
     * {@code hand}'s pickaxe, and {@code hand} the nearest of the keep's hands - every squad's - who
     * could get it. Empty for one with none.
     */
    private Optional<Cell> oreErrand(String hand, int level) {
        Map<String, Position> on = new HashMap<>();
        keepHandsAt.values().forEach(on::putAll);
        if (!on.containsKey(hand) || level == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        Set<Cell> aside = new HashSet<>();
        squadStates.values().forEach(s -> aside.addAll(s.keepOreAside));
        keepOreBeyondHolder.values().forEach(aside::addAll);
        // Only ore still there: one taken, or gone with its pillar, isn't an errand. Live (field427), a cell at
        // y=87 over the level at y=62 was dealt to Alice, Gus and Bob in turn 310 times in five minutes, each
        // finding nothing to cut there, and the keep's crew stood.
        return aside.stream().filter(c -> c.y() >= level - 1 && world.isOre(c.x(), c.y(), c.z())).filter(c -> {
                    Optional<Hardness> hardness = world.hardness(c.x(), c.y(), c.z());
                    java.util.function.Predicate<String> can = n -> hardness.map(hd -> hd.drops(pickaxeTiers.getOrDefault(n, Hardness.HAND))).orElse(false);
                    if (!can.test(hand)) {
                        return false;
                    }
                    // The nearest who can.
                    return on.entrySet().stream().filter(e -> can.test(e.getKey()))
                            .min(Comparator.comparingDouble(e -> Math.hypot(e.getValue().x() - c.x(), e.getValue().z() - c.z())))
                            .map(e -> e.getKey().equals(hand)).orElse(false);
                })
                .min(Comparator.comparingDouble(c -> Math.hypot(on.get(hand).x() - c.x(), on.get(hand).z() - c.z())));
    }

    /**
     * When each builder's rectangles on the level under way are due done, and only while they're not:
     * a builder dealt in with none here is done - written under {@link #keepPlanLock}, read by ticks.
     */
    private final Map<String, Long> keepDue = new ConcurrentHashMap<>();
    /** The site's columns' tops in the keep's last scan, for a rise's line: the keep's squad loop writes and reads. */
    private volatile Map<Long, Integer> lastScanTops = Map.of();
    /** Each of the ground's columns' top as last seen loaded, by packed x and z - squad loops, under its own lock. */
    private final Map<Long, Integer> keepColumnTops = new HashMap<>();
    /** Ore in the dig whose rectangle's holder can't take it, by squad as each last saw it: the ore errand's too. */
    private final Map<String, Set<Cell>> keepOreBeyondHolder = new ConcurrentHashMap<>();
    /** The builders given work on the level under way, done or not - guarded by {@link #keepPlanLock}. */
    private final Set<String> keepDealt = new HashSet<>();

    /** The time a builder is given for a block of its rectangles, on top of {@link #DUE_SLACK_MILLIS}: a walk, a break, a pickup at a good pace. */
    static final long DUE_MILLIS_A_BLOCK = 6_000;
    /** The time a builder is given to get to its rectangles at all. */
    static final long DUE_SLACK_MILLIS = 60_000;

    /**
     * Time a rectangle's due is given for each block its builder climbs to it, on steps it puts up -
     * a step a few seconds. Live (field310), a hilltop's last cells 13 over the builders timed out on
     * the climb, were handed on, and the next builder climbed from the bottom again.
     */
    static final long DUE_MILLIS_A_CLIMB = 5_000;

    /** Whether {@code box} has ground, a step or a tree left at or over {@code level}. */

    /** The foreman's word to {@code builder} on the net: its rectangles at {@code level}, or to wait. */
    private void sayAssignment(String chief, String builder, int level, List<Box> boxes) {
        say(chief, builder, boxes.isEmpty() ? "KEEP WAIT " + level
                : "KEEP DIG " + level + " " + boxes.stream().map(r -> r.minX() + " " + r.minZ() + " " + r.maxX() + " " + r.maxZ())
                .collect(Collectors.joining(" ")));
    }

    /** The foreman's plan for a level: who's on it, their rectangles, and when it was made. */
    private record KeepPlan(int level, Set<String> crew, Map<String, List<Box>> boxes, long at) {
    }

    /**
     * The keep's wood party, as the platoon sergeant decides it ({@link PlatoonSergeant#woodParty}):
     * fed what the builders carry, the stockpile's logs, the trees the keep's squads have seen and
     * the grove's state. A planting that fails pauses the grove {@link #PLANTER_RETRY_MILLIS}, not
     * the planter - what failed it fails the next builder too: live, five took the job in turn within
     * a minute. Changes are logged and said on the net. Guarded by {@link #keepPlanLock}.
     */
    private void woodParty(List<String> onKeep, List<LevelPlan.Builder> onHand, long now) {
        Optional<String> plantingFailed = keepParty.planter().map(foresters::get).flatMap(Forester::failure);
        if (plantingFailed.isPresent()) {
            LOG.info("[Keep] the grove waits {} min: {}", PLANTER_RETRY_MILLIS / 60_000, plantingFailed.get());
            keepGroveBarredUntil = now + PLANTER_RETRY_MILLIS;
        }
        List<PlatoonSergeant.Hand> crew = onHand.stream().map(b -> new PlatoonSergeant.Hand(b.name(),
                keepLogsCarried.getOrDefault(b.name(), 0), keepSaplingsCarried.getOrDefault(b.name(), 0))).toList();
        TreeFarm grove = keepGrove;
        Supply logsMeasured = logsSupply;
        PlatoonSergeant.Wood wood = new PlatoonSergeant.Wood(chainOfCommand.stockpile().map(Stockpile::logs).orElse(0),
                // Or a tree the world model holds near the depot: live (field389), the keep's squads came up
                // from a night in the mine having seen no trees, and nobody cut at 3 logs.
                treesSeenLately(onKeep.stream().flatMap(u -> chainOfCommand.resourcesSeen(u).stream()).anyMatch(r -> r.resource() == Resource.WOOD)
                        || knownTree(now).isPresent(), now)
                        || grove == null && keepGroveToClear != null, // the grove's ground to clear is trees seen
                grove != null && !Forester.planted(world, grove), now < keepGroveBarredUntil,
                logsMeasured == null ? 0 : logsMeasured.reorderPoint(), logsMeasured == null ? 0 : logsMeasured.objective());
        PlatoonSergeant.WoodParty was = keepParty;
        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(crew, keepForeman, was, wood);
        if (!party.cutters().equals(was.cutters()) || !party.planter().equals(was.planter())) {
            int logs = crew.stream().mapToInt(PlatoonSergeant.Hand::logs).sum() + wood.stockpileLogs();
            LOG.info("[Keep] the wood party: {} cut(s) trees for the depot ({} logs between the builders and the stockpile, {} wanted){}",
                    party.cutters().isEmpty() ? "nobody" : party.cutters(), logs, (int) wood.back(),
                    party.planter().map(n -> ", " + n + " plants the keep's grove").orElse(""));
            String chief = keepForeman;
            if (chief != null) {
                party.cutters().stream().filter(n -> !was.cutters().contains(n)).forEach(n -> say(chief, n, "KEEP WOOD " + PlatoonSergeant.WOOD_LOAD));
                party.planter().filter(n -> !was.planter().equals(Optional.of(n)))
                        .ifPresent(n -> say(chief, n, "KEEP PLANT " + grove.centre().x() + " " + grove.centre().z()));
            }
        }
        keepParty = party;
        // Off the party, its tree is let go of, its patience with it too: live (field399), Liam's tree was
        // kept through his night down the mine, and given up as out of reach at dawn - its three minutes run out while he was 70 blocks under it.
        felling.keySet().retainAll(party.cutters());
        fellingLeft.keySet().retainAll(party.cutters());
        fellingSince.keySet().retainAll(party.cutters());
        // Nobody cutting under the reserve says why, once a minute: live (field390), a day went by at 3-19
        // logs with the party never out and no line to say what held it.
        int logsNow = crew.stream().mapToInt(PlatoonSergeant.Hand::logs).sum() + wood.stockpileLogs();
        if (party.cutters().isEmpty() && logsNow < wood.out() && now - keepWoodWhyAt >= WOOD_WHY_MILLIS) {
            keepWoodWhyAt = now;
            LOG.warn("[Keep] nobody cuts at {} logs, under the reserve of {}: crew {}, trees seen lately {}, a known tree {}, the grove's ground to clear {}",
                    logsNow, (int) wood.out(), crew.size(), now - keepTreesSeenAt <= TREES_SEEN_MILLIS,
                    knownTree(now).map(Position::toString).orElse("none within " + KEEP_WOOD_REACH), keepGroveToClear);
        }
    }

    /** When the keep last said why nobody cuts - squad loops only. */
    private volatile long keepWoodWhyAt = Long.MIN_VALUE / 2;
    static final long WOOD_WHY_MILLIS = 60_000;

    /** When a keep squad last saw trees - the squad loop writes and reads it. */
    private volatile long keepTreesSeenAt = Long.MIN_VALUE / 2;
    /** How long trees once seen are counted seen: a survey that misses them doesn't stand the wood party down. */
    static final long TREES_SEEN_MILLIS = 120_000;

    /**
     * Whether trees are seen now, or were within {@link #TREES_SEEN_MILLIS}: live (field374), Ivy was put
     * on the wood party, off it 21 s later ("nobody cut(s) trees" at 5 logs) and on it again 30 s after,
     * each a survey that did or didn't see the trees, her rectangle handed back and forth with Bob.
     */
    private boolean treesSeenLately(boolean seenNow, long now) {
        if (seenNow) {
            keepTreesSeenAt = now;
        }
        return now - keepTreesSeenAt <= TREES_SEEN_MILLIS;
    }

    /** Whether {@code name} is off the dig in the keep's wood party. */
    private boolean inWoodParty(String name) {
        return keepParty.has(name);
    }

    /**
     * The keep's smelter (backlog/common-stockpile.md, the ore's chain of hands: the smelter): one
     * builder off the dig while the surface chests hold raw ore - drawn from whichever holds it,
     * smelted at the main depot's furnace, the ingots put back - so armour needs a table and a chest's
     * ingots, not a furnace and fuel where each soldier stands. Live (field302), the farm squad walked
     * between a table with no furnace by it, a furnace 22 blocks off and a table 50 off, for copper
     * leggings never made, with 74 raw copper in chests. Of the crew, one with no rectangle, nearest
     * the main depot; kept while there's ore to smelt or it carries some.
     */
    private String smelter(List<LevelPlan.Builder> onHand, KeepPlan plan) {
        Workshop depot = keepWorkshop;
        int raw = chainOfCommand.stores().map(s -> s.rawIron() + s.rawCopper()).orElse(0);
        String was = keepSmelter;
        boolean onCrew = was != null && onHand.stream().anyMatch(b -> b.name().equals(was));
        if (onCrew && (raw > 0 || smelterCarries.contains(was))) {
            return was;
        }
        if (depot == null || raw < SMELT_BATCH) {
            if (was != null) {
                LOG.info("[Keep] {} is done smelting - back on the dig", was);
            }
            return null;
        }
        Optional<String> chosen = onHand.stream().filter(b -> plan == null || !plan.boxes().containsKey(b.name()))
                .filter(b -> !inWoodParty(b.name()) && !b.name().equals(keepRoadCutter))
                .min(Comparator.comparingDouble(b -> Math.hypot(b.at().x() - depot.stand().x(), b.at().z() - depot.stand().z())))
                .map(LevelPlan.Builder::name);
        chosen.filter(n -> !n.equals(was)).ifPresent(n -> LOG.info("[Keep] {} is the smelter: {} raw ore in the chests", n, raw));
        return chosen.orElse(null);
    }

    /** How far round the main depot furnaces are looked for: the keep, its mine head and its depots. */
    static final int FURNACE_SCAN = 32;
    /** The furnaces found round the main depot, and when it was looked: once a sweep's span. */
    private volatile Set<BlockPos> furnacesScanned = Set.of();
    private volatile long furnacesScannedAt = Long.MIN_VALUE / 2;

    /** The furnaces standing within {@link #FURNACE_SCAN} of the main depot's stand, 8 up or down, looked for at most once a sweep's span. */
    private Set<BlockPos> furnacesNearDepot(long now) {
        Workshop depot = keepWorkshop;
        if (depot == null || now - furnacesScannedAt < FURNACE_SWEEP_MILLIS) {
            return furnacesScanned;
        }
        Set<BlockPos> found = new HashSet<>();
        BlockPos at = depot.stand();
        for (int x = at.x() - FURNACE_SCAN; x <= at.x() + FURNACE_SCAN; x++) {
            for (int z = at.z() - FURNACE_SCAN; z <= at.z() + FURNACE_SCAN; z++) {
                if (!world.isLoaded(x, z)) {
                    continue;
                }
                for (int y = at.y() - 8; y <= at.y() + 8; y++) {
                    if (world.workstation(x, y, z).equals(Optional.of(Workstation.FURNACE))) {
                        found.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        furnacesScanned = Set.copyOf(found);
        furnacesScannedAt = now;
        return furnacesScanned;
    }

    /** When each furnace was last emptied - the smelter's tick writes and reads. */
    private final Map<BlockPos, Long> furnacesSwept = new ConcurrentHashMap<>();
    /** How often a furnace is emptied: a smelt left in one stands no longer. */
    static final long FURNACE_SWEEP_MILLIS = 10 * 60_000;

    /**
     * The nearest furnace not emptied in {@link #FURNACE_SWEEP_MILLIS}: the depots', the camp's, the
     * squads' workshops', any a soldier has used, and any standing within {@link #FURNACE_SCAN} of the
     * main depot - whoever put it there. Live, 3 iron ingots stood in a furnace by the keep's mine head
     * that no run's log named, while three soldiers wore no armour.
     */
    private Optional<Position> furnaceDue(Position from) {
        long now = clockwork.millis();
        List<Workshop> shops = new ArrayList<>(keepOutposts);
        Optional.ofNullable(keepWorkshop).ifPresent(shops::add);
        Optional.ofNullable(campWorkshop).ifPresent(shops::add);
        shops.addAll(workshops.values());
        Set<BlockPos> known = new HashSet<>(shops.stream().map(Workshop::furnace).toList());
        known.addAll(furnaces.keySet());
        known.addAll(furnacesNearDepot(now));
        return known.stream()
                .filter(f -> world.workstation(f.x(), f.y(), f.z()).equals(Optional.of(Workstation.FURNACE)))
                .filter(f -> now - furnacesSwept.getOrDefault(f, Long.MIN_VALUE / 2) >= FURNACE_SWEEP_MILLIS)
                .map(f -> new Position(f.x() + 0.5, f.y(), f.z() + 0.5))
                .min(Comparator.comparingDouble(p -> Math.hypot(p.x() - from.x(), p.z() - from.z())));
    }

    /** The keep's smelter, if one is detailed - squad loops write, ticks read. */
    private volatile String keepSmelter;
    /** Smelters carrying raw ore or ingots still to put in - their ticks write, squad loops read. */
    private final Set<String> smelterCarries = ConcurrentHashMap.newKeySet();
    /** Raw ore in the chests worth a smelter: a chestplate's copper. */
    static final int SMELT_BATCH = 8;
    /** Raw ore a smelter draws at a time: a stack, one furnace load. */
    static final int SMELT_DRAW = 64;

    /**
     * The cells of the grove's grid within {@link #TREE_FARM_RADIUS} of {@code centre} a sapling could go
     * in once what grows over them is cut: soil under, no water, near the centre's height.
     */
    private int groveGround(BlockPos centre) {
        int cells = 0;
        for (int x = centre.x() - TREE_FARM_RADIUS; x <= centre.x() + TREE_FARM_RADIUS; x++) {
            for (int z = centre.z() - TREE_FARM_RADIUS; z <= centre.z() + TREE_FARM_RADIUS; z++) {
                if (!TreeFarm.onGrid(x, z)) {
                    continue;
                }
                for (int y = centre.y() + 4; y >= centre.y() - 4; y--) {
                    if (world.isWater(x, y, z)) {
                        break;
                    }
                    if (world.isSoil(x, y - 1, z) && !world.isFarmland(x, y - 1, z) && !world.isSolid(x, y, z)) {
                        cells++;
                        break;
                    }
                    if (world.isSolid(x, y - 1, z) && !world.isLog(x, y - 1, z) && world.leaves(x, y - 1, z).isEmpty()) {
                        break; // ground that isn't soil
                    }
                }
            }
        }
        return cells;
    }

    /** The tree each keep cutter is felling: its logs as first found - the dig is its box until they're down and its steps out. */
    private final Map<String, Set<BlockPos>> felling = new ConcurrentHashMap<>();
    /** How far across from its first log a tree's logs are taken in: an oak's or a spruce's spread. */
    static final int TREE_ACROSS = 4;
    /** Logs left on each cutter's tree when last one came off, and when - its patience with it. */
    private final Map<String, Integer> fellingLeft = new ConcurrentHashMap<>();
    private final Map<String, Long> fellingSince = new ConcurrentHashMap<>();
    /** Trees let go as out of reach: never handed out again this run. */
    private final Set<BlockPos> treesLeft = ConcurrentHashMap.newKeySet();
    /** What takes a cutter's hands off its tree for a while without its tree being the trouble. */
    private static final Set<Hands.Routine> ERRANDS = java.util.EnumSet.of(Hands.Routine.SUPPLY_RUN, Hands.Routine.TORCHMAKER,
            Hands.Routine.RATIONS, Hands.Routine.SEALER, Hands.Routine.HUNTER, Hands.Routine.ARMOURER, Hands.Routine.DEPOSITOR,
            Hands.Routine.HANDOVER);
    /** How long a cutter works a tree with no log coming off it before it's let go. */
    static final long TREE_PATIENCE_MILLIS = 180_000;
    /** The stump each keep cutter replants before its next tree - squad loops write, the planting map reads. */
    private final Map<String, BlockPos> keepStumps = new ConcurrentHashMap<>();
    private final Map<String, Long> keepStumpSince = new ConcurrentHashMap<>();
    /** Where each keep cutter's felled logs fell, and when it went to pick them up - squad loops only. */
    private final Map<String, BlockPos> keepCollect = new ConcurrentHashMap<>();
    private final Map<String, Long> keepCollectSince = new ConcurrentHashMap<>();
    /** How long a cutter stands where its tree's logs fell, its scavenger taking them up. */
    static final long COLLECT_MILLIS = 15_000;
    /** How far down a felled trunk's column its logs are looked for. */
    static final int FALL_LOOK_DOWN = 24;
    /** How far down its trunk's column a felled tree's ground is looked for, to replant on. */
    static final int STUMP_LOOK_DOWN = 4;
    /** How long a cutter tries to replant a stump - a sapling from the leaves it dug, or broken for - before it goes on. */
    static final long STUMP_PLANT_MILLIS = 90_000;
    /** The most logs taken as one tree. */
    static final int TREE_LOGS = 96;
    /** Leaves over a tree's top log, dug with it: a spruce's or an oak's crown. */
    static final int TREE_CROWN = 3;
    /** How far round where it's sent a cutter looks for its next tree. */
    static final int TREE_LOOK = 8;
    /** The tallest a tree is looked for over the ground: a giant spruce. */
    static final int TREE_TALLEST = 32;

    /**
     * {@code cutter}'s dig of the tree it's felling, or the next tree nearest {@code at}: the box round
     * its logs and crown, only logs and leaves in it dug - the ground and anything else kept - and its
     * own steps kept while a log stands, dug out once none does. Empty with no tree near.
     */
    private Optional<Order> fellingDig(String cutter, Position at, Set<Cell> except) {
        // The logs it cut picked up first: they fall to the ground under the trunk, and a cutter gone on to
        // its next tree leaves them lying - live (field385), Liam felled three trees and carried 2 logs
        // before and 2 after. It stands where they fell while its scavenger takes them up.
        BlockPos fell = keepCollect.get(cutter);
        if (fell != null) {
            if (clockwork.millis() - keepCollectSince.getOrDefault(cutter, 0L) < COLLECT_MILLIS) {
                return Optional.of(new Order.MoveTo(fell.x() + 0.5, fell.y(), fell.z() + 0.5, 2, 3, Risk.LOW));
            }
            keepCollect.remove(cutter);
            keepCollectSince.remove(cutter);
        }
        // Its stump replanted before the next tree: it stands on it, its forester plants it (keepStumps).
        BlockPos stump = keepStumps.get(cutter);
        if (stump != null) {
            long since = keepStumpSince.getOrDefault(cutter, clockwork.millis());
            if (TreeFarm.planted(world, stump) || clockwork.millis() - since > STUMP_PLANT_MILLIS) {
                if (!TreeFarm.planted(world, stump)) {
                    LOG.warn("[Keep] {} couldn't replant the stump at {} in {} s - left for the leaves' saplings", cutter, stump,
                            STUMP_PLANT_MILLIS / 1000);
                }
                keepStumps.remove(cutter);
                keepStumpSince.remove(cutter);
            } else {
                return Optional.of(new Order.MoveTo(stump.x() + 0.5, stump.y(), stump.z() + 0.5, 2, 3, Risk.LOW));
            }
        }
        Set<BlockPos> tree = felling.get(cutter);
        // A tree nobody can reach - a trunk left floating over the mine's shaft - is let go once no log has
        // come off it a while, and never handed out again: live (field377-379), Jack and then Dana were each
        // sent to the logs at y=76-78 over the shaft at the mine's head, air under them to y=70, and stood.
        long now = clockwork.millis();
        if (tree != null) {
            int left = (int) tree.stream().filter(c -> world.isLog(c.x(), c.y(), c.z())).count();
            Integer was = fellingLeft.get(cutter);
            // Its patience runs only while it's at the tree's work, not on an errand: live (field388), Eli
            // made tools and fetched iron from the stockpile three minutes, and let go as out of reach a tree beside the workshop he'd never gone to.
            Hands.Routine errand = handsHeld.get(cutter);
            if (errand != null && ERRANDS.contains(errand)) {
                fellingSince.put(cutter, now);
            }
            if (was == null || left < was) {
                fellingLeft.put(cutter, left);
                fellingSince.put(cutter, now);
            } else if (left > 0 && now - fellingSince.getOrDefault(cutter, now) > TREE_PATIENCE_MILLIS) {
                LOG.warn("[Keep] {} leaves the tree at {}: no log off it in {} s - out of reach", cutter,
                        tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow(), TREE_PATIENCE_MILLIS / 1000);
                treesLeft.addAll(tree);
                felling.remove(cutter);
                fellingLeft.remove(cutter);
                tree = null;
            }
        }
        boolean standing = tree != null && tree.stream().anyMatch(c -> world.isLog(c.x(), c.y(), c.z()));
        Box held = tree == null ? null : treeBox(tree);
        boolean steps = held != null && !standing && scaffolding.stream().anyMatch(c -> held.contains(c.x(), c.y(), c.z())
                && world.isSolid(c.x(), c.y(), c.z()));
        Box box = held;
        if (!standing && !steps) {
            if (tree != null) {
                BlockPos lowest = tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow();
                LOG.info("[Keep] {} has felled the tree at {} and dug out its steps", cutter, lowest);
                felling.remove(cutter);
                // The ground its trunk stood on, down its column: live (field380), Bob's tree's lowest log
                // stood over air, and its stump wasn't replanted.
                BlockPos foot = lowest;
                for (int down = 0; down < STUMP_LOOK_DOWN && !world.isSolid(foot.x(), foot.y() - 1, foot.z()); down++) {
                    foot = foot.offset(0, -1, 0);
                }
                // Where its logs fell: the ground under the trunk, however far down.
                BlockPos ground = foot;
                for (int down = 0; down < FALL_LOOK_DOWN && !world.isSolid(ground.x(), ground.y() - 1, ground.z()); down++) {
                    ground = ground.offset(0, -1, 0);
                }
                keepCollect.put(cutter, ground);
                keepCollectSince.put(cutter, clockwork.millis());
                // Replanted where it stood, its headroom dug clear with its crown (operator, 2026-11-17).
                if (world.isSoil(foot.x(), foot.y() - 1, foot.z()) && !world.isSolid(foot.x(), foot.y(), foot.z())) {
                    keepStumps.put(cutter, foot);
                    keepStumpSince.put(cutter, clockwork.millis());
                    return Optional.of(new Order.MoveTo(foot.x() + 0.5, foot.y(), foot.z() + 0.5, 2, 3, Risk.LOW));
                }
            }
            // A tree of its own: never one another cutter is felling (work in parallel - nobody on another's
            // share). Live (field380), Liam was given the tree Bob had taken 13 s before.
            Set<BlockPos> others = new HashSet<>();
            felling.forEach((other, logs) -> {
                if (!other.equals(cutter)) {
                    others.addAll(logs);
                }
            });
            tree = nearestLog(at, others).map(this::treeAt).orElse(null);
            if (tree == null) {
                felling.remove(cutter);
                return Optional.empty();
            }
            felling.put(cutter, tree);
            fellingLeft.put(cutter, tree.size());
            fellingSince.put(cutter, now);
            box = treeBox(tree);
            standing = true;
            LOG.info("[Keep] {} fells the tree at {}: {} log(s), dug as {}", cutter, tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow(),
                    tree.size(), box);
        }
        Set<Cell> keep = new HashSet<>(except);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean wood = world.isLog(x, y, z) || world.leaves(x, y, z).isPresent();
                    boolean step = scaffolding.contains(new BlockPos(x, y, z));
                    if (world.isSolid(x, y, z) && !wood && (standing || !step)) {
                        keep.add(new Cell(x, y, z));
                    } else if (step && !standing) {
                        keep.remove(new Cell(x, y, z));
                    }
                }
            }
        }
        return Optional.of(new Order.Dig(box, keep));
    }

    /** The log nearest {@code at}, within {@link #TREE_LOOK} across and up or down. */
    private Optional<BlockPos> nearestLog(Position at, Set<BlockPos> held) {
        int ax = (int) Math.floor(at.x()), ay = (int) Math.floor(at.y()), az = (int) Math.floor(at.z());
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = ax - TREE_LOOK; x <= ax + TREE_LOOK; x++) {
            for (int z = az - TREE_LOOK; z <= az + TREE_LOOK; z++) {
                for (int y = ay - TREE_LOOK; y <= ay + TREE_LOOK; y++) {
                    double d = Math.hypot(x - ax, z - az) + Math.abs(y - ay) * 0.5;
                    BlockPos cell = new BlockPos(x, y, z);
                    if (d < bestDistance && world.isLog(x, y, z) && !treesLeft.contains(cell) && !held.contains(cell)) {
                        best = new BlockPos(x, y, z);
                        bestDistance = d;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every log joined to {@code log}, within {@link #TREE_ACROSS} across of it: one tree. */
    private Set<BlockPos> treeAt(BlockPos log) {
        Set<BlockPos> tree = new HashSet<>(List.of(log));
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(log));
        while (!open.isEmpty() && tree.size() < TREE_LOGS) {
            BlockPos c = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (Math.abs(n.x() - log.x()) <= TREE_ACROSS && Math.abs(n.z() - log.z()) <= TREE_ACROSS
                                && world.isLog(n.x(), n.y(), n.z()) && tree.add(n)) {
                            open.add(n);
                        }
                    }
                }
            }
        }
        return Set.copyOf(tree);
    }

    /** The ground the wood party clears for the keep's grove, while it has none: its trees cut first. */
    private volatile BlockPos keepGroveToClear;
    /** When the keep's grove was last sought. */
    private volatile long keepGroveSoughtAt;
    /** How far from the keep's depot its wood party goes for trees: the ground the keep's squads work. */
    static final int KEEP_WOOD_REACH = 64;
    /** How often a grove not yet sited is sought again, its ground cleared meanwhile. */
    static final long GROVE_RESITE_MILLIS = 120_000;

    /**
     * Where the wood party cuts: the ground being cleared for the keep's grove, while it has none; else
     * the trees nearest the depot a keep squad last saw, else round the grove, else the depot.
     */
    private Position keepWoodAt(List<String> onKeep, TreeFarm grove) {
        BlockPos clearing = keepGroveToClear;
        if (grove == null && clearing != null) {
            return new Position(clearing.x() + 0.5, clearing.y(), clearing.z() + 0.5);
        }
        // Trees on the surface near the depot only: live (field377), Ivy and Liam were sent 120 blocks off to
        // "trees" at y=-28 - a mineshaft's timbers a mining squad's survey saw - never a surface soldier
        // routed down into a cave (backlog/long-routes.md).
        Position depot = chainOfCommand.flatten().depot();
        return onKeep.stream().flatMap(u -> chainOfCommand.resourcesSeen(u).stream())
                .filter(site -> site.resource() == Resource.WOOD).map(ResourceSite::location)
                .filter(l -> !buried(l) && Math.hypot(l.x() - depot.x(), l.z() - depot.z()) <= KEEP_WOOD_REACH)
                .min(Comparator.comparingDouble(l -> Math.hypot(l.x() - chainOfCommand.flatten().depot().x(), l.z() - chainOfCommand.flatten().depot().z())))
                .or(() -> knownTree(clockwork.millis()))
                .orElseGet(() -> grove != null ? new Position(grove.centre().x() + 0.5, grove.centre().y(), grove.centre().z() + 0.5)
                        : chainOfCommand.flatten().depot());
    }

    /**
     * The tree nearest the depot the world model holds, within {@link #KEEP_WOOD_REACH} across: a column
     * whose top is a log, under leaves or open sky - on the surface, never a mineshaft's timbers. Looked
     * for again every {@link #KNOWN_TREE_MILLIS}.
     */
    private Optional<Position> knownTree(long now) {
        if (now - keepKnownTreeAt >= KNOWN_TREE_MILLIS) {
            keepKnownTreeAt = now;
            Position depot = chainOfCommand.flatten().depot();
            int dx = (int) Math.floor(depot.x()), dy = (int) Math.floor(depot.y()), dz = (int) Math.floor(depot.z());
            Position best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = dx - KEEP_WOOD_REACH; x <= dx + KEEP_WOOD_REACH; x++) {
                for (int z = dz - KEEP_WOOD_REACH; z <= dz + KEEP_WOOD_REACH; z++) {
                    double d = Math.hypot(x - dx, z - dz);
                    if (d > KEEP_WOOD_REACH || d >= bestDistance) {
                        continue;
                    }
                    for (int y = dy + KNOWN_TREE_ABOVE; y >= dy - KNOWN_TREE_BELOW; y--) {
                        if (world.isLog(x, y, z)) {
                            if (!treesLeft.contains(new BlockPos(x, y, z))) {
                                best = new Position(x + 0.5, y, z + 0.5);
                                bestDistance = d;
                            }
                            break;
                        }
                        if (world.isSolid(x, y, z) && world.leaves(x, y, z).isEmpty()) {
                            break; // the ground: no tree on this column
                        }
                    }
                }
            }
            keepKnownTree = best;
        }
        return Optional.ofNullable(keepKnownTree);
    }

    /** The tree {@link #knownTree} last found, and when it looked - squad loops only. */
    private volatile Position keepKnownTree;
    private volatile long keepKnownTreeAt = Long.MIN_VALUE / 2;
    static final long KNOWN_TREE_MILLIS = 10_000;
    /** How far over and under the depot's stand a known tree's column is looked down. */
    static final int KNOWN_TREE_ABOVE = 24;
    static final int KNOWN_TREE_BELOW = 8;

    /**
     * The keep's grove: a {@link TreeFarm} near its depot, on the nearest ground with room for one
     * - a ring round the depot's stand searched outward, west first, away from the keep - clear of
     * the slab and its borrow pits, the depot, the mine's head, the farm and the camp. Sited once;
     * what grows in it is the grove working. Empty with nowhere near to plant.
     */
    private Optional<TreeFarm> siteKeepGrove(Workshop depot) {
        List<Box> avoid = new ArrayList<>();
        Flatten site = chainOfCommand.flatten();
        avoid.add(new Box(site.minX() - GROVE_KEEP_CLEAR, Integer.MIN_VALUE, site.minZ() - GROVE_KEEP_CLEAR,
                site.maxX() + GROVE_KEEP_CLEAR, Integer.MAX_VALUE, site.maxZ() + GROVE_KEEP_CLEAR));
        avoid.add(around(depot.stand().x(), depot.stand().z(), Workshop.CHEST_REACH + 2));
        for (SquadState other : squadStates.values()) {
            if (other.stairs != null) {
                avoid.add(around(other.stairs.head().x(), other.stairs.head().z(), 6));
            }
            if (other.farm != null) {
                other.farm.plot().forEach(c -> avoid.add(around(c.x(), c.z(), 3)));
            }
        }
        BlockPos camp = campGround;
        if (camp != null) {
            avoid.add(around(camp.x(), camp.z(), WALL_RADIUS + 6));
        }
        int[][] ways = {{-1, 0}, {-1, -1}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 1}, {1, 0}};
        // Walked to from the depot, not climbed: live, the grove went on a hilltop past the site's
        // bench, and its planter put a pillar up the bench's outer row to reach it, ten steps at a go,
        // each of them the site's highest ground while it stood.
        Set<BlockPos> walkable = Pathfinder.reachable(world, depot.stand(), GROVE_RINGS * GROVE_RING + TREE_FARM_RADIUS + 4, FARM_REACH_BUDGET);
        TreeFarm best = null;
        int bestScore = Integer.MAX_VALUE;
        // Why each ring's site was passed over, said with "nowhere": a grove that never sites is a wood
        // supply that never grows (live, field350-365, the keep had none and nothing said why).
        Map<String, Integer> passedOver = new java.util.TreeMap<>();
        // The nearest site walked to whose trees keep it from being a grove: cleared by the wood party
        // (operator, 2026-11-17: we can clear an area for a grove as well).
        BlockPos toClear = null;
        int toClearCells = 0;
        for (int r = GROVE_RING; r <= GROVE_RINGS * GROVE_RING; r += GROVE_RING / 2) {
            for (int[] way : ways) {
                int cx = depot.stand().x() + way[0] * r, cz = depot.stand().z() + way[1] * r;
                Box plot = around(cx, cz, TREE_FARM_RADIUS + 2);
                if (avoid.stream().anyMatch(a -> overlaps(a, plot))) {
                    passedOver.merge("kept clear", 1, Integer::sum);
                    continue;
                }
                Optional<BlockPos> surface = TerrainSurveyor.surface(world, cx, cz, chainOfCommand.flatten().floor() + 60);
                Optional<TreeFarm> sited = surface.flatMap(ground -> TreeFarm.site(world, ground, TREE_FARM_RADIUS));
                String why = surface.isEmpty() ? "no ground" : sited.isEmpty() ? "no plot" : sited.get().plot().size() < GROVE_MIN_CELLS ? "too small"
                        : !(walkable.contains(sited.get().centre().offset(0, 1, 0)) || walkable.contains(sited.get().centre())) ? "not walked to"
                        : sited.get().centre().y() - depot.stand().y() > GROVE_RISE ? "over the depot" : null;
                if (why != null) {
                    passedOver.merge(why, 1, Integer::sum);
                }
                // The one whose ground could hold a grove once its trees are down: the most cells of the
                // grove's grid with soil under them and no water - not the nearest. Live (field376), the
                // site cleared lay by the farm's water, five of its cells water, and could never be one.
                if (("no plot".equals(why) || "too small".equals(why))
                        && (walkable.contains(surface.get()) || walkable.contains(surface.get().offset(0, 1, 0)))
                        && surface.get().y() - depot.stand().y() <= GROVE_RISE) {
                    int could = groveGround(surface.get());
                    if (could >= GROVE_MIN_CELLS && could > toClearCells) {
                        toClear = surface.get();
                        toClearCells = could;
                    }
                }
                Optional<TreeFarm> grove = sited.filter(g -> g.plot().size() >= GROVE_MIN_CELLS)
                        .filter(g -> walkable.contains(g.centre().offset(0, 1, 0)) || walkable.contains(g.centre()))
                        // And not over the depot: one on a hill is walked to from the depot, but
                        // climbed to from the dig - live, the planter pillared the bench's outer row
                        // from the level up to a grove 13 blocks over it. Below it, walked down to, it's
                        // as good: live (field350-364), the keep had no grove at all, every ring's
                        // ground 6 under the depot's stand at y=75 (operator, 2026-11-17: the area round -49, 69, -15 a grove).
                        .filter(g -> g.centre().y() - depot.stand().y() <= GROVE_RISE);
                if (grove.isPresent()) {
                    int score = groveScore(grove.get(), r);
                    if (score < bestScore) {
                        best = grove.get();
                        bestScore = score;
                    }
                }
            }
        }
        keepGroveToClear = best == null ? toClear : null;
        if (best == null) {
            LOG.warn("[Keep] no grove round the depot's stand at {}: the sites passed over - {}, {} cells walkable from it{}",
                    depot.stand(), passedOver, walkable.size(), toClear == null ? "" : " - the wood party clears " + toClear + " for one");
        }
        return Optional.ofNullable(best);
    }

    /** How long the keep's grove waits after a planting fails - no sapling, no leaves, no way to the cell. */
    static final long PLANTER_RETRY_MILLIS = 5 * 60_000;
    /** How far out from the depot the keep's grove is first looked for, and the steps out from there. */
    static final int GROVE_RING = 16;
    /**
     * Fewest cells the keep's grove is sited with: groves of what the ground holds, several of them, sought
     * farther out (operator, 2026-11-17) - live (field350-418), no site within 64 of the depot held 16, and the
     * keep had no grove at all. It had been 16 (operator, 2026-11-14: 16 or more trees growing).
     */
    static final int GROVE_MIN_CELLS = 8;
    /** How many {@link #GROVE_RING}s out the keep's grove is sought: 96 blocks. */
    static final int GROVE_RINGS = 6;
    /** The world's sea level: water's top at y=62, the ground beside it at 63. */
    static final int SEA_LEVEL = 63;
    /** Half the side of the square round the keep's centre its grove keeps out of: the slab and its borrow pits. */
    static final int GROVE_KEEP_CLEAR = 12;

    /** A builder's standard share of a level, the crew sized from it: one builder to a level of 24 blocks or fewer. */
    static final int BLOCKS_A_BUILDER = 24;
    /** The fewest builders the keep's level is crewed with by day: one squad, whatever its size. */
    static final int MIN_KEEP_BUILDERS = 4;
    /** How far down a column whose top was cut since the site's scan is followed live: a scan's few cuts. */
    static final int LIVE_TOP_DROP = 4;
    /** Blocks left in one builder's rectangles worth splitting with one that has none: half each, a share or two. */
    static final int SPLIT_AT = 4 * LevelPlan.MIN_SHARE;
    /** Blocks over the site's highest a rectangle reaches: a step put up over it after the plan is still dug from its top. */
    static final int CEILING_SLACK = 8;
    /** Levels under the one being dug that size its crew too: it's sized for the widest of them. */
    static final int CREW_LOOK_AHEAD = 3;

    /** How far the ground to flatten's depot spot may be from the depot standing before it's sited again, by the new ground. */
    static final int DEPOT_MOVE = 24;
    /** Each keep builder's digs on the level under way (command.LevelPace) - ticks write, squad loops read and clear. */
    private final Map<String, LevelPace.Tally> keepPace = new ConcurrentHashMap<>();
    /**
     * Each keep builder's ticks on the level under way, by what held them - the routine holding its
     * hands, or waiting with no rectangle (command.LevelPace) - its own tick writes, squad loops take.
     */
    private final Map<String, Map<String, Integer>> keepSpent = new ConcurrentHashMap<>();
    /** The keep's dig so far, level on level - guarded by {@link #keepPlanLock}. */
    private LevelPace.Run keepRun = LevelPace.Run.NONE;
    /** When the level under way was first planned, the nights since left out - guarded by {@link #keepPlanLock}. */
    private long keepLevelAt;
    /** Since when it's been dark over the level under way, or {@code Long.MIN_VALUE} by day - guarded by {@link #keepPlanLock}. */
    private long keepDarkSince = Long.MIN_VALUE;
    /** The keep's grove, once sited - squad loops write, ticks read. */
    private volatile TreeFarm keepGrove;
    /** Whether the keep's grove has been looked for since the start: once, whatever it found. */
    private volatile boolean keepGroveSought;
    /** The keep's wood party, as the platoon sergeant last decided it - guarded by {@link #keepPlanLock}, read by ticks. */
    private volatile PlatoonSergeant.WoodParty keepParty = PlatoonSergeant.WoodParty.NONE;
    /** When the keep's grove, its last planting failed, is planted again - guarded by {@link #keepPlanLock}. */
    private long keepGroveBarredUntil = Long.MIN_VALUE;
    /** The saplings each keep builder carries, at its squad's last look - squad loops. */
    private final Map<String, Integer> keepSaplingsCarried = new ConcurrentHashMap<>();
    /** The logs each keep builder carries, at its squad's last look - squad loops. */
    private final Map<String, Integer> keepLogsCarried = new ConcurrentHashMap<>();

    /** {@code from} says {@code line} to {@code to} on the net: a line a second at most from each sender; nothing said to itself. */
    private void say(String from, String to, String line) {
        if (from.equals(to)) {
            return;
        }
        // Only what's new to it, and only the latest: a line still queued to the same soldier is
        // overtaken by this one. Live, every replan queued a line to every builder, the queue never
        // emptied, the foreman chatted a line a second for an hour - the very rate the server's spam
        // count decays at - and was kicked "for spamming" every minute and a half.
        queueLine(from, to, "msg " + to + " " + line, "[Keep] " + from + " -> " + to + ": " + line);
    }

    /**
     * {@code command} queued on {@code from}'s connection, paced with everything else it sends: only
     * if new on {@code key}, overtaking a line still queued on that key. The net's key is the soldier
     * spoken to; the radio's, the station keying it - live, four squads' FRAGOs every few seconds,
     * keyed out unpaced over the radio operator's connection beside the foreman's net, had Alice
     * kicked for spamming.
     */
    private void queueLine(String from, String key, String command, String logged) {
        if (command.equals(netLastSaid.get(from + ">" + key))) {
            netOutbox.getOrDefault(from, new java.util.concurrent.ConcurrentLinkedDeque<>()).removeIf(q -> q[0].equals(key));
            return;
        }
        java.util.Deque<String[]> queue = netOutbox.computeIfAbsent(from, n -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        queue.removeIf(q -> q[0].equals(key));
        queue.add(new String[]{key, command, logged});
    }

    /**
     * A radio line (a chat command) put on the radio operator's connection, paced. Keyed by its
     * station's call - "[Alpha -> squad] FRAGO" - so a squad's next order overtakes one still queued,
     * and a repeat of the last isn't sent; traffic to the operator is keyed whole, never overtaken.
     */
    private void radio(String operator, String command) {
        int call = command.indexOf(']');
        String key = call > 0 && (command.contains("-> squad] FRAGO") || command.contains("-> PL] CANTCO"))
                ? "radio " + command.substring(0, Math.min(command.length(), call + 8))
                : "radio " + command;
        queueLine(operator, key, command, "[Radio] " + operator + ": " + command);
    }

    /** What each sender last sent each soldier on the net, by "from>to" - the squad loops'. */
    private final Map<String, String> netLastSaid = new ConcurrentHashMap<>();

    /** Sends each sender's next line, a second after its last: the server cuts off a connection that chats faster. */
    private void drainNet(long now) {
        netOutbox.forEach((from, queue) -> {
            if (queue.isEmpty() || now - netSentAt.getOrDefault(from, Long.MIN_VALUE / 2) < NET_LINE_MILLIS) {
                return;
            }
            Consumer<String> line = chatLines.get(from);
            if (line == null) {
                return; // not connected: kept till it is
            }
            String[] next = queue.poll();
            line.accept(next[1]);
            netSentAt.put(from, now);
            netLastSaid.put(from + ">" + next[0], next[1]);
            LOG.info("{}", next[2]);
        });
    }

    /**
     * A sender's line at most this often. Vanilla counts 20 for each chat or command and takes one off
     * each tick, kicking past 200: a line a second is exactly its decay, so any lag in the server's
     * ticks tips it over. One every two and a half seconds is well under, with room for a burst of ten.
     */
    static final long NET_LINE_MILLIS = 2_500;
    /** How often the net's outboxes are looked at for a line due. */
    private static final long NET_DRAIN_MILLIS = 250;
    /** How long a level's plan stands before a builder done with its rectangle has what's left shared out again. */
    static final long KEEP_REPLAN_MILLIS = 5_000;
    /** How long a level's plan stands at the most before it's made again from where the builders are. */
    static final long KEEP_PLAN_MILLIS = 30_000;
    /** The keep's foreman, by name - squad loops. */
    private volatile String keepForeman;
    /** The foreman's plan for the site's level - guarded by {@link #keepPlanLock}; read by every squad's loop. */
    private volatile KeepPlan keepPlan;
    private final Object keepPlanLock = new Object();
    /** Each keep squad's builders on hand - on the keep, fed, not fighting - and where they stand: squad loops. */
    private final Map<String, List<LevelPlan.Builder>> keepCrews = new ConcurrentHashMap<>();
    /** Lines waiting to go on the net, by sender - squad loops. */
    private final Map<String, java.util.Deque<String[]>> netOutbox = new ConcurrentHashMap<>();
    /** When each sender last said a line - squad loops. */
    private final Map<String, Long> netSentAt = new ConcurrentHashMap<>();
    /** Each connected soldier's chat command line, by name. */
    private final Map<String, Consumer<String>> chatLines = new ConcurrentHashMap<>();

    /** How recently a fetcher must have found nothing for its squad's pit to move on. */
    static final long PIT_MOVE_MILLIS = 60_000;
    /** How near its pit a fetcher must be for its finding nothing to move the pit on. */
    static final double PIT_NEAR = 32;
    /** How many times each squad's keep borrow pit has moved on, and when it last did - squad loop only. */
    private final Map<String, Integer> keepPitShift = new ConcurrentHashMap<>();
    /** The sides of the keep a share's borrow pit goes off, in turn - 0 east, 2 south, 3 north; never 1, west, the depot's. */
    private static final int[] PIT_SIDES_FIRST = {3, 0, 2}, PIT_SIDES_SECOND = {0, 2, 3};
    private final Map<String, Long> keepPitMovedAt = new ConcurrentHashMap<>();

    /** Builders on the keep fetching dirt to climb on - squad loop only. */
    private final Set<String> keepFetching = ConcurrentHashMap.newKeySet();

    /** Soldiers the recovery drill has, stranded till they're out - squad loop writes, tick reads. */
    private final Set<String> recovering = ConcurrentHashMap.newKeySet();
    /** Soldiers on the keep sent to the stockpile for cobblestone - squad loop writes, tick reads. */
    private final Set<String> keepDrawing = ConcurrentHashMap.newKeySet();
    /** Builders on the keep drawing cobblestone from the depot to climb on - squad loop writes, tick reads. */
    private final Set<String> keepClimbDrawing = ConcurrentHashMap.newKeySet();

    /** What no ground work digs or fills: the camp's kept structures, its mine, and every squad's stairs. */
    private Set<Cell> groundExcept() {
        Set<Cell> except = new HashSet<>();
        Stream.concat(Stream.concat(campKeep().stream(), campMine().stream()), squadStates.values().stream().filter(s -> s.stairs != null)
                .flatMap(s -> Stream.concat(s.stairs.walkway().stream(), s.stairs.landing().stream()))).forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        return except;
    }

    /** Whether {@code cell} is a structure's - a wall line's, or the keep's slab - where a step goes in as cobblestone. */
    private boolean builtCell(BlockPos cell) {
        return chainOfCommand.flatten().slab().contains(cell.x(), cell.y(), cell.z()) || walls.values().stream().anyMatch(w -> w.contains(cell));
    }

    /**
     * {@code soldier}'s ground-work command to the routine that does it: a dig's cells to its
     * clearer, a fill's to its filler, a gather's box to its gatherer. None, and it's off all three.
     */
    private void route(String soldier, Order order) {
        switch (order) {
            case Order.Dig d -> {
                clearing.put(soldier, d.tunnel() && !d.cells().isEmpty() ? Clearing.toCut(world, d.cells())
                        : Clearing.toClear(world, d.box(), d.except(), d.tunnel()));
                clearingStrip.put(soldier, d.box());
                if (d.tunnel()) {
                    clearingTunnel.add(soldier);
                } else {
                    clearingTunnel.remove(soldier);
                }
                filling.remove(soldier);
                groundGather.remove(soldier);
            }
            case Order.Fill f -> {
                filling.put(soldier, Clearing.toFill(world, f.box(), f.except()));
                if (f.stoneOnly()) {
                    stoneFilling.add(soldier);
                } else {
                    stoneFilling.remove(soldier);
                }
                if (f.material() == Order.Fill.Material.SOIL) {
                    soilFilling.add(soldier);
                } else {
                    soilFilling.remove(soldier);
                }
                clearing.remove(soldier);
                clearingTunnel.remove(soldier);
                groundGather.remove(soldier);
            }
            case Order.Gather g -> {
                groundGather.put(soldier, g);
                clearing.remove(soldier);
                clearingTunnel.remove(soldier);
                filling.remove(soldier);
            }
            case null, default -> {
                // Its stair up gone with its dig: live (field406), Jack, out, was still read as on it.
                clearing.remove(soldier);
                clearingTunnel.remove(soldier);
                filling.remove(soldier);
                groundGather.remove(soldier);
            }
        }
    }

    /** Soldiers whose fill is in cobblestone only - the keep's - squad loop writes, tick reads. */
    private final Set<String> stoneFilling = ConcurrentHashMap.newKeySet();
    /** Soldiers whose fill is in dirt alone: a soil site's top (Flatten.soil) - squad loop writes, tick reads. */
    private final Set<String> soilFilling = ConcurrentHashMap.newKeySet();

    /** Each producer's duty order from its squad leader (command.DutyOrder) - squad loop writes, tick reads. */
    private final DutyBook dutyBook = new DutyBook();
    /** Soldiers whose gather is a woodcutter's, kept to its strip (command.WoodDuties) - squad loop writes, tick reads. */
    private final Set<String> woodcutters = ConcurrentHashMap.newKeySet();
    /** Whether {@code name} gathers wood - a producer, whose 16 logs are a load (SoldierErrands.LOGS_KEPT_DRAWN). */
    private boolean gathersWood(String name) {
        return woodcutters.contains(name) || Optional.ofNullable(groundGather.get(name)).filter(g -> g.resource() == Resource.WOOD).isPresent()
                || Optional.ofNullable(gathering.get(name)).filter(o -> o.task().equals(new Objective.Task.Gather(Resource.WOOD))).isPresent();
    }
    /** Each soldier's {@code Order.Gather} from the ground work's drill - squad loop writes, tick reads. */
    private final Map<String, Order.Gather> groundGather = new ConcurrentHashMap<>();

    /**
     * When each soldier's gather last found nothing within reach - tick writes, squad loop reads. A
     * gather that's found nothing isn't work for a while: live, a squad sent for torch makings with
     * no tree in reach stood about rather than keep the camp. Its gatherer forgets it found nothing
     * once another routine has the hands, so the time is kept here.
     */
    private final Map<String, Long> gatherFoundNothing = new ConcurrentHashMap<>();
    /** How long a gather that found nothing isn't counted as work. */
    static final long FOUND_NOTHING_MILLIS = 5 * 60_000;

    /** Cells of the camp's ground still dark, at most, for its upkeep to go on by night inside the wall line. */
    static final int NIGHT_UPKEEP_DARK = 8;

    /** Soldiers on camp upkeep, their clearing or filling the upkeep's - squad loop only. */
    private final Set<String> upkeeping = ConcurrentHashMap.newKeySet();

    /**
     * Records in the squad's register what each soldier is on this cycle - the work it was handed,
     * in the order its hands take it - with its report and what's left as the world measures it,
     * and logs what changed: a tasking given, done or dropped, a CANTCO, a report the world
     * doesn't bear out (backlog/task-register.md).
     */
    private void register(String squad, SquadState state, List<SoldierStatus> statuses) {
        Map<String, TaskRegister.Assignment> on = new LinkedHashMap<>();
        for (SoldierStatus s : statuses) {
            String n = s.name();
            String task = groundGather.containsKey(n) ? "gather " + groundGather.get(n).resource()
                    : filling.containsKey(n) ? (keepHands.contains(n) ? "fill the keep" : "fill the camp's ground")
                    : clearing.containsKey(n) ? (keepHands.contains(n) ? "dig the keep" : "dig the camp's ground")
                    : walling.containsKey(n) ? "wall the camp"
                    : repointing.containsKey(n) ? "wall the camp in stone"
                    // Lighting only with torches carried or being made: live, a soldier with none farmed
                    // while the register had it lighting, and its farm CANTCO was filed under the light.
                    : lighting.containsKey(n) && (torchesCarried.getOrDefault(n, 0) > 0
                            || Optional.ofNullable(torchmakers.get(n)).map(t -> t.active() || t.standAt().isPresent()).orElse(false))
                    ? "light the ground"
                    : cutting.containsKey(n) ? "cut the mine"
                    : farming.containsKey(n) ? "keep the farm"
                    : planting.containsKey(n) ? "plant the tree farm"
                    : gathering.get(n) instanceof Objective o
                            && o.task() instanceof Objective.Task.Gather(var r) ? "gather " + r
                    : patrolLegs.containsKey(n) ? "patrol" : null;
            if (task == null) {
                continue;
            }
            // The keep's by the squad's share, not the soldier's own strip: a builder climbing to its
            // strip's top puts its dirt steps in the strip, and live its own count rose as fast as it
            // dug - read as stalled while the squad's share came down.
            if (task.equals("dig the keep") || task.equals("fill the keep")) {
                int shareLeft = task.equals("dig the keep") ? state.keepDigLeft : state.keepFillLeft;
                on.put(n, new TaskRegister.Assignment(task, s.report(), shareLeft < 0 ? OptionalInt.empty() : OptionalInt.of(shareLeft)));
                continue;
            }
            List<?> left = switch (task) {
                case "dig the camp's ground", "dig the keep" -> clearing.get(n);
                case "fill the camp's ground", "fill the keep" -> filling.get(n);
                case "wall the camp" -> walling.get(n);
                case "wall the camp in stone" -> repointing.get(n);
                case "light the ground" -> lighting.get(n);
                default -> null;
            };
            on.put(n, new TaskRegister.Assignment(task, s.report(), left == null ? OptionalInt.empty() : OptionalInt.of(left.size())));
        }
        // What's left of each task the squad measures, for a soldier moved off one as its work ran out.
        Map<String, Integer> measured = new HashMap<>();
        if (state.digLeft >= 0) {
            measured.put("dig the camp's ground", state.digLeft);
        }
        if (state.fillLeft >= 0) {
            measured.put("fill the camp's ground", state.fillLeft);
        }
        // The keep's by the squad's share of it: a soldier walking to its strip, or on one its
        // squadmates are clearing, is on work that's going - live, the keep's dig measured as the camp's read every builder as stalled.
        if (state.keepDigLeft >= 0) {
            measured.put("dig the keep", state.keepDigLeft);
        }
        if (state.keepFillLeft >= 0) {
            measured.put("fill the keep", state.keepFillLeft);
        }
        if (state.wallLeft >= 0) {
            measured.put("wall the camp", state.wallLeft);
        }
        if (state.softLeft >= 0) {
            measured.put("wall the camp in stone", state.softLeft);
        }
        // Only SECURE's plan is counted: lighting the camp's upkeep or the stairs isn't, and live a
        // squad off upkeep read as done with SECURE's empty plan.
        if (state.torchesLeft >= 0 && taskOf(squad, Objective.Task.Secure.class).isPresent()) {
            measured.put("light the ground", state.torchesLeft);
        }
        long now = clockwork.millis();
        for (String finding : state.register.update(now, on, measured)) {
            if (finding.startsWith("[Verify]") && !finding.contains("again")) {
                LOG.warn(finding);
            } else {
                LOG.info(finding);
            }
        }
        // Supervise and refine: a soldier stalled on a task is taken off it for its next work, and
        // tried there again later - longer each time; one done with it is forgotten.
        for (TaskRegister.Tasking t : state.register.open()) {
            String key = t.who() + "|" + t.task();
            if (t.state() == TaskRegister.State.STALLED && !state.relieved.barred(key, now)) {
                state.relieved.failed(key, now);
                LOG.warn("[Register] {} taken off {} for now: the world shows no progress", t.who(), t.task());
            } else if (t.state() == TaskRegister.State.DONE) {
                state.relieved.worked(key);
            }
        }
    }

    /** Each soldier's failed tries at fitting its workshop's chest: tried again later each time. */
    private final Setbacks<String> chestFits =
            new Setbacks<>(60_000, 20 * 60_000);

    /** The routine holding each soldier's hands, after its last tick - tick writes, squad loop reads. */
    private final Map<String, Hands.Routine> handsHeld = new ConcurrentHashMap<>();

    /** Where {@code routine}, holding {@code soldier}'s hands, wants it to stand, if anywhere. */
    private Optional<Position> standOf(String soldier, Hands.Routine routine) {
        return switch (routine) {
            case CLEARER -> Optional.ofNullable(clearers.get(soldier)).flatMap(Clearer::standAt);
            case FILLER -> Optional.ofNullable(fillers.get(soldier)).flatMap(Filler::standAt);
            case MASON -> Optional.ofNullable(masons.get(soldier)).flatMap(Mason::standAt);
            case LAMPLIGHTER -> Optional.ofNullable(lamplighters.get(soldier)).flatMap(Lamplighter::standAt);
            case ARMOURER -> Optional.ofNullable(armourers.get(soldier)).flatMap(Armourer::standAt);
            case TORCHMAKER -> Optional.ofNullable(torchmakers.get(soldier)).flatMap(Torchmaker::standAt);
            case DEPOSITOR -> Optional.ofNullable(depositors.get(soldier)).flatMap(Depositor::standAt);
            case FORESTER -> Optional.ofNullable(foresters.get(soldier)).flatMap(Forester::standAt);
            case FARMER -> Optional.ofNullable(farmers.get(soldier)).flatMap(Farmer::standAt);
            case GATHERER -> Optional.ofNullable(gatherers.get(soldier)).flatMap(Gatherer::standAt);
            case HUNTER -> Optional.ofNullable(hunters.get(soldier)).flatMap(Hunter::standAt);
            default -> Optional.empty();
        };
    }

    /** What's left of {@code squad}'s ground work - blocks to dig, then cells to fill - once measured: for the squad simulation. */
    OptionalInt levellingLeft(String squad) {
        SquadState s = squadStates.get(squad);
        return s == null || s.digLeft < 0 || s.digLeft == 0 && s.fillLeft < 0 ? OptionalInt.empty()
                : OptionalInt.of(Math.max(0, s.digLeft) + Math.max(0, s.fillLeft));
    }

    /** {@code squad}'s open taskings - for the squad simulation to read. */
    List<TaskRegister.Tasking> taskings(String squad) {
        return squadStates.containsKey(squad) ? squadStates.get(squad).register.open() : List.of();
    }

    /** {@code squad}'s register review, ended taskings cleared - for the squad simulation to read. */
    List<String> taskingReview(String squad) {
        return squadStates.containsKey(squad) ? squadStates.get(squad).register.review(clockwork.millis()) : List.of();
    }

    /** Each squad's register for the dawn review: what was done and dropped since the last, and what's open. */
    private List<String> registerReview() {
        List<String> lines = new ArrayList<>();
        squadStates.forEach((squad, s) -> {
            lines.add("  " + squad + "'s taskings:");
            lines.addAll(s.register.review(clockwork.millis()));
        });
        return lines;
    }

    /**
     * What levelling the camp never digs: its wall, every squad's hut, and the ground under each
     * workshop's stand and fixtures - a workshop on a rise stays standing.
     */
    /** How far from the camp's centre a mine is the camp's. */
    static final int CAMP_MINE_RANGE = 24;

    /** Whether ({@code x}, {@code z}) is near enough the camp for a mine there to be the camp's. */
    private boolean nearCamp(int x, int z) {
        return camp != null && Math.hypot(x - camp[0], z - camp[1]) <= CAMP_MINE_RANGE;
    }

    /** The camp's mine, as kept on disk: empty for no camp, or no mine sited. */
    /** What a chest row leaves: the trees' claims and each mine's way down at its head (Staircase#headCells). */
    private Set<BlockPos> chestClaims() {
        Set<BlockPos> claims = new HashSet<>(treeClaims);
        Stream.concat(squadStairs.values().stream(), campStairs().stream()).forEach(m -> claims.addAll(m.headCells()));
        return claims;
    }

    private Optional<Staircase> campStairs() {
        if (campStairs == null) {
            campStairs = camp == null ? Optional.empty() : keptMine(camp[0] + "," + camp[1]);
        }
        return campStairs;
    }

    /** The mine kept on disk by {@code base}: a well's first, else a spiral cut before the standard. */
    private Optional<Staircase> keptMine(String base) {
        return mineStore.load(mineKey(base, true)).filter(k -> k.size() == 2 || k.size() == 3).map(k -> keptStairs(k, true))
                .or(() -> mineStore.load(base).filter(k -> k.size() == 2 || k.size() == 3).map(k -> keptStairs(k, false)));
    }

    /** The iron an iron pickaxe takes. */
    private static final int IRON_A_PICKAXE = 3;

    /**
     * Whether the force is short of iron: nobody carries an iron pickaxe, and there isn't the iron to
     * make one (backlog/maturity-model.md, basis of issue: at AIRHEAD the iron pickaxe is the iron
     * authorised - it opens the keep's emerald and the diamonds). A mine worked out is run on while it is.
     */
    /** Whether the force wants ore: short of iron, or a soldier wearing less than a full set of armour. */
    private boolean oreWanted() {
        return ironShort() || activeSoldiers.values().stream()
                .anyMatch(s -> s.status().condition().flatMap(Condition::kit).map(k -> k.armour() < FULL_ARMOUR).orElse(false));
    }

    /** Pieces in a full set of armour. */
    static final int FULL_ARMOUR = 4;

    private boolean ironShort() {
        int iron = soldierIron.values().stream().mapToInt(Integer::intValue).sum()
                + chainOfCommand.stores().map(s -> s.rawIron() + s.ironIngots()).orElse(0);
        return !ironPickaxe() && iron < IRON_A_PICKAXE;
    }

    /**
     * Ore blocks a night's mining should bring up at least (docs/conventions.md, check the outcome):
     * every squad works the mine through the night, and a mine with tunnels to cut finds a vein or
     * two a stretch. Under it, the night is said SHORT and dug into.
     */
    static final int NIGHT_ORE = 16;
    /** The mine's cutters' ore taken, summed, at dusk: the night's yield is the change by dawn. */
    private volatile Integer oreAtDusk;
    /**
     * Whether it was dark at the squad loop's last look: dusk is the change. Dark to begin with, so a
     * garrison come up in the night doesn't take its first look for dusk and measure a part of a night
     * as the whole - live (field310), "0 ore block(s) taken" for the last minutes of one.
     */
    private volatile boolean wasDark = true;
    /**
     * The keep's mine moved on, to disk: lost at a restart, the night shift went back to the old
     * site, dried up, and had to work it out again before it moved.
     */
    private void keepMineSite(Position site) {
        synchronized (chestStore) {
            chestStore.save(MINE_SITE, List.of(BlockPos.containing(site.x(), site.y(), site.z())));
            chestStore.save(MINES_TRIED, chainOfCommand.minesTried().stream().map(p -> BlockPos.containing(p.x(), p.y(), p.z())).toList());
        }
    }

    /** The key the sites the keep's mine has left are kept under. */
    private static final String MINES_TRIED = "minestried";

    /** Each level's tunnels given up, its crew's one set (Miner.tick), by stairs head and bottom - ticks write and read. */
    private final Map<String, Set<Integer>> levelsBlocked = new ConcurrentHashMap<>();
    /** Where soldiers were killed lately (command.DangerAreas) - death handlers write, squad loops read, under its lock. */
    private final DangerAreaBook dangerAreas;
    private final StoresBook storesBook; private final PhaseBook phaseBook; private final WoodsBook woodsBook; // the stores, the phase, the woods cut out
    /** Mine heads worked out or blocked, never sited again - kept on disk. Squad loop, read at siting. */
    private final Set<BlockPos> deadWells = ConcurrentHashMap.newKeySet();
    /** The key the dead wells are kept under. */
    private static final String DEAD_WELLS = "deadwells";

    /** {@code head}'s well is worked out or blocked: never sited again, across restarts. */
    private void deadWell(BlockPos head) {
        if (deadWells.add(head)) {
            synchronized (chestStore) {
                chestStore.save(DEAD_WELLS, List.copyOf(deadWells));
            }
        }
    }

    /** The key the keep's mine as it stands is kept under. */
    private static final String MINE_SITE = "keepmine";

    /** The mines said dried up, by head: said once. */
    private final Set<BlockPos> driedUp = ConcurrentHashMap.newKeySet();

    /** At dusk, the ore taken so far; at dawn, the night's yield against {@link #NIGHT_ORE}. */
    private void nightsMining() {
        int taken = miners.values().stream().mapToInt(Miner::oreTaken).sum();
        Integer dusk = oreAtDusk;
        oreAtDusk = null;
        if (dusk == null) {
            return; // the garrison came up in the night: no dusk to measure from
        }
        int night = taken - dusk;
        if (night < NIGHT_ORE) {
            // Where it stands, so the short night's cause is read off it: each mine's level and length,
            // and whether its cutters were all done - nothing left to cut - or ore no longer wanted.
            String mines = squadStairs.entrySet().stream().map(e -> e.getKey() + " y=" + e.getValue().bottom() + " "
                    + e.getValue().tunnel() + " blocks, crew " + mineCrew(e.getValue()).stream()
                    .map(n -> n + " " + Optional.ofNullable(stairsProgress.get(n)).map(p -> p.getClass().getSimpleName()).orElse("none"))
                    .toList()).collect(Collectors.joining("; "));
            LOG.warn("[Outcome] the night's mining: {} ore block(s) taken, {} expected - SHORT; ore wanted {}; {}", night, NIGHT_ORE,
                    oreWanted(), mines);
        } else {
            LOG.info("[Outcome] the night's mining: {} ore block(s) taken, {} expected", night, NIGHT_ORE);
        }
    }

    /** How long after one stretch is added before a mine read done again may have another: its miners' ticks catch up. */
    private static final long EXTEND_SETTLE_MILLIS = 30_000;

    /**
     * {@code cutter}'s share of {@code stairs} ({@link Staircase#share}): the mine's cutters - every
     * squad's on it - in order by name, and its place among them.
     */
    private Set<Integer> mineShare(String cutter, Staircase stairs) {
        List<String> crew = mineCrew(stairs);
        int at = crew.indexOf(cutter);
        return at < 0 ? stairs.share(0, 1) : stairs.share(at, crew.size());
    }

    /** Torches a mine's cutter carries (the rules of a mine: torches enough), and the mark under which it makes more. */
    static final int MINER_TORCHES = 32, MINER_TORCHES_LOW = 8;

    /** Cutters on a level: two, one to each side of its spine (the rules of a mine). */
    static final int CUTTERS_A_LEVEL = 2;

    /** The soldiers cutting {@code stairs}, every squad's, by name. */
    private List<String> mineCrew(Staircase stairs) {
        // Not one the recovery drill has: it cuts nothing, and counted in it shifts every other cutter's share
        // as it comes and goes. Live (field410-411), Eli, stranded and flipping between his squad's two units,
        // was in Bravo's crew one cycle and out the next, and Dana's share flipped with it - branches 47 and
        // 49 started in turn every second or two, 188 times a night, and the level took no ore.
        return cutting.entrySet().stream().filter(e -> e.getValue().head().equals(stairs.head()) && e.getValue().bottom() == stairs.bottom())
                .map(Map.Entry::getKey).filter(n -> !recovering.contains(n)).sorted().toList();
    }

    /**
     * The squad's second level, for its second pair of cutters (the rules of a mine: two soldiers to a
     * level, a squad of four on two): with three or more carrying a pickaxe, on a mine off the camp,
     * the next free level under every other's - kept on disk as the squad's second; worked out, the
     * next free one again. None for a squad of fewer, or at the camp's mine.
     */
    private Staircase secondLevel(String squad, SquadState state, List<SoldierStatus> statuses) {
        Staircase first = state.stairs;
        String key = squad + " second";
        long picks = statuses.stream().filter(st -> st.condition().flatMap(c -> c.kit()).map(Kit::pickaxe).orElse(false)).count();
        if (first == null || state.stairsFor == null || nearCamp(first.head().x(), first.head().z()) || picks <= CUTTERS_A_LEVEL) {
            squadStairs.remove(key);
            return null;
        }
        int x = state.stairsFor[0], z = state.stairsFor[1];
        // Never a level another squad holds (the rules of a mine: a squad to a level): live (field371),
        // Charlie's and Delta's second levels were both y=-56, one squad's kept on disk from before the
        // other was given it while it had too few pickaxes for one, and four soldiers worked one stretch.
        java.util.function.Predicate<Staircase> ownLevel = k -> squadStairs.entrySet().stream()
                .noneMatch(e -> !e.getKey().equals(key) && e.getValue().head().equals(k.head()) && e.getValue().bottom() == k.bottom());
        Staircase level = state.second != null && state.second.head().equals(first.head()) && ownLevel.test(state.second) ? state.second
                : keptMine(levelKey(x, z, key)).filter(k -> k.head().equals(first.head())).filter(ownLevel).orElse(null);
        List<String> crew = level == null ? List.of() : mineCrew(level);
        // Worked out by its own crew's word, once they've had it a while: a crew given it reports what
        // it last did on another - live (field320), Charlie's second level at y=-12 read worked out the
        // moment it was given, and was given again every cycle.
        long now = clockwork.millis();
        boolean settled = level != null && now - mineExtendedAt.getOrDefault(level.head().offset(0, level.bottom(), 0), 0L) >= EXTEND_SETTLE_MILLIS;
        boolean workedOut = settled && !crew.isEmpty() && crew.stream().allMatch(n -> stairsProgress.get(n) instanceof Miner.Progress.Done);
        if (level == null || workedOut) {
            Staircase left = level;
            // Its own tunnel run on a stretch first, as the first pair's is (operator, 2026-11-17: a mine run
            // wider before it's left): live (field357), the second pair at y=-52 had no level under it, read
            // the keep's mine dried up and moved the night shift off it, its tunnels at 96 of 192.
            // Then the next free level under the rest and under the one it leaves - never the same again.
            Optional<Staircase> runOn = left == null || !oreWanted() ? Optional.empty() : left.extended();
            Optional<Staircase> next = runOn.or(() -> freeLevel(left == null ? first : left.bottom() < first.bottom() ? left : first, key)
                    .filter(n -> left == null || n.bottom() < left.bottom()));
            squadStairs.remove(key);
            if (next.isEmpty()) {
                // No level left under it: the mine is dried up for this pair too.
                if (level != null && driedUp.add(level.head().offset(0, level.bottom(), 0))) {
                    LOG.warn("[Outcome] [{}] the mine at {} is dried up for its second level: every level cut down to y={} - SHORT",
                            squad, first.head(), level.bottom());
                    chainOfCommand.onMineDriedUp(state.stairs.head().x(), state.stairs.head().z()).ifPresent(this::keepMineSite); // by its head, not the finder's order (field381)
                }
                return level;
            }
            if (level != null && runOn.isPresent()) {
                LOG.info("[{}] MINE: its second level at y={} worked out - its main tunnel run on to {} blocks", squad, level.bottom(),
                        runOn.get().tunnel());
                crew.forEach(stairsProgress::remove);
            } else if (level != null) {
                LOG.warn("[Outcome] [{}] its second level at y={} is worked out - its next cut at y={}", squad, level.bottom(), next.get().bottom());
                crew.forEach(stairsProgress::remove);
            }
            level = next.get();
            mineExtendedAt.put(level.head().offset(0, level.bottom(), 0), now);
            LOG.info("[{}] MINE: its second level of the mine at {}: y={}", squad, first.head(), level.bottom());
            mineStore.save(mineKey(levelKey(x, z, key), level.well()), toKeep(level));
            treeClaims.addAll(level.walkway());
        }
        squadStairs.put(key, level);
        return level;
    }

    /**
     * The squad's own level off {@code base}'s stairs (the rules of a mine: a squad to a level): the
     * base's own level if no other squad works it, else the next free y % 4 == 0 under every level
     * the others work - kept on disk as the squad's.
     */
    private Staircase ownLevel(Staircase base, String squad, int x, int z) {
        boolean taken = squadStairs.entrySet().stream().anyMatch(e -> !e.getKey().equals(squad)
                && e.getValue().head().equals(base.head()) && e.getValue().bottom() == base.bottom());
        Staircase level = taken ? freeLevel(base, squad).orElse(base) : base;
        if (level != base) {
            LOG.info("[{}] MINE: its own level of the mine at {}: y={}", squad, base.head(), level.bottom());
        }
        mineStore.save(mineKey(levelKey(x, z, squad), level.well()), toKeep(level));
        return level;
    }

    /** The next level free of every squad's on {@code mine}'s stairs: the first y % 4 == 0 under the deepest of them. */
    private Optional<Staircase> freeLevel(Staircase mine, String squad) {
        int deepest = squadStairs.entrySet().stream().filter(e -> e.getValue().head().equals(mine.head()))
                .mapToInt(e -> e.getValue().bottom()).min().orElse(mine.bottom());
        int next = Math.floorDiv(Math.min(deepest, mine.bottom()) - 1, Staircase.LEVEL_EVERY) * Staircase.LEVEL_EVERY;
        return mine.atLevel(next);
    }

    /** Slots past a soldier's load that make a trip to the depot of their own: fewer go with its next load. */
    static final int SURPLUS_TRIP = 3;

    /** What a miner stashes at the landing, and how much of it makes a trip. Coal stays in the pack, for torches. */
    static final Set<ItemClass> STASHED = Set.of(ItemClass.RAW_IRON, ItemClass.RAW_COPPER, ItemClass.DIAMOND);
    static final int STASH_ORE = 8;
    /** Logs a miner carries before it fits the stash's chest from them: the chest's two, and a log of handles to spare. */
    static final int STASH_LOGS = 4;

    /** Each mine's stash, by the mine's head, as last seen - written by the connection that stowed it. */
    private final Map<BlockPos, Stockpile> mineStash = new ConcurrentHashMap<>();

    /** A draw from a mine's stash: its chest, centred, the ore, and how much to carry when done. */
    record Unstash(Position chest, ItemClass ore, int carry) {
    }

    /** Chests standing on the ground to flatten, to salvage before they're dug - keep squad loops write, ticks read. */
    private volatile Set<BlockPos> condemned = Set.of();
    /** The keep hand salvaging them; null with none - keep squad loops write, ticks read. */
    private volatile String salvager;
    /** When each soldier was passed over as salvager, its pack with no room - ticks write, squad loops read. */
    private final Map<String, Long> salvagePassed = new ConcurrentHashMap<>();
    /** When the ground to flatten was last looked over for chests, and the site it was. */
    private volatile long condemnedAt = Long.MIN_VALUE;
    private volatile Flatten condemnedFor;
    /** How often the ground to flatten is looked over for chests standing on it. */
    static final long CONDEMNED_LOOK_MILLIS = 30_000;
    /** How far under the site's floor a keep hand may stand and still be its salvager. */
    static final int SALVAGER_BELOW = 2;
    /** How far over the site's floor a chest on it is looked for. */
    static final int CONDEMNED_ABOVE = 48;

    /**
     * The chests standing on {@code ground}'s dig - in its columns, at or over its floor - but a mine's stash:
     * looked over every {@link #CONDEMNED_LOOK_MILLIS} from the world, not from what's been opened, since a
     * depot left behind by a restart is known to nobody. Its salvager kept, or the nearest of the keep's
     * hands - every squad's - to the first chest still to empty.
     */
    private Set<BlockPos> condemned(Flatten ground, List<SoldierStatus> hands) {
        long now = clockwork.millis();
        if (!ground.equals(condemnedFor) || now - condemnedAt >= CONDEMNED_LOOK_MILLIS) {
            Set<BlockPos> stashes = squadStairs.values().stream().flatMap(s -> s.store().stream()).collect(Collectors.toSet());
            // The flatten's only while a squad is on it - a camp's chest on ground idle to flatten is the camp's own - and an old camp's always.
            boolean flattening = squadStates.keySet().stream().anyMatch(s -> taskOf(s, Objective.Task.Keep.class).isPresent());
            Set<BlockPos> found = new HashSet<>(flattening ? chestsOn(world, ground, CONDEMNED_ABOVE) : Set.of());
            found.addAll(chestsAround(world, oldCamps, OLD_CAMP_REACH));
            found.addAll(stockpileWatch.givenUp().stream().filter(c -> world.isChest(c.x(), c.y(), c.z())).toList());
            found.removeAll(stashes);
            Set<BlockPos> was = condemned;
            if (!found.equals(was)) {
                LOG.info("[Keep] salvage: {} chest(s) on the ground to flatten, {} still to empty - {}", found.size(),
                        found.stream().filter(c -> !emptied(c)).count(), found);
            }
            condemned = Set.copyOf(found);
            condemnedAt = now;
            condemnedFor = ground;
        }
        Optional<BlockPos> first = condemned.stream().filter(c -> !emptied(c)).min(Comparator.comparing(BlockPos::toString));
        Map<String, Position> on = new HashMap<>();
        keepHandsAt.values().forEach(on::putAll);
        hands.forEach(h -> on.put(h.name(), h.position()));
        // Not one the recovery drill has: live (field432), the first salvager was shut in at y=56 under the
        // keep's site, nearest by its column, and every trip it began was dropped as it cut its way out.
        on.keySet().removeIf(recovering::contains);
        // Nor one under the site's floor, wherever the drill stands with it: a restart forgets who it had, and
        // live (field433) the same salvager was chosen again in the pocket at y=56, 7 under the floor.
        on.values().removeIf(p -> p.y() < ground.bottom() - SALVAGER_BELOW);
        on.keySet().removeIf(n -> clockwork.millis() - salvagePassed.getOrDefault(n, Long.MIN_VALUE / 2) < SALVAGE_PASSED_MILLIS); // nor one whose pack had no room
        String was = salvager;
        // Kept while it's connected and the drill hasn't it, on the keep's rolls this cycle or not: live (field437),
        // the salvager went Jack, Liam, Jack in four minutes as each came off and on the dig, each trip begun lost.
        boolean keep = was != null && activeSoldiers.containsKey(was) && !recovering.contains(was);
        String now2 = first.isEmpty() ? null : keep || was != null && on.containsKey(was) ? was
                : on.entrySet().stream().min(Comparator.comparingDouble(e -> Math.hypot(e.getValue().x() - first.get().x(), e.getValue().z() - first.get().z())))
                .map(Map.Entry::getKey).orElse(null);
        if (!java.util.Objects.equals(was, now2)) {
            LOG.info("[Keep] salvage: {}", now2 != null ? now2 + " empties the chests on the ground into the depot"
                    : first.isEmpty() ? "done - every chest on the ground emptied" : "no keep hand on the ground to empty them for now");
        }
        salvager = now2;
        return condemned;
    }

    /**
     * Whether the chest at {@code chest} was last seen holding none of the depot's kinds - this run, or before
     * a restart (salvaged, kept on disk). Live (field432-435), each restart forgot what each of 26 chests
     * held, and the salvager went round them all again.
     */
    private boolean emptied(BlockPos chest) {
        Map<ItemClass, Integer> seen = chestStock.get(chest);
        if (seen != null && seen.values().stream().allMatch(n -> n == 0)) {
            if (salvaged.add(chest)) {
                synchronized (chestStore) {
                    chestStore.save(SALVAGED, List.copyOf(salvaged));
                }
            }
            return true;
        }
        return seen == null && salvaged.contains(chest);
    }

    /**
     * The salvager's next draw (backlog/salvage-and-pack.md): by day, with room in its pack, the depot's kind
     * the nearest chest still to empty was last seen holding most of - cobblestone to look in one not yet
     * opened. Its pack full, the depot gets the load as any builder's (stockpileFor).
     */
    private Optional<Unstash> salvageFor(String name, Position at, Inventory inventory, boolean dark, java.util.function.Predicate<Position> barred) {
        if (dark || !name.equals(salvager)) {
            return Optional.empty();
        }
        int free = 0;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (inventory.itemAt(slot) < 0) {
                free++;
            }
        }
        if (free < UNSTASH_FREE) { // its pack full, another salvages: live (field503-504), Carol held it all day, five spare swords in it
            if (salvagePassed.put(name, clockwork.millis()) == null || salvager != null) {
                LOG.info("[Keep] salvage: {}'s pack has no room - another salvages", name);
            }
            salvager = null;
            return Optional.empty();
        }
        // Not one its trips couldn't get at, while their setback lasts, but the next (field510: Mia at one all day, 15 behind it).
        Optional<BlockPos> chest = condemned.stream().filter(c -> !emptied(c) && !barred.test(new Position(c.x() + 0.5, c.y(), c.z() + 0.5)))
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - at.x(), c.z() + 0.5 - at.z())));
        if (chest.isEmpty()) {
            return Optional.empty();
        }
        Map<ItemClass, Integer> seen = chestStock.getOrDefault(chest.get(), Map.of());
        Map.Entry<ItemClass, Integer> most = seen.entrySet().stream().filter(e -> e.getValue() > 0).max(Map.Entry.comparingByValue())
                .orElse(Map.entry(ItemClass.COBBLESTONE, UNSTASH_LOAD));
        return Optional.of(new Unstash(new Position(chest.get().x() + 0.5, chest.get().y(), chest.get().z() + 0.5), most.getKey(),
                inventory.count(most.getKey()) + Math.min(UNSTASH_LOAD, most.getValue())));
    }

    /** Raw ore a soldier takes from a stash at a time: a stack. */
    static final int UNSTASH_LOAD = 64;
    /** Free slots a soldier keeps for the walk up with it. */
    static final int UNSTASH_FREE = 2;
    /** How far from a mine's head a soldier below it is down that mine. */
    static final int UNSTASH_REACH = 48;

    /**
     * Raw ore up the stairs (operator, 2026-11-15: bring raw material up so everyone can use it): by
     * day, a soldier down a mine with room in its pack draws the copper - then the iron - its stash
     * was last seen holding, and the metal pool puts what it doesn't need into the depot, where an
     * armourer draws it. Live, 42 raw copper lay in a stash nobody drew from while nine soldiers of
     * sixteen had no armour, each looking for ore of its own. Empty by night, when the stash is filled.
     */
    private Optional<Unstash> unstashFor(Position at, Inventory inventory, boolean dark) {
        if (dark) {
            return Optional.empty();
        }
        int free = 0;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (inventory.itemAt(slot) < 0) {
                free++;
            }
        }
        if (free < UNSTASH_FREE) {
            return Optional.empty();
        }
        for (Staircase mine : squadStairs.values().stream().distinct().toList()) {
            Stockpile seen = mineStash.get(mine.head());
            if (seen == null || at.y() > mine.head().y() - 2
                    || Math.hypot(at.x() - mine.head().x(), at.z() - mine.head().z()) > UNSTASH_REACH) {
                continue;
            }
            ItemClass ore = seen.rawCopper() > 0 ? ItemClass.RAW_COPPER : seen.rawIron() > 0 ? ItemClass.RAW_IRON : null;
            if (ore == null) {
                continue;
            }
            int there = ore == ItemClass.RAW_COPPER ? seen.rawCopper() : seen.rawIron();
            BlockPos chest = mine.store().getFirst();
            return Optional.of(new Unstash(new Position(chest.x() + 0.5, chest.y(), chest.z() + 0.5), ore,
                    inventory.count(ore) + Math.min(UNSTASH_LOAD, there)));
        }
        return Optional.empty();
    }

    /** The mine whose stash chest stands at {@code chest} - centred as a stockpile's position is. */
    private Optional<Staircase> mineOfChest(Position chest) {
        BlockPos cell = BlockPos.containing(chest.x(), chest.y(), chest.z());
        return squadStairs.values().stream().filter(s -> s.store().getFirst().equals(cell)).findFirst();
    }

    /**
     * {@code soldier}'s step on {@code stairs} for the night, holding no cut: a step of its own in a
     * line of every squad's soldiers on the mine but its cutters, by name, below the cutters' own
     * waiting steps - from three behind the cut while the stairs go down, from above the landing once
     * they're down. Past the top, a place of its own beside the head; empty only for one not in the line.
     */
    private Optional<Position> nightPlace(Staircase stairs, String soldier) {
        Set<String> cutters = new HashSet<>(mineCrew(stairs));
        List<String> line = squadStairs.entrySet().stream().filter(e -> e.getValue().head().equals(stairs.head()))
                .flatMap(e -> presentSoldiers(e.getKey()).stream().map(DeterministicSoldier::status).map(SoldierStatus::name))
                .filter(n -> !cutters.contains(n)).sorted().distinct().toList();
        int rank = line.indexOf(soldier);
        if (rank < 0) {
            return Optional.empty();
        }
        int cut = stairs.stepsCut(world);
        int from = cut >= stairs.steps() ? stairs.steps() - 2 : cut - Miner.WAIT_BEHIND;
        int k = from - cutters.size() - rank;
        if (k >= 0) {
            return Optional.of(centre(stairs.step(k)));
        }
        // Past the top - too few steps cut for the line: a place of its own beside the head, four
        // abreast, in the line's order. Live, a mine on its first step sent everyone past the top to
        // its cutter's cell, and pairs stood in one body's width all night.
        int over = -k - 1;
        return Optional.of(new Position(stairs.head().x() - 1.5 + over % NIGHT_ABREAST, stairs.head().y(),
                stairs.head().z() + 3.5 + over / NIGHT_ABREAST));
    }

    /** How near in height a walk up to a squadmate, or onto what it threw, needs to get: a body's height and a jump. */
    static final double TO_SOMEONE_VERTICAL = 2.5;

    /** How many abreast stand beside a mine's head by night, past the top of a line too long for its stairs. */
    static final int NIGHT_ABREAST = 4;

    /** When each mine, by its head, was last run on a stretch - squad loops write and read. */
    private final Map<BlockPos, Long> mineExtendedAt = new ConcurrentHashMap<>();

    /** The camp's mine's walkway and landing, as kept on disk: empty for no camp, or no mine sited. */
    private List<BlockPos> campMine() {
        return campStairs().map(s -> Stream.concat(s.walkway().stream(), s.landing().stream()).toList()).orElse(List.of());
    }

    private Set<BlockPos> campKeep() {
        Set<BlockPos> keep = new HashSet<>();
        // Never the wall: levelled round the camp's ground, not the wall's centre, its high ground
        // took in the wall's own blocks, dug out as the masons put them back - live, soldiers broke blocks and put them back all day.
        walls.values().forEach(keep::addAll);
        // A hut going up is kept as well as one built: cobblestone over the floor is dug by day. Only one on the camp's
        // level, or none of its ground's: one on ground still to come down is the night's, dug with it (CampGround.hutKept).
        Optional<Position> camp = chainOfCommand.foothold().map(Foothold::camp);
        fortifying.values().stream().filter(f -> CampGround.hutKept(f.stand(), camp, CAMP_RADIUS)).forEach(f -> keep.addAll(f.cells()));
        for (SquadState s : squadStates.values()) {
            if (s.builtBase != null && CampGround.hutKept(s.builtBase.floor().getFirst(), camp, CAMP_RADIUS)) {
                s.builtBase.cells().forEach(c -> keep.add(BlockPos.containing(c.x(), c.y(), c.z())));
                s.builtBase.floor().forEach(c -> keep.add(BlockPos.containing(c.x(), c.y() - 1, c.z())));
            }
        }
        List<Workshop> shops = new ArrayList<>(workshops.values());
        if (campWorkshop != null) {
            shops.add(campWorkshop);
        }
        // Each workshop's stand, table, furnace and chest - and every chest found full, its row's earlier ones: live
        // (field480, field486), the camp's upkeep dug its full chests out, and the stores they held were forgotten.
        // And every chest standing in the camp workshop's row: live (field487), idle squads dug them out of it.
        Stream.concat(Stream.concat(shops.stream().flatMap(w -> Stream.concat(Stream.of(w.stand(), w.table(), w.furnace()), w.chest().stream())), fullChests.stream()),
                        campWorkshop == null ? Stream.empty() : campWorkshop.chestCells(world, chestClaims(), DEPOT_CHESTS).stream()
                                .filter(c -> world.isChest(c.x(), c.y(), c.z())))
                .forEach(c -> keep.addAll(List.of(c, c.offset(0, -1, 0))));
        return keep;
    }

    /** How far below the stairs' head a soldier is down the mine, for the mine base's workshop. */
    private static final int MINE_BELOW = 3;
    /** How far across from its last mine's head a soldier below it is sent up its stairs first. */
    static final double CLIMB_OUT_NEAR = 64;

    /**
     * How the squad's task stands, from the world where the task leaves a mark - the wall's open
     * cells, the torches still to put down, the stairs cut - and from its soldiers' reports: the
     * acknowledgement the platoon leader decides on. Empty with no task, or a patrol base, whose own status
     * line says how it stands.
     */
    private Optional<Progress> progress(String squad, SquadState state, List<SoldierStatus> statuses) {
        Optional<Objective> standing = chainOfCommand.objective(squad);
        if (standing.isEmpty() || standing.get().task() instanceof Objective.Task.None
                || standing.get().task() instanceof Objective.Task.PatrolBase) {
            state.workedFor = null;
            state.progress = null;
            return Optional.empty();
        }
        long now = clockwork.millis();
        if (!standing.get().equals(state.workedFor) || statuses.stream().anyMatch(s -> Progress.working(s.report()))) {
            state.workedAt = now;
            state.workedFor = standing.get();
        }
        Optional<String> done = Optional.empty();
        Optional<String> left = Optional.empty();
        switch (standing.get().task()) {
            case Objective.Task.Fortify() -> {
                if (state.wall != null && state.wallLeft == 0 && state.softLeft > 0) {
                    left = Optional.of(state.softLeft + " of the wall's blocks dirt, to replace with stone");
                } else if (state.wall != null && state.wallLeft == 0) {
                    done = Optional.of("the wall is closed, all " + state.wall.size() + " blocks of it");
                } else if (state.wall != null && state.wallLeft > 0) {
                    left = Optional.of(state.wallLeft + " of " + state.wall.size() + " wall blocks to place");
                }
                if (state.digLeft > 0) {
                    left = Optional.of(state.digLeft + " blocks over the camp's ground to dig" + left.map(l -> ", and " + l).orElse(""));
                    done = Optional.empty();
                } else if (state.fillLeft > 0) {
                    left = Optional.of(state.fillLeft + " cells under the camp's ground to fill" + left.map(l -> ", and " + l).orElse(""));
                    done = Optional.empty();
                }
            }
            case Objective.Task.Secure() -> {
                if (state.torchesLeft == 0) {
                    done = Optional.of("every walkable cell round the objective is lit");
                } else if (state.torchesLeft > 0 && state.torchesLeft <= NEAR_DONE_TORCHES
                        && clockwork.millis() - state.torchesChangedAt >= NEAR_DONE_MILLIS) {
                    // All but a cell or two nobody has reached: done, said so - live, a squad stood a
                    // day in a lit camp for one torch it couldn't put down, and the task never ended.
                    done = Optional.of("the ground round the objective is lit but for " + state.torchesLeft
                            + " cell(s) no soldier has reached in " + NEAR_DONE_MILLIS / 60_000 + " minutes");
                } else if (state.torchesLeft > 0) {
                    left = Optional.of(state.torchesLeft + " torches to put down");
                }
            }
            case Objective.Task.Mine() -> {
                // Done when every one of the squad's cutters on its level is, not the first: live (field330),
                // Alpha read DONE and UNDER_WAY by turns each second while its pair cut on. And the steps are
                // its own stairs', not the most any mine has cut - "122 of 70 steps down".
                List<String> crew = state.stairs == null ? List.of() : mineCrew(state.stairs).stream()
                        .filter(n -> statuses.stream().anyMatch(s -> s.name().equals(n))).toList();
                if (!crew.isEmpty() && crew.stream().allMatch(n -> stairsProgress.get(n) instanceof Miner.Progress.Done)) {
                    done = Optional.of("the stairs are cut down to y=" + state.stairs.bottom() + ", and the ore beside them taken");
                } else if (state.stairs != null) {
                    left = Optional.of("the stairs " + state.stairs.stepsCut(world) + " of " + state.stairs.steps() + " steps down");
                }
            }
            case Objective.Task.Gather(Resource r) when r == Resource.WOOD && dutyBook.spent(squad) -> done = Optional.of("every strip of the wood is cut out");
            case Objective.Task.Patrol() -> left = Optional.of("walking the round, on watch");
            default -> {
            }
        }
        Optional<String> behind = dutyBook.behind(squad, statuses.stream().map(SoldierStatus::name).toList(), OrderTrace.taskName(standing.get().task()));
        Progress measured = Progress.of(done, behind.isPresent() ? Optional.of(left.map(l -> l + "; ").orElse("") + behind.get()) : left, statuses, now - state.workedAt);
        if (state.progress == null || state.progress.state() != measured.state()) {
            LOG.info("[{}] task {}: {} - {}", squad, standing.get().task(), measured.state(), measured.detail());
        }
        state.progress = measured;
        return Optional.of(measured);
    }

    /**
     * Whether to sit this cycle out because a live experiment holds the machine's models: every
     * soldier told to stand still, and no SITREP sent - so neither the platoon leader nor a squad leader is
     * asked anything. A lock left by a run that died is said loudly and not obeyed.
     */
    private boolean yieldingToAnExperiment() {
        if (!yieldToExperiments) {
            return false;
        }
        ExperimentLock.State lock = ExperimentLock.state();
        if (lock != lastLock) {
            switch (lock) {
                case HELD -> LOG.info("An experiment is running - standing the squads still until it's done");
                case FREE -> LOG.info("The experiment is done - carrying on");
                case STALE -> LOG.error("The experiment lock at {} is held by a process that's gone - carrying on; delete it", ExperimentLock.PATH);
            }
            lastLock = lock;
        }
        if (lock != ExperimentLock.State.HELD) {
            return false;
        }
        activeSoldiers.values().forEach(soldier -> soldier.receiveOrder(new Order.Idle()));
        return true;
    }

    /** The name of the task {@code squad} holds, for the scorecard: "none" with no objective yet. */
    private String squadTask(String squad) {
        return objectiveOf(squad).map(o -> OrderTrace.taskName(o.task())).orElse("none");
    }

    /** Logs that fit out a depot: one for its table, two for its chest. */
    static final int DEPOT_LOGS = 3;
    /**
     * A block beside a soldier afloat at {@code at} it can cut for footing: level with its head first,
     * then its feet, then below - one a clear digs, with nothing over it that falls or flows into the hole.
     */
    private Optional<BlockPos> bankBlock(Position at) {
        int x = (int) Math.floor(at.x()), y = (int) Math.floor(at.y()), z = (int) Math.floor(at.z());
        for (int dy : new int[]{1, 0, -1}) {
            for (int[] h : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int cx = x + h[0], cy = y + dy, cz = z + h[1];
                if (world.isSolid(cx, cy, cz) && Clearing.diggable(world, cx, cy, cz)
                        && !world.falls(cx, cy + 1, cz) && !world.isHazard(cx, cy + 1, cz)) {
                    return Optional.of(new BlockPos(cx, cy, cz));
                }
            }
        }
        return Optional.empty();
    }

    /** How far above or below a soldier its goal can be for a wall between them to be what stops it. */
    static final int WAY_OUT_RISE = 3;
    /** How long a soldier sent to the camp's farm stays at it, whatever the squad's rating does meanwhile. */
    static final long FARM_STINT_MILLIS = 4 * 60_000;
    /** When each soldier was sent to the camp's farm - squad loops. */
    private final Map<String, Long> farmSince = new ConcurrentHashMap<>();
    /** Looks (one a second) a block at the feet alone must last to be one the soldier stands in, not a step mid-jump. */
    static final int FEET_IN_BLOCK_LOOKS = 2;
    /** How far over a squad's height the surface is looked for from, for the platoon leader's objectives. */
    static final int SURFACE_ABOVE = 128;
    /** The flood fill for whether a soldier reporting no route is in a pocket: how far and how many steps. */
    static final int CUT_OFF_SPAN = 48, CUT_OFF_BUDGET = 20_000;
    /** Each soldier's last pocket flood (cutOff), kept {@link #FLOOD_KEPT_MILLIS} while it stands on the cell - squad loops. */
    private final Map<String, Flood> cutOffFloods = new ConcurrentHashMap<>();
    /** How far across from its head a mine's galleries run: its branches, to 192, and a margin. */
    static final double MINE_GALLERY_REACH = 200;
    /** A soldier that can walk no more cells than this is in a pocket - WayOut's measure of one. */
    static final int CUT_OFF_CELLS = 128;
    /** How far under where it's sent a soldier with no way up to that height is cut off below it. */
    static final int CUT_OFF_RISE = 6;

    /**
     * {@code reported}, with a soldier's "no route" read as stranded only when it's cut off: it can
     * walk no more than {@link #CUT_OFF_CELLS} cells from where it stands, by the route planner's own
     * moves. One whose order's goal is out of reach - a squadmate in a pocket, a plot across water -
     * but that has the ground to walk isn't stranded, and is shown to the drill as moving. Live, the
     * keep's foreman, carrying rations to one in a dead end at the mine's bottom, was "stranded" on
     * the stairs every minute, and sent down to its squad, at MEDIUM risk, each time.
     */
    private List<SoldierStatus> cutOff(List<SoldierStatus> reported) {
        long now = clockwork.millis();
        return reported.stream().map(s -> {
            // A soldier in a pocket is stranded whatever its routine says between its "no route"
            // reports: live, one in a dead end at the mine's bottom reported the dig it meant to pillar
            // up to, and "nothing left to gather", between them, and the drill's count of twenty reports running never got there.
            // And one stood still a minute (IdleWatch) with no "no route" said at all: live, a soldier in
            // a pocket, its gatherer finding no gravel, reported "nothing left to gather" all morning.
            if (Recovery.stranded(s)) {
                noRouteAt.put(s.name(), now);
            } else if ((!noRouteAt.containsKey(s.name()) || now - noRouteAt.get(s.name()) > POCKET_MEMORY_MILLIS) && !idleWatch.alarmed(s.name())) {
                noRouteAt.remove(s.name());
                walksSaid.remove(s.name());
                return s;
            }
            Position from = s.position();
            // Its flood kept a while while it stands in it, flooded afresh each cycle a tenth of the garrison's time (p864) - on its cell, then p976 9%.
            Set<BlockPos> walkable = cutOffFloods.compute(s.name(), (n, f) -> f != null && f.cells().contains(BlockPos.containing(from.x(), from.y(), from.z())) && now - f.at() < FLOOD_KEPT_MILLIS ? f
                    : new Flood(BlockPos.containing(from.x(), from.y(), from.z()), now, Pathfinder.reachable(world, BlockPos.containing(from.x(), from.y(), from.z()), CUT_OFF_SPAN, CUT_OFF_BUDGET))).cells();
            int cells = walkable.size();
            // Or a cave of any size under where it's sent, with no way up to that height: live, two
            // soldiers down the mine's stairs at night stood in a cave of 866 cells at y=44, sent up to
            // y=64, "not stranded", and the drill that lays a stair up never came.
            // Only on a flood that ran out of ground, not budget: live, three on the mine's stairs at y=28
            // flooded the mine's tunnels to the budget before the stairs up, and were read as cut off -
            // a stair up laid out for one, another sent further down.
            boolean below = s.order() instanceof Order.MoveTo m && m.y() - from.y() > CUT_OFF_RISE && cells < CUT_OFF_BUDGET
                    && walkable.stream().noneMatch(c -> c.y() >= m.y() - m.verticalTolerance() - 1);
            // Or under the ground and sent to the surface, whatever the cave: a soldier digs itself out of
            // anything (operator, 2026-11-17). Live (field359-362), Ivy stood half an hour in a stone slot 26
            // under the hill, its cave big enough not to be "a pocket", her goals at her own height on the
            // far side of it - never stranded, so never dug out. Not one sent somewhere underground: a miner.
            // And only with none of the ground it can walk to out from under it: one in a cut it walks out
            // of every day isn't stranded (live, field365, the farm squad at the mine's head).
            // On a flood that ran out of ground, not budget, as below: live (field366), Gus down the mine at
            // y=-24 had a stair up 99 blocks laid out for him, the mine's own stairs past the flood's budget.
            boolean buried = s.order() instanceof Order.MoveTo m && cells < CUT_OFF_BUDGET && buried(from)
                    && !buried(new Position(m.x(), m.y(), m.z()))
                    && walkable.stream().allMatch(c -> buried(new Position(c.x() + 0.5, c.y(), c.z() + 0.5)));
            // In a mine's galleries - under its head, within its branches' reach - the mine's own stairs are its
            // way up, past the flood's span: only a pocket of its own strands it there. Live (field408-409),
            // Hal at his face 90 blocks out at y=-12 was read as buried 16 times a night, Olga 12, given stairs
            // up through 84 blocks of rock, and with them off the work three crews stood empty.
            Optional<Staircase> mine = squadStates.values().stream().flatMap(st -> Stream.of(st.stairs, st.second, st.lastStairs)).filter(Objects::nonNull)
                    .filter(st -> from.y() < st.head().y() - MINE_BELOW && Math.hypot(from.x() - st.head().x(), from.z() - st.head().z()) <= MINE_GALLERY_REACH).findFirst();
            // But a sealed cave - closed, all under the ground, none of it by a mine's stairs - is dug out of (operator, 2026-11-20; field497, Eli at y=20).
            boolean offItsStairs = closedPocket(from, walkable) && walkable.stream().allMatch(c -> buried(new Position(c.x() + 0.5, c.y(), c.z() + 0.5))) && mine.map(m -> walkable.stream().noneMatch(c -> Math.hypot(c.x() - m.head().x(), c.z() - m.head().z()) <= MINE_STAIR_REACH)).orElse(true);
            if (cells <= CUT_OFF_CELLS || mine.isEmpty() && (below || buried) || offItsStairs) {
                walksSaid.remove(s.name());
                return Recovery.stranded(s) ? s : new SoldierStatus(s.name(), s.position(), s.order(), new Report.Cantco(String.format(Locale.ROOT,
                        "no route from (%.0f, %.0f, %.0f): cut off in a pocket of %d cells", from.x(), from.y(), from.z(), cells)), s.condition());
            }
            if (Recovery.stranded(s) && walksSaid.add(s.name())) {
                LOG.info("{} reports no route to its goal but has {} cells to walk from {} - not stranded", s.name(), cells, from);
            }
            return Recovery.stranded(s) ? new SoldierStatus(s.name(), s.position(), s.order(), new Report.Moving(), s.condition()) : s;
        }).toList();
    }

    /**
     * Whether {@code at} is under the ground: more than {@link #UNDERGROUND} blocks of rock or soil over
     * it in its column - a tree's leaves and logs aren't ground. Live (field365), three of the farm squad
     * at the mine's head, under a tree whose top was 16 over them, were read as buried and given a stair
     * up through it.
     */
    private boolean buried(Position at) {
        int x = (int) Math.floor(at.x()), z = (int) Math.floor(at.z());
        if (!world.isLoaded(x, z)) {
            return false;
        }
        int y0 = (int) Math.floor(at.y()) + 2;
        int top = TerrainSurveyor.surface(world, x, z, Keep.SKY_TOP).map(BlockPos::y).orElse(y0);
        int ground = 0;
        for (int y = y0; y < top; y++) {
            if (world.isSolid(x, y, z) && !world.isLog(x, y, z) && world.leaves(x, y, z).isEmpty()) {
                ground++;
            }
        }
        return ground > UNDERGROUND;
    }

    /** How far under the ground over it a stranded soldier is under the ground, for the drill: a stair up, not down. */
    static final int UNDERGROUND = 8;
    /** How long after its last "no route" a soldier is still looked at as possibly in a pocket. */
    static final long POCKET_MEMORY_MILLIS = 60_000;
    /** When each soldier last reported no route - squad loops. */
    private final Map<String, Long> noRouteAt = new ConcurrentHashMap<>();
    /** Soldiers said to have ground to walk despite their "no route", once each time it starts - squad loops. */
    private final Set<String> walksSaid = ConcurrentHashMap.newKeySet();

    /**
     * The supply point a squad short of rations is sent to: none after dark when it's a walk across the
     * open from the squad's objective - the squad shares what it carries until dawn. Live (field385), at
     * night every soldier short of food was sent 100 blocks west to the camp's supply point, none went
     * down the mine, and six died on the way (0 ore of 16).
     */
    private Optional<Position> nightSupply(Position objective) {
        boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
        return supplyPoint.filter(p -> !dark || Math.hypot(p.x() - objective.x(), p.z() - objective.z()) <= NIGHT_SUPPLY_REACH);
    }

    /** How far from its objective a squad goes to its supply point by night. */
    static final int NIGHT_SUPPLY_REACH = 32;

    /** How near a hostile holds a stranded soldier off cutting its stair up. */
    static final double STAIR_THREAT_RADIUS = 4;
    /** What last held each soldier on a stair up from cutting - tick loops only, each its own key. */
    private final Map<String, String> stairHolds = new ConcurrentHashMap<>();
    /** Soldiers kept in their unit by the recovery drill, said once - squad loops only. */
    private final Set<String> heldInUnit = ConcurrentHashMap.newKeySet();
    /** Soldiers on a stair up whose clearer has no stand yet, said once - squad loops only. */
    private final Set<String> stairWaitSaid = ConcurrentHashMap.newKeySet();
    /** Soldiers stranded with water beside their way up, to plug it first - squad loops write, tick loops read. */
    private final Set<String> sealToClimb = ConcurrentHashMap.newKeySet();

    /** The engineer's stair up for a soldier stranded below, from where it stands: the cells to dig. */
    private Function<SoldierStatus, Optional<List<Cell>>> stairUp(String squadName) {
        return stuck -> {
            Position at = stuck.position();
            Map<String, String> whyNot = new LinkedHashMap<>();
            // Never through what no ground work digs - the camp's walls and huts, its mine, a squad's stairs: live
            // (field433), Pete, inside the camp at dusk, was read as in a pocket and given a stair up 15 blocks long out through it.
            Set<Cell> kept = groundExcept();
            Optional<List<Cell>> stair = Stairway.up(world, BlockPos.containing(at.x(), at.y(), at.z()), whyNot)
                    .map(cells -> cells.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList()).filter(cells -> {
                        boolean through = cells.stream().anyMatch(kept::contains);
                        if (through) {
                            whyNot.put("kept", "the way up cuts the camp's kept ground");
                        }
                        return !through;
                    });
            stair.ifPresentOrElse(
                    s -> LOG.info("[{}] RECOVERY: {} is stranded below at {} - a stair up laid out, {} blocks to dig", squadName, stuck.name(), at, s.size()),
                    () -> LOG.warn("[{}] RECOVERY: {} is stranded at {} and no stair up can be dug from there either - {}", squadName, stuck.name(), at, whyNot));
            // Water beside the way up - a cut that would let it in, or a step that is water - plugged with what
            // it carries, so the stair can be dug: live (field393), Eli stood in a pocket at y=-43 beside a
            // waterfall with 48 cobblestone, every heading barred by the water or the dirt it would let in.
            BlockPos feet = BlockPos.containing(at.x(), at.y(), at.z());
            boolean waterNear = false;
            for (int dx = -2; dx <= 2 && !waterNear; dx++) {
                for (int dz = -2; dz <= 2 && !waterNear; dz++) {
                    for (int dy = -1; dy <= 3 && !waterNear; dy++) {
                        waterNear = world.isWater(feet.x() + dx, feet.y() + dy, feet.z() + dz);
                    }
                }
            }
            if (stair.isEmpty() && waterNear) {
                if (sealToClimb.add(stuck.name())) {
                    LOG.warn("[{}] RECOVERY: {} plugs the water beside its way up at {} first", squadName, stuck.name(), at);
                }
            } else {
                sealToClimb.remove(stuck.name());
            }
            return stair;
        };
    }

    /**
     * The formation's slots ({@link Formations#assign}), led by the first soldier not detached to
     * the farm or the wood party: live, Delta's point man was sent to farm, and the squad formed its
     * wedge on her at the farm, 90 blocks from the keep it was ordered to, and stood there. Each
     * order back in the statuses' order.
     */
    private List<Order> formation(SquadOrder order, List<SoldierStatus> statuses) {
        java.util.function.Predicate<SoldierStatus> detached = s -> farming.containsKey(s.name()) || inWoodParty(s.name());
        List<SoldierStatus> formed = Stream.concat(statuses.stream().filter(detached.negate()), statuses.stream().filter(detached)).toList();
        List<Order> slots = Formations.assign(order, formed);
        Map<String, Order> byName = new HashMap<>();
        for (int i = 0; i < formed.size(); i++) {
            byName.put(formed.get(i).name(), slots.get(i));
        }
        return statuses.stream().map(s -> byName.get(s.name())).toList();
    }

    private void orderSquad(String squadName, SquadState state, List<DeterministicSoldier> squad, List<SoldierStatus> statuses,
                            SquadOrder order, Optional<Position> principalAt) {
        // Formation slots, then replacements' link-up, then pace control (the point man halts for
        // a straggler), then resupply at the supply point, then the react-to-contact drill, which
        // overrides all of them. Escorting, the ring around the principal replaces the formation
        // and pace control, and interceptors go after threats to the principal before contact.
        List<Order> orders;
        if (principalAt.isPresent()) {
            Position principal = principalAt.get();
            List<Position> threats = entityTracker.nearbyHostiles(principal, Escort.THREAT_RADIUS).stream()
                    .map(TrackedEntity::position).toList();
            orders = ReactToContact.orders(statuses, Escort.intercept(statuses,
                    // No link-up: a replacement's place is its slot in the ring, not beside the point man.
                    Resupply.orders(statuses, Escort.slots(statuses, order, principal), principal, supplyPoint), principal, threats));
        } else {
            // Each soldier's own report, in the statuses' order and only for those led here: the main
            // body's reports also hold its detached soldiers', and a replacement's place differs. Live,
            // recovery set an order by its place in the reports, and failed every tick for half an hour.
            Map<String, SoldierStatus> ownReports = new HashMap<>();
            state.reported.forEach(r -> ownReports.put(r.name(), r));
            // A stair up dug through - the world shows no cell of it left - ends the recovery it was for.
            statuses.stream().map(SoldierStatus::name)
                    .filter(n -> clearingTunnel.contains(n) && clearing.containsKey(n) && clearing.get(n).isEmpty())
                    .forEach(n -> state.recovery.release(n)
                            .forEach(freed -> LOG.info("[{}] RECOVERY: the stair up for {} is dug - {} walks out", squadName, freed, freed)));
            List<SoldierStatus> reported = cutOff(statuses.stream().map(s -> ownReports.getOrDefault(s.name(), s)).toList());
            condemned(chainOfCommand.flatten(), statuses); // its salvager any squad's soldier: an old camp's chests are emptied in every phase
            orders = ReactToContact.orders(statuses, state.recovery.orders(reported,
                    Resupply.orders(statuses, Cohesion.orders(statuses, state.linkUp.orders(statuses, formation(order, statuses), state.recovery::recovering,
                                    chainOfCommand.time().map(TimeOfDay::dark).orElse(false),
                                    // The squad's mine by night: where a replacement too far to link up goes, under the ground.
                                    chainOfCommand.mineObjective(Plan.squadOf(squadName)).map(Objective::location))),
                            order.objective(), nightSupply(order.objective())), Engineer.stairDown(world, squadName), stairUp(squadName), Engineer.pillarDown(world, this::buried, squadName)));
        }
        String straggler = principalAt.isPresent() ? null : Cohesion.waitingFor(statuses).map(SoldierStatus::name).orElse(null);
        if (!Objects.equals(straggler, state.haltedFor)) {
            if (straggler != null) {
                LOG.info("[{}] HALT: point man waits for {}", squadName, straggler);
            } else {
                LOG.info("[{}] squad closed up - moving on", squadName);
            }
            state.haltedFor = straggler;
        }
        // Pre-combat checks (docs/military-doctrine.md §11): with the squad leader's leave and the
        // squad out of contact, a bare-handed soldier makes itself a sword; while it does, it stands
        // where its routine needs it rather than in its slot.
        boolean squadInContact = statuses.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        Map<String, DeterministicSoldier> byName = new HashMap<>();
        squad.forEach(soldier -> byName.put(soldier.status().name(), soldier));
        // Cross-leveling (docs/military-doctrine.md §13), as the platoon sergeant pairs it.
        List<CrossLevel.Transfer> transfers = PlatoonSergeant.crossLevel(statuses, logsWanted, state.recovery::recovering, planting::containsKey);
        Set<CrossLevel.Transfer> crossLeveling = Set.copyOf(transfers);
        crossLeveling.stream().filter(t -> !state.crossLeveling.contains(t))
                .forEach(t -> LOG.info("[{} -> {}] CROSS-LEVEL: {} {} to {}", t.giver(), squadName, t.count(),
                        t.item().name().toLowerCase(Locale.ROOT), t.receiver()));
        state.crossLeveling = crossLeveling;
        Set<String> receiving = java.util.stream.Stream.concat(transfers.stream(), acrossSquads.values().stream())
                .map(CrossLevel.Transfer::receiver).collect(Collectors.toUnmodifiableSet());
        // Patrol base (docs/military-doctrine.md §14): each soldier to its place inside the hut's
        // outline; once all are in, they build its walls and roof around themselves.
        Optional<Objective> base = squadInContact
                ? Optional.empty() : taskOf(squadName, Objective.Task.PatrolBase.class);
        // A squad that has laid out its base stays on it: the platoon leader re-plans every few seconds and
        // nudges the objective a block or two, and an outline that moves keeps the soldiers walking.
        Position site = base.map(o -> o.location()).orElse(null);
        // Within the square the site was searched in: an old hut taken back can stand at its corner.
        if (site != null && state.baseSite != null && Math.max(Math.abs(site.x() - state.baseSite.x()), Math.abs(site.z() - state.baseSite.z())) <= SITE_SEARCH
                && Math.abs(site.y() - state.baseSite.y()) <= GROUND_SEARCH) {
            site = state.baseSite;
        } else if (site != null) {
            // The model's point is a guess at ground it can't see closely: the base goes on the
            // nearest level footing for the whole floor - never on rubble or a slope, where soldiers can't all stand in their places.
            Position asked = site;
            Position middle = statuses.stream().map(SoldierStatus::position).sorted(Comparator.comparingDouble(Position::y))
                    .toList().get(statuses.size() / 2);
            site = levelSite(site, statuses.size(), squadName, middle).orElse(null);
            if (site == null) {
                if (!asked.equals(state.unbuildable)) {
                    LOG.warn("[{}] no level ground for a patrol base within {} blocks of {} - not building", squadName,
                            SITE_SEARCH, asked);
                }
                state.unbuildable = asked;
            } else if (state.baseSite != null && !site.equals(state.baseSite)) {
                LOG.info("[{}] patrol base moved to {}", squadName, site);
            }
        }
        // Contact pauses the base; it doesn't give it up. Re-sited after every skirmish, a squad at
        // night left its half-built hut each time and laid out another, out in the open.
        if (!squadInContact) {
            state.baseSite = site;
        }
        PatrolBase.Plan plan = site == null ? null : PatrolBase.hasty(site, statuses.size());
        if (plan == null) {
            baseFootprints.remove(squadName);
            state.baseBlocksLeft = OptionalInt.empty();
        } else {
            baseFootprints.put(squadName, footprint(plan));
            state.baseBlocksLeft = OptionalInt.of((int) plan.cells().stream()
                    .filter(c -> !world.isSolid((int) c.x(), (int) c.y(), (int) c.z())).count());
        }
        boolean allInside = plan != null && statuses.stream().allMatch(s -> plan.inside(s.position()));
        if ((plan != null) != state.fortifying) {
            if (plan != null) {
                LOG.info("[{}] PATROL BASE at {}: {} blocks to build", squadName, site, plan.cells().size());
            } else if (squadInContact && state.baseSite != null) {
                LOG.info("[{}] patrol base paused for contact - kept at {}", squadName, state.baseSite);
            } else {
                LOG.info("[{}] patrol base stood down", squadName);
            }
            state.fortifying = plan != null;
        }
        if (allInside && !state.allInside) {
            LOG.info("[{}] all inside the patrol base - building", squadName);
        }
        state.allInside = allInside;
        // The base's standard, re-checked every cycle while it's held - anything can break a wall
        // after it's built: every way into the room is a breach, and goes back on the build list.
        List<Position> breaches = plan == null ? null
                : plan.breaches(c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z()));
        // And lit: every floor cell with block light, so nothing spawns there once the squad leaves.
        List<Position> unlit = plan == null ? List.of() : plan.unlit(c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z()),
                plan.floor().stream().flatMap(f -> Stream.of(f, new Position(f.x(), f.y() + 1, f.z())))
                        .filter(c -> world.isTorch((int) c.x(), (int) c.y(), (int) c.z())).toList());
        boolean lit = plan != null && unlit.isEmpty();
        if (breaches != null && allInside && (!breaches.equals(state.breaches) || lit != state.lit)) {
            if (breaches.isEmpty()) {
                if (lit) {
                    LOG.info("[{}] patrol base SEALED and LIT - nothing can get in or spawn inside", squadName);
                } else {
                    LOG.info("[{}] patrol base SEALED - but UNLIT at {}: its floor is spawn ground once the squad leaves{}", squadName,
                            unlit, squadHasTorchNow(statuses) ? "" : " - and no torch in the squad to light it");
                }
            } else {
                LOG.warn("[{}] patrol base BREACHED at {} - rebuilding", squadName, breaches);
                statuses.forEach(st -> Optional.ofNullable(builders.get(st.name())).ifPresent(Builder::retrySkipped));
            }
        }
        // What was last logged - reset while the squad isn't all in, so entering always says which.
        state.breaches = allInside ? breaches : null;
        state.lit = lit;
        if (plan == null) {
            baseReports.remove(squadName);
        } else {
            baseReports.put(squadName, new BaseReport(allInside, breaches.isEmpty(), lit));
        }
        // A squad with a base to light and no torch: one soldier who can make them does, outside the walls.
        boolean squadHasTorch = statuses.stream().anyMatch(st -> torchesCarried.getOrDefault(st.name(), 0) > 0);
        // Before the squad seals itself in: a torchbearer works outside, and a sealed hut has no door.
        boolean sealedIn = allInside && breaches != null && breaches.isEmpty();
        String torchbearer = plan == null || squadHasTorch || lit || sealedIn ? null : statuses.stream().map(SoldierStatus::name)
                .filter(n -> canMakeTorches.getOrDefault(n, false))
                .min(Comparator.comparing(n -> !n.equals(state.torchbearer))).orElse(null);
        state.torchbearer = torchbearer;
        // The torchbearer makes them at the squad's workshop, sited clear of the hut.
        Position workshop = torchbearer == null || state.workshop == null ? null : centre(state.workshop.stand());
        // Sealed in and dark, with no torch but the makings of some: a soldier who can make them cuts
        // a doorway toward the workshop and goes out. Once it's open the base isn't sealed, so the
        // torchbearer above takes over; the doorway goes back on the build list when it returns.
        if (plan == null || squadHasTorch || lit) {
            state.torchDoor = null;
        }
        String torchCutter = null;
        if (plan != null && sealedIn && !lit && !squadHasTorch) {
            Optional<String> maker = statuses.stream().map(SoldierStatus::name).filter(n -> canMakeTorches.getOrDefault(n, false))
                    .min(Comparator.comparing(n -> !n.equals(state.torchbearer)));
            Optional<Position> outside = maker.isEmpty() || state.workshop == null ? Optional.empty() : Optional.of(centre(state.workshop.stand()));
            if (outside.isPresent()) {
                List<Position> door = plan.doorway(outside.get(), this::walkable);
                if (state.torchDoor == null) {
                    LOG.info("[{}] patrol base sealed DARK - {} cuts a doorway to go and make torches", squadName, maker.get());
                }
                state.torchDoor = door;
                state.torchbearer = maker.get();
                torchCutter = maker.get();
                opening.put(torchCutter, new Opening(plan.insideOf(door), door.stream()
                        .map(c -> new BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList()));
            }
        }
        Set<Position> leftOpen = state.torchDoor == null ? Set.of() : Set.copyOf(state.torchDoor);
        // Sealed and dark: the first soldier inside with a torch puts one down where it stands.
        String lighter = plan == null || lit || breaches == null || !breaches.isEmpty() || !allInside ? null
                : statuses.stream().map(SoldierStatus::name).filter(n -> torchesCarried.getOrDefault(n, 0) > 0).findFirst().orElse(null);
        // Moving on from a base: cut a doorway toward the next objective. Not while only paused for contact - the base is still the squad's.
        String opener = null;
        // A soldier shut in a room the squad has no record of - a base built before a restart, say -
        // leaves it the same way as one it remembers: the room is read from the world.
        if (state.builtBase == null && objectiveOf(squadName).isPresent()) {
            statuses.stream().filter(st -> plan == null || !plan.inside(st.position()))
                    // Not a keep builder on its dirt run, in the borrow pit it's digging: it climbs out
                    // on what it carries. In the squad simulation one three deep in its own pit was
                    // taken for shut in, cut a "doorway" into the ground beside it every few seconds, and never got its load.
                    .filter(st -> !keepFetching.contains(st.name()))
                    // Not down a mine: its tunnels wall every soldier in them. Live, a miner at y=30 was
                    // taken for shut in a room, the room taken for the squad's base, and its way out cut
                    // for an hour from the stairs' top, the doorway routine holding two soldiers' hands.
                    .filter(st -> state.stairs == null || st.position().y() >= state.stairs.head().y() - MINE_BELOW)
                    // Nor a builder on the flatten, nor anyone on its ground: its trenches and steps wall a
                    // soldier in as a hut would, and a room read there is no base. Live, a builder on the
                    // east bench at y=83 and the farm squad's soldier crossing the site were taken for shut in; one boxed in is the recovery drill's.
                    // - while one's dug: with none, the site is the keep's ground round the camp, and the
                    // camp's own huts are what the check is for (SquadSimulationTest's camp on a slope).
                    .filter(st -> !keepHands.contains(st.name()) && (keepHands.isEmpty() || !chainOfCommand.flatten()
                            .benchedContains((int) Math.floor(st.position().x()), (int) Math.floor(st.position().z()))))
                    // Walled by what a squad builds with, not a tree: live, a soldier up a canopy was
                    // taken for shut in a hut, cut a doorway out of the leaves, and fell ten blocks.
                    .flatMap(st -> PatrolBase.enclosing(st.position(), c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z())
                                    && !world.isLog((int) c.x(), (int) c.y(), (int) c.z())
                                    && world.leaves((int) c.x(), (int) c.y(), (int) c.z()).isEmpty())
                            .map(room -> Map.entry(st.name(), room)).stream()).findFirst().ifPresent(shutIn -> {
                        LOG.warn("[{}] {} is shut in at {} - cutting a way out", squadName, shutIn.getKey(), shutIn.getValue().floor());
                        state.builtBase = shutIn.getValue();
                    });
        }
        // Leaving a base the squad built - for no base, or for one somewhere else - starts with a way out.
        boolean newSite = plan != null && state.builtBase != null && !plan.floor().equals(state.builtBase.floor());
        boolean stillCutting = false;
        if (plan != null && !newSite) {
            state.builtBase = plan;
        } else if (state.builtBase != null && (newSite || taskOf(squadName, Objective.Task.PatrolBase.class).isEmpty())) {
            PatrolBase.Plan built = state.builtBase;
            // Toward where the platoon leader is sending the squad: the squad leader's own order can still
            // point inside the hut, since from in there it sees no ground it can reach.
            List<Position> door = built.doorway(objectiveOf(squadName).orElseThrow().location(), this::walkable);
            boolean open = door.stream().noneMatch(c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z()));
            Optional<SoldierStatus> inside = statuses.stream().filter(st -> built.inside(st.position())).findFirst();
            if (open || inside.isEmpty()) {
                LOG.info("[{}] {}", squadName, open ? "doorway open - moving out of the patrol base" : "nobody left in the patrol base");
                state.builtBase = newSite ? plan : null;
            } else {
                stillCutting = true;
                opener = inside.get().name();
                opening.put(opener, new Opening(built.insideOf(door), door.stream()
                        .map(c -> new BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList()));
            }
        }
        final PatrolBase.Plan building = newSite && stillCutting ? null : plan;
        // A base the squad holds isn't a quarry: its walls go in the claims every dig skips, or a
        // soldier short of wood chops the wall it shelters behind. Re-added every cycle, since a
        // dig releasing its own claims can't take a wall's with them for longer than that.
        // And the camp's wall, once it's laid out: nobody quarries the wall they're inside.
        Set<BlockPos> held = Stream.concat(
                Stream.of(plan, state.builtBase).filter(Objects::nonNull).flatMap(p -> p.cells().stream())
                        .map(c -> new BlockPos((int) c.x(), (int) c.y(), (int) c.z())),
                state.wall == null ? Stream.<BlockPos>empty() : state.wall.stream())
                .collect(Collectors.toUnmodifiableSet());
        state.baseCells.stream().filter(c -> !held.contains(c)).forEach(treeClaims::remove);
        treeClaims.addAll(held);
        state.baseCells = held;
        boolean baseOrdered = taskOf(squadName, Objective.Task.PatrolBase.class).isPresent();
        // The farm: the plot round the water nearest the platoon leader's objective, kept while the
        // objective stays put - a farm is tended for days, not re-sited each cycle.
        FarmPlan farm = taskOf(squadName, Objective.Task.Farm.class).map(o -> {
            BlockPos at = BlockPos.containing(o.location().x(), o.location().y(), o.location().z());
            // Kept for the point it was sited for, as a wall is: live, its water lay just over the
            // radius from the site, and it was sited afresh every cycle, found or not by where a soldier stood.
            if (state.farm != null && (at.equals(state.farmFor)
                    || Math.hypot(state.farm.water().x() - at.x(), state.farm.water().z() - at.z()) <= FARM_SITE_RADIUS)) {
                return state.farm;
            }
            // Nor a point none was found for looked at again for a minute: its flood was a twelfth of the garrison's time (p864).
            if (at.equals(state.farmMissed) && clockwork.millis() - state.farmMissedAt < FARM_RETRY_MILLIS) {
                return null;
            }
            Position from = statuses.get(0).position();
            Set<BlockPos> walkable = Pathfinder.reachable(world, BlockPos.containing(from.x(), from.y(), from.z()),
                    (int) Math.hypot(at.x() - from.x(), at.z() - from.z()) + FARM_SITE_RADIUS + 4, FARM_REACH_BUDGET);
            FarmPlan sited = FarmPlan.site(world, at, FARM_SITE_RADIUS, FARM_SITE_RADIUS, walkable::contains).orElse(null);
            if (sited == null) {
                LOG.warn("[{}] no ground to farm by water within {} blocks of {}", squadName, FARM_SITE_RADIUS, o.location());
                state.farmMissed = at;
                state.farmMissedAt = clockwork.millis();
            } else {
                LOG.info("[{}] FARM by the water at {}: {} cells to plant", squadName, sited.water(), sited.plot().size());
                state.farmFor = at;
            }
            return sited;
        }).orElse(null);
        state.farm = farm;
        // The tree farm: laid out once, kept while the objective stays put - what grows in it is the farm working.
        TreeFarm trees = taskOf(squadName, Objective.Task.TreeFarm.class).map(o -> {
            BlockPos at = TerrainSurveyor.groundAt(world, BlockPos.containing(o.location().x(), o.location().y(), o.location().z()));
            Optional<TreeFarm> camps = campTrees.farm(world); // the camp's tree chunk, wherever the order points (CampTrees.farm)
            if (state.trees != null && camps.map(c -> c.centre().equals(state.trees.centre())).orElse(state.trees.centre().x() == at.x() && state.trees.centre().z() == at.z())) {
                return state.trees;
            }
            TreeFarm sited = camps.or(() -> TreeFarm.site(world, at, TREE_FARM_RADIUS)).orElse(null);
            if (sited == null) {
                LOG.warn("[{}] TREE_FARM: no open dirt or grass to plant round {}", squadName, o.location());
            } else {
                LOG.info("[{}] TREE_FARM round {}: {} cells to plant", squadName, sited.centre(), sited.plot().size());
            }
            return sited;
        }).orElse(null);
        state.trees = trees;
        // The wall: laid out once round the objective's ground, then filled in - laid out again after a row, the ground would be the wall's top.
        Optional<Objective> fortify = taskOf(squadName, Objective.Task.Fortify.class);
        if (fortify.isEmpty()) {
            state.wall = null;
            state.wallCentre = null;
            state.wallKey = null;
            state.wallLeft = -1;
        } else {
            // Kept by the place asked, not by the ground found there: the ground moves as the pits
            // are dug and the wall goes up, and a wall laid out again round it lost its progress -
            // in the squad simulation, and live, a wall that never closed.
            Position asked = fortify.get().location();
            // At the camp, the camp's own wall, not a new one round wherever the order points.
            String measured = campWallKey;
            String wallKey = measured != null && nearCamp((int) Math.floor(asked.x()), (int) Math.floor(asked.z())) ? measured
                    : (int) Math.floor(asked.x()) + "," + (int) Math.floor(asked.z());
            if (!wallKey.equals(state.wallKey)) {
                int kx = Integer.parseInt(wallKey.split(",")[0]);
                int kz = Integer.parseInt(wallKey.split(",")[1]);
                // Round the ground most of the camp stands on, not a hut roof at its centre.
                BlockPos at = typicalGround(kx, kz, (int) Math.floor(asked.y())).orElse(TerrainSurveyor.groundAt(world,
                        BlockPos.containing(kx + 0.5, asked.y(), kz + 0.5)));
                // One layout a wall, whichever squad comes to it first: a squad that lays out a wall
                // another has half built lays it out on top of what's there.
                state.wall = walls.computeIfAbsent(wallKey, key -> wallStore.load(key).orElseGet(() -> {
                    List<BlockPos> layout = Perimeter.layout(world, at, WALL_RADIUS);
                    wallStore.save(key, layout);
                    return layout;
                }));
                state.wallCentre = at;
                state.wallKey = wallKey;
                // The camp's ground is claimed, as its wall is: no dig takes it. Live, the masons' own
                // dirt pit, dug nearest first, ate in under the wall's east side and the ground inside it.
                for (int dx = -WALL_RADIUS; dx <= WALL_RADIUS; dx++) {
                    for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
                        for (int dy = -CAMP_GROUND_DEPTH; dy <= CAMP_GROUND_DEPTH; dy++) {
                            int x = at.x() + dx, y = at.y() + dy, z = at.z() + dz;
                            if (world.isSolid(x, y, z)) {
                                treeClaims.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
                LOG.info("[{}] FORTIFY: a wall laid out round {}, {} blocks", squadName, at, state.wall.size());
            }
        }
        List<BlockPos> wallLeft = state.wall == null ? List.of()
                : Perimeter.open(world, state.wall);
        if (state.wall != null && wallLeft.size() != state.wallLeft) {
            LOG.info("[{}] FORTIFY: {} of {} wall blocks still to place", squadName, wallLeft.size(), state.wall.size());
            state.wallLeft = wallLeft.size();
        }
        // A closed wall of dirt isn't done: each block of it is replaced with stone by a mason carrying some.
        List<BlockPos> wallSoft = state.wall == null ? List.of()
                : state.wall.stream().filter(c -> softInWall(c, state.wallCentre.x(), state.wallCentre.z(), Set.copyOf(state.wall))).toList();
        if (state.wall != null && wallSoft.size() != state.softLeft) {
            LOG.info("[{}] FORTIFY: {} of the wall's blocks dirt, to replace with stone{}", squadName, wallSoft.size(),
                    wallSoft.isEmpty() ? "" : " - " + wallSoft.stream().limit(4).map(c -> c.x() + " " + c.y() + " " + c.z()).toList()
                            + " of the wall kept as " + state.wallKey);
            state.softLeft = wallSoft.size();
        } else if (state.wall == null) {
            state.softLeft = -1;
        }
        // The wall closed and of stone, the camp's floor inside it is paved the same way: its soil,
        // block by block, replaced with cobblestone (backlog/cells.md - the floor at its own height).
        List<BlockPos> soft = state.wall != null && wallSoft.isEmpty() && wallLeft.isEmpty() && state.wallKey != null
                && state.wallKey.equals(campWallKey) ? floorToPave() : wallSoft;
        // Fortifying starts with the ground: the engineer's dig and fill boxes round the camp,
        // handed out by the squad's drill as each soldier's concrete command (command.Tasking).
        // Contact takes the soldiers in it off the ground work, not the squad - the rest keep their
        // strips, laid out over the whole crew so nobody's strip moves while a squadmate fights.
        // Live, one zombie at the keep's edge held eight builders off it for a quarter of an hour,
        // every one answering the squad leader's "move to the keep" instead.
        Set<String> fighting = statuses.stream().filter(s -> s.report() instanceof Report.InContact)
                .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
        Map<String, Order> ground = new HashMap<>(state.wallCentre == null ? Map.of()
                : groundwork(squadName, state, state.wallCentre, statuses));
        // Building the keep: its dig and its fill to the plan, a strip each (command.Keep, Tasking.keep).
        boolean onKeep = taskOf(squadName, Objective.Task.Keep.class).isPresent();
        if (onKeep) {
            // Held as fighting half a minute past its last contact: in and out of a fight each second,
            // in the squad simulation, a builder's top came and went with it, the level went up and
            // down a step each cycle, and its squadmate was put on its dig and taken off it all day.
            long keepNow = clockwork.millis();
            statuses.forEach(st -> {
                if (fighting.contains(st.name())) {
                    keepFoughtAt.put(st.name(), keepNow);
                    keepFightingAt.put(st.name(), st.position());
                }
                if (keepNow - keepFoughtAt.getOrDefault(st.name(), Long.MIN_VALUE / 2) < KEEP_FIGHT_MILLIS) {
                    keepFighting.add(st.name());
                } else {
                    keepFighting.remove(st.name());
                }
            });
            // Not one the recovery drill has: its stair is its work till it's out. Live (field340), Jack,
            // his stair up laid out 208 blocks long, was given a rectangle on the keep 130 blocks off and
            // above, and the dig's walk there held his hands.
            Map<String, Order> keepOrders = keepwork(squadName, state, statuses.stream()
                    .filter(s -> !state.recovery.recovering(s.name())).toList());
            ground.putAll(keepOrders);
            statuses.forEach(s -> {
                if (keepOrders.containsKey(s.name())) {
                    keepHands.add(s.name());
                } else {
                    keepHands.remove(s.name());
                }
            });
        } else {
            state.keepDigLeft = -1;
            state.keepFillLeft = -1;
            statuses.forEach(s -> {
                keepDrawing.remove(s.name());
                keepClimbDrawing.remove(s.name());
                keepHands.remove(s.name());
            });
        }
        // A soldier told to level ground: its dig or fill, as the world shows what's left.
        statuses.forEach(s -> planBook.of(s.name()).current().filter(d -> d instanceof Directive.Level)
                .flatMap(d -> levelWork(s, (Directive.Level) d)).ifPresent(work -> ground.put(s.name(), work)));
        // Sent for wood, the squad works it by duty (command.WoodDuties): each woodcutter its own strip's logs.
        Map<String, WoodDuties.Duty> woodDuties = WoodDuties.of(objectiveOf(squadName), statuses.stream().map(SoldierStatus::name).toList(),
                dutyBook.cutOut(squadName, statuses.stream().map(SoldierStatus::name).toList(), objectiveOf(squadName),
                        n -> gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE), at -> woodsBook.spent(chainOfCommand, at, clockwork.millis())));
        woodDuties.values().forEach(d -> d.order().ifPresent(o -> ground.putIfAbsent(d.soldier(), o)));
        statuses.forEach(s -> policeCall.order(s, ground.containsKey(s.name()), withoutWork.alarmed(s.name()) || idleWatch.alarmed(s.name()) && s.report() instanceof Report.Cantco,
                world, scaffolding, stepPlacedAt, activeSoldiers.values().stream().map(d -> d.status().position()).toList(),
                Optional.ofNullable(campWallKey).map(walls::get).map(Set::copyOf).orElse(Set.of()), clockwork.millis()).ifPresent(o -> ground.put(s.name(), o)));
        statuses.forEach(s -> woodcutters.remove(s.name()));
        // Each producer's duty order (command.DutyOrder): its duty, its ground, and the dump its squad stockpiles at, else the camp's.
        dutyBook.assign(squadName, statuses.stream().map(SoldierStatus::name).toList(), DutyBook.given(statuses.stream().map(SoldierStatus::name).toList(),
                woodDuties, farming, statuses.stream().findFirst().flatMap(s -> stockpileFor(s.name())).or(() -> chainOfCommand.foothold()
                        .flatMap(Foothold::dump))), objectiveOf(squadName).map(Objective::task)
                .filter(PhaseGuardrails::production).map(OrderTrace::taskName), clockwork.millis());
        woodDuties.values().stream().filter(d -> d.order().equals(Optional.ofNullable(ground.get(d.soldier())))).forEach(d -> woodcutters.add(d.soldier()));
        // The fight first: a soldier in contact answers its squad leader until it's over, and takes its ground work up again then.
        fighting.forEach(ground::remove);
        // Support is for free hands (docs/military-doctrine.md § 8): a soldier with ground work of its
        // own, out of contact, keeps at it and isn't sent to a squadmate's fight. In the squad
        // simulation a builder left its spire for a zombie its squadmate had in hand, 20 blocks off.
        List<Order> supporting = new ArrayList<>(orders);
        for (int i = 0; i < statuses.size(); i++) {
            SoldierStatus st = statuses.get(i);
            if (supporting.get(i) instanceof Order.MoveTo support && support.urgent() && ground.containsKey(st.name())) {
                supporting.set(i, new Order.MoveTo(st.position().x(), st.position().y(), st.position().z(), 3, 3, Risk.LOW, false));
            }
        }
        List<Order> eased = List.copyOf(supporting);
        if (state.wallCentre == null) {
            state.digLeft = -1;
            state.fillLeft = -1;
        }
        // Mining: the staircase at the objective's x and z, down to its y - kept while the objective
        // stays put, its head where the ground is when first sited.
        Staircase stairs = taskOf(squadName, Objective.Task.Mine.class).map(o -> {
            int x = (int) Math.floor(o.location().x());
            int z = (int) Math.floor(o.location().z());
            if (state.stairs != null && state.stairsFor != null && state.stairsFor[0] == x && state.stairsFor[1] == z) {
                // Worked out with the force short of iron: run on a stretch (operator, 2026-11-15: iron
                // everywhere, and no iron pickaxe made). Live, the mine read DONE all night with one raw
                // iron in the force, and the keep's emerald stood.
                long now = clockwork.millis();
                // One mine, several squads on it: each works the longest any of them has run it.
                Staircase mine = state.stairs;
                // Each squad its own level (the rules of a mine): nothing taken from another's. Live
                // (field308), squads sharing one level's stretches took one another's stale DONE for
                // their own, and the mine was run on with nothing cut.
                // Worked out when every cutter on it - every squad's - has its share cut, not when one has.
                List<String> crew = mineCrew(state.stairs);
                boolean workedOut = !crew.isEmpty() && crew.stream().allMatch(n -> stairsProgress.get(n) instanceof Miner.Progress.Done);
                // Stopped by water or lava beside a step of the stairs, any cutter: the shaft goes no deeper
                // for anyone, and the keep's mine moves on as from one dried up. Live (field348), a fresh well
                // stopped at its 58th step, y=8, every squad's level under it, and its crews stood at the head;
                // not every cutter: those waiting their turn below read "step 1" all night (field351).
                Optional<String> blockedBy = crew.stream().map(stairsProgress::get)
                        .filter(p -> p instanceof Miner.Progress.Stopped s && s.why().contains("water or lava beside step"))
                        .map(p -> ((Miner.Progress.Stopped) p).why()).findFirst();
                long killed = dangerAreas.near(state.stairs.head().x() + 0.5, state.stairs.head().z() + 0.5, now);
                if (killed >= DangerAreas.DEATHS && blockedBy.isEmpty()) {
                    blockedBy = Optional.of(killed + " killed within " + (int) DangerAreas.RADIUS + " blocks of its stairs in a day - a danger area");
                }
                // Its tunnels into a danger area given up, the head safe or not (MineDanger; live, field598, four of Charlie down one gallery) -
                // every level cut from its head, not the squad's alone: live (field605), Olga killed on a second level the check never saw.
                Stream.concat(Stream.of(state.stairs), squadStairs.values().stream().filter(l -> l.head().equals(state.stairs.head()))).distinct().forEach(level -> {
                    Set<Integer> given = levelsBlocked.computeIfAbsent(level.head() + "/" + level.bottom(), k -> ConcurrentHashMap.newKeySet());
                    MineDanger.tunnels(level.cuts(), c -> dangerAreas.near(c.x() + 0.5, c.y(), c.z() + 0.5, now)).forEach((t, at) -> {
                        if (given.add(t)) {
                            LOG.warn("[Outcome] tunnel {} of the mine at {} given up at y={}: a danger area at {}", t, level.head(), level.bottom(), at);
                        }
                    });
                });
                if (blockedBy.isPresent() && driedUp.add(state.stairs.head())) {
                    deadWell(state.stairs.head());
                    String why = blockedBy.get();
                    LOG.warn("[Outcome] the mine at {} is blocked at y={}: {} - SHORT", state.stairs.head(), state.stairs.bottom(), why);
                    chainOfCommand.onMineDriedUp(state.stairs.head().x(), state.stairs.head().z()).ifPresent(this::keepMineSite); // by its head, not the finder's order (field381)
                }
                // Settled by mine, not by squad: live, four squads on one mine each took the others' stale
                // DONE for their own, and it ran on from 24 blocks to 96 in seconds with nothing cut.
                // Worked on while ore is wanted: the iron short, or anyone short of armour (operator,
                // 2026-11-16: by now everyone should have armour). Run on a stretch, then a level deeper.
                if (workedOut && oreWanted() && now - mineExtendedAt.getOrDefault(state.stairs.head(), 0L) >= EXTEND_SETTLE_MILLIS) {
                    Optional<Staircase> run = state.stairs.extended();
                    Optional<Staircase> longer = run.or(() -> freeLevel(state.stairs, squadName));
                    if (run.isEmpty() && longer.isPresent()) {
                        LOG.warn("[Outcome] the mine at {} is worked out at y={} - its next level cut at y={}", state.stairs.head(),
                                state.stairs.bottom(), longer.get().bottom());
                    }
                    if (longer.isEmpty() && driedUp.add(state.stairs.head())) {
                        deadWell(state.stairs.head());
                        LOG.warn("[Outcome] the mine at {} is dried up: every level cut down to y={}, and ore still wanted - SHORT",
                                state.stairs.head(), state.stairs.bottom());
                        chainOfCommand.onMineDriedUp(state.stairs.head().x(), state.stairs.head().z()).ifPresent(this::keepMineSite); // by its head, not the finder's order (field381)
                    }
                    if (longer.isPresent()) {
                        mineExtendedAt.put(state.stairs.head(), now);
                        stairsProgress.clear();
                        if (!nearCamp(x, z)) {
                            mineStore.save(mineKey(levelKey(x, z, squadName), longer.get().well()), toKeep(longer.get())); // the camp's is saved with it below
                        }
                        treeClaims.addAll(longer.get().walkway());
                        LOG.info("[{}] MINE: worked out, the force short of iron - its main tunnel run on to {} blocks", squadName,
                                longer.get().tunnel());
                        return longer.get();
                    }
                }
                return state.stairs;
            }
            // Where the stairs can be cut all the way down: the objective's x and z can be beside
            // water - live, the camp's mine stopped at its second step by a pond. Nowhere there is
            // said once and looked for again a minute on: live, it was searched and said every second, 747 times, beside a flooded shaft.
            // The camp's mine, kept from before a restart, is cut on down wherever it was sited.
            // Its head on the camp's ground, not where a soldier stands: live, one standing down an
            // old shaft had a staircase sited from a step in it.
            // Off the camp - the keep's mine - its head on the column's own surface: the point man may be anywhere at dusk, up a hill or down a pit.
            // No surface read - the column not loaded - is no site yet, not the point man's height: live
            // (field334), with the squad down the old mine at y=-23, the new mine's head was sited there,
            // one step over its bottom, in the old one's tunnels.
            boolean atCamp = campGround != null && Math.hypot(campGround.x() - x, campGround.z() - z) <= 2 * Staircase.SITE_RADIUS;
            OptionalInt surfaceY = atCamp ? OptionalInt.of(campGround.y()) : groundNear(x, z);
            // A site on the ground says where, not how deep: down to the iron. Live, a miner's site at
            // the camp's height was taken for its depth, and three miners stood over one step.
            int asked = (int) Math.floor(o.location().y());
            int depth = surfaceY.isEmpty() || asked >= surfaceY.getAsInt() - 1 ? ChainOfCommand.MINE_DEPTH : asked;
            // Any mine ordered near the camp is the camp's: the platoon leader's point is a guess - live, it
            // moved the order to the camp's centre, 13 blocks from the mine cut to the bottom, and a
            // new staircase was started inside the wall and kept in its place.
            Optional<Staircase> kept = campStairs().filter(k ->
                    (Math.max(Math.abs(k.head().x() - x), Math.abs(k.head().z() - z)) <= Staircase.SITE_RADIUS
                            || nearCamp(x, z))
                            && k.bottom() == (k.well() ? Staircase.wellBottom(depth, k.head().y())
                            : Math.max(Staircase.DEEPEST, Math.min(depth, k.head().y() - 1))));
            // A mine off the camp is kept on disk by the x and z it was ordered at: after a restart it's
            // cut on down, not sited again over its own half-cut steps.
            if (kept.isEmpty() && !nearCamp(x, z)) {
                // Its own level first, kept by squad; else the mine's stairs, and a level of its own off them.
                Optional<Staircase> own = keptMine(levelKey(x, z, squadName));
                kept = own.isPresent() ? own : keptMine("mine" + x + "," + z).map(shared -> ownLevel(shared, squadName, x, z));
            }
            // One kept with its head far under the ground over it was sited from below - the camp's or
            // another - and is let go, every squad's level off it too. Read over the head itself: a
            // shaft is open over its head, a buried head has the rock over it. Live (field338), the
            // keep's mine moved near the camp and was kept as the camp's, its head at y=-23.
            // A dead well - worked out, blocked, a danger area - let go by every squad (live, field465, Delta's levels).
            if (kept.filter(k -> deadWells.contains(k.head())).isPresent()) {
                BlockPos dead = kept.get().head();
                LOG.warn("[{}] MINE: the staircase at {} is a dead well - let go, sited again", squadName, dead);
                squadStairs.entrySet().removeIf(e -> e.getValue().head().equals(dead));
                kept = Optional.empty();
            }
            if (kept.isPresent()) {
                BlockPos head = kept.get().head();
                Optional<BlockPos> over = TerrainSurveyor.surface(world, head.x(), head.z(), Keep.SKY_TOP);
                if (over.isPresent() && head.y() < over.get().y() - BURIED_HEAD) {
                    LOG.warn("[{}] MINE: the staircase kept on disk heads at {}, {} under the ground over it at y={} - let go, sited again",
                            squadName, head, over.get().y() - head.y(), over.get().y());
                    List<String> keys = new ArrayList<>(List.of("mine" + x + "," + z));
                    roster.squads().forEach(sq -> {
                        keys.add(levelKey(x, z, sq.name()));
                        keys.add(levelKey(x, z, sq.name() + " second"));
                    });
                    if (camp != null) {
                        keys.add(camp[0] + "," + camp[1]);
                    }
                    keys.forEach(k -> {
                        mineStore.save(k, List.of());
                        mineStore.save(mineKey(k, true), List.of());
                    });
                    if (campStairs != null && campStairs.filter(c -> c.head().equals(head)).isPresent()) {
                        campStairs = Optional.empty();
                    }
                    squadStairs.entrySet().removeIf(e -> e.getValue().head().equals(head));
                    kept = Optional.empty();
                }
            }
            if (kept.isPresent()) {
                LOG.info("[{}] MINE: the staircase kept on disk: down from {} to y={}", squadName, kept.get().head(), kept.get().bottom());
                state.stairsFor = new int[]{x, z};
                treeClaims.addAll(kept.get().walkway());
                return kept.get();
            }
            boolean failedHere = state.stairsFor != null && state.stairsFor[0] == x && state.stairsFor[1] == z;
            if (failedHere && clockwork.millis() < state.stairsRetryAt) {
                return null;
            }
            if (surfaceY.isEmpty()) {
                LOG.warn("[{}] MINE: no ground read at x={} z={} (loaded: {}) - no staircase sited there yet", squadName, x, z, world.isLoaded(x, z));
                state.stairsFor = new int[]{x, z};
                state.stairsRetryAt = clockwork.millis() + STAIRS_RETRY_MILLIS;
                return null;
            }
            int nearY = surfaceY.getAsInt();
            // To the standard: a 4x4 well centred in the chunk (Staircase.well; operator, 2026-11-15).
            Optional<Staircase> sited = Staircase.siteWell(world, x, z, nearY, depth, deadWells::contains);
            if (sited.isEmpty()) {
                if (!failedHere) {
                    LOG.warn("[{}] MINE: no chunk at or round x={} z={} a well can be cut down clear of water and lava - there itself, {}",
                            squadName, x, z, Staircase.whyNotWell(world, x, z, nearY, depth));
                    // With every chunk round it seen, a site no well can go down is as done as one dried up:
                    // the keep's mine moves on. Live (field354), the site at x=-154 was a hill over an open cave,
                    // and every squad would have been sent back to it each minute all night.
                    boolean seen = true;
                    for (int dx = -16; dx <= 16 && seen; dx += 16) {
                        for (int dz = -16; dz <= 16 && seen; dz += 16) {
                            seen = world.isLoaded(x + dx, z + dz);
                        }
                    }
                    if (seen) {
                        LOG.warn("[Outcome] no well can be sited at or round x={} z={} - SHORT", x, z);
                        chainOfCommand.onMineDriedUp(x, z).ifPresent(this::keepMineSite); // no well, no head: the site ordered
                    }
                }
                state.stairsFor = new int[]{x, z};
                state.stairsRetryAt = clockwork.millis() + STAIRS_RETRY_MILLIS;
                return null;
            }
            LOG.info("[{}] MINE: a staircase down from {} to y={}", squadName, sited.get().head(), sited.get().bottom());
            state.stairsFor = new int[]{x, z};
            Staircase level = sited.get();
            if (!nearCamp(x, z)) {
                mineStore.save(mineKey("mine" + x + "," + z, true), toKeep(sited.get()));
                level = ownLevel(sited.get(), squadName, x, z);
            }
            // Its stairs and tunnels are claimed: no table, furnace or other digger goes in them.
            treeClaims.addAll(level.walkway());
            return level;
        }).orElse(null);
        state.stairs = stairs;
        if (stairs != null) {
            state.lastStairs = stairs;
        }
        if (stairs == null) {
            squadStairs.remove(squadName);
        } else {
            squadStairs.put(squadName, stairs);
        }
        state.second = secondLevel(squadName, state, statuses);
        if (stairs == null) {
            nightBelow.remove(squadName);
        }
        if (stairs != null && camp != null && nearCamp(stairs.head().x(), stairs.head().z()) && !campStairs().equals(Optional.of(stairs))) {
            mineStore.save(mineKey(camp[0] + "," + camp[1], stairs.well()), toKeep(stairs));
            campStairs = Optional.of(stairs);
        }
        siteWorkshop(squadName, state, statuses, plan == null ? List.of()
                : Stream.of(plan.cells(), plan.floor(), plan.floor().stream().map(f -> new Position(f.x(), f.y() + 1, f.z())).toList())
                        .flatMap(List::stream).map(c -> BlockPos.containing(c.x(), c.y(), c.z())).toList());
        state.workshop = nextChestIfFull(state.workshop);
        // Who cuts it: the first two carrying a pickaxe, one to each side of its level's spine (the
        // rules of a mine: two soldiers to a level). Until one does, all make one.
        List<String> cutters = stairs == null || squadInContact ? List.of() : statuses.stream()
                .filter(st -> st.condition().flatMap(c -> c.kit()).map(Kit::pickaxe).orElse(false))
                .map(SoldierStatus::name).limit(state.second == null ? CUTTERS_A_LEVEL : 2L * CUTTERS_A_LEVEL).toList();
        Staircase secondLevel = state.second;
        String cutter = cutters.isEmpty() ? null : cutters.getFirst();
        // Securing: the torches still to put down round the objective, worked out again each cycle -
        // every torch put down, by anyone, changes what's left. Read once: the platoon leader's thread may
        // change the squad's task mid-cycle - live, a second read found SECURE gone and the squad loop died on it.
        Optional<Objective> secureTask = taskOf(squadName, Objective.Task.Secure.class);
        List<BlockPos> torchesLeft = secureTask.map(o -> {
            // Round the ground most of the camp stands on, as the wall and the foothold's measure are:
            // live, the ground found from the objective's height was a hut roof, and SECURE reported done with 371 cells of the camp still dark.
            // At the camp, the camp's own ground, as the foothold measures it: live, SECURE lit a disc
            // round the platoon leader's point, 3 blocks off the camp's centre and 2 above its ground, and
            // reported DONE for days with 8 of the camp's cells dark.
            if (campGround != null && Math.hypot(o.location().x() - campGround.x(), o.location().z() - campGround.z()) <= SECURE_RADIUS) {
                return torchPlan(campGround);
            }
            BlockPos at = typicalGround((int) Math.floor(o.location().x()),
                    (int) Math.floor(o.location().z()), (int) Math.floor(o.location().y()))
                    .orElse(TerrainSurveyor.groundAt(world, BlockPos.containing(o.location().x(), o.location().y(), o.location().z())));
            return torchPlan(at);
        }).orElse(List.of());
        // The camp kept lit whatever the task: the plan is worked out again each cycle from the
        // torches that stand, so one gone - washed away, broken, dug out - is put back.
        List<BlockPos> campDark = campGround == null ? List.of() : torchPlan(campGround);
        if (torchesLeft.size() != state.torchesLeft) {
            state.torchesChangedAt = clockwork.millis();
            if (secureTask.isPresent()) {
                LOG.info("[{}] SECURE: {} torches still to put down", squadName, torchesLeft.size());
            }
            state.torchesLeft = torchesLeft.size();
        }
        List<Position> patrolRound = taskOf(squadName, Objective.Task.Patrol.class).map(o -> patrolRound(o.location())).orElse(List.of());
        int baseBlocks = !baseOrdered ? 0 : state.baseBlocksLeft.orElse(PatrolBase.hasty(new Position(0, 0, 0), statuses.size()).cells().size());
        int stoneShare = (baseBlocks + statuses.size() - 1) / statuses.size();
        // One soldier is the closed wall's while it has dirt in it - the one carrying most stone,
        // then the nearest - off the camp's ground work, which outranks the mason and the quarry
        // for its hands and fills with the stone the wall wants. The rest keep the ground. Live,
        // the ground work, never done, held every soldier all day, and the wall's last dirt block stood for days.
        // Kept while it's here: chosen afresh each cycle it flipped between two with no stone, each
        // taken off the job unfinished as the other came nearer.
        boolean wallWanted = !soft.isEmpty() && wallLeft.isEmpty() && !squadInContact;
        if (!wallWanted) {
            state.wallHand = null;
        } else if (state.wallHand == null || statuses.stream().noneMatch(st -> st.name().equals(state.wallHand))) {
            state.wallHand = statuses.stream()
                    .max(Comparator.<SoldierStatus>comparingInt(st -> st.condition().flatMap(c -> c.kit()).map(Kit::stone).orElse(0))
                            .thenComparingDouble(st -> -Math.hypot(st.position().x() - soft.getFirst().x(), st.position().z() - soft.getFirst().z())))
                    .map(SoldierStatus::name).orElse(null);
        }
        String wallHand = state.wallHand;
        for (int i = 0; i < statuses.size(); i++) {
            String soldierName = statuses.get(i).name();
            // Stranded with nobody to fetch it: the recovery drill sends it down to its squad, over the stand its own routine wants and can't reach.
            Optional<Order.MoveTo> escape = squadInContact ? Optional.empty() : state.recovery.escape(soldierName);
            if (escape.isPresent()) {
                if (state.escaping.add(soldierName)) {
                    LOG.info("[{}] RECOVERY: {} has nobody to fetch it - goes down to its squad itself, at MEDIUM risk, to {}",
                            squadName, soldierName, new Position(escape.get().x(), escape.get().y(), escape.get().z()));
                }
                upkeeping.remove(soldierName);
                byName.get(soldierName).receiveOrder(escape.get());
                continue;
            }
            state.escaping.remove(soldierName);
            // Still down the mine by day: up its stairs first, to their head - the day's work is a long way
            // off and up, and from below no route there is found. Live (field348), Charlie came off the
            // night shift 50 down at the mine's landing, the keep 100 blocks off, its survey 1 of 81 cells
            // reachable, and its soldiers sent to wedge places in the rock round the point man.
            Staircase cameFrom = state.lastStairs;
            Position below = statuses.get(i).position();
            // Under the ground, not on the slope by the head below its height: live (field389-396), soldiers
            // on the ground falling away from the mine's head at y=96 were sent up to it at y=92, sent back down
            // to the day's work at y=94, and up again - 118 crowding alerts there in a day.
            if (state.stairs == null && cameFrom != null && !squadInContact && !state.recovery.recovering(soldierName)
                    && below.y() < cameFrom.head().y() - MINE_BELOW && underground(below)
                    && Math.hypot(below.x() - cameFrom.head().x(), below.z() - cameFrom.head().z()) <= CLIMB_OUT_NEAR) {
                Position head = centre(cameFrom.head());
                if (state.climbingOut.add(soldierName)) {
                    LOG.info("[{}] {} climbs out of the mine to its head at {} first", squadName, soldierName, cameFrom.head());
                }
                byName.get(soldierName).receiveOrder(new Order.MoveTo(head.x(), head.y(), head.z(), 1.5, 1.5, Risk.LOW));
                continue;
            }
            state.climbingOut.remove(soldierName);
            // A flatten builder's, the depot nearest it: its loads go in there, and there its chest is fitted.
            Workshop squadShop = soldiersWorkshop(workshopOf(state, statuses.get(i).position()), keepWorkshop, campWorkshop,
                    keepHands.contains(soldierName));
            Workshop mainDepot = keepWorkshop;
            Optional.ofNullable(squadShop != null && mainDepot != null && squadShop.stand().equals(mainDepot.stand()) && keepHands.contains(soldierName)
                            ? nearestDepot(statuses.get(i).position()) : squadShop)
                    .ifPresentOrElse(w -> workshops.put(soldierName, w), () -> workshops.remove(soldierName));
            // Its own unit's hand-over first, else one across squads.
            transfers.stream().filter(t -> t.giver().equals(soldierName)).findFirst()
                    .or(() -> Optional.ofNullable(acrossSquads.get(soldierName)))
                    .ifPresentOrElse(t -> deliveries.put(soldierName, t), () -> deliveries.remove(soldierName));
            // The basic load - a weapon up to a stone sword - is the soldier's own to keep, out of
            // contact; beyond it, the squad leader's leave. Live, 2 of 8 carried a stone sword or better for days, every rung waiting on "rearm".
            rearmAllowed.put(soldierName, !squadInContact);
            upgradeAllowed.put(soldierName, order.rearm() && !squadInContact && !baseOrdered);
            stoneSetAside.put(soldierName, stoneShare);
            Optional<Objective> gather = squadInContact
                    ? Optional.empty() : taskOf(squadName, Objective.Task.Gather.class);
            // Nothing left round the platoon leader's point: to where the squad saw some, while that order
            // stands - live, a squad sent to gather at a camp long since cleared stood reporting CANTCO.
            int nth = i;
            gather = gather.map(ordered -> {
                Map.Entry<Objective, Objective> sent =
                        gatherSent.get(soldierName);
                if (sent != null && sent.getKey().equals(ordered)) {
                    return sent.getValue();
                }
                gatherSent.remove(soldierName);
                Gatherer gatherer = gatherers.get(soldierName);
                if (gatherer == null || !gatherer.exhausted() || ordered.gather().isEmpty()) {
                    return ordered;
                }
                Position elsewhere = seenFor(squadName, nth, ordered.gather().get(), ordered.location());
                if (Math.hypot(elsewhere.x() - ordered.location().x(), elsewhere.z() - ordered.location().z()) <= GATHER_ELSEWHERE) {
                    return ordered;
                }
                Objective there = new Objective(ordered.mission(), elsewhere, ordered.intent(), ordered.task());
                LOG.info("[{}] {} finds nothing left to gather at {} - goes where the squad saw some, {}", squadName, soldierName,
                        ordered.location(), elsewhere);
                gatherSent.put(soldierName, Map.entry(ordered, there));
                return there;
            });
            gather = gather.filter(o -> !woodDuties.containsKey(soldierName)); // its duty's, not the squad's
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            // Feeding the squad is its own first priority of work (FM 4-0, field feeding): short of
            // food, half of it keeps the camp's farm by day whatever its task - live, the farm was
            // tended only under a FARM order, and the squads went hungry for hours with wheat to grow.
            // Half of it while the squad is RED or a soldier of it is hungry with nothing to eat; one
            // soldier - the first by roster - while it's merely short of rations (AMBER), so the farm
            // is kept and the rest work: live, two squads AMBER on rations alone, every soldier fed, had half of each at the farm and the keep stood.
            boolean mustFeed = statuses.stream().anyMatch(s -> s.condition()
                    .map(c -> Subsistence.mustFeed(c.food(), c.kit().map(Kit::rations).orElse(0))).orElse(false));
            // Not while the squad carries enough between them: a hand-over feeds the short one in
            // seconds, and the farm is a walk of minutes. Live, Bravo went AMBER on Dana's empty pack
            // with three squadmates carrying plenty; she walked 80 blocks to the farm, baked four
            // loaves for herself, was taken off it after 24 seconds and walked 80 back.
            boolean canShare = Subsistence.canShare(statuses);
            boolean hungrySquad = state.classOne != null && state.classOne.rating() == Subsistence.Rating.RED || mustFeed && !canShare;
            boolean shortSquad = state.classOne != null && state.classOne.rating() != Subsistence.Rating.GREEN && !canShare;
            boolean farmDay = !chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            // Every other soldier by its place in the squad's roster, not in this cycle's reports:
            // live, their order changed cycle to cycle, and a farmer was taken off its harvest every few seconds and never reached it.
            int rosterPlace = statuses.stream().map(SoldierStatus::name).sorted().toList().indexOf(soldierName);
            // As the platoon sergeant sets it, by the squad's Class I and the force's rations.
            PlatoonSergeant.Farmers feeding = PlatoonSergeant.farmers(hungrySquad, shortSquad, Optional.ofNullable(rationsSupply));
            boolean farmHalf = feeding == PlatoonSergeant.Farmers.HALF ? rosterPlace % 2 == 0 : feeding == PlatoonSergeant.Farmers.ONE && rosterPlace == 0;
            // Ordered to farm where there's no ground to: the camp's farm, saying why it's there. Live,
            // a farmer's CANTCO for a dry site went unanswered all day, and it stood there.
            Optional<Objective> farmOrdered = taskOf(squadName, Objective.Task.Farm.class);
            boolean siteDry = farmOrdered.isPresent() && farm == null;
            // A farmer sent to the camp's farm stays a stint: the walk there is the cost, and live one
            // was taken off it 24 seconds after arriving, the moment the squad's rating turned.
            Long since = farmSince.get(soldierName);
            boolean onStint = since != null && clockwork.millis() - since < FARM_STINT_MILLIS && farmDay && !squadInContact;
            // With a farm squad named, it keeps the camp's farm by day, every one of it, and no other
            // squad farms but under an order to (operator, 2026-11-15): live, half of each hungry squad
            // went to the farm by the field-feeding rule, and the keep's crew was a third off it.
            Optional<String> farmSquad = chainOfCommand.farmSquad();
            boolean theFarmSquad = farmSquad.filter(squadName::equals).isPresent();
            FarmPlan kept = farm != null ? farm
                    : siteDry && campFarm != null ? campFarm
                    : theFarmSquad ? (farmDay ? campFarm : null)
                    : farmSquad.isEmpty() && (feeding != PlatoonSergeant.Farmers.NONE && farmDay && farmHalf || onStint) ? campFarm : null;
            if (kept != null && !squadInContact) {
                if (kept == campFarm && farm == null) {
                    farmSince.putIfAbsent(soldierName, clockwork.millis());
                }
                farming.put(soldierName, kept);
            } else {
                farming.remove(soldierName);
                farmSince.remove(soldierName);
            }
            if (siteDry) {
                Position farmSite = farmOrdered.get().location();
                String dry = String.format(Locale.ROOT, "no ground to farm by water within %d blocks of (%d, %d, %d)",
                        FARM_SITE_RADIUS, (int) Math.floor(farmSite.x()), (int) Math.floor(farmSite.y()), (int) Math.floor(farmSite.z()));
                // Said only with no camp's farm to keep instead: live, a farmer keeping the camp's
                // farm, pulled off it to fight, reported its dry site as CANTCO while in contact.
                if (campFarm != null) {
                    if (farmlessLogged.add(soldierName)) {
                        LOG.info("[{}] {}: {} - keeps the camp's farm", squadName, soldierName, dry);
                    }
                    farmless.remove(soldierName);
                } else {
                    farmless.put(soldierName, dry);
                }
            } else {
                farmless.remove(soldierName);
                farmlessLogged.remove(soldierName);
            }
            long now = clockwork.millis();
            // The recovery drill's order - its own stair or cut, or a squadmate's - over its ground work:
            // live, a builder in a dead end at the mine's bottom kept its strip's dig over the stair out.
            if (state.recovery.recovering(soldierName)) {
                recovering.add(soldierName);
            } else {
                recovering.remove(soldierName);
            }
            Order work = state.recovery.ordered(soldierName) ? null : ground.get(soldierName);
            if (work != null && !state.relieved(soldierName, keepHands.contains(soldierName) ? keepTask(work) : groundTask(work), now)) {
                // The task's now, not the upkeep's: left the upkeep's, the upkeep would drop it again
                // on seeing the wall - in the squad simulation, every soldier on and off its dig.
                upkeeping.remove(soldierName);
                route(soldierName, work);
            } else if (!upkeeping.contains(soldierName)) {
                route(soldierName, null); // camp upkeep's, if it's on that, is the upkeep's to drop
            }
            if (!soft.isEmpty() && !squadInContact && !state.relieved(soldierName, "wall the camp in stone", now) && !policeCall.on(soldierName)) {
                repointing.put(soldierName, soft);
                wallCentres.put(soldierName, state.wallCentre);
                // No cobblestone to repoint with: from the stockpile, when it was last seen holding
                // some, or quarried - as a mason short of blocks for a wall going up digs dirt. Live,
                // the camp's wall's last dirt block stood a day, its masons carrying none and nothing
                // sending them for it. Not while it's still walling: the open wall comes first.
                if (soldierName.equals(wallHand)) {
                    route(soldierName, null);
                    upkeeping.remove(soldierName);
                }
                Kit pack = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
                if (pack != null && pack.stone() == 0 && wallLeft.isEmpty()) {
                    boolean stocked = chainOfCommand.stockpile().map(st -> st.stone() > 0).orElse(false) && stockpileFor(soldierName).isPresent();
                    if (stocked) {
                        drawingStone.add(soldierName);
                    } else {
                        drawingStone.remove(soldierName);
                        Position centre = new Position(state.wallCentre.x(), state.wallCentre.y(), state.wallCentre.z());
                        gathering.put(soldierName, new Objective("Quarry cobblestone to wall the camp in stone",
                                seenFor(squadName, i, Resource.STONE, centre), "The wall's dirt replaced with stone",
                                new Objective.Task.Gather(Resource.STONE)));
                    }
                }
            } else {
                repointing.remove(soldierName);
            }
            if (!wallLeft.isEmpty() && !squadInContact && !state.relieved(soldierName, "wall the camp", now)) {
                walling.put(soldierName, wallLeft);
                wallCentres.put(soldierName, state.wallCentre);
                Kit bricks = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
                // Nearly out counts as out: a soldier down to its last block or two otherwise neither
                // digs more nor has enough to work with - live, one with a single dirt stood about.
                // Once it's digging, it digs a load: a mason that turned back at its fourth block
                // spent the day walking between the pit and the wall, in the squad simulation.
                int carriedBlocks = bricks == null ? 0 : bricks.stone() + bricks.dirt();
                if (carriedBlocks < WALL_BLOCKS_LOW) {
                    diggingForWall.add(soldierName);
                } else if (carriedBlocks >= WALL_BLOCKS_LOAD) {
                    diggingForWall.remove(soldierName);
                }
                // The stockpile first, when it was last seen holding a load: one squad's quarrying is another's wall.
                boolean stocked = chainOfCommand.stockpile().map(st -> st.stone() >= WALL_BLOCKS_LOAD).orElse(false)
                        && stockpileFor(soldierName).isPresent();
                if (stocked && diggingForWall.contains(soldierName)) {
                    drawingStone.add(soldierName);
                } else {
                    drawingStone.remove(soldierName);
                }
                if (bricks != null && diggingForWall.contains(soldierName) && !stocked) {
                    // Nothing to build with: dirt, dug outside the wall so the ground inside stays whole -
                    // each mason on its own side, so one pit isn't dug out under all of them.
                    int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    int[] side = sides[i % sides.length];
                    gathering.put(soldierName, new Objective("Dig dirt for the wall",
                            new Position(state.wallCentre.x() + side[0] * (WALL_RADIUS + SOIL_OFFSET), state.wallCentre.y(),
                                    state.wallCentre.z() + side[1] * (WALL_RADIUS + SOIL_OFFSET)),
                            "Blocks to wall the camp in", new Objective.Task.Gather(Resource.SOIL)));
                }
            } else {
                walling.remove(soldierName);
            }
            boolean night = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            if (!night && !squadInContact && building == null) {
                huntAllowed.add(soldierName);
                huntGrounds.put(soldierName, objectiveOf(squadName).map(Objective::location)
                        .orElse(statuses.get(i).position()));
            } else {
                huntAllowed.remove(soldierName);
            }
            // The keep's planter plants the keep's grove, whatever its squad's own plot: live, planters
            // sent to a squad's plot 40 blocks up a hill failed the walk one after another.
            // A cutter's stump, felled: its own one-cell plot, planted before its next tree.
            BlockPos felledAt = keepStumps.get(soldierName);
            // A wood squad's planter: the forest's ground the felling has opened, on the grid (command.WoodDuties).
            boolean replants = Optional.ofNullable(woodDuties.get(soldierName)).filter(d -> d.kind() == WoodDuties.Kind.PLANTER).isPresent();
            TreeFarm grove = felledAt != null ? new TreeFarm(felledAt, List.of(felledAt))
                    : replants ? objectiveOf(squadName).flatMap(o -> TreeFarm.site(world, BlockPos.containing(o.location().x(),
                            o.location().y(), o.location().z()), WoodDuties.REACH)).orElse(null)
                    : keepParty.planter().filter(soldierName::equals).isPresent() && keepGrove != null ? keepGrove : trees;
            if (grove != null && !squadInContact) {
                planting.put(soldierName, grove);
            } else {
                planting.remove(soldierName);
            }
            if (!torchesLeft.isEmpty() && !squadInContact && !state.relieved(soldierName, "light the ground", now)) {
                lighting.put(soldierName, torchesLeft);
                // Out of torches and short of their makings: it gathers them near the objective -
                // logs, then cobblestone for the furnace - rather than reporting the shortage forever.
                Kit makings = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
                if (makings != null && torchesCarried.getOrDefault(soldierName, 0) == 0) {
                    Objective secure = secureTask.orElseThrow();
                    Optional<Resource> missing = makings.logs() < TORCH_LOGS
                            ? Optional.of(Resource.WOOD)
                            : makings.stone() < TORCH_STONE && !furnaces.containsValue(soldierName)
                            ? Optional.of(Resource.STONE) : Optional.empty();
                    // Where the squad last saw some: often not within a gatherer's reach of the camp - stone
                    // on level ground by the water lies under the soil.
                    missing.ifPresent(r -> gathering.put(soldierName, new Objective(
                            "Gather the makings of torches", seenFor(squadName, nth, r, secure.location()), secure.intent(),
                            new Objective.Task.Gather(r))));
                }
            } else {
                lighting.remove(soldierName);
            }
            // Upkeep: a soldier at the camp with torches in its pack and nothing else to do puts one
            // down wherever the camp's ground has gone dark, by day. Not one with other work: live, a
            // farmer carrying torches was handed the camp's last dark cell each time it came within 24
            // blocks of the camp and its farm again as it walked out, and finished neither - 281
            // taskings in half an hour ended unfinished within 30 s.
            boolean daylight = !chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            // A gather that's found nothing isn't work, as for the camp's upkeep: live, two soldiers
            // stood a day in a dark camp with 7 and 13 torches, each on a wood gather with none left.
            boolean gatherWorking = gathering.containsKey(soldierName)
                    && now - gatherFoundNothing.getOrDefault(soldierName, Long.MIN_VALUE / 2) > FOUND_NOTHING_MILLIS;
            boolean otherWork = farming.containsKey(soldierName) || walling.containsKey(soldierName)
                    || repointing.containsKey(soldierName) || planting.containsKey(soldierName) || gatherWorking;
            if (!lighting.containsKey(soldierName) && !otherWork && !campDark.isEmpty() && !squadInContact && daylight
                    && torchesCarried.getOrDefault(soldierName, 0) > 0 && campGround != null
                    && Math.hypot(statuses.get(i).position().x() - campGround.x(), statuses.get(i).position().z() - campGround.z()) <= SECURE_RADIUS * 2
                    && Math.abs(statuses.get(i).position().y() - campGround.y()) <= CAMP_VERTICAL) {
                lighting.put(soldierName, campDark);
            }
            // The mine's stairs lit as they're cut, a torch a turn: by the squad holding the top, not
            // the cutter. Live, the dark shaft spawned the zombies that killed a soldier at dawn.
            // Only by one with torches, or their makings and a furnace's stone: lighting isn't a
            // reason to leave the shaft for a workshop and come back with nothing.
            Kit lamp = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
            boolean canLight = torchesCarried.getOrDefault(soldierName, 0) > 0
                    || canMakeTorches.getOrDefault(soldierName, false) && lamp != null && lamp.stone() >= TORCH_STONE;
            if (stairs != null && !squadInContact && torchesLeft.isEmpty() && !cutters.contains(soldierName) && canLight) {
                List<BlockPos> stairsDark = stairs.lights().stream()
                        .filter(c -> !world.isSolid(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z()) && !world.isTorch(c.x(), c.y(), c.z()))
                        .toList();
                if (stairsDark.isEmpty()) {
                    lighting.remove(soldierName);
                } else {
                    lighting.put(soldierName, stairsDark);
                }
            } else if (stairs != null && torchesLeft.isEmpty()) {
                // Out of torches and their makings, the stairs' lights aren't its job any more: live,
                // one at the landing reported CANTCO for half an hour on lights handed it while it had torches, never taken back.
                lighting.remove(soldierName);
            }
            if (stairs != null) {
                mining.add(soldierName);
            } else {
                mining.remove(soldierName);
            }
            // The mine's orders stand over the recovery drill's for a mining squad: the drill reads miners in
            // their tunnels as stranded, and live (field408), with its order outranking the mine's, Hal was
            // given a stair up 26 times in a night, three crews stood empty and the night took no ore
            // (backlog/mining.md, the shaft's bottom reads as a pocket; backlog/long-routes.md, Eli).
            if (stairs != null && cutters.contains(soldierName)) {
                cutting.put(soldierName, cutters.indexOf(soldierName) < CUTTERS_A_LEVEL || secondLevel == null ? stairs : secondLevel);
            } else {
                cutting.remove(soldierName);
                stairsProgress.remove(soldierName);
            }
            if (stairs != null && !squadInContact) {
                // No hut on a mining squad's hands: the branch below never reaches where a patrol base's
                // plan is let go, and live, a squad sent from its night's base to the mine went on
                // building the base's doorway at the stairs' head, reporting Fortifying.
                fortifying.remove(soldierName);
                // The cutter stands where the stairs need it; the rest hold the top, clear of the shaft -
                // by day. By night they go down behind it: the top is the dark, and down the stairs nothing spawns within 24 blocks of them.
                boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
                if (dark) {
                    nightBelow.put(squadName, stairs.head().y());
                } else {
                    nightBelow.remove(squadName);
                }
                // With nothing left to cut, by night the squad waits at the landing, under the
                // ground: live, a mine cut to the bottom left it all night at the head, under the sky.
                Position rest = dark && stairs.landingCut(world) ? centre(stairs.landingStand()) : centre(stairs.head());
                // A load's walk first, while the depositor has the hands - a stand by its chest: live, a
                // miner's every trip to the stockpile timed out where it stood, its order the miner's stand.
                boolean isCutter = cutters.contains(soldierName);
                String own = isCutter ? soldierName : cutter;
                Position cutterAt = own == null ? rest
                        : depositTo(own, statuses).or(() -> armourers.get(own).standAt()).or(() -> miners.get(own).standAt()).orElse(rest);
                // No pickaxe, no mine (operator, 2026-11-17: we can't go into the mines without a pickaxe -
                // be prepared to mine): by night it stays at the head, off the stairs, making one if it
                // can. Live (field364), Fran, Jack and Pete with no pickaxe and the farm squad's Mia and Ned
                // stood the night on the stairs between y=1 and y=11, the cutters queued behind them, and the night's ore was 0 of 16.
                boolean staysUp = dark && !isCutter
                        && !statuses.get(i).condition().flatMap(c -> c.kit()).map(Kit::pickaxe).orElse(false);
                // One holding the top goes on its errand first: its kit at the workshop, torches made, the stairs lit.
                // Not by night, but for one staying up: the top is the dark, and the squad stays down with its cutter.
                Optional<Position> errand = isCutter || dark && !staysUp ? Optional.empty()
                        : depositTo(soldierName, statuses).or(() -> Optional.ofNullable(armourers.get(soldierName)).flatMap(Armourer::standAt))
                        .or(() -> Optional.ofNullable(torchmakers.get(soldierName)).flatMap(Torchmaker::standAt))
                        .or(() -> Optional.ofNullable(lamplighters.get(soldierName)).flatMap(Lamplighter::standAt));
                if (errand.isPresent()) {
                    byName.get(soldierName).receiveOrder(new Order.MoveTo(errand.get().x(), errand.get().y(), errand.get().z(), 0.3, 0.5, Risk.LOW));
                    continue;
                }
                // By night each a step of its own down the stairs, every squad's on the mine in a line by
                // name - not on its cutter's cell: live, ten soldiers of four squads stood in one cell on
                // the stairs and moved as one (operator, 2026-11-15: everyone needs their space bubble).
                Position place = isCutter ? cutterAt
                        : dark && !staysUp ? nightPlace(stairs, soldierName).orElse(cutterAt)
                        : new Position(stairs.head().x() - 1.5 + i, stairs.head().y(), stairs.head().z() + 3.5);
                // Exactly where a cut wants it - or the head, where the next cut starts; but waiting the
                // night out at the landing, as loosely as the rest: live, the landing's stand lost its
                // floor to the ore under it, and the cutter, held to a spot it couldn't stand in, reported itself stranded a block below.
                boolean cutting = isCutter && !(dark && cutterAt.equals(rest) && !rest.equals(centre(stairs.head())));
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(),
                        // Its own step by night, held to it: a body's width, so two don't settle in one cell.
                        cutting ? 0.3 : dark && !staysUp ? (isCutter ? MINE_FOLLOW : 0.6) : 1.5, cutting ? 0.5 : dark && !staysUp ? MINE_FOLLOW : 4, Risk.LOW));
                continue;
            }
            Order given = eased.get(i);
            // On patrol: round the objective's ground, waypoint to waypoint, each soldier at its own
            // point of the round - moving, on watch. Contact takes over as it always does.
            if (!patrolRound.isEmpty() && !squadInContact && !(given instanceof Order.Place)) {
                int leg = patrolLegs.computeIfAbsent(soldierName, n -> nth * patrolRound.size() / statuses.size());
                Position waypoint = patrolRound.get(Math.floorMod(leg, patrolRound.size()));
                Position at = statuses.get(i).position();
                if (Math.hypot(at.x() - waypoint.x(), at.z() - waypoint.z()) < PATROL_REACHED) {
                    leg++;
                    patrolLegs.put(soldierName, leg);
                    waypoint = patrolRound.get(Math.floorMod(leg, patrolRound.size()));
                }
                byName.get(soldierName).receiveOrder(new Order.MoveTo(waypoint.x(), waypoint.y(), waypoint.z(), 1.0, 3, Risk.LOW));
                continue;
            }
            patrolLegs.remove(soldierName);
            if (given instanceof Order.Dig dig) {
                // The recovery drill's stair up: dug as ground work is, its own and nobody's upkeep.
                upkeeping.remove(soldierName);
                route(soldierName, dig);
                // The routine with the hands says where to stand, as below: a load to put in, a table to
                // make kit at, ore to fetch. A chest's stand, if none is found to walk to, its depot's
                // stand - never the chest itself: live, soldiers sent onto the lid stood on it, and the
                // load didn't go in. Live, every flatten builder's walk to a chest was steered to
                // its clearer's stand instead - "last seen 32 blocks off" when the walk timed out - and not one load went in.
                Hands.Routine holding = handsHeld.get(soldierName);
                Position here = statuses.get(i).position();
                Optional<Position> routine = holding == null || holding == Hands.Routine.CLEARER ? Optional.empty()
                        : standOf(soldierName, holding).map(p -> holding == Hands.Routine.DEPOSITOR
                        ? chestStand(here, p).or(() -> Optional.ofNullable(workshops.get(soldierName))
                                .filter(w -> w.chest().filter(c -> c.x() == (int) Math.floor(p.x()) && c.z() == (int) Math.floor(p.z())).isPresent())
                                .map(w -> new Position(w.stand().x() + 0.5, w.stand().y(), w.stand().z() + 0.5))).orElse(p) : p);
                if (routine.isPresent()) {
                    byName.get(soldierName).receiveOrder(new Order.MoveTo(routine.get().x(), routine.get().y(), routine.get().z(), 0.3, 0.5, Risk.LOW));
                    continue;
                }
                // A stair up starts at its feet: until its clearer has a stand it holds where it is, and says
                // why once - live (field393-400), Eli's 304-block stair up was laid four times and he was sent each
                // time to the dig's top corner, 107 up through the rock, "no route", and stood.
                if (dig.tunnel() && clearers.get(soldierName).standAt().isEmpty()) {
                    if (stairWaitSaid.add(soldierName)) {
                        List<BlockPos> cells = clearing.getOrDefault(soldierName, List.of());
                        LOG.warn("[{}] {} has its stair up but its clearer no stand yet - holds at {}: {} cell(s) to cut, the first {}, hands {}, clearer {}",
                                squadName, soldierName, here, cells.size(), cells.stream().limit(3).toList(),
                                holding == null ? "free" : holding, clearers.get(soldierName).stuck().orElse("not stuck"));
                    }
                    byName.get(soldierName).receiveOrder(new Order.MoveTo(here.x(), here.y(), here.z(), 3, 3, Risk.LOW));
                    continue;
                }
                stairWaitSaid.remove(soldierName);
                // Where the clearer stands for its next cut; until it has one, toward the dig's corner.
                Position at = clearers.get(soldierName).standAt().orElse(new Position(dig.box().minX() + 0.5, dig.box().maxY() + 1,
                        dig.box().minZ() + 0.5));
                byName.get(soldierName).receiveOrder(new Order.MoveTo(at.x(), at.y(), at.z(), clearers.get(soldierName).standAt().isPresent() ? 0.3 : 3,
                        clearers.get(soldierName).standAt().isPresent() ? 0.5 : 3, Risk.LOW));
                continue;
            }
            if (given instanceof Order.Place place) {
                // Concrete work: the soldier builds the cells, standing where the building says.
                List<BlockPos> cells = place.cells().stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList();
                if (!cells.equals(placing.get(soldierName))) {
                    placeProgress.remove(soldierName);
                    placing.put(soldierName, cells);
                }
                Position at = placers.get(soldierName).standAt().orElse(null);
                byName.get(soldierName).receiveOrder(at == null ? new Order.Idle()
                        : new Order.MoveTo(at.x(), at.y(), at.z(), 0.3, 0.5, Risk.LOW));
                continue;
            }
            placing.remove(soldierName);
            placeProgress.remove(soldierName);
            if (building != null && soldierName.equals(torchCutter)) {
                fortifying.remove(soldierName);
                Position place = opening.get(soldierName).stand();
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(), HUT_PLACE_TOLERANCE, 0.5, Risk.LOW));
                continue;
            }
            // Short of its share of the hut's blocks: dirt makes up the rest - a squad with nothing to build with still
            // shelters by night - from the camp's ground that has to go anyway, or else dug beside the site.
            Kit carried = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
            // And one that runs out mid-build, with the hut still open, goes out for more: an open
            // hut shelters nobody, and waiting inside it for blocks that won't come is a night lost.
            int carriedBlocks = carried == null ? 0 : carried.stone() + carried.dirt() + carried.logs();
            if (building != null && carried != null && !squadInContact && baseBlocks > 0
                    && (allInside ? carriedBlocks == 0 : carriedBlocks < stoneShare)) {
                Position hutPlace = building.stands().get(i);
                Position dirtPlace = CutFirst.near(campCut, hutPlace).orElseGet(() -> new Position(hutPlace.x() + SOIL_OFFSET, hutPlace.y(), hutPlace.z()));
                gathering.put(soldierName, new Objective("Dig dirt for the patrol base",
                        dirtPlace, "Blocks enough to wall the squad in",
                        new Objective.Task.Gather(Resource.SOIL)));
                fortifying.remove(soldierName);
                Position dig = gatherers.get(soldierName).standAt().orElse(dirtPlace);
                byName.get(soldierName).receiveOrder(new Order.MoveTo(dig.x(), dig.y(), dig.z(), 0.3, 2, Risk.LOW));
                continue;
            }
            if (building != null) {
                if (!soldierName.equals(opener)) {
                    opening.remove(soldierName);
                }
                List<BlockPos> cells = new ArrayList<>(
                        Stream.concat(building.cells().stream(), breaches.stream().filter(b -> !building.cells().contains(b)))
                        .filter(c -> !leftOpen.contains(c))
                        .map(c -> new BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList());
                // Each soldier starts at a different point round the walls, so they don't all go for the same block.
                Collections.rotate(cells, -i * cells.size() / statuses.size());
                boolean makeTorches = soldierName.equals(torchbearer) && workshop != null;
                Position place = makeTorches ? workshop : building.stands().get(i);
                fortifying.put(soldierName, new Fortify(place, List.copyOf(cells), allInside, makeTorches, soldierName.equals(lighter)));
                // Rearming while it waits: it goes where its sword-making takes it, then back to its place.
                Optional<Position> rearming = Optional.ofNullable(armourers.get(soldierName)).flatMap(Armourer::standAt);
                byName.get(soldierName).receiveOrder(rearming
                        .map(r -> (Order) new Order.MoveTo(r.x(), r.y(), r.z(), 0.3, 0.5, Risk.LOW))
                        .orElse(new Order.MoveTo(place.x(), place.y(), place.z(), HUT_PLACE_TOLERANCE, 0.5, Risk.LOW)));
                continue;
            }
            fortifying.remove(soldierName);
            if (soldierName.equals(opener)) {
                Position place = opening.get(soldierName).stand();
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(), HUT_PLACE_TOLERANCE, 0.5, Risk.LOW));
                continue;
            }
            opening.remove(soldierName);
            Armourer armourer = armourers.get(soldierName);
            Gatherer gatherer = gatherers.get(soldierName);
            // Food first: a hungry soldier chasing an animal is wherever the animal is.
            // The routine with the hands says where to stand: live, a mason under way and an armourer
            // walking to a workshop each sent the soldier its own way, and it reached neither.
            Optional<Position> stand = Optional.ofNullable(handsHeld.get(soldierName)).flatMap(h -> standOf(soldierName, h));
            // Walking up to a squadmate, or onto what it threw: its height is where it is, mid-jump or
            // on a step, not ground to stand at - held to half a block of it, the giver built a step
            // to reach it. Live, givers on the keep's finished floor put down step after step for a
            // receiver's y=65.32, and the builders dug each out as the site's highest.
            boolean toSomeone = handsHeld.get(soldierName) == Hands.Routine.HANDOVER || handsHeld.get(soldierName) == Hands.Routine.HUNTER;
            if (stand.isEmpty()) {
                stand = Optional.ofNullable(hunters.get(soldierName)).flatMap(Hunter::standAt);
            }
            if (stand.isEmpty() && armourer != null) {
                stand = armourer.standAt();
            }
            if (stand.isEmpty() && torchmakers.containsKey(soldierName)) {
                stand = torchmakers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && depositors.containsKey(soldierName)) {
                SoldierStatus self = statuses.get(i);
                Depositor own = depositors.get(soldierName);
                stand = own.dumping() ? own.standAt() : own.standAt().map(chest -> chestStand(self.position(), chest).orElseGet(() -> noStandFor(chest)));
            }
            if (stand.isEmpty()) {
                stand = storeWalks.getOrDefault(soldierName, Optional.empty());
            }
            if (stand.isEmpty() && handovers.containsKey(soldierName)) {
                stand = handovers.get(soldierName).standAt();
                toSomeone |= stand.isPresent();
            }
            if (stand.isEmpty() && scavengers.containsKey(soldierName)) {
                stand = scavengers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && receiving.contains(soldierName)) {
                // Walk onto what's been thrown: the server hands a dropped stack to whoever touches it.
                stand = entityTracker.nearbyItems(statuses.get(i).position(), PICKUP_RADIUS).stream().findFirst();
                toSomeone |= stand.isPresent();
            }
            // A mason stands as close to its place's centre as a hut builder: a body off centre leans
            // into the wall cell beside it, and the server won't place a block where a body is.
            double tolerance = 0.3;
            if (stand.isEmpty() && clearers.containsKey(soldierName)) {
                stand = clearers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && masons.containsKey(soldierName)) {
                stand = masons.get(soldierName).standAt();
                tolerance = stand.isPresent() ? HUT_PLACE_TOLERANCE : tolerance;
            }
            if (stand.isEmpty() && lamplighters.containsKey(soldierName)) {
                stand = lamplighters.get(soldierName).standAt();
            }
            if (stand.isEmpty() && foresters.containsKey(soldierName)) {
                stand = foresters.get(soldierName).standAt();
            }
            if (stand.isEmpty() && farmers.containsKey(soldierName)) {
                stand = farmers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && gatherer != null) {
                stand = gatherer.standAt();
            }
            if (stand.isPresent()) {
                given = new Order.MoveTo(stand.get().x(), stand.get().y(), stand.get().z(), tolerance,
                        toSomeone ? TO_SOMEONE_VERTICAL : 0.5, Risk.LOW);
            }
            byName.get(soldierName).receiveOrder(given);
        }
    }

    /**
     * Where a soldier at {@code from} stands to reach the chest at {@code chest}: the nearest cell
     * within reach of it that the soldier can stand in and get to - on foot, on its blocks or
     * cutting steps. Not the chest's own cell, which nobody stands in: live, a chest walled on
     * three sides left every trip to it short of reach, and the day's loads in the soldiers' packs.
     */
    /** Where {@code soldier} stands for a load it carries to a chest, while its depositor has its hands. */
    private Optional<Position> depositTo(String soldier, List<SoldierStatus> statuses) {
        if (handsHeld.get(soldier) != Hands.Routine.DEPOSITOR || !depositors.containsKey(soldier)) {
            return Optional.empty();
        }
        Optional<Position> here = statuses.stream().filter(s -> s.name().equals(soldier)).findFirst().map(SoldierStatus::position);
        Depositor own = depositors.get(soldier);
        return own.dumping() ? own.standAt() : own.standAt().map(chest -> here.flatMap(h -> chestStand(h, chest)).orElseGet(() -> noStandFor(chest)));
    }

    /**
     * Where to make for, for a chest no stand was found for from where the soldier is: a mine's
     * chest's landing - the stand its store is reached from - else the chest's own cell. Never into a
     * mine's chest: live (field319), a soldier on the stairs above a mine's stash was ordered into
     * the stash's own cell, "no route", and stood.
     */
    private Position noStandFor(Position chest) {
        return mineOfChest(chest).map(m -> centre(m.landingStand())).orElse(chest);
    }

    private Optional<Position> chestStand(Position from, Position chest) {
        BlockPos feet = BlockPos.containing(from.x(), from.y(), from.z());
        int cx = (int) Math.floor(chest.x()), cy = (int) Math.floor(chest.y()), cz = (int) Math.floor(chest.z());
        int span = Math.max(Math.abs(cx - feet.x()), Math.abs(cz - feet.z())) + CHEST_STAND_ACROSS + 2;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, CHEST_STAND_BUDGET, 0, false).keySet();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -CHEST_STAND_ACROSS; dx <= CHEST_STAND_ACROSS; dx++) {
            for (int dz = -CHEST_STAND_ACROSS; dz <= CHEST_STAND_ACROSS; dz++) {
                // No lower than a step under the chest: a stand two down is a pit reached by a drop with no
                // walk out of it. Live, three soldiers of three squads were sent into one by the camp's
                // chest, taken for shut in, and cut their way out sideways under the camp.
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = new BlockPos(cx + dx, cy + dy, cz + dz);
                    Position centre = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    // Not on a chest: one is less than a block high, so a stand on it is never quite
                    // reached, and a body on it shuts its lid. Live, the farm squad's soldier was sent
                    // onto the chest beside the camp's stockpile, failed five walks to it in a row, and stood with its load.
                    // In reach wherever in the stand the walk stops - 0.3 of its centre - not only from its
                    // centre: live, a walk ended 3.5 blocks off, a hair past reach, and the load wasn't put in.
                    boolean reachedFromAll = Depositor.withinReach(new Position(centre.x() - 0.3, centre.y(), centre.z() - 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() + 0.3, centre.y(), centre.z() - 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() - 0.3, centre.y(), centre.z() + 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() + 0.3, centre.y(), centre.z() + 0.3), chest);
                    if (!reachable.contains(cell) || !reachedFromAll || world.isChest(cell.x(), cell.y() - 1, cell.z())) {
                        continue;
                    }
                    double distance = Math.hypot(centre.x() - from.x(), centre.z() - from.z()) + Math.abs(centre.y() - from.y());
                    if (distance < bestDistance) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best).map(c -> new Position(c.x() + 0.5, c.y(), c.z() + 0.5));
    }

    /** How far across from a chest a stand to reach it is looked for, and how far the flood for it goes. */
    static final int CHEST_STAND_ACROSS = 3, CHEST_STAND_BUDGET = 20_000;

    /**
     * Whether the wall's cell {@code c}, round a wall centred at ({@code cx}, {@code cz}), is dirt
     * that wants to be stone: dirt or grass, and not buried - covered over and with the camp's
     * ground filled in against it on the inside. A buried block, taken out, opens no way in, and
     * nobody can stand to reach it: live, the camp's levelling buried the wall's bottom course
     * where it ran through a dip, and the camp's wall stood open on that one block for days.
     */
    private boolean softInWall(BlockPos c, int cx, int cz, Set<BlockPos> wall) {
        if (!world.isSoil(c.x(), c.y(), c.z())) {
            return false;
        }
        if (!world.isSolid(c.x(), c.y() + 1, c.z())) {
            return true;
        }
        // Inside: each side nearer the centre that isn't the wall itself - at a corner, two.
        double here = Math.hypot(c.x() - cx, c.z() - cz);
        int insideSides = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos side = c.offset(d[0], 0, d[1]);
            if (wall.contains(side) || Math.hypot(side.x() - cx, side.z() - cz) >= here) {
                continue;
            }
            insideSides++;
            // Ground, risen over it - not a table or a step put down beside the wall.
            if (!world.isSolid(side.x(), side.y(), side.z()) || !world.isSolid(side.x(), side.y() + 1, side.z())) {
                return true;
            }
        }
        return insideSides == 0;
    }

    /** The block-state IDs the world model holds at {@code to}'s cell and the two over it, "?" for none known. */
    private String modelBlocks(Order.MoveTo to) {
        int x = (int) Math.floor(to.x()), y = (int) Math.floor(to.y()), z = (int) Math.floor(to.z());
        List<String> blocks = IntStream.rangeClosed(-1, 1).mapToObj(dy -> chunkCache.describeAt(x, y + dy, z)).toList();
        // Its feet and head cells, where something not air stops a body.
        blocks.subList(1, 3).stream().filter(b -> !b.startsWith("air ") && !b.startsWith("cave_air ") && !b.equals("unknown"))
                .forEach(b -> blocksThatStopped("a stall", b));
        return String.join(" / ", blocks) + " at " + x + " " + (y - 1) + ".." + (y + 1) + " " + z;
    }

    /** How often each kind of block has been where a soldier was refused or stalled, by name and class. */
    private final Map<String, Integer> stoppers = new ConcurrentHashMap<>();

    /**
     * Counts {@code block} as what stopped a soldier ({@code how}), said at its first, tenth and
     * hundredth: the next round of fixes reads them (operator, 2026-11-16: an unknown type, detected
     * and added to the next round). Live, wildflowers - not solid, not replaceable - refused every
     * step put into them, and probing the server couldn't name them.
     */
    private void blocksThatStopped(String how, String block) {
        int seen = stoppers.merge(block, 1, Integer::sum);
        if (seen == 1 || seen == 10 || seen == 100) {
            LOG.warn("[Blocks] {} at a {} - {} time(s) so far", how, block, seen);
        }
    }

    /** {@code task} guarded by {@link #faults}: a fault on one tick is logged and counted, and the next tick runs. */
    private Runnable loudly(String what, Runnable task) {
        return faults.guard(what, task);
    }

    /**
     * Where the squad's {@code soldier}th soldier goes for {@code resource}: one of the places the
     * squad last saw some, not much further from {@code from} than the nearest - each soldier, and
     * each squad, to a different one where there are several, so nobody works in a crowd - or
     * {@code from} itself if it saw none.
     */
    private Position seenFor(String squad, int soldier, Resource resource, Position from) {
        ToDoubleFunction<Position> away = at -> Math.hypot(at.x() - from.x(), at.z() - from.z());
        List<Position> seen = chainOfCommand.resourcesSeen(Plan.squadOf(squad)).stream().filter(site -> site.resource() == resource)
                .map(ResourceSite::location).sorted(Comparator.comparingDouble(away)).toList();
        if (seen.isEmpty()) {
            return from;
        }
        double nearest = away.applyAsDouble(seen.getFirst());
        List<Position> near = seen.stream().filter(at -> away.applyAsDouble(at) <= nearest + SPREAD_BEYOND_NEAREST).toList();
        return near.get(Math.floorMod(soldier + squad.hashCode(), near.size()));
    }

    /** Each soldier sent from a cleared gather objective to where its squad saw more: the order, and where it went instead. */
    private final Map<String, Map.Entry<Objective, Objective>>
            gatherSent = new ConcurrentHashMap<>();
    /** Gatherers reach this far round their objective: a site seen within it is no other place. */
    private static final double GATHER_ELSEWHERE = 16;

    /** How much further than the nearest a place a resource was seen may be, and still be shared out to. */
    private static final double SPREAD_BEYOND_NEAREST = 24;

    private void logSquadSitrep() {
        for (Roster.Squad squadDefinition : roster.squads()) {
            List<DeterministicSoldier> squad = presentSoldiers(squadDefinition.name());
            if (squad.isEmpty()) {
                continue;
            }
            StringBuilder sitrep = new StringBuilder();
            for (DeterministicSoldier soldier : squad) {
                SoldierStatus status = soldier.status();
                // Ground the world model has no data for reads as solid: a soldier standing in it can go nowhere.
                boolean known = chunkCache.stateIdAt((int) Math.floor(status.position().x()), (int) Math.floor(status.position().y()),
                        (int) Math.floor(status.position().z())).isPresent();
                sitrep.append(String.format(Locale.ROOT, "%n  %s at (%.1f, %.1f, %.1f)%s: %s %s",
                        status.name(), status.position().x(), status.position().y(), status.position().z(),
                        known ? "" : " [NO BLOCK DATA HERE]", status.report(),
                        status.condition().map(Object::toString).orElse("(condition not reported)")));
            }
            LOG.info("[squad -> {}] SITREP - {} chunk(s), {} entities tracked:{}",
                    squadDefinition.name(), chunkCache.loadedChunkCount(), entityTracker.trackedEntityCount(), sitrep);
        }
    }

    private boolean squadHasTorchNow(List<SoldierStatus> statuses) {
        return statuses.stream().anyMatch(st -> torchesCarried.getOrDefault(st.name(), 0) > 0);
    }

    private static final int GROUND_SEARCH = 8;

    /**
    /** How far from the platoon leader's point to look for level ground for a patrol base. */
    private static final int SITE_SEARCH = 8;
    /**
     * How close to its place's centre a soldier stands inside a hut: a body 0.6 wide is clear of the
     * walls only within 0.2 of it, and the server won't place a block where a body is.
     */
    private static final double HUT_PLACE_TOLERANCE = 0.15;

    /**
     * The columns each squad's patrol base stands on, walls included - squad loop only. The platoon leader
     * may send two squads to one point; their huts go side by side, not one inside the other.
     */
    private final Map<String, Set<Long>> baseFootprints = new ConcurrentHashMap<>();

    /** How near a waypoint counts as there, for a patrol's next leg. */
    private static final double PATROL_REACHED = 2.5;

    /**
     * A patrol's round: the corners and the midpoints of the square {@link #PATROL_RADIUS} out from
     * the ground the objective stands on, clockwise from the north-west, each on the ground there.
     */
    private List<Position> patrolRound(Position objective) {
        BlockPos centre = typicalGround((int) Math.floor(objective.x()), (int) Math.floor(objective.z()), (int) Math.floor(objective.y()))
                .orElse(TerrainSurveyor.groundAt(world, BlockPos.containing(objective.x(), objective.y(), objective.z())));
        int r = PATROL_RADIUS;
        int[][] round = {{-r, -r}, {0, -r}, {r, -r}, {r, 0}, {r, r}, {0, r}, {-r, r}, {-r, 0}};
        List<Position> waypoints = new ArrayList<>();
        for (int[] p : round) {
            BlockPos ground = TerrainSurveyor.groundAt(world, centre.offset(p[0], 0, p[1]));
            waypoints.add(new Position(ground.x() + 0.5, ground.y(), ground.z() + 0.5));
        }
        return List.copyOf(waypoints);
    }

    /**
     * The torches that would light the ground within {@link #SECURE_RADIUS} of {@code at} so nothing
     * spawns on it (block light 0 is where monsters spawn), from the torches standing now - not on a
     * wall's line, one laid out or the camp's still to be: a torch there holds a cell the mason needs.
     */
    private List<BlockPos> torchPlan(BlockPos at) {
        return Lighting.plan(world, at, SECURE_RADIUS, offTheWall());
    }

    /** {@code cells} but those on a wall's line: the wall's, not the lamplighter's. */
    private List<BlockPos> offTheWall(List<BlockPos> cells) {
        return cells.stream().filter(offTheWall()).toList();
    }

    /** Whether a cell is off every wall's line, laid out or the camp's still to be. */
    private Predicate<BlockPos> offTheWall() {
        Set<Long> wallColumns = walls.values().stream().flatMap(List::stream).map(c -> column(c.x(), c.z()))
                .collect(Collectors.toSet());
        return cell -> !wallColumns.contains(column(cell.x(), cell.z())) && (camp == null
                || Math.max(Math.abs(cell.x() - camp[0]), Math.abs(cell.z() - camp[1])) != WALL_RADIUS);
    }

    /**
     * The nearest point to {@code asked}, within {@value #SITE_SEARCH} blocks across and
     * {@value #GROUND_SEARCH} up or down, where every floor cell of a hut for {@code soldiers} is
     * standable at one height: solid below, room for a body above - and the hut's walls stand at
     * least a block clear of every other squad's, so each keeps a side to cut a doorway through.
     */
    private Optional<Position> levelSite(Position asked, int soldiers, String squadName, Position from) {
        // Only footing the squad can walk to: a level roof of an old hut two blocks up is no site.
        Set<BlockPos> reachable = Pathfinder.reachable(world, TerrainSurveyor.groundAt(world,
                        BlockPos.containing(from.x(), from.y(), from.z())),
                (int) Math.ceil(Math.hypot(asked.x() - from.x(), asked.z() - from.z())) + SITE_SEARCH + 2, SITE_REACH_BUDGET);
        // Shut in a hut, the squad reaches next to nothing: it'll cut its way out, so any site will do.
        boolean shutIn = reachable.size() < SHUT_IN_CELLS;
        Set<Long> taken = new HashSet<>();
        baseFootprints.forEach((other, columns) -> {
            if (!other.equals(squadName)) {
                taken.addAll(columns);
            }
        });
        // Clear of the camp's workshop and its mine head: live, the night's hut at the camp's centre
        // sealed the workshop's chest inside it, and the miners stood all night unable to put their loads away.
        Workshop shop = campWorkshop;
        if (shop != null) {
            Stream.concat(Stream.of(shop.stand(), shop.table(), shop.furnace()), shop.chest().stream())
                    .forEach(c -> taken.add(column(c.x(), c.z())));
        }
        campStairs().ifPresent(st -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (st.inShaft(st.head().x() + dx, st.head().z() + dz)) {
                        taken.add(column(st.head().x() + dx, st.head().z() + dz));
                    }
                }
            }
        });
        int ax = (int) Math.floor(asked.x());
        int az = (int) Math.floor(asked.z());
        int ay = (int) Math.floor(asked.y());
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -SITE_SEARCH; dx <= SITE_SEARCH; dx++) {
            for (int dz = -SITE_SEARCH; dz <= SITE_SEARCH; dz++) {
                for (int dy = 0; dy <= GROUND_SEARCH; dy++) {
                    for (int y : new int[]{ay + dy, ay - dy}) {
                        Position candidate = new Position(ax + dx, y, az + dz);
                        PatrolBase.Plan hut = PatrolBase.hasty(candidate, soldiers);
                        boolean clear = taken.isEmpty() || footprint(hut).stream().noneMatch(c -> {
                            int cx = (int) (c >> 32);
                            int cz = (int) (long) c;
                            return taken.contains(c) || taken.contains(column(cx + 1, cz)) || taken.contains(column(cx - 1, cz))
                                    || taken.contains(column(cx, cz + 1)) || taken.contains(column(cx, cz - 1));
                        });
                        boolean level = clear && (shutIn || hut.floor().stream().allMatch(cell -> reachable.contains(
                                new BlockPos((int) cell.x(), (int) cell.y(), (int) cell.z())))) && hut.floor().stream().allMatch(cell -> {
                            int cx = (int) cell.x();
                            int cy = (int) cell.y();
                            int cz = (int) cell.z();
                            return world.isSolid(cx, cy - 1, cz) && !world.isSolid(cx, cy, cz) && !world.isSolid(cx, cy + 1, cz)
                                    && !world.isHazard(cx, cy, cz);
                        });
                        // Height counts double: a level footing on the roof of an old hut is
                        // nearer across than its own floor, but the floor is the one to reuse.
                        // And each block already standing is one not placed: a hut built before a
                        // restart is taken back rather than a new one built beside it.
                        double distance = Math.hypot(dx, dz) + Math.abs(y - ay) * 2
                                - (level ? hut.standing(c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z())) : 0);
                        if (level && distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
    /** Cells a patrol base's reachability flood may visit: plenty for a site within a few dozen blocks. */
    private static final int SITE_REACH_BUDGET = 20_000;
    /** Fewer cells than this to walk to, and a squad is shut in, not choosing ground. */
    private static final int SHUT_IN_CELLS = 30;
    /** {@code minecraft:cobblestone}'s block state in protocol 777 - a placed pillar block, until the server's update. */
    private static final int COBBLESTONE_STATE = 14;
    /** Stone's block state: what a snag the model didn't show is taken as, until the server says otherwise - a dig timed for it outwaits anything softer. */
    private static final int STONE_STATE = 1;

    /** Whether a soldier can step into {@code cell} from beside it: room for a body, and ground at most a block down. */
    private boolean walkable(Position cell) {
        int x = (int) cell.x();
        int y = (int) cell.y();
        int z = (int) cell.z();
        return !world.isSolid(x, y, z) && !world.isSolid(x, y + 1, z) && !world.isHazard(x, y, z)
                && (world.isSolid(x, y - 1, z) || !world.isSolid(x, y - 1, z) && world.isSolid(x, y - 2, z));
    }

    /** A squad's connected soldiers in formation order (the first present one is the point man). */
    private List<DeterministicSoldier> presentSoldiers(String squad) {
        return soldierDefinitions.stream().filter(d -> d.squad().equals(squad))
                .map(d -> activeSoldiers.get(d.name())).filter(Objects::nonNull).toList();
    }

    private void connect(SoldierDefinition definition) {
        String name = definition.name();
        CompletableFuture<PlayerPosition> firstPosition = new CompletableFuture<>();
        // Populated once onJoined's own scheduler starts - onDisconnected reads it to stop the old
        // tick loop before reconnecting. Netty's per-channel event loop ordering guarantees onJoined
        // (which runs from the same PlayLoginPacket dispatch that completes this join) has already
        // set it by the time any later channelInactive on this same connection could fire onDisconnect.
        AtomicReference<ScheduledExecutorService> activeScheduler = new AtomicReference<>();
        // Set by onJoined, which runs on the event loop inside the first Player Position's dispatch -
        // so every later Player Position on this connection already sees it.
        AtomicReference<DeterministicSoldier> activeSoldier = new AtomicReference<>();
        // The server sends health on join - possibly before the soldier exists - and afterwards
        // only on a change. Kept so the soldier starts from it: otherwise a one-blow kill of a
        // soldier never hurt since joining reads like joining dead, and its death isn't counted.
        AtomicReference<HealthUpdate> lastHealth = new AtomicReference<>();
        // Inventory too arrives on join, before the soldier exists - kept the same way.
        Inventory inventory = new Inventory(); // this connection's thread only
        AtomicReference<Kit> lastKit = new AtomicReference<>();
        AtomicReference<List<String>> enchantmentNames = new AtomicReference<>(List.of());
        AtomicReference<List<String>> clockNames = new AtomicReference<>(List.of());
        // Created with the session (onJoined); container updates before then can't be a supply run.
        AtomicReference<SupplyRun> supplyRun = new AtomicReference<>();
        AtomicReference<Armourer> armourerRef = new AtomicReference<>();
        AtomicReference<Torchmaker> torchmakerRef = new AtomicReference<>();
        AtomicReference<Depositor> depositorRef = new AtomicReference<>();
        CompletableFuture<PlaySession> sessionReady = new CompletableFuture<>();

        CompletableFuture<PlaySession> joined = connector.join(name, new PlaySessionListeners(
                update -> logDivergence(name, chunkCache.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), name)),
                unload -> chunkCache.unload(unload.chunkX(), unload.chunkZ(), name), position -> {
                    if (!firstPosition.complete(position)) {
                        DeterministicSoldier soldier = activeSoldier.get();
                        if (soldier != null) {
                            soldier.correctPosition(new Position(position.x(), position.y(), position.z()));
                        }
                    }
                },
                entity -> entityTracker.spawn(entity.entityId(), entity.uuid(), entity.typeId(), entity.x(), entity.y(), entity.z(), name),
                move -> entityTracker.move(move.entityId(), move.x(), move.y(), move.z()),
                ids -> entityTracker.remove(ids, name), health -> {
                    lastHealth.set(health);
                    DeterministicSoldier soldier = activeSoldier.get();
                    if (soldier != null) {
                        soldier.reportHealth(health.health(), health.food());
                    }
                    // A dead player gets no chunks or entities until it respawns, and a soldier has
                    // no reason to stay dead - respawn immediately. The server then teleports it and
                    // sends a Player Position, which arrives above as a correction.
                    if (health.health() <= 0) {
                        LOG.warn("{} is dead - respawning", name);
                        // What it saw goes with it: the server sends what it sees from where it
                        // respawns, and never unloads what it saw before. Kept on its word, a chunk
                        // nobody saw any more missed every change made there.
                        chunkCache.removeOwner(name);
                        sessionReady.thenAccept(PlaySession::respawn);
                    }
                }, update -> {
                    Kit kit = switch (update) {
                        case InventoryUpdate.Contents(var slots, var complete) -> {
                            for (int slot = 0; slot < slots.size(); slot++) {
                                setSlot(inventory, slot, slots.get(slot), enchantmentNames.get());
                            }
                            yield inventory.kit();
                        }
                        case InventoryUpdate.Slot(int slot, var item) -> setSlot(inventory, slot, item, enchantmentNames.get());
                        case InventoryUpdate.HeldSlot(int hotbarSlot) -> inventory.selectHotbar(hotbarSlot);
                    };
                    lastKit.set(kit);
                    DeterministicSoldier soldier = activeSoldier.get();
                    if (soldier != null) {
                        soldier.reportKit(kit);
                    }
                },
                // Every soldier passes on what it hears: a line said aloud reaches every connection and
                // is passed on once; one whispered to a soldier (/msg) reaches only that one, which relays it up the chain.
                chat -> {
                    if (firstHearing(chat)) {
                        operatorOrders.orderFrom(chat).ifPresent(order -> {
                            LOG.info("{} relays {} from the operator: {}", definition.name(),
                                    order instanceof OperatorOrders.Order.Remark ? "what it heard" : "an order", chat.message());
                            switch (order) {
                                case OperatorOrders.Order.Mission(String operator, String mission) ->
                                        chainOfCommand.onOperatorMission(operator, mission);
                                case OperatorOrders.Order.Escort(String operator, String principal) ->
                                        chainOfCommand.onOperatorEscort(operator, principal);
                                case OperatorOrders.Order.Flatten(String operator, Flatten site) ->
                                        chainOfCommand.onOperatorFlatten(operator, site);
                                case OperatorOrders.Order.Remark(String operator, String remark) ->
                                        chainOfCommand.onOperatorRemark(operator, remark);
                                case OperatorOrders.Order.Unknown(String operator, String command) ->
                                        chainOfCommand.onOperatorUnknown(operator, command);
                            }
                        });
                    }
                }, container -> {
                    SupplyRun run = supplyRun.get();
                    if (run != null) {
                        run.onContainer(container);
                    }
                    Torchmaker torchmaker = torchmakerRef.get();
                    if (torchmaker != null) {
                        torchmaker.onContainer(container);
                    }
                    Armourer armourer = armourerRef.get();
                    if (armourer != null) {
                        armourer.onContainer(container);
                    }
                    Depositor depositor = depositorRef.get();
                    if (depositor != null) {
                        depositor.onContainer(container);
                    }
                    Fitter chestFitter = storeFitters.get(definition.name());
                    if (chestFitter != null) {
                        chestFitter.onContainer(container);
                    }
                },
                // Configuration syncs the registries before any inventory arrives.
                (registry, entries) -> {
                    if (registry.equals("minecraft:enchantment")) {
                        enchantmentNames.set(List.copyOf(entries));
                    } else if (registry.equals("minecraft:world_clock")) {
                        clockNames.set(List.copyOf(entries));
                    }
                },
                // Every connection sees the change; applying it twice is harmless.
                blocks -> blocks.forEach(b -> chunkCache.setBlock(b.x(), b.y(), b.z(), b.stateId())),
                // Every connection hears the same clock; the radio operator's passes it up.
                time -> {
                    if (definition == soldierDefinitions.getFirst()) {
                        overworldClock.update(time, clockNames.get()).ifPresent(chainOfCommand::onTime);
                    }
                }, death -> {
                    LOG.warn("[Casualty] {}", recorders.computeIfAbsent(name, n -> new FlightRecorder()).casualty(name, death));
                    String cause = death.contains("[") ? death.substring(0, death.indexOf('[')) : death;
                    casualties.merge(cause, 1, Integer::sum);
                    Optional.ofNullable(activeSoldiers.get(name)).ifPresent(s -> dangerAreas.killed(s.status().position(), clockwork.millis()));
                    Optional.ofNullable(activeSoldiers.get(name)).ifPresent(s -> worldRecords.recordDeath(chunkCache::stateIdAt, s.status(), clockwork.millis())); // a death's ground, its eval
                    afterActionReview.death(name, cause);
                    afterActionReview.commands.casualty(squadTask(unitOf(name, definition.squad())), Optional.ofNullable(activeSoldiers.get(name))
                            .map(s -> s.status().order().getClass().getSimpleName()).orElse("none"), Optional.ofNullable(activeSoldiers.get(name))
                            .map(s -> CampGround.area(s.status().position(), chainOfCommand.foothold().map(Foothold::camp), WALL_RADIUS)).orElse(null));
                    LOG.warn("[Casualty] since the app started: {}", casualties);
                }, cause -> onDisconnected(definition, activeScheduler, cause)));
        joined.thenAccept(sessionReady::complete);
        joined.thenAcceptBoth(firstPosition, (session, spawn) -> {
                    joinAttempts.remove(name);
                    onJoined(definition, placementAudit.wrap(definition.name(), session, inventory), spawn, activeScheduler, activeSoldier, lastHealth,
                            lastKit, inventory, supplyRun, armourerRef, depositorRef, torchmakerRef); // its placings above sea level audited
                }).exceptionally(cause -> {
                    if (stopping) {
                        return null;
                    }
                    // Tried again, backing off, for as long as it takes: a server restarting after its
                    // watchdog takes a minute, and one try left the whole garrison off the field.
                    int attempt = joinAttempts.merge(name, 1, Integer::sum);
                    long delay = Math.min(REJOIN_MAX_MILLIS, REJOIN_FIRST_MILLIS << Math.min(attempt - 1, 10));
                    LOG.error("{} failed to join {}:{} - {} - trying again in {} s (attempt {})", name, HOST, PORT, cause.getMessage(),
                            delay / 1000, attempt, cause);
                    ScheduledExecutorService retry = reconnects;
                    if (retry == null) {
                        return null; // never run(): a test's one connection, not the garrison
                    }
                    retry.schedule(loudly(name + "'s rejoin", () -> connect(definition)), delay, TimeUnit.MILLISECONDS);
                    return null;
                });
    }

    /** The first wait before a failed join is tried again, doubled each failure up to the most. */
    static final long REJOIN_FIRST_MILLIS = 5_000, REJOIN_MAX_MILLIS = 60_000;

    /** Failed joins in a row, by soldier - cleared on a join; written from each connection's event loop. */
    private final Map<String, Integer> joinAttempts = new ConcurrentHashMap<>();

    /** Where failed joins are tried again from; set by {@link #run}. */
    private volatile ScheduledExecutorService reconnects;

    private void onJoined(SoldierDefinition definition, PlaySession session, PlayerPosition spawn,
                           AtomicReference<ScheduledExecutorService> activeScheduler,
                           AtomicReference<DeterministicSoldier> activeSoldier, AtomicReference<HealthUpdate> lastHealth,
                           AtomicReference<Kit> lastKit, Inventory inventory, AtomicReference<SupplyRun> supplyRun,
                           AtomicReference<Armourer> armourerRef, AtomicReference<Depositor> depositorRef,
                           AtomicReference<Torchmaker> torchmakerRef) {
        String name = definition.name();
        LOG.info("{} joined at real spawn ({}, {}, {}): {}", name, spawn.x(), spawn.y(), spawn.z(), session.playState());

        DeterministicSoldier soldier = new DeterministicSoldier(name, new Position(spawn.x(), spawn.y(), spawn.z()));
        soldier.entityId(session.playState().entityId());
        HealthUpdate health = lastHealth.get();
        if (health != null) {
            soldier.reportHealth(health.health(), health.food());
        }
        Kit kit = lastKit.get();
        if (kit != null) {
            soldier.reportKit(kit);
        }
        activeSoldier.set(soldier);
        if (definition == soldierDefinitions.getFirst()) {
            // The point man carries the squad's radio: the squad leader and platoon leader aren't in the
            // world, so their traffic is keyed out over his connection (rebound on reconnect).
            chainOfCommand.setRadio(command -> radio(name, command));
            // The ground round the point, not the top of whatever stands on it: a heap of old huts
            // at the camp's centre isn't where an objective there is.
            chainOfCommand.setGround(new ChainOfCommand.Ground() {
                @Override
                public OptionalInt heightAt(int x, int z, int nearY) {
                    return typicalGround(x, z, nearY).map(cell -> OptionalInt.of(cell.y())).orElse(OptionalInt.empty());
                }

                @Override
                public OptionalInt surfaceAt(int x, int z, int nearY) {
                    return TerrainSurveyor.ground(world, x, z, nearY + SURFACE_ABOVE).map(cell -> OptionalInt.of(cell.y())).orElse(OptionalInt.empty());
                }
            });
        }
        activeSoldiers.put(name, soldier);
        chatLines.put(name, session::sendChatCommand);
        SupplyRun run = new SupplyRun(name, session, inventory, () -> {
            Kit drawn = inventory.kit();
            lastKit.set(drawn);
            soldier.reportKit(drawn);
        });
        supplyRun.set(run);
        Armourer armourer = new Armourer(name, session, inventory, treeClaims, furnaces, () -> {
            Kit made = inventory.kit();
            lastKit.set(made);
            soldier.reportKit(made);
        });
        armourers.put(name, armourer);
        Gatherer gatherer = new Gatherer(name, session, inventory, treeClaims, scaffolding);
        gatherers.put(name, gatherer);
        Farmer farmer = new Farmer(name, session, inventory, treeClaims, farmCellsFailed, scaffolding);
        farmers.put(name, farmer);
        Forester forester = new Forester(name, session, inventory, treeClaims, farmCellsFailed); // a cell one couldn't plant, none tries for a while
        foresters.put(name, forester);
        AtomicReference<Depositor> stashing = new AtomicReference<>();
        // What a chest held once a load went in: a mine's stash is kept as the mine's, the rest is the stockpile.
        Depositor depositor = new Depositor(name, session, inventory, seen -> {
            Optional<Staircase> mine = Optional.ofNullable(stashing.get()).flatMap(Depositor::chestAt).flatMap(this::mineOfChest);
            if (mine.isPresent()) {
                mineStash.put(mine.get().head(), seen);
                LOG.info("[Mine] the stash at {} holds {} raw iron, {} raw copper", mine.get().head(), seen.rawIron(), seen.rawCopper());
            } else {
                Optional.ofNullable(stashing.get()).flatMap(Depositor::chestAt).ifPresent(chest -> {
                    chestStock.put(BlockPos.containing(chest.x(), chest.y(), chest.z()), stashing.get().stockSeen());
                    stockpileWatch.reached(BlockPos.containing(chest.x(), chest.y(), chest.z()));
                    chestsSeen.put(BlockPos.containing(chest.x(), chest.y(), chest.z()), seen);
                });
                // A load went in - its trip didn't end full - so the chest has room: no longer known full.
                Optional.ofNullable(stashing.get()).filter(d -> d.full().isEmpty()).flatMap(Depositor::chestAt)
                        .map(chest -> BlockPos.containing(chest.x(), chest.y(), chest.z()))
                        .filter(fullChests::remove).ifPresent(chest -> saveFullChests());
                forgetChestsGone(chestsSeen, world); // a chest dug, or never put up, holds nothing of the stores
                chainOfCommand.onStores(storesBook.keep(chestsSeen));
            }
            if (mine.isEmpty() && Optional.ofNullable(stashing.get()).flatMap(Depositor::chestAt).equals(stockpileFor(name))) {
                // Only the stockpile draws come from: an outpost depot's chest, or another the load went
                // into, isn't it. Live, with the flatten's outposts, the force's view of its stockpile
                // took each chest opened in turn, raw copper there one look and none the next, and a
                // draw for it began and was called off every half minute.
                chainOfCommand.onStockpile(seen);
            }
        });
        stashing.set(depositor);
        Fitter storeFitter = new Fitter(name, session, inventory, furnaces);
        storeFitters.put(name, storeFitter);
        depositors.put(name, depositor);
        depositorRef.set(depositor);
        Handover handover = new Handover(name, session, inventory);
        handovers.put(name, handover);
        Builder builder = new Builder(name, session, inventory);
        builders.put(name, builder);
        Builder placer = new Builder(name, session, inventory);
        placers.put(name, placer);
        Rations rations = new Rations(name, session, inventory);
        Pack pack = new Pack(name, session, inventory);
        Breacher doorway = new Breacher(name, session, inventory);
        // Cutting steps is part of moving, like a pillar step's block: no routine has the hands for it.
        Breacher stepCutter = new Breacher(name, session, inventory);
        Breacher lidCutter = new Breacher(name, session, inventory); // a block over the chest it's at: no chest opens under one
        Footing footing = new Footing(session, inventory);
        // What the server was last told about sprinting: a new connection starts walking.
        AtomicBoolean sprintingSent = new AtomicBoolean();
        // Likewise its shield: a new connection starts with it lowered.
        AtomicBoolean shieldSent = new AtomicBoolean();
        AtomicBoolean drawingSent = new AtomicBoolean();
        Quiver quiver = new Quiver(session, inventory);
        armourerRef.set(armourer);
        Torchmaker torchmaker = new Torchmaker(name, session, inventory, furnaces);
        torchmakers.put(name, torchmaker);
        AtomicInteger ladderTicks = new AtomicInteger();
        AtomicInteger crowdTicks = new AtomicInteger(); // the crowd watch looks once a second
        // Looks in a row at which this soldier stood with a solid block at its feet and air at its head.
        AtomicInteger feetInBlock = new AtomicInteger();
        // Boxed in with no doorway anyone knows to cut - a hut it was sealed in before a restart -
        // the wall toward where it's sent, broken from where it stands (bot.supply.WayOut).
        AtomicReference<List<BlockPos>> wayOut = new AtomicReference<>(List.of());
        /* The cell the model took as a block on this soldier's word (takeSnag), to take back if it won't break. */
        AtomicReference<BlockPos> snagMarked = new AtomicReference<>();
        AtomicReference<Optional<Recipe>> ladderStep = new AtomicReference<>(Optional.empty());
        AtomicBoolean armourerWas = new AtomicBoolean();
        // Ticks while told to light the base: one torch tried a second, until the light check sees it.
        AtomicInteger torchTicks = new AtomicInteger();
        torchmakerRef.set(torchmaker);
        Lamplighter lamplighter = new Lamplighter(name, inventory, footing, torchClaims);
        lamplighters.put(name, lamplighter);
        Hunter hunter = new Hunter(name, huntClaims);
        hunters.put(name, hunter);
        Mason mason = new Mason(name, inventory, footing, wallClaims);
        masons.put(name, mason);
        Clearer clearer = new Clearer(name, session, inventory, clearClaims);
        clearers.put(name, clearer);
        Filler plainFiller = new Filler(name, session, inventory);
        // The keep's slab is cobblestone only: a filler of its own, the plain one's dirt never in it.
        Filler paver = new Filler(name, session, inventory, Filler.STONE);
        Filler soiler = new Filler(name, session, inventory, Filler.SOIL);
        fillers.put(name, plainFiller);
        // Logs too, last: live, a farmer with two logs and no dirt floated beside its farm for minutes.
        Builder sealer = new Builder(name, session, inventory, List.of(ItemClass.COBBLESTONE, ItemClass.DIRT, ItemClass.LOG));
        AtomicBoolean sealing = new AtomicBoolean();
        AtomicLong tickCount = new AtomicLong();
        Scavenger scavenger = new Scavenger(name);
        scavengers.put(name, scavenger);
        Miner miner = new Miner(name, session, inventory);
        miners.put(name, miner);

        ScheduledExecutorService scheduler = clockwork.scheduler(name + "-tick");
        activeScheduler.set(scheduler);
        schedulers.add(scheduler);
        sessions.add(session);
        scheduler.scheduleAtFixedRate(loudly(name + "'s tick", () -> {
            // A step's stand-in the server never confirmed is taken back before anyone plans on it.
            int takenBack = chunkCache.expireStandIns(clockwork.millis());
            if (takenBack > 0) {
                LOG.warn("[World] {} stand-in block(s) the server never confirmed, taken back", takenBack);
            }
            soldier.holding(CampGround.held(chainOfCommand.foothold().map(Foothold::camp), WALL_RADIUS)); // its routes leave no step on the camp's ground that a way round spares
            soldier.tick(world).ifPresent(target -> {
                LOG.info("{} attacks entity {}", name, target);
                if (soldier.takeKnockbackHit()) { // its sprint before the hit, not after; the server stops it with the hit (Strike)
                    session.setSprinting(true);
                    sprintingSent.set(false);
                }
                session.attack(target);
                foodLedger.attack(name);
            });
            if (soldier.takeArmSwing()) {
                session.swingArm(); // the charge started over, for a hit short of the sweep (Strike)
            }
            // A cell it's been stuck short of three times is a block the server has and the
            // model doesn't: taken as stone until the server says otherwise - a dig timed for stone
            // outwaits anything softer - so the next plan goes round it; and it's broken straight
            // away, since what stopped the body three times is in its way (backlog/world-model-stale.md).
            // Live, marked and left to the planner, a soldier down the camp's mine stood on with "no route".
            soldier.takeSnag().ifPresent(cell -> {
                // A squadmate standing in the cell is what stopped it, not a block: live, two soldiers
                // held each other in a one-wide passage of the camp's mine for a quarter of an hour.
                Optional<String> standingThere = activeSoldiers.entrySet().stream()
                        .filter(e -> !e.getKey().equals(name)).filter(e -> {
                            Position p = e.getValue().status().position();
                            return Math.hypot(p.x() - cell.x() - 0.5, p.z() - cell.z() - 0.5) < 1 && Math.abs(p.y() - cell.y()) <= 1;
                        }).map(Map.Entry::getKey).findFirst();
                if (standingThere.isPresent()) {
                    LOG.warn("[World] {} stuck short of {} {} times - {} stands there", name, cell, DeterministicSoldier.SNAG_TRIES, standingThere.get());
                } else if (!world.isSolid(cell.x(), cell.y(), cell.z())
                        && clockwork.millis() - snagsTakenBack.getOrDefault(cell, Long.MIN_VALUE / 2) >= SNAG_TAKEN_BACK_MILLIS) {
                    chunkCache.setBlock(cell.x(), cell.y(), cell.z(), STONE_STATE);
                    snags.put(cell, clockwork.millis());
                    LOG.warn("[World] {} stuck short of {} {} times - the model takes it as a block, and breaks it", name, cell, DeterministicSoldier.SNAG_TRIES);
                    snagMarked.set(cell);
                    wayOut.set(List.of(cell, cell.offset(0, 1, 0)));
                }
            });
            SoldierStatus status = soldier.status();
            foodLedger.sample(name, status.condition(), status.position(), soldier.sprinting(), soldier.onGround(), status.order().getClass().getSimpleName() + "/" + status.report().getClass().getSimpleName());
            boolean bareHanded = status.condition().flatMap(c -> c.kit()).map(k -> k.weapon().isEmpty()).orElse(false);
            boolean inContact = status.report() instanceof Report.InContact;
            // Under threat, routines pause - they pick up where they left off once it's gone; not for one the soldier lets be (field652).
            // A hostile it can see: live, two zombies in the dark of the camp's mine shaft, 8 blocks off
            // and out of sight below, held every mason's hands a whole day.
            Position eyes = new Position(status.position().x(), status.position().y() + 1.62, status.position().z());
            boolean threatened = inContact || !soldier.lettingBe() && world.nearbyHostiles(status.position(), REARM_CLEAR_RADIUS).stream()
                    .anyMatch(h -> DeterministicSoldier.lineOfSight(world, eyes,
                            new Position(h.position().x(), h.position().y() + 1, h.position().z())));
            // Afloat and getting nowhere: stuck in the water.
            Position at = status.position();
            int ax = (int) Math.floor(at.x()), az = (int) Math.floor(at.z());
            boolean afloat = world.isWater(ax, (int) Math.floor(at.y()), az) || world.isWater(ax, (int) Math.floor(at.y() - 0.5), az);
            long tickNow = tickCount.incrementAndGet();
            double[] mark = afloatSince.get(name);
            if (!afloat) {
                afloatSince.remove(name);
            } else if (mark == null || Math.hypot(at.x() - mark[0], at.z() - mark[2]) >= STUCK_IN_WATER_MOVE || Math.abs(at.y() - mark[1]) >= STUCK_IN_WATER_MOVE) {
                afloatSince.put(name, new double[]{at.x(), at.y(), at.z(), tickNow});
            }
            boolean stuckInWater = afloat && afloatSince.containsKey(name) && tickNow - afloatSince.get(name)[3] >= STUCK_IN_WATER_TICKS;
            // Flint for arrows, for a soldier with a bow and few arrows, when nothing else is to be gathered.
            Objective gather = gathering.get(name) != null || !huntAllowed.contains(name)
                    || inventory.slotOfNamed("bow") < 0 || inventory.count(ItemClass.ARROWS) >= ARROWS_WANTED
                    || inventory.count(ItemClass.FLINT) > 0 ? gathering.get(name)
                    : new Objective("Dig gravel for flint", huntGrounds.getOrDefault(name,
                    status.position()), "Arrows for the bow", new Objective.Task.Gather(Resource.FLINT));
            // And a dig with stone or cobblestone in it: live, soldiers with no pickaxe handed the
            // cobblestone over the camp's floor failed at it, and nobody else's strip had it.
            boolean stoneToClear = clearing.getOrDefault(name, List.of()).stream()
                    .anyMatch(c -> world.isStone(c.x(), c.y(), c.z()) || world.isCobblestone(c.x(), c.y(), c.z()));
            boolean needsPickaxe = (gather != null && gather.gather().equals(Optional.of(Resource.STONE))
                    || mining.contains(name) || stoneToClear)
                    && inventory.slotOf(ItemClassRegistry.ItemClass.PICKAXE) < 0;
            Staircase stairs = cutting.get(name);
            // The next rung of the equipment ladder, worked out once a second (it looks for ore to
            // dig): a weapon if bare-handed and allowed to rearm, more only if allowed to upgrade.
            // Not while the armourer is at the last one: its own claim on the ore it's digging hides
            // that ore from the look for more, and the rung would read as out of reach mid-job.
            // And at once when a run ends: the rung it made is held now, and asked again it would be
            // made again - a request is for one more than carried.
            boolean armourerActive = armourer.active();
            if ((ladderTicks.getAndIncrement() % 20 == 0 || armourerWas.getAndSet(armourerActive)) && !armourerActive) {
                ladderStep.set(nextRung(name, inventory, status.position()));
            }
            // The ladder's next rung, if allowed; a pickaxe, if sent for stone without one - both at one table.
            Set<Recipe> tools = new LinkedHashSet<>();
            // Not a keep builder's with a full load: it unloads first. A rung is made through planks,
            // sticks and a table, each a slot; in the squad simulation a builder four slots from full
            // made the tool load's swords until it had no slot for the next.
            // Armour isn't made down the mine by night: the shift's work is the ore, and its base has one
            // furnace. Live (field392-397), the night's ore fell 51, 27, 44, 25, 18 as its miners walked to the
            // base for copper armour 222-440 times a run, and gave it up 12-39 times, the furnace held.
            // By day it's made at the squad's workshop on the surface.
            boolean belowByNight = chainOfCommand.time().map(TimeOfDay::dark).orElse(false) && underground(status.position());
            Optional<Recipe> rung = (keepHands.contains(name) && Pack.freeSlots(inventory) <= KEEP_FULL_AT ? Optional.<Recipe>empty() : ladderStep.get())
                    .filter(r -> !(belowByNight && r.makes() == ItemClassRegistry.ItemClass.ARMOUR));
            rung.ifPresent(tools::add);
            // A lumberjack's axe: a builder sent to cut trees for the keep makes a stone one first, from
            // the cobblestone it carries (operator, 2026-11-14: the lumberjacks use axes).
            if (keepParty.cutters().contains(name) && java.util.stream.IntStream.range(0, Inventory.SLOTS).mapToDouble(s -> ItemClassRegistry.axeSpeed(inventory.itemAt(s)).orElse(0)).max().orElse(0) < 4 && inventory.count(ItemClass.COBBLESTONE) >= 3) {
                tools.add(Recipe.STONE_AXE);
            }
            if (needsPickaxe) {
                // A stone one from cobblestone carried: live, a cutter whose pickaxe wore out halfway
                // down asked for a wooden one, with no logs near and 21 cobblestone in its pack.
                tools.add(inventory.count(ItemClass.COBBLESTONE) >= 3 ? Recipe.STONE_PICKAXE : ORDER_PICKAXE);
            }
            // The cutter's next tool before the ladder's next weapon: iron ore drops nothing to a
            // wooden pickaxe, and the stairs give the cobblestone for a stone one.
            if (stairs != null && Ladder.bestPickaxe(inventory) < 1 && inventory.count(ItemClass.COBBLESTONE) >= 3) {
                tools.add(Recipe.STONE_PICKAXE);
            }
            // What needs a table or furnace is made at the workshop the engineer sited: the squad's,
            // or the mine base for a soldier down the stairs. The armourer walks there itself.
            // Its own, else the camp's: live, a farmer working 15 blocks out, sited none, gave up its
            // bread 13 times for want of a table near, and its squad starved with the wheat in its pack.
            // The smelter's is the main depot's: its furnace, its chests.
            boolean smelter = name.equals(keepSmelter);
            Workshop mainShop = keepWorkshop;
            Optional<Workshop> workshop = smelter && mainShop != null ? Optional.of(mainShop)
                    : Optional.ofNullable(workshops.get(name)).or(() -> Optional.ofNullable(campWorkshop));
            // Raw ore through the common stockpile (bot.supply.MetalPool): what its next piece can't
            // be made from goes in; what the stockpile completes it with is drawn - and the armourer
            // waits for the draw rather than walking off to dig.
            // Counted over every surface chest, and drawn from the one holding it: live (field300), 64 raw
            // copper lay in an outpost depot's chests and 10 in the camp's, enough for four soldiers' armour,
            // while draws looked only in the main depot's - and three soldiers wore none.
            MetalPool.Plan own = MetalPool.plan(inventory, rung, chainOfCommand.stores());
            int rawCarried = inventory.count(ItemClass.RAW_IRON) + inventory.count(ItemClass.RAW_COPPER);
            if (smelter && rawCarried + inventory.count(ItemClass.IRON_INGOT) + inventory.count(ItemClass.COPPER_INGOT) > 0) {
                smelterCarries.add(name);
            } else {
                smelterCarries.remove(name);
            }
            // The smelter draws raw ore - all the nearest chest holds of one metal, up to a stack - and
            // keeps it to smelt; the ingots it makes go in as anyone's do.
            Optional<ItemClass> toSmelt = !smelter || rawCarried > 0 ? Optional.empty()
                    : java.util.stream.Stream.of(ItemClass.RAW_COPPER, ItemClass.RAW_IRON)
                    .filter(ore -> metalChest(ore, status.position()).isPresent()).findFirst();
            MetalPool.Plan metal = !smelter ? own
                    : new MetalPool.Plan(own.stock().stream().filter(c -> c != ItemClass.RAW_IRON && c != ItemClass.RAW_COPPER)
                    .collect(Collectors.toSet()), toSmelt.map(ore -> new MetalPool.Draw(ore, SMELT_DRAW)));
            // By night, only from a chest in reach, as a workshop is (nightOpen): no walk across the dark for an
            // ingot. Live (field433), Gus, a miner of Alpha's, set off for the depot 80 blocks up and across 24
            // times in the night for two iron ingots, each walk dropped as it began, and his level stood.
            boolean metalByNight = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            Optional<MetalPool.Draw> metalDraw = metal.draw().filter(d -> metalChest(d.ore(), status.position())
                    .filter(c -> !metalByNight || Math.hypot(c.x() - status.position().x(), c.z() - status.position().z()) <= NIGHT_SUPPLY_REACH)
                    .isPresent());
            // Logs from the nearest chest holding them, as metal is: its own stockpile may hold none. Live
            // (field347), Eli at the mine's head couldn't make a pickaxe, "no LOG within 48 blocks", with
            // 71 logs in the stores - in the keep's depot, and his stockpile the camp's chest.
            Optional<Position> logsAt = metalChest(ItemClass.LOG, status.position());
            boolean drawingMetal = metalDraw.isPresent() || MetalPool.METALS.stream().anyMatch(depositor::drawing);
            if (drawingMetal) {
                rung.ifPresent(tools::remove);
            }
            boolean poolOre = metal.stocking(inventory) >= POOL_TRIP;
            // Down the mine with a load of ore: into the stash at the landing, and back to the cutting.
            Workshop working = workshops.get(name);
            // By night only: by day the stash's ore comes up the stairs to the depot (unstashFor), and stashed again on the way it would never leave.
            boolean darkNow = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            boolean stashOre = darkNow && working != null && mineOfChest(chestCentre(working)).isPresent()
                    && STASHED.stream().mapToInt(inventory::count).sum() >= STASH_ORE;
            Optional<Unstash> unstash = unstashFor(status.position(), inventory, darkNow)
                    .or(() -> salvageFor(name, status.position(), inventory, darkNow, depositor::barred));
            // Bare-handed - or short of a stone sword, the phase's arms, with its own work CANTCO'd - its weapon is its own upkeep
            // (docs/echelons/tenets.md): live (field536), Eli stuck in his mine with a wooden sword held the seizure a day.
            armourer.digsItsMakings(inventory.meleeWeapons() == 0 || status.report() instanceof Report.Cantco
                    && Ladder.bestMelee(inventory) < ItemClassRegistry.meleeRank("stone_sword")
                    || !(cutting.containsKey(name) || dutyBook.of(name).isPresent() || keepHands.contains(name)));
            // A farmer's hoe, and its wheat baked into bread three at a time - by its baker, on a farm that has one.
            FarmPlan field = dutyBook.field(name, farming.get(name));
            boolean bakes = dutyBook.is(name, DutyOrder.Duty.BAKER);
            dutyBook.sample(definition.squad(), name, inventory::count, threatened || field != null && !Farmer.hasWork(world, field, Set.of())
                    || bakes && chainOfCommand.stores().filter(st -> st.wheat() > 0).isEmpty() || dutyBook.plantsNothing(name, planting.get(name), world), clockwork.millis());
            Optional.ofNullable(planting.get(name)).ifPresent(g -> tools.addAll(Forester.wants(world, g, inventory))); // its tree chunk's chest, torches
            if (field != null) { // its hoe, a bucket for its farm's water holes, its chunk farm's fixtures (Farmer.wants)
                tools.addAll(Farmer.wants(world, field, inventory));
                bucketOnFarm |= inventory.count(ItemClass.BUCKET) + inventory.count(ItemClass.WATER_BUCKET) > 0;
            }
            // A shovel for ground work - a dig of the camp's or the keep's ground, or dirt for a wall, not a step taken
            // back: stone if it carries the cobblestone, wooden if not.
            boolean groundDigging = clearing.containsKey(name) && !policeCall.on(name) || groundGather.containsKey(name) || diggingForWall.contains(name);
            if (groundDigging && inventory.count(ItemClass.SHOVEL) == 0) {
                tools.add(inventory.count(ItemClass.COBBLESTONE) > 0 ? Recipe.STONE_SHOVEL : Recipe.WOODEN_SHOVEL);
            }
            // Bread from the wheat it carries: a farmer's work, and anyone's below its basic load
            // (command.Subsistence) - live, a soldier carried 22 wheat for days while its squad starved.
            int carriedRations = status.condition().flatMap(c -> c.kit()).map(Kit::rations).orElse(0);
            // And still wanted while a loaf is under way: its wheat in the table's grid isn't carried.
            // Live, a soldier with 4 wheat put 3 in the grid, wanted no bread with the 1 left, and started and dropped the loaf 518 times.
            if (Subsistence.bakeTrip(inventory.count(ItemClass.WHEAT), carriedRations) && ((dutyBook.of(name).filter(DutyOrder::farm).isEmpty() ? field != null : bakes) || carriedRations < Subsistence.BASIC_LOAD)
                    || armourer.making(Recipe.BREAD)) {
                tools.add(Recipe.BREAD);
            }
            // Arrows from the flint and feathers it has picked up, while it has a bow to shoot them.
            if (inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.FLINT) > 0 && inventory.count(ItemClass.FEATHER) > 0
                    && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED) {
                tools.add(Recipe.ARROWS);
            }
            // A bed of its own, once it has the wool - or the string to make it, four a wool (backlog/rest-plan.md).
            if (inventory.count(ItemClass.BED) == 0 && inventory.count(ItemClass.WOOL) + inventory.count(ItemClass.STRING)
                    / Recipe.WOOL.ingredients().get(ItemClass.STRING) >= Recipe.BED.ingredients().get(ItemClass.WOOL)) {
                tools.add(Recipe.BED);
            }
            boolean rearming = rung.filter(r -> bareHanded && r.makes() == ItemClassRegistry.ItemClass.WEAPON)
                    .isPresent();
            // Bread on an errand of its own - a weapon first, if bare-handed: asked with kit it can't
            // make yet, it waited on that kit's makings, and was given up with it. Live, seven loaves
            // went with copper boots there was no fuel to smelt for.
            if (tools.contains(Recipe.BREAD) && !rearming) {
                tools.retainAll(Set.of(Recipe.BREAD));
            }
            Fortify fort = fortifying.get(name);
            Opening exit = clearingTunnel.contains(name) ? null : opening.get(name); // a stair up laid is its way out (field494: Olga afloat held hers for a stand out of reach)
            torchesCarried.put(name, inventory.count(ItemClassRegistry.ItemClass.TORCH));
            // A run under way has spent what canMake looks for - its furnace is in the world now.
            canMakeTorches.put(name, torchmaker.active() || Torchmaker.canMake(inventory));
            boolean atWorkshop = fort != null && fort.makeTorches()
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.6;
            if (fort != null && fort.placeTorch() && !torchmaker.active() && torchTicks.incrementAndGet() % 20 == 0) {
                BlockPos feet = BlockPos.containing(
                        status.position().x(), status.position().y() + 0.01, status.position().z());
                if (footing.place(feet, ItemClassRegistry.ItemClass.TORCH)) {
                    LOG.info("{} lights the patrol base with a torch at {}", name, feet);
                }
            }
            // Pre-combat checks come first: a bare-handed soldier waiting on its squad's base - not
            // yet building, not the torchbearer - may make its sword meanwhile.
            boolean waiting = fort != null && !fort.allInside() && !fort.makeTorches();
            if (smelter && inventory.count(ItemClass.RAW_COPPER) > 0) {
                tools.add(Recipe.COPPER_INGOT);
            }
            if (smelter && inventory.count(ItemClass.RAW_IRON) > 0) {
                tools.add(Recipe.IRON_INGOT);
            }
            // Nothing made by one the recovery drill has: its hands are the stair's till it's out. Live
            // (field347), Jack's armourer walked him off his stair up every second for the camp's
            // workshop, 200 blocks off and up, for an iron pickaxe, and his clearer never kept a stand.
            // After dark, on the surface, nothing made at a workshop more than NIGHT_SUPPLY_REACH off - but a
            // weapon for one bare-handed: the walk there is across the open in the dark (operator, 2026-11-17:
            // no errands across the surface by night). Live (field410-413), Liam and Gus were shot on their
            // way to a workshop for kit, Liam 7 blocks under where he'd stood 8 s before.
            boolean nightOpen = chainOfCommand.time().map(TimeOfDay::dark).orElse(false) && !underground(status.position())
                    && workshop.map(w -> Math.hypot(w.stand().x() + 0.5 - status.position().x(), w.stand().z() + 0.5 - status.position().z())
                    > NIGHT_SUPPLY_REACH).orElse(false);
            if (nightOpen && !rearming && !tools.isEmpty()) {
                tools.clear();
            }
            Set<Recipe> craftable = recovering.contains(name) ? Set.of() : fort == null ? tools
                    : waiting && rearming ? Set.of(rung.orElseThrow())
                    : Set.of();
            Kit carried = status.condition().flatMap(c -> c.kit()).orElse(null);
            // On the keep, a full load is a full pack: what it digs is the slab's fill or the chest's, and
            // carried while there's room - live, its builders walked a chest a load of 16 logs at a time.
            boolean fullLoad = keepHands.contains(name) ? Pack.freeSlots(inventory) <= KEEP_FULL_AT
                    : carried != null && (carried.logs() >= (gathersWood(name) ? Gatherer.CARRY_LOGS : LOGS_KEPT_DRAWN) || carried.stone() >= Gatherer.CARRY_STONE);
            CrossLevel.Transfer giving = deliveries.get(name);
            boolean atBuildPlace = fort != null && fort.allInside() && !inContact
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.2;
            AtomicReference<Builder.Progress> built = new AtomicReference<>();
            // Who has the soldier's hands this tick: each routine says whether it's under way and
            // what it would do if free; Hands decides who's free, in its one priority order.
            Map<Hands.Routine, Hands.Turn> turns = new EnumMap<>(Hands.Routine.class);
            turns.put(Hands.Routine.SUPPLY_RUN, turn(run::active,
                    // Not one the recovery drill has: its hands are the stair's till it's out. Live (field401-402),
                    // Eli, stranded at y=-43 with no rations, set off for the supply point 115 blocks up whenever
                    // his hands came free, "no route", and his stair up was never cut.
                    free -> run.tick(status.position(), supplyPoint, free && Resupply.isShort(status) && !inContact
                            && !recovering.contains(name))));
            // A way out chosen is kept until it's broken, not dropped with the next report: live, a
            // soldier's CANTCO flipped between "no route" and "no blocks to build with" each second, and the break started and stopped forever.
            boolean breakingOut = !wayOut.get().isEmpty() && exit == null && fort == null && !inContact
                    && wayOut.get().stream().anyMatch(c -> Math.hypot(c.x() + 0.5 - status.position().x(), c.z() + 0.5 - status.position().z()) < 3);
            if (ladderTicks.get() % 20 == 0 && !breakingOut) {
                boolean noRoute = status.report() instanceof Report.Cantco(String why) && why.startsWith("no route");
                // Toward a goal near its own level: one a wall away. A goal far above or below isn't
                // behind a wall - the way there is the recovery drill's stair (docs/military-doctrine.md
                // § 7). In the squad simulation a soldier in a gallery 25 blocks down broke tunnel after
                // tunnel sideways toward its slot on the surface, and its stair up was laid out again from each new spot.
                wayOut.set(exit == null && fort == null && !inContact && noRoute && status.order() instanceof Order.MoveTo to
                        && Math.abs(to.y() - status.position().y()) <= WAY_OUT_RISE
                        ? WayOut.cells(world, BlockPos.containing(status.position().x(), status.position().y() + 0.01, status.position().z()),
                        new Position(to.x(), to.y(), to.z()))
                        : List.of());
                // Never through a wall's line, the camp's included: live, three soldiers in a pocket of
                // the camp's heaps went to break the camp's own west wall.
                if (!wayOut.get().stream().allMatch(offTheWall())) {
                    wayOut.set(List.of());
                }
                // Stuck in the water with nothing to build with: a block cut from the bank beside it, the
                // footing its sealer plugs the water with - a soldier digs itself out of anything. Live
                // (field387), Ned floated 13 minutes in a pit at (-42, 61, 29), a pickaxe in its pack and no
                // block to plug the water or step up onto, every route "no route".
                if (wayOut.get().isEmpty() && stuckInWater && exit == null && fort == null && !inContact
                        && inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.LOG) == 0) {
                    bankBlock(at).filter(c -> java.util.stream.Stream.of(c).allMatch(offTheWall())).ifPresent(c -> {
                        wayOut.set(List.of(c));
                        LOG.warn("{} stuck in the water at {} with no blocks - cuts one from the bank at {}", name, at, c);
                    });
                }
                // Stood in a block - at its feet or its head - it breaks that first, whatever its orders:
                // live, four soldiers stood a day and a night in dirt at the farm, suffocating as fast
                // as they healed, every route from there "no route".
                // At its head, that is: a step it puts under itself mid-jump is at its feet for a tick,
                // and in the squad simulation, broken as that, its pillars and walls came apart.
                BlockPos feetCell = BlockPos.containing(status.position().x(), status.position().y() + 0.01, status.position().z());
                BlockPos headCell = feetCell.offset(0, 1, 0);
                // A block at its feet alone, two looks running - two seconds, no step's tick - is one it
                // stands in to the waist: live, gravel fallen into the pocket at the mine's bottom held two
                // soldiers a day, each with air at its head and a solid cell at its feet, nothing to reach a stand from, every route "no route".
                boolean feetOnly = world.isSolid(feetCell.x(), feetCell.y(), feetCell.z()) && !world.isSolid(headCell.x(), headCell.y(), headCell.z());
                if (!feetOnly) {
                    feetInBlock.set(0);
                }
                int feetLooks = feetOnly ? feetInBlock.incrementAndGet() : 0;
                List<BlockPos> inside = world.isSolid(headCell.x(), headCell.y(), headCell.z())
                        ? java.util.stream.Stream.of(headCell, feetCell)
                        .filter(c -> world.isSolid(c.x(), c.y(), c.z()) && Clearing.diggable(world, c.x(), c.y(), c.z())).toList()
                        : feetLooks >= FEET_IN_BLOCK_LOOKS && Clearing.diggable(world, feetCell.x(), feetCell.y(), feetCell.z()) ? List.of(feetCell)
                        : List.of();
                if (!inside.isEmpty() && !inContact) {
                    wayOut.set(inside);
                    LOG.warn("{} stands in a block at {} - breaks {}", name, status.position(), inside);
                } else if (!wayOut.get().isEmpty() && !doorway.active()) {
                    LOG.warn("{} is boxed in at {} with no way out - breaks the wall at {}", name, status.position(), wayOut.get());
                }
            }
            List<BlockPos> out = wayOut.get();
            turns.put(Hands.Routine.DOORWAY, turn(() -> exit != null || !out.isEmpty(), free -> {
                if (free && exit != null && Math.hypot(status.position().x() - exit.stand().x(), status.position().z() - exit.stand().z()) < 0.5) {
                    doorway.tick(world, exit.cells());
                } else if (free && exit == null && !out.isEmpty() && !threatened) {
                    if (doorway.tick(world, out)) {
                        wayOut.set(List.of());
                    }
                    // The server never broke it: the snag the model took as a block on this soldier's word
                    // isn't one - taken back, or the phantom would wall it in for good (field164, Bob).
                    takeBackSnag(name, doorway).ifPresent(cell -> {
                        snagMarked.set(null);
                        wayOut.set(List.of());
                    });
                } else {
                    doorway.stop();
                }
            }));
            // Securing ground: a soldier out of torches makes more where it stands, then puts them down.
            List<BlockPos> lights = lighting.getOrDefault(name, List.of());
            boolean torchesWanted = !lights.isEmpty() && inventory.count(ItemClassRegistry.ItemClass.TORCH) == 0;
            // And for torches: ground to light, none carried, and not the makings - live (field350), Eli
            // at the mine's head stood "short of {LOG=1}" to make them, with logs in the stores.
            Optional<String> torchShort = torchesWanted && !torchmaker.active() && !Torchmaker.canMake(inventory)
                    ? Optional.of(torchShortfall(inventory, workshops.get(name))) : Optional.empty();
            // A mine's cutter keeps a stock to light its strips as it cuts them (the rules of a mine:
            // torches enough): under its low mark, it makes up to its stock from the coal it mines.
            boolean minerShort = cutting.containsKey(name) && inventory.count(ItemClassRegistry.ItemClass.TORCH) < MINER_TORCHES_LOW
                    && inventory.count(ItemClassRegistry.ItemClass.COAL) > 0; // its coal only: logs for charcoal took it up to the workshop
            turns.put(Hands.Routine.TORCHMAKER, turn(torchmaker::active,
                    free -> torchmaker.tick(status.position(), world, !free ? 0 : atWorkshop ? Torchmaker.TORCHES
                            : torchesWanted ? Math.min(LIGHTING_TORCHES, (lights.size() + 3) / 4 * 4)
                            : minerShort ? MINER_TORCHES : 0, threatened, workshop)));
            // Stuck in water: plug the sources round it - stopping the flow, and each block one more
            // to climb out by - nearest first, from where it floats.
            turns.put(Hands.Routine.SEALER, turn(sealing::get, free -> {
                if (!free || !stuckInWater && !sealToClimb.contains(name)
                        || inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.LOG) == 0) {
                    if (sealing.getAndSet(false)) {
                        sealer.stop();
                    }
                    return;
                }
                List<BlockPos> sources = new ArrayList<>();
                int fy = (int) Math.floor(at.y());
                for (int dx = -SEAL_REACH; dx <= SEAL_REACH; dx++) {
                    for (int dz = -SEAL_REACH; dz <= SEAL_REACH; dz++) {
                        for (int dy = -SEAL_REACH; dy <= SEAL_REACH; dy++) {
                            int x = ax + dx, y = fy + dy, z = az + dz;
                            boolean body = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                            // Any water, not only sources: live, the shaft's water round three
                            // stuck soldiers was all flowing, its source out of reach, and with only sources to plug they placed nothing.
                            if (!body && world.isWater(x, y, z)) {
                                sources.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
                // Sources first, which stop the flow; then the water nearest it, which is footing.
                sources.sort(Comparator.comparing((BlockPos c) -> !world.isWaterSource(c.x(), c.y(), c.z()))
                        .thenComparingDouble(c -> Math.hypot(c.x() + 0.5 - at.x(), c.z() + 0.5 - at.z()) + Math.abs(c.y() - at.y())));
                if (sources.isEmpty()) {
                    sealing.set(false);
                    sealer.stop();
                    sealToClimb.remove(name);
                    return;
                }
                if (!sealing.getAndSet(true)) {
                    LOG.warn("{} {} at {} - fills the {} cell(s) of water round it", name,
                            stuckInWater ? "stuck in the water" : "stranded beside water", at, sources.size());
                }
                if (sealer.tickInPlace(at, world, sources) instanceof Builder.Progress.Failed) {
                    sealer.retrySkipped();
                }
            }));
            turns.put(Hands.Routine.RATIONS, turn(rations::active, free -> {
                rations.tick(status.condition().map(c -> c.food()).orElse(Condition.MAX_FOOD),
                        status.condition().map(c -> c.health() < Condition.MAX_HEALTH).orElse(false), free && !threatened,
                        soldier.yaw(), soldier.pitch());
                // Its pack kept for what's worth more: surplus seeds thrown away, between meals.
                // A mine's cutter keeps only its backfill's bulk: the ore comes first (the rules of a mine).
                boolean cutsAMine = cutting.containsKey(name);
                boolean packFree = free && !threatened && !rations.active();
                if (keepHands.contains(name) && !cutsAMine) {
                    // A keep builder sheds before its load trip's mark, so a trip carries what's worth the walk.
                    long before = pack.thrown();
                    pack.tick(packFree, KEEP_FULL_AT + 1, StockLevels.throwPast(KEEP_PACK, k -> stockedAt(k, Optional.empty())));
                    if (pack.thrown() != before) {
                        thrownAt.put(status.position(), clockwork.millis());
                    }
                } else {
                    pack.tick(packFree, cutsAMine ? Pack.MINER_DIRT_KEPT : Pack.DIRT_KEPT,
                            cutsAMine ? Pack.MINER_COBBLESTONE_KEPT : Pack.COBBLESTONE_KEPT,
                            StockLevels.atMost(ItemClass.RAW_COPPER, k -> stockedAt(k, Optional.empty())));
                }
            }));
            Kit eating = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean hungry = status.condition().filter(c -> c.kit().isPresent()).map(SoldierErrands::wantsFood).orElse(false);
            // Feathers for arrows, for a soldier with a bow and few arrows: a chicken, hunted like any game.
            boolean wantsFeathers = inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED
                    && inventory.count(ItemClass.FEATHER) == 0;
            // Wool for a bed, for a soldier without one: a sheep, hunted like any game.
            boolean wantsWool = inventory.count(ItemClass.BED) == 0
                    && inventory.count(ItemClass.WOOL) < Recipe.BED.ingredients().get(ItemClass.WOOL);
            // Short of its basic load's low mark, it restocks: any game in sight is hunted, fed or not.
            boolean shortOfFood = eating != null && Subsistence.short_(eating.rations());
            IntPredicate quarry = hungry || shortOfFood ? kind -> true
                    : kind -> wantsFeathers && kind == EntityTracker.CHICKEN_TYPE_ID || wantsWool && kind == EntityTracker.SHEEP_TYPE_ID;
            // Food first: the day's work - gathering, lighting, walling, planting - gives the hands
            // up when there's game in sight to eat, and the hunter takes them next tick.
            Position huntGround = huntGrounds.getOrDefault(name, status.position());
            int food = status.condition().map(Condition::food).orElse(Condition.MAX_FOOD);
            boolean gameNear = (hungry || shortOfFood) && Hunter.gameInSight(world, huntGround, food, quarry);
            boolean huntWanted = (hungry || wantsFeathers || wantsWool || shortOfFood && gameNear) && huntAllowed.contains(name) && !inContact;
            // Short with no means of its own - no wheat to bake, no game in sight - it's the squad's
            // to solve: a squadmate shares, or the squad asks for food (Class I RED).
            boolean stockpiledFood = chainOfCommand.stockpile().map(st -> st.rations() > 0).orElse(false) && stockpileFor(name).isPresent();
            // On its squad's FORAGE, it gets its rations off the land (app.Foragers) - but one keeping a farm - and isn't foodless.
            boolean foodlessNow = shortOfFood && inventory.count(ItemClass.WHEAT) < 3 && !gameNear && !stockpiledFood;
            FarmPlan forage = foragers.plotFor(name, world, status.position(), taskOf(definition.squad(), Objective.Task.Forage.class).isPresent()
                    && dutyBook.of(name).filter(DutyOrder::farm).isEmpty() && eating != null && eating.rations() < Subsistence.BASIC_LOAD).orElse(null);
            if (foodlessNow && forage == null) {
                foodless.add(name);
            } else {
                foodless.remove(name);
            }
            // And a meal carried comes before either: live, a soldier on a stone run back to back sat at food 0 with two rations in its pack.
            boolean foodCalls = huntWanted && !hunter.active()
                    && Hunter.gameInSight(world, huntGround, food, quarry)
                    || !rations.active() && !threatened && inventory.slotOf(ItemClass.FOOD) >= 0
                    && Rations.wantsMeal(food, status.condition().map(c -> c.health() < Condition.MAX_HEALTH).orElse(false));
            turns.put(Hands.Routine.HUNTER, turn(hunter::active, free -> hunter.tick(status.position(), huntGround,
                    Hunter.radius(food), quarry, world, free && huntWanted,
                    WeaponStats.melee(eating == null ? Optional.empty() : eating.weapon()).ticksToCharge())
                    .ifPresent(target -> {
                        LOG.info("{} strikes the animal it hunts, entity {}", name, target);
                        session.attack(target);
                    })));
            turns.put(Hands.Routine.ARMOURER, turn(armourer::active,
                    free -> armourer.tick(status.position(), world, free ? craftable : Set.of(), threatened, workshop)));
            turns.put(Hands.Routine.BUILDER, turn(() -> fort != null && fort.allInside() && !builder.done(), free -> {
                if (free && atBuildPlace) {
                    boolean wasDone = builder.done();
                    built.set(builder.tickInPlace(status.position(), world, fort.cells()));
                    if (wasDone && built.get() instanceof Builder.Progress.Working) {
                        // A finished base lost a block - knocked out, blown up, dug through. Faster than
                        // the once-a-second breach check sees it, so say so here.
                        LOG.warn("{} repairing the patrol base - a block is gone", name);
                    }
                    builder.lookAt().ifPresent(soldier::lookAt);
                } else {
                    builder.stop();
                }
            }));
            turns.put(Hands.Routine.PLACING, turn(() -> placing.containsKey(name)
                    && (placeProgress.get(name) == null || placeProgress.get(name) instanceof Builder.Progress.Working), free -> {
                List<BlockPos> cells = placing.get(name);
                if (free && cells != null && !threatened) {
                    placeProgress.put(name, placer.tick(status.position(), world, cells));
                    placer.lookAt().ifPresent(soldier::lookAt);
                } else {
                    placer.stop();
                }
            }));
            Fitter chestFitter = storeFitters.get(name);
            Optional<Workshop> shop = Optional.ofNullable(workshops.get(name));
            // A full load and no chest to put it in: the workshop's is fitted first, at its stand.
            long fitNow = clockwork.millis();
            // And on the keep, the logs for it carried: the depot's chest goes up as soon as a builder has
            // them, not when its pack happens to fill (operator, 2026-11-14: a flattening campaign starts
            // with its supply depot). Live, builders carried 20 logs past a depot with no chest for days.
            boolean depotLogs = keepHands.contains(name) && inventory.count(ItemClass.LOG) >= DEPOT_LOGS;
            // A mine's chest is the stash's, fitted for ore only, from logs to spare beyond a pickaxe's
            // handles or a chest carried down: in the squad simulation a miner spent its logs on it,
            // climbed out for wood for a pickaxe, and stood at the top.
            boolean atMine = working != null && mineOfChest(chestCentre(working)).isPresent();
            // Or a producer with its yield and no dump yet, the camp's chest not standing (field503: 83 harvests, nothing stockpiled).
            boolean undumped = dutyBook.of(name).filter(o -> o.dump().isEmpty()).isPresent() && inventory.count(ItemClass.WHEAT) + inventory.count(ItemClass.LOG) >= BREAD_WHEAT;
            boolean fitStore = (atMine ? stashOre && (inventory.count(ItemClass.CHEST) > 0 || inventory.count(ItemClass.LOG) >= STASH_LOGS)
                    : fullLoad || depotLogs || Depositor.surplusFood(inventory) || poolOre || undumped) && supplyPoint.isEmpty() && shop.isPresent() && shop.get().chest().isPresent()
                    && !shop.get().chestStands(world) && !threatened && fort == null && !chestFits.barred(name, fitNow);
            // The main depot's by its stand, not the object: it's sited again each squad cycle.
            Workshop mainDepot = keepWorkshop;
            if (keepHands.contains(name) && shop.isPresent() && mainDepot != null && shop.get().stand().equals(mainDepot.stand())) {
                depositor.full().ifPresent(this::depotChestFull);
            } else if (shop.isPresent() && !atMine) {
                depositor.full().ifPresent(f -> workshopChestFull(shop.get(), f));
            }
            // Not while it cuts its way out (the recovery drill's stair up): a load put in or a stack
            // picked up is past the stair it hasn't cut. Live, two soldiers in a cave under the mine's
            // stairs, their stair up laid out, held their hands for a stockpile and a pickup with no route to either, and stood.
            // Or builds its way down (the drill's stair down, an Order.Place): live, a builder given one
            // held its hands for a stockpile it had no walk to, its order the stair's - Idle, with no
            // stand from a builder that never got the hands - and stood.
            boolean cuttingOut = clearingTunnel.contains(name) || placing.containsKey(name);
            turns.put(Hands.Routine.DEPOSITOR, turn(() -> (depositor.active() || chestFitter.active()) && !cuttingOut, handsFree -> {
                boolean free = handsFree && !cuttingOut;
                // Its chest under a block - not another chest - broken first: no chest opens under one (field508: Alice's salvage, 5 tries).
                Optional<BlockPos> lid = depositor.active() ? depositor.chestAt().filter(c -> Depositor.withinReach(status.position(), c))
                        .map(c -> BlockPos.containing(c.x(), c.y() + 1, c.z())).filter(l -> world.isSolid(l.x(), l.y(), l.z()) && !world.isChest(l.x(), l.y(), l.z()) && Clearing.diggable(world, l.x(), l.y(), l.z())) : Optional.empty();
                if (free && lid.isPresent()) {
                    lidCutter.tick(world, List.of(lid.get()));
                    return;
                }
                if (free && fitStore && atStand(status.position(), shop.get())) {
                    if (!chestFitter.tickChest(status.position(), world, shop.get(), threatened)) {
                        // Once, then later and later: live, it was said every tick with no log to make a table from.
                        chestFitter.failure().ifPresent(why -> {
                            chestFits.failed(name, fitNow, why);
                            LOG.warn("{} can't fit the workshop's chest: {} - tries again in a while", name, why);
                        });
                    }
                    chestFitter.lookAt().ifPresent(soldier::lookAt);
                    return;
                }
                chestFitter.stop(status.position(), world);
                Optional<Position> stockpile = stashOre && !depositor.active() || depositor.chestAt().flatMap(this::mineOfChest).isPresent() && depositor.active()
                        ? chestOf(working) : stockpileFor(name);
                // A flatten builder's surplus - cobblestone or dirt past what the stockpile needs - is left
                // at its depot's dump, a stack of each kept for steps.
                java.util.function.Predicate<Position> notFull = c -> !fullChests.contains(BlockPos.containing(c.x(), c.y(), c.z()));
                Optional<Position> deposit = (keepHands.contains(name) ? chestOf(shop.orElse(null)).filter(notFull).or(() -> stockpile)
                        : dutyBook.dump(name, world).or(() -> stockpile)).filter(notFull);
                Set<ItemClass> surplus = keepHands.contains(name) && !depositor.active() || depositor.dumping() ? surplusKinds(chestsSeen.values(), deposit) : Set.of();
                boolean dumpNow = fullLoad && surplus.stream().anyMatch(c -> inventory.count(c) > Depositor.KEEP);
                if (depositor.dumping() || dumpNow && !depositor.active()) {
                    depositor.tickDump(status.position(), shop.or(() -> Optional.ofNullable(mainDepot)).flatMap(d -> dumpOf(d, world)), surplus,
                            free && !threatened && fort == null && (depositor.dumping() || dumpNow));
                } else if (depositor.emptying() || smelter && !depositor.active() && rawCarried == 0 && toSmelt.isEmpty()
                        && furnaceDue(status.position()).isPresent()) {
                    // Between batches the smelter empties the force's furnaces of what's left in them.
                    Optional<Position> furnace = depositor.emptying() ? depositor.chestAt() : furnaceDue(status.position());
                    if (!depositor.emptying()) {
                        furnace.ifPresent(f -> furnacesSwept.put(BlockPos.containing(f.x(), f.y(), f.z()), clockwork.millis()));
                    }
                    depositor.tickEmpty(status.position(), furnace, free && !threatened && fort == null);
                } else if (carried != null && Subsistence.short_(carried.rations()) && chestHolding(ItemClass.FOOD, status.position()).isPresent()) {
                    // No rations: bread first, from the nearest chest holding some - not after metal and stone, nor only its own depot's. Live (field568), Eli drew iron and cobblestone all run, shot dead at 1 health.
                    depositor.tickDraw(status.position(), chestHolding(ItemClass.FOOD, status.position()), ItemClass.FOOD, Subsistence.RESUPPLY, free && !threatened);
                } else if (farmer.outOfSeeds() && chestHolding(ItemClass.SEEDS, status.position()).isPresent()) { // its sowing's seeds, drawn (field603)
                    depositor.tickDraw(status.position(), chestHolding(ItemClass.SEEDS, status.position()), ItemClass.SEEDS, SEEDS_DRAWN, free && !threatened);
                } else if (unstash.isPresent()) {
                    depositor.tickDraw(status.position(), Optional.of(unstash.get().chest()), unstash.get().ore(), unstash.get().carry(),
                            free && !threatened);
                } else if (keepClimbDrawing.contains(name)) {
                    // From the nearest chest holding stone, as metal and logs are drawn.
                    depositor.tickDraw(status.position(), metalChest(ItemClass.COBBLESTONE, status.position()).or(() -> stockpile),
                            ItemClass.COBBLESTONE, WALL_BLOCKS_LOAD, free && !threatened);
                } else if (drawingStone.contains(name) || keepDrawing.contains(name)) {
                    depositor.tickDraw(status.position(), stockpile, ItemClass.COBBLESTONE, WALL_BLOCKS_LOAD, free && !threatened);
                } else if (drawsLogs(armourer.failure().or(chestFitter::failure).or(() -> torchShort), inventory.count(ItemClass.LOG), chainOfCommand.stores(), logsAt)
                        || depositor.drawing(ItemClass.LOG)) {
                    depositor.tickDraw(status.position(), logsAt.or(depositor::chestAt), ItemClass.LOG, DRAW_LOGS, free && !threatened
                            && drawsLogs(armourer.failure().or(chestFitter::failure).or(() -> torchShort), inventory.count(ItemClass.LOG), chainOfCommand.stores(), logsAt));
                } else if (drawsStone(armourer.failure(), inventory.count(ItemClass.COBBLESTONE), chainOfCommand.stores(), stockpile)) {
                    depositor.tickDraw(status.position(), metalChest(ItemClass.COBBLESTONE, status.position()).or(() -> stockpile), ItemClass.COBBLESTONE,
                            DRAW_STONE, free && !threatened);
                } else if (drawsFood(carried, chainOfCommand.stockpile(), status.position(), stockpile) || depositor.drawing(ItemClass.FOOD)) {
                    depositor.tickDraw(status.position(), stockpile, ItemClass.FOOD, Subsistence.RESUPPLY, free && !threatened
                            && drawsFood(carried, chainOfCommand.stockpile(), status.position(), stockpile));
                } else if (depositor.drawing(ItemClass.WHEAT) || bakes && inventory.count(ItemClass.WHEAT) < BREAD_WHEAT
                        && chestHolding(ItemClass.WHEAT, status.position()).isPresent()) {
                    // The baker's wheat, from the stockpile the harvesters fill (command.FarmDuties).
                    depositor.tickDraw(status.position(), chestHolding(ItemClass.WHEAT, status.position()).or(depositor::chestAt), ItemClass.WHEAT,
                            BAKER_WHEAT, free && !threatened && fort == null && bakes && inventory.count(ItemClass.WHEAT) < BREAD_WHEAT);
                } else if (depositor.drawing(ItemClass.STRING) || bedString(inventory) > 0
                        && chestHolding(ItemClass.STRING, status.position()).isPresent()) {
                    // A bed's string from the depot, for a soldier without one (operator, 2026-11-18: beds, for a
                    // safe respawn): the night's spiders' string is stored there, four to a wool.
                    depositor.tickDraw(status.position(), chestHolding(ItemClass.STRING, status.position()).or(depositor::chestAt), ItemClass.STRING,
                            inventory.count(ItemClass.STRING) + bedString(inventory), free && !threatened && fort == null && bedString(inventory) > 0);
                } else if (metalDraw.isPresent() || MetalPool.METALS.stream().anyMatch(depositor::drawing)) {
                    ItemClass ore = metalDraw.map(MetalPool.Draw::ore)
                            .orElseGet(() -> MetalPool.METALS.stream().filter(depositor::drawing).findFirst().orElseThrow());
                    Optional<Position> holding = metalDraw.flatMap(d -> metalChest(d.ore(), status.position())).or(depositor::chestAt);
                    depositor.tickDraw(status.position(), holding, ore, metalDraw.map(MetalPool.Draw::carry).orElse(inventory.count(ore)),
                            free && !threatened && fort == null && metalDraw.isPresent());
                } else {
                    Set<ItemClass> pooled = new HashSet<>(metal.stock());
                    pooled.removeIf(kind -> StockLevels.atMost(kind, k -> stockedAt(k, Optional.empty()))); // copper past 256 (StockLevels)
                    if (stashOre) {
                        pooled.addAll(STASHED);
                    }
                    // What it carries past its work's load goes in too (LoadPlan; backlog/load-plans.md): a mine's cutters mine, the
                    // rest work the surface by the base (field505: Gus 11 shovels, Dana 5 swords). A trip of its own once it's a few slots.
                    Optional<LoadPlan> plan = Optional.of(cutting.containsKey(name) ? LoadPlan.MINING : LoadPlan.FLATTENING);
                    boolean surplusTrip = plan.map(p -> p.surplus(inventory).size() >= SURPLUS_TRIP).orElse(false);
                    // The depot's kinds past what the pack keeps go in with it while the depot's under its
                    // most (StockLevels) - not into a mine's stash - and a reserve run short is a trip of its own.
                    Map<ItemClass, Integer> packKept = planting.containsKey(name) ? SoldierErrands.PLANTER_CARRIED : keepHands.contains(name) ? KEEP_PACK
                            : cutting.containsKey(name) ? MINE_CARRIED
                            : dutyBook.is(name, DutyOrder.Duty.HARVESTER) ? HARVESTER_CARRIED : CARRIED;
                    boolean toDepot = deposit.flatMap(this::mineOfChest).isEmpty();
                    boolean stockTrip = toDepot && StockLevels.reserveShort(inventory, packKept, k -> stockedAt(k, deposit));
                    // A flatten builder's load into the nearest depot standing a chest (keepOutposts).
                    // Not to a chest known full: a trip there fails, and is made again. Live (field285), with
                    // the depots' rows filled with cobblestone, the crew's ticks were 38% depositor - trip
                    // after failed trip (deposit, above). With every chest it could go to full, its
                    // cobblestone and dirt go to the dump (surplusKinds), the rest carried on full.
                    // Not up out of the mine by night for a load - the stash's ore goes in below; the rest
                    // waits for day. Live, the night shift's loads were for the camp's chest 85 blocks off and up the stairs, into the dark.
                    Integer nightFloor = nightBelow.get(unitOf(name, definition.squad()));
                    boolean downForTheNight = nightFloor != null && status.position().y() < nightFloor - MINE_BELOW && !stashOre;
                    boolean breadWanted = toDepot && !StockLevels.atMost(ItemClass.FOOD, k -> stockedAt(k, Optional.empty()));
                    depositor.storesFood(breadWanted);
                    // Not while the recovery drill has it: its way out is its work, as for the supply run. Live
                    // (field424), Eli on his stair up from y=-43 set off for the depot 49 times in ten minutes, each
                    // trip taking his hands from the stair and dropped as no longer wanted.
                    depositor.tick(status.position(), deposit, free && (fullLoad || breadWanted && Depositor.surplusFood(inventory) || poolOre || stashOre
                                    || surplusTrip || stockTrip || depositor.pastHalfway()) && !threatened && fort == null && !downForTheNight && !recovering.contains(name), pooled,
                            inv -> {
                                List<Integer> slots = new ArrayList<>(plan.map(p -> p.surplus(inv)).orElse(List.of()));
                                if (toDepot) {
                                    StockLevels.toStore(inv, packKept, k -> stockedAt(k, deposit)).stream().filter(s -> !slots.contains(s)).forEach(slots::add);
                                }
                                return slots;
                            });
                }
            }));
            storeWalks.put(name, fitStore && !atStand(status.position(), shop.get())
                    ? Optional.of(new Position(shop.get().stand().x() + 0.5, shop.get().stand().y(), shop.get().stand().z() + 0.5))
                    : Optional.empty());
            turns.put(Hands.Routine.HANDOVER, turn(handover::active, free -> {
                Optional<Handover.Delivery> delivery = !free || giving == null || threatened || fort != null
                        ? Optional.empty()
                        : Optional.ofNullable(activeSoldiers.get(giving.receiver()))
                                .map(receiver -> new Handover.Delivery(giving.receiver(), receiver.status().position(), giving.item(), giving.count()));
                handover.tick(status.position(), delivery).ifPresent(soldier::lookAt);
            }));
            turns.put(Hands.Routine.MINER, turn(() -> stairs != null && miner.active(), free -> {
                if (stairs == null) {
                    miner.stop();
                } else {
                    if (status.report() instanceof Report.Cantco(String why) && why.startsWith("no route") && status.order() instanceof Order.MoveTo to
                            && miner.standAt().filter(st -> Math.hypot(st.x() - to.x(), st.z() - to.z()) < 0.5 && Math.abs(st.y() - to.y()) < 1).isPresent()) {
                        miner.unreachable(status.position(), why); // its cut's stand has no route (Miner.unreachable)
                    }
                    Miner.Progress cut = miner.tick(status.position(), world, stairs,
                            free && !threatened && armourer.standAt().isEmpty(), Optional.of(mineShare(name, stairs)),
                            Math.max(0, mineCrew(stairs).indexOf(name)), levelsBlocked.computeIfAbsent(stairs.head() + "/" + stairs.bottom(),
                                    k -> ConcurrentHashMap.newKeySet()));
                    stairsProgress.put(name, cut);
                    if (cut instanceof Miner.Progress.Working(int step, int steps)) {
                        stairsCut.accumulateAndGet(Math.min(step, stairs.steps()), Math::max);
                        if (step >= stairs.steps()) {
                            stairsDone.set(true); // down to the iron: the tunnels are the mining
                        }
                    } else if (cut instanceof Miner.Progress.Done) {
                        stairsDone.set(true);
                    }
                }
            }));
            List<BlockPos> wall = walling.getOrDefault(name, List.of());
            pickaxeTiers.put(name, Clearer.bestPickaxeTier(inventory));
            soldierIron.put(name, inventory.count(ItemClassRegistry.ItemClass.RAW_IRON) + inventory.count(ItemClassRegistry.ItemClass.IRON_INGOT));
            // On a stair up and not cutting: what holds it, said each time that changes - live (field401), Eli's
            // hands were free, his clearer not stuck, and no cell of his 304 cut in four hours.
            // Its way out is held only by a hostile at hand, not one in sight across the cave: live (field403),
            // Alice stood on her stair up at y=18 while a spider 10 below and 8 across was in sight.
            // Nor by its own contact report, which a hostile in sight makes: live (field426), Eli's stair up at y=-43
            // was held "a hostile at hand: []" ten times a minute by a skeleton across the cave, till it shot him
            // where he stood - the stair cut into the rock is out of its sight.
            boolean stairThreat = !world.nearbyHostiles(status.position(), STAIR_THREAT_RADIUS).isEmpty();
            if (clearingTunnel.contains(name) && !clearer.active() && !clearing.getOrDefault(name, List.of()).isEmpty()) {
                String hold = stairThreat ? "a hostile at hand: " + world.nearbyHostiles(status.position(), STAIR_THREAT_RADIUS).stream()
                        .map(h -> HostileMobRegistry.nameOf(h.typeId())).sorted().toList()
                        : foodCalls ? "a meal or game in sight"
                        : clearer.idle().map(why -> "its clearer: " + why).orElse(null);
                String was = stairHolds.get(name);
                if (hold != null && !hold.equals(was)) {
                    LOG.warn("{} on its stair up doesn't cut: {}", name, hold);
                }
                if (hold == null) {
                    stairHolds.remove(name);
                } else {
                    stairHolds.put(name, hold);
                }
            }
            turns.put(Hands.Routine.CLEARER, turn(clearer::active, free -> clearer.tick(status.position(), world,
                    // No new block while it's on its way to make kit: the walk to a workshop holds no hands,
                    // so each block taken dropped the errand and each gap between blocks began it again.
                    // Live, a builder whose pickaxe had worn out dug stone by hand, a block every nine
                    // seconds, and never left for the depot's table 25 blocks off (operator, 2026-11-14:
                    // fists are for when a better tool can't be had); in the squad simulation one with no
                    // shovel set out for the workshop three times a second for as long as it dug. Nor while
                    // it's on its way to fit the depot's chest, a walk as handless: there, one with a full
                    // pack and the logs for the next chest dug on beside a full one.
                    clearing.getOrDefault(name, List.of()), free && !(keepHands.contains(name) ? inContact
                            : clearingTunnel.contains(name) ? stairThreat : threatened) && !(foodCalls || forage != null)
                            && (clearingTunnel.contains(name) // its way out comes before any errand
                            || armourer.standAt().isEmpty() && storeWalks.getOrDefault(name, Optional.empty()).isEmpty()),
                    // Its stands in its rectangle or a column beside it: the last block of a rectangle one
                    // wide is cut from the neighbour's edge, not from on top of itself.
                    keepHands.contains(name) ? Optional.ofNullable(clearingStrip.get(name))
                            .map(b -> new Box(b.minX() - 1, b.minY(), b.minZ() - 1, b.maxX() + 1, b.maxY(), b.maxZ() + 1)) : Optional.empty(),
                    clearingTunnel.contains(name), scaffolding::contains)));
            Filler filler = soilFilling.contains(name) ? soiler : stoneFilling.contains(name) ? paver : plainFiller;
            Stream.of(plainFiller, paver, soiler).filter(f -> f != filler).forEach(Filler::stop);
            fillers.put(name, filler);
            turns.put(Hands.Routine.FILLER, turn(filler::active, free -> {
                filler.tick(status.position(), world, filling.getOrDefault(name, List.of()), free && !threatened && !(foodCalls || forage != null));
                filler.lookAt().ifPresent(soldier::lookAt);
            }));
            BlockPos wallCentre = wallCentres.getOrDefault(name,
                    BlockPos.containing(status.position().x(), status.position().y(), status.position().z()));
            List<BlockPos> repoint = repointing.getOrDefault(name, List.of());
            turns.put(Hands.Routine.MASON, turn(mason::active, free -> {
                // Its dirt replaced while there's no open cell it can build: live, one cell nobody could reach held a whole wall's stone back.
                if (!repoint.isEmpty() && inventory.count(ItemClass.COBBLESTONE) > 0
                        && (wall.isEmpty() || mason.nothingToBuild() || mason.active() && mason.repointing())) {
                    mason.repoint(status.position(), world, wallCentre, WALL_RADIUS, repoint, free && !threatened && !(foodCalls || forage != null));
                } else {
                    mason.tick(status.position(), world, wallCentre, WALL_RADIUS, wall, free && !threatened && !(foodCalls || forage != null));
                }
            }));
            turns.put(Hands.Routine.LAMPLIGHTER, turn(lamplighter::active,
                    free -> lamplighter.tick(status.position(), world, lights, free && !threatened && !(foodCalls || forage != null))));
            TreeFarm grove = planting.get(name);
            turns.put(Hands.Routine.FORESTER, turn(forester::active,
                    free -> forester.tick(status.position(), world, Optional.ofNullable(grove), free && !threatened && !(foodCalls || forage != null))));
            turns.put(Hands.Routine.FARMER, turn(farmer::active, free -> {
                // Its stand has no route: the farmer is told, and takes the next cell (Farmer.unreachable).
                if (status.report() instanceof Report.Cantco(String why) && why.startsWith("no route")
                        && status.order() instanceof Order.MoveTo to && farmer.standAt()
                        .filter(stand -> Math.hypot(stand.x() - to.x(), stand.z() - to.z()) < 0.5 && Math.abs(stand.y() - to.y()) < 1).isPresent()) {
                    farmer.unreachable(why);
                }
                farmer.tick(status.position(), world, Optional.ofNullable(forage != null ? forage : field), free && !threatened && !foodCalls
                        && status.position().y() >= SEA_LEVEL - CampGround.BELOW_GROUND); // not from down the mine: field635, 46 "no route" from y=8
            }));
            // A supply run from the ground work's drill first: so much of a resource, from that box.
            Order.Gather groundRun = groundGather.get(name);
            turns.put(Hands.Routine.GATHERER, turn(gatherer::active, free -> {
                if (groundRun != null) {
                    Box b = groundRun.box();
                    // Not while it's on its way to make kit, as for a clear: a lumberjack sent for an axe
                    // chopped by hand on the way, and had its logs before it reached the table.
                    gatherer.tick(status.position(), world, Optional.of(groundRun.resource()), nearestIn(b, status.position()),
                            free && !inContact && !(foodCalls || forage != null) && armourer.standAt().isEmpty(), OptionalInt.of(groundRun.amount()),
                            Optional.of(b).filter(x -> woodcutters.contains(name)));
                } else {
                    gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                            gather == null ? status.position() : gather.location(), free && !inContact && !(foodCalls || forage != null));
                }
            }));
            turns.put(Hands.Routine.SCAVENGER, turn(() -> scavenger.active() && !cuttingOut,
                    free -> scavenger.tick(status.position(), world, free && !cuttingOut && !threatened && fort == null && !(foodCalls || forage != null),
                            item -> atADump(item) || thrownNear(item))));
            Optional<Hands.Routine> hands = Hands.tick(turns);
            placementAudit.routine(name, hands);
            afterActionReview.errands.tick(name, hands, status.position(), ErrandLedger.carried(inventory), clockwork.millis())
                    .forEach(line -> LOG.info(line.startsWith("errands") ? "[Errand] {}" : "[Productivity] {}", line)); // a trip's yield against its walk
            if (keepHands.contains(name)) {
                keepSaplingsCarried.put(name, inventory.count(ItemClass.SAPLING));
                clearer.drainTimes().forEach(t -> keepPace.merge(name, t, LevelPace.Tally::plus));
                KeepPlan levelPlan = keepPlan;
                // With no routine holding its hands, why it has no rectangle - so "waiting" is only a builder on the crew the plan gave nothing to.
                String held = hands.map(r -> r.name().toLowerCase(java.util.Locale.ROOT))
                        .orElseGet(() -> levelPlan != null && levelPlan.boxes().containsKey(name) && !keepDue.containsKey(name)
                                ? "done, waiting for the level"
                                // Held off its rectangle by another order - an errand, a fight's move -
                                // not by its clearer: its squad's order isn't its dig, so no cover is
                                // routed (backlog/keep-pace.md's "nothing to cut").
                                : levelPlan != null && levelPlan.boxes().containsKey(name) && !clearing.containsKey(name)
                                ? "held off its rectangle (" + status.order().getClass().getSimpleName() + ")"
                                : levelPlan == null || levelPlan.boxes().containsKey(name)
                                ? clearer.idleReason().map(r -> "clearer idle, " + r)
                                .orElse("hands free, " + status.order().getClass().getSimpleName())
                                : name.equals(keepSmelter) ? "smelter, walking"
                                : inWoodParty(name) ? "wood party, walking"
                                : name.equals(keepRoadCutter) ? "haul road, walking"
                                : !levelPlan.crew().contains(name) ? "off the crew (" + status.order().getClass().getSimpleName() + ")"
                                : "waiting, no rectangle");
                if (!chainOfCommand.time().map(TimeOfDay::dark).orElse(false)) {
                    keepSpent.computeIfAbsent(name, n -> new ConcurrentHashMap<>()).merge(held, 1, Integer::sum);
                }
            } else {
                clearer.drainTimes();
            }
            if (gatherer.exhausted()) {
                gatherFoundNothing.put(name, clockwork.millis());
            }
            hands.ifPresentOrElse(h -> handsHeld.put(name, h), () -> handsHeld.remove(name));
            // Stand-to: hands free, the soldier wears the best armour it carries and holds its best
            // weapon - not the seeds or the dirt its last job left in its hand.
            if (hands.isEmpty() && standTo.merge(name, -1, Integer::sum) <= 0) {
                standTo.put(name, STAND_TO_TICKS);
                synchronized (inventory) {
                    Quartermaster.equip(inventory)
                            .forEach(step -> Quartermaster.perform(step, session, inventory));
                }
            }
            FlightRecorder recorder = recorders.computeIfAbsent(name, n -> new FlightRecorder());
            if (recorder.due()) {
                // Hiding or sleeping: the squad in its patrol base, or down its mine for the night. Anything else still that long is worth a look.
                Integer below = nightBelow.get(unitOf(name, definition.squad()));
                // Or holding where its squad leader sent it - and got to: waiting there is the order.
                boolean resting = taskOf(unitOf(name, definition.squad()), Objective.Task.PatrolBase.class).isPresent()
                        || (planBook.of(name).role() == Role.RIFLEMAN
                        && planBook.of(name).current().filter(d -> d instanceof Directive.GoTo).isPresent()
                        // Or any soldier saying it's in position: a builder told to wait for the next
                        // rectangle where it stands is idle by its order, not stuck.
                        || status.report() instanceof Report.InPosition)
                        && IdleWatch.holdingWhereSent(status.order(), status.position())
                        || below != null && status.position().y() < below - MINE_BELOW;
                afterActionReview.commands.sample(squadTask(unitOf(name, definition.squad())), status.order().getClass().getSimpleName(), status.report(),
                        CampGround.area(status.position(), chainOfCommand.foothold().map(Foothold::camp), WALL_RADIUS));
                idleWatch.update(name, status.position(), clockwork.millis(), resting, () -> "report " + status.report()
                        + ", order " + status.order() + ", hands " + hands.map(Enum::name).orElse("free")
                        // What holds routines back with free hands: food first, and the work it was handed.
                        + (foodCalls ? ", food calls" : "") + (threatened ? ", threatened" : "")
                        // In contact with what, and where: live, a squad stood a night "in contact" with
                        // one hostile each, and nothing said what or where it was.
                        + (status.report() instanceof Report.InContact ? IdleWatch.nearestHostile(world.nearbyHostiles(status.position(), 24), status.position()) : "")
                        + ", " + lights.size() + " cells to light with " + inventory.count(ItemClass.TORCH) + " torches"
                        + (lights.isEmpty() ? "" : " (" + lamplighter.passedOver(world, lights) + ")")
                        + (farming.get(name) == null ? "" : ", farm " + farmer.passedOver(world, farming.get(name)))
                        // The world model's own blocks where it's sent, to check against the server's.
                        + (status.order() instanceof Order.MoveTo m ? ", model blocks there " + modelBlocks(m) : "")
                        + ", squad's task " + objectiveOf(unitOf(name, definition.squad()))
                        .map(o -> OrderTrace.taskName(o.task()) + " at " + o.location()).orElse("none"))
                        .ifPresent(line -> {
                            LOG.warn("[Alert] {}", line);
                            if (line.contains("stationary")) {
                                afterActionReview.idle(name);
                                worldRecords.record(chunkCache::stateIdAt, status, clockwork.millis()); // ground and case for the simulation
                            }
                        });
                // And holding where sent by day with work to be had: without work, the squad leader's to give it.
                withoutWork.update(name, status.position(), clockwork.millis(), !IdleWatch.withoutWork(resting, objectiveOf(unitOf(name, definition.squad())), below != null),
                        () -> "order " + status.order() + ", report " + status.report()).ifPresent(line -> LOG.warn("[Alert] {}", line));
                afterActionReview.stoodStill.second(name, unitOf(name, definition.squad()), hands.isEmpty() && !(status.report() instanceof Report.InContact)
                        ? idleWatch.stillFor(name, clockwork.millis()) : 0, withoutWork.stillFor(name, clockwork.millis()), clockwork.millis())
                        .ifPresent(line -> LOG.info("[Idle] {}", line)); // stood doing nothing: the soldier's, or its chain of command's
                // And on top of another soldier, moving or not (CrowdWatch) - looked at once a second.
                Map<String, Position> everyone = new HashMap<>();
                if (crowdTicks.getAndIncrement() % 20 == 0) {
                    activeSoldiers.forEach((other, s) -> everyone.put(other, s.status().position()));
                }
                if (!everyone.isEmpty()) {
                    crowdWatch.update(name, status.position(), clockwork.millis(), resting, everyone,
                                    () -> "report " + status.report() + ", order " + status.order() + ", hands " + hands.map(Enum::name).orElse("free"))
                            .ifPresent(line -> LOG.warn("[Alert] {}", line));
                }
                Optional<String> nearest = world.nearbyHostiles(status.position(), RECORDED_HOSTILE_RADIUS).stream()
                        .min(Comparator.comparingDouble(h -> distance(h.position(), status.position())))
                        .map(h -> String.format(Locale.ROOT, "%s %.1f away",
                                HostileMobRegistry.nameOf(h.typeId()), distance(h.position(), status.position())));
                recorder.record(new FlightRecorder.Sample(clockwork.millis(), status.position(), status.report().toString(),
                        (float) (double) status.condition().map(Condition::health).orElse(-1.0),
                        status.condition().map(Condition::food).orElse(-1), hands.map(Enum::name), nearest));
            }
            // Short of logs for tools it tried and failed to chop wood for: a squadmate may have some to spare (§13).
            int logsShort = tools.isEmpty() ? 0 : Armourer.logsShort(inventory, tools);
            if (logsShort > 0 && !armourer.active() && armourer.failure().isPresent()) {
                logsWanted.put(name, logsShort);
            } else {
                logsWanted.remove(name);
            }
            soldier.task(fort != null
                    ? (built.get() instanceof Builder.Progress.Failed(String why) ? new Report.Cantco("can't finish the patrol base: " + why)
                            : built.get() instanceof Builder.Progress.Done ? null : new Report.Fortifying())
                    : stairs != null && stairsProgress.get(name) instanceof Miner.Progress.Working(int step, int steps)
                    ? new Report.Mining(step + 1, steps)
                    : stairs != null && stairsProgress.get(name) instanceof Miner.Progress.Stopped(String why)
                    ? new Report.Cantco("can't cut the staircase further: " + why)
                    : placing.containsKey(name) && placeProgress.get(name) instanceof Builder.Progress.Failed(String why)
                    ? new Report.Cantco("can't build what was laid out: " + why)
                    : placing.containsKey(name) && !(placeProgress.get(name) instanceof Builder.Progress.Done)
                    ? new Report.Building((int) placing.get(name).stream().filter(c -> world.isSolid(c.x(), c.y(), c.z())).count(),
                            placing.get(name).size())
                    // Stuck in the water says so, in the stranded soldier's words, so the squad's
                    // recovery drill sends blocks: live, two soldiers afloat reported the wood they meant to gather, and nobody came.
                    : stuckInWater ? new Report.Cantco(String.format(Locale.ROOT, "no route from (%d, %d, %d) out of the water",
                            ax, (int) Math.floor(at.y()), az))
                    : hunter.active() ? new Report.Hunting()
                    // A ground-work command is reported as itself, between one cut and the next too: live,
                    // a soldier keeping the camp between cuts reported its lamplighter's CANTCO, raised anew
                    // each cycle. A dirt run from the drill has no gather objective behind it.
                    : groundGather.get(name) instanceof Order.Gather g
                    ? new Report.Working("gather " + g.resource(), Math.max(0, g.amount() - inventory.count(switch (g.resource()) {
                        case WOOD -> ItemClass.LOG;
                        case STONE -> ItemClass.COBBLESTONE;
                        case SOIL, FARMLAND -> ItemClass.DIRT;
                        case FLINT -> ItemClass.FLINT;
                    })))
                    : clearer.active() || clearing.containsKey(name) && clearer.stuck().isEmpty()
                    ? new Report.Working(keepHands.contains(name) ? "dig the keep" : "dig the camp's ground", clearing.getOrDefault(name, List.of()).size())
                    : filler.active() || filling.containsKey(name)
                    ? new Report.Working(keepHands.contains(name) ? "fill the keep" : "fill the camp's ground", filling.getOrDefault(name, List.of()).size())
                    : mason.active() ? new Report.Fortifying()
                    // Out of ways of its own - each of its last tries failed, on whatever it tried -
                    // it asks for help: live, a soldier opened a chest that wasn't there all night.
                    : !clearing.getOrDefault(name, List.of()).isEmpty() && clearer.stuck().isPresent() ? new Report.Cantco(clearer.stuck().get())
                    // Each routine's CANTCO only while its work is the soldier's task: live, soldiers
                    // levelling the camp reported the mason's "none to build" between their cuts.
                    : !clearing.containsKey(name) && !walling.getOrDefault(name, List.of()).isEmpty() && mason.stuck().isPresent()
                    ? new Report.Cantco(mason.stuck().get())
                    // Not a keep builder's: a depot chest too full to take its load is the depot's shortfall,
                    // and it digs on with what it carries. Live, the depot's first chest filled with one
                    // builder's load, and four more reported the dig CANTCO from beside it.
                    : depositor.stuck().isPresent() && !depositor.active() && !keepHands.contains(name) ? new Report.Cantco(stockpileUnreached(name, status.position(), depositor))
                    : forester.active() ? new Report.Planting()
                    : grove != null && forester.failure().isPresent() && !Forester.planted(world, grove)
                    ? new Report.Cantco("can't plant the tree farm: " + forester.failure().get())
                    // On its way to the workshop for them counts: live, that walk was reported "short of {}".
                    : lamplighter.active() || (torchmaker.active() || torchmaker.standAt().isPresent()) && torchesWanted ? new Report.Securing()
                    : !clearing.containsKey(name) && !walling.containsKey(name) && !lights.isEmpty() && lamplighter.stuck().isPresent()
                    ? new Report.Cantco(lamplighter.stuck().get())
                    : !lights.isEmpty() && torchesWanted && torchmaker.failure().isPresent()
                    ? new Report.Cantco("can't make torches to light the ground: " + torchmaker.failure().get())
                    // Gone for the makings, between one dig and the next: still at it, not unable.
                    : !lights.isEmpty() && torchesWanted && gather != null && gather.gather().isPresent() && !gatherer.exhausted()
                    ? new Report.Gathering(gather.gather().get())
                    // Nothing to make them from, and nothing said: the crafter doesn't start what it can't finish.
                    : !lights.isEmpty() && torchesWanted && !torchmaker.active() && !gatherer.active() && !armourer.active()
                    ? new Report.Cantco("no torches to light the ground with, and " + torchShortfall(inventory, workshops.get(name)))
                    : farming.containsKey(name) && !farmer.active() && farmer.stuck().isPresent() ? new Report.Cantco(farmer.stuck().get())
                    : !farming.containsKey(name) && farmless.containsKey(name) ? new Report.Cantco(farmless.get(name))
                    : farmer.active() || armourer.active() && armourer.making().stream()
                            .allMatch(r -> r == Recipe.WOODEN_HOE || r == Recipe.BREAD) ? new Report.Farming()
                    : armourer.active()
                    ? (armourer.making().equals(Set.of(ORDER_PICKAXE))
                            ? new Report.Gathering(Resource.STONE) : new Report.Rearming())
                    : depositor.active() ? new Report.Gathering(carried != null && carried.stone() >= Gatherer.CARRY_STONE
                            ? Resource.STONE : Resource.WOOD)
                    : handover.active() ? handover.report().orElseThrow()
                    : gatherer.active() ? new Report.Gathering(gatherer.gathering().orElseThrow())
                    : gather != null && gatherer.exhausted()
                    ? new Report.Cantco("nothing left to gather within 48 blocks of the objective")
                    // Only a tool the order needs makes its failure the order's: a pickaxe to quarry with.
                    // A sword is the soldier's own pre-combat check - going without shows in the LOGSTAT
                    // as bare-handed, not as CANTCO of the mission.
                    : needsPickaxe && armourer.failure().isPresent() ? new Report.Cantco(armourer.failure().get())
                    : patrolLegs.containsKey(name) && !inContact ? new Report.Patrolling() : null);
            Position current = status.position();
            if (soldier.sprinting() != sprintingSent.get()) {
                sprintingSent.set(soldier.sprinting());
                session.setSprinting(soldier.sprinting());
            }
            if (soldier.shieldRaised() != shieldSent.get()) {
                shieldSent.set(soldier.shieldRaised());
                if (soldier.shieldRaised()) {
                    LOG.info("{} raises its shield", name);
                    session.raiseShield(soldier.yaw(), soldier.pitch());
                } else {
                    session.releaseUseItem();
                }
            }
            // Never a position the server kicks for: a coordinate or an angle that isn't a finite number
            // is "Invalid move player packet received" - live, Bob was kicked and rejoined 671 times in
            // forty minutes. Not sent, and said loudly with what the soldier was at, so it can be found.
            if (!Double.isFinite(current.x()) || !Double.isFinite(current.y()) || !Double.isFinite(current.z())
                    || !Float.isFinite(soldier.yaw()) || !Float.isFinite(soldier.pitch())) {
                if (badPositionLogged.add(name)) {
                    LOG.error("{} would send a position the server kicks for - not sent: at {}, yaw {}, pitch {}, order {}, report {}, hands {}",
                            name, current, soldier.yaw(), soldier.pitch(), status.order(), status.report(), hands.map(Enum::name).orElse("free"));
                }
            } else {
                badPositionLogged.remove(name);
                // Never an enderman's eyes met: its facing turned down off them (operator, 2026-11-17: do not provoke).
                float pitch = io.github.jayessdeesea.minecraft.bot.EndermanGaze.pitch(
                        new Position(current.x(), current.y() + 1.62, current.z()), soldier.yaw(), soldier.pitch(),
                        entityTracker.endermenWithin(current, 64));
                session.reportPosition(current.x(), current.y(), current.z(), soldier.yaw(), pitch, soldier.onGround());
            }
            // The bow: drawn with Use Item once it's in hand, loosed with its release - after the
            // position, so the server has the aim the arrow flies on.
            if (soldier.takeLoose()) {
                session.releaseUseItem();
                LOG.info("{} looses an arrow", name);
                drawingSent.set(false);
            }
            if (soldier.drawing() != drawingSent.get()) {
                drawingSent.set(soldier.drawing());
                if (soldier.drawing()) {
                    if (quiver.ready()) {
                        session.useItem(soldier.yaw(), soldier.pitch());
                    }
                } else {
                    session.releaseUseItem();
                    quiver.stow();
                }
            }
            // A pillar step's block goes in after the server has the soldier's new height - it won't place a block where
            // a body is - and into the world model at once, so the soldier lands on it before the server's update.
            List<BlockPos> cut = soldier.cutting();
            if (!cut.isEmpty()) {
                stepCutter.tick(world, cut);
                takeBackSnag(name, stepCutter); // a step cut into a snag that isn't a block, as the doorway's
            } else {
                stepCutter.stop();
            }
            // Cobblestone stands in for whatever went in, dirt included, until the server's update says which. A torch in
            // the cell is broken first - it goes at a touch, and the server won't put a block where one is: live, a
            // soldier pillaring out of a pocket from a torch's cell jumped in place for good.
            soldier.takeTrip().ifPresent(t -> ledger.route(t.planned(), t.ticks(), clockwork.millis()));
            farmer.takeYields().forEach((kind, y) -> ledger.record(kind, y[0], y[1], clockwork.millis()));
            soldier.takePlacement().filter(cell -> !bodyIn(name, cell)).filter(cell -> !refusedStep(name, cell)).ifPresent(cell -> {
                if (world.isTorch(cell.x(), cell.y(), cell.z())) {
                    session.startDigging(cell.x(), cell.y(), cell.z());
                    session.finishDigging(cell.x(), cell.y(), cell.z());
                }
                // A solid block in the model until the server's update says what landed, a tick on: stood on before the
                // server confirms it - over an open cell only, and taken back if the server never does (ChunkCache.standIn).
                placementAudit.routeStep(name, soldier.walkRound(world)); // its walk's step, no routine's - and whether a walk round was there
                Builder.support(world, cell).flatMap(face -> footing.placeStep(cell, face, builtCell(cell)))
                        .ifPresent(block -> {
                            chunkCache.standIn(cell.x(), cell.y(), cell.z(), COBBLESTONE_STATE, clockwork.millis());
                            scaffolding.add(cell);
                            stepPlacedAt.put(cell, clockwork.millis());
                            // Who puts what in the keep: a block there over its floor holds the site's level.
                            if (chainOfCommand.flatten().benchedContains(cell.x(), cell.z()) && cell.y() >= chainOfCommand.flatten().bottom()) {
                                LOG.info("[Keep] {} puts a step at {} {} {} - {}", name, cell.x(), cell.y(), cell.z(), soldier.status().order());
                            }
                        });
            });
            footing.tick();
            session.endTick();
        }), 0, TICK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(loudly(name + "'s status log",
                () -> LOG.debug("{} status: {} - plan: {} - session open: {}", name, soldier.status(), soldier.lastPlan(), session.isOpen())),
                STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * The camp's floor still to pave: the soil at the camp's ground height inside the wall line, open
     * above - the floor, not ground under something - and no kept structure's, nor the mine head's.
     * Empty with no camp ground measured yet.
     */
    private List<BlockPos> floorToPave() {
        BlockPos ground = campGround;
        if (ground == null || camp == null) {
            return List.of();
        }
        Set<Cell> except = groundExcept();
        int headX = camp[0] + ChainOfCommand.MINE_HEAD_DX, headZ = camp[1] + ChainOfCommand.MINE_HEAD_DZ;
        List<BlockPos> floor = new ArrayList<>();
        for (int x = ground.x() - (WALL_RADIUS - 1); x <= ground.x() + WALL_RADIUS - 1; x++) {
            for (int z = ground.z() - (WALL_RADIUS - 1); z <= ground.z() + WALL_RADIUS - 1; z++) {
                int y = ground.y() - 1; // the camp's ground is where its feet are: the floor is under them
                boolean head = Math.abs(x - headX) <= 1 && Math.abs(z - headZ) <= 1;
                if (!head && world.isSoil(x, y, z) && !world.isFarmland(x, y, z) && !world.isSolid(x, y + 1, z)
                        && !except.contains(new Cell(x, y, z))) {
                    floor.add(new BlockPos(x, y, z));
                }
            }
        }
        return floor;
    }

    /** How often the camp's key blocks are logged as the model holds them, for a sweep against the server. */
    static final long SWEEP_EVERY_MILLIS = 10 * 60_000L;
    private long lastSweep = Long.MIN_VALUE;

    /**
     * The camp's key blocks as the model holds them - its wall, its farm's soil and crop, its
     * stockpile's chest - logged every {@link #SWEEP_EVERY_MILLIS} as {@code x,y,z=state} for a
     * console sweep to check against the server's (backlog/world-model-stale.md): the chunks
     * there are held the whole time, so they're never sent again for the model to check itself.
     */
    private void sweepWanted(List<BlockPos> wall, FarmPlan farm) {
        long now = clockwork.millis();
        if (lastSweep != Long.MIN_VALUE && now - lastSweep < SWEEP_EVERY_MILLIS) {
            return;
        }
        lastSweep = now;
        List<BlockPos> cells = new ArrayList<>();
        if (wall != null) {
            cells.addAll(wall);
        }
        // The camp's farm and each soldier's own: live, tills on a squad's FARM plot were refused
        // for cobblestone two high over cells the model held open.
        Set<BlockPos> plots = new LinkedHashSet<>();
        if (farm != null) {
            plots.addAll(farm.plot());
        }
        farming.values().forEach(f -> plots.addAll(f.plot()));
        plots.forEach(c -> {
            cells.add(c);
            cells.add(new BlockPos(c.x(), c.y() + 1, c.z()));
            cells.add(new BlockPos(c.x(), c.y() + 2, c.z()));
        });
        Optional.ofNullable(campWorkshop).flatMap(Workshop::chest).ifPresent(cells::add);
        String held = cells.stream().map(c -> c.x() + "," + c.y() + "," + c.z() + "="
                + chunkCache.stateIdAt(c.x(), c.y(), c.z()).stream().mapToObj(Integer::toString).findFirst().orElse("?"))
                .collect(Collectors.joining(" "));
        LOG.info("[Sweep] {} blocks as the model holds them: {}", cells.size(), held);
    }

    /**
     * A chunk sent again that differs from the one kept, logged: the world model's check on itself
     * (backlog/world-model-stale.md). Each sample is x, y, z, the state kept and the state sent.
     */
    private void logDivergence(String name, ChunkCache.Divergence diverged) {
        if (diverged.blocks() > 0) {
            LOG.warn("[World] a chunk sent to {} differs from the one kept at {} blocks, e.g. {}", name, diverged.blocks(),
                    diverged.samples().stream().map(java.util.Arrays::toString).toList());
        }
    }

    /** Raw ore a soldier carries for the force before it walks it to the stockpile on its own. */
    static final int POOL_TRIP = 3;

    /** The pickaxe a soldier sent to quarry without one makes: the quarry's own stone makes it a better one later. */
    private static final Recipe ORDER_PICKAXE = Recipe.WOODEN_PICKAXE;

    /**
     * The next rung of the equipment ladder for {@code name}: none unless allowed to rearm, the first
     * (a weapon) only unless allowed to upgrade, with its share of the patrol base's cobblestone set
     * aside and not dug for.
     */
    private Optional<Recipe> nextRung(String name, Inventory inventory, Position at) {
        String squad = soldierDefinitions.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow().squad();
        // A keep builder's work load before the protection ladder past its floor (operator, 2026-11-14:
        // kit by role, protection and work apart): a stone sword, then the pickaxes and shovels its share
        // of the dig wears out (bot.craft.WorkLoad), then the rest. Live, builders climbed through copper
        // armour while the dig's tools wore out with no spare. Not one in the wood party: its work is the trees, and its load the axe - in the squad
        // simulation a cutter spent its one log on a spare pickaxe's sticks and had none for the axe.
        if (keepHands.contains(name) && !keepParty.has(name)) {
            int share = keepDigLeftBySquad.getOrDefault(squad, 0) / Math.max(1, keepCrews.getOrDefault(squad, List.of()).size());
            boolean floored = Ladder.upTo(inventory, Map.of(), Set.of(), m -> false, Ladder.rungOf(Recipe.STONE_SWORD)).isEmpty()
                    && Ladder.bestMelee(inventory) >= ItemClassRegistry.meleeRank("stone_sword");
            Optional<Recipe> work = floored ? WorkLoad.builder(inventory, share) : Optional.empty();
            if (work.isPresent()) {
                rungsWanted.put(name, Ladder.rungOf(work.get()));
                return work;
            }
        }
        if (!rearmAllowed.getOrDefault(name, false)) {
            rungsWanted.remove(name);
            return Optional.empty();
        }
        // The squad climbs together: nobody works above the lowest rung a squadmate is working toward,
        // so the first up the ladder doesn't dig out the ore the rest need for theirs.
        int highest = soldierDefinitions.stream().filter(d -> d.squad().equals(squad) && !d.name().equals(name))
                .map(d -> rungsWanted.get(d.name())).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).min().orElse(Ladder.RUNGS - 1);
        Map<ItemClass, Integer> setAside = Map.of(ItemClass.COBBLESTONE, stoneSetAside.getOrDefault(name, 0));
        // A station a walk away counts: the armourer goes to it rather than make another.
        Set<Workstation> inReach = armourers.get(name).stationsNear(world, at);
        // Or ore the stockpile holds, to draw (bot.supply.MetalPool): the force's pooled copper makes
        // a piece for whoever it completes one for, though there's none in reach to dig.
        boolean nearStockpile = stockpileFor(name).isPresent();
        Optional<Stockpile> pool = chainOfCommand.stockpile();
        Predicate<ItemClass> canDig = material ->
                setAside.getOrDefault(material, 0) == 0 && Armourer.canDig(material, inventory, world, at, treeClaims)
                        || nearStockpile && pool.map(st -> material == ItemClass.RAW_COPPER ? st.rawCopper()
                        : material == ItemClass.RAW_IRON ? st.rawIron() : 0).orElse(0) > 0;
        // Past no rung the camp's level doesn't authorise (backlog/maturity-model.md, basis of issue); with no camp there's no level to go by.
        Optional<Recipe> next = Ladder.upTo(inventory, setAside, inReach, canDig,
                upgradeAllowed.getOrDefault(name, false) ? highest : Math.min(highest, Ladder.BASIC_LOAD),
                camp == null ? Optional.empty() : Optional.of(campLevel));
        next.ifPresentOrElse(r -> rungsWanted.put(name, Ladder.rungOf(r)), () -> rungsWanted.remove(name));
        return next;
    }

    /** Each keep squad's cells still to fill, -1 while it's still digging - squad loops write, the site measure reads. */
    private final Map<String, Integer> keepFillLeftBySquad = new ConcurrentHashMap<>();
    /** Each squad's staircase, while it has one - squad loops write, the site measure reads. */
    private final Map<String, Staircase> squadStairs = new ConcurrentHashMap<>();

    /** Each keep squad's blocks still to dig in its share, at its last look - squad loops write, ticks read. */
    private final Map<String, Integer> keepDigLeftBySquad = new ConcurrentHashMap<>();

    /** The rung of the ladder each soldier is working toward, by name, while it is - tick loops write and read. */
    private final Map<String, Integer> rungsWanted = new ConcurrentHashMap<>();

    /**
     * How long the same line from the same sender counts as one hearing: every connection gets a
     * line said aloud within a tick or two of the others.
     */
    private static final long SAME_LINE_NANOS = 2_000_000_000L;
    /** When each line was last heard, by sender and text - every connection's event loop. */
    private final Map<String, Long> lastHeard = new ConcurrentHashMap<>();

    /** The soldier whose offline-mode UUID {@code uuid} is, if it's one of ours. */
    private Optional<String> soldierNamed(java.util.UUID uuid) {
        return soldierDefinitions.stream().map(SoldierDefinition::name)
                .filter(n -> OperatorOrders.offlineUuid(n).equals(uuid)).findFirst();
    }

    /** Whether no connection has heard {@code chat} in the last {@link #SAME_LINE_NANOS}. */
    private boolean firstHearing(ChatMessage chat) {
        long now = System.nanoTime();
        boolean[] first = {false};
        lastHeard.compute(chat.sender() + "\0" + chat.message(), (key, heard) -> {
            first[0] = heard == null || now - heard > SAME_LINE_NANOS;
            return now;
        });
        lastHeard.values().removeIf(heard -> now - heard > SAME_LINE_NANOS);
        return first[0];
    }

    /** How far out a flight recorder notes the nearest hostile. */
    private static final double RECORDED_HOSTILE_RADIUS = 24;

    /**
     * Fires from {@code protocol}'s watchdog (or any other unexpected close - see {@code
     * PlaySessionListeners.onDisconnect}): stops this soldier's now-dead tick loop and reconnects
     * from scratch via {@link #connect}, which re-seeds a brand new {@link DeterministicSoldier}
     * from the new session's own spawn position. One immediate attempt; a join that fails - the
     * server down, or closing the connection mid-login as it restarts - is tried again by
     * {@code connect}'s {@code exceptionally}, backing off from {@link #REJOIN_FIRST_MILLIS} to
     * {@link #REJOIN_MAX_MILLIS}, logged each time, for as long as it takes.
     */
    private void onDisconnected(SoldierDefinition definition, AtomicReference<ScheduledExecutorService> activeScheduler, Throwable cause) {
        String name = definition.name();
        activeSoldiers.remove(name);
        ScheduledExecutorService scheduler = activeScheduler.get();
        if (scheduler != null) {
            scheduler.shutdownNow();
            schedulers.remove(scheduler);
        }
        if (stopping) {
            return;
        }
        LOG.warn("{} disconnected unexpectedly ({}) - reconnecting", name, cause.getMessage());
        // The new connection re-receives Add Entity for everything in view; drop this one's stale view.
        entityTracker.removeOwner(name);
        chunkCache.removeOwner(name);
        connect(definition);
    }
}
