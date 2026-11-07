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
 * <p>All soldiers share one {@link ChunkCache} - AGENTS.md's "Behavior modeling" section built its
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
        /** Torches left to put down round a SECURE objective, at the last check: -1 before the first. */
        int torchesLeft = -1;
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
    /** Each connected soldier's hunting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Hunter> hunters = new ConcurrentHashMap<>();
    /** Soldiers free to hunt when hungry: by day, out of contact, not walling in a base - squad loop writes, tick reads. */
    private final java.util.Set<String> huntAllowed = ConcurrentHashMap.newKeySet();
    /** At this food level or below, with no rations, a soldier free to hunts for meat: it has missed a meal or two. */
    static final int HUNT_BELOW_FOOD = 14;
    /** Each connected soldier's tree planting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Forester> foresters = new ConcurrentHashMap<>();
    /** The tree farm each soldier plants, while its squad's objective is one - squad loop writes, tick reads. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.farm.TreeFarm> planting = new ConcurrentHashMap<>();
    /** Each connected soldier's staircase cutting, by name. */
    private final Map<String, io.github.jayessdeesea.minecraft.bot.supply.Miner> miners = new ConcurrentHashMap<>();
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
    /** A patrol base objective re-issued this close to the base already laid out keeps that one. */
    private static final double BASE_SITE_RADIUS = 8;

    private final OperatorOrders operatorOrders;
    /** The chest soldiers draw kit from, if the operator set one up (docs/military-doctrine.md §4). */
    private final java.util.Optional<Position> supplyPoint;

    public SoldierRunner(ChainOfCommand chainOfCommand, OperatorOrders operatorOrders,
                         @Value("${minecraft.supply.point}") String supplyPoint, Roster roster,
                         @Value("${minecraft.yield-to-experiments}") boolean yieldToExperiments) {
        this.roster = roster;
        this.yieldToExperiments = yieldToExperiments;
        this.soldierDefinitions = roster.squads().stream()
                .flatMap(squad -> squad.soldiers().stream().map(name -> new SoldierDefinition(name, squad.name())))
                .toList();
        this.chainOfCommand = chainOfCommand;
        this.operatorOrders = operatorOrders;
        this.supplyPoint = Resupply.supplyPoint(supplyPoint);
    }

    private final ChunkCache chunkCache = new ChunkCache();
    private final EntityTracker entityTracker = new EntityTracker();
    private final WorldModel world = new WorldModel(chunkCache, entityTracker);

    @Override
    public void run(String... args) {
        for (SoldierDefinition definition : soldierDefinitions) {
            connect(definition);
        }
        ScheduledExecutorService squad = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "squad"));
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
        List<Present> present = new java.util.ArrayList<>();
        for (Roster.Squad squad : roster.squads()) {
            List<DeterministicSoldier> soldiers = presentSoldiers(squad.name());
            if (soldiers.isEmpty()) {
                continue;
            }
            SquadState state = squadStates.computeIfAbsent(squad.name(), name -> new SquadState());
            // Replacements (respawned soldiers) go to the rear and rejoin by link-up, so the first
            // formed soldier is the point man.
            List<SoldierStatus> statuses = state.linkUp.reconstitute(soldiers.stream().map(DeterministicSoldier::status).toList());
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
            Position pointMan = p.statuses().get(0).position();
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
            orders = ReactToContact.orders(statuses,
                    Resupply.orders(statuses, Cohesion.orders(statuses, state.linkUp.orders(statuses, Formations.assign(order, statuses))),
                            order.objective(), supplyPoint));
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
        // to one that needs them for a tool and can't chop any.
        Map<String, Integer> wanted = new HashMap<>();
        statuses.forEach(s -> {
            Integer logs = logsWanted.get(s.name());
            if (logs != null) {
                wanted.put(s.name(), logs);
            }
        });
        List<CrossLevel.Transfer> transfers = CrossLevel.pair(statuses, wanted);
        java.util.Set<CrossLevel.Transfer> crossLeveling = java.util.Set.copyOf(transfers);
        crossLeveling.stream().filter(t -> !state.crossLeveling.contains(t))
                .forEach(t -> LOG.info("[{} -> {}] CROSS-LEVEL: {} logs to {}", t.giver(), squadName, t.logs(), t.receiver()));
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
        if (site != null && state.baseSite != null && Math.hypot(site.x() - state.baseSite.x(), site.z() - state.baseSite.z()) <= BASE_SITE_RADIUS
                && Math.abs(site.y() - state.baseSite.y()) <= 2) {
            site = state.baseSite;
        } else if (site != null) {
            // The model's point is a guess at ground it can't see closely: the base goes on the
            // nearest level footing for the whole floor - never on rubble or a slope, where
            // soldiers can't all stand in their places.
            Position asked = site;
            site = levelSite(site, statuses.size(), squadName).orElse(null);
            if (site == null) {
                if (!asked.equals(state.unbuildable)) {
                    LOG.warn("[{}] no level ground for a patrol base within {} blocks of {} - not building", squadName,
                            SITE_SEARCH, asked);
                }
                state.unbuildable = asked;
            } else if (state.baseSite != null) {
                LOG.info("[{}] patrol base moved to {}", squadName, site);
            }
        }
        state.baseSite = site;
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
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> held = java.util.stream.Stream.of(plan, state.builtBase)
                .filter(java.util.Objects::nonNull).flatMap(p -> p.cells().stream())
                .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z()))
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
        // Mining: the staircase at the objective's x and z, down to its y - kept while the objective
        // stays put, its head where the ground is when first sited.
        io.github.jayessdeesea.minecraft.bot.supply.Staircase stairs = chainOfCommand.mineObjective(squadName).map(o -> {
            int x = (int) Math.floor(o.location().x());
            int z = (int) Math.floor(o.location().z());
            if (state.stairs != null && state.stairs.head().x() == x && state.stairs.head().z() == z) {
                return state.stairs;
            }
            Optional<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> head = io.github.jayessdeesea.minecraft.bot.supply.Staircase.headAt(
                    world, x, z, (int) Math.floor(statuses.get(0).position().y()));
            if (head.isEmpty()) {
                LOG.warn("[{}] MINE: no ground to start a staircase on at x={} z={}", squadName, x, z);
                return null;
            }
            int bottom = Math.max(io.github.jayessdeesea.minecraft.bot.supply.Staircase.DEEPEST,
                    Math.min((int) Math.floor(o.location().y()), head.get().y() - 1));
            LOG.info("[{}] MINE: a staircase down from {} to y={}", squadName, head.get(), bottom);
            return new io.github.jayessdeesea.minecraft.bot.supply.Staircase(head.get(), bottom);
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
            return io.github.jayessdeesea.minecraft.bot.supply.Lighting.plan(world, at, SECURE_RADIUS);
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
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            if (farm != null && !squadInContact) {
                farming.put(soldierName, farm);
            } else {
                farming.remove(soldierName);
            }
            boolean night = chainOfCommand.time().map(io.github.jayessdeesea.minecraft.command.TimeOfDay::dark).orElse(false);
            if (!night && !squadInContact && building == null) {
                huntAllowed.add(soldierName);
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
                    missing.ifPresent(r -> gathering.put(soldierName, new io.github.jayessdeesea.minecraft.command.Objective(
                            "Gather the makings of torches", secure.location(), secure.intent(),
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
                // The cutter stands where the stairs need it; the rest hold the top, clear of the shaft.
                Position place = soldierName.equals(cutter)
                        ? miners.get(soldierName).standAt().orElse(centre(stairs.head()))
                        : new Position(stairs.head().x() - 1.5 + i, stairs.head().y(), stairs.head().z() + 3.5);
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(),
                        soldierName.equals(cutter) ? 0.3 : 1.5, soldierName.equals(cutter) ? 0.5 : 4, Risk.LOW));
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
            if (building != null && !allInside && carried != null && !squadInContact
                    && carried.stone() + carried.dirt() + carried.logs() < stoneShare) {
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
                given = new Order.MoveTo(stand.get().x(), stand.get().y(), stand.get().z(), 0.3, 0.5, Risk.LOW);
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
    private Optional<Position> levelSite(Position asked, int soldiers, String squadName) {
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
                        boolean level = clear && hut.floor().stream().allMatch(cell -> {
                            int cx = (int) cell.x();
                            int cy = (int) cell.y();
                            int cz = (int) cell.z();
                            return world.isSolid(cx, cy - 1, cz) && !world.isSolid(cx, cy, cz) && !world.isSolid(cx, cy + 1, cz)
                                    && !world.isHazard(cx, cy, cz);
                        });
                        // Height counts double: a level footing on the roof of an old hut is
                        // nearer across than its own floor, but the floor is the one to reuse.
                        double distance = Math.hypot(dx, dz) + Math.abs(y - ay) * 2;
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

        CompletableFuture<PlaySession> joined = MinecraftClient.join(HOST, PORT, name, new PlaySessionListeners(
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
            chainOfCommand.setGround((x, z, nearY) -> io.github.jayessdeesea.minecraft.bot.supply.Staircase.headAt(world, x, z, nearY)
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
        io.github.jayessdeesea.minecraft.bot.supply.Scavenger scavenger = new io.github.jayessdeesea.minecraft.bot.supply.Scavenger(name);
        scavengers.put(name, scavenger);
        io.github.jayessdeesea.minecraft.bot.supply.Miner miner = new io.github.jayessdeesea.minecraft.bot.supply.Miner(name, session, inventory);
        miners.put(name, miner);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, name + "-tick"));
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
            io.github.jayessdeesea.minecraft.command.Objective gather = gathering.get(name);
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
            boolean hungry = status.condition().map(c -> c.food() <= HUNT_BELOW_FOOD).orElse(false)
                    && eating != null && eating.rations() == 0;
            turns.put(Hands.Routine.HUNTER, turn(hunter::active, free -> hunter.tick(status.position(), world,
                    free && hungry && huntAllowed.contains(name) && !inContact,
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
                                .map(receiver -> new Handover.Delivery(giving.receiver(), receiver.status().position(), giving.logs()));
                handover.tick(status.position(), delivery).ifPresent(soldier::lookAt);
            }));
            turns.put(Hands.Routine.MINER, turn(() -> stairs != null, free -> {
                if (stairs == null) {
                    miner.stop();
                } else {
                    stairsProgress.put(name, miner.tick(status.position(), world, stairs, free && !threatened));
                }
            }));
            turns.put(Hands.Routine.LAMPLIGHTER, turn(lamplighter::active,
                    free -> lamplighter.tick(status.position(), world, lights, free && !threatened)));
            io.github.jayessdeesea.minecraft.bot.farm.TreeFarm grove = planting.get(name);
            turns.put(Hands.Routine.FORESTER, turn(forester::active,
                    free -> forester.tick(status.position(), world, Optional.ofNullable(grove), free && !threatened)));
            turns.put(Hands.Routine.FARMER, turn(farmer::active,
                    free -> farmer.tick(status.position(), world, Optional.ofNullable(field), free && !threatened)));
            turns.put(Hands.Routine.GATHERER, turn(gatherer::active,
                    free -> gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                            gather == null ? status.position() : gather.location(), free && !inContact)));
            turns.put(Hands.Routine.SCAVENGER, turn(scavenger::active,
                    free -> scavenger.tick(status.position(), world, free && !threatened && fort == null)));
            Hands.tick(turns);
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
                    : forester.active() ? new Report.Planting()
                    : grove != null && forester.failure().isPresent() && !io.github.jayessdeesea.minecraft.bot.supply.Forester.planted(world, grove)
                    ? new Report.Cantco("can't plant the tree farm: " + forester.failure().get())
                    : lamplighter.active() || torchmaker.active() && torchesWanted ? new Report.Securing()
                    : !lights.isEmpty() && torchesWanted && torchmaker.failure().isPresent()
                    ? new Report.Cantco("can't make torches to light the ground: " + torchmaker.failure().get())
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
            soldier.takePlacement().ifPresent(cell -> {
                if (footing.place(cell)) {
                    chunkCache.setBlock(cell.x(), cell.y(), cell.z(), COBBLESTONE_STATE);
                }
            });
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
     * exceptionally} already logs that loudly and stops, per AGENTS.md's revisit-on-real-pressure
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
