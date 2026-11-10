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
 * The camp's sky, cleared: every log and leaf over the camp's ground is cover a monster shelters
 * under at dawn, when the open sky would burn it (docs/military-doctrine.md § 15). Trees are felled
 * from the bottom, lowest cut first; leaves cut off from every log decay on their own, so the ones
 * out of reach go once the trunk does.
 */
public final class Clearing {

    /** How far above the camp's ground cover is looked for. */
    static final int SKY = 24;
    /** How far below it: a tree on a dip in the camp's ground still shades it. */
    static final int BELOW = 2;
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

    /** Every log and leaf block over the ground within {@code radius} of {@code centre}, the camp's feet level - lowest first. */
    public static List<BlockPos> cover(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> cover = new ArrayList<>();
        for (int y = centre.y() - BELOW; y <= centre.y() + SKY; y++) {
            for (int x = centre.x() - radius; x <= centre.x() + radius; x++) {
                for (int z = centre.z() - radius; z <= centre.z() + radius; z++) {
                    if (world.isLog(x, y, z) || world.leaves(x, y, z).isPresent()) {
                        cover.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(cover);
    }

    /**
     * The camp's high ground, the second phase once the sky is clear: the top block of each column
     * inside the wall line (within {@code radius} of {@code centre}) that is dirt or grass at or
     * above the camp's feet level, and open above - dug off a layer at a time, so the ground comes
     * down to the camp's own height and the squads see across it. Never one in {@code keep}: the
     * squads' huts. Its dirt is the wall's.
     */
    public static List<BlockPos> humps(WorldView world, BlockPos centre, int radius, Set<BlockPos> keep) {
        List<BlockPos> humps = new ArrayList<>();
        for (int x = centre.x() - radius; x <= centre.x() + radius; x++) {
            for (int z = centre.z() - radius; z <= centre.z() + radius; z++) {
                for (int y = centre.y() + SKY; y >= centre.y(); y--) {
                    if (!world.isSolid(x, y, z)) {
                        continue;
                    }
                    BlockPos top = new BlockPos(x, y, z);
                    // Never one with water beside it: dug off, it would let the water into the camp.
                    boolean wet = world.isHazard(x + 1, y, z) || world.isHazard(x - 1, y, z) || world.isHazard(x, y, z + 1)
                            || world.isHazard(x, y, z - 1) || world.isHazard(x, y + 1, z);
                    if (world.isSoil(x, y, z) && !world.isFarmland(x, y, z) && !keep.contains(top) && !wet) {
                        humps.add(top);
                    }
                    break;
                }
            }
        }
        humps.sort(java.util.Comparator.comparingInt(BlockPos::y));
        return List.copyOf(humps);
    }

    /** How far below the camp's feet level a hollow is filled from. */
    static final int HOLLOW_DEPTH = 16;

    /**
     * The camp's hollows, the third phase once its high ground is down: every open cell inside the
     * wall line (within {@code radius} of {@code centre}) below the camp's feet level, down to the
     * ground in its column - lowest first, so each is filled on the one beneath it and the floor
     * rises under whoever fills it. Never one in {@code keep}: the mine's shaft, the squads' huts.
     * Not under water, which is a hazard to stand in, and not deeper than {@link #HOLLOW_DEPTH}: a
     * cave below isn't the camp's ground. Live, a pit 13 deep under the wall line left the wall
     * nothing to stand on.
     */
    public static List<BlockPos> hollows(WorldView world, BlockPos centre, int radius, Set<BlockPos> keep) {
        List<BlockPos> hollows = new ArrayList<>();
        for (int x = centre.x() - radius; x <= centre.x() + radius; x++) {
            for (int z = centre.z() - radius; z <= centre.z() + radius; z++) {
                int ground = Integer.MIN_VALUE;
                for (int y = centre.y() - 1; y >= centre.y() - HOLLOW_DEPTH; y--) {
                    if (world.isSolid(x, y, z) || world.isHazard(x, y, z)) {
                        ground = y;
                        break;
                    }
                }
                // Water is the floor of a flooded pit: filled from its surface up, so the water is capped
                // and whoever is afloat in it gets ground to stand on, rising as it fills.
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                for (int y = ground + 1; y < centre.y(); y++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (keep.contains(cell)) {
                        break;
                    }
                    hollows.add(cell);
                }
            }
        }
        hollows.sort(java.util.Comparator.comparingInt(BlockPos::y));
        return List.copyOf(hollows);
    }

    /**
     * The next block of {@code cover} to cut for a soldier at {@code soldier}: one not {@code excluded},
     * with a cell to stand in that its eyes reach it from - the lowest and nearest first.
     */
    public static Optional<Cut> next(WorldView world, List<BlockPos> cover, Position soldier, Set<BlockPos> excluded) {
        Cut best = null;
        double bestScore = Double.MAX_VALUE;
        int lowest = cover.stream().mapToInt(BlockPos::y).min().orElse(0);
        for (BlockPos block : cover) {
            // The list is the squad loop's, up to a cycle old: a block a squadmate has cut since is gone.
            if (excluded.contains(block) || !world.isLog(block.x(), block.y(), block.z())
                    && world.leaves(block.x(), block.y(), block.z()).isEmpty() && !world.isSoil(block.x(), block.y(), block.z())) {
                continue;
            }
            // Cover is lowest first: past the best score, nothing higher can beat it.
            if (HEIGHT_WEIGHT * (block.y() - lowest) > bestScore) {
                break;
            }
            Optional<BlockPos> stand = standFor(world, block, excluded);
            if (stand.isEmpty()) {
                continue;
            }
            BlockPos s = stand.get();
            double score = Math.hypot(s.x() + 0.5 - soldier.x(), s.z() + 0.5 - soldier.z()) + Math.abs(s.y() - soldier.y())
                    + HEIGHT_WEIGHT * (block.y() - lowest);
            if (score < bestScore) {
                best = new Cut(block, s, world.isLog(block.x(), block.y(), block.z()));
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The nearest cell to {@code block} a soldier can stand in and reach it from. */
    static Optional<BlockPos> standFor(WorldView world, BlockPos block, Set<BlockPos> excluded) {
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
                    double distance = reach(stand, block);
                    if (distance <= REACH && distance < bestDistance) {
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
