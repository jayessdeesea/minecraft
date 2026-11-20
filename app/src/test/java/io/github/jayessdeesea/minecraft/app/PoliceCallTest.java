package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoliceCallTest {

    private static final int GROUND = 63;
    private static final Position AT = new Position(0.5, GROUND + 1, 0.5);

    /** Flat ground at {@link #GROUND}, and dirt in {@code placed}. */
    private static WorldView world(Set<BlockPos> placed) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND || placed.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
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
    }

    /** A pillar two high left on the flat: its top first, then its foot. */
    @Test
    void aPillarLeftOnTheFlatIsTakenBackTopFirst() {
        BlockPos foot = new BlockPos(4, GROUND + 1, 0);
        BlockPos top = new BlockPos(4, GROUND + 2, 0);
        Set<BlockPos> steps = Set.of(foot, top);

        assertEquals(Optional.of(top), PoliceCall.litter(world(steps), steps, AT, List.of(), false, Set.of()));
        assertEquals(Optional.of(foot), PoliceCall.litter(world(Set.of(foot)), steps, AT, List.of(), false, Set.of()));
    }

    /** A step that filled a hole, flush with the ground, isn't litter: taken back, the hole is open again. */
    @Test
    void aStepFlushWithTheGroundIsLeft() {
        BlockPos filled = new BlockPos(4, GROUND, 0);

        assertEquals(Optional.empty(), PoliceCall.litter(world(Set.of()), Set.of(filled), AT, List.of(), false, Set.of()));
    }

    /** Not one a soldier stands on, nor a wall's, nor one beyond its neighbourhood. */
    @Test
    void notOneStoodOnKeptOrFarOff() {
        BlockPos under = new BlockPos(4, GROUND + 1, 0);
        BlockPos far = new BlockPos(40, GROUND + 1, 0);
        Set<BlockPos> steps = Set.of(under, far);
        WorldView w = world(steps);

        assertEquals(Optional.empty(), PoliceCall.litter(w, steps, AT, List.of(new Position(4.5, GROUND + 2, 0.5)), false, Set.of()));
        assertEquals(Optional.empty(), PoliceCall.litter(w, steps, AT, List.of(), false, Set.of(under)));
    }

    /** Not one far over its head: a soldier down a mine isn't given the surface's litter (field519). */
    @Test
    void notOneFarOverItsHead() {
        BlockPos up = new BlockPos(4, GROUND + 1, 0);

        assertEquals(Optional.empty(), PoliceCall.litter(world(Set.of(up)), Set.of(up), new Position(0.5, 20, 0.5), List.of(), false, Set.of()));
    }

    /** A step in hand is kept till it's gone, given up on after a minute and passed over, and dropped for other work. */
    @Test
    void aStepInHandIsKeptTillGoneOrGivenUp() {
        BlockPos step = new BlockPos(4, GROUND + 1, 0);
        Set<BlockPos> steps = Set.of(step);
        WorldView w = world(steps);
        PoliceCall call = new PoliceCall();
        java.util.function.Function<Set<BlockPos>, Optional<BlockPos>> find =
                skip -> PoliceCall.litter(w, steps, AT, List.of(), false, skip);

        assertEquals(Optional.of(step), call.step("Alice", false, true, w, 0, find));
        assertEquals(Optional.of(step), call.step("Alice", false, false, w, 30_000, find), "kept on the way, no longer stood idle");
        assertEquals(Optional.empty(), call.step("Bob", false, true, w, 30_000, find), "not given to two");
        assertEquals(Optional.empty(), call.step("Alice", false, true, w, PoliceCall.GIVE_UP_MILLIS, find), "given up on, passed over");
        assertEquals(Optional.of(step), call.step("Alice", false, true, w, PoliceCall.GIVE_UP_MILLIS + PoliceCall.PASSED_MILLIS, find));
        assertEquals(Optional.empty(), call.step("Alice", true, true, w, PoliceCall.GIVE_UP_MILLIS + PoliceCall.PASSED_MILLIS + 1, find), "other work first");
    }

    /**
     * Stood on a "no route" with a pickaxe and no block, in a gallery cut through stone: a stone beside its head to cut -
     * a block to bridge with - not its floor (field512-524, Eli at the gap in his gallery's floor).
     */
    @Test
    void aSoldierWithNoRouteAndNoBlockCutsAStoneBesideItsHead() {
        WorldView gallery = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND + 1 || z != 0 || y > GROUND + 2;
            }

            @Override
            public boolean isStone(int x, int y, int z) {
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
        io.github.jayessdeesea.minecraft.command.Kit noBlocks = new io.github.jayessdeesea.minecraft.command.Kit(Optional.of("stone_sword"),
                0, 0, 0, 0, 0, 16, false, true, 0, false, 0, 1, 0);
        io.github.jayessdeesea.minecraft.command.SoldierStatus eli = new io.github.jayessdeesea.minecraft.command.SoldierStatus("Eli", AT,
                new io.github.jayessdeesea.minecraft.command.Order.Idle(), new io.github.jayessdeesea.minecraft.command.Report.Cantco("no route from (0, 64, 0) to within 0 of (6, 64, 0)"),
                Optional.of(new io.github.jayessdeesea.minecraft.command.Condition(20, 20, 0, Optional.of(noBlocks))));

        PoliceCall call = new PoliceCall();
        assertEquals(Optional.empty(), call.order(eli, false, false, gallery, Set.of(), java.util.Map.of(), List.of(), Set.of(), 0),
                "not at once: its route may come");
        Optional<io.github.jayessdeesea.minecraft.command.Order> cut = call.order(eli, false, false, gallery, Set.of(), java.util.Map.of(),
                List.of(), Set.of(), PoliceCall.GIVE_UP_MILLIS);

        assertEquals(Optional.of(new BlockPos(0, GROUND + 2, 1)), PoliceCall.wayBlock(gallery, AT));
        assertEquals(true, cut.isPresent() && cut.get() instanceof io.github.jayessdeesea.minecraft.command.Order.Dig d && d.box().minY() == GROUND + 2,
                "a stone by its head, not its floor: " + cut);
    }
}
