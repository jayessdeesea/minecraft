package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The camp's farm (docs/military-doctrine.md, Class I): sited once on ground by water near the camp and kept while
 * its water is there - on disk too, since sited again after a restart it moved to another pond - grown once a run to
 * the force's size, round the waters beside its own and along trenches off them; and a chunk farm beside the camp
 * taken in (operator, 2026-11-19), from the first (operator, 2026-11-20).
 */
final class CampFarm {

    private static final Logger LOG = LogManager.getLogger(CampFarm.class);

    /** How far round the camp its farm is looked for: the water it needs can be a way off. */
    static final int RADIUS = 48;
    /** And how far up or down: a camp on a hill farms by the water below it. */
    static final int VERTICAL = 32;
    /**
     * The cells the camp's farm is grown to for each soldier on the roster: enough to feed the force and build its
     * basic load, not only its meals. Hydrated wheat ripens in half an hour or so, three wheat a loaf: live, 97 cells
     * made about 20 loaves an hour, as many as eight soldiers ate. Live (field369), 192 cells - eight soldiers' worth -
     * left a force of sixteen with 12 at no bread: the farm is the force's size, not a fixed one.
     */
    static final int CELLS_A_SOLDIER = 24;
    /** The least the camp's farm is grown to, whatever the force: eight soldiers' worth, a pond or two. */
    static final int CELLS = 192;
    /**
     * How far round its own water it takes in more waters' plots as it grows: live (field371), the ground within 16
     * held 165 cells, the target 384, and the farm didn't grow.
     */
    static final int GROWN_RADIUS = 32;
    /** How many chunks round the camp's own its chunk farm is looked for. */
    static final int CHUNK_RINGS = 2;

    private final WallStore store;
    private final int reachBudget;
    private volatile FarmPlan farm;
    /** Whether it's been grown this run, as far as the ground allows. */
    private boolean grown;
    /** Whether its chunk farm has been looked for this run. */
    private boolean chunked;

    CampFarm(WallStore store, int reachBudget) {
        this.store = store;
        this.reachBudget = reachBudget;
    }

    /** Its chunk farms' chests: each a farm's own, its seed apart from the general stockpile's. */
    Set<BlockPos> chests() {
        FarmPlan kept = farm;
        return kept == null ? Set.of() : kept.chunks().stream().map(ChunkFarm::chest).collect(java.util.stream.Collectors.toSet());
    }

    /** The farm as last kept, if any. */
    FarmPlan farm() {
        return farm;
    }

    /** The cells it's grown to for {@code soldiers}. */
    static int cells(int soldiers) {
        return Math.max(CELLS, CELLS_A_SOLDIER * soldiers);
    }

    /**
     * The camp's farm for the camp at {@code at}, kept under {@code key}: as it was, sited afresh if its water's gone,
     * grown once a run toward {@link #cells} for {@code soldiers}, and with {@code bucket} its chunk farm taken in.
     */
    synchronized FarmPlan kept(WorldView world, BlockPos at, String key, int soldiers, boolean bucket) {
        FarmPlan kept = farm;
        if (kept == null) {
            // As it was before a restart, while its water is there.
            kept = store.load(key).filter(cells -> cells.size() > 1).map(cells -> new FarmPlan(cells.getFirst(), cells.subList(1, cells.size()),
                    store.load(key + ",trench").orElse(List.of()), store.load(key + ",chunks").orElse(List.of()).stream()
                    .map(c -> new ChunkFarm(c.x(), c.z(), c.y())).toList())).orElse(null);
        }
        boolean resite = kept == null || !world.isWater(kept.water().x(), kept.water().y(), kept.water().z());
        boolean chunk = bucket && !chunked && kept != null && kept.chunks().isEmpty();
        if (resite || !grown || chunk) {
            Set<BlockPos> walkable = Pathfinder.reachable(world, at, RADIUS + 4, reachBudget);
            FarmPlan before = resite ? null : kept;
            if (resite) {
                kept = FarmPlan.site(world, at, RADIUS, VERTICAL, walkable::contains).orElse(null);
            }
            if (kept != null && !grown) {
                kept = FarmPlan.grown(world, kept, cells(soldiers), VERTICAL, walkable::contains, GROWN_RADIUS);
                grown = true;
            }
            if (kept != null && bucket && kept.chunks().isEmpty()) {
                chunked = true;
                FarmPlan watered = kept;
                Optional<ChunkFarm> site = ChunkFarm.site(world, at, CHUNK_RINGS, walkable::contains, Set.of());
                site.ifPresent(c -> LOG.info("[Foothold] the camp's chunk farm: chunk {}, {} at y={}, {} cells", c.chunkX(), c.chunkZ(), c.level(),
                        c.plan(world, walkable::contains).plot().size()));
                kept = site.map(c -> watered.with(c.plan(world, walkable::contains))).orElse(kept);
            }
            if (kept != null && !kept.equals(before)) {
                LOG.info("[Foothold] the camp's farm: by the water at {}, {} cells, {} trench cells, {} chunk farms", kept.water(),
                        kept.plot().size(), kept.trenches().size(), kept.chunks().size());
                store.save(key, Stream.concat(Stream.of(kept.water()), kept.plot().stream()).toList());
                store.save(key + ",trench", kept.trenches());
                // Each chunk farm as its chunk and level: (chunk x, level, chunk z).
                store.save(key + ",chunks", kept.chunks().stream().map(c -> new BlockPos(c.chunkX(), c.level(), c.chunkZ())).toList());
            }
        }
        // Kept however it was found - sited now, or as it was before a restart: live, a farm loaded from disk was
        // never kept, and both squads starved under SECURE with a farm by the camp.
        farm = kept;
        return kept;
    }
}
