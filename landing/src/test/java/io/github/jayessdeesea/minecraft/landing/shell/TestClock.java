package io.github.jayessdeesea.minecraft.landing.shell;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** One test's clock: it moves only when the test, or the test's sleeper, moves it. */
final class TestClock extends Clock {

    private Instant now;

    TestClock(Instant start) {
        this.now = start;
    }

    synchronized void advance(Duration by) {
        now = now.plus(by);
    }

    @Override
    public synchronized Instant instant() {
        return now;
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
