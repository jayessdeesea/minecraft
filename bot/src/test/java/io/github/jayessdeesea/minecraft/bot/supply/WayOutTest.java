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

    /** Flat cobblestone - no step a route cuts by hand - and a hut of it over (0, 0): walls two high round 3x3, a roof over. */
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
            public boolean isCobblestone(int x, int y, int z) {
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

    /** A trench two wide and thirty long, cut three deep into cobblestone: walled all round, open to the sky. */
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
            public boolean isCobblestone(int x, int y, int z) {
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

    /**
     * The hut, its east wall's foot opened onto ground a block lower - a step down no soldier fits
     * through, with the wall's top over it.
     */
    private static WorldView hutWithAnOverhang() {
        WorldView hut = hut(true);
        return new WorldView() {
            private boolean solid(int x, int y, int z) {
                boolean gap = x == 2 && z == 0 && y == GROUND;
                boolean lower = x >= 2 && y == GROUND - 1;
                return hut.isSolid(x, y, z) && !gap && !lower;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return solid(x, y, z);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return solid(x, y, z);
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
    void aStepDownUnderAnOverhangIsNoWayOut() {
        assertEquals(List.of(new BlockPos(2, GROUND + 1, 0)),
                WayOut.cells(hutWithAnOverhang(), new BlockPos(0, GROUND, 0), new Position(10.5, GROUND, 0.5)),
                "the wall's top over the step broken, its foot already open");
    }

    @Test
    void strandedOnALedgeItsNotBoxedIn() {
        WorldView pillar = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || x == 0 && z == 0 && y < GROUND + 12;
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return isSolid(x, y, z);
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
        assertEquals(List.of(), WayOut.cells(pillar, new BlockPos(0, GROUND + 12, 0), new Position(10.5, GROUND, 0.5)),
                "a drop all round: the stair down is its way, not a wall");
    }

    @Test
    void notBoxedInThereIsNothingToBreak() {
        assertEquals(List.of(), WayOut.cells(hut(true), new BlockPos(10, GROUND, 0), new Position(0.5, GROUND, 0.5)),
                "outside the hut, on open ground");
    }
}
