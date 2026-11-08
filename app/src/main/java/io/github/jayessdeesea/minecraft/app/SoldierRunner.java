package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Builder;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.supply.Breacher;
import io.github.jayessdeesea.minecraft.bot.supply.Footing;
import io.github.jayessdeesea.minecraft.bot.supply.Gatherer;
import io.github.jayessdeesea.minecraft.bot.supply.Handover;
import io.github.jayessdeesea.minecraft.bot.supply.Rations;
import io.github.jayessdeesea.minecraft.bot.supply.Torchmaker;
import io.github.jayessdeesea.minecraft.bot.supply.SupplyRun;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Ladder;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.supply.Armourer;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.command.Formations;
import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import io.github.jayessdeesea.minecraft.command.Escort;
import io.github.jayessdeesea.minecraft.command.LinkUp;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.ReactToContact;
import io.github.jayessdeesea.minecraft.command.Resupply;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Report;
import java.util.HashMap;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.protocol.HealthUpdate;
import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
public class SoldierRunner implements CommandLineRunner, org.springframework.beans.factory.DisposableBean {

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
        java.util.Set<String> linkingUp = java.util.Set.of();
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
        io.github.jayessdeesea.minecraft.bot.supply.Staircase stairs;
        /** The soldiers' own reports this cycle, before link-up relabels a replacement. */
        List<SoldierStatus> reported = List.of();
        /** The objective's x and z the wall was laid out for: its key in {@code walls}. */
        String wallKey;
        /** The objective's x and z the stairs were sited for. */
        int[] stairsFor;
        /** Torches left to put down round a SECURE objective, at the last check: -1 before the first. */
        int torchesLeft = -1;
        /** The squad's wall, laid out once round its FORTIFY objective's ground. */
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wall;
        /** The ground its wall is laid out round. */
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos wallCentre;
        /** Wall cells left to fill at the last check: -1 before the first. */
        int wallLeft = -1;
        /** The squad's tree farm, while it has one. */
        io.github.jayessdeesea.minecraft.bot.farm.TreeFarm trees;
        /** The squad's farm, while it has one. */
        io.github.jayessdeesea.minecraft.bot.farm.FarmPlan farm;
        /** Blocks of the laid-out base still to place, at the last check - empty with none laid out. */
        java.util.OptionalInt baseBlocksLeft = java.util.OptionalInt.empty();
        /** The doorway cut to let a torchbearer out of a sealed, dark base - left open until it's back with torches. */
        List<Position> torchDoor;
        /** The base's breaches at the last check - logged when they change. */
        List<Position> breaches;
        java.util.Set<CrossLevel.Transfer> crossLeveling = java.util.Set.of();
        /** The walls and roof of the base the squad holds, kept off-limits to digging. */
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> baseCells = java.util.Set.of();
    }

    private static final long SQUAD_PERIOD_MILLIS = 1000;

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
    private final java.util.Set<ScheduledExecutorService> schedulers = ConcurrentHashMap.newKeySet();
    private final java.util.Set<PlaySession> sessions = ConcurrentHashMap.newKeySet();

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
    public java.util.Set<String> connected() {
        return java.util.Set.copyOf(activeSoldiers.keySet());
    }

    /** Where {@code soldier} is now, while connected. */
    /** Hostiles any soldier can see within {@code radius} of {@code center} - for a live scenario's judge. */
    /** How many blocks of {@code squad}'s wall are still to place, while it has one laid out - for scenario checks. */
    public java.util.OptionalInt wallLeft(String squad) {
        SquadState state = squadStates.get(squad);
        return state == null || state.wall == null || state.wallLeft < 0 ? java.util.OptionalInt.empty()
                : java.util.OptionalInt.of(state.wallLeft);
    }

    public int hostilesNear(Position center, double radius) {
        return world.nearbyHostiles(center, radius).size();
    }

    /** What a connected soldier last reported doing - for a live scenario's judge. */
    public Optional<io.github.jayessdeesea.minecraft.command.Report> report(String soldier) {
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
    private final Map<io.github.jayessdeesea.minecraft.bot.nav.BlockPos, String> furnaces = new ConcurrentHashMap<>();
    /** Logs and stand cells a soldier is working, shared so soldiers pick different trees. */
    private final java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> treeClaims = ConcurrentHashMap.newKeySet();
    /** Each connected soldier's trips to the stockpile, by name. */
    private final Map<String, Depositor> depositors = new ConcurrentHashMap<>();
    /** Each connected soldier's handing of logs to a squadmate, by name. */
    private final Map<String, Handover> handovers = new ConcurrentHashMap<>();
    /** Each connected soldier's building, by name. */
    private final Map<String, Builder> builders = new ConcurrentHashMap<>();
    /** A soldier's part in building its squad's patrol base. */
    private record Fortify(Position stand, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells, boolean allInside,
                           boolean makeTorches, boolean placeTorch) {
    }
    /** What each soldier is building, while its squad builds a patrol base - squad loop writes, tick reads. */
    private final Map<String, Fortify> fortifying = new ConcurrentHashMap<>();
    /** Torches each soldier carries, and whether it could make some - tick writes, squad loop reads. */
    private final Map<String, Integer> torchesCarried = new ConcurrentHashMap<>();
    private final Map<String, Boolean> canMakeTorches = new ConcurrentHashMap<>();
    /** The doorway a soldier is cutting out of its squad's base, and where it stands to - squad loop writes, tick reads. */
    private record Opening(Position stand, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells) {
    }
    private final Map<String, Opening> opening = new ConcurrentHashMap<>();
    /** Logs each soldier is short of for a tool it can't chop wood for - tick writes, squad loop reads; absent when none. */
    private final Map<String, Integer> logsWanted = new ConcurrentHashMap<>();
    /** What each soldier is to hand over, by giver - squad loop writes, tick reads; absent when nothing. */
    private final Map<String, CrossLevel.Transfer> deliveries = new ConcurrentHashMap<>();
    /** Each connected soldier's gathering routine, by name. */
    private final Map<String, Gatherer> gatherers = new ConcurrentHashMap<>();
    /** Each connected soldier's farm work, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Farmer> farmers = new ConcurrentHashMap<>();
    /** Arrows a soldier with a bow makes up to, when it carries flint and feathers. */
    private static final int ARROWS_WANTED = 32;
    /** Each connected soldier's picking up of what lies near, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Scavenger> scavengers = new ConcurrentHashMap<>();
    /** Each connected soldier's wall building, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Mason> masons = new ConcurrentHashMap<>();
    /** The wall cells each soldier's squad still has to fill, while its objective is FORTIFY - squad loop writes, tick reads. */
    private final Map<String, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>> walling = new ConcurrentHashMap<>();
    /** The centre of the wall each soldier's squad builds - squad loop writes, tick reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wallCentres = new ConcurrentHashMap<>();
    /**
     * Wall cells a mason has taken - its own set, apart from the claims every dig skips, which hold
     * the whole wall so nobody quarries it.
     */
    private final java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wallClaims = ConcurrentHashMap.newKeySet();
    /** Each wall's layout, by its centre's x and z - shared by every squad building it. */
    private final Map<String, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>> walls = new ConcurrentHashMap<>();
    /** Each wall's layout on disk: laid out again after a restart, it would go up on top of itself. */
    private final WallStore wallStore;
    /** The camp the squads build up, x and z - null for none. */
    private final int[] camp;
    /** Squad-loop cycles between measures of the foothold: about ten seconds. */
    private static final int FOOTHOLD_EVERY = 10;
    private int footholdTicks;
    /** Steps cut down the deepest staircase so far, and whether one reached its bottom - tick threads write. */
    private final java.util.concurrent.atomic.AtomicInteger stairsCut = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean stairsDone = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * The camp measured against the lodgment's phases (docs/military-doctrine.md §17): what's lit,
     * walled, growing, dug and carried - empty until the camp's ground has loaded.
     */
    private Optional<io.github.jayessdeesea.minecraft.command.Foothold> measureFoothold() {
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
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = campGround;
        List<io.github.jayessdeesea.minecraft.command.Foothold.Standard> standards = new java.util.ArrayList<>();
        int dark = io.github.jayessdeesea.minecraft.bot.supply.Lighting.dark(world, at, SECURE_RADIUS);
        // Torches are made from logs: with none carried and no tree in sight, lighting waits on wood
        // grown first - the lookup done for the general, which otherwise sends squads to SECURE with
        // nothing to make torches from.
        int logs = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(Kit::logs).orElse(0)).sum();
        int torches = torchesCarried.values().stream().mapToInt(Integer::intValue).sum();
        boolean noWood = dark > 0 && torches == 0 && logs < TORCH_LOGS && !chainOfCommand.woodSeen();
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(2, "the camp's ground lit", dark == 0,
                dark + " walkable cells within " + SECURE_RADIUS + " blocks still dark"
                        + (noWood ? "; no logs to make torches from and no trees in sight - grow them first" : ""),
                noWood ? "TREE_FARM" : "SECURE"));
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wall = walls.entrySet().stream()
                .filter(e -> Math.hypot(Integer.parseInt(e.getKey().split(",")[0]) - camp[0], Integer.parseInt(e.getKey().split(",")[1]) - camp[1])
                        <= WALL_RADIUS).map(Map.Entry::getValue).findFirst().orElse(null);
        int wallLeft = wall == null ? -1 : io.github.jayessdeesea.minecraft.bot.supply.Perimeter.open(world, wall).size();
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(2, "the camp walled in", wallLeft == 0,
                wall == null ? "not begun" : wallLeft + " of " + wall.size() + " wall blocks still to place", "FORTIFY"));
        long hungry = everyone.stream().filter(s -> s.condition().map(SoldierRunner::wantsFood).orElse(false)).count();
        Optional<io.github.jayessdeesea.minecraft.bot.farm.FarmPlan> farm = io.github.jayessdeesea.minecraft.bot.farm.FarmPlan.site(world, at,
                CAMP_FARM_RADIUS, CAMP_FARM_VERTICAL);
        long growing = farm.map(f -> f.plot().stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).isPresent()).count()).orElse(0L);
        // Food is buildup - unless the force is starving now: then it comes before the wall, as a
        // unit eats before it improves its position. Live, both squads walled the camp while all
        // eight soldiers went hungry, health falling to 1.
        boolean starving = hungry * 2 > everyone.size();
        int foodPhase = starving ? 2 : 3;
        // Met when the farm grows and the force eats: wheat planted today feeds nobody for days.
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(foodPhase, "food", growing >= FARM_GROWING && !starving,
                // Where: a coordinate written out is one the model copies, and here it should - live,
                // told only a count, it sent a squad to farm 50 blocks off, where the count never sees it.
                (farm.isEmpty() ? "no ground to farm by water near the camp" : growing + " of " + farm.get().plot().size()
                        + " cells of the camp's farm ground, by the water at x=" + farm.get().water().x() + " z=" + farm.get().water().z()
                        + ", growing wheat")
                        + "; " + hungry + " of " + everyone.size() + " soldiers hungry with nothing to eat", "FARM"));
        Optional<io.github.jayessdeesea.minecraft.bot.farm.TreeFarm> grove = io.github.jayessdeesea.minecraft.bot.farm.TreeFarm.site(world, at,
                CAMP_RADIUS);
        long trees = grove.map(g -> g.plot().stream().filter(c -> io.github.jayessdeesea.minecraft.bot.farm.TreeFarm.planted(world, c)).count())
                .orElse(0L);
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(3, "wood that grows back", trees >= TREES_GROWING,
                trees + " saplings or trees planted by the camp", "TREE_FARM"));
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(3, "stone and iron", stairsDone.get(),
                stairsCut.get() == 0 ? "no mine yet" : stairsDone.get() ? "a mine cut down to the iron" : "a mine " + stairsCut.get() + " steps down",
                "MINE"));
        int stoneSword = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.meleeRank("stone_sword");
        long armed = everyone.stream().filter(s -> s.condition().flatMap(c -> c.kit()).flatMap(k -> k.weapon())
                .filter(w -> !w.equals("bow")).map(w -> {
                    try {
                        return io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.meleeRank(w) >= stoneSword;
                    } catch (IllegalArgumentException notMelee) {
                        return false;
                    }
                }).orElse(false)).count();
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(4, "every soldier armed", armed == everyone.size(),
                armed + " of " + everyone.size() + " carry a stone sword or better", "STONE"));
        int armour = everyone.stream().mapToInt(s -> s.condition().flatMap(c -> c.kit()).map(k -> k.armour()).orElse(0)).sum();
        standards.add(new io.github.jayessdeesea.minecraft.command.Foothold.Standard(4, "every soldier armoured",
                armour == everyone.size() * Kit.ARMOUR_SLOTS, armour + " of " + everyone.size() * Kit.ARMOUR_SLOTS + " armour slots filled (copper or iron, from ore)",
                "MINE"));
        return Optional.of(new io.github.jayessdeesea.minecraft.command.Foothold(new Position(at.x(), at.y(), at.z()), List.copyOf(standards)));
    }

    /**
     * The ground round ({@code cx}, {@code cz}): the ground most of it stands on, not whatever's tallest at its centre -
     * where the squads have built hut on hut, a roof would be its ground, the wall laid round a roof
     * and the light measured on one. The typical height of the ground across the camp, then the
     * standing cell nearest the centre at about that height.
     */
    private Optional<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> typicalGround(int cx, int cz, int nearY) {
        int[] camp = {cx, cz};
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> columns = new java.util.ArrayList<>();
        for (int dx = -WALL_RADIUS; dx <= WALL_RADIUS; dx++) {
            for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
                io.github.jayessdeesea.minecraft.bot.supply.Staircase.headAt(world, camp[0] + dx, camp[1] + dz, nearY).ifPresent(columns::add);
            }
        }
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        int typical = columns.stream().mapToInt(io.github.jayessdeesea.minecraft.bot.nav.BlockPos::y).sorted().toArray()[columns.size() / 2];
        return columns.stream().filter(c -> Math.abs(c.y() - typical) <= 1)
                .min(java.util.Comparator.comparingInt(c -> Math.abs(c.x() - camp[0]) + Math.abs(c.z() - camp[1])));
    }

    /** How far round the camp its tree farm is looked for. */
    private static final int CAMP_RADIUS = 24;
    /** How far round the camp its farm is looked for: the water it needs can be a way off. */
    private static final int CAMP_FARM_RADIUS = 48;
    /** And how far up or down: a camp on a hill farms by the water below it. */
    private static final int CAMP_FARM_VERTICAL = 32;
    /** The camp's ground, found once. */
    private io.github.jayessdeesea.minecraft.bot.nav.BlockPos campGround;
    /** Farm cells growing wheat for the camp's food to count as met. */
    private static final int FARM_GROWING = 12;
    /** Saplings or trees for the camp's wood to count as met. */
    private static final int TREES_GROWING = 8;
    /** How far out from a FORTIFY objective its wall stands. */
    static final int WALL_RADIUS = 8;
    /** Fewer blocks than this carried, and a mason goes and digs dirt for the wall. */
    static final int WALL_BLOCKS_LOW = 4;
    /** A mason digging for the wall digs this many before it goes back to building. */
    static final int WALL_BLOCKS_LOAD = 32;
    /** Masons digging a load of dirt for the wall - squad loop only. */
    private final java.util.Set<String> diggingForWall = new java.util.HashSet<>();
    /** How close behind the cutter, across and up the stairs, the rest of a squad mining at night keeps. */
    static final double MINE_FOLLOW = 3;
    /** How often, hands free, a soldier checks it's wearing its best and holding its weapon: the server's answer has time to come back. */
    static final int STAND_TO_TICKS = 40;
    /** Each connected soldier's hunting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Hunter> hunters = new ConcurrentHashMap<>();
    /** Soldiers free to hunt when hungry: by day, out of contact, not walling in a base - squad loop writes, tick reads. */
    private final java.util.Set<String> huntAllowed = ConcurrentHashMap.newKeySet();
    /** Where each soldier looks for game: round its squad's objective, so a run of hunts doesn't carry it off. */
    private final Map<String, Position> huntGrounds = new ConcurrentHashMap<>();
    /** At this food level or below, with no rations, a soldier free to hunts for meat: it has missed a meal or two. */
    static final int HUNT_BELOW_FOOD = 14;

    /**
     * Whether a soldier with no rations needs food: at {@link #HUNT_BELOW_FOOD} or below, or
     * wounded and short of the food it heals at - otherwise a wound taken at food 15 never heals.
     */
    static boolean wantsFood(io.github.jayessdeesea.minecraft.command.Condition condition) {
        return condition.kit().map(k -> k.rations() == 0).orElse(true)
                && (condition.food() <= HUNT_BELOW_FOOD || condition.health() < io.github.jayessdeesea.minecraft.command.Condition.MAX_HEALTH
                && condition.food() < io.github.jayessdeesea.minecraft.command.Condition.REGENERATION_FOOD);
    }
    /** Each connected soldier's tree planting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Forester> foresters = new ConcurrentHashMap<>();
    /** The tree farm each soldier plants, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.farm.TreeFarm> planting = new ConcurrentHashMap<>();
    /** Each connected soldier's staircase cutting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Miner> miners = new ConcurrentHashMap<>();
    /** The day's deaths and idlers, reviewed at each dawn. */
    private final AfterActionReview afterActionReview = new AfterActionReview();
    /** Cutters wanting a tool, sent to their mine's landing to make it - tick writes, squad loop reads. */
    private final java.util.Set<String> atLanding = ConcurrentHashMap.newKeySet();
    /** Ticks to each soldier's next stand-to check. */
    private final Map<String, Integer> standTo = new ConcurrentHashMap<>();
    /** Soldiers stood still by day: an alarm each spell. */
    private final IdleWatch idleWatch = new IdleWatch();
    /** Each soldier's last seconds, read out when it dies. */
    private final Map<String, FlightRecorder> recorders = new ConcurrentHashMap<>();
    /** Deaths since the app started, by the server's death message key: what kills soldiers most. */
    private final Map<String, Integer> casualties = new java.util.concurrent.ConcurrentSkipListMap<>();
    /** The staircase a soldier cuts, for the one soldier of a MINE squad cutting it - squad loop writes, tick reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Staircase> cutting = new ConcurrentHashMap<>();
    /** Each soldier of a MINE squad, which wants a pickaxe for it - squad loop writes, tick reads. */
    private final java.util.Set<String> mining = ConcurrentHashMap.newKeySet();
    /** How the staircase went on the cutter's last tick - tick writes, the report reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress> stairsProgress = new ConcurrentHashMap<>();
    /** Each connected soldier's lighting work, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Lamplighter> lamplighters = new ConcurrentHashMap<>();
    /** The torches each soldier's squad still has to put down, while its objective is SECURE - squad loop writes, tick reads. */
    private final Map<String, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>> lighting = new ConcurrentHashMap<>();
    /** The farm each soldier keeps, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.farm.FarmPlan> farming = new ConcurrentHashMap<>();
    /** What each soldier's squad is gathering, and around where - squad loop writes, tick reads; absent when nothing. */
    private final Map<String, io.github.jayessdeesea.minecraft.command.Objective> gathering = new ConcurrentHashMap<>();
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
    private final java.util.Optional<Position> supplyPoint;

    public SoldierRunner(ChainOfCommand chainOfCommand, OperatorOrders operatorOrders,
                         @Value("${minecraft.supply.point}") String supplyPoint, Roster roster,
                         @Value("${minecraft.yield-to-experiments}") boolean yieldToExperiments,
                         @Value("${minecraft.camp}") String camp,
                         @Value("${minecraft.state-dir}") String stateDir) {
        this.roster = roster;
        this.wallStore = new WallStore(java.nio.file.Path.of(stateDir));
        this.camp = camp.isBlank() ? null : java.util.Arrays.stream(camp.split(",")).mapToInt(v -> Integer.parseInt(v.strip())).toArray();
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
        squad.scheduleAtFixedRate(this::logSquadSitrep, STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
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
            measureFoothold().ifPresent(chainOfCommand::onFoothold);
        }
        chainOfCommand.time().flatMap(t -> afterActionReview.time(t.dark(), chainOfCommand.foothold()))
                .ifPresent(review -> LOG.info("[AAR] {}", review));
        List<Present> present = new java.util.ArrayList<>();
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
            java.util.Set<String> linking = statuses.stream().filter(s -> s.report() instanceof Report.LinkingUp)
                    .map(SoldierStatus::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
            java.util.Set<String> wasLinking = state.linkingUp;
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
            // Surveyed from where the squad is: the soldier at its middle height, so one stranded up a
            // tower - respawned on a block over spawn - doesn't show the squad no ground at all.
            Position pointMan = p.statuses().stream().map(SoldierStatus::position).sorted(java.util.Comparator.comparingDouble(Position::y))
                    .toList().get(p.statuses().size() / 2);
            // Climbing counts when the point man carries cobblestone to pillar with.
            int climbBlocks = p.statuses().get(0).condition().flatMap(c -> c.kit()).map(k -> k.stone()).orElse(0);
            boolean stoneSteps = p.statuses().get(0).condition().flatMap(c -> c.kit()).map(k -> k.pickaxe()).orElse(false);
            SquadState state = squadStates.get(p.squad());
            boolean holdingBase = state.allInside && state.breaches != null && state.breaches.isEmpty();
            return new ChainOfCommand.Sitrep(p.squad(), p.statuses(), () -> TerrainSurveyor.survey(world, pointMan, climbBlocks, stoneSteps,
                    chainOfCommand.objective(p.squad()).map(io.github.jayessdeesea.minecraft.command.Objective::location)), p.principalAt(),
                    () -> io.github.jayessdeesea.minecraft.bot.supply.ResourceSurveyor.survey(world, pointMan), holdingBase,
                    holdingBase ? java.util.OptionalInt.of(0) : state.baseBlocksLeft);
        }).toList());
        for (Present p : present) {
            chainOfCommand.currentOrder(p.squad()).ifPresent(order ->
                    orderSquad(p.squad(), squadStates.get(p.squad()), p.soldiers(), p.statuses(), order, p.principalAt()));
        }
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
                    .map(io.github.jayessdeesea.minecraft.bot.world.TrackedEntity::position).toList();
            orders = ReactToContact.orders(statuses, Escort.intercept(statuses,
                    // No link-up: a replacement's place is its slot in the ring, not beside the point man.
                    Resupply.orders(statuses, Escort.slots(statuses, order, principal),
                            principal, supplyPoint),
                    principal, threats));
        } else {
            orders = ReactToContact.orders(statuses, io.github.jayessdeesea.minecraft.command.Recovery.orders(state.reported,
                    Resupply.orders(statuses, Cohesion.orders(statuses, state.linkUp.orders(statuses, Formations.assign(order, statuses))),
                            order.objective(), supplyPoint)));
        }
        String straggler = principalAt.isPresent() ? null : Cohesion.waitingFor(statuses).map(SoldierStatus::name).orElse(null);
        if (!java.util.Objects.equals(straggler, state.haltedFor)) {
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
        Map<String, Integer> fed = new HashMap<>();
        statuses.forEach(s -> s.condition().filter(c -> c.kit().isPresent() && wantsFood(c))
                .ifPresent(c -> fed.put(s.name(), CrossLevel.RATIONS_GIVEN)));
        // And a spare weapon to one bare-handed: a replacement respawns with nothing. Not to one
        // making its kit: mid-craft its weapon can be on a window's cursor, and it isn't counted -
        // in the squad simulation a sword went round the squad from hand to hand that way.
        Map<String, Integer> disarmed = new HashMap<>();
        statuses.stream().filter(s -> !(s.report() instanceof Report.Rearming))
                .forEach(s -> s.condition().flatMap(Condition::kit).filter(k -> k.weapon().isEmpty())
                        .ifPresent(k -> disarmed.put(s.name(), 1)));
        List<CrossLevel.Transfer> transfers = CrossLevel.pair(statuses, wanted, fed, disarmed);
        java.util.Set<CrossLevel.Transfer> crossLeveling = java.util.Set.copyOf(transfers);
        crossLeveling.stream().filter(t -> !state.crossLeveling.contains(t))
                .forEach(t -> LOG.info("[{} -> {}] CROSS-LEVEL: {} {} to {}", t.giver(), squadName, t.count(),
                        t.item().name().toLowerCase(java.util.Locale.ROOT), t.receiver()));
        state.crossLeveling = crossLeveling;
        java.util.Set<String> receiving = transfers.stream().map(CrossLevel.Transfer::receiver)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        // Patrol base (docs/military-doctrine.md §14): each soldier to its place inside the hut's
        // outline; once all are in, they build its walls and roof around themselves.
        Optional<io.github.jayessdeesea.minecraft.command.Objective> base = squadInContact
                ? Optional.empty() : chainOfCommand.patrolBaseObjective(squadName);
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
            Position middle = statuses.stream().map(SoldierStatus::position).sorted(java.util.Comparator.comparingDouble(Position::y))
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
            state.baseBlocksLeft = java.util.OptionalInt.empty();
        } else {
            baseFootprints.put(squadName, footprint(plan));
            state.baseBlocksLeft = java.util.OptionalInt.of((int) plan.cells().stream()
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
                plan.floor().stream().flatMap(f -> java.util.stream.Stream.of(f, new Position(f.x(), f.y() + 1, f.z())))
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
                .min(java.util.Comparator.comparing(n -> !n.equals(state.torchbearer))).orElse(null);
        state.torchbearer = torchbearer;
        Position workshop = torchbearer == null ? null : workshop(plan).orElse(null);
        // Sealed in and dark, with no torch but the makings of some: a soldier who can make them cuts
        // a doorway toward the workshop and goes out. Once it's open the base isn't sealed, so the
        // torchbearer above takes over; the doorway goes back on the build list when it returns.
        if (plan == null || squadHasTorch || lit) {
            state.torchDoor = null;
        }
        String torchCutter = null;
        if (plan != null && sealedIn && !lit && !squadHasTorch) {
            Optional<String> maker = statuses.stream().map(SoldierStatus::name).filter(n -> canMakeTorches.getOrDefault(n, false))
                    .min(java.util.Comparator.comparing(n -> !n.equals(state.torchbearer)));
            Optional<Position> outside = maker.isEmpty() ? Optional.empty() : workshop(plan);
            if (outside.isPresent()) {
                List<Position> door = plan.doorway(outside.get(), this::walkable);
                if (state.torchDoor == null) {
                    LOG.info("[{}] patrol base sealed DARK - {} cuts a doorway to go and make torches", squadName, maker.get());
                }
                state.torchDoor = door;
                state.torchbearer = maker.get();
                torchCutter = maker.get();
                opening.put(torchCutter, new Opening(plan.insideOf(door), door.stream()
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList()));
            }
        }
        java.util.Set<Position> leftOpen = state.torchDoor == null ? java.util.Set.of() : java.util.Set.copyOf(state.torchDoor);
        // Sealed and dark: the first soldier inside with a torch puts one down where it stands.
        String lighter = plan == null || lit || breaches == null || !breaches.isEmpty() || !allInside ? null
                : statuses.stream().map(SoldierStatus::name).filter(n -> torchesCarried.getOrDefault(n, 0) > 0).findFirst().orElse(null);
        // Moving on from a base: cut a doorway toward the next objective. Not while only paused for
        // contact - the base is still the squad's.
        String opener = null;
        // A soldier shut in a room the squad has no record of - a base built before a restart, say -
        // leaves it the same way as one it remembers: the room is read from the world.
        if (state.builtBase == null && chainOfCommand.objective(squadName).isPresent()) {
            statuses.stream().filter(st -> plan == null || !plan.inside(st.position()))
                    .flatMap(st -> PatrolBase.enclosing(st.position(), c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z()))
                            .map(room -> java.util.Map.entry(st.name(), room)).stream())
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
        } else if (state.builtBase != null && (newSite || chainOfCommand.patrolBaseObjective(squadName).isEmpty())) {
            PatrolBase.Plan built = state.builtBase;
            // Toward where the general is sending the squad: the squad leader's own order can still
            // point inside the hut, since from in there it sees no ground it can reach.
            List<Position> door = built.doorway(chainOfCommand.objective(squadName).orElseThrow().location(), this::walkable);
            boolean open = door.stream().noneMatch(c -> world.isSolid((int) c.x(), (int) c.y(), (int) c.z()));
            Optional<SoldierStatus> inside = statuses.stream().filter(st -> built.inside(st.position())).findFirst();
            if (open || inside.isEmpty()) {
                LOG.info("[{}] {}", squadName, open ? "doorway open - moving out of the patrol base" : "nobody left in the patrol base");
                state.builtBase = newSite ? plan : null;
            } else {
                stillCutting = true;
                opener = inside.get().name();
                opening.put(opener, new Opening(built.insideOf(door), door.stream()
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList()));
            }
        }
        final PatrolBase.Plan building = newSite && stillCutting ? null : plan;
        // A base the squad holds isn't a quarry: its walls go in the claims every dig skips, or a
        // soldier short of wood chops the wall it shelters behind. Re-added every cycle, since a
        // dig releasing its own claims can't take a wall's with them for longer than that.
        // And the camp's wall, once it's laid out: nobody quarries the wall they're inside.
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> held = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(plan, state.builtBase).filter(java.util.Objects::nonNull).flatMap(p -> p.cells().stream())
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())),
                state.wall == null ? java.util.stream.Stream.<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>empty() : state.wall.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        state.baseCells.stream().filter(c -> !held.contains(c)).forEach(treeClaims::remove);
        treeClaims.addAll(held);
        state.baseCells = held;
        boolean baseOrdered = chainOfCommand.patrolBaseObjective(squadName).isPresent();
        // The farm: the plot round the water nearest the general's objective, kept while the
        // objective stays put - a farm is tended for days, not re-sited each cycle.
        io.github.jayessdeesea.minecraft.bot.farm.FarmPlan farm = chainOfCommand.farmObjective(squadName).map(o -> {
            io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(
                    o.location().x(), o.location().y(), o.location().z());
            if (state.farm != null && Math.hypot(state.farm.water().x() - at.x(), state.farm.water().z() - at.z()) <= FARM_SITE_RADIUS) {
                return state.farm;
            }
            io.github.jayessdeesea.minecraft.bot.farm.FarmPlan sited = io.github.jayessdeesea.minecraft.bot.farm.FarmPlan.site(world, at,
                    FARM_SITE_RADIUS, FARM_SITE_RADIUS).orElse(null);
            if (sited == null) {
                LOG.warn("[{}] no ground to farm by water within {} blocks of {}", squadName, FARM_SITE_RADIUS, o.location());
            } else {
                LOG.info("[{}] FARM by the water at {}: {} cells to plant", squadName, sited.water(), sited.plot().size());
            }
            return sited;
        }).orElse(null);
        state.farm = farm;
        // The tree farm: laid out round the objective's ground once, and kept while the objective
        // stays put - what grows in it is the farm working, not a reason to lay it out again.
        io.github.jayessdeesea.minecraft.bot.farm.TreeFarm trees = chainOfCommand.treeFarmObjective(squadName).map(o -> {
            io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.groundAt(world,
                    io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(o.location().x(), o.location().y(), o.location().z()));
            if (state.trees != null && state.trees.centre().x() == at.x() && state.trees.centre().z() == at.z()) {
                return state.trees;
            }
            io.github.jayessdeesea.minecraft.bot.farm.TreeFarm sited = io.github.jayessdeesea.minecraft.bot.farm.TreeFarm.site(world, at,
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
        Optional<io.github.jayessdeesea.minecraft.command.Objective> fortify = chainOfCommand.fortifyObjective(squadName);
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
            String wallKey = (int) Math.floor(asked.x()) + "," + (int) Math.floor(asked.z());
            if (!wallKey.equals(state.wallKey)) {
                // Round the ground most of the camp stands on, not a hut roof at its centre.
                io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = typicalGround((int) Math.floor(asked.x()), (int) Math.floor(asked.z()),
                        (int) Math.floor(asked.y())).orElse(io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.groundAt(world,
                                io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(asked.x(), asked.y(), asked.z())));
                // One layout a wall, whichever squad comes to it first: a squad that lays out a wall
                // another has half built lays it out on top of what's there.
                state.wall = walls.computeIfAbsent(wallKey, key -> wallStore.load(key).orElseGet(() -> {
                    List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> layout = io.github.jayessdeesea.minecraft.bot.supply.Perimeter
                            .layout(world, at, WALL_RADIUS);
                    wallStore.save(key, layout);
                    return layout;
                }));
                state.wallCentre = at;
                state.wallKey = wallKey;
                LOG.info("[{}] FORTIFY: a wall laid out round {}, {} blocks", squadName, at, state.wall.size());
            }
        }
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wallLeft = state.wall == null ? List.of()
                : io.github.jayessdeesea.minecraft.bot.supply.Perimeter.open(world, state.wall);
        if (state.wall != null && wallLeft.size() != state.wallLeft) {
            LOG.info("[{}] FORTIFY: {} of {} wall blocks still to place", squadName, wallLeft.size(), state.wall.size());
            state.wallLeft = wallLeft.size();
        }
        // Mining: the staircase at the objective's x and z, down to its y - kept while the objective
        // stays put, its head where the ground is when first sited.
        io.github.jayessdeesea.minecraft.bot.supply.Staircase stairs = chainOfCommand.mineObjective(squadName).map(o -> {
            int x = (int) Math.floor(o.location().x());
            int z = (int) Math.floor(o.location().z());
            if (state.stairs != null && state.stairsFor != null && state.stairsFor[0] == x && state.stairsFor[1] == z) {
                return state.stairs;
            }
            // Where the stairs can be cut all the way down: the objective's x and z can be beside
            // water - live, the camp's mine stopped at its second step by a pond.
            Optional<io.github.jayessdeesea.minecraft.bot.supply.Staircase> sited = io.github.jayessdeesea.minecraft.bot.supply.Staircase.site(
                    world, x, z, (int) Math.floor(statuses.get(0).position().y()), (int) Math.floor(o.location().y()));
            if (sited.isEmpty()) {
                LOG.warn("[{}] MINE: nowhere within {} blocks of x={} z={} a staircase can be cut down clear of water and lava",
                        squadName, io.github.jayessdeesea.minecraft.bot.supply.Staircase.SITE_RADIUS, x, z);
                state.stairsFor = new int[]{x, z};
                return null;
            }
            LOG.info("[{}] MINE: a staircase down from {} to y={}", squadName, sited.get().head(), sited.get().bottom());
            state.stairsFor = new int[]{x, z};
            // Its stairs and tunnels are claimed: no table, furnace or other digger goes in them.
            treeClaims.addAll(sited.get().walkway());
            return sited.get();
        }).orElse(null);
        state.stairs = stairs;
        // The one who cuts it: the first soldier carrying a pickaxe. Until one does, all make one.
        String cutter = stairs == null || squadInContact ? null : statuses.stream()
                .filter(st -> st.condition().flatMap(c -> c.kit()).map(Kit::pickaxe).orElse(false))
                .map(SoldierStatus::name).findFirst().orElse(null);
        // Securing: the torches still to put down round the objective, worked out again each cycle -
        // every torch put down, by anyone, changes what's left.
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> torchesLeft = chainOfCommand.secureObjective(squadName).map(o -> {
            io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.groundAt(world,
                    io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(o.location().x(), o.location().y(), o.location().z()));
            // Not on a wall's line - one laid out, or the camp's still to be: a torch there holds a
            // cell the mason needs.
            java.util.Set<Long> wallColumns = walls.values().stream().flatMap(List::stream).map(c -> column(c.x(), c.z()))
                    .collect(java.util.stream.Collectors.toSet());
            return io.github.jayessdeesea.minecraft.bot.supply.Lighting.plan(world, at, SECURE_RADIUS).stream()
                    .filter(cell -> !wallColumns.contains(column(cell.x(), cell.z())) && (camp == null
                            || Math.max(Math.abs(cell.x() - camp[0]), Math.abs(cell.z() - camp[1])) != WALL_RADIUS))
                    .toList();
        }).orElse(List.of());
        if (torchesLeft.size() != state.torchesLeft) {
            if (chainOfCommand.secureObjective(squadName).isPresent()) {
                LOG.info("[{}] SECURE: {} torches still to put down", squadName, torchesLeft.size());
            }
            state.torchesLeft = torchesLeft.size();
        }
        int baseBlocks = !baseOrdered ? 0 : state.baseBlocksLeft.orElse(PatrolBase.hasty(new Position(0, 0, 0), statuses.size()).cells().size());
        int stoneShare = (baseBlocks + statuses.size() - 1) / statuses.size();
        for (int i = 0; i < statuses.size(); i++) {
            String soldierName = statuses.get(i).name();
            transfers.stream().filter(t -> t.giver().equals(soldierName)).findFirst()
                    .ifPresentOrElse(t -> deliveries.put(soldierName, t), () -> deliveries.remove(soldierName));
            rearmAllowed.put(soldierName, order.rearm() && !squadInContact);
            upgradeAllowed.put(soldierName, order.rearm() && !squadInContact && !baseOrdered);
            stoneSetAside.put(soldierName, stoneShare);
            Optional<io.github.jayessdeesea.minecraft.command.Objective> gather = squadInContact
                    ? Optional.empty() : chainOfCommand.gatherObjective(squadName);
            // Nothing left round the general's point: to where the squad saw some, while that order
            // stands - live, a squad sent to gather at a camp long since cleared stood reporting CANTCO.
            int nth = i;
            gather = gather.map(ordered -> {
                Map.Entry<io.github.jayessdeesea.minecraft.command.Objective, io.github.jayessdeesea.minecraft.command.Objective> sent =
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
                io.github.jayessdeesea.minecraft.command.Objective there = new io.github.jayessdeesea.minecraft.command.Objective(
                        ordered.mission(), elsewhere, ordered.intent(), ordered.task());
                LOG.info("[{}] {} finds nothing left to gather at {} - goes where the squad saw some, {}", squadName, soldierName,
                        ordered.location(), elsewhere);
                gatherSent.put(soldierName, Map.entry(ordered, there));
                return there;
            });
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            if (farm != null && !squadInContact) {
                farming.put(soldierName, farm);
            } else {
                farming.remove(soldierName);
            }
            if (!wallLeft.isEmpty() && !squadInContact) {
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
                if (bricks != null && diggingForWall.contains(soldierName)) {
                    // Nothing to build with: dirt, dug outside the wall so the ground inside stays whole -
                    // each mason on its own side, so one pit isn't dug out under all of them.
                    int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    int[] side = sides[i % sides.length];
                    gathering.put(soldierName, new io.github.jayessdeesea.minecraft.command.Objective("Dig dirt for the wall",
                            new Position(state.wallCentre.x() + side[0] * (WALL_RADIUS + SOIL_OFFSET), state.wallCentre.y(),
                                    state.wallCentre.z() + side[1] * (WALL_RADIUS + SOIL_OFFSET)),
                            "Blocks to wall the camp in",
                            new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(io.github.jayessdeesea.minecraft.command.Resource.SOIL)));
                }
            } else {
                walling.remove(soldierName);
            }
            boolean night = chainOfCommand.time().map(io.github.jayessdeesea.minecraft.command.TimeOfDay::dark).orElse(false);
            if (!night && !squadInContact && building == null) {
                huntAllowed.add(soldierName);
                huntGrounds.put(soldierName, chainOfCommand.objective(squadName).map(io.github.jayessdeesea.minecraft.command.Objective::location)
                        .orElse(statuses.get(i).position()));
            } else {
                huntAllowed.remove(soldierName);
            }
            if (trees != null && !squadInContact) {
                planting.put(soldierName, trees);
            } else {
                planting.remove(soldierName);
            }
            if (!torchesLeft.isEmpty() && !squadInContact) {
                lighting.put(soldierName, torchesLeft);
                // Out of torches and short of their makings: it gathers them near the objective -
                // logs, then cobblestone for the furnace - rather than reporting the shortage forever.
                Kit makings = statuses.get(i).condition().flatMap(c -> c.kit()).orElse(null);
                if (makings != null && torchesCarried.getOrDefault(soldierName, 0) == 0) {
                    io.github.jayessdeesea.minecraft.command.Objective secure = chainOfCommand.secureObjective(squadName).orElseThrow();
                    Optional<io.github.jayessdeesea.minecraft.command.Resource> missing = makings.logs() < TORCH_LOGS
                            ? Optional.of(io.github.jayessdeesea.minecraft.command.Resource.WOOD)
                            : makings.stone() < TORCH_STONE && !furnaces.containsValue(soldierName)
                            ? Optional.of(io.github.jayessdeesea.minecraft.command.Resource.STONE) : Optional.empty();
                    // Where the squad last saw some: often not within a gatherer's reach of the camp - stone
                    // on level ground by the water lies under the soil.
                    missing.ifPresent(r -> gathering.put(soldierName, new io.github.jayessdeesea.minecraft.command.Objective(
                            "Gather the makings of torches", seenFor(squadName, nth, r, secure.location()), secure.intent(),
                            new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(r))));
                }
            } else {
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
                // The cutter stands where the stairs need it; the rest hold the top, clear of the shaft -
                // by day. By night they go down behind it: the top is the dark, and down the stairs
                // nothing spawns within 24 blocks of them.
                boolean dark = chainOfCommand.time().map(io.github.jayessdeesea.minecraft.command.TimeOfDay::dark).orElse(false);
                Position cutterAt = cutter == null ? centre(stairs.head())
                        : atLanding.contains(cutter) ? centre(stairs.landingStand())
                        : miners.get(cutter).standAt().orElse(centre(stairs.head()));
                boolean isCutter = soldierName.equals(cutter);
                Position place = isCutter ? cutterAt
                        : dark ? cutterAt
                        : new Position(stairs.head().x() - 1.5 + i, stairs.head().y(), stairs.head().z() + 3.5);
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(),
                        isCutter ? 0.3 : dark ? MINE_FOLLOW : 1.5, isCutter ? 0.5 : dark ? MINE_FOLLOW : 4, Risk.LOW));
                continue;
            }
            Order given = orders.get(i);
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
                gathering.put(soldierName, new io.github.jayessdeesea.minecraft.command.Objective("Dig dirt for the patrol base",
                        dirtPlace, "Blocks enough to wall the squad in",
                        new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(io.github.jayessdeesea.minecraft.command.Resource.SOIL)));
                fortifying.remove(soldierName);
                Position dig = gatherers.get(soldierName).standAt().orElse(dirtPlace);
                byName.get(soldierName).receiveOrder(new Order.MoveTo(dig.x(), dig.y(), dig.z(), 0.3, 2, Risk.LOW));
                continue;
            }
            if (building != null) {
                if (!soldierName.equals(opener)) {
                    opening.remove(soldierName);
                }
                List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells = new java.util.ArrayList<>(
                        java.util.stream.Stream.concat(building.cells().stream(), breaches.stream().filter(b -> !building.cells().contains(b)))
                        .filter(c -> !leftOpen.contains(c))
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList());
                // Each soldier starts at a different point round the walls, so they don't all go for the same block.
                java.util.Collections.rotate(cells, -i * cells.size() / statuses.size());
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
            Optional<Position> stand = Optional.ofNullable(hunters.get(soldierName)).flatMap(io.github.jayessdeesea.minecraft.bot.supply.Hunter::standAt);
            if (stand.isEmpty() && armourer != null) {
                stand = armourer.standAt();
            }
            if (stand.isEmpty() && depositors.containsKey(soldierName)) {
                stand = depositors.get(soldierName).standAt();
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
     * {@code task}, failing loudly: a periodic task that throws is cancelled by its executor with
     * nothing said - a soldier frozen mid-stride, its last report standing. Logged with the stack
     * trace, then rethrown, so the task still ends as it would.
     */
    private static Runnable loudly(String what, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                LOG.error("{} failed and has stopped: {}", what, e.toString(), e);
                throw e;
            }
        };
    }

    /**
     * Where the squad's {@code soldier}th soldier goes for {@code resource}: one of the places the
     * squad last saw some, not much further from {@code from} than the nearest - each soldier, and
     * each squad, to a different one where there are several, so nobody works in a crowd - or
     * {@code from} itself if it saw none.
     */
    private Position seenFor(String squad, int soldier, io.github.jayessdeesea.minecraft.command.Resource resource, Position from) {
        java.util.function.ToDoubleFunction<Position> away = at -> Math.hypot(at.x() - from.x(), at.z() - from.z());
        List<Position> seen = chainOfCommand.resourcesSeen(squad).stream().filter(site -> site.resource() == resource)
                .map(io.github.jayessdeesea.minecraft.command.ResourceSite::location)
                .sorted(java.util.Comparator.comparingDouble(away)).toList();
        if (seen.isEmpty()) {
            return from;
        }
        double nearest = away.applyAsDouble(seen.getFirst());
        List<Position> near = seen.stream().filter(at -> away.applyAsDouble(at) <= nearest + SPREAD_BEYOND_NEAREST).toList();
        return near.get(Math.floorMod(soldier + squad.hashCode(), near.size()));
    }

    /** Each soldier sent from a cleared gather objective to where its squad saw more: the order, and where it went instead. */
    private final Map<String, Map.Entry<io.github.jayessdeesea.minecraft.command.Objective, io.github.jayessdeesea.minecraft.command.Objective>>
            gatherSent = new ConcurrentHashMap<>();
    /** Gatherers reach this far round their objective: a site seen within it is no other place. */
    private static final double GATHER_ELSEWHERE = 16;

    /** How much further than the nearest a place a resource was seen may be, and still be shared out to. */
    private static final double SPREAD_BEYOND_NEAREST = 24;

    private static Position centre(io.github.jayessdeesea.minecraft.bot.nav.BlockPos cell) {
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
     * Where a torchbearer works: a standing spot a few blocks out from the hut's walls, so the table
     * and furnace it puts down beside it land clear of the hut and everyone's places.
     */
    private Optional<Position> workshop(PatrolBase.Plan plan) {
        double minX = plan.floor().stream().mapToDouble(Position::x).min().orElseThrow();
        double maxX = plan.floor().stream().mapToDouble(Position::x).max().orElseThrow();
        double minZ = plan.floor().stream().mapToDouble(Position::z).min().orElseThrow();
        double maxZ = plan.floor().stream().mapToDouble(Position::z).max().orElseThrow();
        int y = (int) plan.floor().getFirst().y();
        for (int out = 4; out <= 6; out++) {
            for (double[] c : new double[][]{{maxX + out, minZ}, {minX - out, minZ}, {minX, maxZ + out}, {minX, minZ - out}}) {
                for (int dy = 0; dy <= 2; dy++) {
                    for (int cy : new int[]{y + dy, y - dy}) {
                        int cx = (int) c[0];
                        int cz = (int) c[1];
                        if (world.isSolid(cx, cy - 1, cz) && !world.isSolid(cx, cy, cz) && !world.isSolid(cx, cy + 1, cz)
                                && !world.isHazard(cx, cy, cz)) {
                            return Optional.of(new Position(cx + 0.5, cy, cz + 0.5));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
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
    private final Map<String, java.util.Set<Long>> baseFootprints = new ConcurrentHashMap<>();

    private static java.util.Set<Long> footprint(PatrolBase.Plan plan) {
        return java.util.stream.Stream.concat(plan.floor().stream(), plan.cells().stream())
                .map(c -> column((int) Math.floor(c.x()), (int) Math.floor(c.z())))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> reachable = io.github.jayessdeesea.minecraft.bot.nav.Pathfinder.reachable(
                world, io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.groundAt(world,
                        io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(from.x(), from.y(), from.z())),
                (int) Math.ceil(Math.hypot(asked.x() - from.x(), asked.z() - from.z())) + SITE_SEARCH + 2, SITE_REACH_BUDGET);
        // Shut in a hut, the squad reaches next to nothing: it'll cut its way out, so any site will do.
        boolean shutIn = reachable.size() < SHUT_IN_CELLS;
        java.util.Set<Long> taken = new java.util.HashSet<>();
        baseFootprints.forEach((other, columns) -> {
            if (!other.equals(squadName)) {
                taken.addAll(columns);
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
                                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) cell.x(), (int) cell.y(), (int) cell.z())))) && hut.floor().stream().allMatch(cell -> {
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
                .map(d -> activeSoldiers.get(d.name())).filter(java.util.Objects::nonNull).toList();
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
                update -> chunkCache.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), name),
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
                    .map(cell -> java.util.OptionalInt.of(cell.y())).orElse(java.util.OptionalInt.empty()));
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
        io.github.jayessdeesea.minecraft.bot.supply.Farmer farmer = new io.github.jayessdeesea.minecraft.bot.supply.Farmer(name, session,
                inventory, treeClaims);
        farmers.put(name, farmer);
        io.github.jayessdeesea.minecraft.bot.supply.Forester forester = new io.github.jayessdeesea.minecraft.bot.supply.Forester(name, session,
                inventory, treeClaims);
        foresters.put(name, forester);
        Depositor depositor = new Depositor(name, session, inventory, chainOfCommand::onStockpile);
        depositors.put(name, depositor);
        depositorRef.set(depositor);
        Handover handover = new Handover(name, session, inventory);
        handovers.put(name, handover);
        Builder builder = new Builder(name, session, inventory);
        builders.put(name, builder);
        Rations rations = new Rations(name, session, inventory);
        Breacher doorway = new Breacher(name, session, inventory);
        // Cutting steps is part of moving, like a pillar step's block: no routine has the hands for it.
        Breacher stepCutter = new Breacher(name, session, inventory);
        Footing footing = new Footing(session, inventory);
        // What the server was last told about sprinting: a new connection starts walking.
        java.util.concurrent.atomic.AtomicBoolean sprintingSent = new java.util.concurrent.atomic.AtomicBoolean();
        // Likewise its shield: a new connection starts with it lowered.
        java.util.concurrent.atomic.AtomicBoolean shieldSent = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean drawingSent = new java.util.concurrent.atomic.AtomicBoolean();
        io.github.jayessdeesea.minecraft.bot.supply.Quiver quiver = new io.github.jayessdeesea.minecraft.bot.supply.Quiver(session, inventory);
        armourerRef.set(armourer);
        Torchmaker torchmaker = new Torchmaker(name, session, inventory, furnaces, treeClaims);
        java.util.concurrent.atomic.AtomicInteger ladderTicks = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<Optional<Recipe>> ladderStep = new AtomicReference<>(Optional.empty());
        // Ticks while told to light the base: one torch tried a second, until the light check sees it.
        java.util.concurrent.atomic.AtomicInteger torchTicks = new java.util.concurrent.atomic.AtomicInteger();
        torchmakerRef.set(torchmaker);
        io.github.jayessdeesea.minecraft.bot.supply.Lamplighter lamplighter = new io.github.jayessdeesea.minecraft.bot.supply.Lamplighter(
                name, inventory, footing, treeClaims);
        lamplighters.put(name, lamplighter);
        io.github.jayessdeesea.minecraft.bot.supply.Hunter hunter = new io.github.jayessdeesea.minecraft.bot.supply.Hunter(name);
        hunters.put(name, hunter);
        io.github.jayessdeesea.minecraft.bot.supply.Mason mason = new io.github.jayessdeesea.minecraft.bot.supply.Mason(name, inventory, footing,
                wallClaims);
        masons.put(name, mason);
        io.github.jayessdeesea.minecraft.bot.supply.Scavenger scavenger = new io.github.jayessdeesea.minecraft.bot.supply.Scavenger(name);
        scavengers.put(name, scavenger);
        io.github.jayessdeesea.minecraft.bot.supply.Miner miner = new io.github.jayessdeesea.minecraft.bot.supply.Miner(name, session, inventory);
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
            boolean threatened = inContact || !world.nearbyHostiles(status.position(), REARM_CLEAR_RADIUS).isEmpty();
            // Flint for arrows, for a soldier with a bow and few arrows, when nothing else is to be gathered.
            io.github.jayessdeesea.minecraft.command.Objective gather = gathering.get(name) != null || !huntAllowed.contains(name)
                    || inventory.slotOfNamed("bow") < 0 || inventory.count(ItemClass.ARROWS) >= ARROWS_WANTED
                    || inventory.count(ItemClass.FLINT) > 0 ? gathering.get(name)
                    : new io.github.jayessdeesea.minecraft.command.Objective("Dig gravel for flint", huntGrounds.getOrDefault(name,
                    status.position()), "Arrows for the bow", new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(
                    io.github.jayessdeesea.minecraft.command.Resource.FLINT));
            boolean needsPickaxe = (gather != null && gather.gather().equals(Optional.of(io.github.jayessdeesea.minecraft.command.Resource.STONE))
                    || mining.contains(name))
                    && inventory.slotOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.PICKAXE) < 0;
            io.github.jayessdeesea.minecraft.bot.supply.Staircase stairs = cutting.get(name);
            // The next rung of the equipment ladder, worked out once a second (it looks for ore to
            // dig): a weapon if bare-handed and allowed to rearm, more only if allowed to upgrade.
            // Not while the armourer is at the last one: its own claim on the ore it's digging hides
            // that ore from the look for more, and the rung would read as out of reach mid-job.
            if (ladderTicks.getAndIncrement() % 20 == 0 && !armourer.active()) {
                ladderStep.set(nextRung(name, inventory, status.position()));
            }
            // The ladder's next rung, if allowed; a pickaxe, if sent for stone without one - both at one table.
            java.util.Set<Recipe> tools = new java.util.LinkedHashSet<>();
            Optional<Recipe> rung = ladderStep.get();
            rung.ifPresent(tools::add);
            if (needsPickaxe) {
                tools.add(ORDER_PICKAXE);
            }
            // The cutter's next tool before the ladder's next weapon: iron ore drops nothing to a
            // wooden pickaxe, and the stairs give the cobblestone for a stone one.
            if (stairs != null && Ladder.bestPickaxe(inventory) < 1 && inventory.count(ItemClass.COBBLESTONE) >= 3) {
                tools.add(Recipe.STONE_PICKAXE);
            }
            // On the stairs there's no room for a table: the cutter makes what it wants at the landing,
            // once it's cut - the squad loop sends it there, and the stairs wait meanwhile.
            if (stairs != null && !tools.isEmpty() && stairs.landingCut(world)) {
                atLanding.add(name);
            } else {
                atLanding.remove(name);
            }
            // A farmer's hoe, and its wheat baked into bread three at a time.
            io.github.jayessdeesea.minecraft.bot.farm.FarmPlan field = farming.get(name);
            if (field != null && inventory.count(ItemClass.HOE) == 0) {
                tools.add(Recipe.WOODEN_HOE);
            }
            if (field != null && inventory.count(ItemClass.WHEAT) >= 3) {
                tools.add(Recipe.BREAD);
            }
            // Arrows from the flint and feathers it has picked up, while it has a bow to shoot them.
            if (inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.FLINT) > 0 && inventory.count(ItemClass.FEATHER) > 0
                    && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED) {
                tools.add(Recipe.ARROWS);
            }
            boolean rearming = rung.filter(r -> bareHanded && r.makes() == io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.WEAPON)
                    .isPresent();
            Fortify fort = fortifying.get(name);
            Opening exit = opening.get(name);
            torchesCarried.put(name, inventory.count(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.TORCH));
            // A run under way has spent what canMake looks for - its furnace is in the world now.
            canMakeTorches.put(name, torchmaker.active() || Torchmaker.canMake(inventory));
            boolean atWorkshop = fort != null && fort.makeTorches()
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.6;
            if (fort != null && fort.placeTorch() && !torchmaker.active() && torchTicks.incrementAndGet() % 20 == 0) {
                io.github.jayessdeesea.minecraft.bot.nav.BlockPos feet = io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(
                        status.position().x(), status.position().y() + 0.01, status.position().z());
                if (footing.place(feet, io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.TORCH)) {
                    LOG.info("{} lights the patrol base with a torch at {}", name, feet);
                }
            }
            // Pre-combat checks come first: a bare-handed soldier waiting on its squad's base - not
            // yet building, not the torchbearer - may make its sword meanwhile.
            boolean waiting = fort != null && !fort.allInside() && !fort.makeTorches();
            java.util.Set<Recipe> craftable = fort == null ? tools
                    : waiting && rearming ? java.util.Set.of(rung.orElseThrow())
                    : java.util.Set.of();
            Kit carried = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean fullLoad = carried != null && (carried.logs() >= Gatherer.CARRY_LOGS || carried.stone() >= Gatherer.CARRY_STONE);
            CrossLevel.Transfer giving = deliveries.get(name);
            boolean atBuildPlace = fort != null && fort.allInside() && !inContact
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.2;
            AtomicReference<Builder.Progress> built = new AtomicReference<>();
            // Who has the soldier's hands this tick: each routine says whether it's under way and
            // what it would do if free; Hands decides who's free, in its one priority order.
            Map<Hands.Routine, Hands.Turn> turns = new java.util.EnumMap<>(Hands.Routine.class);
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
            List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> lights = lighting.getOrDefault(name, List.of());
            boolean torchesWanted = !lights.isEmpty() && inventory.count(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.TORCH) == 0;
            turns.put(Hands.Routine.TORCHMAKER, turn(torchmaker::active,
                    free -> torchmaker.tick(status.position(), world, !free ? 0 : atWorkshop ? Torchmaker.TORCHES
                            : torchesWanted ? Math.min(LIGHTING_TORCHES, (lights.size() + 3) / 4 * 4) : 0, threatened)));
            turns.put(Hands.Routine.RATIONS, turn(rations::active,
                    free -> rations.tick(status.condition().map(c -> c.food()).orElse(Condition.MAX_FOOD), free && !threatened,
                            soldier.yaw(), soldier.pitch())));
            Kit eating = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean hungry = status.condition().filter(c -> c.kit().isPresent()).map(SoldierRunner::wantsFood).orElse(false);
            // Feathers for arrows, for a soldier with a bow and few arrows: a chicken, hunted like any game.
            boolean wantsFeathers = inventory.slotOfNamed("bow") >= 0 && inventory.count(ItemClass.ARROWS) < ARROWS_WANTED
                    && inventory.count(ItemClass.FEATHER) == 0;
            java.util.function.IntPredicate quarry = hungry ? kind -> true
                    : kind -> kind == io.github.jayessdeesea.minecraft.bot.world.EntityTracker.CHICKEN_TYPE_ID;
            boolean huntWanted = (hungry || wantsFeathers) && huntAllowed.contains(name) && !inContact;
            // Food first: the day's work - gathering, lighting, walling, planting - gives the hands
            // up when there's game in sight to eat, and the hunter takes them next tick.
            Position huntGround = huntGrounds.getOrDefault(name, status.position());
            int food = status.condition().map(Condition::food).orElse(Condition.MAX_FOOD);
            // And a meal carried comes before either: live, a soldier on a stone run back to back sat at
            // food 0 with two rations in its pack.
            boolean foodCalls = huntWanted && !hunter.active()
                    && io.github.jayessdeesea.minecraft.bot.supply.Hunter.gameInSight(world, huntGround, food, quarry)
                    || !rations.active() && !threatened && food < Condition.REGENERATION_FOOD && inventory.slotOf(ItemClass.FOOD) >= 0;
            turns.put(Hands.Routine.HUNTER, turn(hunter::active, free -> hunter.tick(status.position(), huntGround,
                    io.github.jayessdeesea.minecraft.bot.supply.Hunter.radius(food), quarry, world,
                    free && huntWanted,
                    io.github.jayessdeesea.minecraft.bot.world.WeaponStats.melee(eating == null ? Optional.empty() : eating.weapon()).ticksToCharge())
                    .ifPresent(target -> {
                        LOG.info("{} strikes the animal it hunts, entity {}", name, target);
                        session.attack(target);
                    })));
            turns.put(Hands.Routine.ARMOURER, turn(armourer::active,
                    free -> armourer.tick(status.position(), world, free ? craftable : java.util.Set.of(), threatened)));
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
            turns.put(Hands.Routine.DEPOSITOR, turn(depositor::active,
                    free -> depositor.tick(status.position(), supplyPoint, free && fullLoad && !threatened && fort == null)));
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
                    io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress cut = miner.tick(status.position(), world, stairs,
                            free && !threatened && !atLanding.contains(name));
                    stairsProgress.put(name, cut);
                    if (cut instanceof io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress.Working(int step, int steps)) {
                        stairsCut.accumulateAndGet(Math.min(step, stairs.steps()), Math::max);
                        if (step >= stairs.steps()) {
                            stairsDone.set(true); // down to the iron: the tunnels are the mining
                        }
                    } else if (cut instanceof io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress.Done) {
                        stairsDone.set(true);
                    }
                }
            }));
            List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> wall = walling.getOrDefault(name, List.of());
            turns.put(Hands.Routine.MASON, turn(mason::active, free -> mason.tick(status.position(), world,
                    wallCentres.getOrDefault(name, io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(status.position().x(),
                            status.position().y(), status.position().z())), WALL_RADIUS, wall, free && !threatened && !foodCalls)));
            turns.put(Hands.Routine.LAMPLIGHTER, turn(lamplighter::active,
                    free -> lamplighter.tick(status.position(), world, lights, free && !threatened && !foodCalls)));
            io.github.jayessdeesea.minecraft.bot.farm.TreeFarm grove = planting.get(name);
            turns.put(Hands.Routine.FORESTER, turn(forester::active,
                    free -> forester.tick(status.position(), world, Optional.ofNullable(grove), free && !threatened && !foodCalls)));
            turns.put(Hands.Routine.FARMER, turn(farmer::active,
                    free -> farmer.tick(status.position(), world, Optional.ofNullable(field), free && !threatened && !foodCalls)));
            turns.put(Hands.Routine.GATHERER, turn(gatherer::active,
                    free -> gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                            gather == null ? status.position() : gather.location(), free && !inContact && !foodCalls)));
            turns.put(Hands.Routine.SCAVENGER, turn(scavenger::active,
                    free -> scavenger.tick(status.position(), world, free && !threatened && fort == null && !foodCalls)));
            Optional<Hands.Routine> hands = Hands.tick(turns);
            // Stand-to: hands free, the soldier wears the best armour it carries and holds its best
            // weapon - not the seeds or the dirt its last job left in its hand.
            if (hands.isEmpty() && standTo.merge(name, -1, Integer::sum) <= 0) {
                standTo.put(name, STAND_TO_TICKS);
                synchronized (inventory) {
                    io.github.jayessdeesea.minecraft.bot.supply.Quartermaster.equip(inventory)
                            .forEach(step -> io.github.jayessdeesea.minecraft.bot.supply.Quartermaster.perform(step, session, inventory));
                }
            }
            FlightRecorder recorder = recorders.computeIfAbsent(name, n -> new FlightRecorder());
            if (recorder.due()) {
                boolean day = chainOfCommand.time().map(t -> !t.dark()).orElse(false);
                idleWatch.update(name, status.position(), clockwork.millis(), day, () -> "report " + status.report()
                        + ", order " + status.order() + ", hands " + hands.map(Enum::name).orElse("free")
                        + ", squad's task " + chainOfCommand.objective(definition.squad())
                        .map(o -> OrderTrace.taskName(o.task()) + " at " + o.location()).orElse("none"))
                        .ifPresent(line -> {
                            LOG.warn("[Idle] {}", line);
                            if (line.contains("stationary")) {
                                afterActionReview.idle(name);
                            }
                        });
                Optional<String> nearest = world.nearbyHostiles(status.position(), RECORDED_HOSTILE_RADIUS).stream()
                        .min(java.util.Comparator.comparingDouble(h -> distance(h.position(), status.position())))
                        .map(h -> String.format(java.util.Locale.ROOT, "%s %.1f away",
                                io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry.nameOf(h.typeId()), distance(h.position(), status.position())));
                recorder.record(new FlightRecorder.Sample(clockwork.millis(), status.position(), status.report().toString(),
                        (float) (double) status.condition().map(io.github.jayessdeesea.minecraft.command.Condition::health).orElse(-1.0),
                        status.condition().map(io.github.jayessdeesea.minecraft.command.Condition::food).orElse(-1),
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
                    : stairs != null && stairsProgress.get(name) instanceof io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress.Working(int step, int steps)
                    ? new Report.Mining(step + 1, steps)
                    : stairs != null && stairsProgress.get(name) instanceof io.github.jayessdeesea.minecraft.bot.supply.Miner.Progress.Stopped(String why)
                    ? new Report.Cantco("can't cut the staircase further: " + why)
                    : hunter.active() ? new Report.Hunting()
                    : mason.active() ? new Report.Fortifying()
                    : forester.active() ? new Report.Planting()
                    : grove != null && forester.failure().isPresent() && !io.github.jayessdeesea.minecraft.bot.supply.Forester.planted(world, grove)
                    ? new Report.Cantco("can't plant the tree farm: " + forester.failure().get())
                    : lamplighter.active() || torchmaker.active() && torchesWanted ? new Report.Securing()
                    : !lights.isEmpty() && torchesWanted && torchmaker.failure().isPresent()
                    ? new Report.Cantco("can't make torches to light the ground: " + torchmaker.failure().get())
                    // Gone for the makings, between one dig and the next: still at it, not unable.
                    : !lights.isEmpty() && torchesWanted && gather != null && gather.gather().isPresent() && !gatherer.exhausted()
                    ? new Report.Gathering(gather.gather().get())
                    // Nothing to make them from, and nothing said: the crafter doesn't start what it can't finish.
                    : !lights.isEmpty() && torchesWanted && !torchmaker.active() && !gatherer.active() && !armourer.active()
                    ? new Report.Cantco("no torches to light the ground with, and short of "
                            + Torchmaker.shortOf(inventory, LIGHTING_TORCHES) + " to make them")
                    : farmer.active() || armourer.active() && armourer.making().stream()
                            .allMatch(r -> r == Recipe.WOODEN_HOE || r == Recipe.BREAD) ? new Report.Farming()
                    : armourer.active()
                    ? (armourer.making().equals(java.util.Set.of(ORDER_PICKAXE))
                            ? new Report.Gathering(io.github.jayessdeesea.minecraft.command.Resource.STONE) : new Report.Rearming())
                    : depositor.active() ? new Report.Gathering(carried != null && carried.stone() >= Gatherer.CARRY_STONE
                            ? io.github.jayessdeesea.minecraft.command.Resource.STONE : io.github.jayessdeesea.minecraft.command.Resource.WOOD)
                    : handover.active() ? handover.report().orElseThrow()
                    : gatherer.active() ? new Report.Gathering(gather.gather().orElseThrow())
                    : gather != null && gatherer.exhausted()
                    ? new Report.Cantco("nothing left to gather within 16 blocks of the objective")
                    // Only a tool the order needs makes its failure the order's: a pickaxe to quarry with.
                    // A sword is the soldier's own pre-combat check - going without shows in the LOGSTAT
                    // as bare-handed, not as CANTCO of the mission.
                    : needsPickaxe && armourer.failure().isPresent() ? new Report.Cantco(armourer.failure().get()) : null);
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
            List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cut = soldier.cutting();
            if (!cut.isEmpty()) {
                stepCutter.tick(world, cut);
            } else {
                stepCutter.stop();
            }
            // Cobblestone stands in for whatever went in, dirt included, until the server's update says which.
            soldier.takePlacement().ifPresent(cell -> io.github.jayessdeesea.minecraft.bot.supply.Builder.support(world, cell)
                    .filter(face -> footing.placeStep(cell, face))
                    .ifPresent(face -> chunkCache.setBlock(cell.x(), cell.y(), cell.z(), COBBLESTONE_STATE)));
            footing.tick();
            session.endTick();
        }), 0, TICK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> LOG.debug("{} status: {} - plan: {} - session open: {}", name, soldier.status(), soldier.lastPlan(), session.isOpen()),
                STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

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
                .map(d -> rungsWanted.get(d.name())).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).min().orElse(Ladder.RUNGS - 1);
        Map<ItemClass, Integer> setAside = Map.of(ItemClass.COBBLESTONE, stoneSetAside.getOrDefault(name, 0));
        // A station a walk away counts: the armourer goes to it rather than make another.
        java.util.Set<Workstation> inReach = armourers.get(name).stationsNear(world, at);
        java.util.function.Predicate<ItemClass> canDig = material ->
                setAside.getOrDefault(material, 0) == 0 && Armourer.canDig(material, inventory, world, at, treeClaims);
        Optional<Recipe> next = Ladder.upTo(inventory, setAside, inReach, canDig, upgradeAllowed.getOrDefault(name, false) ? highest : 0);
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
    private boolean firstHearing(io.github.jayessdeesea.minecraft.protocol.ChatMessage chat) {
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
    private static Hands.Turn turn(java.util.function.BooleanSupplier active, java.util.function.Consumer<Boolean> tick) {
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
