package io.github.jayessdeesea.minecraft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Every periodic task's faults, so none dies of one (docs/conventions.md, "No fallbacks"). A
 * scheduled task that throws is cancelled by its executor for good: live, the squad loop died at
 * midnight on one bad read, and for seven hours the soldiers worked their last orders with no squad
 * leader or general. So each tick runs guarded: an exception is logged with its stack trace the
 * first time, counted after that and summed up once a {@link #SUMMARY_MILLIS}, its recovery said,
 * and every fault is in the dawn review. The next tick starts again from the world and the chain
 * of command as they stand, so nothing half-done is carried over. An {@link Error} - the VM itself
 * failing - is not caught.
 */
final class Faults {

    private static final Logger LOG = LogManager.getLogger(Faults.class);

    /** How often a fault that keeps happening is summed up in the log. */
    static final long SUMMARY_MILLIS = 60_000;

    private final LongSupplier clock;
    /** By task: the fault it's in now, if any. */
    private final Map<String, Fault> current = new ConcurrentHashMap<>();
    /** By task and fault: how many times since the last review. */
    private final Map<String, Integer> tally = new ConcurrentHashMap<>();

    private static final class Fault {
        final String signature;
        int count = 1;
        long summarisedAt;

        Fault(String signature, long now) {
            this.signature = signature;
            this.summarisedAt = now;
        }
    }

    Faults(LongSupplier clock) {
        this.clock = clock;
    }

    /** {@code task}, guarded: it can fail on any tick and still run the next. */
    Runnable guard(String what, Runnable task) {
        return () -> run(what, task);
    }

    /** Runs {@code task} once, guarded. */
    void run(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            fault(what, e);
            return;
        }
        Fault ended = current.remove(what);
        if (ended != null) {
            LOG.info("[Fault] {} runs again, after failing {} times: {}", what, ended.count, ended.signature);
        }
    }

    private void fault(String what, RuntimeException e) {
        long now = clock.getAsLong();
        String signature = signature(e);
        tally.merge(what + " - " + signature, 1, Integer::sum);
        Fault fault = current.get(what);
        if (fault == null || !fault.signature.equals(signature)) {
            current.put(what, new Fault(signature, now));
            LOG.error("[Fault] {} failed - it carries on next tick: {}", what, e.toString(), e);
            return;
        }
        fault.count++;
        if (now - fault.summarisedAt >= SUMMARY_MILLIS) {
            LOG.error("[Fault] ALARM: {} still failing, {} times now: {}", what, fault.count, signature);
            fault.summarisedAt = now;
        }
    }

    /** The exception and where it was thrown: the same fault again is the same signature. */
    static String signature(RuntimeException e) {
        StackTraceElement[] trace = e.getStackTrace();
        return e.getClass().getSimpleName() + (trace.length == 0 ? "" : " at " + trace[0].getClassName().replaceAll(".*\\.", "")
                + "." + trace[0].getMethodName() + ":" + trace[0].getLineNumber())
                + (e.getMessage() == null ? "" : " - " + e.getMessage());
    }

    /** The dawn review's faults since the last one: each task's, and how often; then cleared. */
    List<String> review() {
        List<String> lines = new ArrayList<>();
        tally.forEach((fault, count) -> lines.add("  fault: " + fault + " x" + count));
        tally.clear();
        if (lines.isEmpty()) {
            lines.add("  no faults");
        }
        return lines;
    }
}
