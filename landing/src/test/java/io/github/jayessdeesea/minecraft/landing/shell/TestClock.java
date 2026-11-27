package io.github.jayessdeesea.minecraft.landing.shell;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * One test's clock: it moves only when the test, or the test's sleeper, moves it - and, once told to {@link #tick},
 * with real time too, for the rows whose verify the watchdog must see go quiet.
 */
final class TestClock extends Clock {

    private Instant now;
    private long tickingSince = -1;

    TestClock(Instant start) {
        this.now = start;
    }

    synchronized void advance(Duration by) {
        now = now.plus(by);
    }

    /** From now on, real time moves it as well. */
    synchronized void tick() {
        if (tickingSince < 0) {
            tickingSince = System.nanoTime();
        }
    }

    @Override
    public synchronized Instant instant() {
        return tickingSince < 0 ? now : now.plusNanos(System.nanoTime() - tickingSince);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
