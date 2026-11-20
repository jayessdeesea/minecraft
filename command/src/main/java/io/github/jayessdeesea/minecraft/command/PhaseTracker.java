package io.github.jayessdeesea.minecraft.command;

import java.util.Locale;

/**
 * Moves the force's {@link Phase} on measured readiness, with a gap between the way up and the way down and a
 * day's dwell so it doesn't flap (operator, 2026-11-18): up a phase once its standard has held a day; down once
 * the lower bound fails after a day in the phase; to {@link Phase#SEIZURE} at once on a breach. Days of supply
 * are Class I's (rations) and Class IV's (logs), the lower of the two (FM 4-0). Not thread-safe: one owner.
 */
public final class PhaseTracker {

    /** What the phase is measured on. */
    public record Readiness(boolean secure, boolean armed, boolean armoured, double rationsDays, double logsDays, boolean breached) {

        /** The lower of Class I's and Class IV's days of supply. */
        public double days() {
            return Math.min(rationsDays, logsDays);
        }
    }

    /** Days of supply to enter {@link Phase#BUILDUP}, and below which it falls back to {@link Phase#CONSOLIDATION}. */
    public static final double BUILDUP_UP = 3, BUILDUP_DOWN = 1;
    /** Days of supply to enter {@link Phase#BREAKOUT}, and below which it falls back to {@link Phase#BUILDUP}. */
    public static final double BREAKOUT_UP = 7, BREAKOUT_DOWN = 3;
    /** How long a standard holds before the phase goes up, and the least time in a phase before it goes down: a day. */
    public static final long DWELL_MILLIS = 20 * 60_000;

    private Phase phase = Phase.SEIZURE;
    private long since = Long.MIN_VALUE / 2;
    private long upHeldSince = Long.MIN_VALUE;

    public Phase phase() {
        return phase;
    }

    /** When the phase was entered. */
    public long since() {
        return since;
    }

    /** Since when the next phase's standard has held, or {@link Long#MIN_VALUE} while it doesn't. */
    public long upHeldSince() {
        return upHeldSince;
    }

    /** The phase and when it was entered, as kept before a restart. */
    public void restore(Phase restored, long enteredAt) {
        restore(restored, enteredAt, Long.MIN_VALUE);
    }

    /**
     * The phase, when it was entered, and since when the next one's standard has held, as kept before a restart: a
     * restart doesn't start the day's dwell over. Live (field516-523), redeployed every quarter hour, the camp lit and
     * every soldier armed, the force stayed in the SEIZURE - its day never held across one run.
     */
    public void restore(Phase restored, long enteredAt, long upSince) {
        this.phase = restored;
        this.since = enteredAt;
        this.upHeldSince = upSince;
    }

    /** The phase on {@code readiness} at {@code now}. */
    public Phase update(Readiness readiness, long now) {
        if (readiness.breached() && phase != Phase.SEIZURE) {
            enter(Phase.SEIZURE, now);
            return phase;
        }
        boolean up = switch (phase) {
            case SEIZURE -> readiness.secure() && readiness.armed();
            case CONSOLIDATION -> readiness.days() >= BUILDUP_UP;
            case BUILDUP -> readiness.days() >= BREAKOUT_UP && readiness.armoured();
            case BREAKOUT -> false;
        };
        boolean down = switch (phase) {
            case SEIZURE -> false;
            case CONSOLIDATION -> !readiness.secure();
            case BUILDUP -> readiness.days() < BUILDUP_DOWN;
            case BREAKOUT -> readiness.days() < BREAKOUT_DOWN || !readiness.armoured();
        };
        if (down && now - since >= DWELL_MILLIS) {
            enter(Phase.values()[phase.ordinal() - 1], now);
        } else if (up) {
            if (upHeldSince == Long.MIN_VALUE) {
                upHeldSince = now;
            } else if (now - upHeldSince >= DWELL_MILLIS) {
                enter(Phase.values()[phase.ordinal() + 1], now);
            }
        } else {
            upHeldSince = Long.MIN_VALUE;
        }
        return phase;
    }

    private void enter(Phase next, long now) {
        phase = next;
        since = now;
        upHeldSince = Long.MIN_VALUE;
    }

    /** What stands between the force and the next phase, for the platoon leader. */
    public static String toNext(Phase phase, Readiness r) {
        return switch (phase) {
            case SEIZURE -> "to CONSOLIDATION: the camp " + (r.secure() ? "lit" : "not yet lit") + ", "
                    + (r.armed() ? "every soldier armed" : "not every soldier armed") + ", held a day";
            case CONSOLIDATION -> String.format(Locale.ROOT, "to BUILDUP: Class I %.1f and Class IV %.1f days of supply, of %.0f each, held a day",
                    r.rationsDays(), r.logsDays(), BUILDUP_UP);
            case BUILDUP -> String.format(Locale.ROOT, "to BREAKOUT: Class I %.1f and Class IV %.1f days of supply, of %.0f each, and every soldier armoured%s",
                    r.rationsDays(), r.logsDays(), BREAKOUT_UP, r.armoured() ? "" : " (not yet)");
            case BREAKOUT -> "the last phase";
        };
    }
}
