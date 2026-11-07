package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * One soldier cutting its squad's {@link Staircase} down: from the step above the first not yet
 * clear, it breaks that step's cells ({@link Breacher}), then walks down onto it and cuts the next.
 * Its state is the world's - a step is cut when its cells are open - so a run stopped anywhere, by a
 * fight or a restart, picks up where the stairs end.
 *
 * <p>Ore it can see from where it stands - beside its body or the step it cuts, outside the shaft's
 * 3x3, where taking it holes no step and no pillar - it mines first, when its pickaxe gets the drop
 * and nothing liquid lies behind it. That's how the stairs pay in iron and diamonds, not only stone.
 *
 * <p>It stops, loudly, where going on would be a fall or a flood: water or lava beside a cell it
 * would open, or no floor under a step - an open cave. Those are a squad leader's to route round,
 * not a soldier's to guess past.
 */
public final class Miner {

    private static final Logger LOG = LogManager.getLogger(Miner.class);

    /** How far off the standing cell's centre still counts as standing on it. */
    private static final double ON_CELL = 0.6;

    /** What the stairs came to this tick. */
    public sealed interface Progress {
        /** Cutting step {@code step} of {@code steps}, or walking down to it. */
        record Working(int step, int steps) implements Progress {
        }

        /** Every step cut: the stairs reach the bottom. */
        record Done() implements Progress {
        }

        /** Can't go on: {@code why}. */
        record Stopped(String why) implements Progress {
        }
    }

    /** Ticks spent on one ore before it's left: one the server won't break isn't worth the stairs. */
    static final int ORE_PATIENCE_TICKS = 600;

    private final String name;
    private final Inventory inventory;
    private final Breacher breacher;
    private final java.util.Set<BlockPos> leftOre = new java.util.HashSet<>();
    private BlockPos ore;
    private int oreTicks;
    private Position standAt;
    private int lastStep = -1;

    public Miner(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.inventory = inventory;
        this.breacher = new Breacher(name, session, inventory);
    }

    /** Where the soldier should stand: on the step above the one it cuts. */
    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    /** The first step of {@code stairs} not yet cut, or empty when all are. */
    public static java.util.OptionalInt firstUncut(WorldView world, Staircase stairs) {
        for (int k = 0; k < stairs.steps(); k++) {
            if (stairs.cells(k).stream().anyMatch(c -> world.isSolid(c.x(), c.y(), c.z()))) {
                return java.util.OptionalInt.of(k);
            }
        }
        return java.util.OptionalInt.empty();
    }

    /** Each tick: cuts {@code stairs} while {@code free}; anything else stops the dig under way. */
    public synchronized Progress tick(Position soldier, WorldView world, Staircase stairs, boolean free) {
        java.util.OptionalInt next = firstUncut(world, stairs);
        if (next.isEmpty()) {
            stop();
            return new Progress.Done();
        }
        int k = next.getAsInt();
        Optional<String> hazard = hazard(world, stairs, k);
        if (hazard.isPresent()) {
            stop();
            return new Progress.Stopped(hazard.get());
        }
        BlockPos from = stairs.above(k);
        standAt = new Position(from.x() + 0.5, from.y(), from.z() + 0.5);
        if (k != lastStep) {
            LOG.info("{} cuts step {} of {} down at {}", name, k + 1, stairs.steps(), stairs.step(k));
            lastStep = k;
        }
        boolean on = Math.hypot(soldier.x() - standAt.x(), soldier.z() - standAt.z()) < ON_CELL && Math.abs(soldier.y() - from.y()) < ON_CELL;
        if (!free || !on) {
            breacher.stop();
            return new Progress.Working(k, stairs.steps());
        }
        Optional<BlockPos> seen = oreInSight(world, stairs, from, k);
        if (seen.isPresent()) {
            if (!seen.get().equals(ore)) {
                ore = seen.get();
                oreTicks = 0;
                LOG.info("{} mines the ore at {} beside the stairs", name, ore);
            }
            if (++oreTicks > ORE_PATIENCE_TICKS) {
                LOG.warn("{} leaves the ore at {}: not broken in {} ticks", name, ore, ORE_PATIENCE_TICKS);
                leftOre.add(ore);
                breacher.stop();
            } else {
                breacher.tick(world, List.of(ore));
            }
            return new Progress.Working(k, stairs.steps());
        }
        breacher.tick(world, stairs.cells(k));
        return new Progress.Working(k, stairs.steps());
    }

    /**
     * Ore beside the body standing on {@code from}, or beside step {@code k}'s cells: outside the
     * shaft's 3x3, not left before, its drop got with the pickaxe carried, nothing liquid beside it.
     */
    Optional<BlockPos> oreInSight(WorldView world, Staircase stairs, BlockPos from, int k) {
        int tier = bestPickaxe();
        List<BlockPos> near = new java.util.ArrayList<>(List.of(from, from.offset(0, 1, 0)));
        near.addAll(stairs.cells(k));
        for (BlockPos cell : near) {
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos at = cell.offset(d[0], d[1], d[2]);
                boolean outside = Math.abs(at.x() - stairs.head().x()) > 1 || Math.abs(at.z() - stairs.head().z()) > 1;
                if (outside && !leftOre.contains(at) && world.isOre(at.x(), at.y(), at.z())
                        && world.hardness(at.x(), at.y(), at.z()).map(h -> h.drops(tier)).orElse(false)
                        && !besideLiquid(world, at)) {
                    return Optional.of(at);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean besideLiquid(WorldView world, BlockPos cell) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    /** The best pickaxe's mining tier carried, or {@link io.github.jayessdeesea.minecraft.bot.world.Hardness#HAND} with none. */
    private int bestPickaxe() {
        int best = io.github.jayessdeesea.minecraft.bot.world.Hardness.HAND;
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            java.util.OptionalInt tier = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.miningTier(inventory.itemAt(slot));
            if (tier.isPresent()) {
                best = Math.max(best, tier.getAsInt());
            }
        }
        return best;
    }

    /** Why cutting step {@code k} would let in water or lava, or leave a body nothing to stand on. */
    static Optional<String> hazard(WorldView world, Staircase stairs, int k) {
        List<BlockPos> cells = stairs.cells(k);
        for (BlockPos cell : cells) {
            for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                    return Optional.of("water or lava beside step " + (k + 1) + " at " + cell.offset(d[0], d[1], d[2]));
                }
            }
        }
        BlockPos floor = stairs.step(k).offset(0, -1, 0);
        if (!world.isSolid(floor.x(), floor.y(), floor.z())) {
            return Optional.of("no floor under step " + (k + 1) + " at " + floor + " - an open cave below");
        }
        return Optional.empty();
    }

    /** Stops the dig under way. */
    public synchronized void stop() {
        breacher.stop();
        standAt = null;
    }
}
