package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One soldier filling its camp's hollows ({@code bot.engineer.Clearing.hollows}): cobblestone or
 * dirt into each open cell below the camp's ground, lowest first, nearest first among the lowest -
 * a {@link Builder} that stands where it reaches each and skips one it can't reach.
 * Out of blocks, it stops: a mason's dirt dig is the soldier's to make.
 */
public final class Filler {

    private static final Logger LOG = LogManager.getLogger(Filler.class);

    /** What a hollow is filled with: never planks or logs, which are tools and torches. */
    static final List<ItemClass> MATERIALS = List.of(ItemClass.COBBLESTONE, ItemClass.DIRT);

    private final String name;
    private final Inventory inventory;
    private final Builder builder;
    private boolean active;
    private String failure;
    /** Ticks to wait after a fill that couldn't go on, before trying again. */
    static final int COOLDOWN_TICKS = 200;
    private int cooldown;

    public Filler(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.inventory = inventory;
        this.builder = new Builder(name, session, inventory, MATERIALS);
    }

    public synchronized boolean active() {
        return active;
    }

    public synchronized Optional<Position> standAt() {
        return active ? builder.standAt() : Optional.empty();
    }

    public synchronized Optional<Position> lookAt() {
        return active ? builder.lookAt() : Optional.empty();
    }

    /** Why the last fill stopped, if it couldn't go on. */
    public synchronized Optional<String> failure() {
        return Optional.ofNullable(failure);
    }

    /** Each tick: fills {@code hollows} while {@code free} and carrying blocks; anything else stops it. */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> hollows, boolean free) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!free || cooldown > 0 || hollows.isEmpty() || MATERIALS.stream().noneMatch(m -> inventory.count(m) > 0)) {
            stop();
            return;
        }
        // The lowest first, nearest first among them; one out of reach - over water nobody can stand
        // in, deep below the rim - is the builder's to skip for the next. A block put in high can
        // seal a pocket of air or water under it: underground and harmless, and a flooded shaft is
        // capped that way from its rim, as live one had to be with soldiers afloat in it.
        List<BlockPos> plan = hollows.stream()
                .sorted(Comparator.comparingInt(BlockPos::y)
                        .thenComparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())))
                .toList();
        active = true;
        switch (builder.tick(soldier, world, plan)) {
            case Builder.Progress.Working _, Builder.Progress.Done _ -> failure = null;
            case Builder.Progress.Failed(String why) -> {
                if (!why.equals(failure)) {
                    LOG.info("{} stops filling for now - {}", name, why);
                }
                failure = why;
                builder.retrySkipped();
                cooldown = COOLDOWN_TICKS;
                stop();
            }
        }
    }

    public synchronized void stop() {
        if (active) {
            builder.stop();
        }
        active = false;
    }
}
