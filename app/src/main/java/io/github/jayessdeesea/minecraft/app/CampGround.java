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
     * The columns in {@code dig} never dug past the wall line: the farm's plot, trenches and water, its chunk farms'
     * boxes, and the grove's {@code planted} cells a cell round - each its whole height in the box. Planted, not where a
     * grove could go: on open ground that's anywhere, and in the squad simulation it kept a heap past the wall line.
     */
    static Set<Cell> kept(Box dig, FarmPlan farm, List<BlockPos> planted) {
        Set<int[]> columns = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        if (farm != null) {
            farm.plot().forEach(c -> add(columns, seen, c.x(), c.z(), 0));
            farm.trenches().forEach(c -> add(columns, seen, c.x(), c.z(), 0));
            add(columns, seen, farm.water().x(), farm.water().z(), 1);
            for (ChunkFarm chunk : farm.chunks()) {
                Box b = chunk.box();
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        add(columns, seen, x, z, 0);
                    }
                }
            }
        }
        planted.forEach(c -> add(columns, seen, c.x(), c.z(), 1));
        Set<Cell> kept = new HashSet<>();
        for (int[] c : columns) {
            if (c[0] < dig.minX() || c[0] > dig.maxX() || c[1] < dig.minZ() || c[1] > dig.maxZ()) {
                continue;
            }
            for (int y = dig.minY(); y <= dig.maxY(); y++) {
                kept.add(new Cell(c[0], y, c[1]));
            }
        }
        return kept;
    }

    private static void add(Set<int[]> columns, Set<Long> seen, int x, int z, int round) {
        for (int dx = -round; dx <= round; dx++) {
            for (int dz = -round; dz <= round; dz++) {
                if (seen.add(((long) (x + dx) << 32) ^ ((z + dz) & 0xFFFFFFFFL))) {
                    columns.add(new int[]{x + dx, z + dz});
                }
            }
        }
    }
}
