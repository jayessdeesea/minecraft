package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Set<BlockPos> leftOre = new HashSet<>();
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
    public static OptionalInt firstUncut(WorldView world, Staircase stairs) {
        return firstUncut(world, stairs.cuts(), Set.of());
    }

    /**
     * The first of {@code cuts} still to cut, skipping any in a {@code blocked} tunnel and any whose
     * standing cell isn't open yet - a branch off a stretch of main tunnel that was given up.
     */
    static OptionalInt firstUncut(WorldView world, List<Staircase.Cut> cuts, Set<Integer> blocked) {
        for (int k = 0; k < cuts.size(); k++) {
            Staircase.Cut cut = cuts.get(k);
            if (blocked.contains(cut.tunnel()) || cut.cells().stream().noneMatch(c -> world.isSolid(c.x(), c.y(), c.z()) && world.workstation(c.x(), c.y(), c.z()).isEmpty())) {
                continue;
            }
            BlockPos stand = cut.stand();
            if (cut.tunnel() >= 0 && (world.isSolid(stand.x(), stand.y(), stand.z()) || world.isSolid(stand.x(), stand.y() + 1, stand.z()))) {
                continue;
            }
            return OptionalInt.of(k);
        }
        return OptionalInt.empty();
    }

    /** How far above the stairs' bottom a cutter still works a mine cut to the bottom for its ore. */
    static final int PROSPECT_FROM = 8;

    /** Tunnels given up for water, lava or a cave beside them. */
    private final Set<Integer> blocked = new HashSet<>();

    /** Each tick: cuts {@code stairs} while {@code free}; anything else stops the dig under way. */
    public synchronized Progress tick(Position soldier, WorldView world, Staircase stairs, boolean free) {
        List<Staircase.Cut> cuts = stairs.cuts();
        OptionalInt next;
        Staircase.Cut cut;
        while (true) {
            next = firstUncut(world, cuts, blocked);
            if (next.isEmpty()) {
                // All cut: worked again for the ore in sight of what was cut - passed by, or not
                // harvestable then. Live, no miner mined ore in twelve runs: the tunnels were cut,
                // and nobody went back down them.
                // Only from down there - at the landing for the night: from the top, the mine reads
                // done and the squad holds the top. Live, working it from the top kept the squad's
                // task open, and its soldiers were ordered to the bottom 54 blocks down by a route
                // that failed: an hour and more of "no route" CANTCO a day.
                next = soldier.y() <= stairs.bottom() + PROSPECT_FROM ? prospect(world, stairs, cuts) : OptionalInt.empty();
                if (next.isEmpty()) {
                    stop();
                    return new Progress.Done();
                }
                cut = cuts.get(next.getAsInt());
                break;
            }
            cut = cuts.get(next.getAsInt());
            Optional<String> hazard = hazard(world, cut, next.getAsInt());
            if (hazard.isEmpty()) {
                break;
            }
            if (cut.tunnel() < 0) {
                stop();
                return new Progress.Stopped(hazard.get());
            }
            // A tunnel is one of many: give it up, and go on with the next.
            LOG.warn("{} gives up tunnel {} of the mine: {}", name, cut.tunnel(), hazard.get());
            blocked.add(cut.tunnel());
        }
        int k = next.getAsInt();
        BlockPos from = cut.stand();
        standAt = new Position(from.x() + 0.5, from.y(), from.z() + 0.5);
        if (k != lastStep) {
            if (cut.tunnel() < 0) {
                LOG.info("{} cuts step {} of {} down at {}", name, k + 1, stairs.steps(), cut.feet());
            } else if (lastStep < 0 || cuts.get(lastStep).tunnel() != cut.tunnel()) {
                LOG.info("{} cuts {} of the mine from {}", name, cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel(), cut.feet());
            }
            lastStep = k;
        }
        boolean on = Math.hypot(soldier.x() - standAt.x(), soldier.z() - standAt.z()) < ON_CELL && Math.abs(soldier.y() - from.y()) < ON_CELL;
        if (!free || !on) {
            breacher.stop();
            return new Progress.Working(k, cuts.size());
        }
        Optional<BlockPos> seen = oreInSight(world, stairs, from, cut);
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
            return new Progress.Working(k, cuts.size());
        }
        breacher.tick(world, cut.cells());
        return new Progress.Working(k, cuts.size());
    }

    /** The first cut, all open, with ore in sight of its stand ({@link #oreInSight}) - empty when none has. */
    private OptionalInt prospect(WorldView world, Staircase stairs, List<Staircase.Cut> cuts) {
        for (int k = 0; k < cuts.size(); k++) {
            Staircase.Cut cut = cuts.get(k);
            if (blocked.contains(cut.tunnel())
                    || cut.cells().stream().anyMatch(c -> world.isSolid(c.x(), c.y(), c.z()) && world.workstation(c.x(), c.y(), c.z()).isEmpty())) {
                continue;
            }
            if (oreInSight(world, stairs, cut.stand(), cut).isPresent()) {
                return OptionalInt.of(k);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Ore beside the body standing on {@code from}, or beside the cut's cells: outside the shaft's
     * 3x3, not under a tunnel's floor, not left before, its drop got with the pickaxe carried,
     * nothing liquid beside it.
     */
    Optional<BlockPos> oreInSight(WorldView world, Staircase stairs, BlockPos from, Staircase.Cut cut) {
        int tier = bestPickaxe();
        Set<BlockPos> floors = stairs.cuts().stream().filter(c -> c.tunnel() >= 0)
                .map(c -> c.feet().offset(0, -1, 0)).collect(Collectors.toSet());
        List<BlockPos> near = new ArrayList<>(List.of(from, from.offset(0, 1, 0)));
        near.addAll(cut.cells());
        for (BlockPos cell : near) {
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos at = cell.offset(d[0], d[1], d[2]);
                boolean outside = Math.abs(at.x() - stairs.head().x()) > 1 || Math.abs(at.z() - stairs.head().z()) > 1;
                if (outside && !floors.contains(at) && !leftOre.contains(at) && world.isOre(at.x(), at.y(), at.z())
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

    /** The best pickaxe's mining tier carried, or {@link Hardness#HAND} with none. */
    private int bestPickaxe() {
        int best = Hardness.HAND;
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            OptionalInt tier = ItemClassRegistry.miningTier(inventory.itemAt(slot));
            if (tier.isPresent()) {
                best = Math.max(best, tier.getAsInt());
            }
        }
        return best;
    }

    /** Why making cut {@code k} would let in water or lava, or leave a body nothing to stand on. */
    static Optional<String> hazard(WorldView world, Staircase.Cut cut, int k) {
        String what = cut.tunnel() < 0 ? "step " + (k + 1) : cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel();
        for (BlockPos cell : cut.cells()) {
            for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                    return Optional.of("water or lava beside " + what + " at " + cell.offset(d[0], d[1], d[2]));
                }
            }
        }
        BlockPos floor = cut.feet().offset(0, -1, 0);
        if (!world.isSolid(floor.x(), floor.y(), floor.z())) {
            return Optional.of("no floor under " + what + " at " + floor + " - an open cave below");
        }
        return Optional.empty();
    }

    /**
     * Whether a block is being broken now. Between blocks the soldier's hands are free, so a routine
     * ranked above this one - making a stone pickaxe from the cobblestone the stairs gave - gets its
     * turn; one ranked below doesn't, since this one starts its next block first.
     */
    public synchronized boolean active() {
        return breacher.active();
    }

    /** Stops the dig under way. */
    public synchronized void stop() {
        breacher.stop();
        standAt = null;
    }
}
