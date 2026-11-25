package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadContact;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
 * {@code ChainOfCommand} decides when the platoon leader thinks and hands objectives down; this decides
 * when its squad leader thinks, at the {@link Pace} of what decides for it.
 */
final class SquadCommand {

    private static final Logger LOG = LogManager.getLogger(SquadCommand.class);

    /** How far the principal moves before the squad leader is given its new position. */
    static final double ESCORT_RETASK_DISTANCE = 8;
    /**
     * How long a principal can be out of every soldier's sight before the escort ends: a player who
     * leaves the game is gone from the world, and a squad shouldn't guard an empty spot all night.
     */
    static final long ESCORT_LOST_MILLIS = 30_000;

    final String name;
    private final SquadLeader squadLeader;
    /** The time of day last sent - a connection's thread writes, for the log of each decision. */
    private volatile TimeOfDay time;
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
    /** The squad's contact, carried SITREP to SITREP: over once it has settled - the squad loop's thread writes. */
    private volatile SquadContact contact = SquadContact.NONE;
    /** The soldiers whose contact began since the squad leader last decided, and is still on: each a contact report to a rule. */
    private volatile Set<String> contactsUnanswered = Set.of();
    /** The soldiers in contact when the squad leader last decided - to a model, one in contact who isn't among them is a contact report. */
    private volatile Set<String> contactsAnswered = Set.of();
    /** The player this squad is escorting, or null - set by the operator's thread. */
    private volatile String escort;
    private volatile boolean principalOutOfSight;
    /** When the principal was last seen - the squad loop's thread writes and reads. */
    private long principalSeenMillis;
    /** The principal the escort just ended for, until the chain of command hears of it. */
    private volatile String escortEnded;

    /** The server sent the time of day - the squad leader plans around nightfall too. */
    void receiveTime(TimeOfDay time) {
        this.time = time;
        squadLeader.receiveTime(time);
    }

    /** Its soldiers' standing plans, as kept - the squad loop's thread sends them, for the squad leader's next look. */
    void receivePlans(Map<String, Plan> plans, Map<String, String> workOrders) {
        this.plans = Map.copyOf(plans);
        squadLeader.receivePlans(plans, workOrders);
    }

    /** Each soldier's standing role other than rifleman, as its plan last stood: for the platoon leader's picture. */
    Map<String, Role> roles() {
        Map<String, Role> roles = new HashMap<>();
        plans.forEach((name, plan) -> {
            if (plan.role() != Role.RIFLEMAN) {
                roles.put(name, plan.role());
            }
        });
        return roles;
    }

    /** The squad's plans, as the squad loop last gave them - squad loop writes, the platoon leader's decision reads. */
    private volatile Map<String, Plan> plans = Map.of();

    private final ModelCalls modelCalls;

    SquadCommand(String name, SquadLeader squadLeader, Consumer<String> radio, ModelCalls modelCalls) {
        this(name, squadLeader, radio, modelCalls, Clockwork.REAL, new Faults(Clockwork.REAL::millis));
    }

    SquadCommand(String name, SquadLeader squadLeader, Consumer<String> radio, ModelCalls modelCalls, Clockwork clockwork, Faults faults) {
        this.modelCalls = modelCalls;
        this.name = name;
        this.squadLeader = squadLeader;
        this.radio = radio;
        this.clockwork = clockwork;
        this.thread = clockwork.thread("squad-leader-" + name);
        this.pace = Pace.of(squadLeader);
        this.faults = faults;
    }

    private final Clockwork clockwork;
    /** How often its squad leader is asked, and where its decision runs: a rule's pace, or a model's. */
    private final Pace pace;
    /** The chain of command's: a rule's failed decision or survey fails alone, logged and counted, and is in the dawn review. */
    private final Faults faults;
    /** The terrain round its point man, as a rule's decisions read it: taken on the survey's clock, not theirs. */
    private final Surveyed<TerrainSurvey> surveyed = new Surveyed<>(survey -> new Position(survey.originX() + 0.5, 0, survey.originZ() + 0.5));
    /** Whether its squad leader's report was a CANTCO at its last decision: said once under an order that stands. */
    private volatile boolean cantcoSaid;

