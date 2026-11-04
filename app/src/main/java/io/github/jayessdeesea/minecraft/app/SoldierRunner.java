package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Builder;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.supply.Doorway;
import io.github.jayessdeesea.minecraft.bot.supply.Footing;
import io.github.jayessdeesea.minecraft.bot.supply.Gatherer;
import io.github.jayessdeesea.minecraft.bot.supply.Handover;
import io.github.jayessdeesea.minecraft.bot.supply.Rations;
import io.github.jayessdeesea.minecraft.bot.supply.Torchmaker;
import io.github.jayessdeesea.minecraft.bot.supply.SupplyRun;
import io.github.jayessdeesea.minecraft.bot.supply.Woodcraft;
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
public class SoldierRunner implements CommandLineRunner {

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
    private static final List<SoldierDefinition> SOLDIERS = Roster.SQUADS.stream()
            .flatMap(squad -> squad.soldiers().stream().map(name -> new SoldierDefinition(name, squad.name())))
            .toList();

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
        /** The base's breaches at the last check - logged when they change. */
        List<Position> breaches;
        java.util.Set<CrossLevel.Transfer> crossLeveling = java.util.Set.of();
    }

    private static final long SQUAD_PERIOD_MILLIS = 1000;

    /** Currently connected soldiers by name - written by connect/disconnect, read by the squad loop. */
    private final Map<String, DeterministicSoldier> activeSoldiers = new ConcurrentHashMap<>();
    private final ChainOfCommand chainOfCommand;
    private final Map<String, SquadState> squadStates = new HashMap<>(); // squad thread only
    /** Each connected soldier's sword-making routine, by name - set on join, read by the squad loop. */
    private final Map<String, Woodcraft> woodcrafts = new ConcurrentHashMap<>();
    /** Whether each soldier's squad leader lets it stop to rearm, with its squad out of contact - squad loop writes, tick reads. */
    private final Map<String, Boolean> rearmAllowed = new ConcurrentHashMap<>();
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
    /** What each soldier's squad is gathering, and around where - squad loop writes, tick reads; absent when nothing. */
    private final Map<String, io.github.jayessdeesea.minecraft.command.Objective> gathering = new ConcurrentHashMap<>();
    /** No hostile this close to a soldier, nor any squadmate in contact, before it stops to make a sword. */
    private static final double REARM_CLEAR_RADIUS = 16;
    /** A soldier being handed logs goes for dropped items this close - where a throw lands. */
    private static final double PICKUP_RADIUS = 4;
    /** A patrol base objective re-issued this close to the base already laid out keeps that one. */
    private static final double BASE_SITE_RADIUS = 8;

    private final OperatorOrders operatorOrders;
    /** The chest soldiers draw kit from, if the operator set one up (docs/military-doctrine.md §4). */
    private final java.util.Optional<Position> supplyPoint;

    public SoldierRunner(ChainOfCommand chainOfCommand, OperatorOrders operatorOrders,
                         @Value("${minecraft.supply.point}") String supplyPoint) {
        this.chainOfCommand = chainOfCommand;
        this.operatorOrders = operatorOrders;
        this.supplyPoint = Resupply.supplyPoint(supplyPoint);
    }

    private final ChunkCache chunkCache = new ChunkCache();
    private final EntityTracker entityTracker = new EntityTracker();
    private final WorldModel world = new WorldModel(chunkCache, entityTracker);

    @Override
    public void run(String... args) {
        for (SoldierDefinition definition : SOLDIERS) {
            connect(definition);
        }
        ScheduledExecutorService squad = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "squad"));
        squad.scheduleAtFixedRate(this::leadSquad, SQUAD_PERIOD_MILLIS, SQUAD_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
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
        List<Present> present = new java.util.ArrayList<>();
        for (Roster.Squad squad : Roster.SQUADS) {
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
            boolean canClimb = p.statuses().get(0).condition().flatMap(c -> c.kit()).map(k -> k.stone() > 0).orElse(false);
            SquadState state = squadStates.get(p.squad());
            boolean holdingBase = state.allInside && state.breaches != null && state.breaches.isEmpty();
            return new ChainOfCommand.Sitrep(p.squad(), p.statuses(), () -> TerrainSurveyor.survey(world, pointMan, canClimb), p.principalAt(),
                    () -> io.github.jayessdeesea.minecraft.bot.supply.ResourceSurveyor.survey(world, pointMan), holdingBase);
        }).toList());
        for (Present p : present) {
            chainOfCommand.currentOrder(p.squad()).ifPresent(order ->
                    orderSquad(p.squad(), squadStates.get(p.squad()), p.soldiers(), p.statuses(), order, p.principalAt()));
        }
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
            site = levelSite(site, statuses.size()).orElse(null);
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
        boolean allInside = plan != null && statuses.stream().allMatch(s -> plan.inside(s.position()));
        if ((plan != null) != state.fortifying) {
            LOG.info(plan != null ? "[{}] PATROL BASE at {}: {} blocks to build" : "[{}] patrol base stood down",
                    squadName, site, plan == null ? 0 : plan.cells().size());
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
        // A squad with a base to light and no torch: one soldier who can make them does, outside the walls.
        boolean squadHasTorch = statuses.stream().anyMatch(st -> torchesCarried.getOrDefault(st.name(), 0) > 0);
        // Before the squad seals itself in: a torchbearer works outside, and a sealed hut has no door.
        boolean sealedIn = allInside && breaches != null && breaches.isEmpty();
        String torchbearer = plan == null || squadHasTorch || lit || sealedIn ? null : statuses.stream().map(SoldierStatus::name)
                .filter(n -> canMakeTorches.getOrDefault(n, false))
                .min(java.util.Comparator.comparing(n -> !n.equals(state.torchbearer))).orElse(null);
        state.torchbearer = torchbearer;
        Position workshop = torchbearer == null ? null : workshop(plan).orElse(null);
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
        for (int i = 0; i < statuses.size(); i++) {
            String soldierName = statuses.get(i).name();
            transfers.stream().filter(t -> t.giver().equals(soldierName)).findFirst()
                    .ifPresentOrElse(t -> deliveries.put(soldierName, t), () -> deliveries.remove(soldierName));
            rearmAllowed.put(soldierName, order.rearm() && !squadInContact);
            Optional<io.github.jayessdeesea.minecraft.command.Objective> gather = squadInContact
                    ? Optional.empty() : chainOfCommand.gatherObjective(squadName);
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            Order given = orders.get(i);
            if (building != null) {
                List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells = new java.util.ArrayList<>(
                        java.util.stream.Stream.concat(building.cells().stream(), breaches.stream().filter(b -> !building.cells().contains(b)))
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList());
                // Each soldier starts at a different point round the walls, so they don't all go for the same block.
                java.util.Collections.rotate(cells, -i * cells.size() / statuses.size());
                boolean makeTorches = soldierName.equals(torchbearer) && workshop != null;
                Position place = makeTorches ? workshop : building.stands().get(i);
                fortifying.put(soldierName, new Fortify(place, List.copyOf(cells), allInside, makeTorches,
                        soldierName.equals(lighter)));
                // Rearming while it waits: it goes where its sword-making takes it, then back to its place.
                Optional<Position> rearming = Optional.ofNullable(woodcrafts.get(soldierName)).flatMap(Woodcraft::standAt);
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
            Woodcraft woodcraft = woodcrafts.get(soldierName);
            Gatherer gatherer = gatherers.get(soldierName);
            Optional<Position> stand = woodcraft == null ? Optional.empty() : woodcraft.standAt();
            if (stand.isEmpty() && depositors.containsKey(soldierName)) {
                stand = depositors.get(soldierName).standAt();
            }
            if (stand.isEmpty() && handovers.containsKey(soldierName)) {
                stand = handovers.get(soldierName).standAt();
            }
            if (stand.isEmpty() && receiving.contains(soldierName)) {
                // Walk onto what's been thrown: the server hands a dropped stack to whoever touches it.
                stand = entityTracker.nearbyItems(statuses.get(i).position(), PICKUP_RADIUS).stream().findFirst();
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

    private void logSquadSitrep() {
        for (Roster.Squad squadDefinition : Roster.SQUADS) {
            List<DeterministicSoldier> squad = presentSoldiers(squadDefinition.name());
            if (squad.isEmpty()) {
                continue;
            }
            StringBuilder sitrep = new StringBuilder();
            for (DeterministicSoldier soldier : squad) {
                SoldierStatus status = soldier.status();
                sitrep.append(String.format(Locale.ROOT, "%n  %s at (%.1f, %.1f, %.1f): %s %s",
                        status.name(), status.position().x(), status.position().y(), status.position().z(), status.report(),
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
     * The nearest point to {@code asked}, within {@value #SITE_SEARCH} blocks across and
     * {@value #GROUND_SEARCH} up or down, where every floor cell of a hut for {@code soldiers} is
     * standable at one height: solid below, room for a body above.
     */
    private Optional<Position> levelSite(Position asked, int soldiers) {
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
                        boolean level = PatrolBase.hasty(candidate, soldiers).floor().stream().allMatch(cell -> {
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
        return SOLDIERS.stream().filter(d -> d.squad().equals(squad))
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
        AtomicReference<Woodcraft> woodcraftRef = new AtomicReference<>();
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
                // Every connection hears every chat line; only the radio operator's passes it on.
                chat -> {
                    if (definition == SOLDIERS.getFirst()) {
                        operatorOrders.orderFrom(chat).ifPresent(order -> {
                            switch (order) {
                                case OperatorOrders.Order.Mission(String operator, String mission) ->
                                        chainOfCommand.onOperatorMission(operator, mission);
                                case OperatorOrders.Order.Escort(String operator, String principal) ->
                                        chainOfCommand.onOperatorEscort(operator, principal);
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
                    Woodcraft woodcraft = woodcraftRef.get();
                    if (woodcraft != null) {
                        woodcraft.onContainer(container);
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
                    if (definition == SOLDIERS.getFirst()) {
                        overworldClock(time, clockNames.get()).ifPresent(chainOfCommand::onTime);
                    }
                },
                cause -> onDisconnected(definition, activeScheduler, cause)));
        joined.thenAccept(sessionReady::complete);
        joined.thenAcceptBoth(firstPosition, (session, spawn) -> onJoined(definition, session, spawn, activeScheduler, activeSoldier, lastHealth, lastKit, inventory, supplyRun, woodcraftRef, depositorRef, torchmakerRef))
                .exceptionally(cause -> {
                    LOG.error("{} failed to join {}:{} - {}", name, HOST, PORT, cause.getMessage(), cause);
                    return null;
                });
    }

    /**
     * The overworld's time of day from a Set Time, its clocks named through this session's synced
     * registry - empty when this update doesn't carry the overworld clock (it only sends clocks that
     * changed).
     */
    static Optional<io.github.jayessdeesea.minecraft.command.TimeOfDay> overworldClock(TimeUpdate time, List<String> clockNames) {
        for (Map.Entry<Integer, TimeUpdate.Clock> clock : time.clocks().entrySet()) {
            int id = clock.getKey();
            if (id < 0 || id >= clockNames.size()) {
                throw new IllegalStateException("world clock " + id + " isn't in the synced registry of " + clockNames.size());
            }
            if (clockNames.get(id).equals("minecraft:overworld")) {
                return Optional.of(io.github.jayessdeesea.minecraft.command.TimeOfDay.of(clock.getValue().totalTicks(),
                        clock.getValue().rate() > 0));
            }
        }
        return Optional.empty();
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
                           AtomicReference<Woodcraft> woodcraftRef, AtomicReference<Depositor> depositorRef,
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
        if (definition == SOLDIERS.getFirst()) {
            // The point man carries the squad's radio: the squad leader and general aren't in the
            // world, so their traffic is keyed out over his connection (rebound on reconnect).
            chainOfCommand.setRadio(session::sendChatCommand);
        }
        activeSoldiers.put(name, soldier);
        SupplyRun run = new SupplyRun(name, session, inventory, () -> {
            Kit drawn = inventory.kit();
            lastKit.set(drawn);
            soldier.reportKit(drawn);
        });
        supplyRun.set(run);
        Woodcraft woodcraft = new Woodcraft(name, session, inventory, treeClaims, () -> {
            Kit made = inventory.kit();
            lastKit.set(made);
            soldier.reportKit(made);
        });
        woodcrafts.put(name, woodcraft);
        Gatherer gatherer = new Gatherer(name, session, inventory, treeClaims);
        gatherers.put(name, gatherer);
        Depositor depositor = new Depositor(name, session, inventory, chainOfCommand::onStockpile);
        depositors.put(name, depositor);
        depositorRef.set(depositor);
        Handover handover = new Handover(name, session, inventory);
        handovers.put(name, handover);
        Builder builder = new Builder(name, session, inventory);
        builders.put(name, builder);
        Rations rations = new Rations(name, session, inventory);
        Doorway doorway = new Doorway(name, session, inventory);
        Footing footing = new Footing(session, inventory);
        // What the server was last told about sprinting: a new connection starts walking.
        java.util.concurrent.atomic.AtomicBoolean sprintingSent = new java.util.concurrent.atomic.AtomicBoolean();
        woodcraftRef.set(woodcraft);
        Torchmaker torchmaker = new Torchmaker(name, session, inventory);
        // Ticks while told to light the base: one torch tried a second, until the light check sees it.
        java.util.concurrent.atomic.AtomicInteger torchTicks = new java.util.concurrent.atomic.AtomicInteger();
        torchmakerRef.set(torchmaker);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, name + "-tick"));
        activeScheduler.set(scheduler);
        scheduler.scheduleAtFixedRate(() -> {
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
            boolean needsPickaxe = gather != null && gather.gather().equals(Optional.of(io.github.jayessdeesea.minecraft.command.Resource.STONE))
                    && inventory.slotOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.PICKAXE) < 0;
            // A sword, if bare-handed and allowed; a pickaxe, if sent for stone without one - both at one table.
            java.util.Set<Woodcraft.Tool> tools = java.util.EnumSet.noneOf(Woodcraft.Tool.class);
            if (bareHanded && rearmAllowed.getOrDefault(name, false)) {
                tools.add(Woodcraft.Tool.WOODEN_SWORD);
            }
            if (needsPickaxe) {
                tools.add(Woodcraft.Tool.WOODEN_PICKAXE);
            }
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
            java.util.Set<Woodcraft.Tool> craftable = fort == null ? tools
                    : waiting && tools.contains(Woodcraft.Tool.WOODEN_SWORD) ? java.util.Set.of(Woodcraft.Tool.WOODEN_SWORD)
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
            turns.put(Hands.Routine.TORCHMAKER, turn(torchmaker::active,
                    free -> torchmaker.tick(status.position(), world, free && atWorkshop, threatened)));
            turns.put(Hands.Routine.RATIONS, turn(rations::active,
                    free -> rations.tick(status.condition().map(c -> c.food()).orElse(Condition.MAX_FOOD), free && !threatened,
                            soldier.yaw(), soldier.pitch())));
            turns.put(Hands.Routine.WOODCRAFT, turn(woodcraft::active,
                    free -> woodcraft.tick(status.position(), world, free ? craftable : java.util.Set.of(), threatened)));
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
            turns.put(Hands.Routine.GATHERER, turn(gatherer::active,
                    free -> gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                            gather == null ? status.position() : gather.location(), free && !inContact)));
            Hands.tick(turns);
            // Short of logs for tools it tried and failed to chop wood for: a squadmate may have
            // some to spare (§13).
            int logsShort = tools.isEmpty() ? 0 : Woodcraft.logsShort(inventory, tools);
            if (logsShort > 0 && !woodcraft.active() && woodcraft.failure().isPresent()) {
                logsWanted.put(name, logsShort);
            } else {
                logsWanted.remove(name);
            }
            soldier.task(fort != null
                    ? (built.get() instanceof Builder.Progress.Failed(String why) ? new Report.Cantco("can't finish the patrol base: " + why)
                            : built.get() instanceof Builder.Progress.Done ? null : new Report.Fortifying())
                    : woodcraft.active()
                    ? (woodcraft.tools().contains(Woodcraft.Tool.WOODEN_SWORD) ? new Report.Rearming()
                            : new Report.Gathering(io.github.jayessdeesea.minecraft.command.Resource.STONE))
                    : depositor.active() ? new Report.Gathering(carried != null && carried.stone() >= Gatherer.CARRY_STONE
                            ? io.github.jayessdeesea.minecraft.command.Resource.STONE : io.github.jayessdeesea.minecraft.command.Resource.WOOD)
                    : handover.active() ? handover.report().orElseThrow()
                    : gatherer.active() ? new Report.Gathering(gather.gather().orElseThrow())
                    : gather != null && gatherer.exhausted()
                    ? new Report.Cantco("nothing left to gather within 16 blocks of the objective")
                    : !tools.isEmpty() && woodcraft.failure().isPresent() ? new Report.Cantco(woodcraft.failure().get()) : null);
            Position current = status.position();
            if (soldier.sprinting() != sprintingSent.get()) {
                sprintingSent.set(soldier.sprinting());
                session.setSprinting(soldier.sprinting());
            }
            session.reportPosition(current.x(), current.y(), current.z(), soldier.yaw(), soldier.pitch(), soldier.onGround());
            // A pillar step's block goes in after the server has the soldier's new height - it won't
            // place a block where a body is - and into the world model at once, so the soldier lands
            // on it before the server's block update arrives.
            soldier.takePlacement().ifPresent(cell -> {
                if (footing.place(cell)) {
                    chunkCache.setBlock(cell.x(), cell.y(), cell.z(), COBBLESTONE_STATE);
                }
            });
            footing.tick();
            session.endTick();
        }, 0, TICK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> LOG.debug("{} status: {} - plan: {} - session open: {}", name, soldier.status(), soldier.lastPlan(), session.isOpen()),
                STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
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
        LOG.warn("{} disconnected unexpectedly ({}) - reconnecting", name, cause.getMessage());
        activeSoldiers.remove(name);
        ScheduledExecutorService scheduler = activeScheduler.get();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        // The new connection re-receives Add Entity for everything in view; drop this one's stale view.
        entityTracker.removeOwner(name);
        chunkCache.removeOwner(name);
        connect(definition);
    }
}
