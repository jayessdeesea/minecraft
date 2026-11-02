package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Builder;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.supply.Gatherer;
import io.github.jayessdeesea.minecraft.bot.supply.Handover;
import io.github.jayessdeesea.minecraft.bot.supply.Rations;
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
    private record Fortify(Position stand, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells, boolean allInside) {
    }
    /** What each soldier is building, while its squad builds a patrol base - squad loop writes, tick reads. */
    private final Map<String, Fortify> fortifying = new ConcurrentHashMap<>();
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
            return new ChainOfCommand.Sitrep(p.squad(), p.statuses(), () -> TerrainSurveyor.survey(world, pointMan), p.principalAt(),
                    () -> io.github.jayessdeesea.minecraft.bot.supply.ResourceSurveyor.survey(world, pointMan));
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
            // The model's height is a guess; the floor goes on the ground actually there.
            site = groundAt(site);
            if (state.baseSite != null) {
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
        for (int i = 0; i < statuses.size(); i++) {
            String soldierName = statuses.get(i).name();
            transfers.stream().filter(t -> t.giver().equals(soldierName)).findFirst()
                    .ifPresentOrElse(t -> deliveries.put(soldierName, t), () -> deliveries.remove(soldierName));
            rearmAllowed.put(soldierName, order.rearm() && !squadInContact);
            Optional<io.github.jayessdeesea.minecraft.command.Objective> gather = squadInContact
                    ? Optional.empty() : chainOfCommand.gatherObjective(squadName);
            gather.ifPresentOrElse(o -> gathering.put(soldierName, o), () -> gathering.remove(soldierName));
            Order given = orders.get(i);
            if (plan != null) {
                List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells = new java.util.ArrayList<>(plan.cells().stream()
                        .map(c -> new io.github.jayessdeesea.minecraft.bot.nav.BlockPos((int) c.x(), (int) c.y(), (int) c.z())).toList());
                // Each soldier starts at a different point round the walls, so they don't all go for the same block.
                java.util.Collections.rotate(cells, -i * cells.size() / statuses.size());
                fortifying.put(soldierName, new Fortify(plan.stands().get(i), List.copyOf(cells), allInside));
                Position place = plan.stands().get(i);
                byName.get(soldierName).receiveOrder(new Order.MoveTo(place.x(), place.y(), place.z(), 0.3, 0.5, Risk.LOW));
                continue;
            }
            fortifying.remove(soldierName);
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

    /**
     * The standable cell nearest in height to {@code at} - solid below, room for a body above - or
     * {@code at} itself when there's none within {@value #GROUND_SEARCH} blocks up or down.
     */
    private Position groundAt(Position at) {
        int x = (int) Math.floor(at.x());
        int z = (int) Math.floor(at.z());
        int y = (int) Math.floor(at.y());
        for (int dy = 0; dy <= GROUND_SEARCH; dy++) {
            for (int candidate : new int[]{y + dy, y - dy}) {
                if (world.isSolid(x, candidate - 1, z) && !world.isSolid(x, candidate, z) && !world.isSolid(x, candidate + 1, z)) {
                    return new Position(at.x(), candidate, at.z());
                }
            }
        }
        return at;
    }

    private static final int GROUND_SEARCH = 8;

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
        joined.thenAcceptBoth(firstPosition, (session, spawn) -> onJoined(definition, session, spawn, activeScheduler, activeSoldier, lastHealth, lastKit, inventory, supplyRun, woodcraftRef, depositorRef))
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
                           AtomicReference<Woodcraft> woodcraftRef, AtomicReference<Depositor> depositorRef) {
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
        // What the server was last told about sprinting: a new connection starts walking.
        java.util.concurrent.atomic.AtomicBoolean sprintingSent = new java.util.concurrent.atomic.AtomicBoolean();
        woodcraftRef.set(woodcraft);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, name + "-tick"));
        activeScheduler.set(scheduler);
        scheduler.scheduleAtFixedRate(() -> {
            soldier.tick(world).ifPresent(target -> {
                LOG.info("{} attacks entity {}", name, target);
                session.attack(target);
            });
            SoldierStatus status = soldier.status();
            run.tick(status.position(), supplyPoint, Resupply.isShort(status) && !(status.report() instanceof Report.InContact));
            boolean bareHanded = status.condition().flatMap(c -> c.kit()).map(k -> k.weapon().isEmpty()).orElse(false);
            boolean clear = !(status.report() instanceof Report.InContact) && !run.active()
                    && world.nearbyHostiles(status.position(), REARM_CLEAR_RADIUS).isEmpty();
            io.github.jayessdeesea.minecraft.command.Objective gather = gathering.get(name);
            boolean needsPickaxe = gather != null && gather.gather().equals(Optional.of(io.github.jayessdeesea.minecraft.command.Resource.STONE))
                    && inventory.slotOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.PICKAXE) < 0;
            // A sword first, if bare-handed and allowed; then a pickaxe, if sent for stone without one.
            Optional<Woodcraft.Tool> tool = !clear ? Optional.empty()
                    : bareHanded && rearmAllowed.getOrDefault(name, false) ? Optional.of(Woodcraft.Tool.WOODEN_SWORD)
                    : needsPickaxe ? Optional.of(Woodcraft.Tool.WOODEN_PICKAXE) : Optional.empty();
            Fortify fort = fortifying.get(name);
            boolean building = fort != null && fort.allInside() && !builder.done();
            rations.tick(status.condition().map(c -> c.food()).orElse(Condition.MAX_FOOD),
                    clear && !woodcraft.active() && !depositor.active() && !handover.active() && !building,
                    soldier.yaw(), soldier.pitch());
            woodcraft.tick(status.position(), world, fort == null && !rations.active() ? tool : Optional.empty());
            // Short of logs for a tool it tried and failed to chop wood for: a squadmate may have
            // some to spare (§13).
            Optional<Woodcraft.Tool> needed = bareHanded && rearmAllowed.getOrDefault(name, false) ? Optional.of(Woodcraft.Tool.WOODEN_SWORD)
                    : needsPickaxe ? Optional.of(Woodcraft.Tool.WOODEN_PICKAXE) : Optional.empty();
            int logsCarried = inventory.count(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.LOG);
            if (needed.isPresent() && !woodcraft.active() && woodcraft.failure().isPresent() && logsCarried < needed.get().logs()) {
                logsWanted.put(name, needed.get().logs() - logsCarried);
            } else {
                logsWanted.remove(name);
            }
            Kit carried = status.condition().flatMap(c -> c.kit()).orElse(null);
            boolean fullLoad = carried != null && (carried.logs() >= Gatherer.CARRY_LOGS || carried.stone() >= Gatherer.CARRY_STONE);
            depositor.tick(status.position(), supplyPoint, fullLoad && clear && !woodcraft.active() && !handover.active() && fort == null
                    && !rations.active());
            CrossLevel.Transfer giving = deliveries.get(name);
            Optional<Handover.Delivery> delivery = giving == null || !clear || woodcraft.active() || depositor.active() || fort != null
                    || rations.active()
                    ? Optional.empty()
                    : Optional.ofNullable(activeSoldiers.get(giving.receiver()))
                            .map(receiver -> new Handover.Delivery(giving.receiver(), receiver.status().position(), giving.logs()));
            handover.tick(status.position(), delivery).ifPresent(soldier::lookAt);
            Builder.Progress built = null;
            if (fort != null && fort.allInside() && !(status.report() instanceof Report.InContact) && !rations.active()
                    && Math.hypot(status.position().x() - fort.stand().x(), status.position().z() - fort.stand().z()) < 0.5) {
                built = builder.tickInPlace(status.position(), world, fort.cells());
                builder.lookAt().ifPresent(soldier::lookAt);
            } else {
                builder.stop();
            }
            gatherer.tick(status.position(), world, gather == null ? Optional.empty() : gather.gather(),
                    gather == null ? status.position() : gather.location(),
                    !(status.report() instanceof Report.InContact) && !woodcraft.active() && !run.active() && !depositor.active()
                            && !handover.active() && !rations.active());
            soldier.task(fort != null
                    ? (built instanceof Builder.Progress.Failed(String why) ? new Report.Cantco("can't finish the patrol base: " + why)
                            : built instanceof Builder.Progress.Done ? null : new Report.Fortifying())
                    : woodcraft.active()
                    ? (woodcraft.tool() == Woodcraft.Tool.WOODEN_SWORD ? new Report.Rearming()
                            : new Report.Gathering(io.github.jayessdeesea.minecraft.command.Resource.STONE))
                    : depositor.active() ? new Report.Gathering(carried != null && carried.stone() >= Gatherer.CARRY_STONE
                            ? io.github.jayessdeesea.minecraft.command.Resource.STONE : io.github.jayessdeesea.minecraft.command.Resource.WOOD)
                    : handover.active() ? handover.report().orElseThrow()
                    : gatherer.active() ? new Report.Gathering(gather.gather().orElseThrow())
                    : gather != null && gatherer.exhausted()
                    ? new Report.Cantco("nothing left to gather within 16 blocks of the objective")
                    : tool.isPresent() && woodcraft.failure().isPresent() ? new Report.Cantco(woodcraft.failure().get()) : null);
            Position current = status.position();
            if (soldier.sprinting() != sprintingSent.get()) {
                sprintingSent.set(soldier.sprinting());
                session.setSprinting(soldier.sprinting());
            }
            session.reportPosition(current.x(), current.y(), current.z(), soldier.yaw(), soldier.pitch(), soldier.onGround());
            session.endTick();
        }, 0, TICK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> LOG.debug("{} status: {} - plan: {} - session open: {}", name, soldier.status(), soldier.lastPlan(), session.isOpen()),
                STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
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
