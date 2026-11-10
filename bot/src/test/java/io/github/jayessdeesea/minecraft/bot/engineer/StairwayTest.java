package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StairwayTest {

    /** Flat ground at y=63, and a 3x3 stone platform at y=80 over x 6..8, z -1..1. */
    private static boolean ground(int x, int y, int z) {
        return y <= 63 || y == 80 && x >= 6 && x <= 8 && z >= -1 && z <= 1;
    }

    private static WorldView world(Predicate<BlockPos> solid, Predicate<BlockPos> hazard) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return solid.test(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return hazard.test(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    private static final BlockPos ON_THE_PLATFORM = new BlockPos(7, 81, 0);

    @Test
    void aStairFromAPlatformIsTwoBlocksAStepBuiltFromTheGroundUpEachAgainstOneAlreadyIn() {
        WorldView world = world(c -> ground(c.x(), c.y(), c.z()), c -> false);

        List<BlockPos> stair = Stairway.down(world, ON_THE_PLATFORM).orElseThrow();

        // 17 down: 16 steps, the bottom one on the ground, the rest each on a block of its own.
        assertEquals(31, stair.size());
        Set<BlockPos> in = new HashSet<>();
        for (BlockPos cell : stair) {
            boolean against = ground(cell.x(), cell.y() - 1, cell.z()) || java.util.stream.Stream.of(
                    cell.offset(1, 0, 0), cell.offset(-1, 0, 0), cell.offset(0, 1, 0), cell.offset(0, -1, 0),
                    cell.offset(0, 0, 1), cell.offset(0, 0, -1)).anyMatch(n -> in.contains(n) || ground(n.x(), n.y(), n.z()));
            assertTrue(against, cell + " has nothing to go against");
            in.add(cell);
        }
        // Built, it's a walk from the platform to the ground with no fall.
        WorldView built = world(c -> ground(c.x(), c.y(), c.z()) || in.contains(c), c -> false);
        Pathfinder.Result route = Pathfinder.find(built, ON_THE_PLATFORM, new Goal(0.5, 64, 0.5, 1.5, 0.5), 20_000);
        assertTrue(route.reachesGoal(), "no route down the stair");
    }

    /** Flat dirt ground at y=63, with a pocket at (0, 0) open from 60 to 61: a body boxed in below. */
    private static boolean pocket(BlockPos c) {
        return c.y() <= 63 && !(c.x() == 0 && c.z() == 0 && c.y() >= 60 && c.y() <= 61);
    }

    private static WorldView soil(Predicate<BlockPos> solid, Predicate<BlockPos> notSoil) {
        WorldView base = world(solid, c -> false);
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return base.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return base.isSolid(x, y, z) && !notSoil.test(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void aStairUpOutOfAPocketIsDugAndWalksOut() {
        WorldView world = soil(StairwayTest::pocket, c -> false);
        BlockPos feet = new BlockPos(0, 60, 0);

        List<BlockPos> dig = Stairway.up(world, feet).orElseThrow();

        Set<BlockPos> dug = new HashSet<>(dig);
        WorldView after = world(c -> pocket(c) && !dug.contains(c), c -> false);
        assertTrue(Pathfinder.find(after, feet, new Goal(10.5, 64, 0.5, 2, 0.5), 20_000).reachesGoal(), "no way out up the stair: " + dig);
        assertTrue(!Pathfinder.find(world, feet, new Goal(10.5, 64, 0.5, 2, 0.5), 20_000).reachesGoal(), "boxed in before");
    }

    @Test
    void noStairUpIsLaidThroughCobblestone() {
        WorldView world = soil(StairwayTest::pocket, c -> c.y() >= 60);

        assertEquals(java.util.Optional.empty(), Stairway.up(world, new BlockPos(0, 60, 0)));
    }

    @Test
    void noneIsLaidForAStepDownOrOverWater() {
        WorldView step = world(c -> c.y() <= 63 || c.y() == 64 && c.x() >= 6, c -> false);
        WorldView lake = world(c -> ground(c.x(), c.y(), c.z()), c -> c.y() <= 70 && c.y() > 63);

        assertTrue(Stairway.down(step, new BlockPos(7, 65, 0)).isEmpty());
        assertTrue(Stairway.down(lake, ON_THE_PLATFORM).isEmpty());
    }
}
