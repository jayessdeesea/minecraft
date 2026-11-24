package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRouteTest {

    /** A hill west of x=0 with its top at y=72, the dug floor east of it at y=63; dug cells open. */
    private final Set<BlockPos> dug = new HashSet<>();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            if (dug.contains(new BlockPos(x, y, z))) {
                return false;
            }
            return x < 0 ? y <= 72 : y <= 63;
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

    /**
     * From a depot on the hilltop to the keep's floor below it (operator, 2026-11-15): a route carved
     * a step at a time, no more than a block down each, nothing put down, out of the keep till its
     * last cell - and, once cut, nothing left to cut.
     */
    @Test
    void aRouteFromTheHilltopDepotIsCarvedDownToTheFloorWithNothingPlaced() {
        BlockPos depot = new BlockPos(-6, 73, 0);
        java.util.function.Predicate<BlockPos> inKeep = c -> c.x() >= 0;
        AccessRoute route = AccessRoute.plan(world, depot, c -> inKeep.test(c) && c.y() == 64, inKeep).orElseThrow();

        assertEquals(depot, route.feet().getFirst());
        assertEquals(64, route.feet().getLast().y(), "down to the floor");
        for (int i = 1; i < route.feet().size(); i++) {
            BlockPos a = route.feet().get(i - 1), b = route.feet().get(i);
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), b + " beside " + a);
            assertTrue(Math.abs(a.y() - b.y()) <= 1, b + " a block at most from " + a);
            assertTrue(i == route.feet().size() - 1 || !inKeep.test(b), b + " out of the keep till the last");
        }
        assertTrue(route.floors().isEmpty(), "nothing put down: " + route.floors());
        assertTrue(!route.toCut().isEmpty(), "the hill's face cut for it");

        dug.addAll(route.toCut());
        assertTrue(AccessRoute.plan(world, depot, c -> inKeep.test(c) && c.y() == 64, inKeep).orElseThrow().cut(),
                "cut, the same route has nothing left to cut");
    }

    /** What stands in the one cell of a fence line at x=3 that a road may cross, on flat ground with feet at y=64. */
    private enum Fixture { SHUT_GATE, CHEST, TABLE }

    private static final BlockPos CROSSING = new BlockPos(3, 64, 0);

    /**
     * Flat ground with a fence along x=3 and {@code fixture} in its line at {@link #CROSSING}. A fence post and a shut gate
     * read solid in their own cell and in the cell over it, and are a block only in their own.
     */
    private static WorldView fenced(Fixture fixture) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                boolean tall = z != 0 || fixture == Fixture.SHUT_GATE;
                return y <= 63 || x == 3 && (y == 64 || y == 65 && tall);
            }

            @Override
            public boolean solidHere(int x, int y, int z) {
                return y <= 63 || x == 3 && y == 64;
            }

            @Override
            public boolean isGate(int x, int y, int z) {
                return fixture == Fixture.SHUT_GATE && CROSSING.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isShutGate(int x, int y, int z) {
                return isGate(x, y, z);
            }

            @Override
            public boolean isChest(int x, int y, int z) {
                return fixture == Fixture.CHEST && CROSSING.equals(new BlockPos(x, y, z));
            }

            @Override
            public java.util.Optional<io.github.jayessdeesea.minecraft.bot.world.Workstation> workstation(int x, int y, int z) {
                return fixture == Fixture.TABLE && CROSSING.equals(new BlockPos(x, y, z))
                        ? java.util.Optional.of(io.github.jayessdeesea.minecraft.bot.world.Workstation.CRAFTING_TABLE) : java.util.Optional.empty();
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

    /** The fence line but its crossing, at every height: the road's keep-out, so the only way is through {@link #CROSSING}. */
    private static final java.util.function.Predicate<BlockPos> LINE_BUT_CROSSING = c -> c.x() == 3 && !c.equals(CROSSING);

    private static final java.util.function.Predicate<BlockPos> PAST_THE_LINE = c -> c.x() == 6 && c.y() == 64;

    /**
     * A road whose only way lies through a shut gate is planned through it, and lists the gate's cell and the cell over it
     * as rock to cut: a shut gate reads solid, and so does the cell over a tall block, which is no block. Pinned as it
     * stands, ahead of backlog/kept-ground.md, which has the road neither cut nor pass a gate.
     */
    @Test
    void aRoadThroughAShutGateListsTheGateAndTheCellOverItToCut() {
        AccessRoute route = AccessRoute.plan(fenced(Fixture.SHUT_GATE), new BlockPos(0, 64, 0), PAST_THE_LINE, LINE_BUT_CROSSING).orElseThrow();

        assertTrue(route.feet().contains(CROSSING), "through the gate's cell: " + route.feet());
        assertEquals(List.of(CROSSING, CROSSING.offset(0, 1, 0)), route.toCut(), "the gate, and the cell over it");
        assertTrue(route.floors().isEmpty(), "nothing put down: " + route.floors());
        assertEquals(java.util.Optional.of(AccessRoute.STEP + 2 * AccessRoute.DIG),
                AccessRoute.stepCost(fenced(Fixture.SHUT_GATE), new BlockPos(2, 64, 0), CROSSING), "a step and two digs");
    }

    /**
     * Left free to, a road crosses a fence over its gate: the cell over the gate cut, the gate its floor - a dig cheaper
     * than through it. Pinned as it stands, ahead of backlog/kept-ground.md.
     */
    @Test
    void aRoadFreeToCrossesOverAShutGateStandingOnIt() {
        AccessRoute route = AccessRoute.plan(fenced(Fixture.SHUT_GATE), new BlockPos(0, 64, 0), PAST_THE_LINE, c -> false).orElseThrow();

        assertTrue(route.feet().contains(CROSSING.offset(0, 1, 0)), "over the gate: " + route.feet());
        assertEquals(List.of(CROSSING.offset(0, 1, 0)), route.toCut(), "the cell over the gate only");
        assertTrue(route.floors().isEmpty(), "the gate its floor: " + route.floors());
    }

    /** A road is never planned through a chest's cell or a workstation's: with no other way, there is no road. */
    @Test
    void aRoadIsNeverPlannedThroughAChestOrAWorkstation() {
        for (Fixture fixture : List.of(Fixture.CHEST, Fixture.TABLE)) {
            WorldView world = fenced(fixture);
            assertEquals(java.util.Optional.empty(), AccessRoute.stepCost(world, new BlockPos(2, 64, 0), CROSSING), fixture + ": no step into it");
            assertEquals(java.util.Optional.empty(), AccessRoute.stepCost(world, new BlockPos(2, 63, 0), CROSSING.offset(0, -1, 0)),
                    fixture + ": nor with it for head room");
            assertEquals(java.util.Optional.empty(), AccessRoute.plan(world, new BlockPos(0, 64, 0), PAST_THE_LINE, LINE_BUT_CROSSING),
                    fixture + ": no road with no other way");

            AccessRoute over = AccessRoute.plan(world, new BlockPos(0, 64, 0), PAST_THE_LINE, c -> false).orElseThrow();
            assertTrue(!over.feet().contains(CROSSING) && !over.toCut().contains(CROSSING), fixture + ": free to, never through it: " + over.feet());
        }
    }
}
