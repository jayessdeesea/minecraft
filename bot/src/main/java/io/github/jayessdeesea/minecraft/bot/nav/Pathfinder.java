package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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
 * soldier can dig a step into a slope too steep to jump: {@link #stepCut} cells of soil by hand, or
 * of stone too with a pickaxe. And one carrying blocks builds steps where there are none
 * ({@link #stepBuild}): down off a drop it would otherwise fall, or up toward a goal above it with
 * no wall to pillar beside - one block a step against ground beside it, two in open air. Built
 * steps are offered only toward the goal's height, so the search never builds into the sky.
 * Water is swum at the surface - in from the bank, level or a drop down, across, and out onto
 * ground level with the water or one block up - at {@link #SWIM_COST} a block: a soldier is never
 * stopped by a pond or a river. Other hazards (lava, fire) are never entered; no ladders.
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
    /** Each block placed for a built step: a pillar step's price, the block being the dear part. */
    static final double BUILD_COST = PILLAR_COST;
    /** Each block of soil dug for a step: about a second by hand, five blocks' walk. */
    static final double DIG_COST = 5.0;
    /** Each block of stone dug for a step: about two seconds with a wooden pickaxe, eight blocks' walk. */
    static final double STONE_DIG_COST = 8.0;
    /** A block swum: about half walking pace. */
    static final double SWIM_COST = 2.5;
    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    public record Result(List<BlockPos> path, boolean reachesGoal) {
    }

    private Pathfinder() {
    }

    public static boolean standable(WorldView world, BlockPos cell) {
        return clear(world, cell) && world.isSolid(cell.x(), cell.y() - 1, cell.z());
    }

    /** Afloat: water at the feet and open air at the head - the surface, swum with the head out. */
    public static boolean swimmable(WorldView world, BlockPos cell) {
        return world.isWater(cell.x(), cell.y(), cell.z()) && open(world, cell.x(), cell.y() + 1, cell.z());
    }

    /** Somewhere a soldier can be between moves: on its feet, or afloat. */
    public static boolean footing(WorldView world, BlockPos cell) {
        return standable(world, cell) || swimmable(world, cell);
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
        return find(world, start, goal, maxExpanded, falls, blocks, false);
    }

    /**
     * As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance, int)}, for a soldier that may
     * also cut steps into plain stone, carrying a {@code pickaxe}.
     */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                              boolean pickaxe) {
        return find(world, start, goal, maxExpanded, falls, blocks, pickaxe, false);
    }

    /**
     * As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance, int, boolean)}, and with
     * {@code build}, built steps ({@link #stepBuild}) too. They're for when nothing else gets there:
     * offered everywhere toward the goal's height, they spend a search's budget on open air that
     * a route round would have used.
     */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                              boolean pickaxe, boolean build) {
        return find(world, start, goal, maxExpanded, falls, blocks, pickaxe, build, Set.of());
    }

    /**
     * What a step into one of the cells a soldier just failed to reach costs on top: enough that a
     * route round it is taken if there's one, not so much that none at all is left. The same thing
     * tried again and again - live, a soldier jumping at the same block - is a soldier stuck.
     */
    static final double AVOID_COST = 20;

    /** As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance, int, boolean, boolean)}, a step into {@code avoid} costing {@link #AVOID_COST} more. */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                              boolean pickaxe, boolean build, Set<BlockPos> avoid) {
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
            // Built steps only toward the goal's height, for the same reason.
            int heading = !build ? 0 : goal.y() < current.y() - goal.verticalTolerance() ? -1 : goal.y() > current.y() + goal.verticalTolerance() ? 1 : 0;
            for (Move move : moves(world, current, falls, used < blocks && besideWall(world, current), pickaxe, blocks - used, heading)) {
                double cost = base + move.cost() + (avoid.contains(move.to()) ? AVOID_COST : 0);
                Double known = costSoFar.get(move.to());
                if (known == null || cost < known) {
                    costSoFar.put(move.to(), cost);
                    spent.put(move.to(), used + move.blocks());
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
    public static Set<BlockPos> reachable(WorldView world, BlockPos start, int maxHorizontalDistance, int maxExpanded) {
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
        return climbs(world, start, maxHorizontalDistance, maxExpanded, blocks, false);
    }

    /** As {@link #climbs(WorldView, BlockPos, int, int, int)}, for a point man that cuts steps into stone with a {@code pickaxe}. */
    public static Map<BlockPos, Integer> climbs(WorldView world, BlockPos start, int maxHorizontalDistance, int maxExpanded,
                                                int blocks, boolean pickaxe) {
        Map<BlockPos, Integer> fewest = new HashMap<>();
        if (!footing(world, start)) {
            return fewest;
        }
        // 0-1 breadth-first: a walk costs no blocks and goes to the front, a pillar step one and
        // goes to the back, so each cell is settled at the fewest blocks.
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> settled = new HashSet<>();
        fewest.put(start, 0);
        frontier.add(start);
        while (!frontier.isEmpty() && settled.size() < maxExpanded) {
            BlockPos current = frontier.poll();
            if (!settled.add(current)) {
                continue;
            }
            int used = fewest.get(current);
            for (Move move : moves(world, current, FallAllowance.NONE, used < blocks && besideWall(world, current), pickaxe, 0, 0)) {
                BlockPos next = move.to();
                int cost = used + move.blocks();
                if (Math.abs(next.x() - start.x()) <= maxHorizontalDistance
                        && Math.abs(next.z() - start.z()) <= maxHorizontalDistance
                        && cost < fewest.getOrDefault(next, Integer.MAX_VALUE)) {
                    fewest.put(next, cost);
                    if (move.blocks() > 0) {
                        frontier.addLast(next);
                    } else {
                        frontier.addFirst(next);
                    }
                }
            }
        }
        return fewest;
    }

    /**
     * @param blocksLeft how many blocks it carries for built steps
     * @param heading    which way built steps go: -1 down, 1 up, 0 none
     */
    private static List<Move> moves(WorldView world, BlockPos from, FallAllowance falls, boolean canPillar, boolean pickaxe,
                                    int blocksLeft, int heading) {
        List<Move> moves = new ArrayList<>(9);
        boolean afloat = swimmable(world, from);
        if (canPillar && !afloat && open(world, from.x(), from.y() + 2, from.z())) {
            moves.add(new Move(from.offset(0, 1, 0), PILLAR_COST, 1)); // a block placed where it stood
        }
        if (heading != 0 && !afloat && blocksLeft > 0) {
            for (int[] d : ORTHOGONAL) {
                BlockPos to = from.offset(d[0], heading, d[1]);
                List<BlockPos> build = stepBuild(world, from, to);
                if (!build.isEmpty() && build.size() <= blocksLeft) {
                    moves.add(new Move(to, (heading > 0 ? JUMP_COST : 1.5) + build.size() * BUILD_COST, build.size()));
                }
            }
        }
        for (int[] d : ORTHOGONAL) {
            BlockPos level = from.offset(d[0], 0, d[1]);
            if (standable(world, level)) {
                moves.add(new Move(level, afloat ? SWIM_COST : 1));
                continue;
            }
            BlockPos up = from.offset(d[0], 1, d[1]);
            if (standable(world, up) && !world.isSolid(from.x(), from.y() + 2, from.z())) {
                moves.add(new Move(up, JUMP_COST));
                continue;
            }
            if (swimmable(world, level)) {
                moves.add(new Move(level, SWIM_COST));
                continue;
            }
            if (world.isSolid(level.x(), level.y(), level.z())) {
                // A step too high to jump: cut it lower - from the water too, where a bank two high
                // round a pool is otherwise a cell with no way out. Live, a soldier swam in a flooded
                // pit for hours with a pickaxe in its pack.
                List<BlockPos> cut = stepCut(world, from, up, pickaxe);
                if (!cut.isEmpty()) {
                    double digging = cut.stream().mapToDouble(c -> world.isSoil(c.x(), c.y(), c.z()) ? DIG_COST : STONE_DIG_COST).sum();
                    moves.add(new Move(up, JUMP_COST + digging));
                }
                continue;
            }
            if (afloat) {
                continue; // no drop is walked off from the water
            }
            if (clear(world, level)) {
                int deepest = Math.min(MAX_FALL, MAX_DROP + (int) Math.floor(falls.maxDamage() / falls.damageFactor()));
                for (int drop = 1; drop <= deepest; drop++) {
                    BlockPos down = from.offset(d[0], -drop, d[1]);
                    if (!open(world, down.x(), down.y(), down.z())) {
                        if (swimmable(world, down)) {
                            moves.add(new Move(down, SWIM_COST + drop * 0.5)); // water breaks the fall
                        }
                        break; // landed on something that isn't standable (a 1-high gap, lava)
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
            if (afloat && swimmable(world, diagonal)
                    && swimmable(world, from.offset(d[0], 0, 0)) && swimmable(world, from.offset(0, 0, d[1]))) {
                moves.add(new Move(diagonal, SWIM_COST * DIAGONAL));
                continue;
            }
            if (!afloat && standable(world, diagonal)
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
     * soil by hand, or plain stone too with a {@code pickaxe} - not sand or gravel, which would fall
     * into the gap - with no liquid beside a cell to flow in, and nothing that falls resting on one.
     */
    public static List<BlockPos> stepCut(WorldView world, BlockPos from, BlockPos up) {
        return stepCut(world, from, up, false);
    }

    /** {@link #stepCut(WorldView, BlockPos, BlockPos)}, into stone as well when the soldier carries a {@code pickaxe}. */
    public static List<BlockPos> stepCut(WorldView world, BlockPos from, BlockPos up, boolean pickaxe) {
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
                if (!world.isSoil(cell.x(), cell.y(), cell.z()) && !(pickaxe && world.isStone(cell.x(), cell.y(), cell.z()))) {
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

    /**
     * The blocks to place, in order, so a soldier standing at {@code from} can step onto {@code to} -
     * one block up or down, orthogonally - where there's no ground to step onto: the block under
     * {@code to}, and first a block under that when nothing beside it holds it up. Empty when
     * {@code to} already has ground, or a step can't be built there.
     *
     * <p>Down, the first block goes under the soldier's own ground and holds the step off its side:
     * a stair built down a sheer face or out of open air, two blocks a step. Up, it goes beside the
     * soldier's ground, under the step. A drop it could walk down unhurt - {@link #MAX_DROP} or less
     * onto ground - is walked, not built.
     */
    public static List<BlockPos> stepBuild(WorldView world, BlockPos from, BlockPos to) {
        int dy = to.y() - from.y();
        // The ground under {@code from} may be a step built earlier on the same route, not yet in the world.
        if (Math.abs(dy) != 1 || Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1) {
            return List.of();
        }
        BlockPos step = to.offset(0, -1, 0);
        // Nothing is built in water: a swimmer near the bed isn't on a stair.
        if (!clear(world, to) || !open(world, step.x(), step.y(), step.z()) || swimmable(world, from)
                || world.isWater(to.x(), to.y(), to.z()) || world.isWater(step.x(), step.y(), step.z())) {
            return List.of();
        }
        if (dy > 0) {
            // The jump needs headroom over where it stands.
            if (!open(world, from.x(), from.y() + 2, from.z())) {
                return List.of();
            }
        } else {
            // Walked off the edge, the body passes through the cell beside it at its own height.
            BlockPos edge = from.offset(to.x() - from.x(), 0, to.z() - from.z());
            if (!clear(world, edge) || walkableDrop(world, edge)) {
                return List.of();
            }
        }
        if (heldUp(world, step, to)) {
            return List.of(step);
        }
        // Nothing beside the step: first a block it can go against.
        BlockPos under = dy < 0 ? from.offset(0, -2, 0) : step.offset(0, -1, 0);
        if (!open(world, under.x(), under.y(), under.z())) {
            return List.of();
        }
        return List.of(under, step);
    }

    /** Whether walking off into {@code edge} lands unhurt on ground within {@link #MAX_DROP}, or in water. */
    private static boolean walkableDrop(WorldView world, BlockPos edge) {
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos down = edge.offset(0, -drop, 0);
            if (!open(world, down.x(), down.y(), down.z())) {
                return swimmable(world, down);
            }
            if (world.isSolid(down.x(), down.y() - 1, down.z())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block placed at {@code cell} has a solid block on some side to go against - any side but {@code above}. */
    private static boolean heldUp(WorldView world, BlockPos cell, BlockPos above) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            BlockPos side = cell.offset(d[0], d[1], d[2]);
            if (!side.equals(above) && world.isSolid(side.x(), side.y(), side.z())) {
                return true;
            }
        }
        return false;
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

    /** @param blocks how many blocks the move places - a pillar step one, a built step one or two */
    private record Move(BlockPos to, double cost, int blocks) {
        Move(BlockPos to, double cost) {
            this(to, cost, 0);
        }
    }
}
