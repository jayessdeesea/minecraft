package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier clearing its camp ({@link Clearing}): the sky over it first - the lowest log or
 * leaves it can reach - then its high ground, a block of dirt at a time; one {@link Digger} job at
 * a time, keeping what it cuts. Soldiers clearing one camp share
 * {@code claims}, so each takes a different block. A block it couldn't cut is left longer each time
 * ({@link Setbacks}).
 */
public final class Clearer {

    private static final Logger LOG = LogManager.getLogger(Clearer.class);

    /** How long it waits after a cut fails, or when there's nothing in reach, before looking again. */
    static final int COOLDOWN_TICKS = 100;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;
    private final Setbacks<BlockPos> failed = new Setbacks<>(600, 12_000);

    private Digger digger;
    private long ticks;
    private int cooldown;

    public Clearer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return digger != null;
    }

    /** Where the soldier should be standing for the cut under way. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return failed.stuck().map(s -> "can't clear the camp: " + s);
    }

    /** Each tick: cuts the {@code cover} over the camp while {@code free}; anything else stops the cut under way. */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free) {
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (cover.isEmpty()) {
            failed.noWork();
        }
        if (!free || cover.isEmpty()) {
            stop();
            return;
        }
        if (digger != null) {
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> {
                    failed.worked(digger.spot().block());
                    stop();
                }
                // The block gone is the cut done, whatever became of its drop: live, a leaf's sapling
                // fell out of reach and the cut counted as failed.
                case Digger.Progress.Failed _ when !digger.spot().target().at(world, digger.spot().block()) -> {
                    failed.worked(digger.spot().block());
                    stop();
                }
                case Digger.Progress.Failed(String why) -> {
                    LOG.info("{} leaves that cut for now - {}", name, why);
                    failed.failed(digger.spot().block(), ticks, why);
                    stop();
                    cooldown = COOLDOWN_TICKS;
                }
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        Set<BlockPos> excluded = failed.barredNow(ticks);
        excluded.addAll(claims);
        Optional<Clearing.Cut> cut = Clearing.next(world, cover, soldier, excluded);
        if (cut.isEmpty()) {
            cooldown = COOLDOWN_TICKS;
            return;
        }
        Clearing.Cut c = cut.get();
        Digger.Target target = c.log() ? Digger.Target.LOG
                : world.leaves(c.block().x(), c.block().y(), c.block().z()).isPresent() ? Digger.Target.LEAVES : Digger.Target.SOIL;
        digger = new Digger(name, session, inventory, claims, new Digger.Spot(target, c.stand(), c.block()));
    }

    /** Stops the cut under way, its claims given up. */
    public synchronized void stop() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }
}
