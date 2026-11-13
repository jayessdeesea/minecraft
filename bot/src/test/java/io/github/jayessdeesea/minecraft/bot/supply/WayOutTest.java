package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WayOutTest {

    private static final int GROUND = 64;

    /** Flat soil, and a dirt hut over (0, 0): walls two high round 3x3, a roof over. */
    private static WorldView hut(boolean roofed) {
        return new WorldView() {
            private boolean dirt(int x, int y, int z) {
                boolean wall = (Math.abs(x) == 2 && Math.abs(z) <= 2 || Math.abs(z) == 2 && Math.abs(x) <= 2) && (y == GROUND || y == GROUND + 1);
                boolean roof = roofed && Math.abs(x) <= 2 && Math.abs(z) <= 2 && y == GROUND + 2;
                return y < GROUND || wall || roof;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return dirt(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return dirt(x, y, z);
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
    void boxedInItBreaksTheWallNearestWhereItsSent() {
        assertEquals(List.of(new BlockPos(2, GROUND + 1, 0), new BlockPos(2, GROUND, 0)),
                WayOut.cells(hut(true), new BlockPos(0, GROUND, 0), new Position(10.5, GROUND, 0.5)));
    }

    /** A trench two wide and thirty long, cut into the ground: walled all round, open to the sky. */
    private static WorldView trench() {
        return new WorldView() {
            private boolean dirt(int x, int y, int z) {
                boolean cut = x >= 0 && x < 2 && z >= 0 && z < 30 && y >= GROUND - 3;
                return y < GROUND && !cut;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return dirt(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return dirt(x, y, z);
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
    void cutOffInATrenchTooLongForAHutItBreaksTheWallNearestWhereItsSent() {
        List<BlockPos> cells = WayOut.cells(trench(), new BlockPos(0, GROUND - 3, 5), new Position(10.5, GROUND, 5.5));
        assertEquals(List.of(new BlockPos(2, GROUND - 2, 5), new BlockPos(2, GROUND - 3, 5)), cells);
    }

    @Test
    void notBoxedInThereIsNothingToBreak() {
        assertEquals(List.of(), WayOut.cells(hut(true), new BlockPos(10, GROUND, 0), new Position(0.5, GROUND, 0.5)),
                "outside the hut, on open ground");
    }
}
