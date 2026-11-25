package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A squad leader that decides by standing rules, no model (backlog/fleet-command.md: the squad
 * leader's allocation becomes a deterministic one, as the foreman shares out the keep). Each rule
 * is one the model was told in words and followed most of the time; here it's followed always:
 * <ul>
 *   <li><b>Where:</b> the objective itself on the keep; else the surveyed spot nearest it that the
 *       point man can walk to, or failing any, climb to - asked again each decision from where he
 *       stands, so a far objective is reached spot by spot. Not the objective itself when it's beyond
 *       the survey: behind a cliff or a ravine, that's an order nobody can reach.</li>
 *   <li><b>How:</b> WEDGE, the default for moving; FILE once the point man reports his route
 *       blocked (ATP 3-21.8: single file through close terrain). TIGHT at night and in a patrol
 *       base, NORMAL by day.</li>
 *   <li><b>Risk:</b> LOW, and MEDIUM for one soldier whose CANTCO says a MEDIUM route exists - a
 *       stuck soldier freed for half his health at most.</li>
 *   <li><b>Rearm:</b> by day, out of contact - the squad's, over once no soldier has been in one for the settle time
 *       ({@link SquadContact}) - and with no task - the squad holding. A squad given work does the work
 *       (operator, 2026-11-22: soldiers do nothing without a command): its soldiers climb no kit ladder on a task, only
 *       make the weapon or the pickaxe they have none of.</li>
 *   <li><b>Task organisation:</b> plans as they stand, but for two: on a mission's ground work - a flatten, a floor, a
 *       supply point's template - every soldier detached on a role comes back to the squad; and with every soldier detached on a
 *       role, the one nearest the objective comes back to the squad as a rifleman - live, a squad
 *       of four miners left the platoon leader's FORTIFY to nobody all day.</li>
 * </ul>
 * It reports CANTCO only when no surveyed spot can be reached at all. What it can't do - a
 * detachment asked for in the mission's words, "post Bob as a lookout" - it doesn't guess at: the
 * platoon leader gives detachments as data (backlog/fleet-command.md).
 */
public final class DrillSquadLeader implements SquadLeader, DecidesByRules {

    /** How far above or below the point's height the squad may end up. */
    static final double VERTICAL_TOLERANCE = 3;

    private final String name;
    private volatile Objective objective;
    private volatile TimeOfDay time;
    private volatile Map<String, Plan> plans = Map.of();
    private volatile Report report = new Report.Idle();
    private volatile boolean squadInContact;
    /** Where the squad holds while no surveyed ground toward {@link #heldFor} can be reached: a place, kept till some can. */
    private volatile Position heldAt;
    private volatile Objective heldFor;

    public DrillSquadLeader(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void receiveObjective(Objective objective) {
        this.objective = objective;
    }

    @Override
    public void receiveTime(TimeOfDay time) {
        this.time = time;
    }

    @Override
    public void receiveContact(boolean squadInContact) {
        this.squadInContact = squadInContact;
    }

    @Override
    public void receivePlans(Map<String, Plan> plans, Map<String, String> workOrders) {
        this.plans = Map.copyOf(plans);
    }

    @Override
    public Report report() {
        return report;
    }

    @Override
    public Optional<SquadOrder> decide(List<SoldierStatus> soldiers, TerrainSurvey terrain) {
        Objective current = objective;
        if (current == null || soldiers.isEmpty()) {
            return Optional.empty();
        }
        Optional<Position> point = point(current, terrain);
        report = point.isPresent() ? new Report.Moving() : new Report.Cantco("no surveyed ground the squad can reach toward the objective");
        // With nowhere to go the squad holds where its point man stood when that was found, not where he stands each
        // time it's asked: asked every SITREP, an order that followed him would be a new order each second.
        if (point.isPresent()) {
            heldAt = null;
        } else if (heldAt == null || !current.equals(heldFor)) {
            heldAt = soldiers.getFirst().position();
            heldFor = current;
        }
        Position at = point.orElse(heldAt);
        boolean night = time != null && time.dark();
        // The squad's contact, over only once it has settled: a soldier's own report goes on and off between a contact's bursts.
        boolean inContact = squadInContact || soldiers.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        boolean blocked = soldiers.getFirst().report() instanceof Report.Cantco;
        Formation formation = blocked && !(current.task() instanceof Objective.Task.Keep) ? Formation.FILE : Formation.WEDGE;
        Interval interval = night || current.patrolBase() ? Interval.TIGHT : Interval.NORMAL;
        Map<String, Risk> soldierRisk = new HashMap<>();
        soldiers.stream().skip(1).filter(s -> s.report() instanceof Report.Cantco(String why) && why.contains("(MEDIUM risk)"))
                .forEach(s -> soldierRisk.put(s.name(), Risk.MEDIUM));
        boolean rearm = !night && !inContact && current.task() instanceof Objective.Task.None;
        return Optional.of(new SquadOrder(at, formation, interval, VERTICAL_TOLERANCE, Risk.LOW, soldierRisk, rearm, current.intent(),
                changes(soldiers, current)));
    }

    /** Where the squad moves: the objective, or the reachable spot nearest it in the survey - empty when none is. */
    static Optional<Position> point(Objective objective, TerrainSurvey terrain) {
        Position goal = objective.location();
        // A tree farm's ground is at its objective, under its trees: the survey's ground there is the canopy's top, and a
        // squad sent to that climbs it on steps it leaves standing on the farm.
        if (objective.task() instanceof Objective.Task.Keep || objective.task() instanceof Objective.Task.TreeFarm) {
            return Optional.of(goal);
        }
        // Walked to before climbed to: a spot reached only by pillaring costs cobblestone and time, and
        // is taken only when nothing is reachable on foot.
        List<TerrainSurvey.Sample> reachable = Stream.concat(terrain.atObjective().stream(), terrain.samples().stream())
                .filter(s -> s.ground() == TerrainSurvey.Ground.LAND && s.reachable()).toList();
        List<TerrainSurvey.Sample> walked = reachable.stream().filter(s -> s.climb() == 0).toList();
        return (walked.isEmpty() ? reachable : walked).stream()
                // Nearest counting height too: a MINE objective's height is the depth to reach, and by x and
                // z alone, live, the squad was sent up a hill 66 blocks over the mine it was to go down.
                .min(Comparator.comparingDouble(s -> Math.hypot(Math.hypot(s.x() + 0.5 - goal.x(), s.z() + 0.5 - goal.z()), s.height() - goal.y())))
                .map(s -> new Position(s.x() + 0.5, s.height(), s.z() + 0.5));
    }

    /**
     * One soldier back to the squad when every one is detached on a role; and on a mission's ground work - a flatten, a
     * floor, a supply point's template, tasks only a mission gives - every one detached back: the work is the whole
     * squad's (operator, 2026-11-22). Live (field696), a tree farm's flatten had two of Bravo's four for days, one a miner
     * detached under an older order and still down a mine 80 blocks off.
     */
    private List<Plan.Change> changes(List<SoldierStatus> soldiers, Objective current) {
        Map<String, Plan> kept = plans;
        if (current.task() instanceof Objective.Task.Keep || current.task() instanceof Objective.Task.Floor || current.task() instanceof Objective.Task.Stores) {
            return soldiers.stream().filter(s -> kept.getOrDefault(s.name(), Plan.RIFLEMAN).role() != Role.RIFLEMAN)
                    .map(s -> Plan.Change.role(s.name(), Role.RIFLEMAN, current.location())).toList();
        }
        boolean allDetached = soldiers.stream().allMatch(s -> kept.getOrDefault(s.name(), Plan.RIFLEMAN).role() != Role.RIFLEMAN);
        if (!allDetached) {
            return List.of();
        }
        Position goal = current.location();
        return soldiers.stream().min(Comparator.comparingDouble(s -> Math.hypot(s.position().x() - goal.x(), s.position().z() - goal.z())))
                .map(s -> List.of(Plan.Change.role(s.name(), Role.RIFLEMAN, goal))).orElse(List.of());
    }
}
