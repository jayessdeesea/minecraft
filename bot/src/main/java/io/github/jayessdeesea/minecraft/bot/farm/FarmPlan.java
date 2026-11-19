package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a squad's wheat farm goes, and which cells it's planted in: the ground round one water
 * source, within {@link #HYDRATION} blocks of it - farmland that close to water is hydrated, and
 * wheat on hydrated farmland grows about twice as fast as on dry. A plot cell is ground a hoe
 * tills (or farmland already) level with the water, with open air over it for the crop. Before the force has a
 * bucket the farm grows along {@link #trenches}: a block wide, cut at the farmland's level off a pond, the pond's
 * water flowing down each to {@link #FLOW} blocks, and hydrating the ground beside it as a source does. With one, it
 * takes in {@link #chunks}: their holes dug in the farmland's layer and filled by bucket, their fences, gates and chests
 * put up.
 *
 * <p>Pure: worked out from a {@link WorldView}, so it's tested without a server.
 */
public record FarmPlan(BlockPos water, List<BlockPos> plot, List<BlockPos> trenches, List<ChunkFarm> chunks) {

    /** How far from water farmland stays hydrated, in each direction. */
    public static final int HYDRATION = 4;
    /** The least a plot is worth tending: a handful of cells grows bread slower than it's eaten. */
    public static final int MIN_CELLS = 12;
    /** How far below and above {@code around} the search looks for water. */
    static final int VERTICAL = 8;

    /** How far water flows along a trench on the flat from the pond it's cut off. */
    public static final int FLOW = 7;

    public FarmPlan {
        plot = List.copyOf(plot);
        trenches = List.copyOf(trenches);
        chunks = List.copyOf(chunks);
    }

    /** Its chunk farms' water holes. */
    public List<BlockPos> holes() {
        return chunks.stream().flatMap(c -> c.holes().stream()).toList();
    }

    /** A farm round its water and along its trenches, no chunk farm. */
    public FarmPlan(BlockPos water, List<BlockPos> plot, List<BlockPos> trenches) {
        this(water, plot, trenches, List.of());
    }

    /** A farm round its water alone, no trench cut. */
    public FarmPlan(BlockPos water, List<BlockPos> plot) {
        this(water, plot, List.of());
    }

    /** This farm with {@code other}'s ground, trenches and chunk farms taken in, kept by its own water: one farm, tended as one. */
    public FarmPlan with(FarmPlan other) {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>(plot);
        cells.addAll(other.plot());
        LinkedHashSet<BlockPos> cut = new LinkedHashSet<>(trenches);
        cut.addAll(other.trenches());
        LinkedHashSet<ChunkFarm> dug = new LinkedHashSet<>(chunks);
        dug.addAll(other.chunks());
        return new FarmPlan(water, List.copyOf(cells), List.copyOf(cut), List.copyOf(dug));
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
                                          Predicate<BlockPos> walkable) {
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
    public static FarmPlan grown(WorldView world, FarmPlan farm, int want, int vertical, Predicate<BlockPos> walkable) {
        return grown(world, farm, want, vertical, walkable, GROWN_RADIUS);
    }

    /** As {@link #grown(WorldView, FarmPlan, int, int, Predicate)}, the waters looked for out to {@code radius} of its own. */
    public static FarmPlan grown(WorldView world, FarmPlan farm, int want, int vertical, Predicate<BlockPos> walkable, int radius) {
        LinkedHashSet<BlockPos> plot = new LinkedHashSet<>(farm.plot());
        while (plot.size() < want) {
            Set<BlockPos> taken = Set.copyOf(plot);
            Optional<FarmPlan> next = site(world, farm.water(), radius, vertical,
                    stand -> walkable.test(stand) && !taken.contains(stand.offset(0, -1, 0)));
            if (next.isEmpty()) {
                break;
            }
            plot.addAll(next.get().plot());
        }
        // Then the trenches off the ponds, the best first, until it's big enough or none adds MIN_CELLS.
        List<BlockPos> trenches = new ArrayList<>(farm.trenches());
        while (plot.size() < want) {
            Optional<List<BlockPos>> cut = bestTrench(world, farm.water(), radius, plot, trenches, walkable);
            if (cut.isEmpty()) {
                break;
            }
            trenches.addAll(cut.get());
            cut.get().forEach(plot::remove);
            plot.addAll(watered(world, cut.get(), plot, trenches, walkable));
        }
        return plot.size() == farm.plot().size() && trenches.size() == farm.trenches().size() ? farm
                : new FarmPlan(farm.water(), List.copyOf(plot), trenches, farm.chunks());
    }

    /**
     * The trench off a water source at {@code water}'s level within {@code radius} of it that adds the most ground
     * to {@code plot}, less the plot cells it's cut through - at least {@link #MIN_CELLS} - or none.
     */
    static Optional<List<BlockPos>> bestTrench(WorldView world, BlockPos water, int radius, Set<BlockPos> plot,
                                               List<BlockPos> trenches, Predicate<BlockPos> walkable) {
        List<BlockPos> best = null;
        int bestGain = MIN_CELLS - 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos source = water.offset(dx, 0, dz);
                if (!world.isWaterSource(source.x(), source.y(), source.z())) {
                    continue;
                }
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    List<BlockPos> cut = trench(world, source, d[0], d[1], trenches);
                    if (cut.isEmpty()) {
                        continue;
                    }
                    int gain = watered(world, cut, plot, trenches, walkable).size() - (int) cut.stream().filter(plot::contains).count();
                    if (gain > bestGain) {
                        best = cut;
                        bestGain = gain;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The trench off {@code source} heading {@code (dx, dz)}: the run of ground level with it - tillable, tilled, or
     * a trench's water already - with ground under each cell and beside it to hold the water, out to {@link #FLOW}
     * and ending against ground; empty if there's none, or it would meet one of {@code trenches}.
     */
    static List<BlockPos> trench(WorldView world, BlockPos source, int dx, int dz, List<BlockPos> trenches) {
        List<BlockPos> cut = new ArrayList<>();
        for (int k = 1; k <= FLOW; k++) {
            BlockPos c = source.offset(dx * k, 0, dz * k);
            boolean ground = world.isTillable(c.x(), c.y(), c.z()) || world.isFarmland(c.x(), c.y(), c.z());
            boolean dug = world.isWater(c.x(), c.y(), c.z()) && !world.isWaterSource(c.x(), c.y(), c.z());
            if (!ground && !dug || trenches.contains(c) || !world.isSolid(c.x(), c.y() - 1, c.z())
                    || ground && (world.isSolid(c.x(), c.y() + 1, c.z()) || world.isHazard(c.x(), c.y() + 1, c.z()))
                    || !holds(world, c.offset(dz, 0, dx)) || !holds(world, c.offset(-dz, 0, -dx))) {
                break;
            }
            cut.add(c);
        }
        // Water spills out of an end standing open onto lower ground: cut back to one that ends against ground.
        while (!cut.isEmpty() && cut.size() < FLOW && !holds(world, cut.getLast().offset(dx, 0, dz))) {
            cut.removeLast();
        }
        return cut;
    }

    /** Whether the cell beside a trench keeps its water in: ground, or water already. */
    private static boolean holds(WorldView world, BlockPos c) {
        return world.isSolid(c.x(), c.y(), c.z()) && !world.isWater(c.x(), c.y(), c.z()) || world.isWater(c.x(), c.y(), c.z());
    }

    /** The ground {@code cut} hydrates that isn't in {@code plot} or a trench yet, standing on cells {@code walkable} allows. */
    private static List<BlockPos> watered(WorldView world, List<BlockPos> cut, Set<BlockPos> plot, List<BlockPos> trenches,
                                          Predicate<BlockPos> walkable) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos c : cut) {
            plotAround(world, c).stream().filter(p -> !plot.contains(p) && !cut.contains(p) && !trenches.contains(p)
                    && walkable.test(p.offset(0, 1, 0))).forEach(cells::add);
        }
        return List.copyOf(cells);
    }

    /** The cells round {@code water} that make farmland: tillable or tilled, level with it, open above. */
    public static List<BlockPos> plotAround(WorldView world, BlockPos water) {
        List<BlockPos> plot = new ArrayList<>();
        for (int dx = -HYDRATION; dx <= HYDRATION; dx++) {
            for (int dz = -HYDRATION; dz <= HYDRATION; dz++) {
                BlockPos cell = water.offset(dx, 0, dz);
                boolean ground = (world.isTillable(cell.x(), cell.y(), cell.z()) || world.isFarmland(cell.x(), cell.y(), cell.z()))
                        && !world.isWater(cell.x(), cell.y(), cell.z());
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
