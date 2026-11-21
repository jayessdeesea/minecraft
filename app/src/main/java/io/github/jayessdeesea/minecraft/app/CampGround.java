package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How far the camp's ground work reaches (operator, 2026-11-20: the ground a bit flatter): the box inside the wall
 * line first, then out a ring at a time once it's level - dug down, not filled, past the wall line - to the camp's
 * radius. Past the wall line the camp's farm and its chunk farms, and the camp's grove, are never its work: a
 * grove's trees, a farm's crop and its water stand.
 */
final class CampGround {

    private static final Logger LOG = LogManager.getLogger(CampGround.class);

    /** How much further out each ring takes the dig. */
    static final int RING = 4;

    private CampGround() {
    }

    /** The dig's next radius: one ring further once the box is level, dug and filled, up to {@code most}. */
    static int grown(int radius, int digLeft, int fillLeft, int most, String squad) {
        if (digLeft != 0 || fillLeft != 0 || radius >= most) {
            return radius;
        }
        int next = Math.min(most, radius + RING);
        LOG.info("[{}] the camp's ground level out to {} - its ground work goes out to {}", squad, radius, next);
        return next;
    }

    /**
     * The cells in {@code dig} its ground work leaves: past the wall line ({@code inner}), the farm's plot, trenches and
     * water, its chunk farms' boxes, and the grove's {@code planted} cells a cell round - each its whole height in the
     * box. Planted, not where a grove could go: on open ground that's anywhere, and in the squad simulation it kept a heap
     * past the wall line. Inside the wall line a farm cell keeps only its ground and the cell over it, the crop's: the
     * heap over it is the camp's ground, dug with the rest - live (field584), plot cells under the camp's hillside, four
     * to seven blocks down, were kept whole by the camp's dig and out of the farmer's reach, nobody's.
     */
    static Set<Cell> kept(Box dig, Box inner, FarmPlan farm, List<BlockPos> planted) {
        Set<Cell> kept = new HashSet<>();
        if (farm != null) {
            for (BlockPos c : farm.plot()) {
                keep(kept, dig, inner, c.x(), c.z(), c.y());
            }
            for (BlockPos c : farm.trenches()) {
                keep(kept, dig, inner, c.x(), c.z(), c.y());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    keep(kept, dig, inner, farm.water().x() + dx, farm.water().z() + dz, farm.water().y());
                }
            }
            for (ChunkFarm chunk : farm.chunks()) {
                Box b = chunk.box();
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        keep(kept, dig, inner, x, z, chunk.level());
                    }
                }
            }
        }
        for (BlockPos c : planted) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    keep(kept, dig, null, c.x() + dx, c.z() + dz, c.y());
                }
            }
        }
        return kept;
    }

    /** Column ({@code x}, {@code z}) of {@code dig} kept: its whole height past {@code inner}, inside it only {@code ground} and the cell over it. */
    private static void keep(Set<Cell> kept, Box dig, Box inner, int x, int z, int ground) {
        if (x < dig.minX() || x > dig.maxX() || z < dig.minZ() || z > dig.maxZ()) {
            return;
        }
        boolean in = inner != null && x >= inner.minX() && x <= inner.maxX() && z >= inner.minZ() && z <= inner.maxZ();
        for (int y = in ? Math.max(dig.minY(), ground) : dig.minY(); y <= (in ? Math.min(dig.maxY(), ground + 1) : dig.maxY()); y++) {
            kept.add(new Cell(x, y, z));
        }
    }
}
