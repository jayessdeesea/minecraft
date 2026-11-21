package io.github.jayessdeesea.minecraft.bot;

import static io.github.jayessdeesea.minecraft.bot.Ballistics.*;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.PlanMemo;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Soldier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.jayessdeesea.minecraft.bot.Body.*;
import static io.github.jayessdeesea.minecraft.bot.Gait.*;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.*;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (docs/architecture.md's \"Behavior modeling\" section). {@code app}'s {@code SoldierRunner} is
 * the imperative shell: it calls {@link #tick} at 20Hz and reports the resulting gait.position to the
 * server; tests call it directly with a fake {@code WorldView}.
 *
 * <p>{@code gait.position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
/**
 * A soldier's moves: the route it plans to its order's goal and walks - replanned, legs and wide searches, snags and
 * cells routed round, steps cut and built, pillars - over its body's walk ({@link Gait}); what it decides to do is
 * {@link DeterministicSoldier}'s (backlog/split-oversized-sources.md: DeterministicSoldier's moves). Tick loop only, as
 * the soldier is.
 */
abstract class Mover {

    final String name;
    /** Its body moving through the world: where it is, how it's falling or swimming (bot.Gait). Tick loop writes. */
    final Gait gait;
    volatile Order order = new Order.Idle();

    // Written only by the connection's thread (reportHealth), as gait.position is only by the tick loop.
    volatile Condition condition;
    /** Whether the last step was a sprint - tick thread writes, its caller reads. */
    volatile boolean sprinting;
    volatile Kit kit; // written only by the connection's thread (reportKit)
    int underFireTicks; // tick-loop only

    /** Whether its shield is up: it moves at {@link DeterministicSoldier#SHIELD_SLOWDOWN} of its pace. */
    public abstract boolean shieldRaised();

    Mover(String name, Position start) {
        this.name = name;
        this.gait = new Gait(start);
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
    /** Ticks before a plan that found no route is tried again. */
    static final int NO_ROUTE_RETRY_TICKS = 20;
    /** Ticks without real progress along the route before re-planning. */
    static final int STUCK_TICKS = 20;
    /** How long a cell it got stuck short of is routed round: long enough for a squadmate in the way to move on. */
    static final int AVOID_TICKS = 200;
    /** Cells it got stuck short of, and the tick each is tried again from: plans route round them meanwhile. */
    final Map<BlockPos, Integer> avoided = new HashMap<>();
    /**
     * How far round a creeper a route gives it berth (operator, 2026-11-16, the engagement doctrine:
     * a creeper on the route is gone round, not fought): past its blast, each cell inside costing
     * {@link Pathfinder#AVOID_COST} more, so a way round is taken where there is one.
     */
    static final int CREEPER_BERTH = 5;
    /** How far off a creeper is known when a route is planned: about a route's first stretch. */
    static final double CREEPER_WATCH = 24;
    /** The creepers its route was planned round: a new one comes into the watch, and it plans again. */
    Set<Integer> creepersRoutedRound = Set.of();
    /**
     * How many times it gets stuck short of one cell before that cell is a snag: a block
     * the server has and the model doesn't (backlog/world-model-stale.md). Live, a soldier down the
     * camp's mine was stuck short of one cell 161 times in a quarter of an hour, every plan through it.
     */
    public static final int SNAG_TRIES = 3;
    /** How many times it has been stuck short of each cell - a cell is routed round for a while between tries. */
    final Map<BlockPos, Integer> snagTries = new HashMap<>();
    BlockPos snag;

    /** Whether {@code to} can be seen from {@code from} (Body#lineOfSight). */
    public static boolean lineOfSight(WorldView world, Position from, Position to) {
        return Body.lineOfSight(world, from, to);
    }

    /**
     * The cell it has been stuck short of {@link #SNAG_TRIES} times, if any - taken, so it's
     * reported once: the caller marks it solid in the model, and the next plan goes round it or breaks it.
     */
    public Optional<BlockPos> takeSnag() {
        BlockPos taken = snag;
        snag = null;
        return Optional.ofNullable(taken);
    }

    /**
     * Stuck short of {@code failed}, a waypoint it couldn't get into - stalled at it, or put back by
     * the server: not tried again straight away, and counted toward a snag.
     */
    void stuckShortOf(BlockPos failed) {
        if (avoided.put(failed, navTicks + AVOID_TICKS) != null) {
            return;
        }
        LOG.info("{} stuck short of {} - tries another way round it for a while", name, failed);
        if (snagTries.merge(failed, 1, Integer::sum) >= SNAG_TRIES) {
            snag = failed;
            snagTries.remove(failed);
        }
    }
    static final Logger LOG = LogManager.getLogger(DeterministicSoldier.class);
    int navTicks;
    /** Cell visits per plan - bounds the planning cost per tick (see {@link Pathfinder}). */
    static final int PLAN_BUDGET = 4000;
    /** The goal a wide search was needed for, searched wide on every replan while it's the goal. Tick loop only. */
    Goal wideGoal;
    /** The wider search, once the cheap ones gain no ground: a way round beyond one cheap search. */
    static final int LONG_PLAN_BUDGET = 60_000;
    /** The goal a wide search last failed for, and at which tick - tick loop only. */
    Goal wideFailedGoal;
    /** The last wide search and what it found; its ground stamped this far round its start, in chunks, past its budget's reach. */
    final PlanMemo wideMemo = new PlanMemo();
    static final int WIDE_GROUND_CHUNKS = 4;
    /** The goal the last leg was planned toward, where it began and its path, and the cells legs since go round. */
    Goal legGoal;
    BlockPos legFrom;
    List<BlockPos> legPath;
    final Set<BlockPos> legAvoid = new java.util.HashSet<>();
    /** How many times running a leg has been planned again from the same cell. */
    int legAgain;
    /** How near the last leg's start a new one begins to be the same leg again. */
    static final int LEG_AGAIN_REACH = 2;
    /** How many of a leg got nowhere's first cells the next leg goes round. */
    static final int LEG_AVOID_CELLS = 4;
    int wideFailedAt;
    /**
     * Ticks before a goal a wide search failed for is searched wide again: five seconds, doubled for each failure in a
     * row for the same goal, to {@link #WIDE_RETRY_MOST_TICKS}. Commit 944's profile: Eli at a cave's edge, no route to
     * his gallery's next stand, searched 60,000 cells every five seconds - a thirteenth of the garrison's time.
     */
    static final int WIDE_RETRY_TICKS = 100;
    static final int WIDE_RETRY_MOST_TICKS = 1600;
    /** Wide searches in a row that have failed for {@link #wideFailedGoal}. */
    int wideFailures;
    /**
     * Walking a leg: the way a wide search found toward a goal beyond it, to its nearest ground to the
     * goal, followed to its end before the next search - not replanned on the clock, which from inside
     * the hollow it's leaving would lead straight back (backlog/long-routes.md). Tick loop only.
     */
    boolean onLeg;
    /** Blocks nearer the goal a leg must end, to be one. */
    static final int LEG_GAIN = 8;
    /** How far under its column's surface a cell is under the ground: a cave's, not the surface's. */
    static final int UNDER_THE_SURFACE = 8;
    static final double WAYPOINT_REACHED = 0.25;

    // Navigation state - tick-loop only, like gait.verticalVelocity.
    Order plannedOrder;
    Goal goal;
    List<BlockPos> route;
    int routeIndex;
    int ticksSincePlan;
    int stuckTicks;
    Position progressMark;
    volatile String lastPlan = "none";
    volatile Report report = new Report.Idle();
    /** Consecutive partial plans with no real headway - see {@link #CANTCO_AFTER_FAILED_PLANS}. */
    int failedPlans;
    Position lastPartialPlanFrom;

    /** Cutting a step counts as headway for this long - past it, a dig that isn't happening is being stuck. */
    static final int CUT_PATIENCE_TICKS = 300;
    /** The blocks to dig for the step the soldier is on - tick thread writes, its caller reads. */
    volatile List<BlockPos> cutting = List.of();
    int cuttingTicks;

    /**
     * The blocks the soldier needs dug to take its next step up a slope, in the order to dig them -
     * empty unless it's stopped to cut one ({@link Pathfinder#stepCut}). Its caller digs them; the
     * soldier waits, then jumps up once they're gone.
     */
    public List<BlockPos> cutting() {
        return cutting;
    }

    /** Observability only: the most recent route plan, e.g. "partial, 12 steps ending BlockPos[...]". */
    public String lastPlan() {
        return lastPlan;
    }

    void navigate(WorldView world) {
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
        if ((gait.onGround || gait.afloat) && goal.contains(BlockPos.containing(gait.position.x(), gait.position.y(), gait.position.z()))) {
            route = null;
            failedPlans = 0;
            // A goal finer than half a block wants a spot, not just a cell - inside a hut a body off
            // its cell's centre leans into the wall being built. Step onto it before standing.
            if (goal.horizontalTolerance() < 0.5 && !goal.contains(gait.position.x(), gait.position.y(), gait.position.z())) {
                stepToward(world, goal.x(), goal.z());
                report = new Report.Moving();
                return;
            }
            report = new Report.InPosition();
            if (goal.equals(tripGoal)) {
                tripDone = new Trip(tripSteps, navTicks - tripFrom);
                tripGoal = null;
            }
            return;
        }

        ticksSincePlan++;
        trackProgress();
        if (cuttingTicks > 0 && cuttingTicks < CUT_PATIENCE_TICKS) {
            stuckTicks = 0; // standing still to cut a step is headway
        }
        // A plan that found no way at all isn't made again the next tick: live (commit 544's profile),
        // one soldier in a cave with no route to its goal searched 60,000 cells twenty times a second,
        // more than half the garrison's CPU.
        // On dry ground only: in water each plan is the plug's next step (the squad simulation's soldier
        // stuck in water with only logs plugs its way out a plan at a time).
        boolean noRoute = route != null && route.isEmpty() && ticksSincePlan < NO_ROUTE_RETRY_TICKS && !wet && !gait.afloat;
        List<TrackedEntity> creepers = world.nearbyHostiles(gait.position, CREEPER_WATCH).stream().filter(TrackedEntity::explodes).toList();
        Set<Integer> creeperIds = creepers.stream().map(TrackedEntity::entityId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean newCreeper = !creepersRoutedRound.containsAll(creeperIds);
        boolean replan = route == null || routeIndex >= route.size() && !noRoute
                || ticksSincePlan >= REPLAN_TICKS && !onLeg || stuckTicks >= STUCK_TICKS || newCreeper;
        navTicks++;
        avoided.values().removeIf(until -> until <= navTicks);
        if (replan && (gait.onGround || gait.afloat)) {
            boolean stuck = stuckTicks >= STUCK_TICKS;
            // Stuck short of a waypoint: not tried again straight away. The next plan goes round it if
            // there's a way - a squadmate may be on the same step - and comes back to it only later.
            if (stuck && route != null && routeIndex < route.size() && !goal.contains(route.get(routeIndex))) {
                stuckShortOf(route.get(routeIndex));
            }
            Set<BlockPos> avoid = new java.util.HashSet<>(avoided.keySet());
            // A leg planned again from where the last began, toward the same goal, goes round the way that one
            // went: it got nowhere. Live (field428, field433), Eli planned the same 214-step leg 69 times and
            // Alice the same 69-step leg 31 times in a night, Alpha's two levels standing empty.
            if (!goal.equals(legGoal)) {
                legGoal = goal;
                legAvoid.clear();
                legFrom = null;
            }
            avoid.addAll(legAvoid);
            // A berth round each creeper in the watch: the route goes round, not past it at arm's length.
            avoid.addAll(Pathfinder.berth(creepers.stream().map(c -> BlockPos.containing(c.position().x(), c.position().y(), c.position().z())).toList(), CREEPER_BERTH));
            creepersRoutedRound = creeperIds;
            // A fall is the exception, never the plan: first a route with none - walked, then with
            // steps built from what it carries - and only with no such route one the order's risk allows.
            BlockPos start = standingCell(world, gait.position);
            onLeg = false;
            // Wide straight away for a goal a wide search was needed for: a cheap replan on the way
            // round would only lead back to the wall it's going round.
            Pathfinder.Result plan = Pathfinder.find(world, start, goal, goal.equals(wideGoal) ? LONG_PLAN_BUDGET : PLAN_BUDGET,
                    FallAllowance.NONE, pillarBlocks(), carriesPickaxe(), false, avoid);
            if (!plan.reachesGoal() && pillarBlocks() > 0) {
                Pathfinder.Result built = Pathfinder.find(world, start, goal, PLAN_BUDGET, FallAllowance.NONE, pillarBlocks(),
                        carriesPickaxe(), true, avoid);
                // Only one that gets there: a partial one, toward a goal beyond one search, leads it astray.
                if (built.reachesGoal()) {
                    plan = built;
                }
            }
            // A goal beyond one cheap search: once the cheap plans gain no ground, one search wider.
            // Live, two soldiers stood a day 80 blocks out, every plan ending where they stood.
            // Not again straight after one that didn't get there: commit 624's profile, three soldiers at a
            // mine's bottom with no route out searched 60,000 cells at the end of each partial route, two
            // thirds of the soldiers' CPU.
            boolean wideJustFailed = goal.equals(wideFailedGoal)
                    && navTicks - wideFailedAt < Math.min(WIDE_RETRY_MOST_TICKS, WIDE_RETRY_TICKS << Math.min(4, Math.max(0, wideFailures - 1)));
            if (!plan.reachesGoal() && failedPlans > 0 && !wideJustFailed) {
                Pathfinder.Result wide = wideMemo.find(new Object[]{start, goal, world.stamp(start.x(), start.z(), WIDE_GROUND_CHUNKS), Set.copyOf(avoid),
                        pillarBlocks(), carriesPickaxe()}, () -> Pathfinder.find(world, start, goal, LONG_PLAN_BUDGET, FallAllowance.NONE, pillarBlocks(), carriesPickaxe(), false, avoid));
                if (wide.reachesGoal()) {
                    plan = wide;
                    wideGoal = goal;
                    wideFailures = 0;
                } else {
                    wideFailures = goal.equals(wideFailedGoal) ? wideFailures + 1 : 1;
                    wideFailedGoal = goal;
                    wideFailedAt = navTicks;
                    // Beyond even the wide search, a leg: the way to its nearest ground to the goal,
                    // walked to its end, and searched on from there. Live, Ivy stood half an hour in
                    // two runs at (38, 74, -51), every goal 70 to 120 blocks west.
                    if (isLeg(world, start, wide)) {
                        if (legFrom != null && Math.abs(legFrom.x() - start.x()) + Math.abs(legFrom.y() - start.y())
                                + Math.abs(legFrom.z() - start.z()) <= LEG_AGAIN_REACH && legPath != null) {
                            legPath.stream().skip(1).limit(LEG_AVOID_CELLS).forEach(legAvoid::add);
                            LOG.warn("{} plans the same leg again from {} toward ({}, {}, {}) - the next goes round its first {} cells",
                                    name, start, (int) goal.x(), (int) goal.y(), (int) goal.z(), LEG_AVOID_CELLS);
                            // Planned and never walked, time after time: its first step is a snag (takeSnag) - a
                            // block the model hasn't, marked so the next plan breaks it or goes round. Live (field436),
                            // Gus on the keep's mine stairs at y=44 planned one leg up them every 5 s for minutes.
                            if (++legAgain >= SNAG_TRIES && legPath.size() > 1) {
                                snag = legPath.get(1);
                                legAgain = 0;
                            }
                        }
                        if (legFrom == null || !legFrom.equals(start)) {
                            legAgain = 0;
                        }
                        legFrom = start;
                        legPath = wide.path();
                        plan = wide;
                        onLeg = true;
                        LOG.info("{} walks a leg toward ({}, {}, {}): {} steps to {}, {} blocks nearer", name,
                                (int) goal.x(), (int) goal.y(), (int) goal.z(), wide.path().size(), wide.path().getLast(),
                                (int) (goal.estimate(start) - goal.estimate(wide.path().getLast())));
                    }
                }
            }
            FallAllowance allowance = fallAllowance(current);
            if (!plan.reachesGoal() && !allowance.equals(FallAllowance.NONE)) {
                Pathfinder.Result risky = Pathfinder.find(world, start, goal, PLAN_BUDGET, allowance, pillarBlocks(), carriesPickaxe(), false, avoid);
                if (risky.reachesGoal()) {
                    plan = risky;
                }
            }
            route = plan.path();
            pillarFrom = null;
            assessPlan(world, plan, stuck);
            if (onLeg) {
                failedPlans = 0; // a leg walked is headway, not a plan that failed
                report = new Report.Moving();
            }
            if (plan.reachesGoal() && !goal.equals(tripGoal) && !route.isEmpty()) {
                tripGoal = goal;
                tripFrom = navTicks;
                tripSteps = route.size();
            }
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
        BlockPos here = BlockPos.containing(gait.position.x(), gait.position.y() + EPSILON, gait.position.z());
        // Afloat too: the planner cuts a step out of a pool whose banks are too high to climb.
        List<BlockPos> cut = gait.onGround || gait.afloat ? toCut(world, here, waypoint, carriesPickaxe()) : List.of();
        if (!cut.isEmpty()) {
            cutting = cut;
            cuttingTicks++;
            report = new Report.Moving();
            return;
        }
        cuttingTicks = 0;
        if (gait.onGround && buildStep(world, here, waypoint)) {
            report = new Report.Moving();
            return;
        }
        if (waypoint.x() == here.x() && waypoint.z() == here.z() && waypoint.y() == here.y() + 1
                || pillarFrom != null && waypoint.equals(pillarFrom.offset(0, 1, 0))) {
            pillar(world, waypoint);
            return;
        }
        double centreX = waypoint.x() + 0.5;
        double centreZ = waypoint.z() + 0.5;
        if (Math.hypot(centreX - gait.position.x(), centreZ - gait.position.z()) < WAYPOINT_REACHED
                && Math.abs(waypoint.y() - gait.position.y()) < 0.6) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                return;
            }
            waypoint = route.get(routeIndex);
            centreX = waypoint.x() + 0.5;
            centreZ = waypoint.z() + 0.5;
        }
        boolean far = Math.hypot(goal.x() - gait.position.x(), goal.z() - gait.position.z()) > SPRINT_DISTANCE;
        // The server charges sprinting 0.1 exhaustion a metre and walking nothing: a routine walk is
        // walked, and only an urgent move, or one under fire, is run. Live, soldiers carrying rations
        // sprinted every walk past eight blocks and ate eleven loaves a day each - about the farm's
        // whole harvest (operator, 2026-11-13).
        boolean urgent = order instanceof Order.MoveTo move && move.urgent() || underFireTicks > 0;
        sprinting = far && canSprint() && urgent;
        gait.levelStep = waypoint.y() <= Math.floor(gait.position.y() + EPSILON);
        stepToward(world, centreX, centreZ, sprinting ? SPRINT_STEP : STEP);
        gait.levelStep = false;
    }

    /**
     * Whether the game lets it sprint: not at food {@link #SPRINT_FOOD_FLOOR} or below, and not with
     * its shield up - an item in use slows a player and stops a sprint.
     */
    boolean canSprint() {
        Condition known = condition;
        return (known == null || known.food() > SPRINT_FOOD_FLOOR) && !shieldRaised() && !gait.afloat;
    }

    /** Whether it carries a pickaxe, to cut steps into stone with - unknown kit carries none. */
    boolean carriesPickaxe() {
        Kit carried = kit;
        return carried != null && carried.pickaxe();
    }


    /** The cell a pillar step rises from, while one is under way. */
    BlockPos pillarFrom;
    /** A block to place this tick, under the soldier - tick thread writes, its caller takes it. */
    BlockPos placement;

    /**
     * One tick of a pillar step to {@code top}: jump from the cell below it, and once the feet are
     * clear of that cell - the server won't place a block where a body is - ask for a block there,
     * then land on it.
     */
    void pillar(WorldView world, BlockPos top) {
        BlockPos from = top.offset(0, -1, 0);
        if (pillarFrom == null) {
            if (!gait.onGround) {
                return;
            }
            pillarFrom = from;
            gait.verticalVelocity = JUMP_VELOCITY;
            gait.onGround = false;
            return;
        }
        if (world.isSolid(from.x(), from.y(), from.z())) {
            if (gait.onGround) {
                pillarFrom = null; // standing on it: the step is done
                routeIndex++;
            }
            return;
        }
        if (gait.position.y() >= from.y() + 1) {
            placement = from;
        } else if (gait.onGround) {
            pillarFrom = null; // landed back where it started - the block never went in; try again
        }
    }

    /** Ticks between asking for the same built step's block again, while the server hasn't shown it. */
    static final int BUILD_RETRY_TICKS = 10;
    int buildWait;

    /**
     * Whether the step onto {@code next} still needs blocks placed ({@link Pathfinder#stepBuild}) -
     * if so, asks for the first one, standing in the middle of its own cell, so its body is clear of
     * a block going in beside its feet. The world model takes a placed block at once; the server's
     * answer corrects it.
     */
    boolean buildStep(WorldView world, BlockPos here, BlockPos next) {
        List<BlockPos> build = Pathfinder.stepBuild(world, here, next);
        if (build.isEmpty()) {
            buildWait = 0;
            return false;
        }
        double cx = here.x() + 0.5;
        double cz = here.z() + 0.5;
        if (Math.hypot(cx - gait.position.x(), cz - gait.position.z()) > BUILD_CENTRED) {
            stepToward(world, cx, cz);
            return true;
        }
        if (buildWait-- <= 0) {
            placement = build.getFirst();
            buildWait = BUILD_RETRY_TICKS;
        }
        return true;
    }

    /** How near its cell's middle a soldier stands to build a step - a 0.6-wide body then clears the cells beside. */
    static final double BUILD_CENTRED = 0.15;

    /**
     * How many blocks a route can be planned on - pillar and built steps: one per cobblestone or
     * dirt carried. Logs are kept for tools, except in the water: getting out comes first. Live, a
     * farmer with two logs and no dirt floated beside its farm for minutes, with no route out.
     */
    int pillarBlocks() {
        Kit carried = kit;
        return carried == null ? 0 : carried.stone() + carried.dirt() + (wet ? carried.logs() : 0);
    }

    /** Whether its feet were in water at its last tick. */
    volatile boolean wet;

    /** The trip under way: its goal, the tick it was planned at, its planned steps. */
    private Goal tripGoal;
    private int tripFrom, tripSteps;
    private volatile Trip tripDone;

    /** The trip just finished, if one has - taken, so it's counted once (backlog/simulator-fidelity.md, the ledger). */
    public Optional<Trip> takeTrip() {
        Trip taken = tripDone;
        tripDone = null;
        return Optional.ofNullable(taken);
    }

    /**
     * The block to place this tick, if a pillar or built step wants one - taken, so it's asked for
     * once. The caller places it, against any solid side, after reporting the soldier's new height,
     * and adds it to the world model so the soldier can land on it before the server confirms.
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
    /**
     * Whether a wide search that didn't reach its goal found a leg toward it: a way to ground at least
     * {@link #LEG_GAIN} blocks nearer, and - from the surface - on the surface, never down into a cave
     * toward a goal there's no walk to (backlog/long-routes.md: live, Jack went from y=73 into the caves
     * to y=-19 in a minute, walking cave to cave).
     */
    boolean isLeg(WorldView world, BlockPos start, Pathfinder.Result wide) {
        if (wide.path().isEmpty()) {
            return false;
        }
        BlockPos end = wide.path().getLast();
        if (goal.estimate(start) - goal.estimate(end) < LEG_GAIN) {
            return false;
        }
        return !onTheSurface(world, start) || onTheSurface(world, end);
    }


    void assessPlan(WorldView world, Pathfinder.Result plan, boolean stuck) {
        if (stuck) {
            failedPlans++;
        } else if (plan.reachesGoal()) {
            failedPlans = 0;
            lastPartialPlanFrom = null;
        } else {
            boolean gainedGround = lastPartialPlanFrom != null
                    && Math.hypot(gait.position.x() - lastPartialPlanFrom.x(), gait.position.z() - lastPartialPlanFrom.z()) > 1;
            failedPlans = plan.path().isEmpty() || !gainedGround ? failedPlans + 1 : 0;
            lastPartialPlanFrom = gait.position;
        }
        report = failedPlans >= CANTCO_AFTER_FAILED_PLANS
                // Each end named by its block, not its centre rounded: (23.5, 65, 87.5) printed "%.0f" read
                // as (24, 65, 88), the block beside it - and live, the wrong blocks were probed for why.
                ? new Report.Cantco(String.format(Locale.ROOT,
                        "no route from (%d, %d, %d) to within %.0f of (%d, %d, %d)",
                        (int) Math.floor(gait.position.x()), (int) Math.floor(gait.position.y()), (int) Math.floor(gait.position.z()),
                        goal.horizontalTolerance(), (int) Math.floor(goal.x()), (int) Math.floor(goal.y()), (int) Math.floor(goal.z()))
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
    String priceOfARiskierRoute(WorldView world, Pathfinder.Result safe) {
        Condition known = condition;
        if (known == null || known.health() < 2) {
            return "";
        }
        BlockPos start = standingCell(world, gait.position);
        double factor = fallDamageFactor();
        Pathfinder.Result route = Pathfinder.find(world, start, goal, PLAN_BUDGET,
                new FallAllowance((int) Math.ceil(known.health()) - 1, 1, factor));
        BlockPos safeEnd = safe.path().isEmpty() ? start : safe.path().getLast();
        BlockPos riskyEnd = route.path().isEmpty() ? start : route.path().getLast();
        if (!route.reachesGoal() && goal.estimate(safeEnd) - goal.estimate(riskyEnd) < RETASK_DISTANCE) {
            return "";
        }
        int damage = (int) Math.ceil(Pathfinder.fallDamage(start, route.path()) * factor);
        // A route with no fall past what its own order allows is no riskier route: it was held up on the way - stuck,
        // a squadmate in the way - not barred by the risk. Live (field577), 36 CANTCOs a block or two from their goal
        // said "falls costing 0 ... (MEDIUM risk)", and a MEDIUM order would have met the same squadmate.
        if (damage == 0 && route.reachesGoal()) {
            return "; a route with no fall in it exists - held up on the way, not by the risk";
        }
        double left = known.health() - damage;
        Risk needed = left >= Risk.MEDIUM.minHealthAfter() ? Risk.MEDIUM : left >= Risk.HIGH.minHealthAfter() ? Risk.HIGH : null;
        return String.format(Locale.ROOT, "; a route with falls costing %d of my %.0f health exists%s", damage,
                known.health(), needed == null ? ", too costly even at HIGH risk" : " (" + needed + " risk)");
    }

    void trackProgress() {
        // Height is headway only once it's landed there: a jump that comes back down isn't. Live
        // (field364), Olga and Ned jumped at a step under a tree's leaves at the farm's edge, each jump
        // read as headway, and the planner never tried another way (operator, 2026-11-17: try twice,
        // then another way).
        if (progressMark == null || Math.hypot(gait.position.x() - progressMark.x(), gait.position.z() - progressMark.z()) > 0.5
                || gait.onGround && Math.abs(gait.position.y() - progressMark.y()) > 0.5) {
            progressMark = gait.position;
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
        return new FallAllowance((int) Math.min(Math.floor(spare), move.risk().maxDamage()), cost, fallDamageFactor());
    }

    /** The share of fall damage this soldier's worn armour lets through - 1 until its kit is reported. */
    double fallDamageFactor() {
        Kit carried = kit;
        return carried == null ? 1 : carried.fallDamageFactor();
    }

    Goal goalFor(Order order) {
        return switch (order) {
            // Where to stand for the next cell is the building's to say, given as a move.
            case Order.Idle(), Order.Place _, Order.Dig _, Order.Fill _, Order.Gather _ -> null;
            case Order.MoveTo move -> new Goal(move.x(), move.y(), move.z(), move.horizontalTolerance(), move.verticalTolerance());
        };
    }




    void stepToward(WorldView world, double targetX, double targetZ) {
        stepToward(world, targetX, targetZ, STEP);
    }

    void stepToward(WorldView world, double targetX, double targetZ, double pace) {
        gait.step(world, targetX, targetZ, (shieldRaised() ? pace * SHIELD_SLOWDOWN : pace));
    }
}
