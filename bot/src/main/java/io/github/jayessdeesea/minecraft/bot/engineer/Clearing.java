package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The cells of a soldier's ground-work command (command.Box): what an {@code Order.Dig} digs and an
 * {@code Order.Fill} fills, and the next block to dig and where to stand for it. The camp's sky
 * cleared - every log and leaf over its ground is cover a monster shelters under at dawn, when the
 * open sky would burn it (docs/military-doctrine.md § 15) - and its ground brought to one height.
 * Trees are felled from the bottom; leaves cut off from every log decay on their own, so the ones
 * out of reach go once the trunk does.
 */
public final class Clearing {

    /**
     * How far from its eyes a soldier digs, to the nearest face of the block: the server allows its
     * 4.5-block reach plus one, and this keeps a margin under that.
     */
    static final double REACH = 5.0;
    static final double EYES = 1.62;
    /** How far across from the block a stand is looked for. */
    static final int STAND_ACROSS = 3;
    /** A block one higher counts as this many blocks' walk further: the lowest goes first. */
    static final double HEIGHT_WEIGHT = 2;

    /** One block to cut: a log or leaves, and where to stand to reach it. */
    public record Cut(BlockPos block, BlockPos stand, boolean log) {
    }

    private Clearing() {
    }

    /**
     * What an {@code Order.Dig} of {@code box} digs: every log, leaf, soil, plain stone or gravel
     * block in it but {@code except}, in the order it's dug - a tree's logs and leaves lowest first
     * (cut-off leaves decay), the ground top down (soil is dug only from an open top). Anything else
     * - cobblestone, a table, rock - is left for the soldier to report, and so is the ground under
     * it or under a cell in {@code except}: it can't be dug from its top, and it's what that stands on.
     * Live, a soldier tried soil under a hut's wall every tick, its digger finding no open top.
     */
    public static List<BlockPos> toClear(WorldView world, io.github.jayessdeesea.minecraft.command.Box box,
                                         Set<io.github.jayessdeesea.minecraft.command.Cell> except) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                // Down each column: ground is dug only from an open top, so a block of it is in the dig
                // only with the cell over it open or in the dig too. Live, five blocks under ones the dig
                // left - above the box, or kept - were counted for hours and never dug.
                boolean clearAbove = !world.isSolid(x, box.maxY() + 1, z);
                for (int y = box.maxY(); y >= box.minY(); y--) {
                    boolean ground = !world.isLog(x, y, z) && world.leaves(x, y, z).isEmpty();
                    boolean dug = !except.contains(new io.github.jayessdeesea.minecraft.command.Cell(x, y, z)) && diggable(world, x, y, z)
                            && (!ground || clearAbove);
                    if (dug) {
                        cells.add(new BlockPos(x, y, z));
                    }
                    clearAbove = dug || !world.isSolid(x, y, z);
                }
            }
        }
        cells.sort(java.util.Comparator.comparingInt(c -> order(world, c)));
        return List.copyOf(cells);
    }

    /**
     * What an {@code Order.Fill} of {@code box} fills: in each column, the open cells from the box's
     * top down to the first solid block or water - open to the sky, so a pit is filled and a
     * flooded one capped over its water, and a cave underground is left alone. Never one in
     * {@code except}. Lowest first.
     */
    public static List<BlockPos> toFill(WorldView world, io.github.jayessdeesea.minecraft.command.Box box,
                                        Set<io.github.jayessdeesea.minecraft.command.Cell> except) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                List<BlockPos> column = new ArrayList<>();
                boolean grounded = false;
                for (int y = box.maxY(); y >= box.minY(); y--) {
                    if (world.isSolid(x, y, z) || world.isHazard(x, y, z)) {
                        grounded = true;
                        break;
                    }
                    if (except.contains(new io.github.jayessdeesea.minecraft.command.Cell(x, y, z))) {
                        column.clear(); // a kept cell is a floor of its own: nothing over it is a hollow
                        continue;
                    }
                    column.add(new BlockPos(x, y, z));
                }
                if (grounded || world.isSolid(x, box.minY() - 1, z) || world.isHazard(x, box.minY() - 1, z)) {
                    cells.addAll(column);
                }
            }
        }
        cells.sort(java.util.Comparator.comparingInt(BlockPos::y));
        return List.copyOf(cells);
    }

    /**
     * Whether a clear digs the block at (x, y, z): a tree's, or the ground's - never ground with
     * water beside or over it, which would flow into the hole and the camp.
     */
    public static boolean diggable(WorldView world, int x, int y, int z) {
        if (world.isLog(x, y, z) || world.leaves(x, y, z).isPresent()) {
            return true;
        }
        return (world.isSoil(x, y, z) && !world.isFarmland(x, y, z) || world.isStone(x, y, z) || world.isGravel(x, y, z))
                && !letsInWater(world, new BlockPos(x, y, z));
    }

    /**
     * Whether breaking {@code block} would open a way in for water or lava: one beside it or above
     * it would flow into the hole. Live, digging by a pond flooded the camp's mine shaft, with
     * soldiers in it - the miner checked this for its own cuts, and the other digs didn't.
     */
    public static boolean letsInWater(WorldView world, BlockPos block) {
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
        for (int[] d : sides) {
            if (world.isHazard(block.x() + d[0], block.y() + d[1], block.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    /** The order a clear digs in: a tree from its foot up, then the ground from its top down. */
    static int order(WorldView world, BlockPos c) {
        boolean tree = world.isLog(c.x(), c.y(), c.z()) || world.leaves(c.x(), c.y(), c.z()).isPresent();
        return tree ? c.y() : 10_000 - c.y();
    }

    /**
     * The next block of {@code cover} to cut for a soldier at {@code soldier}: one not {@code excluded},
     * with a cell to stand in that its eyes reach it from - the lowest and nearest first.
     */
    public static Optional<Cut> next(WorldView world, List<BlockPos> cover, Position soldier, Set<BlockPos> excluded) {
        Cut best = null;
        double bestScore = Double.MAX_VALUE;
        int lowest = cover.stream().mapToInt(c -> order(world, c)).min().orElse(0);
        // Where it stands, if it's somewhere to stand: up in a canopy already - climbed, or left there
        // by a cut below - it cuts what it reaches from there, and so its way down. Live, a soldier in
        // a canopy over the camp was sent to stands on the ground it had no way to, and stood an hour.
        BlockPos here = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        boolean canStandHere = Pathfinder.standable(world, here) && !excluded.contains(here)
                && (world.isLog(here.x(), here.y() - 1, here.z()) || world.leaves(here.x(), here.y() - 1, here.z()).isPresent());
        for (BlockPos block : cover) {
            // The list is the squad loop's, up to a cycle old: a block a squadmate has cut since is gone.
            if (excluded.contains(block) || !diggable(world, block.x(), block.y(), block.z())) {
                continue;
            }
            // Ground only from an open top - the digger won't take it otherwise - so the block over it
            // first: live, a soldier near a low block of a mound was handed it under one still there,
            // its digger refused it every try, and it said it couldn't clear the camp.
            boolean ground = !world.isLog(block.x(), block.y(), block.z()) && world.leaves(block.x(), block.y(), block.z()).isEmpty();
            if (ground && world.isSolid(block.x(), block.y() + 1, block.z())) {
                continue;
            }
            // In digging order: past the best score, nothing later can beat it.
            if (HEIGHT_WEIGHT * (order(world, block) - lowest) > bestScore) {
                break;
            }
            Optional<BlockPos> stand = canStandHere && !here.offset(0, -1, 0).equals(block) && reach(here, block) <= REACH
                    ? Optional.of(here) : standFor(world, block, excluded);
            if (stand.isEmpty()) {
                continue;
            }
            BlockPos s = stand.get();
            double score = Math.hypot(s.x() + 0.5 - soldier.x(), s.z() + 0.5 - soldier.z()) + Math.abs(s.y() - soldier.y())
                    + HEIGHT_WEIGHT * (order(world, block) - lowest);
            if (score < bestScore) {
                best = new Cut(block, s, world.isLog(block.x(), block.y(), block.z()));
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The nearest cell to {@code block} a soldier can stand in and reach it from. Ground is dug from
     * no more than a block below where it can be, as a hole is dug from its top: in the squad
     * simulation, a squadmate digging a stair up to one boxed in below was sent to stand in the box.
     * Not from above only: then a mound is climbed, on blocks put down, to dig its top.
     */
    static Optional<BlockPos> standFor(WorldView world, BlockPos block, Set<BlockPos> excluded) {
        boolean ground = !world.isLog(block.x(), block.y(), block.z()) && world.leaves(block.x(), block.y(), block.z()).isEmpty();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -STAND_ACROSS; dx <= STAND_ACROSS; dx++) {
            for (int dz = -STAND_ACROSS; dz <= STAND_ACROSS; dz++) {
                for (int dy = -(int) Math.ceil(REACH + EYES); dy <= 1; dy++) {
                    BlockPos stand = block.offset(dx, dy, dz);
                    // On the ground, not on the tree: never on the log being cut, nor up in a canopy.
                    if (excluded.contains(stand) || !Pathfinder.standable(world, stand) || stand.offset(0, -1, 0).equals(block)
                            || world.isLog(stand.x(), stand.y() - 1, stand.z()) || world.leaves(stand.x(), stand.y() - 1, stand.z()).isPresent()) {
                        continue;
                    }
                    double distance = reach(stand, block) + (ground && stand.y() < block.y() - 1 ? REACH : 0);
                    if (reach(stand, block) <= REACH && distance < bestDistance) {
                        best = stand;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** From the eyes of a soldier standing in {@code stand} to the nearest point of {@code block}. */
    static double reach(BlockPos stand, BlockPos block) {
        double ex = stand.x() + 0.5, ey = stand.y() + EYES, ez = stand.z() + 0.5;
        double dx = ex - Math.clamp(ex, block.x(), block.x() + 1);
        double dy = ey - Math.clamp(ey, block.y(), block.y() + 1);
        double dz = ez - Math.clamp(ez, block.z(), block.z() + 1);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
