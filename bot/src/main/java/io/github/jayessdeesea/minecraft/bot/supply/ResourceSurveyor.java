package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a squad can see to gather: for trees and for exposed stone, the nearest site and the two
 * biggest around a point, from the blocks its soldiers have loaded. A site is the blocks of one material within
 * {@link #STAND_RADIUS} of one another, reported at their middle with their count, nearest first.
 * Stone counts only where it's exposed to the open sky - beside or under an open cell with nothing
 * solid over it for {@link #SKY_CLEARANCE} blocks - since buried stone, or a cave wall, takes a mine
 * to reach. Precomputed for the general - like the squad
 * leader's terrain survey - rather than handing a model a block scan to read.
 */
public final class ResourceSurveyor {

    /** How far around the squad to look. */
    static final int SURVEY_RADIUS = 64;
    /** How far out a farm site is looked for: a farm is tended for days, so near is worth more than big. */
    static final int FARM_RADIUS = 48;
    /** Vertically, around the squad's height. */
    static final int SURVEY_HEIGHT = 24;
    /** Logs this close belong to the same stand. */
    static final int STAND_RADIUS = 8;
    /** Fewer logs than this aren't worth sending a squad for. */
    static final int MIN_STAND = 4;
    /** Nor fewer blocks of exposed stone than this. */
    static final int MIN_OUTCROP = 8;
    /** An open cell with this much open space above it is under the sky, not in a cave. */
    static final int SKY_CLEARANCE = 12;
    static final int MAX_STANDS = 3;

    private ResourceSurveyor() {
    }

    /** Trees then stone, each nearest first. */
    public static List<ResourceSite> survey(WorldView world, Position around) {
        BlockPos centre = BlockPos.containing(around.x(), around.y(), around.z());
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> stone = new ArrayList<>();
        for (int dx = -SURVEY_RADIUS; dx <= SURVEY_RADIUS; dx++) {
            for (int dz = -SURVEY_RADIUS; dz <= SURVEY_RADIUS; dz++) {
                for (int dy = -SURVEY_HEIGHT; dy <= SURVEY_HEIGHT; dy++) {
                    BlockPos cell = centre.offset(dx, dy, dz);
                    if (world.isLog(cell.x(), cell.y(), cell.z())) {
                        logs.add(cell);
                    } else if (world.isStone(cell.x(), cell.y(), cell.z()) && exposed(world, cell)) {
                        stone.add(cell);
                    }
                }
            }
        }
        List<ResourceSite> sites = new ArrayList<>(cluster(logs, centre, Resource.WOOD, MIN_STAND));
        sites.addAll(cluster(stone, centre, Resource.STONE, MIN_OUTCROP));
        // Ground to farm: the best plot by water in sight, as many cells as it has to plant - placed
        // where a soldier stands on the plot by the water, not in the water, which no survey starts from.
        io.github.jayessdeesea.minecraft.bot.farm.FarmPlan.site(world, centre, FARM_RADIUS, SURVEY_HEIGHT).ifPresent(farm -> {
            BlockPos water = farm.water();
            BlockPos land = farm.plot().stream()
                    .min(java.util.Comparator.comparingInt(c -> Math.abs(c.x() - water.x()) + Math.abs(c.z() - water.z()))).orElseThrow();
            sites.add(new ResourceSite(Resource.FARMLAND, new Position(land.x(), land.y() + 1, land.z()), farm.plot().size()));
        });
        return List.copyOf(sites);
    }

    private static boolean exposed(WorldView world, BlockPos cell) {
        return underSky(world, cell.offset(0, 1, 0)) || underSky(world, cell.offset(1, 0, 0))
                || underSky(world, cell.offset(-1, 0, 0)) || underSky(world, cell.offset(0, 0, 1))
                || underSky(world, cell.offset(0, 0, -1));
    }

    private static boolean underSky(WorldView world, BlockPos open) {
        for (int up = 0; up <= SKY_CLEARANCE; up++) {
            if (world.isSolid(open.x(), open.y() + up, open.z())) {
                return false;
            }
        }
        return true;
    }

    private static List<ResourceSite> cluster(List<BlockPos> logs, BlockPos centre, Resource resource, int minimum) {
        logs.sort(Comparator.comparingDouble(log -> horizontal(log, centre)));
        List<ResourceSite> stands = new ArrayList<>();
        boolean[] taken = new boolean[logs.size()];
        for (int i = 0; i < logs.size(); i++) {
            if (taken[i]) {
                continue;
            }
            BlockPos seed = logs.get(i);
            double sx = 0, sy = 0, sz = 0;
            int count = 0;
            for (int j = i; j < logs.size(); j++) {
                if (!taken[j] && horizontal(logs.get(j), seed) <= STAND_RADIUS) {
                    taken[j] = true;
                    sx += logs.get(j).x();
                    sy += logs.get(j).y();
                    sz += logs.get(j).z();
                    count++;
                }
            }
            if (count >= minimum) {
                stands.add(new ResourceSite(resource, new Position(Math.round(sx / count), Math.round(sy / count),
                        Math.round(sz / count)), count));
            }
        }
        // The nearest, and the biggest: a lone tree by the camp is all a nearest-three list shows
        // once the camp has cut what grew round it, with a forest just past it.
        List<ResourceSite> shown = new ArrayList<>();
        stands.stream().findFirst().ifPresent(shown::add);
        stands.stream().sorted(Comparator.comparingInt(ResourceSite::amount).reversed())
                .filter(stand -> !shown.contains(stand)).limit(MAX_STANDS - shown.size()).forEach(shown::add);
        shown.sort(Comparator.comparingDouble(stand -> Math.hypot(stand.location().x() - centre.x(), stand.location().z() - centre.z())));
        return List.copyOf(shown);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
