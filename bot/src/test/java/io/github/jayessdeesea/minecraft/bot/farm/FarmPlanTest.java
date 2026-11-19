package io.github.jayessdeesea.minecraft.bot.farm;

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

class FarmPlanTest {

    private static final int GROUND = 63;

    /** Grass ground at y=63, with water sources at {@code water} and stone wherever {@code stone} says. */
    private static WorldView ground(Set<BlockPos> water, Set<BlockPos> stone) {
        return ground(water, stone, Set.of());
    }

    /** As {@link #ground(Set, Set)}, open air dug out wherever {@code air} says. */
    private static WorldView ground(Set<BlockPos> water, Set<BlockPos> stone, Set<BlockPos> air) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return y <= GROUND && !water.contains(c) && !air.contains(c) || stone.contains(c);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return water.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isWaterSource(int x, int y, int z) {
                return water.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return y == GROUND && !water.contains(c) && !stone.contains(c) && !air.contains(c);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void aFarmIsTheHydratedGroundRoundOneWaterSource() {
        BlockPos pond = new BlockPos(10, GROUND, 0);
        FarmPlan plan = FarmPlan.site(ground(Set.of(pond), Set.of()), new BlockPos(0, GROUND, 0), 16).orElseThrow();

        assertEquals(pond, plan.water());
        assertEquals(80, plan.plot().size(), "9x9 round the water, less the water itself");
        assertTrue(plan.plot().stream().allMatch(c -> Math.abs(c.x() - pond.x()) <= FarmPlan.HYDRATION
                && Math.abs(c.z() - pond.z()) <= FarmPlan.HYDRATION && c.y() == GROUND));
    }

    @Test
    void aFarmIsGrownWithThePlotRoundTheNextWater() {
        BlockPos first = new BlockPos(0, GROUND, 0);
        BlockPos second = new BlockPos(8, GROUND, 0);
        WorldView world = ground(Set.of(first, second), Set.of());
        FarmPlan farm = FarmPlan.site(world, first, 0).orElseThrow();

        FarmPlan grown = FarmPlan.grown(world, farm, 96, 4, cell -> true);

        assertEquals(first, grown.water(), "kept by its first water");
        assertTrue(grown.plot().containsAll(farm.plot()), "its own cells kept");
        assertEquals(80 + 71, grown.plot().size(), "and the second's, less the column of 9 they share");
        assertEquals(grown.plot().size(), Set.copyOf(grown.plot()).size(), "no cell twice");
    }

    /** With no other water near, a farm grows along a trench cut off its pond: seven long, the ground beside it hydrated. */
    @Test
    void aFarmWithNoOtherWaterNearGrowsAlongATrenchOffItsPond() {
        BlockPos pond = new BlockPos(0, GROUND, 0);
        WorldView world = ground(Set.of(pond, new BlockPos(FarmPlan.GROWN_RADIUS + 10, GROUND, 0)), Set.of());
        FarmPlan farm = FarmPlan.site(world, pond, 0).orElseThrow();

        FarmPlan grown = FarmPlan.grown(world, farm, 96, 4, cell -> true);

        assertEquals(List.of(new BlockPos(1, GROUND, 0), new BlockPos(2, GROUND, 0), new BlockPos(3, GROUND, 0), new BlockPos(4, GROUND, 0),
                new BlockPos(5, GROUND, 0), new BlockPos(6, GROUND, 0), new BlockPos(7, GROUND, 0)), grown.trenches());
        assertEquals(80 - 4 + 60, grown.plot().size(), "the four plot cells it's cut through lost, 9x7 beyond it gained less its own three");
        assertTrue(grown.plot().stream().noneMatch(grown.trenches()::contains), "a trench isn't tilled");
        assertTrue(grown.plot().stream().allMatch(c -> Math.abs(c.z()) <= FarmPlan.HYDRATION && c.x() >= -4 && c.x() <= 7 + FarmPlan.HYDRATION),
                "every cell within reach of the pond's water or the trench's");
        assertEquals(grown, FarmPlan.grown(world, grown, 96, 4, cell -> true), "big enough: as it is");
    }

    /** A trench ends against ground: cut up to an open drop, the water would spill out onto it. */
    @Test
    void aTrenchEndsAgainstGround() {
        BlockPos pond = new BlockPos(0, GROUND, 0);

        assertEquals(List.of(new BlockPos(1, GROUND, 0), new BlockPos(2, GROUND, 0), new BlockPos(3, GROUND, 0)),
                FarmPlan.trench(ground(Set.of(pond), Set.of(new BlockPos(4, GROUND, 0))), pond, 1, 0, List.of()), "up to stone");
        assertEquals(List.of(new BlockPos(1, GROUND, 0), new BlockPos(2, GROUND, 0)),
                FarmPlan.trench(ground(Set.of(pond), Set.of(), Set.of(new BlockPos(4, GROUND, 0))), pond, 1, 0, List.of()),
                "short of the cell before a hole, which holds the water in");
        assertEquals(List.of(), FarmPlan.trench(ground(Set.of(pond), Set.of(), Set.of(new BlockPos(1, GROUND, 1))), pond, 1, 0, List.of()),
                "none past a hole at its side");
    }

    @Test
    void groundNoFarmerCanWalkToIsNoFarm() {
        BlockPos pond = new BlockPos(10, GROUND, 0);

        assertTrue(FarmPlan.site(ground(Set.of(pond), Set.of()), new BlockPos(0, GROUND, 0), 16, 4, cell -> false).isEmpty(),
                "a pond across water nobody can reach is no farm");
        FarmPlan half = FarmPlan.site(ground(Set.of(pond), Set.of()), new BlockPos(0, GROUND, 0), 16, 4, cell -> cell.x() <= 10).orElseThrow();
        assertTrue(half.plot().stream().allMatch(c -> c.x() <= 10), "only the walkable side: " + half.plot());
    }

    @Test
    void groundThatWontTillOrIsCoveredIsLeftOutAndTooLittleIsNoFarm() {
        BlockPos pond = new BlockPos(0, GROUND, 0);
        Set<BlockPos> stone = new HashSet<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
                    stone.add(new BlockPos(dx, GROUND, dz)); // all but the ring next to the water is stone
                }
            }
        }
        assertEquals(Optional.empty(), FarmPlan.site(ground(Set.of(pond), stone), new BlockPos(0, GROUND, 0), 16),
                "8 cells is less than a farm's worth");
    }
}
