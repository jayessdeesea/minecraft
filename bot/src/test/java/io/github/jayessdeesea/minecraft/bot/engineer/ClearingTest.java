package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearingTest {

    /** Flat ground, feet at y=64, with these logs and leaves on it. */
    private static WorldView with(Set<BlockPos> logs, Set<BlockPos> leaves) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos at = new BlockPos(x, y, z);
                return y <= 63 || logs.contains(at) || leaves.contains(at);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public Optional<String> leaves(int x, int y, int z) {
                return leaves.contains(new BlockPos(x, y, z)) ? Optional.of("oak") : Optional.empty();
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    private static final BlockPos CENTRE = new BlockPos(0, 64, 0);

    /** A trunk {@code height} logs tall at (x, z), and a layer of leaves round its top. */
    private static Set<BlockPos> trunk(int x, int z, int height) {
        Set<BlockPos> logs = new HashSet<>();
        for (int y = 64; y < 64 + height; y++) {
            logs.add(new BlockPos(x, y, z));
        }
        return logs;
    }

    @Test
    void aTreeInTheCampIsCoverLogsAndLeaves() {
        Set<BlockPos> leaves = Set.of(new BlockPos(4, 69, 3), new BlockPos(2, 69, 2));
        List<BlockPos> cover = Clearing.cover(with(trunk(3, 3, 5), leaves), CENTRE, 8);

        assertEquals(7, cover.size());
        assertEquals(new BlockPos(3, 64, 3), cover.getFirst(), "lowest first");
    }

    @Test
    void theTreeIsFelledFromTheBottomStandingOnTheGround() {
        Set<BlockPos> logs = trunk(3, 3, 5);
        WorldView world = with(logs, Set.of());
        List<BlockPos> cover = Clearing.cover(world, CENTRE, 8);

        Clearing.Cut cut = Clearing.next(world, cover, new Position(0.5, 64, 0.5), Set.of()).orElseThrow();

        assertEquals(new BlockPos(3, 64, 3), cut.block());
        assertTrue(cut.log());
        assertEquals(64, cut.stand().y(), "on the ground beside it");
    }

    @Test
    void aSoldierReachesSixBlocksUpFromTheGroundAndNoHigher() {
        BlockPos high = new BlockPos(3, 70, 3);
        BlockPos tooHigh = new BlockPos(3, 71, 3);

        assertTrue(Clearing.next(with(Set.of(high), Set.of()), List.of(high), new Position(0.5, 64, 0.5), Set.of()).isPresent());
        assertEquals(Optional.empty(), Clearing.next(with(Set.of(tooHigh), Set.of()), List.of(tooHigh), new Position(0.5, 64, 0.5), Set.of()));
    }

    @Test
    void neverStandsOnTheTreeItCuts() {
        // A stump one log high: the only cell over it is on the log itself.
        BlockPos stump = new BlockPos(3, 64, 3);
        WorldView world = with(Set.of(stump), Set.of());

        Clearing.Cut cut = Clearing.next(world, List.of(stump), new Position(3.5, 65, 3.5), Set.of()).orElseThrow();

        assertTrue(!cut.stand().equals(stump.offset(0, 1, 0)), "not on top of the stump: " + cut.stand());
    }

    /** Flat dirt ground, feet at y=64, with a mound of dirt two high at (2, 2) and one high at (-2, -2). */
    private static final WorldView MOUND = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63 || x == 2 && z == 2 && y <= 65 || x == -2 && z == -2 && y == 64;
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return isSolid(x, y, z) && y >= 60;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    @Test
    void highGroundIsDugOffATopBlockAtATime() {
        assertEquals(List.of(new BlockPos(-2, 64, -2), new BlockPos(2, 65, 2)), Clearing.humps(MOUND, CENTRE, 7, Set.of()),
                "each column's top block above the camp's ground, never one with another on it");
    }

    @Test
    void aHutIsNotHighGround() {
        assertEquals(List.of(new BlockPos(2, 65, 2)), Clearing.humps(MOUND, CENTRE, 7, Set.of(new BlockPos(-2, 64, -2))));
    }

    @Test
    void aHumpIsDugFromBesideItNotFromOnIt() {
        BlockPos hump = new BlockPos(-2, 64, -2);
        Clearing.Cut cut = Clearing.next(MOUND, List.of(hump), new Position(-1.5, 65, -1.5), Set.of()).orElseThrow();

        assertTrue(!cut.stand().equals(hump.offset(0, 1, 0)), "not on top of it: " + cut.stand());
    }

    @Test
    void anExcludedBlockIsLeftForAnother() {
        Set<BlockPos> logs = trunk(3, 3, 2);
        WorldView world = with(logs, Set.of());

        Clearing.Cut cut = Clearing.next(world, Clearing.cover(world, CENTRE, 8), new Position(0.5, 64, 0.5),
                Set.of(new BlockPos(3, 64, 3))).orElseThrow();

        assertEquals(new BlockPos(3, 65, 3), cut.block());
    }
}
