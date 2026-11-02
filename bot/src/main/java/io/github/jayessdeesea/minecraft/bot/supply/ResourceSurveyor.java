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
 * What wood a squad can see: the nearest stands of trees around a point, from the blocks its
 * soldiers have loaded. A stand is the logs within {@link #STAND_RADIUS} of one another, reported at
 * their middle with their count, nearest first. Precomputed for the general - like the squad
 * leader's terrain survey - rather than handing a model a block scan to read.
 */
public final class ResourceSurveyor {

    /** How far around the squad to look. */
    static final int SURVEY_RADIUS = 64;
    /** Vertically, around the squad's height. */
    static final int SURVEY_HEIGHT = 24;
    /** Logs this close belong to the same stand. */
    static final int STAND_RADIUS = 8;
    /** Fewer logs than this aren't worth sending a squad for. */
    static final int MIN_STAND = 4;
    static final int MAX_STANDS = 3;

    private ResourceSurveyor() {
    }

    public static List<ResourceSite> woods(WorldView world, Position around) {
        BlockPos centre = BlockPos.containing(around.x(), around.y(), around.z());
        List<BlockPos> logs = new ArrayList<>();
        for (int dx = -SURVEY_RADIUS; dx <= SURVEY_RADIUS; dx++) {
            for (int dz = -SURVEY_RADIUS; dz <= SURVEY_RADIUS; dz++) {
                for (int dy = -SURVEY_HEIGHT; dy <= SURVEY_HEIGHT; dy++) {
                    BlockPos cell = centre.offset(dx, dy, dz);
                    if (world.isLog(cell.x(), cell.y(), cell.z())) {
                        logs.add(cell);
                    }
                }
            }
        }
        logs.sort(Comparator.comparingDouble(log -> horizontal(log, centre)));
        List<ResourceSite> stands = new ArrayList<>();
        boolean[] taken = new boolean[logs.size()];
        for (int i = 0; i < logs.size() && stands.size() < MAX_STANDS; i++) {
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
            if (count >= MIN_STAND) {
                stands.add(new ResourceSite(Resource.WOOD, new Position(Math.round(sx / count), Math.round(sy / count),
                        Math.round(sz / count)), count));
            }
        }
        return List.copyOf(stands);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
