package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSiteTest {

    private static final int GROUND = 63;
    private static final ChunkFarm FARM = new ChunkFarm(0, 0, GROUND);

    /** A world: solid where {@code solid}, water where {@code water} (sources all), farmland, wheat, torches, gates as given. */
    private static WorldView world(Predicate<BlockPos> solid, Predicate<BlockPos> water, Predicate<BlockPos> farmland,
                                   Predicate<BlockPos> wheat, Set<BlockPos> torches, Set<BlockPos> gates) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return solid.test(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return water.test(new BlockPos(x, y, z));
            }

            @Override
            public boolean isWaterSource(int x, int y, int z) {
                return water.test(new BlockPos(x, y, z));
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return farmland.test(new BlockPos(x, y, z));
            }

            @Override
            public OptionalInt wheatAge(int x, int y, int z) {
                return wheat.test(new BlockPos(x, y, z)) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public boolean isTorch(int x, int y, int z) {
                return torches.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isGate(int x, int y, int z) {
                return gates.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    /** Flat grass at its level, nothing done: its holes to dig and pour, its ground to till and sow, all to put up. */
    @Test
    void aFlatSiteHasItsHolesToDigFirstThenItsWaterAndCrop() {
        WorldView flat = world(c -> c.y() <= GROUND, c -> false, c -> false, c -> false, Set.of(), Set.of());

        FarmSite.Work work = FarmSite.work(flat, FARM);

        int postTorches = FARM.torches().size() - 4;
        assertEquals(new FarmSite.Work(4, 0, 0, 4, 216, 216, 4, FARM.fence().size() + postTorches, FARM.gates().size()), work, "a torch on a post's top the fence's");
        assertEquals(FarmSite.Stage.SURVEYED, work.stage(), "its holes still to dig");
        assertEquals(new FarmSite.Supply(0, 216, 4, true), work.toProducing(), "seeds, torches and a bucket - no blocks");
    }

    /** Over a shallow pond at its level: its ground built up, its holes the water there already - no bucket, no iron. */
    @Test
    void aSiteOverShallowWaterWantsBlocksAndNoBucket() {
        Set<BlockPos> holes = Set.copyOf(FARM.holes());
        Predicate<BlockPos> pond = c -> c.y() == GROUND && c.x() > 0 && c.x() < 16 && c.z() > 0 && c.z() < 16;
        WorldView water = world(c -> c.y() < GROUND || c.y() == GROUND && !pond.test(c), pond, c -> false, c -> false, Set.of(), Set.of());

        FarmSite.Work work = FarmSite.work(water, FARM);

        assertEquals(216 + 5, work.fill(), "its ground built up over the pond, and what its four field torches and its chest stand on");
        assertEquals(0, work.pours(), "its holes the pond's own water");
        assertFalse(work.toProducing().bucket(), "no bucket: no iron");
        assertTrue(holes.stream().allMatch(h -> pond.test(h)));
    }

    /** Tilled, sown, watered and lit: Producing; and its fence and gates up, Fenced. */
    @Test
    void aTilledSownLitSiteIsProducingAndFencedOnceItsFenceIsUp() {
        Set<BlockPos> holes = Set.copyOf(FARM.holes());
        Set<BlockPos> ground = Set.copyOf(FARM.ground());
        Set<BlockPos> posts = Set.copyOf(FARM.fence());
        Predicate<BlockPos> solidBut = c -> c.y() < GROUND || c.y() == GROUND && !holes.contains(c);
        WorldView producing = world(solidBut, holes::contains, ground::contains, c -> c.y() == GROUND + 1 && ground.contains(c.offset(0, -1, 0)),
                Set.copyOf(FARM.torches()), Set.of());
        WorldView fenced = world(c -> solidBut.test(c) || posts.contains(c), holes::contains, ground::contains,
                c -> c.y() == GROUND + 1 && ground.contains(c.offset(0, -1, 0)), Set.copyOf(FARM.torches()), Set.copyOf(FARM.gates()));

        assertEquals(FarmSite.Stage.PRODUCING, FarmSite.stage(producing, FARM));
        assertEquals(FarmSite.Stage.FENCED, FarmSite.stage(fenced, FARM));
    }
}
