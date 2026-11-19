package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
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
 * to reach. Precomputed for the platoon leader - like the squad
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
    /**
     * Stands shown, the nearest and then the biggest: enough that, with the woods found cut out dropped from them
     * (app.WoodSightings), one is left to send a squad to - live (field478), the one wood it was sent to cut out, three
     * shown left the platoon leader the spent ones.
     */
    static final int MAX_STANDS = 6;

    private ResourceSurveyor() {
    }

    /** How far round a log a tree's leaves are looked for. */
    static final int LEAF_REACH = 4;

    /**
     * Whether the log at {@code cell} is a tree's: leaves within {@link #LEAF_REACH} of it, round it and over it.
     * A structure's logs have none - live (field432), an ancient city's dark oak at y=-52, under the miners,
     * was a stand of wood the squad was sent to gather, 115 blocks under the surface.
     */
    static boolean leafy(WorldView world, BlockPos cell) {
        for (int dx = -LEAF_REACH; dx <= LEAF_REACH; dx++) {
            for (int dz = -LEAF_REACH; dz <= LEAF_REACH; dz++) {
                for (int dy = -1; dy <= 2 * LEAF_REACH; dy++) {
                    if (world.leaves(cell.x() + dx, cell.y() + dy, cell.z() + dz).isPresent()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** How far trees are looked for once every wood within {@link #SURVEY_RADIUS} is spent: seven chunks, still loaded. */
    static final int FAR_RADIUS = 112;

    /** Trees then stone, each nearest first. */
    public static List<ResourceSite> survey(WorldView world, Position around) {
        return survey(world, around, at -> false);
    }

    /**
     * As {@link #survey(WorldView, Position)}, leaving out the trees of the woods {@code spent} says were found cut out -
     * and, with none left within {@link #SURVEY_RADIUS}, looking for trees out to {@link #FAR_RADIUS}: live (field478),
     * the woods in reach cut out, the force short of wood had nowhere to send anyone.
     */
    public static List<ResourceSite> survey(WorldView world, Position around, java.util.function.Predicate<Position> spent) {
        BlockPos centre = BlockPos.containing(around.x(), around.y(), around.z());
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> stone = new ArrayList<>();
        scan(world, centre, SURVEY_RADIUS, 0, logs, stone, spent);
        if (logs.isEmpty()) {
            scan(world, centre, FAR_RADIUS, SURVEY_RADIUS, logs, null, spent);
        }
        List<ResourceSite> sites = new ArrayList<>(cluster(logs, centre, Resource.WOOD, MIN_STAND));
        sites.addAll(cluster(stone, centre, Resource.STONE, MIN_OUTCROP));
        // Ground to farm: the best plot by water in sight, as many cells as it has to plant - placed
        // where a soldier stands on the plot by the water, not in the water, which no survey starts from.
        FarmPlan.site(world, centre, FARM_RADIUS, SURVEY_HEIGHT).ifPresent(farm -> {
            BlockPos water = farm.water();
            BlockPos land = farm.plot().stream()
                    .min(Comparator.comparingInt(c -> Math.abs(c.x() - water.x()) + Math.abs(c.z() - water.z()))).orElseThrow();
            sites.add(new ResourceSite(Resource.FARMLAND, new Position(land.x(), land.y() + 1, land.z()), farm.plot().size()));
        });
        return List.copyOf(sites);
    }

    /**
     * The leafy logs - not in a wood {@code spent} - and, given a list, the exposed stone within {@code radius} of
     * {@code centre}, past {@code inner} (a ring: what's inside it was looked at already).
     */
    private static void scan(WorldView world, BlockPos centre, int radius, int inner, List<BlockPos> logs, List<BlockPos> stone,
                             java.util.function.Predicate<Position> spent) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= inner && inner > 0) {
                    continue;
                }
                for (int dy = -SURVEY_HEIGHT; dy <= SURVEY_HEIGHT; dy++) {
                    BlockPos cell = centre.offset(dx, dy, dz);
                    if (world.isLog(cell.x(), cell.y(), cell.z()) && leafy(world, cell) && !spent.test(new Position(cell.x(), cell.y(), cell.z()))) {
                        logs.add(cell);
                    } else if (stone != null && world.isStone(cell.x(), cell.y(), cell.z()) && exposed(world, cell)) {
                        stone.add(cell);
                    }
                }
            }
        }
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
