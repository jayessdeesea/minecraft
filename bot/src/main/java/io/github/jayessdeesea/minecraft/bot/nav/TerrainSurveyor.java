package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey.Ground;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link TerrainSurvey} for a squad leader from the shared world model: ground height at
 * each sample column, and whether the point man can reach it (one {@link Pathfinder#reachable}
 * flood fill, so reachability uses exactly the moves soldiers can make). Cheap enough to run per
 * squad-leader decision - a few thousand block lookups plus a bounded flood fill - not per tick.
 */
public final class TerrainSurveyor {

    public static final int SPACING = 8;
    public static final int RADIUS = 32;
    /** How far above and below the point man to look for ground in each column. */
    private static final int VERTICAL_SCAN = 24;
    private static final int FLOOD_BUDGET = 40_000;

    private TerrainSurveyor() {
    }

    public static TerrainSurvey survey(WorldView world, Position pointMan) {
        return survey(world, pointMan, false);
    }

    /**
     * As {@link #survey(WorldView, Position)}; with {@code canClimb} - the point man carries blocks to
     * pillar with - ground up a wall or cliff counts as reachable too.
     */
    public static TerrainSurvey survey(WorldView world, Position pointMan, boolean canClimb) {
        BlockPos start = groundUnder(world, BlockPos.containing(pointMan.x(), pointMan.y(), pointMan.z()));
        Set<BlockPos> reachable = Pathfinder.reachable(world, start, RADIUS + 2, FLOOD_BUDGET, canClimb);

        List<TerrainSurvey.Sample> samples = new ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz += SPACING) {
            for (int dx = -RADIUS; dx <= RADIUS; dx += SPACING) {
                samples.add(sample(world, start.x() + dx, start.z() + dz, start.y(), reachable));
            }
        }
        return new TerrainSurvey(start.x(), start.z(), SPACING, RADIUS, List.copyOf(samples));
    }

    /**
     * The standing cell at or just below {@code cell} - the point man may be mid-jump or mid-fall
     * when surveyed, and a flood fill from a cell he isn't standing in finds nothing reachable.
     */
    private static BlockPos groundUnder(WorldView world, BlockPos cell) {
        for (int drop = 0; drop <= Pathfinder.MAX_DROP + 1; drop++) {
            BlockPos below = new BlockPos(cell.x(), cell.y() - drop, cell.z());
            if (Pathfinder.standable(world, below)) {
                return below;
            }
        }
        return cell;
    }

    private static TerrainSurvey.Sample sample(WorldView world, int x, int z, int aroundY, Set<BlockPos> reachable) {
        for (int y = aroundY + VERTICAL_SCAN; y >= aroundY - VERTICAL_SCAN; y--) {
            if (world.isHazard(x, y, z)) {
                return new TerrainSurvey.Sample(x, z, Ground.WATER, y, false);
            }
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell)) {
                return new TerrainSurvey.Sample(x, z, Ground.LAND, y, reachable.contains(cell));
            }
        }
        return new TerrainSurvey.Sample(x, z, Ground.UNKNOWN, aroundY, false);
    }
}
