package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.FlattenPlan;
import io.github.jayessdeesea.minecraft.command.Keep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
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

    private static final Logger LOG = LogManager.getLogger(SiteClears.class);

    /** How long a site's dig, as scanned, serves every squad's cycle before it's scanned again. */
    static final long SITE_CLEAR_MILLIS = 1_000;

    private final WorldView world;
    private final LongSupplier clock;
    /** The site's dig as last scanned, by what was kept out of it: its ground, its columns, when, the cells. */
    private final Map<Set<Cell>, Object[]> scans = new LinkedHashMap<>();
    /** The blocks of no known break time in the site's dig as last scanned ({@code Clearing.unknownIn}): left, and said up. */
    private volatile List<BlockPos> unknown = List.of();
    /** The water in the site's dig as last scanned ({@code Clearing.waterIn}): to fill with dirt, then dig as ground. */
    private volatile List<BlockPos> water = List.of();
    /** Those alerted, once each. */
    private final Set<BlockPos> alerted = new HashSet<>();

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
        List<Box> boxes = new ArrayList<>();
        if (reach.isPresent()) {
            for (FlattenPlan.Column c : reach.get()) {
                if (c.to() < Keep.SKY_TOP) {
                    boxes.add(new Box(c.x(), c.to() + 1, c.z(), c.x(), Keep.SKY_TOP, c.z()));
                }
            }
        } else {
            // The site and its benched edge (clearing rule 6): each row of the ring round it, d out, dug down
            // to d over the floor, so the edge is a walkable slope - live, soldiers put steps in the edge
            // column to climb the hillside the site was cut into, each one a level of its own.
            boxes.add(ground.dig());
            for (int d = 1; d <= Flatten.BENCH; d++) {
                boxes.addAll(ground.bench(d));
            }
        }
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> unknowns = new ArrayList<>();
        List<BlockPos> waters = new ArrayList<>();
        for (Box box : boxes) {
            cells.addAll(Clearing.toClear(world, box, except));
            unknowns.addAll(Clearing.unknownIn(world, box, except));
            waters.addAll(Clearing.waterIn(world, box, except));
        }
        unknown = List.copyOf(unknowns);
        if (!waters.equals(water)) {
            LOG.info("[Keep] water in the dig: {} cell(s){} - filled with dirt, then dug", waters.size(), waters.stream().limit(4).map(c -> " " + c.x() + "," + c.y() + "," + c.z()).reduce("", String::concat));
        }
        water = List.copyOf(waters);
        synchronized (alerted) {
            // Said once each, by name, so the block tables are put right from the log (operator, 2026-11-22).
            unknowns.stream().filter(alerted::add).forEach(c -> LOG.warn("[Alert] unknown block {} at {} in the dig: no break time known - left, "
                    + "and said up; the block tables want it", world.describeAt(c.x(), c.y(), c.z()), c));
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

    /** The water in the dig as last scanned: filled first, then dug with the ground. */
    List<BlockPos> water() {
        return water;
    }

    /** The blocks of no known break time in the dig as last scanned: in the clear's way, left for orders. */
    List<BlockPos> unknown() {
        return unknown;
    }
}
