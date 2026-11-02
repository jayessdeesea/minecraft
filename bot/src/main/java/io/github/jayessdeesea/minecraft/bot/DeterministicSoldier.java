package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (AGENTS.md's "Behavior modeling" section). {@code app}'s {@code SoldierRunner} is
 * the imperative shell: it calls {@link #tick} at 20Hz and reports the resulting position to the
 * server; tests call it directly with a fake {@code WorldView}.
 *
 * <p>{@code position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
public final class DeterministicSoldier implements Soldier {

    /** Blocks per tick - 4 blocks/s at 20Hz, just under vanilla walk speed (~4.3), not tuned.
     * Revisit if the server starts correcting reported positions (it would send Player Position). */
    private static final double STEP = 0.2;
    /** Sprinting, vanilla's 5.6 blocks/s: used when the goal is more than {@link #SPRINT_DISTANCE} away. */
    static final double SPRINT_STEP = 0.28;
    /** Far enough from its goal that a soldier sprints to catch up - a moving principal, a distant slot. */
    static final double SPRINT_DISTANCE = 8;
    /** At this food level or below a player can't sprint. */
    static final int SPRINT_FOOD_FLOOR = 6;

    /**
     * Whether the soldier is sprinting - to be told to the server, which charges sprinting's food cost
     * (0.1 exhaustion a metre) only to a player it knows is sprinting.
     */
    public boolean sprinting() {
        return sprinting;
    }

    // Vanilla player physics constants (LivingEntity): per tick, velocity -= 0.08 then *= 0.98;
    // a jump starts at 0.42 (peaks ~1.25 blocks - enough for one block, not two).
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double BODY_HEIGHT = 1.8;
    private static final double HALF_WIDTH = 0.3; // a player's box is 0.6 x 1.8 x 0.6
    private static final double EPSILON = 1e-9;

    private final String name;
    private volatile Position position;
    private volatile Order order = new Order.Idle();
    private final AtomicReference<Position> pendingCorrection = new AtomicReference<>();
    // Tick-loop-only, like position's writes - no other thread reads it.
    private double verticalVelocity = 0;
    private volatile boolean onGround = true;
    private volatile Report task;
    // Look direction, written by the tick loop, read by the caller reporting it.
    private volatile float yaw;
    private volatile float pitch;
    private int ticksUntilAttack = 0; // tick-loop only
    // Written only by the connection's thread (reportHealth), as position is only by the tick loop.
    private volatile Condition condition;
    /** Whether the last step was a sprint - tick thread writes, its caller reads. */
    private volatile boolean sprinting;
    private volatile Kit kit; // written only by the connection's thread (reportKit)
    private double lastReportedHealth = -1;
    private int deaths;
    // Set by the connection's thread on a hit, consumed by the tick loop.
    private final java.util.concurrent.atomic.AtomicBoolean hurt = new java.util.concurrent.atomic.AtomicBoolean();
    private int underFireTicks; // tick-loop only

    public DeterministicSoldier(String name, Position startingPosition) {
        this.name = name;
        this.position = startingPosition;
    }

    @Override
    public void receiveOrder(Order order) {
        this.order = order;
    }

    /**
     * The server says this soldier is somewhere else (a respawn, or a correction of a reported
     * move) - authoritative, so it wins. Applied at the start of the next {@link #tick} rather than
     * written here, so the tick loop stays the sole writer of {@code position}.
     */
    public void correctPosition(Position serverPosition) {
        pendingCorrection.set(serverPosition);
    }

    /**
     * The server's health and food for this soldier. Counts a death when health drops to 0 from
     * alive; a soldier already dead when it joins died in an earlier run, so that isn't counted.
     * Called from the connection's thread only - the sole writer of the condition fields.
     */
    public void reportHealth(double health, int food) {
        if (health <= 0 && lastReportedHealth > 0) {
            deaths++;
        } else if (health > 0 && health < lastReportedHealth) {
            hurt.set(true);
        }
        lastReportedHealth = health;
        condition = new Condition(health, food, deaths, java.util.Optional.empty());
    }

    /**
     * What the soldier is busy with besides its order - {@code Rearming} while it makes itself a
     * weapon, {@code Gathering} while it collects material ({@code bot.supply}) - reported instead of
     * its movement unless it's in contact; null when it's only following its order. Set by whoever
     * runs the routine.
     */
    public void task(Report task) {
        this.task = task;
    }

    /** What the soldier now carries (LACE ammunition and equipment). Connection thread only. */
    public void reportKit(Kit kit) {
        this.kit = kit;
    }

    /**
     * Where the soldier looks, reported with each move so other players see it turn: at the
     * hostile it's fighting, else along its last step, else wherever it last looked. Yaw in
     * degrees, 0 facing south (+z) and 90 west (-x); pitch negative looking up.
     */
    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Eye height of a standing player, and the height a hostile is looked at - roughly its chest. */
    public static final double EYE_HEIGHT = 1.62;
    public static final double TARGET_HEIGHT = 1.2;

    /** Yaw toward a horizontal direction, in Minecraft's convention. */
    static float yawToward(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    /** Whether the last tick ended standing on something - reported to the server with each move. */
    public boolean onGround() {
        return onGround;
    }

    @Override
    public SoldierStatus status() {
        Kit carried = kit;
        Report busy = task;
        Report now = busy != null && !(report instanceof Report.InContact) ? busy : report;
        return new SoldierStatus(name, position, order, now, java.util.Optional.ofNullable(condition)
                .map(c -> new Condition(c.health(), c.food(), c.deaths(), java.util.Optional.ofNullable(carried))));
    }

    /**
     * Ballpark starting value, not tuned against real mob behavior - vanilla hostiles typically
     * notice a player around 16 blocks out, so this reacts well inside that. Revisit once a live
     * run shows soldiers reacting too late or too skittishly.
     */
    public static final double CONTACT_RADIUS = 8.0;

    /**
     * Centre-to-centre distance a soldier swings at. The server's check (eye to target hitbox,
     * {@code isWithinAttackRange}) is more generous; staying inside it avoids attacks it would
     * silently drop.
     */
    public static final double ATTACK_REACH = 3.0;

    /**
     * Closer than this, back off: outside a zombie's own reach (about 1.5) but inside ours, so a
     * soldier striking each time its weapon recharges, with knockback, keeps it from landing a
     * hit. Between this and {@link #ATTACK_REACH} the soldier holds its ground.
     */
    static final double KEEP_AWAY = 2.5;

    /**
     * Losing health with no hostile in contact radius means being shot at - a skeleton holds 11 to
     * 15 blocks off ({@code RangedBowAttackGoal}). For this long after a hit the soldier looks out
     * to {@link #UNDER_FIRE_RADIUS} for the shooter and goes for it.
     */
    static final int UNDER_FIRE_TICKS = 100;
    /** Just beyond a skeleton's 15-block bow range. */
    public static final double UNDER_FIRE_RADIUS = 16;

    /**
     * One tick of movement. First the "actions on contact" reflex: if a hostile is within {@link
     * #CONTACT_RADIUS}, the current order is set aside and the soldier fights the nearest one at
     * arm's length - closing to {@link #ATTACK_REACH}, striking whenever the attack has recharged,
     * backing off inside {@link #KEEP_AWAY}, holding its ground in between. A creeper is the
     * exception: the soldier gets away from it. Decided here at tick speed with no squad leader
     * involved. Running from a zombie can't break contact - it chases as fast as a soldier walks -
     * which is why the reflex holds rather than flees; the squad's react-to-contact drill brings
     * help ({@code command.ReactToContact}). A blocked step means standing still, never falling back
     * to the order (which might lead toward the threat). A hit with nothing in contact radius means
     * ranged fire: for {@link #UNDER_FIRE_TICKS} the soldier treats the nearest hostile within
     * {@link #UNDER_FIRE_RADIUS} as its contact.
     *
     * <p>Otherwise follows a route to the current order's goal - planned by {@link Pathfinder}
     * around trees, up one-block steps and down short drops, and re-planned when the order
     * changes, when the soldier stops making progress, or every {@link #REPLAN_TICKS} ticks (the
     * world changes, and a far goal is reached in budget-sized legs). A step needs room for the
     * whole ~1.8-block body; a one-block rise is jumped.
     *
     * <p>Then vertical movement with vanilla gravity: the server kicks a client that reports
     * standing while hovering over lower ground ("floating too long"), so falling is not optional.
     *
     * @return the entity to attack this tick, if any - the caller sends it, keeping this decision
     *     free of I/O like the rest of the tick
     */
    public OptionalInt tick(WorldView world) {
        if (ticksUntilAttack > 0) {
            ticksUntilAttack--;
        }

        Position correction = pendingCorrection.getAndSet(null);
        if (correction != null) {
            position = correction;
            verticalVelocity = 0;
            onGround = false; // unknown until gravity settles it - and no route is planned mid-air
            route = null;
            return OptionalInt.empty();
        }

        OptionalInt attack = OptionalInt.empty();
        sprinting = false; // only a long move sprints; fighting and short moves walk
        Position before = position;
        if (hurt.getAndSet(false)) {
            underFireTicks = UNDER_FIRE_TICKS;
        }
        List<TrackedEntity> threats = visible(world, world.nearbyHostiles(position, CONTACT_RADIUS));
        if (threats.isEmpty() && underFireTicks > 0) {
            // Hit with nothing in reach: someone is shooting - find the shooter.
            threats = visible(world, world.nearbyHostiles(position, UNDER_FIRE_RADIUS));
        }
        if (underFireTicks > 0) {
            underFireTicks--;
        }
        if (!threats.isEmpty()) {
            TrackedEntity nearest = threats.get(0);
            Position threat = nearest.position();
            double range = distance(position, threat);
            if (nearest.explodes()) {
                // A creeper is never fought at arm's length: get clear of the blast.
                stepToward(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
            } else if (nearest.shoots() && !armedForMelee()) {
                // An archer out-fights a bare hand from any range: get out of its sight, and stay out.
                Optional<Position> cover = coverFrom(world, threat);
                if (cover.isPresent()) {
                    stepToward(world, cover.get().x(), cover.get().z());
                } else {
                    stepToward(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
                }
                holdCoverTicks = UNDER_FIRE_TICKS;
            } else {
                if (ticksUntilAttack == 0 && range <= ATTACK_REACH) {
                    attack = OptionalInt.of(nearest.entityId());
                    // A hit's damage scales with the swing's charge: wait for the held weapon's full one.
                    Kit carried = kit;
                    ticksUntilAttack = WeaponStats.melee(carried == null ? Optional.empty() : carried.weapon()).ticksToCharge();
                }
                if (range < KEEP_AWAY) {
                    stepToward(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
                } else if (range > ATTACK_REACH) {
                    stepToward(world, threat.x(), threat.z());
                }
            }
            route = null; // wherever the fight ends up, the old route no longer starts here
            report = new Report.InContact(threats.size());
            look(threat);
        } else if (holdCoverTicks > 0) {
            // In cover from an archer it can't fight: out of its sight isn't out of its range. Hold.
            holdCoverTicks--;
        } else {
            navigate(world);
            double dx = position.x() - before.x();
            double dz = position.z() - before.z();
            if (dx * dx + dz * dz > 1e-4) {
                yaw = yawToward(dx, dz);
                pitch = 0;
            }
        }

        applyVerticalMovement(world);
        return attack;
    }

    /** Ticks left holding cover from an archer, once out of its sight. */
    private int holdCoverTicks;
    /** How far a soldier looks for cover from an archer. */
    static final int COVER_RADIUS = 6;

    /** Whether the soldier holds a melee weapon - with only its hands (or a bow), an archer out-fights it. */
    private boolean armedForMelee() {
        Kit carried = kit;
        return carried == null || WeaponStats.melee(carried.weapon()) != WeaponStats.HAND;
    }

    /**
     * The nearest standing cell within {@link #COVER_RADIUS} that {@code shooter} can't see - nothing
     * solid between its eyes and the soldier's - if there's one.
     */
    private Optional<Position> coverFrom(WorldView world, Position shooter) {
        Position shooterEye = new Position(shooter.x(), shooter.y() + EYE_HEIGHT, shooter.z());
        BlockPos here = BlockPos.containing(position.x(), position.y() + EPSILON, position.z());
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -COVER_RADIUS; dx <= COVER_RADIUS; dx++) {
            for (int dz = -COVER_RADIUS; dz <= COVER_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = here.offset(dx, dy, dz);
                    if (!Pathfinder.standable(world, cell)) {
                        continue;
                    }
                    Position spot = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    double distance = Math.hypot(spot.x() - position.x(), spot.z() - position.z());
                    if (distance < bestDistance
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + EYE_HEIGHT, spot.z()))
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + 0.5, spot.z()))) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Turns to face a squadmate standing at {@code target} - before handing something across, which
     * flies the way the soldier faces. The next step or fight turns it again.
     */
    public void lookAt(Position target) {
        look(target);
    }

    /**
     * The hostiles this soldier can see: a hostile behind a wall can't reach it and can't be
     * reached, so it isn't contact - a squad sealed in its patrol base isn't fighting the night
     * outside. Vanilla mobs likewise need a line of sight to target a player.
     */
    private List<TrackedEntity> visible(WorldView world, List<TrackedEntity> hostiles) {
        Position eye = new Position(position.x(), position.y() + EYE_HEIGHT, position.z());
        return hostiles.stream().filter(hostile -> lineOfSight(world, eye,
                new Position(hostile.position().x(), hostile.position().y() + TARGET_HEIGHT, hostile.position().z()))).toList();
    }

    /** Sampling step along a sight line: fine enough not to step over the corner of a block. */
    static final double SIGHT_STEP = 0.2;

    /** Whether no solid block lies on the straight line from {@code from} to {@code to}. */
    public static boolean lineOfSight(WorldView world, Position from, Position to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / SIGHT_STEP);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            if (world.isSolid((int) Math.floor(from.x() + dx * t), (int) Math.floor(from.y() + dy * t),
                    (int) Math.floor(from.z() + dz * t))) {
                return false;
            }
        }
        return true;
    }

    private void look(Position target) {
        double dx = target.x() - position.x();
        double dz = target.z() - position.z();
        double dy = target.y() + TARGET_HEIGHT - (position.y() + EYE_HEIGHT);
        yaw = yawToward(dx, dz);
        pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    /**
     * Partial plans in a row that gained no ground before this soldier reports CANTCO (unable to
     * comply, docs/military-doctrine.md §3): stuck in a pit, cut off by water, objective on a ledge.
     * It keeps re-planning - the terrain or the order may change - but stops pretending it's
     * on the way.
     */
    static final int CANTCO_AFTER_FAILED_PLANS = 3;

    /** How far an order's goal must move to count as a new task (resetting CANTCO), not slot drift. */
    static final double RETASK_DISTANCE = 8;

    /** Re-plan at least this often while moving - 2 seconds. */
    static final int REPLAN_TICKS = 40;
    /** Ticks without real progress along the route before re-planning. */
    private static final int STUCK_TICKS = 20;
    /** Cell visits per plan - bounds the planning cost per tick (see {@link Pathfinder}). */
    private static final int PLAN_BUDGET = 4000;
    private static final double WAYPOINT_REACHED = 0.25;

    // Navigation state - tick-loop only, like verticalVelocity.
    private Order plannedOrder;
    private Goal goal;
    private List<BlockPos> route;
    private int routeIndex;
    private int ticksSincePlan;
    private int stuckTicks;
    private Position progressMark;
    private volatile String lastPlan = "none";
    private volatile Report report = new Report.Idle();
    /** Consecutive partial plans with no real headway - see {@link #CANTCO_AFTER_FAILED_PLANS}. */
    private int failedPlans;
    private Position lastPartialPlanFrom;

    /** Observability only: the most recent route plan, e.g. "partial, 12 steps ending BlockPos[...]". */
    public String lastPlan() {
        return lastPlan;
    }

    private void navigate(WorldView world) {
        Order current = this.order;
        if (current != plannedOrder) {
            plannedOrder = current;
            Goal previous = goal;
            goal = goalFor(current);
            route = null;
            if (previous == null || goal == null
                    || Math.hypot(goal.x() - previous.x(), goal.z() - previous.z()) > RETASK_DISTANCE) {
                // A genuinely different task: earlier failures say nothing about this one. A slot
                // drifting with the point man is the same task, so its failures keep counting.
                failedPlans = 0;
                lastPartialPlanFrom = null;
            }
        }
        // Arrived = standing in a cell the goal accepts - the same cell-centre rule the planner uses,
        // so the two can never disagree about whether there's anywhere left to go.
        if (goal == null) {
            route = null;
            report = new Report.Idle();
            return;
        }
        if (onGround && goal.contains(BlockPos.containing(position.x(), position.y(), position.z()))) {
            route = null;
            failedPlans = 0;
            // A goal finer than half a block wants a spot, not just a cell - inside a hut a body off
            // its cell's centre leans into the wall being built. Step onto it before standing.
            if (goal.horizontalTolerance() < 0.5 && !goal.contains(position.x(), position.y(), position.z())) {
                stepToward(world, goal.x(), goal.z());
                report = new Report.Moving();
                return;
            }
            report = new Report.InPosition();
            return;
        }

        ticksSincePlan++;
        trackProgress();
        boolean replan = route == null || routeIndex >= route.size()
                || ticksSincePlan >= REPLAN_TICKS || stuckTicks >= STUCK_TICKS;
        if (replan && onGround) {
            boolean stuck = stuckTicks >= STUCK_TICKS;
            Pathfinder.Result plan = Pathfinder.find(world, BlockPos.containing(position.x(), position.y(), position.z()), goal,
                    PLAN_BUDGET, fallAllowance(current), canPillar());
            route = plan.path();
            pillarFrom = null;
            assessPlan(world, plan, stuck);
            lastPlan = (plan.reachesGoal() ? "to goal" : "partial") + ", " + route.size() + " steps"
                    + (route.isEmpty() ? "" : " ending " + route.getLast());
            routeIndex = 0;
            ticksSincePlan = 0;
            stuckTicks = 0;
        }
        if (route == null || routeIndex >= route.size()) {
            return; // nothing reachable makes headway - wait for the next re-plan
        }

        BlockPos waypoint = route.get(routeIndex);
        BlockPos here = BlockPos.containing(position.x(), position.y() + EPSILON, position.z());
        if (waypoint.x() == here.x() && waypoint.z() == here.z() && waypoint.y() == here.y() + 1
                || pillarFrom != null && waypoint.equals(pillarFrom.offset(0, 1, 0))) {
            pillar(world, waypoint);
            return;
        }
        double centreX = waypoint.x() + 0.5;
        double centreZ = waypoint.z() + 0.5;
        if (Math.hypot(centreX - position.x(), centreZ - position.z()) < WAYPOINT_REACHED
                && Math.abs(waypoint.y() - position.y()) < 0.6) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                return;
            }
            waypoint = route.get(routeIndex);
            centreX = waypoint.x() + 0.5;
            centreZ = waypoint.z() + 0.5;
        }
        boolean far = Math.hypot(goal.x() - position.x(), goal.z() - position.z()) > SPRINT_DISTANCE;
        // Vanilla won't let a player sprint at food 6 or below; the server charges for every metre.
        Condition known = condition;
        sprinting = far && (known == null || known.food() > SPRINT_FOOD_FLOOR);
        stepToward(world, centreX, centreZ, sprinting ? SPRINT_STEP : STEP);
    }

    /** The cell a pillar step rises from, while one is under way. */
    private BlockPos pillarFrom;
    /** A block to place this tick, under the soldier - tick thread writes, its caller takes it. */
    private BlockPos placement;

    /**
     * One tick of a pillar step to {@code top}: jump from the cell below it, and once the feet are
     * clear of that cell - the server won't place a block where a body is - ask for a block there,
     * then land on it.
     */
    private void pillar(WorldView world, BlockPos top) {
        BlockPos from = top.offset(0, -1, 0);
        if (pillarFrom == null) {
            if (!onGround) {
                return;
            }
            pillarFrom = from;
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
            return;
        }
        if (world.isSolid(from.x(), from.y(), from.z())) {
            if (onGround) {
                pillarFrom = null; // standing on it: the step is done
                routeIndex++;
            }
            return;
        }
        if (position.y() >= from.y() + 1) {
            placement = from;
        } else if (onGround) {
            pillarFrom = null; // landed back where it started - the block never went in; try again
        }
    }

    /**
     * Whether a pillar step can be planned: the soldier carries cobblestone to stand on. Logs are
     * kept for tools.
     */
    private boolean canPillar() {
        Kit carried = kit;
        return carried != null && carried.stone() > 0;
    }

    /**
     * The block to place this tick, if a pillar step wants one - taken, so it's asked for once. The
     * caller places it after reporting the soldier's new height, and adds it to the world model so
     * the soldier can land on it before the server confirms.
     */
    public Optional<BlockPos> takePlacement() {
        BlockPos taken = placement;
        placement = null;
        return Optional.ofNullable(taken);
    }

    /**
     * A re-plan forced by lack of progress counts as a failure even if the new route looks fine on
     * paper: the planner can't see everything that stops a body (an off-grid snag, an entity in the
     * way), and a soldier that keeps re-planning the same route without moving must eventually say
     * so rather than report "Moving" forever.
     */
    private void assessPlan(WorldView world, Pathfinder.Result plan, boolean stuck) {
        if (stuck) {
            failedPlans++;
        } else if (plan.reachesGoal()) {
            failedPlans = 0;
            lastPartialPlanFrom = null;
        } else {
            boolean gainedGround = lastPartialPlanFrom != null
                    && Math.hypot(position.x() - lastPartialPlanFrom.x(), position.z() - lastPartialPlanFrom.z()) > 1;
            failedPlans = plan.path().isEmpty() || !gainedGround ? failedPlans + 1 : 0;
            lastPartialPlanFrom = position;
        }
        report = failedPlans >= CANTCO_AFTER_FAILED_PLANS
                ? new Report.Cantco(String.format(java.util.Locale.ROOT,
                        "no route from (%.0f, %.0f, %.0f) to within %.0f of (%.0f, %.0f, %.0f)",
                        position.x(), position.y(), position.z(), goal.horizontalTolerance(), goal.x(), goal.y(), goal.z())
                        + priceOfARiskierRoute(world, plan))
                : new Report.Moving();
    }

    /**
     * When this soldier can't comply under its order's risk, whether a riskier order would let it -
     * and at what price - so the squad leader can weigh the mission against the soldier's health
     * rather than guess. Searches with every fall it could survive allowed (cheaply priced, to find
     * the least damaging such route), then names the least risk level that would accept it. "Would
     * let it" means reaching the goal, or - for a goal beyond one search - getting meaningfully
     * closer than the safe plan does: the shape of escaping a trap on the way somewhere far.
     */
    private String priceOfARiskierRoute(WorldView world, Pathfinder.Result safe) {
        Condition known = condition;
        if (known == null || known.health() < 2) {
            return "";
        }
        BlockPos start = BlockPos.containing(position.x(), position.y(), position.z());
        double factor = fallDamageFactor();
        Pathfinder.Result route = Pathfinder.find(world, start, goal, PLAN_BUDGET,
                new FallAllowance((int) Math.ceil(known.health()) - 1, 1, factor));
        BlockPos safeEnd = safe.path().isEmpty() ? start : safe.path().getLast();
        BlockPos riskyEnd = route.path().isEmpty() ? start : route.path().getLast();
        if (!route.reachesGoal() && goal.estimate(safeEnd) - goal.estimate(riskyEnd) < RETASK_DISTANCE) {
            return "";
        }
        int damage = (int) Math.ceil(Pathfinder.fallDamage(start, route.path()) * factor);
        double left = known.health() - damage;
        Risk needed = left >= Risk.MEDIUM.minHealthAfter() ? Risk.MEDIUM : left >= Risk.HIGH.minHealthAfter() ? Risk.HIGH : null;
        return String.format(java.util.Locale.ROOT, "; a route with falls costing %d of my %.0f health exists%s", damage,
                known.health(), needed == null ? ", too costly even at HIGH risk" : " (" + needed + " risk)");
    }

    private void trackProgress() {
        if (progressMark == null || Math.hypot(position.x() - progressMark.x(), position.z() - progressMark.z()) > 0.5
                || Math.abs(position.y() - progressMark.y()) > 0.5) {
            progressMark = position;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
    }

    /**
     * How much fall damage this soldier may take under {@code order}'s {@link Risk}: whatever keeps
     * it at or above the risk's floor, from the health it last reported - none if it hasn't
     * reported any. A well-fed soldier regenerates, so damage costs it half as much.
     */
    FallAllowance fallAllowance(Order order) {
        Condition known = condition;
        if (!(order instanceof Order.MoveTo move) || known == null) {
            return FallAllowance.NONE;
        }
        double spare = known.health() - move.risk().minHealthAfter();
        if (!(spare >= 1)) {
            return FallAllowance.NONE;
        }
        double cost = move.risk().costPerHealth() * (known.food() >= Condition.REGENERATION_FOOD ? 0.5 : 1);
        return new FallAllowance((int) Math.floor(spare), cost, fallDamageFactor());
    }

    /** The share of fall damage this soldier's worn armour lets through - 1 until its kit is reported. */
    private double fallDamageFactor() {
        Kit carried = kit;
        return carried == null ? 1 : carried.fallDamageFactor();
    }

    private Goal goalFor(Order order) {
        return switch (order) {
            case Order.Idle() -> null;
            case Order.MoveTo move -> new Goal(move.x(), move.y(), move.z(), move.horizontalTolerance(), move.verticalTolerance());
        };
    }

    private static double distance(Position a, Position b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private void applyVerticalMovement(WorldView world) {
        double x = position.x();
        double z = position.z();
        double y = position.y();
        double velocity = onGround ? -GRAVITY * DRAG : verticalVelocity;
        double nextY = y + velocity;

        if (velocity < 0) {
            // Land on the highest solid block whose top lies between here and nextY.
            for (int blockY = (int) Math.floor(y + EPSILON) - 1; blockY >= (int) Math.floor(nextY); blockY--) {
                if (anySolid(world, x, z, blockY, blockY)) {
                    position = new Position(position.x(), blockY + 1, position.z());
                    verticalVelocity = 0;
                    onGround = true;
                    return;
                }
            }
        } else if (anySolid(world, x, z, (int) Math.floor(nextY + BODY_HEIGHT), (int) Math.floor(nextY + BODY_HEIGHT))) {
            nextY = y; // bumped a ceiling
            velocity = 0;
        }

        position = new Position(position.x(), nextY, position.z());
        verticalVelocity = (velocity - GRAVITY) * DRAG;
        onGround = false;
    }

    /** Room for the whole body, and no hazard (water, fire) to step into - including when retreating. */
    private static boolean bodyFits(WorldView world, double x, double y, double z) {
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + BODY_HEIGHT - EPSILON);
        return !anySolid(world, x, z, minY, maxY) && !anyHazard(world, x, z, minY, maxY);
    }

    private static boolean anyHazard(WorldView world, double x, double z, int minY, int maxY) {
        int minX = (int) Math.floor(x - HALF_WIDTH);
        int maxX = (int) Math.floor(x + HALF_WIDTH - EPSILON);
        int minZ = (int) Math.floor(z - HALF_WIDTH);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isHazard(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether any block in rows {@code minY..maxY} under the body's 0.6-wide footprint at (x, z) is solid. */
    private static boolean anySolid(WorldView world, double x, double z, int minY, int maxY) {
        int minX = (int) Math.floor(x - HALF_WIDTH);
        int maxX = (int) Math.floor(x + HALF_WIDTH - EPSILON);
        int minZ = (int) Math.floor(z - HALF_WIDTH);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isSolid(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void stepToward(WorldView world, double targetX, double targetZ) {
        stepToward(world, targetX, targetZ, STEP);
    }

    private void stepToward(WorldView world, double targetX, double targetZ, double step) {
        double dx = targetX - position.x();
        double dz = targetZ - position.z();
        double distance = Math.hypot(dx, dz);
        if (distance < EPSILON) {
            return;
        }
        double move = Math.min(step, distance); // never overshoot - nor stall just short of the target

        double stepX = (dx / distance) * move;
        double stepZ = (dz / distance) * move;
        double nextX = position.x() + stepX;
        double nextZ = position.z() + stepZ;
        double y = position.y();
        if (bodyFits(world, nextX, y, nextZ)) {
            position = new Position(nextX, y, nextZ);
        } else if (onGround && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A one-block rise with headroom here and there: jump; the step happens once airborne.
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        } else if (Math.abs(stepX) > 1e-9 && bodyFits(world, nextX, y, position.z())) {
            // Blocked on the diagonal: slide along the obstacle, one axis at a time - how vanilla
            // resolves collisions. The route is planned on cell centres; a 0.6-wide body standing
            // off-centre can snag a corner the plan doesn't see.
            position = new Position(nextX, y, position.z());
        } else if (Math.abs(stepZ) > 1e-9 && bodyFits(world, position.x(), y, nextZ)) {
            position = new Position(position.x(), y, nextZ);
        }
    }
}
