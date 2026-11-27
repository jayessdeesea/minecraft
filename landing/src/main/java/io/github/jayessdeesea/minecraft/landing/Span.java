package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;

/** A span of time in the words the queue's lines use: minutes and seconds, never hours - "12s", "3m55s", "90m". */
public final class Span {

    private Span() {
    }

    /** Exact to the second, rounded down. */
    public static String of(Duration d) {
        long s = Math.max(0, d.toSeconds());
        if (s < 60) {
            return s + "s";
        }
        return s % 60 == 0 ? s / 60 + "m" : s / 60 + "m" + s % 60 + "s";
    }

    /** For an estimate: to the minute from ten minutes up, else to ten seconds. */
    public static String about(Duration d) {
        long ms = Math.max(0, d.toMillis());
        long s = ms >= 600_000 ? Math.round(ms / 60_000.0) * 60 : Math.round(ms / 10_000.0) * 10;
        return of(Duration.ofSeconds(s));
    }
}
