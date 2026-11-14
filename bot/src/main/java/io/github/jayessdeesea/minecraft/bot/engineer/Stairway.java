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
    /**
     * The pillar a soldier at {@code feet} stands on, top first: the column under it standing free
     * - nothing solid beside it at any height - down to the ground it stands on. Empty when it isn't
     * up one at least two blocks high, or any block of it won't dig.
     */
    public static Optional<List<BlockPos>> pillarUnder(WorldView world, BlockPos feet) {
        List<BlockPos> pillar = new java.util.ArrayList<>();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = feet.y() - 1; ; y--) {
            BlockPos c = new BlockPos(feet.x(), y, feet.z());
            if (!world.isSolid(c.x(), c.y(), c.z())) {
                return Optional.empty();
            }
            int fy = y;
            boolean free = java.util.Arrays.stream(sides).noneMatch(d -> world.isSolid(c.x() + d[0], fy, c.z() + d[1]));
            if (!free) {
                break; // the ground it stands on
            }
            if (!Clearing.diggable(world, c.x(), c.y(), c.z())) {
                return Optional.empty();
            }
            pillar.add(c);
        }
        return pillar.size() >= 2 ? Optional.of(List.copyOf(pillar)) : Optional.empty();
    }

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

    /**
     * Steps at most up out of a pocket: a tunnel to the surface from a dead end at the bottom of a
     * mine. Live, a soldier stood a day in a pocket 30 blocks under the camp, a 12-step stair short.
     */
    static final int MAX_STEPS_UP = MAX_STEPS;

    /**
     * The cells to dig for the shortest stair from {@code feet} - where a soldier stands boxed in
     * below the ground - up and out, one block up and one along a step: each step's body room, and
     * the head room over the step before it, so the soldier jumps up into it. Empty if none can be
     * laid: on every heading a cell no clear digs (cobblestone, a table), water, no floor to step
     * onto, more than {@link #MAX_STEPS_UP} steps, or a cut under sand or gravel - which would come
     * down on the digger's head - or under water or lava (operator, 2026-11-13: cut above only toward
     * what's known, never under a falling block or a liquid). Live, a soldier waited two runs in a
     * pocket its squad had no stair down for (docs/military-doctrine.md § 7).
     */
    public static Optional<List<BlockPos>> up(WorldView world, BlockPos feet) {
        return up(world, feet, new java.util.HashMap<>());
    }

    /** Steps the search for a stair up looks at before giving up: a few hundred flights' worth. */
    static final int UP_SEARCH_BUDGET = 4000;

    /**
     * As {@link #up(WorldView, BlockPos)}, and when none can be laid, {@code whyNot} gets, for each
     * heading out of the pocket, the deepest step reached on it and what stopped it there.
     */
    public static Optional<List<BlockPos>> up(WorldView world, BlockPos feet, java.util.Map<String, String> whyNot) {
        // A flight winds: straight on where it can, else round to the left or right of its heading,
        // never back - a cave across the way, or gravel over it, stops a straight flight and live, from
        // a dead end at a mine's bottom, every one of the four. Depth first, straight on first, so the
        // first flight found is the straightest; the deepest failure on each heading is kept for the log.
        record Node(BlockPos last, int dx, int dz, int k, List<BlockPos> dig, int from) {
        }
        java.util.Deque<Node> stack = new java.util.ArrayDeque<>();
        for (int i = HEADINGS.length - 1; i >= 0; i--) {
            stack.push(new Node(feet, HEADINGS[i][0], HEADINGS[i][1], 1, List.of(), i));
        }
        int[] deepest = new int[HEADINGS.length];
        int budget = UP_SEARCH_BUDGET;
        while (!stack.isEmpty() && budget-- > 0) {
            Node n = stack.pop();
            BlockPos step = n.last().offset(n.dx(), 1, n.dz());
            String stopped = null;
            List<BlockPos> dig = new ArrayList<>(n.dig());
            if (!world.isSolid(step.x(), step.y() - 1, step.z()) || world.isHazard(step.x(), step.y() - 1, step.z())) {
                stopped = "nothing to step up onto at " + step.offset(0, -1, 0);
            } else {
                for (BlockPos cell : List.of(n.last().offset(0, 2, 0), step, step.offset(0, 1, 0))) {
                    if (world.isHazard(cell.x(), cell.y(), cell.z())) {
                        stopped = "water or lava at " + cell;
                    } else if (world.isSolid(cell.x(), cell.y(), cell.z())) {
                        BlockPos over = cell.offset(0, 1, 0);
                        if (!Clearing.diggable(world, cell.x(), cell.y(), cell.z())) {
                            stopped = "a block no clear digs at " + cell;
                        } else if (world.falls(cell.x(), cell.y(), cell.z())) {
                            stopped = "sand or gravel at " + cell;
                        } else if (world.falls(over.x(), over.y(), over.z())) {
                            // Over the cut, what the dig leaves standing: nothing that falls or flows.
                            stopped = "sand or gravel over " + cell;
                        } else if (world.isHazard(over.x(), over.y(), over.z())) {
                            stopped = "water or lava over " + cell;
                        } else {
                            dig.add(cell);
                        }
                    }
                    if (stopped != null) {
                        break;
                    }
                }
            }
            if (stopped != null) {
                if (n.k() >= deepest[n.from()]) {
                    deepest[n.from()] = n.k();
                    whyNot.put(headingName(HEADINGS[n.from()]), "step " + n.k() + ": " + stopped);
                }
                continue;
            }
            // Out: open sky over the step, the ground's own level.
            if (!world.isSolid(step.x(), step.y() + 2, step.z()) && !world.isSolid(step.x(), step.y() + 3, step.z())
                    && !world.isSolid(step.x() + n.dx(), step.y(), step.z() + n.dz())) {
                if (!dig.isEmpty()) {
                    return Optional.of(List.copyOf(dig));
                }
                continue;
            }
            if (n.k() >= MAX_STEPS_UP) {
                if (n.k() >= deepest[n.from()]) {
                    deepest[n.from()] = n.k();
                    whyNot.put(headingName(HEADINGS[n.from()]), "step " + n.k() + ": no open sky yet");
                }
                continue;
            }
            // The turns first onto the stack, straight on last - so it's popped first.
            stack.push(new Node(step, -n.dz(), n.dx(), n.k() + 1, dig, n.from()));
            stack.push(new Node(step, n.dz(), -n.dx(), n.k() + 1, dig, n.from()));
            stack.push(new Node(step, n.dx(), n.dz(), n.k() + 1, dig, n.from()));
        }
        return Optional.empty();
    }

    private static String headingName(int[] h) {
        return h[0] > 0 ? "east" : h[0] < 0 ? "west" : h[1] > 0 ? "south" : "north";
    }

    private static boolean open(WorldView world, BlockPos cell) {
        return !world.isSolid(cell.x(), cell.y(), cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z());
    }
}
