package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.FlattenPlan;
import io.github.jayessdeesea.minecraft.command.Keep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The ground to flatten's dig as scanned ({@code Clearing.toClear}), once a {@link #SITE_CLEAR_MILLIS} for every squad's
 * cycle. Commit 496's profile: four squads each scanned the big site - 33 by 129 columns from the dig's bottom to the top
 * of the world, a million cells - twice a cycle, and it was most of the garrison's CPU.
 */
final class SiteClears {

    /** How long a site's dig, as scanned, serves every squad's cycle before it's scanned again. */
    static final long SITE_CLEAR_MILLIS = 1_000;

    private final WorldView world;
    private final LongSupplier clock;
    /** The site's dig as last scanned, by what was kept out of it: its ground, its columns, when, the cells. */
    private final Map<Set<Cell>, Object[]> scans = new LinkedHashMap<>();

    SiteClears(WorldView world, LongSupplier clock) {
        this.world = world;
        this.clock = clock;
    }

    /**
     * What's to dig on {@code ground} with {@code except} kept out: its box and its benched edge - or, a farm mission's
     * flatten, the columns of its {@link FlattenPlan#reach}, each over its highest top (operator, 2026-11-21: a chunk's
     * slopes a block a block out on each side sloped, never into a template).
     */
    List<BlockPos> cells(Flatten ground, Set<Cell> except, Optional<List<FlattenPlan.Column>> reach) {
        long now = clock.getAsLong();
        synchronized (scans) {
            Object[] seen = scans.get(except);
            if (seen != null && seen[0].equals(ground) && seen[1].equals(reach) && now - (long) seen[2] < SITE_CLEAR_MILLIS) {
                @SuppressWarnings("unchecked")
                List<BlockPos> cells = (List<BlockPos>) seen[3];
                return cells;
            }
        }
        List<BlockPos> cells = new ArrayList<>();
        if (reach.isPresent()) {
            for (FlattenPlan.Column c : reach.get()) {
                if (c.to() < Keep.SKY_TOP) {
                    cells.addAll(Clearing.toClear(world, new Box(c.x(), c.to() + 1, c.z(), c.x(), Keep.SKY_TOP, c.z()), except));
                }
            }
        } else {
            // The site and its benched edge (clearing rule 6): each row of the ring round it, d out, dug down
            // to d over the floor, so the edge is a walkable slope - live, soldiers put steps in the edge
            // column to climb the hillside the site was cut into, each one a level of its own.
            cells.addAll(Clearing.toClear(world, ground.dig(), except));
            for (int d = 1; d <= Flatten.BENCH; d++) {
                for (Box row : ground.bench(d)) {
                    cells.addAll(Clearing.toClear(world, row, except));
                }
            }
        }
        List<BlockPos> scanned = List.copyOf(cells);
        synchronized (scans) {
            scans.put(Set.copyOf(except), new Object[]{ground, reach, now, scanned});
            // Only the last few kept: one for each squad's two scans.
            while (scans.size() > 8) {
                scans.remove(scans.keySet().iterator().next());
            }
        }
        return scanned;
    }
}
