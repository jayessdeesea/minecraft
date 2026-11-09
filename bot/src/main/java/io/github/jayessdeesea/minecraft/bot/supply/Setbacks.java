package io.github.jayessdeesea.minecraft.bot.supply;

import java.util.HashMap;
import java.util.Map;

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
 * <p>Not thread-safe: each routine keeps its own, under its own lock.
 */
public final class Setbacks<T> {

    private final long first;
    private final long most;
    private final Map<T, long[]> failed = new HashMap<>();
    /** Tries in a row that failed, on any target, and the last one's target and reason. */
    private int streak;
    private T lastTarget;
    private String lastWhy;

    /** Tries failed in a row, on any target, that make a routine ask for help. */
    public static final int ESCALATE = 3;

    public Setbacks(long first, long most) {
        if (first <= 0 || most < first) {
            throw new IllegalArgumentException("first " + first + " and most " + most + " ticks");
        }
        this.first = first;
        this.most = most;
    }

    /** {@code target} failed at tick {@code now} for {@code why}: it's left alone for longer than the last time. */
    public void failed(T target, long now, String why) {
        streak++;
        lastTarget = target;
        lastWhy = why;
        failed(target, now);
    }

    /** {@code target} failed at tick {@code now}: it's left alone for longer than the last time. */
    public void failed(T target, long now) {
        long[] entry = failed.computeIfAbsent(target, t -> new long[]{0, 0});
        long wait = Math.min(most, first << Math.min(entry[0], 30));
        entry[0]++;
        entry[1] = now + wait;
    }

    /** {@code target} worked: its failures are forgotten, and the routine's run of them ends. */
    public void worked(T target) {
        failed.remove(target);
        streak = 0;
    }

    /** The work is gone - done by someone else, or no longer asked for: nothing to ask help with. */
    public void noWork() {
        streak = 0;
    }

    /**
     * Why the routine needs help, once its last {@link #ESCALATE} tries in a row all failed: how
     * many, and the last one's target and reason.
     */
    public java.util.Optional<String> stuck() {
        return streak < ESCALATE ? java.util.Optional.empty()
                : java.util.Optional.of(streak + " tries in a row failed, the last at " + lastTarget + ": " + lastWhy);
    }

    /** Whether {@code target} is still being left alone at tick {@code now}. */
    public boolean barred(T target, long now) {
        long[] entry = failed.get(target);
        return entry != null && entry[1] > now;
    }

    /** The targets still being left alone at tick {@code now}. */
    public java.util.Set<T> barredNow(long now) {
        java.util.Set<T> barred = new java.util.HashSet<>();
        failed.forEach((target, entry) -> {
            if (entry[1] > now) {
                barred.add(target);
            }
        });
        return barred;
    }

    /** How many times in a row {@code target} has failed. */
    public int failures(T target) {
        long[] entry = failed.get(target);
        return entry == null ? 0 : (int) entry[0];
    }
}
