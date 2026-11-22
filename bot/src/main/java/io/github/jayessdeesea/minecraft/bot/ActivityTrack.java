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

    /** A change of activity: what was left, after how long, for what - and whether it's a return. */
    public record Change(SoldierActivity from, SoldierActivity to, long heldMillis, boolean returned) {

        /** The log's line for it, less the soldier's name. */
        public String describe() {
            return from.label() + " -> " + to.label() + " after " + heldMillis / 1000 + " s" + (returned ? " (back again)" : "");
        }
    }

    private volatile Now now;
    private SoldierActivity before;

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
        return Optional.of(new Change(was.activity(), activity, held, returned));
    }
}
