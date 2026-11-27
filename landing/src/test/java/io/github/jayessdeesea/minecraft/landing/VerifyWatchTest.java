package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The verify's watchdog (design section 2): the one progress signal is the log's size changing. Quiet for the stall
 * limit ends it, Stalled; running the cap ends it however busy, Capped; quiet and capped at once, Stalled. A look more
 * than four looks after the last was not watching - the machine slept, the integrator was starved - so the gap past one
 * look moves both clocks forward and counts toward neither limit. The limits are 10m stall, 90m cap, a look each 10 s.
 */
class VerifyWatchTest {

    private static final Instant T0 = Instant.parse("2026-11-27T12:00:00Z");
    private static final VerifyWatch.Limits LIMITS = new VerifyWatch.Limits(Duration.ofMinutes(10), Duration.ofMinutes(90),
            Duration.ofSeconds(10));

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    /** A watch begun at 0, last looked at {@code lastLook}, the log {@code bytes} long since {@code lastGrew}. */
    private static VerifyWatch watch(int lastLook, long bytes, int lastGrew) {
        return new VerifyWatch(T0, at(lastLook), bytes, at(lastGrew), Duration.ZERO);
    }

    private static String line(VerifyWatch.Verdict verdict) {
        return switch (verdict) {
            case VerifyWatch.Verdict.Going g -> "Going, grew at " + Duration.between(T0, g.next().lastGrew()).toSeconds();
            case VerifyWatch.Verdict.Stalled s -> "Stalled, quiet " + s.quiet().toSeconds();
            case VerifyWatch.Verdict.Capped c -> "Capped, ran " + c.ran().toSeconds();
        };
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # last look | bytes then | last grew | bytes now | now   -> verdict (times as seconds past the watch's start)
            # grew: Going, its time now
            590         | 100        | 0         | 200       | 600   | Going, grew at 600
            # the same size under the limit
            590         | 100        | 0         | 100       | 599   | Going, grew at 0
            # quiet exactly the stall limit
            590         | 100        | 0         | 100       | 600   | Stalled, quiet 600
            # still growing at the cap
            5390        | 100        | 5390      | 200       | 5400  | Capped, ran 5400
            # quiet and at the cap together: the stall is what is said
            5390        | 100        | 4800      | 100       | 5400  | Stalled, quiet 600
            # no log ever, 0 bytes, for the limit
            590         | 0          | 0         | 0         | 600   | Stalled, quiet 600
            # a shrunk log is a changed one
            590         | 100        | 0         | 50        | 600   | Going, grew at 600
            # a look four looks late: neither limit advanced by the gap past one look
            300         | 100        | 0         | 100       | 1200  | Going, grew at 890
            5100        | 100        | 5100      | 200       | 6000  | Going, grew at 6000
            # exactly four looks late is still watched
            560         | 100        | 0         | 100       | 600   | Stalled, quiet 600
            """)
    void whatALookFinds(int lastLook, long bytes, int lastGrew, long bytesNow, int now, String expected) {
        assertEquals(expected, line(VerifyWatch.look(watch(lastLook, bytes, lastGrew), bytesNow, at(now), LIMITS)));
    }

    /** The blind gap moves the start too: a look 15m late at 85m of a 90m cap is not Capped. */
    @Test
    void aBlindGapMovesTheStartToo() {
        VerifyWatch.Verdict v = VerifyWatch.look(watch(5100, 100, 5100), 200, at(6000), LIMITS);
        assertEquals(T0.plusSeconds(890), ((VerifyWatch.Verdict.Going) v).next().started());
    }

    /**
     * A size that could not be read is no growth (review round 1): the last size kept, the quiet going on - and a log that
     * stays unreadable ends as a stall.
     */
    @Test
    void anUnreadableSizeIsNoGrowth() {
        VerifyWatch.Verdict v = VerifyWatch.look(watch(590, 100, 0), java.util.OptionalLong.empty(), at(599), LIMITS);
        VerifyWatch next = ((VerifyWatch.Verdict.Going) v).next();
        assertEquals(T0, next.lastGrew());
        assertEquals(100, next.bytes());
        assertEquals("Stalled, quiet 600", line(VerifyWatch.look(next, java.util.OptionalLong.empty(), at(600), LIMITS)));
    }

    /**
     * A log that goes and comes back at its size is no output (review round 2, B): looks of none, 100, none, 100 never
     * move the last growth, and it stalls at the limit; from the start, no reading until the limit is a stall too.
     */
    @Test
    void aLogThatGoesAndComesBackIsNoOutput() {
        java.util.OptionalLong none = java.util.OptionalLong.empty();
        java.util.OptionalLong hundred = java.util.OptionalLong.of(100);
        VerifyWatch w = watch(0, 100, 0);
        int t = 0;
        for (java.util.OptionalLong read : java.util.List.of(none, hundred, none, hundred, none, hundred)) {
            t += 10;
            w = ((VerifyWatch.Verdict.Going) VerifyWatch.look(w, read, at(t), LIMITS)).next();
            assertEquals(T0, w.lastGrew());
        }
        VerifyWatch late = new VerifyWatch(T0, at(590), 100, T0, Duration.ZERO);
        assertEquals("Stalled, quiet 600", line(VerifyWatch.look(late, hundred, at(600), LIMITS)));
        VerifyWatch fresh = VerifyWatch.start(T0);
        for (t = 10; t < 600; t += 10) {
            fresh = ((VerifyWatch.Verdict.Going) VerifyWatch.look(fresh, none, at(t), LIMITS)).next();
        }
        assertEquals("Stalled, quiet 600", line(VerifyWatch.look(fresh, none, at(600), LIMITS)));
    }

    /** Begun: nothing written yet, the log 0 bytes, quiet since now. */
    @Test
    void aWatchBegins() {
        assertEquals(new VerifyWatch(T0, T0, 0, T0, Duration.ZERO), VerifyWatch.start(T0));
    }

    /** The longest quiet is kept across looks, the stall limit's expected-against-max. */
    @Test
    void theLongestQuietIsKept() {
        VerifyWatch w = VerifyWatch.start(T0);
        w = going(VerifyWatch.look(w, 10, at(10), LIMITS));
        w = going(VerifyWatch.look(w, 10, at(20), LIMITS));
        w = going(VerifyWatch.look(w, 10, at(30), LIMITS));
        w = going(VerifyWatch.look(w, 20, at(40), LIMITS));
        assertEquals(Duration.ofSeconds(30), w.longestQuiet());
        w = going(VerifyWatch.look(w, 30, at(50), LIMITS));
        assertEquals(Duration.ofSeconds(30), w.longestQuiet());
        assertEquals(at(50), w.lastGrew());
        assertEquals(30, w.bytes());
    }

    private static VerifyWatch going(VerifyWatch.Verdict v) {
        return ((VerifyWatch.Verdict.Going) v).next();
    }

    /** Refused unless 0 < look and 4 looks <= stall < cap. */
    @Test
    void theLimitsAreRefusedOutOfOrder() {
        assertThrows(IllegalArgumentException.class, () -> new VerifyWatch.Limits(Duration.ofMinutes(10), Duration.ofMinutes(90),
                Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new VerifyWatch.Limits(Duration.ofSeconds(39), Duration.ofMinutes(90),
                Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> new VerifyWatch.Limits(Duration.ofMinutes(90), Duration.ofMinutes(90),
                Duration.ofSeconds(10)));
        new VerifyWatch.Limits(Duration.ofSeconds(40), Duration.ofSeconds(41), Duration.ofSeconds(10));
        assertEquals(Duration.ofMinutes(120), LIMITS.withCap(Duration.ofMinutes(120)).cap());
    }
}
