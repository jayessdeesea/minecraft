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
        if (room.size() > BOXED_IN) {
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
     * The cells it can walk to from {@code feet} - level, a step up or a step down - up to one more
     * than {@link #BOXED_IN}: no steps cut, as {@link Pathfinder#reachable} cuts them through soil.
     */
    static Set<BlockPos> walkable(WorldView world, BlockPos feet) {
        Set<BlockPos> seen = new java.util.HashSet<>(List.of(feet));
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>(List.of(feet));
        while (!frontier.isEmpty() && seen.size() <= BOXED_IN) {
            BlockPos c = frontier.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos n = c.offset(d[0], dy, d[1]);
                    boolean headroom = dy <= 0 || !world.isSolid(c.x(), c.y() + 2, c.z());
                    if (headroom && Pathfinder.standable(world, n) && seen.add(n)) {
                        frontier.add(n);
                        break;
                    }
                }
            }
        }
        return seen;
    }

    private static boolean diggable(WorldView world, BlockPos c) {
        return world.isSolid(c.x(), c.y(), c.z()) && Clearing.diggable(world, c.x(), c.y(), c.z());
    }
}
