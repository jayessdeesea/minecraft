package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey.Ground;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return survey(world, pointMan, 0);
    }

    /**
     * As {@link #survey(WorldView, Position)}, for a point man carrying {@code blocks} to pillar
     * with: ground up a wall or cliff counts as reachable too, priced in the blocks it takes.
     */
    public static TerrainSurvey survey(WorldView world, Position pointMan, int blocks) {
        return survey(world, pointMan, blocks, false);
    }

    /**
     * As {@link #survey(WorldView, Position, int)}, for a point man that cuts steps into stone with a
     * {@code pickaxe}: the steps it cuts stand for the squad following it.
     */
    public static TerrainSurvey survey(WorldView world, Position pointMan, int blocks, boolean pickaxe) {
        return survey(world, pointMan, blocks, pickaxe, java.util.Optional.empty());
    }

    /** As {@link #survey(WorldView, Position, int, boolean)}, sampling the {@code objective}'s own column too. */
    public static TerrainSurvey survey(WorldView world, Position pointMan, int blocks, boolean pickaxe,
                                       java.util.Optional<Position> objective) {
        BlockPos start = groundUnder(world, BlockPos.containing(pointMan.x(), pointMan.y(), pointMan.z()));
        Map<BlockPos, Integer> reachable = Pathfinder.climbs(world, start, RADIUS + 2, FLOOD_BUDGET, blocks, pickaxe);

        List<TerrainSurvey.Sample> samples = new ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz += SPACING) {
            for (int dx = -RADIUS; dx <= RADIUS; dx += SPACING) {
                samples.add(sample(world, start.x() + dx, start.z() + dz, start.y(), reachable));
            }
        }
        java.util.Optional<TerrainSurvey.Sample> atObjective = objective
                .filter(o -> Math.abs(o.x() - start.x()) <= RADIUS && Math.abs(o.z() - start.z()) <= RADIUS)
                .map(o -> sample(world, (int) Math.floor(o.x()), (int) Math.floor(o.z()), start.y(), reachable));
        return new TerrainSurvey(start.x(), start.z(), SPACING, RADIUS, List.copyOf(samples), atObjective);
    }

    /**
     * The standing cell at or just below {@code cell} - the point man may be mid-jump or mid-fall
     * when surveyed, and a flood fill from a cell he isn't standing in finds nothing reachable. Under
     * water, the nearest cell beside him he could stand or swim in.
     */
    /** Where a body at {@code cell} would be standing or swimming: at it, just under it, or beside it. */
    public static BlockPos groundAt(WorldView world, BlockPos cell) {
        return groundUnder(world, cell);
    }

    static BlockPos groundUnder(WorldView world, BlockPos cell) {
        for (int ring = 0; ring <= WADE_RADIUS; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = ring == 0 ? 0 : -1; dy <= (ring == 0 ? 0 : 1); dy++) {
                        for (int drop = 0; drop <= Pathfinder.MAX_DROP + 1; drop++) {
                            BlockPos below = new BlockPos(cell.x() + dx, cell.y() + dy - drop, cell.z() + dz);
                            if (Pathfinder.footing(world, below)) {
                                return below;
                            }
                        }
                    }
                }
            }
        }
        return cell;
    }

    /** How far from a point man with nowhere to stand or swim to look for somewhere to survey from. */
    private static final int WADE_RADIUS = 2;

    private static TerrainSurvey.Sample sample(WorldView world, int x, int z, int aroundY, Map<BlockPos, Integer> reachable) {
        for (int y = aroundY + VERTICAL_SCAN; y >= aroundY - VERTICAL_SCAN; y--) {
            if (world.isHazard(x, y, z)) {
                return new TerrainSurvey.Sample(x, z, Ground.WATER, y, false);
            }
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell)) {
                Integer climb = reachable.get(cell);
                return new TerrainSurvey.Sample(x, z, Ground.LAND, y, climb != null, climb == null ? 0 : climb);
            }
        }
        return new TerrainSurvey.Sample(x, z, Ground.UNKNOWN, aroundY, false);
    }
}
