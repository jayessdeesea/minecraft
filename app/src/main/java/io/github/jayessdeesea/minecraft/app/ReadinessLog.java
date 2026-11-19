package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Readiness;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each squad's readiness logged where it changed - safety where it stands, and its C-rating - once the change has
 * held {@value #READINESS_HOLDS} SITREPs: soldiers ranging in and out of the camp flip it back and forth. Thread-safe.
 */
final class ReadinessLog {

    private static final Logger LOG = LogManager.getLogger(ReadinessLog.class);

    /** SITREPs a new readiness holds before it's logged. */
    static final int READINESS_HOLDS = 3;

    /** Each squad's readiness as last logged, so only a change of it is. */
    private final Map<String, Readiness> readiness = new ConcurrentHashMap<>();
    /** A squad's readiness seen since the last logged, and in how many SITREPs running. */
    private final Map<String, Readiness> readinessSeen = new ConcurrentHashMap<>();
    private final Map<String, Integer> readinessHeld = new ConcurrentHashMap<>();

    /** {@code squad}'s readiness {@code now}, logged if it's a change that has held. */
    void log(String squad, Readiness now) {
        Readiness was = readiness.get(squad);
        // A change of state, not of the counts behind it: hunger and health move every few seconds.
        boolean changed = was == null || now.safety() != was.safety() || now.rating() != was.rating();
        Readiness seen = readinessSeen.put(squad, now);
        boolean same = seen != null && seen.safety() == now.safety() && seen.rating() == now.rating();
        int held = same ? readinessHeld.merge(squad, 1, Integer::sum) : 1;
        if (!same) {
            readinessHeld.put(squad, 1);
        }
        if (changed && (was == null || held >= READINESS_HOLDS)) {
            readiness.put(squad, now);
            LOG.info("[Readiness] {}: {}, C-{}{}", squad, now.safety(), now.rating(), now.shortfall().isEmpty() ? "" : " - " + now.shortfall());
        }
    }
}
