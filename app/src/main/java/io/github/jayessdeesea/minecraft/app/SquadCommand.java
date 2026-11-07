package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * One squad's half of {@link ChainOfCommand}: its squad leader, its own decision thread, the
 * objective and order it's working to, and its escort if the operator attached it to a player.
 * {@code ChainOfCommand} decides when the general thinks and hands objectives down; this decides
 * when its squad leader thinks.
 */
final class SquadCommand {

    private static final Logger LOG = LogManager.getLogger(SquadCommand.class);

    private static final long SQUAD_PERIOD_MILLIS = 20_000;
    /** How far the principal moves before the squad leader is given its new position. */
    static final double ESCORT_RETASK_DISTANCE = 8;

    final String name;
    private final SquadLeader squadLeader;
    private final Consumer<String> radio;
    private final ExecutorService thread;
    private final AtomicBoolean busy = new AtomicBoolean();

    private volatile SquadOrder order;
    private volatile Objective objective;
    private volatile boolean newObjective;
    private volatile long lastDecisionMillis = Long.MIN_VALUE / 2;
    // The reports last answered - a decision is triggered early only when these change.
    private volatile Report reportAnswered = new Report.Idle();
    private volatile Set<String> cantcosAnswered = Set.of();
    /** The soldiers in contact when the squad leader last decided - a new one is a contact report. */
    private volatile Set<String> contactsAnswered = Set.of();
    /** Whether any soldier was in contact at the last SITREP the general's trigger looked at. */
    private volatile boolean contactReported;
    /** The player this squad is escorting, or null - set by the operator's thread. */
    private volatile String escort;
    private volatile boolean principalOutOfSight;

    /** The server sent the time of day - the squad leader plans around nightfall too. */
    void receiveTime(io.github.jayessdeesea.minecraft.command.TimeOfDay time) {
        squadLeader.receiveTime(time);
    }

    SquadCommand(String name, SquadLeader squadLeader, Consumer<String> radio) {
        this.name = name;
        this.squadLeader = squadLeader;
        this.radio = radio;
        this.thread = Executors.newSingleThreadExecutor(r -> new Thread(r, "squad-leader-" + name));
    }

    /**
     * The squad's report upward. Holding its sealed patrol base, the squad is doing what it was
     * ordered - whatever its squad leader last said: from inside a sealed hut no ground is
     * reachable, and that reads as CANTCO if anyone asks.
     */
    Report report() {
        return holdingBase ? new Report.InPosition() : squadLeader.report();
    }

    /** Whether the squad is all inside its sealed patrol base - nothing for its squad leader to decide. */
    private volatile boolean holdingBase;

    Optional<SquadOrder> order() {
        return Optional.ofNullable(order);
    }

    /** The general's objective for the squad, once it has one. */
    Optional<Objective> objective() {
        return Optional.ofNullable(objective);
    }

