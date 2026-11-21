package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How well a soldier gets about (operator, 2026-11-20: optimal paths for a bunch of situations, the simulator what lets
 * us find out): in every terrain family over many seeds, the ticks it takes to reach its objective against a reference
 * best - the route an unbounded search finds, priced at what each kind of move costs in the soldier's own physics,
 * measured here: a block walked, a step up, a block swum. Its efficiency is the reference over the actual; a table by
 * family is printed, and with {@code -Dmovement.eval=true} each family's median must reach {@link #TARGET}.
 */
class MovementEfficiencyTest {

    /** The efficiency a family's median is held to, run as the eval. */
    static final double TARGET = 0.9;
    private static final int SEEDS = Integer.getInteger("movement.seeds", 12);
    private static final int ORACLE_BUDGET = 500_000;
    private static final int MOST_TICKS = 6_000;

    /** What each kind of move costs the soldier, in ticks, measured on its own physics. */
    record Costs(double walk, double stepUp, double stepDown, double swim) {
    }

    /** The costs measured: a flat walk, a stair up, a swim across a pool. */
    static Costs measure() {
        double walk = ticksA(flat(), new BlockPos(0, 64, 0), new BlockPos(30, 64, 0)) / 30.0;
        SyntheticWorld stair = flat();
        for (int x = 1; x <= 10; x++) {
            stair.setGround(x, 0, 63 + x);
        }
        for (int x = 11; x <= 20; x++) {
            stair.setGround(x, 0, 73);
        }
        double stepUp = (ticksA(stair, new BlockPos(0, 64, 0), new BlockPos(20, 74, 0)) - 20 * walk) / 10.0;
        // Down the same stair: a drop a block at a time, its fall and landing - unpriced, the worst routes of broken
        // camp ground (app's RecordedGroundEfficiencyTest) were all descents.
        double stepDown = (ticksA(stair, new BlockPos(20, 74, 0), new BlockPos(0, 64, 0)) - 20 * walk) / 10.0;
        SyntheticWorld pool = flat();
        for (int x = 3; x <= 22; x++) {
            for (int z = -3; z <= 3; z++) {
                pool.setGround(x, z, 61);
                pool.water(x, 62, z);
                pool.water(x, 63, z);
            }
        }
        double swim = (ticksA(pool, new BlockPos(0, 64, 0), new BlockPos(25, 64, 0)) - 5 * walk) / 20.0;
        return new Costs(walk, stepUp, stepDown, swim);
    }

    private static SyntheticWorld flat() {
        return new SyntheticWorld(63);
    }

    /** The ticks a soldier takes from {@code start} to stand at {@code goal}, or the most allowed. */
    static int ticksA(SyntheticWorld world, BlockPos start, BlockPos goal) {
        DeterministicSoldier soldier = new DeterministicSoldier("Alice", new Position(start.x() + 0.5, start.y(), start.z() + 0.5));
        soldier.receiveOrder(new Order.MoveTo(goal.x() + 0.5, goal.y(), goal.z() + 0.5, 1.5, 1, Risk.LOW));
        for (int tick = 0; tick < MOST_TICKS; tick++) {
            soldier.tick(world);
            if (soldier.status().report() instanceof Report.InPosition) {
                return tick + 1;
            }
            if (soldier.status().report() instanceof Report.Cantco) {
                return MOST_TICKS;
            }
        }
        return MOST_TICKS;
    }

    /** The reference best: the unbounded search's route, each move priced at {@code costs}. Empty if there's none. */
    static OptionalInt reference(SyntheticWorld world, BlockPos start, BlockPos goal, Costs costs) {
        Pathfinder.Result route = Pathfinder.find(world, start, new Goal(goal.x() + 0.5, goal.y(), goal.z() + 0.5, 1.5, 1),
                ORACLE_BUDGET, FallAllowance.NONE, 0);
        if (!route.reachesGoal()) {
            return OptionalInt.empty();
        }
        double ticks = 0;
        BlockPos from = start;
        for (BlockPos to : route.path()) {
            double across = Math.hypot(to.x() - from.x(), to.z() - from.z());
            ticks += across * (world.isWater(to.x(), to.y(), to.z()) ? costs.swim() : costs.walk());
            if (to.y() > from.y()) {
                ticks += (to.y() - from.y()) * costs.stepUp();
            } else if (to.y() < from.y()) {
                ticks += (from.y() - to.y()) * costs.stepDown();
            }
            from = to;
        }
        return OptionalInt.of((int) Math.round(ticks));
    }

    /** Every family's median and worst efficiency, printed as a table; held to the target when run as the eval. */
    @Test
    void everyFamilysEfficiency() {
        Costs costs = measure();
        System.out.printf(Locale.ROOT, "[Movement] costs: walk %.2f ticks a block, step up %.2f more, step down %.2f, swim %.2f a block%n",
                costs.walk(), costs.stepUp(), costs.stepDown(), costs.swim());
        Map<Terrain, List<Double>> byFamily = new EnumMap<>(Terrain.class);
        for (Terrain terrain : Terrain.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Scenario s = Scenario.generate(terrain, seed);
                OptionalInt best = reference(s.world(), s.start(), s.objective(), costs);
                if (best.isEmpty()) {
                    continue;
                }
                int actual = ticksA(Scenario.generate(terrain, seed).world(), s.start(), s.objective());
                byFamily.computeIfAbsent(terrain, t -> new ArrayList<>()).add(Math.min(1.0, (double) best.getAsInt() / actual));
            }
        }
        List<String> short_ = new ArrayList<>();
        System.out.printf(Locale.ROOT, "[Movement] %-9s %5s %7s %7s%n", "family", "runs", "median", "worst");
        byFamily.forEach((terrain, list) -> {
            List<Double> sorted = list.stream().sorted().toList();
            double median = sorted.get(sorted.size() / 2);
            System.out.printf(Locale.ROOT, "[Movement] %-9s %5d %6.0f%% %6.0f%%%n", terrain.name().toLowerCase(), sorted.size(),
                    100 * median, 100 * sorted.getFirst());
            if (median < TARGET) {
                short_.add(terrain.name().toLowerCase() + " " + Math.round(100 * median) + "%");
            }
        });
        if (Boolean.getBoolean("movement.eval")) {
            assertTrue(short_.isEmpty(), "families under " + Math.round(100 * TARGET) + "%: " + short_);
        }
    }
}
