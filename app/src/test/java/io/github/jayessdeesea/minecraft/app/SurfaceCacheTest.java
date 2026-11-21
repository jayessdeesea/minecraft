package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A soldier under a tree's canopy isn't underground; one under rock is. Live (field598), Ned on the farm under an oak,
 * its top 13 over him, read as down the mine by day, and was called up to the mine's head 54 blocks off every couple of
 * seconds - his harvest walk to the wheat at (9, 63, 98) timed out 77 times over four runs.
 */
class SurfaceCacheTest {

    private static WorldView with(boolean leaves) {
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

    @Test
    void underATreesCanopyIsntUnderground() {
        Position ned = new Position(0.5, 67, 0.5);
        assertFalse(new SurfaceCache().underground(with(true), ned, SoldierRunner.UNDERGROUND, 0), "under an oak's leaves");
        assertTrue(new SurfaceCache().underground(with(false), ned, SoldierRunner.UNDERGROUND, 0), "under rock");
    }
}
