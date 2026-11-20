package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Farmer;
import io.github.jayessdeesea.minecraft.bot.supply.Forage;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The soldiers foraging the land for food (bot.supply.Forage), on their squad's FORAGE order (operator, 2026-11-20: a
 * soldier with no food complains to its squad leader, the squad leader to the platoon leader, and the platoon leader
 * has the squad forage - but its soldiers told to keep the farms): the camp's ripe wheat, looked over now and then
 * and its count told to the platoon leader, and each forager's plot of it - kept till it's worked, the seeds sown back,
 * while its forager is under its basic load; never a squadmate's cells. Live (field497-499), the stores at 0 and the
 * haul to the hilltop failing, the hungry stood with the fields round them ripe.
 */
final class Foragers {

    private static final Logger LOG = LogManager.getLogger(Foragers.class);

    /** How often the camp's ground is looked over for ripe wheat. */
    static final long LOOK_MILLIS = 30_000;

    /** Ripe wheat round the camp, and when it was looked over - squad loops write, ticks read. */
    private volatile List<BlockPos> ripe = List.of();
    private volatile long lookedAt = -LOOK_MILLIS;
    /** Each forager's plot - tick loops, each its own key. */
    private final Map<String, FarmPlan> plots = new ConcurrentHashMap<>();

    /** The camp's ground's ripe wheat looked over, every {@link #LOOK_MILLIS} once its ground is known: how much, for the platoon leader. */
    void lookOver(WorldView world, BlockPos campGround, long now, java.util.function.IntConsumer told) {
        if (campGround != null && now - lookedAt >= LOOK_MILLIS) {
            lookedAt = now;
            ripe = List.copyOf(Forage.ripe(world, campGround, Forage.REACH, Forage.DEPTH));
            told.accept(ripe.size());
        }
    }

    /**
     * {@code soldier}'s plot while it's {@code foraging} - its squad's order, under its basic load: its own while it has
     * work, else the ripe wheat nearest {@code at} no squadmate's plot holds; else none. A change is said.
     */
    Optional<FarmPlan> plotFor(String soldier, WorldView world, Position at, boolean foraging) {
        Set<BlockPos> others = plots.entrySet().stream().filter(e -> !e.getKey().equals(soldier)).flatMap(e -> e.getValue().plot().stream())
                .collect(Collectors.toSet());
        Optional<FarmPlan> plot = !foraging ? Optional.empty() : Optional.ofNullable(plots.get(soldier)).filter(f -> Farmer.hasWork(world, f, Set.of()))
                .or(() -> Forage.plot(ripe, at, others));
        if (plot.isEmpty() ? plots.remove(soldier) != null : !plot.get().equals(plots.put(soldier, plot.get()))) {
            LOG.info("{} {}", soldier, plot.map(p -> "forages: " + p.plot().size() + " ripe wheat from " + p.water()).orElse("stops foraging"));
        }
        return plot;
    }
}
