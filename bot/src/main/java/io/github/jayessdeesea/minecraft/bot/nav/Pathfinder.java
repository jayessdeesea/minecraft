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
 * {@link FallAllowance}, at a cost per point of fall damage. Hazards (water, lava, fire) are never
 * entered - no swimming, no ladders.
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
        if (goal.contains(start)) {
            return new Result(List.of(), true);
        }

        Map<BlockPos, Double> costSoFar = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
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
            for (Move move : moves(world, current, falls)) {
                double cost = base + move.cost();
                Double known = costSoFar.get(move.to());
                if (known == null || cost < known) {
                    costSoFar.put(move.to(), cost);
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
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>();
        if (!standable(world, start)) {
            return seen;
        }
        seen.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && seen.size() < maxExpanded) {
            BlockPos current = frontier.poll();
            for (Move move : moves(world, current, FallAllowance.NONE)) {
                BlockPos next = move.to();
                if (Math.abs(next.x() - start.x()) <= maxHorizontalDistance
                        && Math.abs(next.z() - start.z()) <= maxHorizontalDistance
                        && seen.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return seen;
    }

    private static List<Move> moves(WorldView world, BlockPos from, FallAllowance falls) {
        List<Move> moves = new ArrayList<>(8);
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

    private record Move(BlockPos to, double cost) {
    }
}
