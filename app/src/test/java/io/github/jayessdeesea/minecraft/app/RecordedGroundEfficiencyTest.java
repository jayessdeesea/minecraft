package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The movement eval on ground kept live (backlog/simulator-fidelity.md, DEVELOPMENT.md § movement efficiency): the
 * soldier's ticks between pairs of stands on a recorded world, against the unbounded search's route priced at its own
 * measured costs - as `bot`'s MovementEfficiencyTest does over the synthetic families, here over broken camp ground,
 * Mia's at the camp's heaped farm (field540). Held to the families' 90% median when run as the eval
 * ({@code -Dmovement.eval=true}).
 */
class RecordedGroundEfficiencyTest {

    private static final int SECTIONS = 24;
    private static final int MIN_Y = -64;
    private static final int STONE = 1, AIR = 0, WATER = 89;
    private static final int ORACLE_BUDGET = 500_000;
    private static final int MOST_TICKS = 6_000;
    private static final int PAIRS = Integer.getInteger("movement.pairs", 16);

    /** What a walked block, a step up and a swum block cost the soldier, in ticks. */
    record Costs(double walk, double stepUp, double stepDown, double swim) {
    }

    @Test
    void brokenCampGroundsEfficiency() throws Exception {
        WorldRecords.Snapshot camp = WorldRecords.load(Path.of(new ClassPathResource("worlds/camp-heaped-field540.json").getURI()));
        WorldView ground = world(camp.minX() - 16, camp.minZ() - 16, camp.sizeX() + 32, camp.sizeZ() + 32, (x, z) -> 0, (x, yz) -> {
            int y = yz >> 16, z = (short) (yz & 0xFFFF);
            int s = camp.stateAt(x, y, z);
            return s == WorldRecords.UNKNOWN ? (y < camp.minY() ? STONE : AIR) : s;
        });
        Costs costs = measure();
        System.out.printf(Locale.ROOT, "[Movement] costs: walk %.2f ticks a block, step up %.2f more, step down %.2f, swim %.2f a block%n",
                costs.walk(), costs.stepUp(), costs.stepDown(), costs.swim());
        List<BlockPos> stands = new ArrayList<>();
        for (int x = camp.minX() + 6; x < camp.minX() + camp.sizeX() - 6; x++) {
            for (int z = camp.minZ() + 6; z < camp.minZ() + camp.sizeZ() - 6; z++) {
                for (int y = camp.minY() + 2; y < camp.minY() + camp.sizeY() - 3; y++) {
                    if (Pathfinder.standable(ground, new BlockPos(x, y, z)) && !ground.isHazard(x, y, z)) {
                        stands.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        Random random = new Random(540);
        List<Double> efficiency = new ArrayList<>();
        Map<String, Double> pairs = new HashMap<>();
        for (int tries = 0; efficiency.size() < PAIRS && tries < 50 * PAIRS; tries++) {
            BlockPos a = stands.get(random.nextInt(stands.size())), b = stands.get(random.nextInt(stands.size()));
            double across = Math.hypot(a.x() - b.x(), a.z() - b.z());
            if (across < 8 || across > 24) {
                continue;
            }
            OptionalInt best = reference(ground, a, b, costs);
            if (best.isEmpty()) {
                continue;
            }
            int actual = ticks(ground, a, b);
            double e = Math.min(1.0, (double) best.getAsInt() / actual);
            efficiency.add(e);
            pairs.put(String.format(Locale.ROOT, "%.0f%% %s->%s (%d ticks, best %d)", 100 * e, a, b, actual, best.getAsInt()), e);
        }
        List<Double> sorted = efficiency.stream().sorted().toList();
        double median = sorted.get(sorted.size() / 2);
        System.out.printf(Locale.ROOT, "[Movement] camp-heaped %5d %6.0f%% %6.0f%%%n", sorted.size(), 100 * median, 100 * sorted.getFirst());
        pairs.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(3)
                .forEach(w -> System.out.println("[Movement] camp-heaped worst: " + w.getKey()));
        assertTrue(sorted.size() >= PAIRS / 2, "pairs with a route: " + sorted.size());
        if (Boolean.getBoolean("movement.eval")) {
            assertTrue(median >= 0.9, "broken camp ground's median " + Math.round(100 * median) + "% under 90%");
        }
    }

    /** The costs, measured on the soldier's own physics: a flat walk, a stair up, a swim across a pool. */
    private static Costs measure() {
        WorldView flat = world(-16, -16, 64, 32, (x, z) -> 63, null);
        double walk = ticks(flat, new BlockPos(0, 64, 0), new BlockPos(30, 64, 0)) / 30.0;
        WorldView stair = world(-16, -16, 64, 32, (x, z) -> x >= 1 && x <= 10 ? 63 + x : x > 10 ? 73 : 63, null);
        double stepUp = (ticks(stair, new BlockPos(0, 64, 0), new BlockPos(20, 74, 0)) - 20 * walk) / 10.0;
        double stepDown = (ticks(stair, new BlockPos(20, 74, 0), new BlockPos(0, 64, 0)) - 20 * walk) / 10.0;
        WorldView pool = world(-16, -16, 64, 32, (x, z) -> x >= 3 && x <= 22 && Math.abs(z) <= 3 ? 61 : 63,
                (x, yz) -> {
                    int y = yz >> 16, z = (short) (yz & 0xFFFF);
                    boolean in = x >= 3 && x <= 22 && Math.abs(z) <= 3;
                    return y <= (in ? 61 : 63) ? STONE : in && y <= 63 ? WATER : AIR;
                });
        double swim = (ticks(pool, new BlockPos(0, 64, 0), new BlockPos(25, 64, 0)) - 5 * walk) / 20.0;
        return new Costs(walk, stepUp, stepDown, swim);
    }

    /**
     * A world of block states over {@code sizeX} by {@code sizeZ} from ({@code minX}, {@code minZ}): {@code state}'s for
     * each cell (x, y << 16 | z), or with none, stone to {@code top}'s height and air over it.
     */
    static WorldView world(int minX, int minZ, int sizeX, int sizeZ, IntBinaryOperator top, IntBinaryOperator state) {
        return world(minX, minZ, sizeX, sizeZ, top, state, new EntityTracker());
    }

    /** As {@link #world(int, int, int, int, IntBinaryOperator, IntBinaryOperator)}, its entities {@code entities}'s. */
    static WorldView world(int minX, int minZ, int sizeX, int sizeZ, IntBinaryOperator top, IntBinaryOperator state, EntityTracker entities) {
        ChunkCache chunks = new ChunkCache();
        for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(minX + sizeX - 1, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(minZ + sizeZ - 1, 16); cz++) {
                List<int[]> sections = new ArrayList<>();
                for (int s = 0; s < SECTIONS; s++) {
                    int[] states = new int[4096];
                    for (int ly = 0; ly < 16; ly++) {
                        int y = MIN_Y + 16 * s + ly;
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                int x = 16 * cx + lx, z = 16 * cz + lz;
                                states[(ly << 8) | (lz << 4) | lx] = state != null ? state.applyAsInt(x, (y << 16) | (z & 0xFFFF))
                                        : y <= top.applyAsInt(x, z) ? STONE : AIR;
                            }
                        }
                    }
                    sections.add(states);
                }
                chunks.load(cx, cz, sections, "eval");
            }
        }
        return new WorldModel(chunks, entities);
    }

    /** The ticks a soldier takes from {@code start} to stand at {@code goal}, or the most allowed. */
    private static int ticks(WorldView world, BlockPos start, BlockPos goal) {
        DeterministicSoldier soldier = new DeterministicSoldier("Mia", new Position(start.x() + 0.5, start.y(), start.z() + 0.5));
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

    /** The unbounded search's route, each move priced at {@code costs}; empty with none. */
    private static OptionalInt reference(WorldView world, BlockPos start, BlockPos goal, Costs costs) {
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
                ticks += (from.y() - to.y()) * costs.stepDown(); // a drop priced: the worst pairs were all descents
            }
            from = to;
        }
        return OptionalInt.of((int) Math.round(ticks));
    }
}