    /** What the squad's objective has it gather, and around where - if anything. */
    Optional<Objective> gatherObjective() {
        Objective current = objective;
        return current != null && current.gather().isPresent() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> patrolBaseObjective() {
        Objective current = objective;
        return current != null && current.patrolBase() ? Optional.of(current) : Optional.empty();
    }

    boolean hasObjective() {
        return objective != null;
    }

    Optional<String> escort() {
        return Optional.ofNullable(escort);
    }

    void escort(String principal) {
        escort = principal;
        principalOutOfSight = false;
    }

    /** True the first time the squad leader's own report turns to CANTCO - the general's early trigger. */
    boolean newCantco() {
        Report report = report();
        boolean fresh = report instanceof Report.Cantco && !(reportAnswered instanceof Report.Cantco);
        reportAnswered = report;
        return fresh;
    }

    /**
     * True the first time a SITREP shows the squad in contact after one that didn't - the general's
     * early trigger, as a contact report goes up the chain (docs/military-doctrine.md § 8).
     */
    boolean newContact(List<SoldierStatus> squad) {
        boolean inContact = squad.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        boolean fresh = inContact && !contactReported;
        contactReported = inContact;
        return fresh;
    }

    void receiveObjective(Objective given) {
        squadLeader.receiveObjective(given);
        objective = given;
        newObjective = true;
    }

    /** A SITREP from this squad's soldiers: decides whether its squad leader should think now. */
    void onSitrep(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain, Optional<Position> principalAt,
                  boolean holdingBase, long now, long minGapMillis) {
        this.holdingBase = holdingBase;
        if (holdingBase) {
            return; // the order is being met: no model call, and no terrain survey from inside a box
        }
        String principal = escort;
        if (principal != null) {
            followPrincipal(principal, principalAt);
        }
        Set<String> cantcos = squad.stream().filter(s -> s.report() instanceof Report.Cantco)
                .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
        boolean newSoldierCantco = !cantcosAnswered.containsAll(cantcos);
        // A soldier newly in contact is a contact report: the squad leader decides now whether to
        // fight, bypass or break contact, not at its next scheduled look.
        Set<String> contacts = squad.stream().filter(s -> s.report() instanceof Report.InContact)
                .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
        boolean newSoldierContact = !contactsAnswered.containsAll(contacts);
        if (objective != null && (newObjective || now - lastDecisionMillis >= SQUAD_PERIOD_MILLIS
                || ((newSoldierCantco || newSoldierContact) && now - lastDecisionMillis >= minGapMillis))) {
            if (newSoldierContact && !newObjective) {
                LOG.info("[{}] contact report: {} in contact - the squad leader decides now", name, contacts);
            }
            cantcosAnswered = cantcos;
            contactsAnswered = contacts;
            decide(squad, terrain, now);
        }
    }

    /**
     * While escorting, the squad leader's objective is the principal's position - re-issued once
     * the principal has moved {@link #ESCORT_RETASK_DISTANCE} from it, so the squad leader's view
     * keeps up without a decision every second (the escort drill follows the live position in
     * between). A principal nobody in the squad can see keeps the last objective.
     */
    private void followPrincipal(String principal, Optional<Position> principalAt) {
        if (principalAt.isEmpty()) {
            if (!principalOutOfSight) {
                LOG.warn("[{}] can't see {} - holding the last known position", name, principal);
                principalOutOfSight = true;
            }
            return;
        }
        principalOutOfSight = false;
        Position at = principalAt.get();
        Objective current = objective;
        if (current != null && current.mission().equals(escortMission(principal))
                && Cohesion.distance(current.location(), at) < ESCORT_RETASK_DISTANCE) {
            return;
        }
        LOG.info("[{}] escort objective follows {} to {}", name, principal, ChainOfCommand.at(at));
        receiveObjective(new Objective(escortMission(principal), at,
                "Keep " + principal + " safe: stay close around them and intercept hostiles that come near them."));
    }

    private static String escortMission(String principal) {
        return "Escort and protect " + principal;
    }

    private void decide(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain, long now) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        lastDecisionMillis = now;
        newObjective = false;
        thread.execute(() -> {
            try {
                long started = System.currentTimeMillis();
                TerrainSurvey survey = terrain.get();
                long reachable = survey.samples().stream().filter(TerrainSurvey.Sample::reachable).count();
                squadLeader.decide(squad, survey).ifPresent(decided -> {
                    LOG.info("[{} -> squad] ({} ms, terrain {}/{} reachable) FRAGO: move to ({}, {}, {}) in {} at {} interval, {} risk{}. Intent: {}",
                            name, System.currentTimeMillis() - started, reachable, survey.samples().size(),
                            Math.round(decided.objective().x()), Math.round(decided.objective().y()), Math.round(decided.objective().z()),
                            decided.formation(), decided.interval(), decided.risk(), soldierRisk(decided), decided.intent());
                    radio.accept("[" + name + " -> squad] FRAGO: " + decided.formation() + " to " + ChainOfCommand.at(decided.objective())
                            + ", " + decided.risk() + " risk" + soldierRisk(decided) + ". Intent: " + decided.intent());
                    if (squadLeader.report() instanceof Report.Cantco cantco) {
                        LOG.warn("[{} -> General] CANTCO: {}", name, cantco.reason());
                        radio.accept("[" + name + " -> General] CANTCO: " + cantco.reason());
                    }
                    order = decided;
                });
            } catch (RuntimeException e) {
                LOG.error("[{}] no decision - squad keeps its last order: {}", name, e.getMessage(), e);
            } finally {
                busy.set(false);
            }
        });
    }

    /** ", Carol at MEDIUM risk" for each per-soldier override, in name order; empty if none. */
    private static String soldierRisk(SquadOrder order) {
        return order.soldierRisk().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> ", " + e.getKey() + " at " + e.getValue() + " risk").collect(Collectors.joining());
    }

    /** Stops the squad leader's thread - a decision in flight is abandoned. */
    void stop() {
        thread.shutdownNow();
    }
}
