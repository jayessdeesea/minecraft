package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where a squad's wheat farm goes, and which cells it's planted in: the ground round one water
 * source, within {@link #HYDRATION} blocks of it - farmland that close to water is hydrated, and
 * wheat on hydrated farmland grows about twice as fast as on dry. A plot cell is ground a hoe
 * tills (or farmland already) level with the water, with open air over it for the crop.
 *
 * <p>Pure: worked out from a {@link WorldView}, so it's tested without a server.
 */
public record FarmPlan(BlockPos water, List<BlockPos> plot) {

    /** How far from water farmland stays hydrated, in each direction. */
    public static final int HYDRATION = 4;
    /** The least a plot is worth tending: a handful of cells grows bread slower than it's eaten. */
    public static final int MIN_CELLS = 12;
    /** How far below and above {@code around} the search looks for water. */
    static final int VERTICAL = 8;

    public FarmPlan {
        plot = List.copyOf(plot);
    }

    /**
     * The best farm within {@code radius} blocks (x and z) of {@code around}: round the water
     * source with the most cells to plant, the nearest of equals - or none with at least
     * {@link #MIN_CELLS}.
     */
    public static Optional<FarmPlan> site(WorldView world, BlockPos around, int radius) {
        return site(world, around, radius, VERTICAL);
    }

    /** {@link #site(WorldView, BlockPos, int)}, looking {@code vertical} blocks below and above {@code around} for water. */
    public static Optional<FarmPlan> site(WorldView world, BlockPos around, int radius, int vertical) {
        return site(world, around, radius, vertical, cell -> true);
    }

    /**
     * As {@link #site(WorldView, BlockPos, int, int)}, counting only plot cells whose standing cell
     * - the one over it - {@code walkable} says the squad can walk to: live, a farm sited by the
     * ground alone lay where no farmer could reach it, and it was kept.
     */
    public static Optional<FarmPlan> site(WorldView world, BlockPos around, int radius, int vertical,
                                          java.util.function.Predicate<BlockPos> walkable) {
        FarmPlan best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -vertical; dy <= vertical; dy++) {
                    BlockPos water = around.offset(dx, dy, dz);
                    if (!world.isWaterSource(water.x(), water.y(), water.z())) {
                        continue;
                    }
                    List<BlockPos> plot = plotAround(world, water).stream().filter(c -> walkable.test(c.offset(0, 1, 0))).toList();
                    double distance = Math.hypot(dx, dz);
                    if (plot.size() >= MIN_CELLS && (best == null || plot.size() > best.plot().size()
                            || plot.size() == best.plot().size() && distance < bestDistance)) {
                        best = new FarmPlan(water, plot);
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** How far from a farm's first water the plots that grow it are looked for: one field, not a walk between two. */
    public static final int GROWN_RADIUS = 16;

    /**
     * {@code farm} grown to at least {@code want} cells, if the ground allows: the plot round the
     * best water within {@link #GROWN_RADIUS} of its own added, cells already in it left out, then
     * the next, until it's big enough or there's no plot of {@link #MIN_CELLS} left to add. One
     * water's plot is 80 cells at best, and by a pond's edge about half that: live, a camp's farm
     * of 44 cells fed eight soldiers a few loaves an hour, and they went hungry.
     */
    public static FarmPlan grown(WorldView world, FarmPlan farm, int want, int vertical, java.util.function.Predicate<BlockPos> walkable) {
        java.util.LinkedHashSet<BlockPos> plot = new java.util.LinkedHashSet<>(farm.plot());
        while (plot.size() < want) {
            java.util.Set<BlockPos> taken = java.util.Set.copyOf(plot);
            Optional<FarmPlan> next = site(world, farm.water(), GROWN_RADIUS, vertical,
                    stand -> walkable.test(stand) && !taken.contains(stand.offset(0, -1, 0)));
            if (next.isEmpty()) {
                break;
            }
            plot.addAll(next.get().plot());
        }
        return plot.size() == farm.plot().size() ? farm : new FarmPlan(farm.water(), List.copyOf(plot));
    }

    /** The cells round {@code water} that make farmland: tillable or tilled, level with it, open above. */
    public static List<BlockPos> plotAround(WorldView world, BlockPos water) {
        List<BlockPos> plot = new ArrayList<>();
        for (int dx = -HYDRATION; dx <= HYDRATION; dx++) {
            for (int dz = -HYDRATION; dz <= HYDRATION; dz++) {
                BlockPos cell = water.offset(dx, 0, dz);
                boolean ground = world.isTillable(cell.x(), cell.y(), cell.z()) || world.isFarmland(cell.x(), cell.y(), cell.z());
                int y = cell.y() + 1;
                boolean open = !world.isSolid(cell.x(), y, cell.z()) && !world.isHazard(cell.x(), y, cell.z());
                if ((dx != 0 || dz != 0) && ground && open) {
                    plot.add(cell);
                }
            }
        }
        plot.sort(Comparator.comparingInt((BlockPos c) -> Math.abs(c.x() - water.x()) + Math.abs(c.z() - water.z())));
        return plot;
    }
}
