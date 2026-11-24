package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.engineer.Kept;
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
 * where they get somewhere a walk can't, or in open air straight under a goal more than a jump
 * overhead; anywhere else they'd spend the search on open air. And any
 * soldier can dig a step into a slope too steep to jump: {@link #stepCut} cells of soil by hand, or
 * of stone too with a pickaxe. And one carrying blocks builds steps where there are none
 * ({@link #stepBuild}): down off a drop it would otherwise fall, or up toward a goal above it with
 * no wall to pillar beside - one block a step against ground beside it, two in open air. Built
 * steps are offered only toward the goal's height, so the search never builds into the sky.
 * Water is swum at the surface - in from the bank, level or a drop down, across, and out onto
 * ground level with the water or one block up, or over a lip and down a drop it takes unhurt - at {@link #SWIM_COST} a block: a soldier is never
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

    /**
     * A route, whether it reaches the goal, and - one that doesn't - whether its search ran out of cells to try before its
     * budget: every cell it could get to tried, so a wider search finds no more.
     */
    public record Result(List<BlockPos> path, boolean reachesGoal, boolean exhausted) {

        public Result(List<BlockPos> path, boolean reachesGoal) {
            this(path, reachesGoal, false);
        }
    }

    private Pathfinder() {
    }

    /**
     * The cell a body at ({@code x}, {@code y}, {@code z}) stands in: the one it's in, unless that has no
     * floor and the body, 0.6 wide, stands on the edge of a block beside it - then that block's cell,
     * the nearest such. Live, a farmer on a block's edge over the crop beside it planned every route
     * from the crop's cell, with no floor, and reported no route for good; and a stranded soldier's
     * clearer, its stands looked for from the floorless cell, found none to cut its stair up from.
     */
    public static BlockPos standingCell(WorldView world, double x, double y, double z) {
        BlockPos here = BlockPos.containing(x, y, z);
        // On a block not quite a block high - farmland, a path - its feet are just under the next
        // cell, and that's the one it stands in: live, farmers planned every route from inside the
        // farmland under them, and reported "no route" to the cell they stood in.
        if (solid(world, here.x(), here.y(), here.z()) && y - here.y() >= PARTIAL_BLOCK_TOP) {
            here = here.offset(0, 1, 0);
        }
        if (standable(world, here) || swimmable(world, here)) {
            return here;
        }
        BlockPos best = here;
        double bestDistance = Double.MAX_VALUE;
        Footprint under = Footprint.of(x, z);
        for (int cx = under.minX(); cx <= under.maxX(); cx++) {
            for (int cz = under.minZ(); cz <= under.maxZ(); cz++) {
                BlockPos cell = new BlockPos(cx, here.y(), cz);
                double distance = Math.hypot(cx + 0.5 - x, cz + 0.5 - z);
                if (!cell.equals(here) && standable(world, cell) && distance < bestDistance) {
                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Whether (x, y, z) is solid to a walker: a shut gate isn't, nor the cell over one - a soldier opens it as it walks
     * up and shuts it behind ({@code bot.GateHabit}), so a route goes through a gate, never round the fence. To
     * everything else - what's built, filled, measured - a shut gate is the block it is ({@code WorldView.isSolid}).
     */
    private static boolean solid(WorldView world, int x, int y, int z) {
        return world.barsWay(x, y, z);
    }

    /** Feet this far up a solid cell are on its top: farmland and paths are 15/16 high. */
    public static final double PARTIAL_BLOCK_TOP = 0.75;

    /**
     * Somewhere to stand: room for the body, on something solid - never on a fence's top (the cell over one, solid to a
     * body and no block itself): a fence is crossed at its gate, not by a step put up beside it (operator, 2026-11-24).
     */
    public static boolean standable(WorldView world, BlockPos cell) {
        return clear(world, cell) && world.floors(cell.x(), cell.y() - 1, cell.z());
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
        return !solid(world, x, y, z) && !world.isHazard(x, y, z);
    }

    /** The cells within {@code radius} across, and 2 up and down, of each of {@code centres}: a berth a route goes round. */
    public static Set<BlockPos> berth(List<BlockPos> centres, int radius) {
        Set<BlockPos> berth = new HashSet<>();
        for (BlockPos at : centres) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -2; dy <= 2 && dx * dx + dz * dz <= radius * radius; dy++) {
                        berth.add(at.offset(dx, dy, dz));
                    }
                }
            }
        }
        return berth;
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
    /**
     * What a step down onto farmland costs over the walk: a crop cell trampled, worth a detour of this
     * many blocks and no more - a wall it is not (operator, 2026-11-13: soldiers are never stuck).
     */
    static final double TRAMPLE_COST = 40;

    /**
     * How many blocks' walk a route that places no block may cost over one that does, for each block it saves
     * (operator, 2026-11-18: if there are several ways somewhere, walk a bit more rather than place blocks; 2026-11-20:
     * a block is placed with intent - the steps routes left littered the camp, 11,745 of them never taken down). Over
     * {@link #PILLAR_COST}, a placed block is worth 44 blocks' walk - without the search itself pricing it so:
     * dearer in the search, a climb that has to build runs out of its node budget, and a soldier boxed in tunnels
     * sideways.
     */
    static final double DETOUR_PER_BLOCK = 40;
    /**
     * The fewest blocks a route places for a way round it to be looked for: a single step up a slope is a builder's
     * stride - weighed again each step it took, a mason on a slope walked to and fro and left its wall short - and a
     * route of several is the one worth walking round.
     */
    static final int WALK_ROUND_FROM = 2;

    /** A search's route, what it costs, the blocks it places, and whether any step of it is rough ({@link Move#rough}). */
    private record Search(Result result, double cost, int placed, boolean rough) {
    }

    /**
     * As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance, int, boolean, boolean)}, a step into {@code avoid}
     * costing {@link #AVOID_COST} more. A route to the goal that places blocks is searched again placing none, and that
     * one taken if it's within {@link #DETOUR_PER_BLOCK} a block saved and a plain walk: no step dug, no crop trampled,
     * no fall that hurts, no cell it was stuck short of - the allowance is for walking, not for another cost. Only for
     * a goal no more than a jump above the start: blocks placed to reach one higher are the work, not the way.
     */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                              boolean pickaxe, boolean build, Set<BlockPos> avoid) {
        return find(world, start, goal, maxExpanded, falls, blocks, pickaxe, build, avoid, false);
    }

    /**
     * What taking a step down again costs, in blocks' walk, on ground the force holds: its dig, and the walk back to it.
     * Off that ground a step is left; on it, it's a debt the ground work pays (operator, 2026-11-21).
     */
    static final double TAKEDOWN_COST = 8;

    /**
     * As {@link #find(WorldView, BlockPos, Goal, int, FallAllowance, int, boolean, boolean, Set)}, on ground the force
     * {@code holds} - the camp's area of influence - with a way round looked for even for one block placed, and each
     * block priced with its {@link #TAKEDOWN_COST} too, a goal above weighed like any other (operator, 2026-11-21: blocks left round the camp for navigation
     * are a debt never paid off, where a slightly dearer route leaves none).
     */
    public static Result find(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                              boolean pickaxe, boolean build, Set<BlockPos> avoid, boolean holds) {
        Search placing = search(world, start, goal, maxExpanded, falls, blocks, pickaxe, build, avoid, Double.MAX_VALUE);
        // Blocks placed to reach a goal above, up a jump, are the work - a mason onto its wall's top; only placed
        // to cross what's in the way are they a route to walk round.
        // On ground the force holds a goal above is weighed against the walk too: a wall's top has no walk to it, so a mason
        // still builds there; a hill does - live (field644), the flatten crew put 19 steps up the camp's hill in three
        // minutes, most beside a walk round as short (operator, 2026-11-21: we should be trending to sea level).
        if (!placing.result().reachesGoal() || placing.placed() < (holds ? 1 : WALK_ROUND_FROM)
                || !holds && goal.y() - goal.verticalTolerance() > start.y() + 1) {
            return placing.result();
        }
        double perBlock = DETOUR_PER_BLOCK + (holds ? TAKEDOWN_COST : 0);
        // The walk searched only as far as it could still be taken: commit 1088's profile, a second full search for every
        // route placing a block on the camp's ground was a quarter of the garrison's time.
        Search walking = search(world, start, goal, maxExpanded, falls, 0, pickaxe, false, avoid, placing.cost() + perBlock * placing.placed());
        return walking.result().reachesGoal() && !walking.rough() && walking.cost() <= placing.cost() + perBlock * placing.placed()
                ? walking.result() : placing.result();
    }

    /**
     * A* from {@code start} to {@code goal}, given up past {@code maxExpanded} cells, or once the cheapest open cell can
     * only lead to a route dearer than {@code costLimit} - the estimate is a straight line, never more than the walk.
     */
    private static Search search(WorldView world, BlockPos start, Goal goal, int maxExpanded, FallAllowance falls, int blocks,
                                 boolean pickaxe, boolean build, Set<BlockPos> avoid, double costLimit) {
        if (goal.contains(start)) {
            return new Search(new Result(List.of(), true), 0, 0, false);
        }

        Map<BlockPos, Double> costSoFar = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        // Blocks spent on the cheapest way found to each cell: a pillar step needs one left.
        Map<BlockPos, Integer> spent = new HashMap<>();
        spent.put(start, 0);
        // Whether the cheapest way found to each cell has a rough step in it.
        Map<BlockPos, Boolean> rough = new HashMap<>();
        rough.put(start, false);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::priority));
        costSoFar.put(start, 0.0);
        open.add(new Node(start, goal.estimate(start)));

        BlockPos closest = start;
        double closestEstimate = goal.estimate(start);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxExpanded && open.peek().priority() <= costLimit) {
            BlockPos current = open.poll().cell();
            expanded++;
            if (goal.contains(current)) {
                return new Search(new Result(reconstruct(cameFrom, start, current), true), costSoFar.get(current), spent.get(current),
                        rough.get(current));
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
            // And straight up under a goal well overhead - past a jump - in open air: a pillar to a
            // stand in the sky over its column, to cut the top of a strip from (operator, 2026-11-13:
            // pillar up to it). Not for a block up: in the squad simulation a farmer pillared a step
            // in its farm's hollow and trampled the crop stepping off.
            boolean underGoal = current.x() == (int) Math.floor(goal.x()) && current.z() == (int) Math.floor(goal.z())
                    && goal.y() > current.y() + goal.verticalTolerance() + 2;
            for (Move move : moves(world, current, falls, used < blocks && (besideWall(world, current) || underGoal), pickaxe, blocks - used, heading)) {
                double cost = base + move.cost() + (avoid.contains(move.to()) ? AVOID_COST : 0);
                Double known = costSoFar.get(move.to());
                if (known == null || cost < known) {
                    costSoFar.put(move.to(), cost);
                    spent.put(move.to(), used + move.blocks());
                    rough.put(move.to(), rough.get(current) || move.rough() || avoid.contains(move.to()));
                    cameFrom.put(move.to(), current);
                    open.add(new Node(move.to(), cost + goal.estimate(move.to())));
                }
            }
        }

        return new Search(new Result(reconstruct(cameFrom, start, closest), false, open.isEmpty()), costSoFar.get(closest), spent.get(closest),
                rough.get(closest));
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
        if (canPillar && !afloat && open(world, from.x(), from.y() + 2, from.z()) && !spoils(world, from)) {
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
            // A bridge: a hole in the floor at its own level, no drop to walk down into, floored with a block it
            // carries and walked over (backlog/mining.md; field512, Eli stood for good at a gap in his gallery).
            if (blocksLeft > 0 && !afloat) {
                List<BlockPos> floor = stepBuild(world, from, level);
                if (floor.size() == 1) {
                    moves.add(new Move(level, 1 + BUILD_COST, 1));
                }
            }
            BlockPos up = from.offset(d[0], 1, d[1]);
            if (standable(world, up) && !solid(world, from.x(), from.y() + 2, from.z())) {
                moves.add(new Move(up, JUMP_COST));
                continue;
            }
            if (swimmable(world, level)) {
                moves.add(new Move(level, SWIM_COST));
                continue;
            }
            if (solid(world, level.x(), level.y(), level.z())) {
                // A step too high to jump: cut it lower - from the water too, where a bank two high
                // round a pool is otherwise a cell with no way out. Live, a soldier swam in a flooded
                // pit for hours with a pickaxe in its pack.
                List<BlockPos> cut = stepCut(world, from, up, pickaxe);
                if (!cut.isEmpty()) {
                    double digging = cut.stream().mapToDouble(c -> world.isSoil(c.x(), c.y(), c.z()) ? DIG_COST : STONE_DIG_COST).sum();
                    moves.add(new Move(up, JUMP_COST + digging, 0, true));
                }
                continue;
            }
            if (clear(world, level)) {
                // From the water only a drop it takes unhurt: swum out over a lip and down to ground or water within a
                // step's drop. Live (field563) Olga stood afloat in a lone cell of flowing water at y=69 over a farm,
                // open air all round it at its height and the farm two under - no move out at all.
                int deepest = afloat ? MAX_DROP : Math.min(MAX_FALL, MAX_DROP + (int) Math.floor(falls.maxDamage() / falls.damageFactor()));
                for (int drop = 1; drop <= deepest; drop++) {
                    BlockPos down = from.offset(d[0], -drop, d[1]);
                    if (!open(world, down.x(), down.y(), down.z())) {
                        if (swimmable(world, down)) {
                            moves.add(new Move(down, SWIM_COST + drop * 0.5)); // water breaks the fall
                        }
                        break; // landed on something that isn't standable (a 1-high gap, lava)
                    }
                    // Down onto farmland only at a price: landing on it from over half a block tramples
                    // it back to dirt. Live, half the camp's farm stood bare, trampled as fast as it
                    // was tilled by farmers stepping down onto it off the ground round it - so any way
                    // round within TRAMPLE_COST blocks is taken instead; but never a wall: with the
                    // farm's rim tilled, eight soldiers stood at the camp with "no route" to the keep.
                    // Never down onto a stalagmite's tip: the fall's 2.5 longer onto it and does double. Live (field423,
                    // field428), Dana and Alice fell 22 and 13 blocks into caves onto one, each killed.
                    if (standable(world, down) && drop > 1 && world.isStalagmite(down.x(), down.y() - 1, down.z())) {
                        break;
                    }
                    if (standable(world, down)) {
                        double damage = Math.max(0, drop - MAX_DROP) * falls.damageFactor();
                        double trample = world.isFarmland(down.x(), down.y() - 1, down.z()) ? TRAMPLE_COST : 0;
                        moves.add(new Move(down, 1 + drop * 0.5 + damage * falls.costPerDamage() + trample, 0, damage > 0 || trample > 0));
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
                || !solid(world, up.x(), up.y() - 1, up.z()) || !open(world, up.x(), up.y() + 2, up.z())) {
            return List.of();
        }
        List<BlockPos> cells = List.of(up, up.offset(0, 1, 0), from.offset(0, 2, 0));
        List<BlockPos> cut = new ArrayList<>(3);
        for (BlockPos cell : cells) {
            if (world.isHazard(cell.x(), cell.y(), cell.z())) {
                return List.of();
            }
            if (solid(world, cell.x(), cell.y(), cell.z())) {
                // Never farmland: a step cut through a farm is a cell of it gone.
                if (world.isFarmland(cell.x(), cell.y(), cell.z())
                        || !world.isSoil(cell.x(), cell.y(), cell.z()) && !(pickaxe && world.isStone(cell.x(), cell.y(), cell.z()))) {
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
            if (solid(world, above.x(), above.y(), above.z()) && !cut.contains(above) && !holdsUp(world, above)) {
                return List.of();
            }
        }
        return List.copyOf(cut);
    }

    /**
     * The blocks to place, in order, so a soldier standing at {@code from} can step onto {@code to} -
     * one block up or down, or level over a hole that's no walkable drop, orthogonally - where there's no ground to
     * step onto: the block under
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
        if (Math.abs(dy) > 1 || Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1) {
            return List.of();
        }
        BlockPos step = to.offset(0, -1, 0);
        if (dy == 0) {
            // Level, a bridge: the floor of a hole that's no walkable drop - over a cave or a shaft - held up beside it, with
            // ground on its far side: a gap in a floor, not a ledge. Off a ledge, in the squad simulation, a soldier up its
            // own pillar bridged out into the air rather than dig the pillar down, and left the blocks hanging.
            BlockPos beyond = to.offset(to.x() - from.x(), 0, to.z() - from.z());
            return standable(world, beyond) && clear(world, to) && open(world, step.x(), step.y(), step.z()) && !walkableDrop(world, to) && !swimmable(world, from)
                    && !world.isWater(to.x(), to.y(), to.z()) && !world.isWater(step.x(), step.y(), step.z()) && !spoils(world, step)
                    && heldUp(world, step, to) ? List.of(step) : List.of();
        }
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
        if (spoils(world, step)) {
            return List.of();
        }
        if (heldUp(world, step, to)) {
            return List.of(step);
        }
        // Nothing beside the step: first a block it can go against.
        BlockPos under = dy < 0 ? from.offset(0, -2, 0) : step.offset(0, -1, 0);
        if (!open(world, under.x(), under.y(), under.z()) || spoils(world, under)) {
            return List.of();
        }
        return List.of(under, step);
    }

    /**
     * Whether a block put down at {@code cell} would spoil a farm or a way kept open: on farmland, which turns to dirt
     * under it, or in a crop; in a cell kept open - a staircase's, where a route's step left behind blocks the way up; or
     * in a gate's own cell, a way through to a walker and never ground to build in. The rules are {@link Kept}'s.
     */
    static boolean spoils(WorldView world, BlockPos cell) {
        return Kept.NONE.bars(Kept.Use.BUILD, world, cell);
    }

    /** Whether walking off into {@code edge} lands unhurt on ground within {@link #MAX_DROP}, or in water. */
    private static boolean walkableDrop(WorldView world, BlockPos edge) {
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos down = edge.offset(0, -drop, 0);
            if (!open(world, down.x(), down.y(), down.z())) {
                return swimmable(world, down);
            }
            if (solid(world, down.x(), down.y() - 1, down.z())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block placed at {@code cell} has a solid block on some side to go against - any side but {@code above}. */
    private static boolean heldUp(WorldView world, BlockPos cell, BlockPos above) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            BlockPos side = cell.offset(d[0], d[1], d[2]);
            if (!side.equals(above) && solid(world, side.x(), side.y(), side.z())) {
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
            if (solid(world, cell.x() + d[0], cell.y() + 1, cell.z() + d[1])) {
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
    /** A move: where to, at what cost, the blocks it places, and whether it's rough - a step dug, a crop trampled, a fall that hurts. */
    private record Move(BlockPos to, double cost, int blocks, boolean rough) {
        Move(BlockPos to, double cost) {
            this(to, cost, 0, false);
        }

        Move(BlockPos to, double cost, int blocks) {
            this(to, cost, blocks, false);
        }
    }
}