    /**
     * The squad's report upward. Holding its sealed patrol base, the squad is doing what it was
     * ordered - whatever its squad leader last said: from inside a sealed hut no ground is
     * reachable, and that reads as CANTCO if anyone asks.
     */
    Report report() {
        if (holdingBase) {
            return new Report.InPosition();
        }
        Map<String, Plan> plans = this.plans;
        Objective objective = this.objective;
        if (objective != null && noneFree(plans.values().stream().map(Plan::role).toList(), objective.task())) {
            return noneFree(roles(), objective);
        }
        return squadLeader.report();
    }

    /**
     * Whether a squad whose soldiers hold {@code roles} has nobody to do {@code task}: every soldier
     * has a standing job of its own and none of those jobs is the task. A patrol base is the
     * exception - every soldier comes in for the night - and so is a plain move.
     */
    static boolean noneFree(List<Role> roles, Objective.Task task) {
        return !roles.isEmpty() && !(task instanceof Objective.Task.None) && !(task instanceof Objective.Task.PatrolBase)
                && roles.stream().allMatch(r -> r != Role.RIFLEMAN && !r.task().equals(Optional.of(task)));
    }

    /** The squad's CANTCO when nobody is free for its task: who has which job, so the platoon leader can send another squad. */
    static Report noneFree(Map<String, Role> roles, Objective objective) {
        StringBuilder jobs = new StringBuilder();
        new java.util.TreeMap<>(roles).forEach((name, role) -> jobs.append(jobs.isEmpty() ? "" : ", ")
                .append(name).append(' ').append(role.name().toLowerCase(java.util.Locale.ROOT)));
        return new Report.Cantco("no soldier free for \"" + objective.mission()
                + "\": every soldier has a standing job of its own (" + jobs + ")");
    }

    /** Whether the squad is all inside its sealed patrol base - nothing for its squad leader to decide. */
    private volatile boolean holdingBase;

    Optional<SquadOrder> order() {
        return Optional.ofNullable(order);
    }

    /** The platoon leader's objective for the squad, once it has one. */
    Optional<Objective> objective() {
        return Optional.ofNullable(objective);
    }

