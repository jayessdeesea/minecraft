package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One soldier, sent across every terrain family under many seeds. Properties checked in every
 * variant:
 * <ul>
 *   <li>its body never overlaps a solid block and never stands in a hazard;</li>
 *   <li>if the objective is reachable, it arrives (reports InPosition) within a tick budget;</li>
 *   <li>if it isn't, it reports Cantco rather than Moving forever.</li>
 * </ul>
 * "Reachable" is decided by the same {@link Pathfinder} with an effectively unlimited budget - the
 * oracle checks that the soldier's bounded, re-planning, physics-driven execution delivers what
 * the planner says is possible, not that the planner itself is right.
 *
 * <p>Every case runs three ways ({@link Means}): on foot; climbing - carrying cobblestone, so it can
 * pillar up a wall (the oracle plans pillar steps too, and each block the soldier places goes into
 * the world); and digging - the ground is soil, so it can cut steps into a slope (each block it asks
 * dug is taken out after soil's break time).
 *
 * <p>Each case is named {@code <family> seed=<n>} and is fully reproducible from that. Run more
 * seeds with {@code -Dscenario.seeds=200}, or one case with {@code -Dscenario.only=forest:7} (or
 * {@code forest:7:climbing}, {@code cliff:3:digging}). A
 * failing seed worth keeping becomes an entry in {@link #REGRESSIONS}.
 */
class SoldierScenarioTest {

    private static final int DEFAULT_SEEDS = 20;
    private static final int ORACLE_BUDGET = 500_000;
    /** Cobblestone the climbing run carries: a stack, enough for every cliff the generator makes. */
    private static final int CARRIED_STONE = 64;
    private static final int UNREACHABLE_TICK_BUDGET = 2_000;
    private static final double HALF_WIDTH = 0.3;
    private static final double BODY_HEIGHT = 1.8;
    private static final double EPSILON = 1e-6;

    /** Ticks a block of soil takes to dig by hand - the server's answer to a cut step. */
    private static final int SOIL_DIG_TICKS = 25;

    /** What the soldier can do to get somewhere a walk can't. */
    enum Means { ON_FOOT, CLIMBING, DIGGING }

    /** Seeds that once failed, kept so they keep passing: "family:seed". */
    private static final List<String> REGRESSIONS = List.of();

    @TestFactory
    Stream<DynamicTest> everyTerrainFamily() {
        String only = System.getProperty("scenario.only");
        if (only != null) {
            String[] parts = only.split(":");
            return Stream.of(test(Scenario.generate(Terrain.valueOf(parts[0].toUpperCase()), Long.parseLong(parts[1])), means(parts)));
        }
        int seeds = Integer.getInteger("scenario.seeds", DEFAULT_SEEDS);
        List<DynamicTest> tests = new ArrayList<>();
        for (Terrain terrain : Terrain.values()) {
            for (long seed = 1; seed <= seeds; seed++) {
                for (Means means : Means.values()) {
                    tests.add(test(Scenario.generate(terrain, seed), means));
                }
            }
        }
        for (String regression : REGRESSIONS) {
            String[] parts = regression.split(":");
            tests.add(test(Scenario.generate(Terrain.valueOf(parts[0].toUpperCase()), Long.parseLong(parts[1])), means(parts)));
        }
        return tests.stream();
    }

    private static Means means(String[] parts) {
        return parts.length > 2 ? Means.valueOf(parts[2].toUpperCase()) : Means.ON_FOOT;
    }

    private static DynamicTest test(Scenario scenario, Means means) {
        return DynamicTest.dynamicTest(scenario + (means == Means.ON_FOOT ? "" : " " + means.name().toLowerCase()),
                () -> run(scenario, means));
    }

    static void run(Scenario scenario) {
        run(scenario, Means.ON_FOOT);
    }

    static void run(Scenario scenario, Means means) {
        boolean climbing = means == Means.CLIMBING;
        SyntheticWorld world = scenario.world();
        if (means == Means.DIGGING) {
            world.soilGround();
        }
        BlockPos start = scenario.start();
        BlockPos objective = scenario.objective();
        Goal goal = new Goal(objective.x() + 0.5, objective.y(), objective.z() + 0.5, 1.5, 1);
        Pathfinder.Result oracle = Pathfinder.find(world, start, goal, ORACLE_BUDGET, FallAllowance.NONE, climbing ? CARRIED_STONE : 0);
        boolean reachable = oracle.reachesGoal();
        int digs = 0;
        BlockPos previous = start;
        for (BlockPos step : oracle.path()) {
            digs += Pathfinder.stepCut(world, previous, step).size();
            previous = step;
        }
        int budget = reachable ? 400 + 15 * oracle.path().size() + 2 * SOIL_DIG_TICKS * digs : UNREACHABLE_TICK_BUDGET;

        DeterministicSoldier soldier = new DeterministicSoldier("Alice", new Position(start.x() + 0.5, start.y(), start.z() + 0.5));
        if (climbing) {
            soldier.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, CARRIED_STONE));
        }
        soldier.receiveOrder(new Order.MoveTo(goal.x(), goal.y(), goal.z(), goal.horizontalTolerance(), goal.verticalTolerance(), Risk.LOW));
        Set<BlockPos> trail = new LinkedHashSet<>();

        int digging = 0;
        for (int tick = 0; tick < budget; tick++) {
            soldier.tick(world);
            List<BlockPos> cut = soldier.cutting();
            if (cut.isEmpty()) {
                digging = 0;
            } else if (++digging >= SOIL_DIG_TICKS) {
                world.dig(cut.getFirst().x(), cut.getFirst().y(), cut.getFirst().z()); // the server breaks it
                digging = 0;
            }
            soldier.takePlacement().ifPresent(cell -> world.solid(cell.x(), cell.y(), cell.z())); // the server takes it
            Position p = soldier.status().position();
            trail.add(BlockPos.containing(p.x(), p.y(), p.z()));
            String violation = bodyViolation(world, p);
            if (violation != null) {
                fail(describe(scenario, soldier, trail, "tick " + tick + ": " + violation));
            }
            Report report = soldier.status().report();
            if (report instanceof Report.InPosition) {
                if (!reachable) {
                    fail(describe(scenario, soldier, trail, "arrived at an objective the oracle says is unreachable"));
                }
                return;
            }
            if (report instanceof Report.Cantco) {
                if (reachable) {
                    fail(describe(scenario, soldier, trail, "CANTCO at tick " + tick + " on a reachable objective"));
                }
                return;
            }
        }
        fail(describe(scenario, soldier, trail, (reachable ? "didn't arrive" : "didn't report CANTCO")
                + " within " + budget + " ticks"));
    }

    /** What's wrong with the body at {@code p}, or null: the 0.6 x 1.8 box against solids and hazards. */
    private static String bodyViolation(SyntheticWorld world, Position p) {
        int y0 = (int) Math.floor(p.y() + EPSILON);
        int y1 = (int) Math.floor(p.y() + BODY_HEIGHT - EPSILON);
        for (double cx : new double[]{p.x() - HALF_WIDTH + EPSILON, p.x() + HALF_WIDTH - EPSILON}) {
            for (double cz : new double[]{p.z() - HALF_WIDTH + EPSILON, p.z() + HALF_WIDTH - EPSILON}) {
                int bx = (int) Math.floor(cx);
                int bz = (int) Math.floor(cz);
                for (int y = y0; y <= y1; y++) {
                    if (world.isSolid(bx, y, bz)) {
                        return "body inside a solid block at (" + bx + ", " + y + ", " + bz + "), soldier at " + p;
                    }
                    if (world.isHazard(bx, y, bz)) {
                        return "body in a hazard at (" + bx + ", " + y + ", " + bz + "), soldier at " + p;
                    }
                }
            }
        }
        return null;
    }

    private static String describe(Scenario scenario, DeterministicSoldier soldier, Set<BlockPos> trail, String problem) {
        return scenario + ": " + problem
                + "\n  start " + scenario.start() + " objective " + scenario.objective()
                + "\n  final " + soldier.status()
                + "\n  last plan " + soldier.lastPlan()
                + "\n  reproduce: -Dscenario.only=" + scenario.terrain().name().toLowerCase() + ":" + scenario.seed()
                + " (add :climbing or :digging for those runs)"
                + "\n" + scenario.world().render(scenario.start(), scenario.objective(), trail);
    }
}
