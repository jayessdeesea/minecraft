package io.github.jayessdeesea.minecraft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every echelon's decisions as they go to the models: when each was queued, when it started,
 * when its answer came, and how many others were in flight. The models answer one or a few calls
 * at a time - Ollama queues the rest - so a decision's wait is the chain of command's real
 * latency, and it grows with every squad added. Each decision is logged with its times; an ALARM
 * is logged when one waited or took too long, or too many were in flight at once.
 *
 * <p>Load is also shed here: a routine decision - one nothing new asked for - is skipped while
 * {@link #MAX_IN_FLIGHT} or more are under way, so the urgent ones (a contact, a CANTCO, a new
 * order) aren't queued behind it.
 */
final class ModelCalls {

    private static final Logger LOG = LogManager.getLogger(ModelCalls.class);

    /** Waiting this long to start - behind others in the same echelon's queue - is an alarm. */
    static final long WAIT_ALARM_MILLIS = 5_000;
    /** Queued to answered, longer than this, is an alarm: the soldiers act on stale orders meanwhile. */
    static final long TOTAL_ALARM_MILLIS = 20_000;
    /** In flight at once, from this many, routine decisions are shed. */
    static final int MAX_IN_FLIGHT = 3;

    private final AtomicInteger inFlight = new AtomicInteger();

    /** A decision queued for {@code who}: its times are taken from here on. */
    Ticket queued(String who) {
        return new Ticket(who, System.currentTimeMillis(), inFlight.incrementAndGet());
    }

    /** How many decisions are queued or running now. */
    int inFlight() {
        return inFlight.get();
    }

    /** Whether a routine decision should wait for the next cycle: enough are in flight already. */
    boolean shed(String who) {
        int now = inFlight.get();
        if (now >= MAX_IN_FLIGHT) {
            LOG.info("[Model] {}: routine decision shed - {} already in flight", who, now);
            return true;
        }
        return false;
    }

    /** One decision's passage through the queue. */
    final class Ticket {
        private final String who;
        private final long queuedAt;
        private final int aheadAtQueue;
        private long startedAt;

        private Ticket(String who, long queuedAt, int inFlightAtQueue) {
            this.who = who;
            this.queuedAt = queuedAt;
            this.aheadAtQueue = inFlightAtQueue - 1;
        }

        /** It's off the queue and running. */
        void started() {
            startedAt = System.currentTimeMillis();
        }

        /** Its answer came - or it failed; either way it's out of flight. */
        void finished() {
            long done = System.currentTimeMillis();
            int left = inFlight.decrementAndGet();
            long waited = (startedAt == 0 ? done : startedAt) - queuedAt;
            long ran = startedAt == 0 ? 0 : done - startedAt;
            if (waited >= WAIT_ALARM_MILLIS || waited + ran >= TOTAL_ALARM_MILLIS || aheadAtQueue + 1 > MAX_IN_FLIGHT) {
                LOG.warn("[Model] ALARM {}: waited {} ms to start, ran {} ms, {} ahead of it when queued, {} in flight now",
                        who, waited, ran, aheadAtQueue, left);
            } else {
                LOG.info("[Model] {}: waited {} ms, ran {} ms, {} ahead of it when queued", who, waited, ran, aheadAtQueue);
            }
        }
    }
}
