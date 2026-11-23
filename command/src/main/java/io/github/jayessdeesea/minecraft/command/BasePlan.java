package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The base the platoon builds, as a plan (operator, 2026-11-23: an overall strategy of expanding influence - a large
 * permanent structure over x=-64..64, z=-64..64 at sea level, a template at each of its chunks; never over-extended,
 * so the renewables go in as the work goes): a square of chunks, {@code minChunk} to {@code maxChunk} each way, each
 * with the template it is to carry, at {@code level}.
 *
 * <p>It is built a cluster at a time (operator, 2026-11-23: a crew builds a mineshaft and then works it; a second squad
 * a supply dump next to it, so nobody walks far; then a wheat farm nearby, then a tree farm; then another mineshaft two
 * chunks away - the centre of mass shifting to the origin a step at a time, never the whole square flattened first). A
 * cluster is two chunks by two: its mineshaft where chunk x and z are both even, the supply point east of it, the tree
 * farm south of it, the wheat farm across the corner. The chunk the plan is centred on is the camp's, and stays as it
 * is. Pure: what's taken is handed in.
 */
public record BasePlan(int minChunk, int maxChunk, int level) {

    /** The base round the world's origin: chunks -4..3 each way - x and z -64..63 - at sea level. */
    public static final BasePlan ROUND_THE_ORIGIN = new BasePlan(-4, 3, 63);

