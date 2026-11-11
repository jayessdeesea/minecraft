package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The engineer's stair down from a ledge (docs/military-doctrine.md § 7): a flight of steps one
 * block down and one along, from the ledge's edge to the ground, laid out as the cells to fill, so
 * a soldier with blocks builds it with {@code Order.Place} and knows nothing of stairs.
 *
 * <p>Each step stands on a block of its own, beside the step below it: that block is what the step
 * is placed against, since a step only touches the one below it at a corner. Two blocks a step,
 * built from the ground up, so every block goes against one already in: the bottom step against
 * the ground, then each block against the last. Over each step the air a soldier walks up through
 * stays clear.
 */
public final class Stairway {

    /** Steps at most: a ledge higher than this is no stair's to reach. */
    static final int MAX_STEPS = 40;
    /** How far along the ledge its edge is looked for. */
    static final int EDGE_SEARCH = 8;
    private static final int[][] HEADINGS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Stairway() {
    }

    /**
     * The cells of the shortest stair from {@code feet} - where a soldier stands on the ledge - down
     * to the ground, in the order they're built: the bottom first. Empty if it needs none (the drop
     * is a step), or if none can be laid: no edge near, a wall in the way, water below, or ground
     * further down than {@link #MAX_STEPS}.
     */
    public static Optional<List<BlockPos>> down(WorldView world, BlockPos feet) {
        return Arrays.stream(HEADINGS)
                .map(h -> flight(world, feet, h[0], h[1]))
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(List::size));
    }

    private static Optional<List<BlockPos>> flight(WorldView world, BlockPos feet, int dx, int dz) {
        // The ledge's edge: the first cell along this heading with no floor at the ledge's level.
        BlockPos edge = null;
        for (int i = 1; i <= EDGE_SEARCH; i++) {
            BlockPos cell = new BlockPos(feet.x() + dx * i, feet.y(), feet.z() + dz * i);
            if (!open(world, cell) || !open(world, cell.offset(0, 1, 0))) {
                return Optional.empty(); // a wall, not an edge
            }
            if (!world.isSolid(cell.x(), cell.y() - 1, cell.z())) {
                edge = cell;
                break;
            }
        }
        if (edge == null) {
            return Optional.empty();
        }
        // Step k, from the top: one along and one down from the last.
        List<BlockPos[]> steps = new ArrayList<>(); // {step, block under it, or null where solid}
        for (int k = 1; k <= MAX_STEPS + 1; k++) {
            BlockPos step = new BlockPos(edge.x() + dx * (k - 1), feet.y() - 1 - k, edge.z() + dz * (k - 1));
            if (world.isSolid(step.x(), step.y(), step.z())) {
                // The ground: stepped down onto from the last step, into air enough for a body.
                if (k == 1 || !Pathfinder.standable(world, step.offset(0, 1, 0))) {
                    return Optional.empty();
                }
                List<BlockPos> plan = new ArrayList<>();
                for (BlockPos[] s : steps.reversed()) {
                    if (s[1] != null) {
                        plan.add(s[1]);
                    }
                    plan.add(s[0]);
                }
                return Optional.of(List.copyOf(plan));
            }
            if (k > MAX_STEPS || world.isHazard(step.x(), step.y(), step.z())) {
                return Optional.empty();
            }
            for (int up = 1; up <= 3; up++) {
                if (!open(world, new BlockPos(step.x(), step.y() + up, step.z()))) {
                    return Optional.empty();
                }
            }
            BlockPos under = new BlockPos(step.x(), step.y() - 1, step.z());
            if (world.isHazard(under.x(), under.y(), under.z())) {
                return Optional.empty();
            }
            steps.add(new BlockPos[]{step, world.isSolid(under.x(), under.y(), under.z()) ? null : under});
        }
        return Optional.empty();
    }

    /** Steps at most up out of a pocket: deeper than this is the mine's, not a pocket's. */
    static final int MAX_STEPS_UP = 12;

    /**
     * The cells to dig for the shortest stair from {@code feet} - where a soldier stands boxed in
     * below the ground - up and out, one block up and one along a step: each step's body room, and
     * the head room over the step before it, so the soldier jumps up into it. Empty if none can be
     * laid: on every heading a cell no clear digs (cobblestone, a table), water, no floor to step
     * onto, or more than {@link #MAX_STEPS_UP} steps. Live, a soldier waited two runs in a pocket
     * its squad had no stair down for (docs/military-doctrine.md § 7).
     */
    public static Optional<List<BlockPos>> up(WorldView world, BlockPos feet) {
        return Arrays.stream(HEADINGS)
                .map(h -> flightUp(world, feet, h[0], h[1]))
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(List::size));
    }

    private static Optional<List<BlockPos>> flightUp(WorldView world, BlockPos feet, int dx, int dz) {
        List<BlockPos> dig = new ArrayList<>();
        BlockPos last = feet;
        for (int k = 1; k <= MAX_STEPS_UP; k++) {
            BlockPos step = new BlockPos(feet.x() + dx * k, feet.y() + k, feet.z() + dz * k);
            if (!world.isSolid(step.x(), step.y() - 1, step.z()) || world.isHazard(step.x(), step.y() - 1, step.z())) {
                return Optional.empty(); // nothing to step up onto
            }
            for (BlockPos cell : List.of(last.offset(0, 2, 0), step, step.offset(0, 1, 0))) {
                if (world.isHazard(cell.x(), cell.y(), cell.z())) {
                    return Optional.empty();
                }
                if (world.isSolid(cell.x(), cell.y(), cell.z())) {
                    if (!Clearing.diggable(world, cell.x(), cell.y(), cell.z())) {
                        return Optional.empty();
                    }
                    dig.add(cell);
                }
            }
            // Out: open sky over the step, the ground's own level.
            if (!world.isSolid(step.x(), step.y() + 2, step.z()) && !world.isSolid(step.x(), step.y() + 3, step.z())
                    && !world.isSolid(step.x() + dx, step.y(), step.z() + dz)) {
                return dig.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(dig));
            }
            last = step;
        }
        return Optional.empty();
    }

    private static boolean open(WorldView world, BlockPos cell) {
        return !world.isSolid(cell.x(), cell.y(), cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z());
    }
}
