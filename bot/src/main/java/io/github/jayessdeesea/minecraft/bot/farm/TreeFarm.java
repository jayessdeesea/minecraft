package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where a squad plants trees round a point, so a camp that has cut down what grew near it has
 * logs again (docs/military-doctrine.md §12): the world's grid of cells {@link #SPACING} apart ({@link #onGrid}) - room for a
 * spruce, the camp's tree, to grow beside its neighbours - each on dirt or grass, with
 * {@link #HEADROOM} blocks of open air over it for the trunk. The cells are where the saplings go;
 * standing in one, a soldier plants on the ground under it.
 *
 * @param centre the ground the plot is laid round
 * @param plot   the cells to plant, nearest the centre first
 */
public record TreeFarm(BlockPos centre, List<BlockPos> plot) {

    /**
     * Blocks from sapling to sapling, both ways (operator, 2026-11-14: a spruce every four blocks):
     * a small spruce's crown is about five across, so neighbours' crowns touch without shading out.
     */
    public static final int SPACING = 4;
    /** Open air a sapling needs above it to grow into a small spruce. */
    public static final int HEADROOM = 7;
    /** Fewest cells worth calling a tree farm. */
    public static final int MIN_CELLS = 4;
    private static final int VERTICAL = 4;

    /** The tree farm round {@code centre}, out to {@code radius} blocks - empty if the ground has too few cells. */
    public static Optional<TreeFarm> site(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> plot = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!onGrid(centre.x() + dx, centre.z() + dz)) {
                    continue;
                }
                for (int dy = VERTICAL; dy >= -VERTICAL; dy--) {
                    BlockPos cell = centre.offset(dx, dy, dz);
                    if (plantable(world, cell)) {
                        plot.add(cell);
                        break;
                    }
                }
            }
        }
        plot.sort(Comparator.comparingInt(c -> Math.abs(c.x() - centre.x()) + Math.abs(c.z() - centre.z())));
        return plot.size() < MIN_CELLS ? Optional.empty() : Optional.of(new TreeFarm(centre, List.copyOf(plot)));
    }

    /**
     * Whether a sapling can go in {@code cell} and grow: soil under it that isn't farmland, room for a body, and room to
     * grow - its trunk's column open {@link #HEADROOM} up, and the ring a block round it open over the trunk's top
     * {@link #CROWN} blocks, where a birch's leaves form (operator, 2026-11-21: sufficient horizontal and vertical
     * space) - or a sapling or tree already there, which is the plot doing its job. Leaves don't count against it: a
     * felled tree's decay within minutes, and a stump is replanted under its own.
     */
    static boolean plantable(WorldView world, BlockPos cell) {
        if (planted(world, cell)) {
            return true;
        }
        if (!Pathfinder.standable(world, cell) || !world.isSoil(cell.x(), cell.y() - 1, cell.z())
                || world.isFarmland(cell.x(), cell.y() - 1, cell.z())) {
            return false;
        }
        for (int up = 0; up < HEADROOM; up++) {
            if (blocks(world, cell.x(), cell.y() + up, cell.z())) {
                return false;
            }
        }
        for (int up = HEADROOM - CROWN; up < HEADROOM; up++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && blocks(world, cell.x() + dx, cell.y() + up, cell.z() + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The top blocks of a trunk's height its crown needs the ring round it open over. */
    static final int CROWN = 3;

    /** Whether the block at (x, y, z) keeps a tree from growing into it: solid, and not leaves - those decay. */
    private static boolean blocks(WorldView world, int x, int y, int z) {
        return world.isSolid(x, y, z) && world.leaves(x, y, z).isEmpty();
    }

    /**
     * Whether a sapling goes in column x, z: the world's own grid, x and z each two past a multiple of
     * {@link #SPACING} (operator, 2026-11-14), not one laid from wherever a grove's centre fell - so a
     * grove sited again, or after a restart, or beside another, lines up with the trees already
     * growing.
     */
    public static boolean onGrid(int x, int z) {
        return Math.floorMod(x + 2, SPACING) == 0 && Math.floorMod(z + 2, SPACING) == 0;
    }

    /** Whether {@code cell} holds a sapling, or the trunk it grew into. */
    public static boolean planted(WorldView world, BlockPos cell) {
        return world.sapling(cell.x(), cell.y(), cell.z()).isPresent() || world.isLog(cell.x(), cell.y(), cell.z());
    }
}
