package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Short-range A* over block cells, run on a soldier's tick thread - so it's bounded by an
 * expansion budget rather than unbounded: an unreachable or far-off goal costs at most
 * {@code maxExpanded} cell visits, never a stalled tick loop.
 *
 * <p>Moves are what {@code DeterministicSoldier} can physically do: walk to any of the 8 neighbours
 * on the same level (diagonals only when both orthogonal cells are clear, so the 0.6-wide body
 * doesn't clip a corner), jump up one block orthogonally (needs headroom above the takeoff cell),
 * or walk off an edge and drop up to {@link #MAX_DROP} blocks orthogonally - further only within a
 * {@link FallAllowance}, at a cost per point of fall damage. A soldier carrying blocks can also
 * pillar: jump and place a block under itself, rising one block straight up - the way up a wall
 * or cliff, repeated, then a step off onto the top. Pillar steps are offered only beside a wall,
 * where they get somewhere a walk can't; anywhere else they'd spend the search on open air. And any
 * soldier can dig a step into a slope too steep to jump: {@link #stepCut} cells of soil, by hand.
 * Hazards (water, lava, fire) are never entered - no swimming, no ladders.
 *
 * <p>A goal outside the budget isn't a failure: the result is a path to the explored cell that
 * got closest, flagged {@code reachesGoal = false}, and the caller re-plans from there. That's how
 * a target 100 blocks away is reached with a 32-block-sized search.
 */
public final class Pathfinder {

    /** Drops this far take damage-free-ish falls (vanilla starts fall damage after 3 blocks). */
    public static final int MAX_DROP = 3;
    /** No allowance plans a fall longer than this; 23 blocks is lethal from full health anyway. */
    static final int MAX_FALL = 23;

    private static final double DIAGONAL = Math.sqrt(2);
    private static final double JUMP_COST = 2.0;
    /** A pillar step costs a block and a jump's worth of time: dearer than walking round, cheaper than no way at all. */
    static final double PILLAR_COST = 4.0;
    /** Each block of soil dug for a step: about a second by hand, five blocks' walk. */
    static final double DIG_COST = 5.0;
    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    public record Result(List<BlockPos> path, boolean reachesGoal) {
    }

    private Pathfinder() {
    }

    public static boolean standable(WorldView world, BlockPos cell) {
        return clear(world, cell) && world.isSolid(cell.x(), cell.y() - 1, cell.z());
    }

    /** Room for the body: this cell and the one above are neither solid nor hazardous (water, fire). */
    private static boolean clear(WorldView world, BlockPos cell) {
        return open(world, cell.x(), cell.y(), cell.z()) && open(world, cell.x(), cell.y() + 1, cell.z());
    }

    private static boolean open(WorldView world, int x, int y, int z) {
        return !world.isSolid(x, y, z) && !world.isHazard(x, y, z);
    }

    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded) {
        return find(world, start, goal, maxExpanded, FallAllowance.NONE);
    }

    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls) {
        return find(world, start, goal, maxExpanded, falls, 0);
    }

    /**
     * As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance)}, with up to {@code blocks}
     * pillar steps - one block each, from what the soldier carries. A route is never planned on
     * more blocks than that: one that needs more goes round, or gets as close as it can.
     */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks) {
        if (goal.contains(start)) {
            return new Result(List.of(), true);
        }

        Map<BlockPos, Double> costSoFar = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        // Blocks spent on the cheapest way found to each cell: a pillar step needs one left.
        Map<BlockPos, Integer> spent = new HashMap<>();
        spent.put(start, 0);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::priority));
        costSoFar.put(start, 0.0);
        open.add(new Node(start, goal.estimate(start)));

        BlockPos closest = start;
        double closestEstimate = goal.estimate(start);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxExpanded) {
            BlockPos current = open.poll().cell();
            expanded++;
            if (goal.contains(current)) {
                return new Result(reconstruct(cameFrom, start, current), true);
            }
            double estimate = goal.estimate(current);
            if (estimate < closestEstimate) {
                closest = current;
                closestEstimate = estimate;
            }

            double base = costSoFar.get(current);
            int used = spent.get(current);
            // Pillar steps only beside a wall: anywhere else they spend the search budget on open air.
            for (Move move : moves(world, current, falls, used < blocks && besideWall(world, current))) {
                double cost = base + move.cost();
                Double known = costSoFar.get(move.to());
                if (known == null || cost < known) {
                    costSoFar.put(move.to(), cost);
                    spent.put(move.to(), used + (move.pillar() ? 1 : 0));
                    cameFrom.put(move.to(), current);
                    open.add(new Node(move.to(), cost + goal.estimate(move.to())));
                }
            }
        }

        return new Result(reconstruct(cameFrom, start, closest), false);
    }

    /**
     * Every standing cell reachable from {@code start} with the same moves {@link #find} uses,
     * staying within {@code maxHorizontalDistance} blocks (x and z) of it, visiting at most
     * {@code maxExpanded} cells. A breadth-first flood fill - one call answers "can the point man
     * get there?" for a whole area at once.
     */
    public static java.util.Set<BlockPos> reachable(WorldView world, BlockPos start, int maxHorizontalDistance, int maxExpanded) {
        return climbs(world, start, maxHorizontalDistance, maxExpanded, 0).keySet();
    }

    /**
     * As {@link #reachable(WorldView, BlockPos, int, int)}, with up to {@code blocks} pillar steps,
     * each cell mapped to the fewest blocks it takes to get there - 0 for a walk. Pillar steps only
     * beside a wall, the one place one gets anywhere a walk can't: without that limit the fill would
     * climb every open column into the sky.
     */
    public static Map<BlockPos, Integer> climbs(WorldView world, BlockPos start, int maxHorizontalDistance, int maxExpanded,
                                                int blocks) {
        Map<BlockPos, Integer> fewest = new HashMap<>();
        if (!standable(world, start)) {
            return fewest;
        }
        // 0-1 breadth-first: a walk costs no blocks and goes to the front, a pillar step one and
        // goes to the back, so each cell is settled at the fewest blocks.
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> settled = new java.util.HashSet<>();
        fewest.put(start, 0);
        frontier.add(start);
        while (!frontier.isEmpty() && settled.size() < maxExpanded) {
            BlockPos current = frontier.poll();
            if (!settled.add(current)) {
                continue;
            }
            int used = fewest.get(current);
            for (Move move : moves(world, current, FallAllowance.NONE, used < blocks && besideWall(world, current))) {
                BlockPos next = move.to();
                int cost = used + (move.pillar() ? 1 : 0);
                if (Math.abs(next.x() - start.x()) <= maxHorizontalDistance
                        && Math.abs(next.z() - start.z()) <= maxHorizontalDistance
                        && cost < fewest.getOrDefault(next, Integer.MAX_VALUE)) {
                    fewest.put(next, cost);
                    if (move.pillar()) {
                        frontier.addLast(next);
                    } else {
                        frontier.addFirst(next);
                    }
                }
            }
        }
        return fewest;
    }

    private static List<Move> moves(WorldView world, BlockPos from, FallAllowance falls, boolean canPillar) {
        List<Move> moves = new ArrayList<>(9);
        if (canPillar && open(world, from.x(), from.y() + 2, from.z())) {
            moves.add(new Move(from.offset(0, 1, 0), PILLAR_COST, true)); // a block placed where it stood
        }
        for (int[] d : ORTHOGONAL) {
            BlockPos level = from.offset(d[0], 0, d[1]);
            if (standable(world, level)) {
                moves.add(new Move(level, 1));
                continue;
            }
            BlockPos up = from.offset(d[0], 1, d[1]);
            if (standable(world, up) && !world.isSolid(from.x(), from.y() + 2, from.z())) {
                moves.add(new Move(up, JUMP_COST));
                continue;
            }
            if (world.isSolid(level.x(), level.y(), level.z())) {
                // A step too high to jump: cut it lower.
                List<BlockPos> cut = stepCut(world, from, up);
                if (!cut.isEmpty()) {
                    moves.add(new Move(up, JUMP_COST + DIG_COST * cut.size()));
                }
                continue;
            }
            if (clear(world, level)) {
                int deepest = Math.min(MAX_FALL, MAX_DROP + (int) Math.floor(falls.maxDamage() / falls.damageFactor()));
                for (int drop = 1; drop <= deepest; drop++) {
                    BlockPos down = from.offset(d[0], -drop, d[1]);
                    if (!open(world, down.x(), down.y(), down.z())) {
                        break; // landed on something that isn't standable (a 1-high gap, water)
                    }
                    if (standable(world, down)) {
                        double damage = Math.max(0, drop - MAX_DROP) * falls.damageFactor();
                        moves.add(new Move(down, 1 + drop * 0.5 + damage * falls.costPerDamage()));
                        break;
                    }
                }
            }
        }
        for (int[] d : DIAGONALS) {
            BlockPos diagonal = from.offset(d[0], 0, d[1]);
            if (standable(world, diagonal)
                    && clear(world, from.offset(d[0], 0, 0))
                    && clear(world, from.offset(0, 0, d[1]))) {
                moves.add(new Move(diagonal, DIAGONAL));
            }
        }
        return moves;
    }

    /**
     * The blocks to dig so a soldier at {@code from} can jump up onto {@code up}: room for the body at
     * {@code up}, and headroom over {@code from} for the jump - in that order, the order they're dug.
     * Empty when that can't be done by hand, or when there's nothing to dig.
     *
     * <p>A step is cut only into the face of a slope - the cell over the soldier's head at {@code up}
     * must already be open - so the cut is at most three blocks and the planner never tunnels. Only
     * soil (not stone, not sand or gravel that would fall into the gap), with no liquid beside a cell
     * to flow in, and nothing that falls resting on one.
     */
    public static List<BlockPos> stepCut(WorldView world, BlockPos from, BlockPos up) {
        if (up.y() != from.y() + 1 || Math.abs(up.x() - from.x()) + Math.abs(up.z() - from.z()) != 1
                || !world.isSolid(up.x(), up.y() - 1, up.z()) || !open(world, up.x(), up.y() + 2, up.z())) {
            return List.of();
        }
        List<BlockPos> cells = List.of(up, up.offset(0, 1, 0), from.offset(0, 2, 0));
        List<BlockPos> cut = new ArrayList<>(3);
        for (BlockPos cell : cells) {
            if (world.isHazard(cell.x(), cell.y(), cell.z())) {
                return List.of();
            }
            if (world.isSolid(cell.x(), cell.y(), cell.z())) {
                if (!world.isSoil(cell.x(), cell.y(), cell.z())) {
                    return List.of();
                }
                cut.add(cell);
            }
        }
        for (BlockPos cell : cut) {
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                    return List.of();
                }
            }
            BlockPos above = cell.offset(0, 1, 0);
            if (world.isSolid(above.x(), above.y(), above.z()) && !cut.contains(above) && !holdsUp(world, above)) {
                return List.of();
            }
        }
        return List.copyOf(cut);
    }

    /** Whether a block stays put with the one under it dug out: soil, stone and logs do; sand and gravel - or anything unknown - might not. */
    private static boolean holdsUp(WorldView world, BlockPos cell) {
        return world.isSoil(cell.x(), cell.y(), cell.z()) || world.isStone(cell.x(), cell.y(), cell.z())
                || world.isLog(cell.x(), cell.y(), cell.z());
    }

    /** Whether a block one up from {@code cell} is solid on some side - a wall to climb beside. */
    private static boolean besideWall(WorldView world, BlockPos cell) {
        for (int[] d : ORTHOGONAL) {
            if (world.isSolid(cell.x() + d[0], cell.y() + 1, cell.z() + d[1])) {
                return true;
            }
        }
        return false;
    }

    /** Total fall damage along {@code path} from {@code start}: 1 per block beyond {@link #MAX_DROP}, per fall. */
    public static int fallDamage(BlockPos start, List<BlockPos> path) {
        int damage = 0;
        BlockPos previous = start;
        for (BlockPos cell : path) {
            damage += Math.max(0, previous.y() - cell.y() - MAX_DROP);
            previous = cell;
        }
        return damage;
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos start, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cell = end; !cell.equals(start); cell = cameFrom.get(cell)) {
            path.add(cell);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private record Node(BlockPos cell, double priority) {
    }

    private record Move(BlockPos to, double cost, boolean pillar) {
        Move(BlockPos to, double cost) {
            this(to, cost, false);
        }
    }
}
