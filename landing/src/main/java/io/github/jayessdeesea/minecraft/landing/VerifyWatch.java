package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;

/**
 * One verify as its watchdog sees it: when it began, the last look, the log's size then, when the log last changed,
 * and the longest quiet so far. Its one progress signal is the log's size changing - a Maven line, a finished test
 * class, a test's own output all grow it. What kills is an observation, never a guess: no change for the stall limit
 * (Stalled), or the cap run however busy (Capped). Alive for one verify, in the verifier's loop; nothing survives it.
 */
public record VerifyWatch(Instant started, Instant lastLook, long bytes, Instant lastGrew, Duration longestQuiet) {

    /**
     * The watchdog's limits: {@code stall} quiet ends a verify, {@code cap} ends it however busy, a look every
     * {@code look}. Refused unless 0 < look and four looks <= stall < cap: a stall shorter than four looks could be
     * a watcher's own lateness.
     */
    public record Limits(Duration stall, Duration cap, Duration look) {
        public Limits {
            if (look == null || stall == null || cap == null || look.isNegative() || look.isZero()) {
                throw new IllegalArgumentException("a watchdog with no look interval");
            }
            if (stall.compareTo(look.multipliedBy(4)) < 0) {
                throw new IllegalArgumentException("a stall limit of " + stall + " under four looks of " + look);
            }
            if (stall.compareTo(cap) >= 0) {
                throw new IllegalArgumentException("a stall limit of " + stall + " not under the cap " + cap);
            }
        }

        /** The same limits with this verify's cap ({@link Integrator#cap}): refused, as any, unless above the stall limit. */
        public Limits withCap(Duration verifyCap) {
            return new Limits(stall, verifyCap, look);
        }
    }

    /** What a look found. */
    public sealed interface Verdict {
        /** Going on: the watch as it now stands. */
        record Going(VerifyWatch next) implements Verdict {
        }

        /** Quiet for {@code quiet}, at least the stall limit. */
        record Stalled(VerifyWatch at, Duration quiet) implements Verdict {
        }

        /** Ran {@code ran}, at least the cap, still writing. */
        record Capped(VerifyWatch at, Duration ran) implements Verdict {
        }
    }

    /** Begun: nothing written yet, quiet since now. */
    public static VerifyWatch start(Instant now) {
        return new VerifyWatch(now, now, 0, now, Duration.ZERO);
    }

    /**
     * A look at the log, {@code bytesNow} long, in order: blind time is not counted - a look more
     * than four looks after the last means the watcher was not looking, so both clocks move forward by the gap past one
     * look; a changed size is growth; quiet for the stall limit is Stalled; else run for the cap is Capped; else Going.
     */
    public static Verdict look(VerifyWatch w, long bytesNow, Instant now, Limits limits) {
        return look(w, java.util.OptionalLong.of(bytesNow), now, limits);
    }

    /**
     * The same, the size {@code read}, or none while the log is not there or cannot be read: none is the last size
     * kept - no growth - so a log not there yet, gone for a moment or unreadable for one is never taken as output, and
     * one gone or unreadable for good ends as a stall, said.
     */
    public static Verdict look(VerifyWatch w, java.util.OptionalLong read, Instant now, Limits limits) {
        long bytesNow = read.orElse(w.bytes());
        Duration gap = Duration.between(w.lastLook(), now);
        Instant started = w.started();
        Instant lastGrew = w.lastGrew();
        if (gap.compareTo(limits.look().multipliedBy(4)) > 0) {
            Duration blind = gap.minus(limits.look());
            started = started.plus(blind);
            lastGrew = lastGrew.plus(blind);
        }
        Duration quiet = Duration.between(lastGrew, now);
        Duration longest = quiet.compareTo(w.longestQuiet()) > 0 ? quiet : w.longestQuiet();
        if (bytesNow != w.bytes()) {
            lastGrew = now;
            quiet = Duration.ZERO;
        }
        VerifyWatch next = new VerifyWatch(started, now, bytesNow, lastGrew, longest);
        if (quiet.compareTo(limits.stall()) >= 0) {
            return new Verdict.Stalled(next, quiet);
        }
        Duration ran = Duration.between(started, now);
        if (ran.compareTo(limits.cap()) >= 0) {
            return new Verdict.Capped(next, ran);
        }
        return new Verdict.Going(next);
    }
}
