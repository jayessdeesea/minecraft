package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceCacheTest {

    /** Ground up to y=63; with {@code roofed}, a roof at y=80 over it. */
    private static WorldView world(boolean[] roofed) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || roofed[0] && y == 80;
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
    }

    /** A column's surface is scanned once and kept a while: a roof put up over it is read once that's past. */
    @Test
    void aColumnsSurfaceIsKeptAWhileThenScannedAgain() {
        boolean[] roofed = {false};
        WorldView world = world(roofed);
        SurfaceCache cache = new SurfaceCache();
        Position under = new Position(0.5, 64, 0.5);

        assertFalse(cache.underground(world, under, 6, 0), "open sky over it");
        roofed[0] = true;
        assertFalse(cache.underground(world, under, 6, SurfaceCache.KEPT_MILLIS), "the column as it was, still kept");
        assertTrue(cache.underground(world, under, 6, SurfaceCache.KEPT_MILLIS + 1), "scanned again: the roof 16 over it");
    }

    /**
     * A soldier under a tree's canopy isn't underground; one under rock is. Live (field598), Ned on the farm under an oak,
     * its top 13 over him, read as down the mine by day, and was called up to the mine's head 54 blocks off every couple
     * of seconds - his harvest walk to the wheat at (9, 63, 98) timed out 77 times over four runs.
     */
    @Test
    void underATreesCanopyIsntUnderground() {
        Position ned = new Position(0.5, 67, 0.5);
        assertFalse(new SurfaceCache().underground(oak(true), ned, SoldierRunner.UNDERGROUND, 0), "under an oak's leaves");
        assertTrue(new SurfaceCache().underground(oak(false), ned, SoldierRunner.UNDERGROUND, 0), "under rock");
    }

    /** Ground up to y=66, and blocks at y=75-79 over it - leaves, or with {@code leaves} false, rock. */
    private static WorldView oak(boolean leaves) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < 67 || y >= 75 && y <= 79;
            }

            @Override
            public Optional<String> leaves(int x, int y, int z) {
                return leaves && y >= 75 && y <= 79 ? Optional.of("oak") : Optional.empty();
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
    }
}