    /** A chunk as one number, for a set of them. */
    public static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xffffffffL;
    }

    /** The template chunk ({@code chunkX}, {@code chunkZ}) is to carry: none outside the base, or on the camp's own chunk. */
    public Optional<FarmMission.Crop> template(int chunkX, int chunkZ) {
        if (chunkX < minChunk || chunkX > maxChunk || chunkZ < minChunk || chunkZ > maxChunk || chunkX == 0 && chunkZ == 0) {
            return Optional.empty();
        }
        boolean xEven = Math.floorMod(chunkX, 2) == 0, zEven = Math.floorMod(chunkZ, 2) == 0;
        return Optional.of(xEven && zEven ? FarmMission.Crop.MINE : zEven ? FarmMission.Crop.STORES : xEven ? FarmMission.Crop.TREES : FarmMission.Crop.WHEAT);
    }

    /** The mission the plan has for chunk ({@code chunkX}, {@code chunkZ}), if it has one. */
    private Optional<FarmMission> mission(int chunkX, int chunkZ) {
        return template(chunkX, chunkZ).map(crop -> new FarmMission(chunkX, chunkZ, level, crop));
    }

    /**
     * A cluster's missions in the order they're built, by the chunk its mineshaft is on ({@code x}, {@code z}, both even):
     * the mineshaft, the supply point beside it, the wheat farm, the tree farm.
     */
    public List<FarmMission> cluster(int x, int z) {
        List<FarmMission> order = new ArrayList<>();
        for (int[] at : new int[][]{{x, z}, {x + 1, z}, {x + 1, z + 1}, {x, z + 1}}) {
            mission(at[0], at[1]).ifPresent(order::add);
        }
        return order;
    }

    /** The clusters, each by its mineshaft's chunk: the nearest the origin first, then by chunk x, then z. */
    private List<int[]> clusters() {
        List<int[]> all = new ArrayList<>();
        for (int x = minChunk - Math.floorMod(minChunk, 2); x <= maxChunk; x += 2) {
            for (int z = minChunk - Math.floorMod(minChunk, 2); z <= maxChunk; z += 2) {
                all.add(new int[]{x, z});
            }
        }
        // A cluster's middle is the corner its four chunks share: (x + 1, z + 1) in chunks.
        all.sort(Comparator.<int[]>comparingInt(c -> (c[0] + 1) * (c[0] + 1) + (c[1] + 1) * (c[1] + 1)).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        return all;
    }

    /** Every mission of the plan, cluster by cluster. */
    public List<FarmMission> missions() {
        return clusters().stream().flatMap(c -> cluster(c[0], c[1]).stream()).toList();
    }

    /**
     * The mission to give next, the chunks {@code taken} - by {@link #key} - each having a mission already, open or
     * built. A cluster begun is finished before another is: of those with a chunk taken and one not, the nearest the
     * origin, its next mission in its order. With none begun and unfinished, the cluster beside one begun that's nearest
     * the origin - the base grows from where it's being built, toward the middle and then on; with nothing built at all,
     * the cluster nearest the origin. None when every chunk is taken.
     */
    public Optional<FarmMission> next(Set<Long> taken) {
        return next(taken, Set.of(FarmMission.Crop.WHEAT, FarmMission.Crop.TREES), Set.of(FarmMission.Crop.WHEAT, FarmMission.Crop.TREES));
    }

    /**
     * As {@link #next(Set)}, food and wood first (operator, 2026-11-23: start with something easy - a reliable food
     * source, a reliable wood source - and use it to bootstrap the rest; where to expand, where to hold, is the
     * experiment). {@code had} is the kinds with a mission, open or built; {@code built}, those with one built. With no
     * wheat farm had, the next is a wheat farm; with no tree farm, a tree farm - each where the cluster order would put
     * it. Until one of each is built nothing else is begun: none. Then the clusters, as {@link #next(Set)}.
     */
    public Optional<FarmMission> next(Set<Long> taken, Set<FarmMission.Crop> had, Set<FarmMission.Crop> built) {
        for (FarmMission.Crop staple : List.of(FarmMission.Crop.WHEAT, FarmMission.Crop.TREES)) {
            if (!had.contains(staple)) {
                Set<Long> butThatKind = new java.util.HashSet<>(taken);
                missions().stream().filter(m -> m.crop() != staple).forEach(m -> butThatKind.add(key(m.chunkX(), m.chunkZ())));
                return inClusters(butThatKind, taken);
            }
        }
        if (!built.contains(FarmMission.Crop.WHEAT) || !built.contains(FarmMission.Crop.TREES)) {
            return Optional.empty();
        }
        return inClusters(taken, taken);
    }

    /** The next mission by the clusters' order among the chunks not {@code closed}, a cluster begun being one with a chunk {@code taken}. */
    private Optional<FarmMission> inClusters(Set<Long> closed, Set<Long> taken) {
        List<int[]> clusters = clusters();
        List<int[]> begun = clusters.stream().filter(c -> cluster(c[0], c[1]).stream().anyMatch(m -> taken.contains(key(m.chunkX(), m.chunkZ())))).toList();
        List<int[]> from = begun.stream().filter(c -> left(c, closed).isPresent()).toList();
        if (from.isEmpty()) {
            // Beside one begun; of those as near the origin as each other, the one nearest what's built.
            double midX = begun.stream().mapToInt(b -> b[0]).average().orElse(0), midZ = begun.stream().mapToInt(b -> b[1]).average().orElse(0);
            from = clusters.stream().filter(c -> left(c, closed).isPresent() && (begun.isEmpty()
                            || begun.stream().anyMatch(b -> Math.abs(b[0] - c[0]) <= 2 && Math.abs(b[1] - c[1]) <= 2)))
                    .sorted(Comparator.<int[]>comparingInt(c -> (c[0] + 1) * (c[0] + 1) + (c[1] + 1) * (c[1] + 1))
                            .thenComparingDouble(c -> begun.isEmpty() ? 0 : (c[0] - midX) * (c[0] - midX) + (c[1] - midZ) * (c[1] - midZ))).toList();
        }
        if (from.isEmpty()) { // every cluster beside one begun is built: the nearest left, wherever it is
            from = clusters.stream().filter(c -> left(c, closed).isPresent()).toList();
        }
        return from.stream().findFirst().flatMap(c -> left(c, closed));
    }

    /** The first mission of the cluster at {@code c}, in its order, whose chunk isn't {@code taken}. */
    private Optional<FarmMission> left(int[] c, Set<Long> taken) {
        return cluster(c[0], c[1]).stream().filter(m -> !taken.contains(key(m.chunkX(), m.chunkZ()))).findFirst();
    }
}
