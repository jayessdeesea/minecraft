package io.github.jayessdeesea.minecraft.bot;

import java.util.Optional;

/**
 * One soldier's activity over time: what it's doing now and since when, which routine has its hands, how often its
 * activity has changed, and how often a change was a return - back to the activity it had just left, having held
 * the one between for under {@link #RETURN_MILLIS}. Changes and returns are the soldier's churn, countable in a
 * simulation as in the field.
 *
 * <p>Written by the soldier's tick alone ({@link #observe}); read by anyone ({@link #now}), as one value.
 */
public final class ActivityTrack {

    /** Held for less than this, an activity left for the one before it was a return, not a piece of work. */
    public static final long RETURN_MILLIS = 10_000;

    /** What the soldier is doing and since when, the routine that has its hands, and its counts so far. */
    public record Now(SoldierActivity activity, Optional<Hands.Routine> hands, long since, int changes, int returns) {
    }

    /** Returns inside this long are counted together: a soldier going to and fro between two activities. */
    public static final long LOOP_MILLIS = 5 * 60_000;
    /** So many returns inside {@link #LOOP_MILLIS} is a loop, said then and at each as many again. */
    public static final int LOOP_RETURNS = 8;

    /**
     * A change of activity: what was left, after how long, for what - whether it's a return, and how many returns it has
     * made inside {@link #LOOP_MILLIS}.
     */
    public record Change(SoldierActivity from, SoldierActivity to, long heldMillis, boolean returned, int returnsLately) {

        /** Whether this return makes a loop to say: the soldier's own report that it's getting nowhere. */
        public boolean loops() {
            return returned && returnsLately > 0 && returnsLately % LOOP_RETURNS == 0;
        }

        /** The log's line for it, less the soldier's name. */
        public String describe() {
            return from.label() + " -> " + to.label() + " after " + heldMillis / 1000 + " s" + (returned ? " (back again)" : "");
        }
    }

    private volatile Now now;
    private SoldierActivity before;
    private final java.util.ArrayDeque<Long> returnedAt = new java.util.ArrayDeque<>();

    /** What it's doing, or empty before its first tick. */
    public Optional<Now> now() {
        return Optional.ofNullable(now);
    }

    /**
     * The tick's reading at {@code millis}: its activity, and the routine that has its hands.
     *
     * @return the change, if the activity differs from the last reading's; empty for the first
     */
    public Optional<Change> observe(long millis, SoldierActivity activity, Optional<Hands.Routine> hands) {
        Now was = now;
        if (was == null) {
            now = new Now(activity, hands, millis, 0, 0);
            return Optional.empty();
        }
        if (was.activity().equals(activity)) {
            if (!was.hands().equals(hands)) {
                now = new Now(activity, hands, was.since(), was.changes(), was.returns());
            }
            return Optional.empty();
        }
        long held = millis - was.since();
        boolean returned = activity.equals(before) && held < RETURN_MILLIS;
        before = was.activity();
        now = new Now(activity, hands, millis, was.changes() + 1, was.returns() + (returned ? 1 : 0));
        if (returned) {
            returnedAt.add(millis);
        }
        while (!returnedAt.isEmpty() && millis - returnedAt.peek() > LOOP_MILLIS) {
            returnedAt.poll();
        }
        return Optional.of(new Change(was.activity(), activity, held, returned, returnedAt.size()));
    }
}