    /** What the squad's objective has it gather, and around where - if anything. */
    Optional<Objective> gatherObjective() {
        Objective current = objective;
        return current != null && current.gather().isPresent() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> treeFarmObjective() {
        Objective current = objective;
        return current != null && current.treeFarm() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> fortifyObjective() {
        Objective current = objective;
        return current != null && current.fortify() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> mineObjective() {
        Objective current = objective;
        return current != null && current.mine() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> patrolObjective() {
        Objective current = objective;
        return current != null && current.patrol() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> secureObjective() {
        Objective current = objective;
        return current != null && current.secure() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> farmObjective() {
        Objective current = objective;
        return current != null && current.farm() ? Optional.of(current) : Optional.empty();
    }

    Optional<Objective> patrolBaseObjective() {
        Objective current = objective;
        return current != null && current.patrolBase() ? Optional.of(current) : Optional.empty();
    }

    boolean hasObjective() {
        return objective != null;
    }

    /** The principal whose escort ended since last asked - out of sight too long - once. */
    Optional<String> takeEscortEnded() {
        String ended = escortEnded;
        escortEnded = null;
        return Optional.ofNullable(ended);
    }

    Optional<String> escort() {
        return Optional.ofNullable(escort);
    }

    /** When the squad leader last gave the squad an order (a FRAGO), in epoch millis: -1 before its first. */
    long lastFrago() {
        return lastFragoMillis;
    }

    private volatile long lastFragoMillis = -1;

    void escort(String principal) {
        escort = principal;
        principalOutOfSight = false;
    }

    /** True the first time the squad leader's own report turns to CANTCO - the platoon leader's early trigger. */
    boolean newCantco() {
        Report report = report();
        boolean fresh = report instanceof Report.Cantco && !(reportAnswered instanceof Report.Cantco);
        reportAnswered = report;
        return fresh;
    }

    void receiveObjective(Objective given) {
        squadLeader.receiveObjective(given);
        objective = given;
        newObjective = true;
    }

    /**
     * A SITREP from this squad's soldiers: decides whether its squad leader should think now - on a new objective,
     * at its {@link Pace}'s routine look, or on a soldier's new CANTCO or contact once its pace's gap has passed.
     */
    void onSitrep(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain, Optional<Position> principalAt,
                  boolean holdingBase, long now) {
        this.holdingBase = holdingBase;
        contact = contact.seen(squad, now);
        squadLeader.receiveContact(contact.under());
        // Kept before anything returns: a contact that begins on a SITREP with no decision is still to be reported.
        Set<String> contacts = squad.stream().filter(s -> s.report() instanceof Report.InContact)
                .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
        Set<String> began = contact.began();
        Set<String> unanswered = contactsUnanswered;
        contactsUnanswered = contacts.stream().filter(s -> began.contains(s) || unanswered.contains(s)).collect(Collectors.toUnmodifiableSet());
        if (holdingBase) {
            return; // the order is being met: no decision, and no terrain survey from inside a box
        }
        String principal = escort;
        if (principal != null) {
            followPrincipal(principal, principalAt, now);
        }
        Set<String> cantcos = squad.stream().filter(s -> s.report() instanceof Report.Cantco)
                .map(SoldierStatus::name).collect(Collectors.toUnmodifiableSet());
        boolean newSoldierCantco = !cantcosAnswered.containsAll(cantcos);
        // A soldier newly in contact is a contact report: the squad leader decides now whether to
        // fight, bypass or break contact, not at its next scheduled look. Newly, to a rule, is by the squad's contact
        // (SquadContact): one in and out of it between a contact's bursts is in the one contact, reported once. To a
        // model it is as it was - one in contact who wasn't at its last decision: its gap keeps the bursts apart.
        boolean newSoldierContact = pace.settlesContact() ? !contactsUnanswered.isEmpty() : !contactsAnswered.containsAll(contacts);
        Objective toward = objective;
        if (toward != null && (newObjective || now - lastDecisionMillis >= pace.periodMillis()
                || ((newSoldierCantco || newSoldierContact) && now - lastDecisionMillis >= pace.reportGapMillis()))) {
            // The ground the decision reads: a model's own survey as it decides, a rule's the last one kept for this objective.
            Optional<Supplier<TerrainSurvey>> ground = pace.survey(surveyed, name + "'s terrain survey", terrain, point(squad, toward), toward, now,
                    thread, faults);
            if (ground.isEmpty()) {
                // A rule's survey for this objective isn't in: the next SITREP decides on it. One that failed is tried again on
                // the survey's clock, and till then there's no decision, as a model's has none when its survey fails: ground not
                // surveyed isn't ground that can't be reached, and deciding on it would send a CANTCO up that nothing bears out.
                return;
            }
            if (newSoldierContact && !newObjective) {
                LOG.info("[{}] contact report: {} in contact - the squad leader decides now", name, contacts);
            }
            // A routine look, with nothing new to answer, waits while the models are backed up - a model's does.
            boolean routine = !newObjective && !newSoldierCantco && !newSoldierContact;
            if (routine && pace.sheds(modelCalls, name + "'s squad leader")) {
                return;
            }
            cantcosAnswered = cantcos;
            contactsUnanswered = Set.of();
            contactsAnswered = contacts;
            decide(squad, ground.get(), now);
        }
    }

    /**
     * While escorting, the squad leader's objective is the principal's position - re-issued once
     * the principal has moved {@link #ESCORT_RETASK_DISTANCE} from it, so the squad leader's view
     * keeps up without a decision every second (the escort drill follows the live position in
     * between). A principal nobody in the squad can see keeps the last objective.
     */
    private void followPrincipal(String principal, Optional<Position> principalAt, long now) {
        if (principalAt.isEmpty()) {
            if (principalOutOfSight && now - principalSeenMillis >= ESCORT_LOST_MILLIS) {
                LOG.warn("[{}] {} out of sight for {} s - the escort ends", name, principal, ESCORT_LOST_MILLIS / 1000);
                escort = null;
                escortEnded = principal;
                return;
            }
            if (!principalOutOfSight) {
                principalSeenMillis = now;
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
        LOG.info("[{}] escort objective follows {} to {}", name, principal, DayWorkRules.at(at));
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
        boolean answersObjective = newObjective;
        newObjective = false;
        Pace.Deciding deciding = pace.deciding(name + "'s squad leader", modelCalls, faults);
        deciding.run(thread, () -> {
            deciding.started();
            try {
                give(squad, terrain, answersObjective);
            } catch (RuntimeException e) {
                deciding.failed(e, () -> LOG.error("[{}] no decision - squad keeps its last order: {}", name, e.getMessage(), e));
            } finally {
                deciding.finished();
                busy.set(false);
            }
        });
    }

    /**
     * Asks the squad leader, and gives the squad its order - if it is one. An order that stands ({@link Pace#stands})
     * is neither said, logged nor given again: the squad loop works each order object's plan changes once, and the same
     * order a second time would be worked again. Its CANTCO goes up with each order said, and when it first has one.
     */
    private void give(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain, boolean answersObjective) {
        long started = clockwork.millis();
        TerrainSurvey survey = terrain.get();
        long reachable = survey.samples().stream().filter(TerrainSurvey.Sample::reachable).count();
        squadLeader.decide(squad, survey).ifPresent(decided -> {
            boolean stands = pace.stands(decided, order, answersObjective);
            if (!stands) {
                LOG.info("[{} -> squad] ({} ms, {}, terrain {}/{} reachable) FRAGO: move to ({}, {}, {}) in {} at {} interval, {} risk{}. Intent: {}",
                        name, clockwork.millis() - started, ChainOfCommand.phase(time), reachable, survey.samples().size(),
                        Math.round(decided.objective().x()), Math.round(decided.objective().y()), Math.round(decided.objective().z()),
                        decided.formation(), decided.interval(), decided.risk(), soldierRisk(decided), decided.intent());
                radio.accept("[" + name + " -> squad] FRAGO: " + decided.formation() + " to " + DayWorkRules.at(decided.objective())
                        + ", " + decided.risk() + " risk" + soldierRisk(decided) + ". Intent: " + decided.intent());
            }
            Report up = squadLeader.report();
            if (up instanceof Report.Cantco cantco && (!stands || !cantcoSaid)) {
                LOG.warn("[{} -> PL] CANTCO: {}", name, cantco.reason());
                radio.accept("[" + name + " -> PL] CANTCO: " + cantco.reason());
            }
            cantcoSaid = up instanceof Report.Cantco;
            if (!stands) {
                order = decided;
            }
            lastFragoMillis = clockwork.millis(); // the order confirmed is the order given: an order's trace waits on this
        });
    }

    /**
     * Where the squad's survey is taken from, near enough to tell how far that has moved: the soldier nearest the
     * objective across the ground who isn't being recovered. The squad loop picks its point man the same way, within a
     * height of the objective besides - so this is at most a soldier's place in the formation off.
     */
    private static Position point(List<SoldierStatus> squad, Objective toward) {
        return squad.stream().filter(s -> !(s.report() instanceof Report.Recovering)).map(SoldierStatus::position)
                .min(java.util.Comparator.comparingDouble(p -> Cohesion.distance(p, toward.location())))
                .orElse(squad.isEmpty() ? toward.location() : squad.getFirst().position());
    }

    /** ", Carol at MEDIUM risk" for each per-soldier override, in name order; empty if none. */
    private static String soldierRisk(SquadOrder order) {
        return order.soldierRisk().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> ", " + e.getKey() + " at " + e.getValue() + " risk").collect(Collectors.joining());
    }

    /** Stops the squad leader's thread - a decision in flight is abandoned. */
    void stop() {
        thread.shutdownNow();
    }
}
