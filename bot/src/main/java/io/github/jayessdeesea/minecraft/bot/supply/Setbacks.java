package io.github.jayessdeesea.minecraft.bot.supply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a soldier failed at, target by target, and how long it leaves each alone: the first failure
 * for {@code first} ticks, each one after that on the same target for twice as long as the last, up
 * to {@code most}. A target that works is forgotten. So something that failed once - a squadmate in
 * the way - is tried again soon, and something that keeps failing - a cell across water, a torch on
 * leaves - is tried less and less: live, one soldier walked at the same unreachable cell 462 times
 * in a night, another put a torch on the same treetop 426 times.
 *
 * <p>It's also the routine's escalation ladder. Waiting and trying again is the first rung; the
 * next target - another cell, another spot - is the second; when the last {@link #ESCALATE}
 * tries in a row have all failed, whatever they were at, the routine is out of ways of its own and
 * says so ({@link #stuck}): the soldier reports CANTCO, which is a request to its squad leader.
 *
 * <p>Thread-safe: a routine keeps its own, and one shared by soldiers' ticks is safe too.
 */
public final class Setbacks<T> {

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger(Setbacks.class);

    private final long first;
    private final long most;
    /** What spreads its waits, or 0 for none: two soldiers failed at one thing don't both come back at the same tick. */
    private final long salt;
    /** How many failures in a row at one target are said, and then at each doubling: a retry that isn't working is dug into. */
    public static final int SAID_FROM = 4;
    private final Map<T, long[]> failed = new HashMap<>();
    /** Tries in a row that failed, on any target, and the last one's target and reason. */
    private int streak;
    private T lastTarget;
    private String lastWhy;

    /** Tries failed in a row, on any target, that make a routine ask for help. */
    public static final int ESCALATE = 3;

    public Setbacks(long first, long most) {
        this(first, most, 0);
    }

    /**
     * As {@link #Setbacks(long, long)}, each wait lengthened by up to a quarter of itself, by {@code salt} - its owner's,
     * a soldier's name's hash - the target and how often it has failed (operator, 2026-11-23: trying again, do something
     * different - wait a little while, with jitter). Worked out, not drawn: the same failures give the same waits.
     */
    public Setbacks(long first, long most, long salt) {
        if (first <= 0 || most < first) {
            throw new IllegalArgumentException("first " + first + " and most " + most + " ticks");
        }
        this.first = first;
        this.most = most;
        this.salt = salt;
    }

    /** {@code target} failed at tick {@code now} for {@code why}: it's left alone for longer than the last time. */
    public synchronized void failed(T target, long now, String why) {
        streak++;
        lastTarget = target;
        lastWhy = why;
        failed(target, now);
        // Said at four in a row at one target, and at each doubling: the count is what says a retry isn't working.
        int failures = failures(target);
        if (failures >= SAID_FROM && Integer.bitCount(failures) == 1) {
            LOG.warn("[Retry] {} tries in a row have failed at {}, the last: {} - left alone longer each time", failures, target, why);
        }
    }

    /** {@code target} failed at tick {@code now}: it's left alone for longer than the last time. */
    public synchronized void failed(T target, long now) {
        long[] entry = failed.computeIfAbsent(target, t -> new long[]{0, 0});
        long wait = Math.min(most, first << Math.min(entry[0], 30));
        if (salt != 0) {
            wait += Math.floorMod(31L * java.util.Objects.hash(target, entry[0]) + salt, wait / 4 + 1);
        }
        entry[0]++;
        entry[1] = now + wait;
    }

    /** {@code target} worked: its failures are forgotten, and the routine's run of them ends. */
    public synchronized void worked(T target) {
        failed.remove(target);
        streak = 0;
    }

    /** The work is gone - done by someone else, or no longer asked for: nothing to ask help with. */
    public synchronized void noWork() {
        streak = 0;
    }

    /**
     * Why the routine needs help, once its last {@link #ESCALATE} tries in a row all failed: how
     * many, and the last one's target and reason.
     */
    public synchronized Optional<String> stuck() {
        return streak < ESCALATE ? Optional.empty()
                : Optional.of(streak + " tries in a row failed, the last at " + lastTarget + ": " + lastWhy);
    }

    /** Whether {@code target} is still being left alone at tick {@code now}. */
    public synchronized boolean barred(T target, long now) {
        long[] entry = failed.get(target);
        return entry != null && entry[1] > now;
    }

    /** The targets still being left alone at tick {@code now}. */
    public synchronized Set<T> barredNow(long now) {
        Set<T> barred = new HashSet<>();
        failed.forEach((target, entry) -> {
            if (entry[1] > now) {
                barred.add(target);
            }
        });
        return barred;
    }

    /** How many times in a row {@code target} has failed. */
    public synchronized int failures(T target) {
        long[] entry = failed.get(target);
        return entry == null ? 0 : (int) entry[0];
    }
}
