package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Ladder;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.engineer.Stairway;
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
import io.github.jayessdeesea.minecraft.command.Groundwork;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.LinkUp;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Plan;
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
 * <p>The squad's {@link SquadOrder} comes from the {@link ChainOfCommand} (general and squad
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

    // Hardcoded pending a real deployment need for external config - matches the dev server
    // DEVELOPMENT.md's own setup instructions put on localhost.
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

    /** A squad's deterministic state across cycles - squad thread only. */
    private static final class SquadState {
        final LinkUp linkUp = new LinkUp();
        Set<String> linkingUp = Set.of();
        /** The squad leader's order whose plan changes were last made - each order's are made once. */
        SquadOrder planned;
        /** Its soldiers' work orders, followed until done or timed out. */
        final WorkOrders workOrders = new WorkOrders();
        String haltedFor;
        boolean fortifying;
        boolean allInside;
        /** Where the squad's patrol base is laid out, once it is. */
        Position baseSite;
        /** The last patrol base the squad built - to cut its way out of, once it moves on. */
        PatrolBase.Plan builtBase;
        /** A patrol base point with no level ground near it - logged once. */
        Position unbuildable;
        /** Whether the base was lit at the last check. */
        boolean lit;
        /** Who the squad's torchbearer was last cycle: kept while it still can, so a run isn't handed over half done. */
        String torchbearer;
        /** The squad's staircase, while its objective is MINE. */
        Staircase stairs;
        /** The soldiers' own reports this cycle, before link-up relabels a replacement. */
        List<SoldierStatus> reported = List.of();
        /** The objective's x and z the wall was laid out for: its key in {@code walls}. */
        String wallKey;
        /** The objective's x and z the stairs were sited for. */
        int[] stairsFor;
        /** The recovery drill: who's stranded, the stair laid out for each, and who's fetching. */
        final Recovery recovery = new Recovery(STRANDED_REPORTS);
        /** Soldiers the recovery drill is sending down on their own - said once a spell. */
        final Set<String> escaping = new HashSet<>();
        /** Torches left to put down round a SECURE objective, at the last check: -1 before the first. */
        int torchesLeft = -1;
        /** The squad's wall, laid out once round its FORTIFY objective's ground. */
        List<BlockPos> wall;
        /** The ground its wall is laid out round. */
        BlockPos wallCentre;
        /** Wall cells left to fill at the last check: -1 before the first. */
        int wallLeft = -1;
        /** Blocks of the camp's ground work still to dig - trees over it, ground above its level - at the last check: -1 before the first. */
        int digLeft = -1;
        /** The wall's blocks of dirt or grass, to replace with stone, at the last check: -1 before the first. */
        int softLeft = -1;
        /** Open cells of the camp's ground work still to fill, at the last check: -1 before the first. */
        int fillLeft = -1;
        /**
         * What the ground work's log line last said: said again only when it changes. FORTIFY resets
         * the counts each cycle it has no wall, and camp upkeep measures them again - live, the same
         * line was logged every second.
         */
        String groundSaid;
        /** Soldiers sent off base for dirt by the ground work's drill, until they carry a load. */
        final Set<String> fetchingDirt = new HashSet<>();
        /** When the torches still to put down last changed: a plan stuck at a cell or two is done. */
        long torchesChangedAt;
        /** When a staircase nobody could site at {@code stairsFor} is looked for again. */
        long stairsRetryAt;
        /** Who in the squad is on what, followed to completion and checked against the world. */
        final TaskRegister register = new TaskRegister();
        /**
         * Soldiers taken off a task the register found STALLED, by "soldier|task": each goes to its
         * next work, and back to that task after a wait that grows each time it stalls there again.
         */
        final Setbacks<String> relieved =
                new Setbacks<>(RELIEVED_MILLIS, 8 * RELIEVED_MILLIS);

        /** Whether {@code soldier} is off {@code task} for now. */
        boolean relieved(String soldier, String task, long now) {
            return relieved.barred(soldier + "|" + task, now);
        }
        /** The squad's tree farm, while it has one. */
        TreeFarm trees;
        /** The squad's farm, while it has one. */
        FarmPlan farm;
        /** The soldier who is the closed wall's while it has dirt in it, if one is. */
        String wallHand;
        /** The point the farm was sited for: kept while it's ordered there. */
        BlockPos farmFor;
        /** Blocks of the laid-out base still to place, at the last check - empty with none laid out. */
        OptionalInt baseBlocksLeft = OptionalInt.empty();
        /** When a soldier of the squad last worked at its task, or the task was given: what a stall is measured from. */
        long workedAt = Long.MIN_VALUE;
        /** The task {@code workedAt} was measured for: a new task starts the clock again. */
        Objective workedFor;
        /** The squad's Class I at the last SITREP, logged when its rating changes. */
        Subsistence.Status classOne;
        /** The last progress measured, logged when its state changes. */
        Progress progress;
        /** Where the squad crafts on the surface, sited by the engineer near its objective; null with nowhere to put one. */
        Workshop workshop;
        /** The ground {@code workshop} was sited at: sited again only when the objective moves. */
        BlockPos workshopFor;
        /** The doorway cut to let a torchbearer out of a sealed, dark base - left open until it's back with torches. */
        List<Position> torchDoor;
        /** The base's breaches at the last check - logged when they change. */
        List<Position> breaches;
        Set<CrossLevel.Transfer> crossLeveling = Set.of();
        /** The walls and roof of the base the squad holds, kept off-limits to digging. */
        Set<BlockPos> baseCells = Set.of();
    }

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
    private record Fortify(Position stand, List<BlockPos> cells, boolean allInside,
                           boolean makeTorches, boolean placeTorch) {
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
    private final WallStore farmStore;
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
    /** Squad-loop cycles between measures of the foothold: about ten seconds. */
    private static final int FOOTHOLD_EVERY = 10;
    private int footholdTicks;
    /** Steps cut down the deepest staircase so far, and whether one reached its bottom - tick threads write. */
    private final AtomicInteger stairsCut = new AtomicInteger();
    private final AtomicBoolean stairsDone = new AtomicBoolean();

    /**
     * The camp measured against the lodgment's phases (docs/military-doctrine.md §17): what's lit,
     * walled, growing, dug and carried - empty until the camp's ground has loaded.
     */
    private Optional<Foothold> measureFoothold() {
        List<SoldierStatus> everyone = activeSoldiers.values().stream().map(DeterministicSoldier::status).toList();
        if (everyone.isEmpty()) {
            return Optional.empty();
        }
        // The camp's ground found once and kept: found again from where the squads are, it moves
        // onto a hut roof one time and back the next, and every measure moves with it.
        if (campGround == null) {
            double[] heights = everyone.stream().mapToDouble(s -> s.position().y()).sorted().toArray();
            campGround = typicalGround(camp[0], camp[1], (int) Math.floor(heights[heights.length / 2])).orElse(null);
            if (campGround == null) {
                return Optional.empty();
            }
            LOG.info("[Foothold] the camp's ground is {}", campGround);
        }
        BlockPos at = campGround;
        List<Foothold.Standard> standards = new ArrayList<>();
        // The cells SECURE lights, no others: one measure for the standard and the task that meets it.
        int dark = offTheWall(Lighting.darkCells(world, at, SECURE_RADIUS)).size();
        // Torches are made from logs: with none carried and no tree in sight, lighting waits on wood
        // grown first - the lookup done for the general, which otherwise sends squads to SECURE with
        // nothing to make torches from.
        int logs = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(Kit::logs).orElse(0)).sum();
        int torches = torchesCarried.values().stream().mapToInt(Integer::intValue).sum();
        boolean noWood = dark > 0 && torches == 0 && logs < TORCH_LOGS && !chainOfCommand.woodSeen();
        standards.add(new Foothold.Standard(2, "the camp's ground lit", dark == 0,
                dark + " walkable cells within " + SECURE_RADIUS + " blocks still dark"
                        + (noWood ? "; no logs to make torches from and no trees in sight - grow them first" : ""),
                noWood ? "TREE_FARM" : "SECURE"));
        // The camp's wall as kept on disk, whether or not a squad is ordered to it this run: live,
        // after a restart with no FORTIFY order, a closed wall read "not begun".
        wallStore.keys().stream().filter(k -> Math.hypot(Integer.parseInt(k.split(",")[0]) - camp[0],
                        Integer.parseInt(k.split(",")[1]) - camp[1]) <= WALL_RADIUS)
                .forEach(k -> walls.computeIfAbsent(k, key -> wallStore.load(key).orElseThrow()));
        // The one the world shows most built, of the layouts round the camp: one was laid out for each
        // place FORTIFY was ever ordered near it.
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
        standards.add(new Foothold.Standard(2, "the camp walled in", wallLeft == 0 && wallSoft == 0,
                wall == null ? "not begun" : wallLeft > 0 ? wallLeft + " of " + wall.size() + " wall blocks still to place"
                        + (wallSoft > 0 ? ", and " + wallSoft + " of dirt to replace with stone" : "")
                        : wallSoft + " of its blocks dirt, to replace with stone", "FORTIFY"));
        // Its floor paved at its own height (the operator's: not cut to sea level), once it's walled:
        // FORTIFY's last work, the soil inside replaced with stone.
        int unpaved = floorToPave().size();
        standards.add(new Foothold.Standard(3, "the camp's floor paved", unpaved == 0,
                unpaved + " cells of the floor inside the wall still soil, to pave with stone", "FORTIFY"));
        long hungry =everyone.stream().filter(s -> s.condition().map(SoldierRunner::wantsFood).orElse(false)).count();
        // The camp's farm, sited once and kept while its water is there: a farm is tended for days,
        // and live a farm sited again each check moved between ponds, and its crop was left to new ground.
        FarmPlan keptFarm = campFarm;
        if (keptFarm == null) {
            // As it was before a restart, while its water is there.
            keptFarm = farmStore.load(camp[0] + "," + camp[1]).filter(cells -> cells.size() > 1)
                    .map(cells -> new FarmPlan(cells.getFirst(), cells.subList(1, cells.size())))
                    .orElse(null);
        }
        boolean resite = keptFarm == null || !world.isWater(keptFarm.water().x(), keptFarm.water().y(), keptFarm.water().z());
        if (resite || !campFarmGrown) {
            Set<BlockPos> walkable =
                    Pathfinder.reachable(world, at, CAMP_FARM_RADIUS + 4, FARM_REACH_BUDGET);
            FarmPlan before = resite ? null : keptFarm;
            if (resite) {
                keptFarm = FarmPlan.site(world, at, CAMP_FARM_RADIUS, CAMP_FARM_VERTICAL,
                        walkable::contains).orElse(null);
            }
            // Grown once a run to feed the camp: the plots round the waters beside its own.
            if (keptFarm != null) {
                keptFarm = FarmPlan.grown(world, keptFarm, CAMP_FARM_CELLS, CAMP_FARM_VERTICAL, walkable::contains);
                campFarmGrown = true;
            }
            if (keptFarm != null && !keptFarm.equals(before)) {
                LOG.info("[Foothold] the camp's farm: by the water at {}, {} cells", keptFarm.water(), keptFarm.plot().size());
                List<BlockPos> kept = new ArrayList<>();
                kept.add(keptFarm.water());
                kept.addAll(keptFarm.plot());
                farmStore.save(camp[0] + "," + camp[1], kept);
            }
        }
        // Kept however it was found - sited now, or as it was before a restart: live, a farm loaded
        // from disk was never kept, and both squads starved under SECURE with a farm by the camp.
        campFarm = keptFarm;
        Optional<FarmPlan> farm = Optional.ofNullable(keptFarm);
        long growing = farm.map(f -> f.plot().stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).isPresent()).count()).orElse(0L);
        // Safety first: food is buildup, behind the wall and the weapons (the operator's). Unless
        // the force's survival is at stake - most of it hungry with nothing to eat, or one starving
        // one hit from death - then food stands beside the wall, and the general does what it takes.
        // Live, both squads walled the camp while all eight soldiers went hungry, health falling to 1.
        boolean starving = hungry * 2 > everyone.size()
                || everyone.stream().anyMatch(CrossLevel::critical);
        int foodPhase = starving ? 2 : 3;
        // Met when the farm grows and the force eats: wheat planted today feeds nobody for days.
        standards.add(new Foothold.Standard(foodPhase, "food", growing >= FARM_GROWING && !starving,
                // Where: a coordinate written out is one the model copies, and here it should - live,
                // told only a count, it sent a squad to farm 50 blocks off, where the count never sees it.
                (farm.isEmpty() ? "no ground to farm by water near the camp" : growing + " of " + farm.get().plot().size()
                        + " cells of the camp's farm ground, by the water at x=" + farm.get().water().x() + " z=" + farm.get().water().z()
                        + ", growing wheat")
                        + "; " + hungry + " of " + everyone.size() + " soldiers hungry with nothing to eat", "FARM"));
        Optional<TreeFarm> grove = TreeFarm.site(world, at,
                CAMP_RADIUS);
        long trees = grove.map(g -> g.plot().stream().filter(c -> TreeFarm.planted(world, c)).count())
                .orElse(0L);
        standards.add(new Foothold.Standard(3, "wood that grows back", trees >= TREES_GROWING,
                trees + " saplings or trees planted by the camp", "TREE_FARM"));
        // The camp's mine as the world shows it, as well as a cutter at work this run: live, after a
        // restart with no MINE order, a mine cut to the bottom read "no mine yet".
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
        int stoneSword = ItemClassRegistry.meleeRank("stone_sword");
        long armed = everyone.stream().filter(s -> s.condition().flatMap(c -> c.kit()).flatMap(k -> k.weapon())
                .filter(w -> !w.equals("bow")).map(w -> {
                    try {
                        return ItemClassRegistry.meleeRank(w) >= stoneSword;
                    } catch (IllegalArgumentException notMelee) {
                        return false;
                    }
                }).orElse(false)).count();
        // A weapon each is shelter's other half: even a wooden sword wins most fights a fist loses.
        standards.add(new Foothold.Standard(2, "every soldier armed", armed == everyone.size(),
                armed + " of " + everyone.size() + " carry a stone sword or better", "STONE"));
        int armour = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(k -> k.armour()).orElse(0)).sum();
        standards.add(new Foothold.Standard(4, "every soldier armoured",
                armour == everyone.size() * Kit.ARMOUR_SLOTS, armour + " of " + everyone.size() * Kit.ARMOUR_SLOTS + " armour slots filled (copper or iron, from ore)",
                "MINE"));
        measureRoutes(at);
        sweepWanted(wall, farm.orElse(null));
        return Optional.of(new Foothold(new Position(at.x(), at.y(), at.z()), List.copyOf(standards)));
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

    /** How long a soldier is first kept off a task it stalled on (the squad's register's finding). */
    static final long RELIEVED_MILLIS = 5 * 60_000;

    /** How far round the camp its tree farm is looked for. */
    private static final int CAMP_RADIUS = 24;
    /** How far round the camp its farm is looked for: the water it needs can be a way off. */
    private static final int CAMP_FARM_RADIUS = 48;
    /**
     * The cells the camp's farm is grown to: enough to feed the force and build its basic load, not
     * only its meals. Hydrated wheat ripens in half an hour or so, three wheat a loaf: live, 97
     * cells made about 20 loaves an hour, as many as eight soldiers ate, and hunting added next to
     * nothing - none of them carried more than a few rations. One water's plot is a quarter of it
     * by a pond.
     */
    static final int CAMP_FARM_CELLS = 192;
    /** And how far up or down: a camp on a hill farms by the water below it. */
    private static final int CAMP_FARM_VERTICAL = 32;
    /** Farm cells a farmer couldn't work, passed over by every farmer for a while. */
    private final Setbacks<BlockPos> farmCellsFailed =
            new Setbacks<>(Farmer.PASS_OVER_TICKS,
                    16L * Farmer.PASS_OVER_TICKS);
    /** How many cells the flood that finds a farm's walkable ground may expand: the ground within some 50 blocks. */
    private static final int FARM_REACH_BUDGET = 40_000;
    /** The camp's farm ground by water, as the foothold last measured it: kept by squads short of food. */
    private volatile FarmPlan campFarm;
    /** Whether the camp's farm has been grown to {@link #CAMP_FARM_CELLS} this run, as far as the ground allows. */
    private boolean campFarmGrown;
    /** The camp's ground, found once. */
    private BlockPos campGround;
    /** Farm cells growing wheat for the camp's food to count as met. */
    private static final int FARM_GROWING = 12;
    /** Saplings or trees for the camp's wood to count as met. */
    private static final int TREES_GROWING = 8;
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
    /** At this food level or below, with no rations, a soldier free to hunts for meat: it has missed a meal or two. */
    static final int HUNT_BELOW_FOOD = 14;

    /**
     * Whether a soldier with no rations needs food: at {@link #HUNT_BELOW_FOOD} or below, or
     * wounded and short of the food it heals at - otherwise a wound taken at food 15 never heals.
     */
    static boolean wantsFood(Condition condition) {
        return condition.kit().map(k -> Subsistence.short_(k.rations())).orElse(true)
                && (condition.food() <= HUNT_BELOW_FOOD || condition.health() < Condition.MAX_HEALTH
                && condition.food() < Condition.REGENERATION_FOOD);
    }
    /** Within this many blocks of the stockpile, a soldier below {@link Subsistence#RESUPPLY} draws bread from it on its way. */
    static final double DRAW_FOOD_NEAR = 16;

    /**
     * Whether a soldier carrying {@code kit} goes to {@code stockpile} for bread: there's bread in it
     * ({@code seen}, as the chest was last opened), and it is below {@link Subsistence#FOOD_FIRST} -
     * food before its work, wherever it is - or below {@link Subsistence#RESUPPLY} and near it, as
     * it's convenient.
     */
    static boolean drawsFood(Kit kit, Optional<Stockpile> seen, Position at, Optional<Position> stockpile) {
        if (kit == null || stockpile.isEmpty() || seen.map(st -> st.rations() == 0).orElse(true)) {
            return false;
        }
        return Subsistence.short_(kit.rations()) || Subsistence.low(kit.rations())
                && Cohesion.distance(at, stockpile.get()) <= DRAW_FOOD_NEAR;
    }

    /** Each connected soldier's tree planting, by name. */
    private final Map<String, Forester> foresters = new ConcurrentHashMap<>();
    /** The tree farm each soldier plants, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, TreeFarm> planting = new ConcurrentHashMap<>();
    /** Each connected soldier's staircase cutting, by name. */
    private final Map<String, Miner> miners = new ConcurrentHashMap<>();
    /** The day's deaths and idlers, reviewed at each dawn. */
    private final AfterActionReview afterActionReview = new AfterActionReview();
    /** Cutters wanting a tool, sent to their mine's landing to make it - tick writes, squad loop reads. */
    /** Soldiers walking a PATROL, and the leg of its round each is on - squad loop writes, tick reads. */
    private final Map<String, Integer> patrolLegs = new ConcurrentHashMap<>();
    /** How far out from a PATROL objective its round goes: just inside the wall line. */
    static final int PATROL_RADIUS = WALL_RADIUS - 2;
    /** Where a soldier walks to fit its workshop's chest, while it does - tick writes, squad loop reads. */
    private final Map<String, Optional<Position>> storeWalks = new ConcurrentHashMap<>();

    /** Whether {@code at} is at {@code workshop}'s stand: within reach of its fixtures. */
    private static boolean atStand(Position at, Workshop workshop) {
        return Math.hypot(at.x() - workshop.stand().x() - 0.5, at.z() - workshop.stand().z() - 0.5) <= Armourer.WORKSHOP_TOLERANCE
                && Math.abs(at.y() - workshop.stand().y()) <= 1;
    }

    /** Masons out of blocks sent to draw cobblestone from the stockpile - squad loop writes, tick reads. */
    private final Set<String> drawingStone = ConcurrentHashMap.newKeySet();
    /** Each soldier's fitting of its workshop's chest, by name. */
    private final Map<String, Fitter> storeFitters = new ConcurrentHashMap<>();
    /** Soldiers short of food with no means of their own to get any: wheat, game in sight. Tick writes, squad loop reads. */
    private final Set<String> foodless = ConcurrentHashMap.newKeySet();
    /** The workshop each soldier crafts at (backlog/sites.md): its squad's, or the mine base below ground. */
    private final Map<String, Workshop> workshops = new ConcurrentHashMap<>();
    /** The camp's own workshop, at its ground: every squad near the camp crafts there. */
    private volatile Workshop campWorkshop;
    /**
     * Whether no wall is going up round the camp - none laid out, or closed and all stone - as last
     * measured: foothold check writes, squad loop reads.
     */
    private volatile boolean campWalled = true;
    /** How near the camp an objective is for its squad to craft at the camp's workshop. */
    private static final int CAMP_WORKSHOP_RANGE = 48;
    /** Ticks to each soldier's next stand-to check. */
    private final Map<String, Integer> standTo = new ConcurrentHashMap<>();
    /** Soldiers stood still by day: an alarm each spell. */
    private final IdleWatch idleWatch = new IdleWatch();
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
    /** How far from the general's farm objective the plot's water may be, across and up or down. */
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
    private static final int LIGHTING_TORCHES = 8;
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
        this.farmStore = new WallStore(Path.of(stateDir), "farm");
        this.mineStore = new WallStore(Path.of(stateDir), "stairs");
        this.planBook = new PlanBook(Path.of(stateDir));
        this.scoreboard = new Scoreboard(Path.of(stateDir));
        this.camp = camp.isBlank() ? null : Arrays.stream(camp.split(",")).mapToInt(v -> Integer.parseInt(v.strip())).toArray();
        if (this.camp != null && this.camp.length != 2) {
            throw new IllegalArgumentException("minecraft.camp is x,z - not " + camp);
        }
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
        for (SoldierDefinition definition : soldierDefinitions) {
            connect(definition);
        }
        ScheduledExecutorService squad = clockwork.scheduler("squad");
        schedulers.add(squad);
        squad.scheduleAtFixedRate(loudly("the squad loop", this::leadSquad), SQUAD_PERIOD_MILLIS, SQUAD_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        squad.scheduleAtFixedRate(loudly("the SITREP log", this::logSquadSitrep), STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reports up, orders down: pass every squad's SITREPs to the chain of command, then hand every
     * present soldier its slot under its squad's standing order (the deterministic half of squad
     * leading - {@link Formations} and the drills).
     */
    private void leadSquad() {
        record Present(String squad, List<DeterministicSoldier> soldiers, List<SoldierStatus> statuses,
                       Optional<Position> principalAt) {
        }
        if (yieldingToAnExperiment()) {
            return;
        }
        if (camp != null && footholdTicks++ % FOOTHOLD_EVERY == 0) {
            // Its own request: a measure that fails costs the measure, not the squads' orders.
            faults.run("the foothold's measure", () -> measureFoothold().ifPresent(f -> {
                chainOfCommand.onFoothold(f);
                afterActionReview.measured(f);
            }));
        }
        chainOfCommand.time().flatMap(t -> {
                    List<SoldierStatus> all = activeSoldiers.values().stream().map(DeterministicSoldier::status).toList();
                    int hungry = (int) all.stream().filter(s -> s.condition().map(SoldierRunner::wantsFood).orElse(false)).count();
                    return afterActionReview.time(t.dark(), chainOfCommand.foothold(), hungry, all.size(), clockwork.millis(),
                            routes.stream().map(RouteEffort::line).toList(),
                            squadStates.values().stream().mapToInt(st -> st.register.takeChurned()).sum());
                })
                .ifPresent(dawn -> {
                    LOG.info("[AAR] {}\n  {}\n  {}\n  {}", dawn.review(), String.join("\n  ", chainOfCommand.requestReview()),
                            String.join("\n  ", registerReview()), String.join("\n  ", faults.review()));
                    LOG.info("[Scoreboard] {}", scoreboard.add(dawn.score()));
                });
        List<Present> present = new ArrayList<>();
        for (Roster.Squad squad : roster.squads()) {
            List<DeterministicSoldier> soldiers = presentSoldiers(squad.name());
            if (soldiers.isEmpty()) {
                continue;
            }
            SquadState state = squadStates.computeIfAbsent(squad.name(), name -> new SquadState());
            // Replacements (respawned soldiers) go to the rear and rejoin by link-up, so the first
            // formed soldier is the point man.
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
            // shown its CANTCO, the squad leader reported the mission one, and the general spent the
            // squad's days moving it to "resolve movement issues".
            List<SoldierStatus> shown = state.recovery.asRecovering(p.statuses());
            // Surveyed from where the squad is: the soldier at its middle height, so one stranded up a
            // tower - respawned on a block over spawn - doesn't show the squad no ground at all; and
            // never one being recovered: live, the survey from one in a canopy, or in a hole under the
            // mine's landing, showed the squad 0 of 81 cells reachable, and it reported its task CANTCO.
            List<Position> formed = shown.stream().filter(s -> !(s.report() instanceof Report.Recovering))
                    .map(SoldierStatus::position).sorted(Comparator.comparingDouble(Position::y)).toList();
            Position pointMan = formed.isEmpty() ? shown.getFirst().position() : formed.get(formed.size() / 2);
            // Climbing counts when the point man carries cobblestone to pillar with.
            int climbBlocks = shown.get(0).condition().flatMap(c -> c.kit()).map(k -> k.stone()).orElse(0);
            boolean stoneSteps = shown.get(0).condition().flatMap(c -> c.kit()).map(k -> k.pickaxe()).orElse(false);
            boolean holdingBase = state.allInside && state.breaches != null && state.breaches.isEmpty();
            return new ChainOfCommand.Sitrep(p.squad(), shown, () -> TerrainSurveyor.survey(world, pointMan, climbBlocks, stoneSteps,
                    chainOfCommand.objective(p.squad()).map(Objective::location)), p.principalAt(),
                    () -> ResourceSurveyor.survey(world, pointMan), holdingBase,
                    holdingBase ? OptionalInt.of(0) : state.baseBlocksLeft, progress(p.squad(), state, p.statuses()),
                    Optional.of(classOne(p.squad(), state, p.statuses())));
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
        List<SoldierStatus> free = outOfContact.stream().filter(s -> !busy.contains(s.name())).toList();
        List<SoldierStatus> short_ = free.stream().filter(s -> s.condition().flatMap(Condition::kit)
                .filter(k -> Subsistence.low(k.rations())).isPresent()).toList();
        Map<String, CrossLevel.Transfer> now = new HashMap<>();
        CrossLevel.across(short_, free).forEach(t -> now.put(t.giver(), t));
        now.values().stream().filter(t -> !was.containsValue(t))
                .forEach(t -> LOG.info("[{} -> {}] CROSS-LEVEL across squads: {} rations", t.giver(), t.receiver(), t.count()));
        acrossSquads = Map.copyOf(now);
    }

    /**
     * A squad's unit: its main body, or a detachment its soldiers' plans make - led as a squad is, to
     * {@code objective}. {@code directed} is the directive a detachment of one is on.
     */
    private record Unit(String name, List<DeterministicSoldier> soldiers, List<SoldierStatus> statuses,
                        Optional<Objective> objective,
                        Optional<Directive> directed, boolean roled) {

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
            String unit = escorting || inForTheNight ? squad : plan.unit(squad, names.get(i));
            members.computeIfAbsent(unit, u -> new ArrayList<>()).add(i);
            String was = unitOf.put(names.get(i), unit);
            if (!unit.equals(was == null ? squad : was)) {
                LOG.info("[{} -> {}] now in {}", names.get(i), squad, unit);
                // Off camp upkeep's ground work: its new unit gives it its work.
                if (upkeeping.remove(names.get(i))) {
                    route(names.get(i), null);
                }
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
    private Optional<Order> levelWork(SoldierStatus soldier,
                                                                              Directive.Level level) {
        Set<Cell> except = groundExcept();
        Box dig = new Box(level.minX(), level.y(), level.minZ(),
                level.maxX(), level.y() + LEVEL_ABOVE, level.maxZ());
        if (!Clearing.toClear(world, dig, except).isEmpty()) {
            return Optional.of(new Order.Dig(dig, except));
        }
        Box fill = new Box(level.minX(), level.y() - LEVEL_BELOW,
                level.minZ(), level.maxX(), level.y() - 1, level.maxZ());
        if (Clearing.toFill(world, fill, except).isEmpty()) {
            return Optional.empty();
        }
        int blocks = soldier.condition().flatMap(c -> c.kit()).map(k -> k.stone() + k.dirt()).orElse(0);
        if (blocks == 0) {
            int r = Directive.FETCH_RADIUS;
            Position at = level.centre();
            return Optional.of(new Order.Gather(Resource.SOIL,
                    new Box((int) at.x() - r, level.y() - 8, (int) at.z() - r,
                            (int) at.x() + r, level.y() + 4, (int) at.z() + r), WALL_BLOCKS_LOAD));
        }
        return Optional.of(new Order.Fill(fill, except));
    }

    /** How many cells of {@code level} are still to dig or fill, as the world shows. */
    private int levelLeft(Directive.Level level) {
        Set<Cell> except = groundExcept();
        return Clearing.toClear(world, new Box(level.minX(),
                        level.y(), level.minZ(), level.maxX(), level.y() + LEVEL_ABOVE, level.maxZ()), except).size()
                + Clearing.toFill(world, new Box(level.minX(),
                        level.y() - LEVEL_BELOW, level.minZ(), level.maxX(), level.y() - 1, level.maxZ()), except).size();
    }

    /** The objective {@code unit} works to: a detachment's own, or its squad's from the general. */
    private Optional<Objective> objectiveOf(String unit) {
        return Plan.detachment(unit) ? Optional.ofNullable(detached.get(unit)) : chainOfCommand.objective(unit);
    }

    /** {@code unit}'s objective, if its task is a {@code kind}. */
    private Optional<Objective> taskOf(String unit,
                                                                               Class<? extends Objective.Task> kind) {
        return objectiveOf(unit).filter(o -> kind.isInstance(o.task()));
    }

    /** The unit {@code soldier} of {@code squad} was last put in. */
    private String unitOf(String soldier, String squad) {
        return unitOf.getOrDefault(soldier, squad);
    }

    /**
     * The squad's Class I ({@code command.Subsistence}): who is short of food, and of them who has no
     * means of getting any - no wheat, no game in sight - and isn't being shared some by a squadmate.
     * Logged when its rating changes.
     */
    private Subsistence.Status classOne(String squad, SquadState state, List<SoldierStatus> statuses) {
        Set<String> shared = state.crossLeveling.stream().filter(t -> t.item() == CrossLevel.Item.RATIONS)
                .map(CrossLevel.Transfer::receiver).collect(Collectors.toSet());
        // Wheat growing in the camp's farm is a means: the squad keeps it (field feeding, half of a
        // hungry squad on it), and its bread is on the way. Live, squads with a farm growing raised
        // Class I RED to the general all night, which it can't answer better than the farm already does.
        FarmPlan farm = campFarm;
        boolean farmGrowing = farm != null && farm.plot().stream().anyMatch(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).isPresent());
        Set<String> noMeans = farmGrowing ? Set.of() : statuses.stream().map(SoldierStatus::name)
                .filter(n -> foodless.contains(n) && !shared.contains(n)).collect(Collectors.toSet());
        Subsistence.Status status = Subsistence.of(statuses, noMeans);
        if (state.classOne == null || state.classOne.rating() != status.rating()) {
            LOG.info("[{}] {}", squad, status.line());
        }
        state.classOne = status;
        return status;
    }

    /**
     * The squad's surface workshop, sited by the engineer ({@code bot.engineer.Workshop}): the
     * camp's, for an objective within {@link #CAMP_WORKSHOP_RANGE} of it, else a hasty one at the
     * objective's ground - where the squad stands with no objective. Sited again only when that ground
     * moves, or the stand stops being standable. Never in its patrol base: {@code hut}'s cells are left alone.
     */
    private void siteWorkshop(String squad, SquadState state, List<SoldierStatus> statuses, List<BlockPos> hut) {
        // A MINE objective's height is the depth to reach: its workshop is at the stairs' head.
        Position at = state.stairs != null
                ? new Position(state.stairs.head().x(), state.stairs.head().y(), state.stairs.head().z())
                : objectiveOf(squad).map(Objective::location)
                .orElse(statuses.getFirst().position());
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
            if (camp == null || !Pathfinder.standable(world, camp.stand())) {
                camp = Workshop.hasty(world, campGround, claimed).orElse(null);
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
        boolean landing = state.stairs.landingCut(world);
        if (landing && at.y() < state.stairs.head().y() - MINE_BELOW) {
            return Workshop.mine(state.stairs);
        }
        boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
        return dark ? (landing ? Workshop.mine(state.stairs) : null) : state.workshop;
    }

    /**
     * Where {@code soldier}'s loads go and are drawn from: the supply point the operator set up, else
     * its workshop's chest once it stands (backlog/sites.md).
     */
    private Optional<Position> stockpileFor(String soldier) {
        if (supplyPoint.isPresent()) {
            return supplyPoint;
        }
        Workshop w = workshops.get(soldier);
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

    /** Health a soldier keeps the camp by night with, besides a weapon: half its hearts. */
    static final double NIGHT_UPKEEP_HEALTH = 10;

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
                if (upkeeping.remove(s.name())) {
                    route(s.name(), null);
                }
            });
            return;
        }
        List<SoldierStatus> idle = new ArrayList<>();
        for (SoldierStatus s : statuses) {
            String n = s.name();
            boolean otherWork = (clearing.containsKey(n) || filling.containsKey(n) || groundGather.containsKey(n)) && !upkeeping.contains(n)
                    || walling.containsKey(n) || repointing.containsKey(n)
                    || farming.containsKey(n) && (Optional.ofNullable(farmers.get(n)).map(f -> f.active()).orElse(false)
                            || Farmer.hasWork(world, farming.get(n)))
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
            // By night only the fit: lit ground spawns nothing, but phantoms come down out of the sky
            // on anyone under it. Live, an unarmed soldier at 4 health digging the camp by night was
            // killed by one.
            boolean fit = daylight || s.condition().filter(c -> c.health() >= NIGHT_UPKEEP_HEALTH
                    && c.kit().flatMap(Kit::weapon).isPresent()).isPresent();
            if (otherWork || !near || !fit) {
                if (upkeeping.remove(n)) {
                    route(n, null);
                }
            } else {
                idle.add(s);
            }
        }
        if (idle.isEmpty()) {
            return;
        }
        SquadState state = squadStates.get(squad);
        Map<String, Order> work = groundwork(squad, state, campGround, idle);
        for (SoldierStatus s : idle) {
            Order order = work.get(s.name());
            if (order == null) {
                if (upkeeping.remove(s.name())) {
                    route(s.name(), null);
                }
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
        Box dig = Groundwork.dig(at, WALL_RADIUS - 1);
        // Inside the wall line, as the dig is: the wall's own footing is the masons' (Perimeter.open).
        Box fill = Groundwork.fill(at, WALL_RADIUS - 1);
        // The camp's mine's shaft - the 3x3 columns round its head, where the night shift cuts it - is
        // never ground work, by day or night, whoever's on it: live, a day's fill with no squad on
        // MINE would have poured dirt down it.
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
        // Cobblestone over the floor only while no wall is going up: the masons' steps inside it are
        // cobblestone, and dug out from under them the wall stood unfinished in the squad simulation.
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
        }
        List<BlockPos> toDig = Clearing.toClear(world, dig, except);
        List<BlockPos> toFill = toDig.isEmpty()
                ? Clearing.toFill(world, fill, except) : List.of();
        int digLeft = toDig.size();
        int fillLeft = digLeft > 0 ? -1 : toFill.size();
        String said = "ground work round " + feet + ": " + digLeft + " blocks to dig (" + dig + ")"
                + (fillLeft < 0 ? "" : ", " + fillLeft + " cells to fill (" + fill + ")");
        if (!said.equals(state.groundSaid)) {
            LOG.info("[{}] {}", squad, said);
            state.groundSaid = said;
        }
        state.digLeft = digLeft;
        state.fillLeft = fillLeft;
        List<Tasking.Hand> crew = hands.stream().map(h -> new Tasking.Hand(h.name(),
                h.condition().flatMap(c -> c.kit()).map(k -> k.stone() + k.dirt()).orElse(0))).toList();
        // Split where the work is, not the whole site: live in the squad simulation, a pit in one
        // half of the camp left the soldier given the other half nothing to fill.
        Map<String, Order> orders = Tasking.allocate(crew, at, WALL_RADIUS,
                bounds(toDig), bounds(toFill), except, state.fetchingDirt);
        state.fetchingDirt.clear();
        orders.forEach((n, o) -> {
            if (o instanceof Order.Gather) {
                state.fetchingDirt.add(n);
            }
        });
        return orders;
    }

    /** What no ground work digs or fills: the camp's kept structures, its mine, and every squad's stairs. */
    private Set<Cell> groundExcept() {
        Set<Cell> except = new HashSet<>();
        campKeep().forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        campMine().forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        squadStates.values().stream().filter(s -> s.stairs != null).forEach(s -> {
            s.stairs.walkway().forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
            s.stairs.landing().forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        });
        return except;
    }

    /** The least box holding every one of {@code cells}, if there are any. */
    private static Optional<Box> bounds(List<BlockPos> cells) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos c : cells) {
            lo[0] = Math.min(lo[0], c.x());
            lo[1] = Math.min(lo[1], c.y());
            lo[2] = Math.min(lo[2], c.z());
            hi[0] = Math.max(hi[0], c.x());
            hi[1] = Math.max(hi[1], c.y());
            hi[2] = Math.max(hi[2], c.z());
        }
        return Optional.of(new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
    }

    /** The register's name for a ground-work command: what it's on. */
    private static String groundTask(Order order) {
        return switch (order) {
            case Order.Dig _ -> "dig the camp's ground";
            case Order.Fill _ -> "fill the camp's ground";
            default -> "gather SOIL";
        };
    }

    /**
     * {@code soldier}'s ground-work command to the routine that does it: a dig's cells to its
     * clearer, a fill's to its filler, a gather's box to its gatherer. None, and it's off all three.
     */
    private void route(String soldier, Order order) {
        switch (order) {
            case Order.Dig d -> {
                clearing.put(soldier, Clearing.toClear(world, d.box(), d.except()));
                filling.remove(soldier);
                groundGather.remove(soldier);
            }
            case Order.Fill f -> {
                filling.put(soldier, Clearing.toFill(world, f.box(), f.except()));
                clearing.remove(soldier);
                groundGather.remove(soldier);
            }
            case Order.Gather g -> {
                groundGather.put(soldier, g);
                clearing.remove(soldier);
                filling.remove(soldier);
            }
            case null, default -> {
                clearing.remove(soldier);
                filling.remove(soldier);
                groundGather.remove(soldier);
            }
        }
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
            String task = groundGather.containsKey(n) ? "gather SOIL"
                    : filling.containsKey(n) ? "fill the camp's ground"
                    : clearing.containsKey(n) ? "dig the camp's ground"
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
            List<?> left = switch (task) {
                case "dig the camp's ground" -> clearing.get(n);
                case "fill the camp's ground" -> filling.get(n);
                case "wall the camp" -> walling.get(n);
                case "wall the camp in stone" -> repointing.get(n);
                case "light the ground" -> lighting.get(n);
                default -> null;
            };
            on.put(n, new TaskRegister.Assignment(task, s.report(),
                    left == null ? OptionalInt.empty() : OptionalInt.of(left.size())));
        }
        // What's left of each task the squad measures, for a soldier moved off one as its work ran out.
        Map<String, Integer> measured = new HashMap<>();
        if (state.digLeft >= 0) {
            measured.put("dig the camp's ground", state.digLeft);
        }
        if (state.fillLeft >= 0) {
            measured.put("fill the camp's ground", state.fillLeft);
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

    /**
     * Why a soldier can't make torches, for its CANTCO: what it's short of, or with the makings
     * carried, that it has no workshop to make them at - live, a CANTCO read "short of {}".
     */
    private static String torchShortfall(Inventory inventory, Workshop workshop) {
        Map<ItemClass, Integer> short_ = Torchmaker.shortOf(inventory, LIGHTING_TORCHES);
        return !short_.isEmpty() ? "short of " + short_ + " to make them"
                : workshop == null ? "the makings carried, but no workshop sited to make them at"
                : "the makings carried, but not yet made at the workshop at " + workshop.stand();
    }

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
    private Optional<Staircase> campStairs() {
        if (campStairs == null) {
            campStairs = camp == null ? Optional.empty() : mineStore.load(camp[0] + "," + camp[1])
                    .filter(kept -> kept.size() == 2)
                    .map(kept -> new Staircase(kept.get(0), kept.get(1).y()));
        }
        return campStairs;
    }

    /** The camp's mine's walkway and landing, as kept on disk: empty for no camp, or no mine sited. */
    private List<BlockPos> campMine() {
        return campStairs().map(s -> {
            List<BlockPos> cells = new ArrayList<>(s.walkway());
            cells.addAll(s.landing());
            return cells;
        }).orElse(List.of());
    }

    private Set<BlockPos> campKeep() {
        Set<BlockPos> keep = new HashSet<>();
        // Never the wall: levelled round the camp's ground, not the wall's centre, its high ground
        // took in the wall's own blocks, dug out as the masons put them back - live, soldiers broke
        // blocks and put them back all day.
        walls.values().forEach(keep::addAll);
        // A hut going up is kept as well as one built: cobblestone over the floor is dug by day.
        fortifying.values().forEach(f -> keep.addAll(f.cells()));
        for (SquadState s : squadStates.values()) {
            if (s.builtBase != null) {
                s.builtBase.cells().forEach(c -> keep.add(BlockPos.containing(c.x(), c.y(), c.z())));
                s.builtBase.floor().forEach(c -> keep.add(BlockPos.containing(c.x(), c.y() - 1, c.z())));
            }
        }
        List<Workshop> shops = new ArrayList<>(workshops.values());
        if (campWorkshop != null) {
            shops.add(campWorkshop);
        }
        for (Workshop w : shops) {
            Stream.concat(Stream.of(w.stand(), w.table(), w.furnace()), w.chest().stream())
                    .forEach(c -> {
                        keep.add(c);
                        keep.add(c.offset(0, -1, 0));
                    });
        }
        return keep;
    }

    /** How far below the stairs' head a soldier is down the mine, for the mine base's workshop. */
    private static final int MINE_BELOW = 3;

    /**
     * How the squad's task stands, from the world where the task leaves a mark - the wall's open
     * cells, the torches still to put down, the stairs cut - and from its soldiers' reports: the
     * acknowledgement the general decides on. Empty with no task, or a patrol base, whose own status
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
                if (state.stairs != null && statuses.stream().anyMatch(s -> stairsProgress.get(s.name()) instanceof Miner.Progress.Done)) {
                    done = Optional.of("the stairs are cut down to y=" + state.stairs.bottom() + ", and the ore beside them taken");
                } else if (state.stairs != null) {
                    left = Optional.of("the stairs " + stairsCut.get() + " of " + state.stairs.steps() + " steps down");
                }
            }
            case Objective.Task.Patrol() -> left = Optional.of("walking the round, on watch");
            default -> {
            }
        }
        Progress measured = Progress.of(done, left, statuses,
                now - state.workedAt);
        if (state.progress == null || state.progress.state() != measured.state()) {
            LOG.info("[{}] task {}: {} - {}", squad, standing.get().task(), measured.state(), measured.detail());
        }
        state.progress = measured;
        return Optional.of(measured);
    }

    /**
     * Whether to sit this cycle out because a live experiment holds the machine's models: every
     * soldier told to stand still, and no SITREP sent - so neither the general nor a squad leader is
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

    /** The engineer's stair down for a stranded soldier, from where it stands. */
    private Function<SoldierStatus, Optional<List<Cell>>> stairDown(String squadName) {
        return stuck -> {
            String n = stuck.name();
            Position at = stuck.position();
            Optional<List<Cell>> stair = Stairway.down(world,
                            BlockPos.containing(at.x(), at.y(), at.z()))
                    .map(cells -> cells.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList());
            if (stair.isPresent()) {
                LOG.info("[{}] RECOVERY: {} is stranded at {} - a stair down laid out, {} blocks", squadName, n, at, stair.get().size());
            } else {
                LOG.warn("[{}] RECOVERY: {} is stranded at {} and no stair down can be laid from there", squadName, n, at);
            }
            return stair;
        };
    }

    /** The engineer's stair up for a soldier stranded below, from where it stands: the cells to dig. */
    private Function<SoldierStatus, Optional<List<Cell>>> stairUp(String squadName) {
        return stuck -> {
            Position at = stuck.position();
            Optional<List<Cell>> stair = Stairway.up(world,
                            BlockPos.containing(at.x(), at.y(), at.z()))
                    .map(cells -> cells.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList());
            stair.ifPresentOrElse(
                    s -> LOG.info("[{}] RECOVERY: {} is stranded below at {} - a stair up laid out, {} blocks to dig", squadName, stuck.name(), at, s.size()),
                    () -> LOG.warn("[{}] RECOVERY: {} is stranded at {} and no stair up can be dug from there either", squadName, stuck.name(), at));
            return stair;
        };
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
                    Resupply.orders(statuses, Escort.slots(statuses, order, principal),
                            principal, supplyPoint),
                    principal, threats));
        } else {
            // Each soldier's own report, in the statuses' order and only for those led here: the main
            // body's reports also hold its detached soldiers', and a replacement's place differs. Live,
            // recovery set an order by its place in the reports, and failed every tick for half an hour.
            Map<String, SoldierStatus> ownReports = new HashMap<>();
            state.reported.forEach(r -> ownReports.put(r.name(), r));
            List<SoldierStatus> reported = statuses.stream().map(s -> ownReports.getOrDefault(s.name(), s)).toList();
            orders = ReactToContact.orders(statuses, state.recovery.orders(reported,
                    Resupply.orders(statuses, Cohesion.orders(statuses, state.linkUp.orders(statuses, Formations.assign(order, statuses))),
                            order.objective(), supplyPoint), stairDown(squadName), stairUp(squadName)));
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
        // Cross-leveling (docs/military-doctrine.md §13): logs from a squadmate with some to spare
        // to one that needs them for a tool and can't chop any, and food to one that wants it and
        // has none.
        Map<String, Integer> wanted = new HashMap<>();
        statuses.forEach(s -> {
            Integer logs = logsWanted.get(s.name());
            if (logs != null) {
                wanted.put(s.name(), logs);
            }
        });
        // Rations to one running low (command.Subsistence's resupply mark), hungry yet or not: it
        // asks its squadmates before it goes hungry (the operator's).
        Map<String, Integer> fed = new HashMap<>();
        statuses.forEach(s -> s.condition().flatMap(Condition::kit).filter(k -> Subsistence.low(k.rations()))
                .ifPresent(k -> fed.put(s.name(), CrossLevel.RATIONS_GIVEN)));
        // And a spare weapon to one bare-handed: a replacement respawns with nothing. Not to one
        // making its kit: mid-craft its weapon can be on a window's cursor, and it isn't counted -
        // in the squad simulation a sword went round the squad from hand to hand that way.
        Map<String, Integer> disarmed = new HashMap<>();
        statuses.stream().filter(s -> !(s.report() instanceof Report.Rearming))
                .forEach(s -> s.condition().flatMap(Condition::kit).filter(k -> k.weapon().isEmpty())
                        .ifPresent(k -> disarmed.put(s.name(), 1)));
        List<CrossLevel.Transfer> transfers = CrossLevel.pair(statuses, wanted, fed, disarmed);
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
        // A squad that has laid out its base stays on it: the general re-plans every few seconds and
        // nudges the objective a block or two, and an outline that moves keeps the soldiers walking.
        Position site = base.map(o -> o.location()).orElse(null);
        // Within the square the site was searched in: an old hut taken back can stand at its corner.
        if (site != null && state.baseSite != null && Math.max(Math.abs(site.x() - state.baseSite.x()), Math.abs(site.z() - state.baseSite.z())) <= SITE_SEARCH
                && Math.abs(site.y() - state.baseSite.y()) <= GROUND_SEARCH) {
            site = state.baseSite;
        } else if (site != null) {
            // The model's point is a guess at ground it can't see closely: the base goes on the
            // nearest level footing for the whole floor - never on rubble or a slope, where
            // soldiers can't all stand in their places.
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
        // Moving on from a base: cut a doorway toward the next objective. Not while only paused for
        // contact - the base is still the squad's.
        String opener = null;
        // A soldier shut in a room the squad has no record of - a base built before a restart, say -
        // leaves it the same way as one it remembers: the room is read from the world.
        if (state.builtBase == null && objectiveOf(squadName).isPresent()) {
            statuses.stream().filter(st -> plan == null || !plan.inside(st.position()))
                    // Walled by what a squad builds with, not a tree: live, a soldier up a canopy was
                    // taken for shut in a hut, cut a doorway out of the leaves, and fell ten blocks.
                    .flatMap(st -> PatrolBase.enclosing(st.position(), c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z())
                                    && !world.isLog((int) c.x(), (int) c.y(), (int) c.z())
                                    && world.leaves((int) c.x(), (int) c.y(), (int) c.z()).isEmpty())
                            .map(room -> Map.entry(st.name(), room)).stream())
                    .findFirst().ifPresent(shutIn -> {
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
            // Toward where the general is sending the squad: the squad leader's own order can still
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
        // The farm: the plot round the water nearest the general's objective, kept while the
        // objective stays put - a farm is tended for days, not re-sited each cycle.
        FarmPlan farm = taskOf(squadName, Objective.Task.Farm.class).map(o -> {
            BlockPos at = BlockPos.containing(
                    o.location().x(), o.location().y(), o.location().z());
            // Kept for the point it was sited for, as a wall is: live, its water lay just over the
            // radius from the site, and it was sited afresh every cycle, found or not by where a
            // soldier stood.
            if (state.farm != null && (at.equals(state.farmFor)
                    || Math.hypot(state.farm.water().x() - at.x(), state.farm.water().z() - at.z()) <= FARM_SITE_RADIUS)) {
                return state.farm;
            }
            Position from = statuses.get(0).position();
            Set<BlockPos> walkable = Pathfinder.reachable(
                    world, BlockPos.containing(from.x(), from.y(), from.z()),
                    (int) Math.hypot(at.x() - from.x(), at.z() - from.z()) + FARM_SITE_RADIUS + 4, FARM_REACH_BUDGET);
            FarmPlan sited = FarmPlan.site(world, at,
                    FARM_SITE_RADIUS, FARM_SITE_RADIUS, walkable::contains).orElse(null);
            if (sited == null) {
                LOG.warn("[{}] no ground to farm by water within {} blocks of {}", squadName, FARM_SITE_RADIUS, o.location());
            } else {
                LOG.info("[{}] FARM by the water at {}: {} cells to plant", squadName, sited.water(), sited.plot().size());
                state.farmFor = at;
            }
            return sited;
        }).orElse(null);
        state.farm = farm;
        // The tree farm: laid out round the objective's ground once, and kept while the objective
        // stays put - what grows in it is the farm working, not a reason to lay it out again.
        TreeFarm trees = taskOf(squadName, Objective.Task.TreeFarm.class).map(o -> {
            BlockPos at = TerrainSurveyor.groundAt(world,
                    BlockPos.containing(o.location().x(), o.location().y(), o.location().z()));
            if (state.trees != null && state.trees.centre().x() == at.x() && state.trees.centre().z() == at.z()) {
                return state.trees;
            }
            TreeFarm sited = TreeFarm.site(world, at,
                    TREE_FARM_RADIUS).orElse(null);
            if (sited == null) {
                LOG.warn("[{}] TREE_FARM: no open dirt or grass to plant round {}", squadName, o.location());
            } else {
                LOG.info("[{}] TREE_FARM round {}: {} cells to plant", squadName, at, sited.plot().size());
            }
            return sited;
        }).orElse(null);
        state.trees = trees;
        // The wall: laid out once round the objective's ground, then filled in - laid out again
        // after a row, the ground would be the wall's top.
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
            // At the camp, the camp's wall - the one its standard measures - not a new one round
            // wherever the order points.
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
                    List<BlockPos> layout = Perimeter
                            .layout(world, at, WALL_RADIUS);
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
        Map<String, Order> ground = new HashMap<>(state.wallCentre == null || squadInContact ? Map.of()
                : groundwork(squadName, state, state.wallCentre, statuses));
        // A soldier told to level ground: its dig or fill, as the world shows what's left.
        if (!squadInContact) {
            statuses.forEach(s -> planBook.of(s.name()).current()
                    .filter(d -> d instanceof Directive.Level)
                    .flatMap(d -> levelWork(s, (Directive.Level) d))
                    .ifPresent(work -> ground.put(s.name(), work)));
        }
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
                return state.stairs;
            }
            // Where the stairs can be cut all the way down: the objective's x and z can be beside
            // water - live, the camp's mine stopped at its second step by a pond. Nowhere there is
            // said once and looked for again a minute on: live, it was searched and said every
            // second, 747 times, beside a flooded shaft.
            // The camp's mine, kept from before a restart, is cut on down wherever it was sited.
            // Its head on the camp's ground, not where a soldier stands: live, one standing down an
            // old shaft had a staircase sited from a step in it.
            int nearY = campGround != null && Math.hypot(campGround.x() - x, campGround.z() - z) <= 2 * Staircase.SITE_RADIUS
                    ? campGround.y() : (int) Math.floor(statuses.get(0).position().y());
            // A site on the ground says where, not how deep: down to the iron. Live, a miner's site at
            // the camp's height was taken for its depth, and three miners stood over one step.
            int asked = (int) Math.floor(o.location().y());
            int depth = asked >= nearY - 1 ? ChainOfCommand.MINE_DEPTH : asked;
            // Any mine ordered near the camp is the camp's: the general's point is a guess - live, it
            // moved the order to the camp's centre, 13 blocks from the mine cut to the bottom, and a
            // new staircase was started inside the wall and kept in its place.
            Optional<Staircase> kept = campStairs().filter(k ->
                    (Math.max(Math.abs(k.head().x() - x), Math.abs(k.head().z() - z)) <= Staircase.SITE_RADIUS
                            || nearCamp(x, z))
                            && k.bottom() == Math.max(Staircase.DEEPEST, Math.min(depth, k.head().y() - 1)));
            if (kept.isPresent()) {
                LOG.info("[{}] MINE: the camp's staircase, kept on disk: down from {} to y={}", squadName, kept.get().head(), kept.get().bottom());
                state.stairsFor = new int[]{x, z};
                treeClaims.addAll(kept.get().walkway());
                return kept.get();
            }
            boolean failedHere = state.stairsFor != null && state.stairsFor[0] == x && state.stairsFor[1] == z;
            if (failedHere && clockwork.millis() < state.stairsRetryAt) {
                return null;
            }
            Optional<Staircase> sited = Staircase.site(
                    world, x, z, nearY, depth);
            if (sited.isEmpty()) {
                if (!failedHere) {
                    LOG.warn("[{}] MINE: nowhere within {} blocks of x={} z={} a staircase can be cut down clear of water and lava - there itself, {}",
                            squadName, Staircase.SITE_RADIUS, x, z,
                            Staircase.whyNot(world, x, z, nearY, depth));
                }
                state.stairsFor = new int[]{x, z};
                state.stairsRetryAt = clockwork.millis() + STAIRS_RETRY_MILLIS;
                return null;
            }
            LOG.info("[{}] MINE: a staircase down from {} to y={}", squadName, sited.get().head(), sited.get().bottom());
            state.stairsFor = new int[]{x, z};
            // Its stairs and tunnels are claimed: no table, furnace or other digger goes in them.
            treeClaims.addAll(sited.get().walkway());
            return sited.get();
        }).orElse(null);
        state.stairs = stairs;
        if (stairs == null) {
            nightBelow.remove(squadName);
        }
        if (stairs != null && camp != null && nearCamp(stairs.head().x(), stairs.head().z()) && !campStairs().equals(Optional.of(stairs))) {
            mineStore.save(camp[0] + "," + camp[1], List.of(stairs.head(), stairs.step(stairs.steps() - 1)));
            campStairs = Optional.of(stairs);
        }
        siteWorkshop(squadName, state, statuses, plan == null ? List.of()
                : Stream.of(plan.cells(), plan.floor(), plan.floor().stream().map(f -> new Position(f.x(), f.y() + 1, f.z())).toList())
                        .flatMap(List::stream).map(c -> BlockPos.containing(c.x(), c.y(), c.z())).toList());
        // The one who cuts it: the first soldier carrying a pickaxe. Until one does, all make one.
        String cutter = stairs == null || squadInContact ? null : statuses.stream()
                .filter(st -> st.condition().flatMap(c -> c.kit()).map(Kit::pickaxe).orElse(false))
                .map(SoldierStatus::name).findFirst().orElse(null);
        // Securing: the torches still to put down round the objective, worked out again each cycle -
        // every torch put down, by anyone, changes what's left. Read once: the general's thread may
        // change the squad's task mid-cycle - live, a second read found SECURE gone and the squad
        // loop died on it.
        Optional<Objective> secureTask = taskOf(squadName, Objective.Task.Secure.class);
        List<BlockPos> torchesLeft = secureTask.map(o -> {
            // Round the ground most of the camp stands on, as the wall and the foothold's measure are:
            // live, the ground found from the objective's height was a hut roof, and SECURE reported
            // done with 371 cells of the camp still dark.
            // At the camp, the camp's own ground, as the foothold measures it: live, SECURE lit a disc
            // round the general's point, 3 blocks off the camp's centre and 2 above its ground, and
            // reported DONE for days with 8 of the camp's cells dark.
            if (campGround != null && Math.hypot(o.location().x() - campGround.x(), o.location().z() - campGround.z()) <= SECURE_RADIUS) {
                return torchPlan(campGround);
            }
            BlockPos at = typicalGround((int) Math.floor(o.location().x()),
                    (int) Math.floor(o.location().z()), (int) Math.floor(o.location().y()))
                    .orElse(TerrainSurveyor.groundAt(world,
                            BlockPos.containing(o.location().x(), o.location().y(), o.location().z())));
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
        // the ground work, never done, held every soldier all day, and the wall's last dirt block
        // stood for days.
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
            // Stranded with nobody to fetch it: the recovery drill sends it down to its squad, over
            // the stand its own routine wants and can't reach.
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
            Optional.ofNullable(workshopOf(state, statuses.get(i).position()))
                    .ifPresentOrElse(w -> workshops.put(soldierName, w), () -> workshops.remove(soldierName));
            // Its own unit's hand-over first, else one across squads.
            transfers.stream().filter(t -> t.giver().equals(soldierName)).findFirst()
                    .or(() -> Optional.ofNullable(acrossSquads.get(soldierName)))
                    .ifPresentOrElse(t -> deliveries.put(soldierName, t), () -> deliveries.remove(soldierName));
            // The basic load - a weapon up to a stone sword - is the soldier's own to keep, out of
            // contact; beyond it, the squad leader's leave. Live, 2 of 8 carried a stone sword or
            // better for days, every rung waiting on "rearm".
            rearmAllowed.put(soldierName, !squadInContact);
            upgradeAllowed.put(soldierName, order.rearm() && !squadInContact && !baseOrdered);
            stoneSetAside.put(soldierName, stoneShare);
            Optional<Objective> gather = squadInContact
                    ? Optional.empty() : taskOf(squadName, Objective.Task.Gather.class);
            // Nothing left round the general's point: to where the squad saw some, while that order
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
                Objective there = new Objective(
                        ordered.mission(), elsewhere, ordered.intent(), ordered.task());
                LOG.info("[{}] {} finds nothing left to gather at {} - goes where the squad saw some, {}", squadName, soldierName,
                        ordered.location(), elsewhere);
                gatherSent.put(soldierName, Map.entry(ordered, there));
                return there;
            });
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            // Feeding the squad is its own first priority of work (FM 4-0, field feeding): short of
            // food, half of it keeps the camp's farm by day whatever its task - live, the farm was
            // tended only under a FARM order, and the squads went hungry for hours with wheat to grow.
            boolean hungrySquad = state.classOne != null
                    && state.classOne.rating() != Subsistence.Rating.GREEN;
            boolean farmDay = !chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
            // Every other soldier by its place in the squad's roster, not in this cycle's reports:
            // live, their order changed cycle to cycle, and a farmer was taken off its harvest every
            // few seconds and never reached it.
            boolean farmHalf = statuses.stream().map(SoldierStatus::name).sorted().toList().indexOf(soldierName) % 2 == 0;
            // Ordered to farm where there's no ground to: the camp's farm, saying why it's there. Live,
            // a farmer's CANTCO for a dry site went unanswered all day, and it stood there.
            Optional<Objective> farmOrdered = taskOf(squadName, Objective.Task.Farm.class);
            boolean siteDry = farmOrdered.isPresent() && farm == null;
            FarmPlan kept = farm != null ? farm
                    : siteDry && campFarm != null ? campFarm
                    : hungrySquad && farmDay && farmHalf ? campFarm : null;
            if (kept != null && !squadInContact) {
                farming.put(soldierName, kept);
            } else {
                farming.remove(soldierName);
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
            Order work = ground.get(soldierName);
            if (work != null && !state.relieved(soldierName, groundTask(work), now)) {
                // The task's now, not the upkeep's: left the upkeep's, the upkeep would drop it again
                // on seeing the wall - in the squad simulation, every soldier on and off its dig.
                upkeeping.remove(soldierName);
                route(soldierName, work);
            } else if (!upkeeping.contains(soldierName)) {
                route(soldierName, null); // camp upkeep's, if it's on that, is the upkeep's to drop
            }
            if (!soft.isEmpty() && !squadInContact && !state.relieved(soldierName, "wall the camp in stone", now)) {
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
                // The stockpile first, when it was last seen holding a load: one squad's quarrying is
                // another's wall.
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
                            "Blocks to wall the camp in",
                            new Objective.Task.Gather(Resource.SOIL)));
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
            if (trees != null && !squadInContact) {
                planting.put(soldierName, trees);
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
            if (stairs != null && !squadInContact && torchesLeft.isEmpty() && !soldierName.equals(cutter) && canLight) {
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
                // one at the landing reported CANTCO for half an hour on lights handed it while it
                // had torches, never taken back.
                lighting.remove(soldierName);
            }
            if (stairs != null) {
                mining.add(soldierName);
            } else {
                mining.remove(soldierName);
            }
            if (stairs != null && soldierName.equals(cutter)) {
                cutting.put(soldierName, stairs);
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
                // by day. By night they go down behind it: the top is the dark, and down the stairs
                // nothing spawns within 24 blocks of them.
                boolean dark = chainOfCommand.time().map(TimeOfDay::dark).orElse(false);
                if (dark) {
                    nightBelow.put(squadName, stairs.head().y());
                } else {
                    nightBelow.remove(squadName);
                }
                // With nothing left to cut, by night the squad waits at the landing, under the
                // ground: live, a mine cut to the bottom left it all night at the head, under the sky.
                Position rest = dark && stairs.landingCut(world) ? centre(stairs.landingStand()) : centre(stairs.head());
                Position cutterAt = cutter == null ? rest
                        : armourers.get(cutter).standAt().or(() -> miners.get(cutter).standAt()).orElse(rest);
                boolean isCutter = soldierName.equals(cutter);
                // One holding the top goes on its errand first: its kit at the workshop, torches made, the stairs lit.
                // Not by night: the top is the dark, and the squad stays down with its cutter.
                Optional<Position> errand = isCutter || dark ? Optional.empty()
                        : Optional.ofNullable(armourers.get(soldierName)).flatMap(Armourer::standAt)
                        .or(() -> Optional.ofNullable(torchmakers.get(soldierName)).flatMap(Torchmaker::standAt))
                        .or(() -> Optional.ofNullable(lamplighters.get(soldierName))
                                .flatMap(Lamplighter::standAt));
                if (errand.isPresent()) {
                    byName.get(soldierName).receiveOrder(new Order.MoveTo(errand.get().x(), errand.get().y(), errand.get().z(), 0.3, 0.5, Risk.LOW));
                    continue;
                }
                Position place = isCutter ? cutterAt
                        : dark ? cutterAt
                        : new Position(stairs.head().x() - 1.5 + i, stairs.head().y(), stairs.head().z() + 3.5);
                // Exactly where a cut wants it - or the head, where the next cut starts; but waiting the
                // night out at the landing, as loosely as the rest: live, the landing's stand lost its
                // floor to the ore under it, and the cutter, held to a spot it couldn't stand in,
                // reported itself stranded a block below.
                boolean cutting = isCutter && !(dark && cutterAt.equals(rest) && !rest.equals(centre(stairs.head())));
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(),
                        cutting ? 0.3 : dark ? MINE_FOLLOW : 1.5, cutting ? 0.5 : dark ? MINE_FOLLOW : 4, Risk.LOW));
                continue;
            }
            Order given = orders.get(i);
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
                // Where the clearer stands for its next cut; until it has one, toward the stair's foot.
                Position at = clearers.get(soldierName).standAt().orElse(new Position(dig.box().minX() + 0.5, dig.box().maxY() + 1,
                        dig.box().minZ() + 0.5));
                byName.get(soldierName).receiveOrder(new Order.MoveTo(at.x(), at.y(), at.z(), clearers.get(soldierName).standAt().isPresent() ? 0.3 : 3,
                        clearers.get(soldierName).standAt().isPresent() ? 0.5 : 3, Risk.LOW));
                continue;
            }
            if (given instanceof Order.Place place) {
                // Concrete work: the soldier builds the cells, standing where the building says.
                List<BlockPos> cells = place.cells().stream()
                        .map(c -> new BlockPos(c.x(), c.y(), c.z())).toList();
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
            // Short of its share of the hut's blocks: dirt, dug beside the site, makes up the rest -
            // a squad with nothing to build with still shelters by night.
            Kit carried = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
            // And one that runs out mid-build, with the hut still open, goes out for more: an open
            // hut shelters nobody, and waiting inside it for blocks that won't come is a night lost.
            int carriedBlocks = carried == null ? 0 : carried.stone() + carried.dirt() + carried.logs();
            if (building != null && carried != null && !squadInContact && baseBlocks > 0
                    && (allInside ? carriedBlocks == 0 : carriedBlocks < stoneShare)) {
                Position hutPlace = building.stands().get(i);
                Position dirtPlace = new Position(hutPlace.x() + SOIL_OFFSET, hutPlace.y(), hutPlace.z());
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
                fortifying.put(soldierName, new Fortify(place, List.copyOf(cells), allInside, makeTorches,
                        soldierName.equals(lighter)));
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
                stand = depositors.get(soldierName).standAt().map(chest -> chestStand(self.position(), chest).orElse(chest));
            }
            if (stand.isEmpty()) {
                stand = storeWalks.getOrDefault(soldierName, Optional.empty());
            }
            if (stand.isEmpty() && handovers.containsKey(soldierName)) {
                stand = handovers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && scavengers.containsKey(soldierName)) {
                stand = scavengers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && receiving.contains(soldierName)) {
                // Walk onto what's been thrown: the server hands a dropped stack to whoever touches it.
                stand = entityTracker.nearbyItems(statuses.get(i).position(), PICKUP_RADIUS).stream().findFirst();
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
                given = new Order.MoveTo(stand.get().x(), stand.get().y(), stand.get().z(), tolerance, 0.5, Risk.LOW);
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
    private Optional<Position> chestStand(Position from, Position chest) {
        BlockPos feet = BlockPos.containing(from.x(), from.y(), from.z());
        int cx = (int) Math.floor(chest.x()), cy = (int) Math.floor(chest.y()), cz = (int) Math.floor(chest.z());
        int span = Math.max(Math.abs(cx - feet.x()), Math.abs(cz - feet.z())) + CHEST_STAND_ACROSS + 2;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, CHEST_STAND_BUDGET, 0, false).keySet();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -CHEST_STAND_ACROSS; dx <= CHEST_STAND_ACROSS; dx++) {
            for (int dz = -CHEST_STAND_ACROSS; dz <= CHEST_STAND_ACROSS; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos cell = new BlockPos(cx + dx, cy + dy, cz + dz);
                    Position centre = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    if (!reachable.contains(cell) || !Depositor.withinReach(centre, chest)) {
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
        return IntStream.rangeClosed(-1, 1).mapToObj(dy -> chunkCache.stateIdAt(x, y + dy, z))
                .map(id -> id.isPresent() ? String.valueOf(id.getAsInt()) : "?").collect(Collectors.joining("/"))
                + " at " + x + " " + (y - 1) + ".." + (y + 1) + " " + z;
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
                .map(ResourceSite::location)
                .sorted(Comparator.comparingDouble(away)).toList();
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

    private static Position centre(BlockPos cell) {
        return new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }

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
    /** How far from the general's point to look for level ground for a patrol base. */
    private static final int SITE_SEARCH = 8;
    /**
     * How close to its place's centre a soldier stands inside a hut: a body 0.6 wide is clear of the
     * walls only within 0.2 of it, and the server won't place a block where a body is.
     */
    private static final double HUT_PLACE_TOLERANCE = 0.15;

    /**
     * The columns each squad's patrol base stands on, walls included - squad loop only. The general
     * may send two squads to one point; their huts go side by side, not one inside the other.
     */
    private final Map<String, Set<Long>> baseFootprints = new ConcurrentHashMap<>();

    private static Set<Long> footprint(PatrolBase.Plan plan) {
        return Stream.concat(plan.floor().stream(), plan.cells().stream())
                .map(c -> column((int) Math.floor(c.x()), (int) Math.floor(c.z())))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** How near a waypoint counts as there, for a patrol's next leg. */
    private static final double PATROL_REACHED = 2.5;

    /**
     * A patrol's round: the corners and the midpoints of the square {@link #PATROL_RADIUS} out from
     * the ground the objective stands on, clockwise from the north-west, each on the ground there.
     */
    private List<Position> patrolRound(Position objective) {
        BlockPos centre = typicalGround((int) Math.floor(objective.x()), (int) Math.floor(objective.z()), (int) Math.floor(objective.y()))
                .orElse(TerrainSurveyor.groundAt(world,
                        BlockPos.containing(objective.x(), objective.y(), objective.z())));
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

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * The nearest point to {@code asked}, within {@value #SITE_SEARCH} blocks across and
     * {@value #GROUND_SEARCH} up or down, where every floor cell of a hut for {@code soldiers} is
     * standable at one height: solid below, room for a body above - and the hut's walls stand at
     * least a block clear of every other squad's, so each keeps a side to cut a doorway through.
     */
    private Optional<Position> levelSite(Position asked, int soldiers, String squadName, Position from) {
        // Only footing the squad can walk to: a level roof of an old hut two blocks up is no site.
        Set<BlockPos> reachable = Pathfinder.reachable(
                world, TerrainSurveyor.groundAt(world,
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
        // sealed the workshop's chest inside it, and the miners stood all night unable to put their
        // loads away.
        Workshop shop = campWorkshop;
        if (shop != null) {
            Stream.concat(Stream.of(shop.stand(), shop.table(), shop.furnace()), shop.chest().stream())
                    .forEach(c -> taken.add(column(c.x(), c.z())));
        }
        campStairs().ifPresent(st -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    taken.add(column(st.head().x() + dx, st.head().z() + dz));
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
                unload -> chunkCache.unload(unload.chunkX(), unload.chunkZ(), name),
                position -> {
                    if (!firstPosition.complete(position)) {
                        DeterministicSoldier soldier = activeSoldier.get();
                        if (soldier != null) {
                            soldier.correctPosition(new Position(position.x(), position.y(), position.z()));
                        }
                    }
                },
                entity -> entityTracker.spawn(entity.entityId(), entity.uuid(), entity.typeId(), entity.x(), entity.y(), entity.z(), name),
                move -> entityTracker.move(move.entityId(), move.x(), move.y(), move.z()),
                ids -> entityTracker.remove(ids, name),
                health -> {
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
                },
                update -> {
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
                // is passed on once; one whispered to a soldier (/msg) reaches only that one, which
                // relays it up the chain.
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
                                case OperatorOrders.Order.Remark(String operator, String remark) ->
                                        chainOfCommand.onOperatorRemark(operator, remark);
                                case OperatorOrders.Order.Unknown(String operator, String command) ->
                                        chainOfCommand.onOperatorUnknown(operator, command);
                            }
                        });
                    }
                },
                container -> {
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
                },
                death -> {
                    LOG.warn("[Casualty] {}", recorders.computeIfAbsent(name, n -> new FlightRecorder()).casualty(name, death));
                    String cause = death.contains("[") ? death.substring(0, death.indexOf('[')) : death;
                    casualties.merge(cause, 1, Integer::sum);
                    afterActionReview.death(name, cause);
                    afterActionReview.commands.casualty(squadTask(unitOf(name, definition.squad())), Optional.ofNullable(activeSoldiers.get(name))
                            .map(s -> s.status().order().getClass().getSimpleName()).orElse("none"));
                    LOG.warn("[Casualty] since the app started: {}", casualties);
                },
                cause -> onDisconnected(definition, activeScheduler, cause)));
        joined.thenAccept(sessionReady::complete);
        joined.thenAcceptBoth(firstPosition, (session, spawn) -> onJoined(definition, session, spawn, activeScheduler, activeSoldier, lastHealth, lastKit, inventory, supplyRun, armourerRef, depositorRef, torchmakerRef))
                .exceptionally(cause -> {
                    LOG.error("{} failed to join {}:{} - {}", name, HOST, PORT, cause.getMessage(), cause);
                    return null;
                });
    }

    /** One inventory slot, its enchantments named through this session's synced registry. */
    private static Kit setSlot(Inventory inventory, int slot, Optional<ItemSlot> item, List<String> enchantmentNames) {
        if (item.isEmpty()) {
            return inventory.setSlot(slot, null);
        }
        Map<String, Integer> named = new HashMap<>();
        item.get().enchantments().forEach((id, level) -> {
            if (id < 0 || id >= enchantmentNames.size()) {
                throw new IllegalStateException("enchantment " + id + " isn't in the synced registry of " + enchantmentNames.size());
            }
            named.put(enchantmentNames.get(id), level);
        });
        return inventory.setSlot(slot, new int[]{item.get().itemId(), item.get().count()}, named);
    }

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
            // The point man carries the squad's radio: the squad leader and general aren't in the
            // world, so their traffic is keyed out over his connection (rebound on reconnect).
            chainOfCommand.setRadio(session::sendChatCommand);
            // The ground round the point, not the top of whatever stands on it: a heap of old huts
            // at the camp's centre isn't where an objective there is.
            chainOfCommand.setGround((x, z, nearY) -> typicalGround(x, z, nearY)
                    .map(cell -> OptionalInt.of(cell.y())).orElse(OptionalInt.empty()));
        }
        activeSoldiers.put(name, soldier);
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
        Gatherer gatherer = new Gatherer(name, session, inventory, treeClaims);
        gatherers.put(name, gatherer);
        Farmer farmer = new Farmer(name, session,
                inventory, treeClaims, farmCellsFailed);
        farmers.put(name, farmer);
        Forester forester = new Forester(name, session,
                inventory, treeClaims);
        foresters.put(name, forester);
        Depositor depositor = new Depositor(name, session, inventory, chainOfCommand::onStockpile);
        Fitter storeFitter = new Fitter(name, session,
                inventory, furnaces);
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
        AtomicReference<Optional<Recipe>> ladderStep = new AtomicReference<>(Optional.empty());
        AtomicBoolean armourerWas = new AtomicBoolean();
        // Ticks while told to light the base: one torch tried a second, until the light check sees it.
        AtomicInteger torchTicks = new AtomicInteger();
        torchmakerRef.set(torchmaker);
        Lamplighter lamplighter = new Lamplighter(name, inventory, footing, torchClaims);
        lamplighters.put(name, lamplighter);
        Hunter hunter = new Hunter(name);
        hunters.put(name, hunter);
        Mason mason = new Mason(name, inventory, footing,
                wallClaims);
        masons.put(name, mason);
        Clearer clearer = new Clearer(name, session, inventory,
                clearClaims);
        clearers.put(name, clearer);
        Filler filler = new Filler(name, session, inventory);
        fillers.put(name, filler);
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
            soldier.tick(world).ifPresent(target -> {
                LOG.info("{} attacks entity {}", name, target);
                session.attack(target);
            });
            SoldierStatus status = soldier.status();
            boolean bareHanded = status.condition().flatMap(c -> c.kit()).map(k -> k.weapon().isEmpty()).orElse(false);
            boolean inContact = status.report() instanceof Report.InContact;
            // Under threat, routines pause - they pick up where they left off once it's gone.
            // A hostile it can see: live, two zombies in the dark of the camp's mine shaft, 8 blocks off
            // and out of sight below, held every mason's hands a whole day.
            Position eyes = new Position(status.position().x(), status.position().y() + 1.62, status.position().z());
            boolean threatened = inContact || world.nearbyHostiles(status.position(), REARM_CLEAR_RADIUS).stream()
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
                    status.position()), "Arrows for the bow", new Objective.Task.Gather(
                    Resource.FLINT));
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
            Optional<Recipe> rung = ladderStep.get();
            rung.ifPresent(tools::add);
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
            Optional<Workshop> workshop = Optional.ofNullable(workshops.get(name)).or(() -> Optional.ofNullable(campWorkshop));
            // Raw ore through the common stockpile (bot.supply.MetalPool): what its next piece can't
            // be made from goes in; what the stockpile completes it with is drawn - and the armourer
            // waits for the draw rather than walking off to dig.
            MetalPool.Plan metal = MetalPool.plan(inventory, rung, chainOfCommand.stockpile());
            Optional<MetalPool.Draw> metalDraw = metal.draw().filter(d -> stockpileFor(name).isPresent());
            boolean drawingMetal = metalDraw.isPresent() || depositor.drawing(ItemClass.RAW_IRON) || depositor.drawing(ItemClass.RAW_COPPER);
            if (drawingMetal) {
                rung.ifPresent(tools::remove);
            }
            boolean poolOre = metal.stocking(inventory) >= POOL_TRIP;
            // A farmer's hoe, and its wheat baked into bread three at a time.
            FarmPlan field = farming.get(name);
            if (field != null && inventory.count(ItemClass.HOE) == 0) {
                tools.add(Recipe.WOODEN_HOE);
            }
            // Bread from the wheat it carries: a farmer's work, and anyone's below its basic load
            // (command.Subsistence) - live, a soldier carried 22 wheat for days while its squad starved.
            int carriedRations = status.condition().flatMap(c -> c.kit()).map(Kit::rations).orElse(0);
            // And still wanted while a loaf is under way: its wheat in the table's grid isn't carried.
            // Live, a soldier with 4 wheat put 3 in the grid, wanted no bread with the 1 left, and
            // started and dropped the loaf 518 times.
            if (inventory.count(ItemClass.WHEAT) >= 3 && (field != null || carriedRations < Subsistence.BASIC_LOAD)
                    || armourer.making(Recipe.BREAD)) {
                tools.add(Recipe.BREAD);
            }
            // Arrows from the flint and feathers it has picked up, while it has a bow to shoot them.
            if (inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.FLINT) > 0 && inventory.count(ItemClass.FEATHER) > 0
                    && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED) {
                tools.add(Recipe.ARROWS);
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
            Opening exit = opening.get(name);
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
            Set<Recipe> craftable = fort == null ? tools
                    : waiting && rearming ? Set.of(rung.orElseThrow())
                    : Set.of();
            Kit carried = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean fullLoad = carried != null && (carried.logs() >= Gatherer.CARRY_LOGS || carried.stone() >= Gatherer.CARRY_STONE);
            CrossLevel.Transfer giving = deliveries.get(name);
            boolean atBuildPlace = fort != null && fort.allInside() && !inContact
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.2;
            AtomicReference<Builder.Progress> built = new AtomicReference<>();
            // Who has the soldier's hands this tick: each routine says whether it's under way and
            // what it would do if free; Hands decides who's free, in its one priority order.
            Map<Hands.Routine, Hands.Turn> turns = new EnumMap<>(Hands.Routine.class);
            turns.put(Hands.Routine.SUPPLY_RUN, turn(run::active,
                    free -> run.tick(status.position(), supplyPoint, free && Resupply.isShort(status) && !inContact)));
            turns.put(Hands.Routine.DOORWAY, turn(() -> exit != null, free -> {
                if (free && exit != null && Math.hypot(status.position().x() - exit.stand().x(), status.position().z() - exit.stand().z()) < 0.5) {
                    doorway.tick(world, exit.cells());
                } else {
                    doorway.stop();
                }
            }));
            // Securing ground: a soldier out of torches makes more where it stands, then puts them down.
            List<BlockPos> lights = lighting.getOrDefault(name, List.of());
            boolean torchesWanted = !lights.isEmpty() && inventory.count(ItemClassRegistry.ItemClass.TORCH) == 0;
            turns.put(Hands.Routine.TORCHMAKER, turn(torchmaker::active,
                    free -> torchmaker.tick(status.position(), world, !free ? 0 : atWorkshop ? Torchmaker.TORCHES
                            : torchesWanted ? Math.min(LIGHTING_TORCHES, (lights.size() + 3) / 4 * 4) : 0, threatened, workshop)));
            // Stuck in water: plug the sources round it - stopping the flow, and each block one more
            // to climb out by - nearest first, from where it floats.
            turns.put(Hands.Routine.SEALER, turn(sealing::get, free -> {
                if (!free || !stuckInWater || inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.LOG) == 0) {
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
                            // stuck soldiers was all flowing, its source out of reach, and with
                            // only sources to plug they placed nothing.
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
                    return;
                }
                if (!sealing.getAndSet(true)) {
                    LOG.warn("{} stuck in the water at {} - fills the {} cell(s) of water round it", name, at, sources.size());
                }
                if (sealer.tickInPlace(at, world, sources) instanceof Builder.Progress.Failed) {
                    sealer.retrySkipped();
                }
            }));
            turns.put(Hands.Routine.RATIONS, turn(rations::active, free -> {
                rations.tick(status.condition().map(c -> c.food()).orElse(Condition.MAX_FOOD), free && !threatened,
                        soldier.yaw(), soldier.pitch());
                // Its pack kept for what's worth more: surplus seeds thrown away, between meals.
                pack.tick(free && !threatened && !rations.active());
            }));
            Kit eating = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean hungry = status.condition().filter(c -> c.kit().isPresent()).map(SoldierRunner::wantsFood).orElse(false);
            // Feathers for arrows, for a soldier with a bow and few arrows: a chicken, hunted like any game.
            boolean wantsFeathers = inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED
                    && inventory.count(ItemClass.FEATHER) == 0;
            // Short of its basic load's low mark, it restocks: any game in sight is hunted, fed or not.
            boolean shortOfFood = eating != null && Subsistence.short_(eating.rations());
            IntPredicate quarry = hungry || shortOfFood ? kind -> true
                    : kind -> kind == EntityTracker.CHICKEN_TYPE_ID;
            // Food first: the day's work - gathering, lighting, walling, planting - gives the hands
            // up when there's game in sight to eat, and the hunter takes them next tick.
            Position huntGround = huntGrounds.getOrDefault(name, status.position());
            int food = status.condition().map(Condition::food).orElse(Condition.MAX_FOOD);
            boolean gameNear = (hungry || shortOfFood) && Hunter.gameInSight(world, huntGround, food, quarry);
            boolean huntWanted = (hungry || wantsFeathers || shortOfFood && gameNear) && huntAllowed.contains(name) && !inContact;
            // Short with no means of its own - no wheat to bake, no game in sight - it's the squad's
            // to solve: a squadmate shares, or the squad asks for food (Class I RED).
            boolean stockpiledFood = chainOfCommand.stockpile().map(st -> st.rations() > 0).orElse(false) && stockpileFor(name).isPresent();
            if (shortOfFood && inventory.count(ItemClass.WHEAT) < 3 && !gameNear && !stockpiledFood) {
                foodless.add(name);
            } else {
                foodless.remove(name);
            }
            // And a meal carried comes before either: live, a soldier on a stone run back to back sat at
            // food 0 with two rations in its pack.
            boolean foodCalls = huntWanted && !hunter.active()
                    && Hunter.gameInSight(world, huntGround, food, quarry)
                    || !rations.active() && !threatened && food < Condition.REGENERATION_FOOD && inventory.slotOf(ItemClass.FOOD) >= 0;
            turns.put(Hands.Routine.HUNTER, turn(hunter::active, free -> hunter.tick(status.position(), huntGround,
                    Hunter.radius(food), quarry, world,
                    free && huntWanted,
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
            boolean fitStore = (fullLoad || Depositor.surplusFood(inventory) || poolOre) && supplyPoint.isEmpty() && shop.isPresent() && shop.get().chest().isPresent()
                    && !shop.get().chestStands(world) && !threatened && fort == null && !chestFits.barred(name, fitNow);
            turns.put(Hands.Routine.DEPOSITOR, turn(() -> depositor.active() || chestFitter.active(), free -> {
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
                Optional<Position> stockpile = stockpileFor(name);
                if (drawingStone.contains(name)) {
                    depositor.tickDraw(status.position(), stockpile, ItemClass.COBBLESTONE, WALL_BLOCKS_LOAD, free && !threatened);
                } else if (drawsFood(carried, chainOfCommand.stockpile(), status.position(), stockpile) || depositor.drawing(ItemClass.FOOD)) {
                    depositor.tickDraw(status.position(), stockpile, ItemClass.FOOD, Subsistence.RESUPPLY, free && !threatened
                            && drawsFood(carried, chainOfCommand.stockpile(), status.position(), stockpile));
                } else if (metalDraw.isPresent() || depositor.drawing(ItemClass.RAW_IRON) || depositor.drawing(ItemClass.RAW_COPPER)) {
                    ItemClass ore = metalDraw.map(MetalPool.Draw::ore)
                            .orElse(depositor.drawing(ItemClass.RAW_IRON) ? ItemClass.RAW_IRON : ItemClass.RAW_COPPER);
                    depositor.tickDraw(status.position(), stockpile, ore, metalDraw.map(MetalPool.Draw::carry).orElse(inventory.count(ore)),
                            free && !threatened && fort == null && metalDraw.isPresent());
                } else {
                    depositor.tick(status.position(), stockpile, free && (fullLoad || Depositor.surplusFood(inventory) || poolOre)
                            && !threatened && fort == null, metal.stock());
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
                    Miner.Progress cut = miner.tick(status.position(), world, stairs,
                            free && !threatened && armourer.standAt().isEmpty());
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
            turns.put(Hands.Routine.CLEARER, turn(clearer::active, free -> clearer.tick(status.position(), world,
                    clearing.getOrDefault(name, List.of()), free && !threatened && !foodCalls)));
            turns.put(Hands.Routine.FILLER, turn(filler::active, free -> {
                filler.tick(status.position(), world, filling.getOrDefault(name, List.of()), free && !threatened && !foodCalls);
                filler.lookAt().ifPresent(soldier::lookAt);
            }));
            BlockPos wallCentre = wallCentres.getOrDefault(name,
                    BlockPos.containing(status.position().x(), status.position().y(), status.position().z()));
            List<BlockPos> repoint = repointing.getOrDefault(name, List.of());
            turns.put(Hands.Routine.MASON, turn(mason::active, free -> {
                // Its dirt replaced while there's no open cell it can build: live, one cell nobody
                // could reach held a whole wall's stone back.
                if (!repoint.isEmpty() && inventory.count(ItemClass.COBBLESTONE) > 0
                        && (wall.isEmpty() || mason.nothingToBuild() || mason.active() && mason.repointing())) {
                    mason.repoint(status.position(), world, wallCentre, WALL_RADIUS, repoint, free && !threatened && !foodCalls);
                } else {
                    mason.tick(status.position(), world, wallCentre, WALL_RADIUS, wall, free && !threatened && !foodCalls);
                }
            }));
            turns.put(Hands.Routine.LAMPLIGHTER, turn(lamplighter::active,
                    free -> lamplighter.tick(status.position(), world, lights, free && !threatened && !foodCalls)));
            TreeFarm grove = planting.get(name);
            turns.put(Hands.Routine.FORESTER, turn(forester::active,
                    free -> forester.tick(status.position(), world, Optional.ofNullable(grove), free && !threatened && !foodCalls)));
            turns.put(Hands.Routine.FARMER, turn(farmer::active,
                    free -> farmer.tick(status.position(), world, Optional.ofNullable(field), free && !threatened && !foodCalls)));
            // A supply run from the ground work's drill first: so much of a resource, from that box.
            Order.Gather groundRun = groundGather.get(name);
            turns.put(Hands.Routine.GATHERER, turn(gatherer::active, free -> {
                if (groundRun != null) {
                    Box b = groundRun.box();
                    gatherer.tick(status.position(), world, Optional.of(groundRun.resource()),
                            new Position((b.minX() + b.maxX()) / 2.0 + 0.5, (b.minY() + b.maxY()) / 2.0, (b.minZ() + b.maxZ()) / 2.0 + 0.5),
                            free && !inContact && !foodCalls, OptionalInt.of(groundRun.amount()));
                } else {
                    gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                            gather == null ? status.position() : gather.location(), free && !inContact && !foodCalls);
                }
            }));
            turns.put(Hands.Routine.SCAVENGER, turn(scavenger::active,
                    free -> scavenger.tick(status.position(), world, free && !threatened && fort == null && !foodCalls)));
            Optional<Hands.Routine> hands = Hands.tick(turns);
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
                // Hiding or sleeping: the squad in its patrol base, or down its mine for the night.
                // Anything else still that long is worth a look.
                Integer below = nightBelow.get(unitOf(name, definition.squad()));
                // Or holding where its squad leader sent it: waiting there is the order.
                boolean resting = taskOf(unitOf(name, definition.squad()), Objective.Task.PatrolBase.class).isPresent()
                        || planBook.of(name).role() == Role.RIFLEMAN
                        && planBook.of(name).current().filter(d -> d instanceof Directive.GoTo).isPresent()
                        || below != null && status.position().y() < below - MINE_BELOW;
                afterActionReview.commands.sample(squadTask(unitOf(name, definition.squad())), status.order().getClass().getSimpleName(), status.report());
                idleWatch.update(name, status.position(), clockwork.millis(), resting, () -> "report " + status.report()
                        + ", order " + status.order() + ", hands " + hands.map(Enum::name).orElse("free")
                        // What holds routines back with free hands: food first, and the work it was handed.
                        + (foodCalls ? ", food calls" : "") + (threatened ? ", threatened" : "")
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
                            }
                        });
                Optional<String> nearest = world.nearbyHostiles(status.position(), RECORDED_HOSTILE_RADIUS).stream()
                        .min(Comparator.comparingDouble(h -> distance(h.position(), status.position())))
                        .map(h -> String.format(Locale.ROOT, "%s %.1f away",
                                HostileMobRegistry.nameOf(h.typeId()), distance(h.position(), status.position())));
                recorder.record(new FlightRecorder.Sample(clockwork.millis(), status.position(), status.report().toString(),
                        (float) (double) status.condition().map(Condition::health).orElse(-1.0),
                        status.condition().map(Condition::food).orElse(-1),
                        hands.map(Enum::name), nearest));
            }
            // Short of logs for tools it tried and failed to chop wood for: a squadmate may have
            // some to spare (§13).
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
                    // recovery drill sends blocks: live, two soldiers afloat reported the wood they
                    // meant to gather, and nobody came.
                    : stuckInWater ? new Report.Cantco(String.format(Locale.ROOT, "no route from (%d, %d, %d) out of the water",
                            ax, (int) Math.floor(at.y()), az))
                    : hunter.active() ? new Report.Hunting()
                    // A ground-work command is reported as itself, between one cut and the next too: live,
                    // a soldier keeping the camp between cuts reported its lamplighter's CANTCO, raised anew
                    // each cycle. A dirt run from the drill has no gather objective behind it.
                    : groundGather.get(name) instanceof Order.Gather g
                    ? new Report.Working("gather " + g.resource(), Math.max(0, g.amount() - inventory.count(ItemClass.DIRT)))
                    : clearer.active() || clearing.containsKey(name) && clearer.stuck().isEmpty()
                    ? new Report.Working("dig the camp's ground", clearing.getOrDefault(name, List.of()).size())
                    : filler.active() || filling.containsKey(name)
                    ? new Report.Working("fill the camp's ground", filling.getOrDefault(name, List.of()).size())
                    : mason.active() ? new Report.Fortifying()
                    // Out of ways of its own - each of its last tries failed, on whatever it tried -
                    // it asks for help: live, a soldier opened a chest that wasn't there all night.
                    : !clearing.getOrDefault(name, List.of()).isEmpty() && clearer.stuck().isPresent() ? new Report.Cantco(clearer.stuck().get())
                    // Each routine's CANTCO only while its work is the soldier's task: live, soldiers
                    // levelling the camp reported the mason's "none to build" between their cuts.
                    : !clearing.containsKey(name) && !walling.getOrDefault(name, List.of()).isEmpty() && mason.stuck().isPresent()
                    ? new Report.Cantco(mason.stuck().get())
                    : depositor.stuck().isPresent() && !depositor.active() ? new Report.Cantco(depositor.stuck().get())
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
                    : gatherer.active() ? new Report.Gathering(gather.gather().orElseThrow())
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
            session.reportPosition(current.x(), current.y(), current.z(), soldier.yaw(), soldier.pitch(), soldier.onGround());
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
            // A pillar step's block goes in after the server has the soldier's new height - it won't
            // place a block where a body is - and into the world model at once, so the soldier lands
            // on it before the server's block update arrives.
            List<BlockPos> cut = soldier.cutting();
            if (!cut.isEmpty()) {
                stepCutter.tick(world, cut);
            } else {
                stepCutter.stop();
            }
            // Cobblestone stands in for whatever went in, dirt included, until the server's update says which.
            // A torch in the cell is broken first - it goes at a touch, and the server won't put a block
            // where one is: live, a soldier pillaring out of a pocket from a torch's cell jumped in place for good.
            soldier.takePlacement().ifPresent(cell -> {
                if (world.isTorch(cell.x(), cell.y(), cell.z())) {
                    session.startDigging(cell.x(), cell.y(), cell.z());
                    session.finishDigging(cell.x(), cell.y(), cell.z());
                }
                Builder.support(world, cell)
                        .filter(face -> footing.placeStep(cell, face))
                        .ifPresent(face -> chunkCache.setBlock(cell.x(), cell.y(), cell.z(), COBBLESTONE_STATE));
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
        if (!rearmAllowed.getOrDefault(name, false)) {
            rungsWanted.remove(name);
            return Optional.empty();
        }
        // The squad climbs together: nobody works above the lowest rung a squadmate is working toward,
        // so the first up the ladder doesn't dig out the ore the rest need for theirs.
        String squad = soldierDefinitions.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow().squad();
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
        Optional<Recipe> next = Ladder.upTo(inventory, setAside, inReach, canDig,
                upgradeAllowed.getOrDefault(name, false) ? highest : Math.min(highest, Ladder.BASIC_LOAD));
        next.ifPresentOrElse(r -> rungsWanted.put(name, Ladder.rungOf(r)), () -> rungsWanted.remove(name));
        return next;
    }

    /** The rung of the ladder each soldier is working toward, by name, while it is - tick loops write and read. */
    private final Map<String, Integer> rungsWanted = new ConcurrentHashMap<>();

    /**
     * How long the same line from the same sender counts as one hearing: every connection gets a
     * line said aloud within a tick or two of the others.
     */
    private static final long SAME_LINE_NANOS = 2_000_000_000L;
    /** When each line was last heard, by sender and text - every connection's event loop. */
    private final Map<String, Long> lastHeard = new ConcurrentHashMap<>();

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

    private static double distance(Position a, Position b) {
        return Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()) + (a.z() - b.z()) * (a.z() - b.z()));
    }

    /** One routine's turn at the soldier's hands. */
    private static Hands.Turn turn(BooleanSupplier active, Consumer<Boolean> tick) {
        return new Hands.Turn() {
            @Override
            public boolean active() {
                return active.getAsBoolean();
            }

            @Override
            public void tick(boolean free) {
                tick.accept(free);
            }
        };
    }

    /**
     * Fires from {@code protocol}'s watchdog (or any other unexpected close - see {@code
     * PlaySessionListeners.onDisconnect}): stops this soldier's now-dead tick loop and reconnects
     * from scratch via {@link #connect}, which re-seeds a brand new {@link DeterministicSoldier}
     * from the new session's own spawn position. Deliberately a single immediate attempt, not a
     * retry loop with backoff - if the server is actually down, {@code connect}'s own {@code
     * exceptionally} already logs that loudly and stops, per docs/conventions.md's revisit-on-real-pressure
     * tenet this can grow a backoff/retry-limit policy if a real deployment ever needs one.
     */
    private void onDisconnected(SoldierDefinition definition,
                                 AtomicReference<ScheduledExecutorService> activeScheduler, Throwable cause) {
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
