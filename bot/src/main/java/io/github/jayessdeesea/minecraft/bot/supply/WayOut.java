package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The way out for a soldier boxed in - a hut it was sealed in before a restart, whose doorway no
 * one knows to cut: the wall at its feet and head on the side nearest where it's been sent, to break
 * from where it stands ({@link Breacher}). A route never tunnels, so boxed in it would stand there
 * for good. Live, three soldiers stood in a sealed hut a whole day after a restart, every move a
 * "no route" CANTCO; and three more in a trench between the camp's wall and old cobblestone heaps.
 *
 * <p>Pure: from the world and where it stands.
 */
public final class WayOut {

    /**
     * Cells a soldier can walk to at or under which it's boxed in: a hut's floor, or a trench cut off
     * between a wall and old heaps - live, three soldiers stood a day in one beside the camp's wall,
     * too big for a hut's 24. Under the walled camp's own floor (17 x 17), which is never a pocket.
     */
    public static final int BOXED_IN = 128;

    private WayOut() {
    }

    /**
     * The cells to break for the soldier at {@code feet} sent to {@code target}: empty unless it's
     * boxed in, and then the soil or stone of the wall column - feet and head height - beside the
     * cells it can reach, the one nearest the target.
     */
    public static List<BlockPos> cells(WorldView world, BlockPos feet, Position target) {
        Set<BlockPos> room = walkable(world, feet);
        if (room.size() > BOXED_IN || room.stream().anyMatch(c -> dropBeside(world, c, room))) {
            return List.of();
        }
        Optional<BlockPos> wall = room.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.offset(1, 0, 0), c.offset(-1, 0, 0), c.offset(0, 0, 1), c.offset(0, 0, -1)))
                .filter(w -> !room.contains(w) && (diggable(world, w) || diggable(world, w.offset(0, 1, 0))))
                .min(Comparator.comparingDouble((BlockPos w) -> Math.hypot(w.x() + 0.5 - target.x(), w.z() + 0.5 - target.z()))
                        .thenComparing(BlockPos::y).thenComparing(BlockPos::x).thenComparing(BlockPos::z));
        return wall.map(w -> java.util.stream.Stream.of(w.offset(0, 1, 0), w).filter(c -> diggable(world, c)).toList()).orElse(List.of());
    }

    /**
     * The cells it can walk to from {@code feet}, up to one more than {@link #BOXED_IN}: the route
     * planner's own moves ({@link Pathfinder#reachable}), so what reads as a way out is one a route
     * can take. Live, a flood of its own stepped down under an overhang no soldier fits under, read
     * a trench of six cells as open to a quarry of 130, and broke nothing.
     */
    static Set<BlockPos> walkable(WorldView world, BlockPos feet) {
        return Pathfinder.reachable(world, feet, BOXED_IN, BOXED_IN + 1);
    }

    /**
     * Whether a side of {@code c} outside the room is open at feet and head - a drop off a ledge,
     * not a wall: a soldier on a ledge is stranded, not boxed in, and breaking blocks won't bring it
     * down - building its stair will.
     */
    private static boolean dropBeside(WorldView world, BlockPos c, Set<BlockPos> room) {
        return java.util.stream.Stream.of(c.offset(1, 0, 0), c.offset(-1, 0, 0), c.offset(0, 0, 1), c.offset(0, 0, -1))
                .anyMatch(n -> !room.contains(n) && !world.isSolid(n.x(), n.y(), n.z()) && !world.isSolid(n.x(), n.y() + 1, n.z())
                        && !ontoTheFarm(world, n));
    }

    /**
     * Whether going over the side into {@code n} lands on a farm - its wheat, or its farmland: no
     * route treads there, so it's no way down and the side is a wall. Live, four soldiers stood two
     * days in a pocket by the camp's farm whose one open side dropped into the wheat, read as a
     * ledge, and nothing broke them out.
     */
    private static boolean ontoTheFarm(WorldView world, BlockPos n) {
        for (int down = 0; down <= 3; down++) {
            BlockPos c = n.offset(0, -down, 0);
            if (world.wheatAge(c.x(), c.y(), c.z()).isPresent() || world.isFarmland(c.x(), c.y() - 1, c.z())) {
                return true;
            }
            if (world.isSolid(c.x(), c.y() - 1, c.z())) {
                return false;
            }
        }
        return false;
    }

    private static boolean diggable(WorldView world, BlockPos c) {
        return world.isSolid(c.x(), c.y(), c.z()) && Clearing.diggable(world, c.x(), c.y(), c.z());
    }
}
