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
}
