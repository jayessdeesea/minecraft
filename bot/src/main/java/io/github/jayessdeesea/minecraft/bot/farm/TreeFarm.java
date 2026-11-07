package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a squad plants trees round a point, so a camp that has cut down what grew near it has
 * logs again (docs/military-doctrine.md §12): a grid of cells {@link #SPACING} apart - room for a
 * spruce, the camp's tree, to grow beside its neighbours - each on dirt or grass, with
 * {@link #HEADROOM} blocks of open air over it for the trunk. The cells are where the saplings go;
 * standing in one, a soldier plants on the ground under it.
 *
 * @param centre the ground the plot is laid round
 * @param plot   the cells to plant, nearest the centre first
 */
public record TreeFarm(BlockPos centre, List<BlockPos> plot) {

    /** Blocks between saplings, both ways: a spruce's crown is about five across. */
    public static final int SPACING = 3;
    /** Open air a sapling needs above it to grow into a small spruce. */
    public static final int HEADROOM = 7;
    /** Fewest cells worth calling a tree farm. */
    public static final int MIN_CELLS = 4;
    private static final int VERTICAL = 4;

    /** The tree farm round {@code centre}, out to {@code radius} blocks - empty if the ground has too few cells. */
    public static java.util.Optional<TreeFarm> site(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> plot = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.floorMod(dx, SPACING) != 0 || Math.floorMod(dz, SPACING) != 0) {
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
        return plot.size() < MIN_CELLS ? java.util.Optional.empty() : java.util.Optional.of(new TreeFarm(centre, List.copyOf(plot)));
    }

    /**
     * Whether a sapling can go in {@code cell} and grow: soil under it that isn't farmland, room for
     * a body, and open air above - or a sapling or tree already there, which is the plot doing its job.
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
            if (world.isSolid(cell.x(), cell.y() + up, cell.z())) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code cell} holds a sapling, or the trunk it grew into. */
    public static boolean planted(WorldView world, BlockPos cell) {
        return world.sapling(cell.x(), cell.y(), cell.z()).isPresent() || world.isLog(cell.x(), cell.y(), cell.z());
    }
}
