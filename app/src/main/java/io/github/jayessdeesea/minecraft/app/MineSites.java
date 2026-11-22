package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.jayessdeesea.minecraft.app.DayWorkRules.cutsInto;

/**
 * The keep's mine and the sites it has been at and left (operator, 2026-11-16: move to other sites or go deeper): it
 * starts at {@link #KEEP_MINE} and, each one dried up, moves to the nearest untried site on a grid round
 * it, clear of the flatten. Kept on disk by the soldier runner, and set back from it at start-up. Thread-safe.
 */
final class MineSites {

    private static final Logger LOG = LogManager.getLogger(MineSites.class);

    /**
     * The keep's mine head: off its north-west corner, clear of the slab, its depot and the west
     * borrow pit (x -25..-20, z -17..17).
     */
    static final Position KEEP_MINE = new Position(Keep.MIN - 10, ChainOfCommand.MINE_DEPTH, Keep.MIN - 8);
    /** How far apart the sites a mine may move to are: two chunks. */
    static final int MINE_SITE_STEP = 32;
    /** How many sites out every way from the keep's first mine one may be. */
    static final int MINE_SITE_RINGS = 6;
    /** How far round a mine's head is kept clear of the flatten site and its bench: the stairs' well. */
    static final int MINE_SITE_CLEAR = 8;

    private volatile Position keepMine = KEEP_MINE;
    private final Set<Position> tried = ConcurrentHashMap.newKeySet();

    /** The sites the keep's mine has been at and left. */
    Set<Position> tried() {
        return Set.copyOf(tried);
    }

    /** The sites tried, read back from disk at start-up. */
    void setTried(Collection<Position> sites) {
        tried.addAll(sites);
    }

    /** The keep's mine as it stands. */
    Position keepMine() {
        return keepMine;
    }

    /** The keep's mine as it was left, read back from disk at start-up. */
    void setKeepMine(Position site) {
        LOG.info("[PL] the keep's mine, kept on disk: {}", site);
        keepMine = site;
        if (!site.equals(KEEP_MINE)) {
            tried.add(KEEP_MINE); // moved off it: it was worked out
        }
    }

    /**
     * The mine ordered at ({@code x}, {@code z}) is dried up - every level cut and ore still wanted: if it's the keep's
     * mine as it stands, the keep's mine moves on to a fresh site clear of {@code flatten}. Returns the site it moved
     * to, or empty for another mine's.
     */
    Optional<Position> onDriedUp(int x, int z, Flatten flatten) {
        Position was = keepMine;
        if ((int) Math.floor(was.x()) != x || (int) Math.floor(was.z()) != z) {
            return Optional.empty();
        }
        tried.add(was);
        Position next = next(flatten).orElse(null);
        if (next == null) {
            LOG.warn("[PL] the keep's mine at {} is dried up, and every site within {} blocks has been tried", was, MINE_SITE_RINGS * MINE_SITE_STEP);
            return Optional.empty();
        }
        keepMine = next;
        LOG.warn("[PL] the keep's mine at {} is dried up - the night shift's mine moves to {}", was, next);
        return Optional.of(next);
    }

    /**
     * Where the keep's mine goes when it's spent: the nearest untried site on a grid round its first mine, every way,
     * but the one it's at, clear of {@code flatten} - live (field355), stepping only west put the night shift's mine
     * 160 blocks off, past a lake, a blocked well and a hill. Cut by day ahead of time by a squad the flatten doesn't
     * want (operator, 2026-11-17: dig deeper, but also cut the next mine).
     */
    Optional<Position> next(Flatten flatten) {
        Position centre = flatten.centre();
        Position at = keepMine;
        Position next = null;
        double best = Double.MAX_VALUE;
        for (int i = -MINE_SITE_RINGS; i <= MINE_SITE_RINGS; i++) {
            for (int j = -MINE_SITE_RINGS; j <= MINE_SITE_RINGS; j++) {
                Position p = new Position(KEEP_MINE.x() + i * MINE_SITE_STEP, KEEP_MINE.y(), KEEP_MINE.z() + j * MINE_SITE_STEP);
                double d = Math.hypot(p.x() - centre.x(), p.z() - centre.z());
                if (d < best && !p.equals(at) && !tried.contains(p) && !cutsInto(flatten, (int) Math.floor(p.x()), (int) Math.floor(p.z()))) {
                    best = d;
                    next = p;
                }
            }
        }
        return Optional.ofNullable(next);
    }
}
