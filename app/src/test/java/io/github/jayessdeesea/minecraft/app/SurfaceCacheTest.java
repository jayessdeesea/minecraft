package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
