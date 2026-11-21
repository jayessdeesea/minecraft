package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseTrackerTest {

    private static final long DAY = PhaseTracker.DWELL_MILLIS;

    private static PhaseTracker.Readiness ready(double days) {
        return new PhaseTracker.Readiness(true, true, false, days, days, false);
    }

    /** Up a phase only once its standard has held a day. */
    @Test
    void aPhaseGoesUpOnceItsStandardHasHeldADay() {
        PhaseTracker tracker = new PhaseTracker();
        assertEquals(Phase.SEIZURE, tracker.update(ready(0), 0));
        assertEquals(Phase.SEIZURE, tracker.update(ready(0), DAY - 1), "secured and armed, not yet a day");
        assertEquals(Phase.CONSOLIDATION, tracker.update(ready(0), DAY));
        assertEquals(Phase.CONSOLIDATION, tracker.update(ready(3), 2 * DAY), "three days of supply: held from now");
        assertEquals(Phase.CONSOLIDATION, tracker.update(ready(2.9), 2 * DAY + 1), "dipped under: the day starts again");
        tracker.update(ready(3), 2 * DAY + 2);
        assertEquals(Phase.BUILDUP, tracker.update(ready(3.5), 3 * DAY + 2));
    }

    /** Down only below the lower bound, and not within a day of entering: no flapping between 1 and 3 days. */
    @Test
    void aPhaseFallsBackOnlyBelowItsLowerBoundAfterADay() {
        PhaseTracker tracker = new PhaseTracker();
        tracker.restore(Phase.BUILDUP, 0);
        assertEquals(Phase.BUILDUP, tracker.update(ready(2), DAY), "between 1 and 3 days: it holds");
        tracker.restore(Phase.BUILDUP, DAY);
        assertEquals(Phase.BUILDUP, tracker.update(ready(0.5), DAY + DAY / 2), "under a day's supply, but not a day in it yet");
        assertEquals(Phase.CONSOLIDATION, tracker.update(ready(0.5), 2 * DAY), "under a day's supply: back to consolidation");
    }

    /** A breach throws it back to the seizure at once. */
    @Test
    void aBreachIsTheSeizureAtOnce() {
        PhaseTracker tracker = new PhaseTracker();
        tracker.restore(Phase.BUILDUP, 0);
        assertEquals(Phase.SEIZURE, tracker.update(new PhaseTracker.Readiness(true, true, false, 5, 5, true), 1));
    }

    /** The day's dwell kept across a restart: up a phase a day after its standard first held, not a day after the restart. */
    @Test
    void theDwellHeldBeforeARestartCountsAfterIt() {
        PhaseTracker.Readiness ready = new PhaseTracker.Readiness(true, true, false, 0, 0, false);
        PhaseTracker before = new PhaseTracker();
        before.update(ready, 0);
        before.update(ready, 1000);

        PhaseTracker after = new PhaseTracker();
        after.restore(before.phase(), before.since(), before.upHeldSince());

        assertEquals(Phase.CONSOLIDATION, after.update(ready, PhaseTracker.DWELL_MILLIS));
    }

    /** Unmet a moment within the day, the phase still goes up a day after its standard first held; unmet over a tenth of it, not. */
    @Test
    void aMomentUnmetDoesntStartTheDayOver() {
        PhaseTracker.Readiness ready = new PhaseTracker.Readiness(true, true, false, 0, 0, false);
        PhaseTracker.Readiness unarmed = new PhaseTracker.Readiness(true, false, false, 0, 0, false);
        PhaseTracker brief = new PhaseTracker();
        brief.update(ready, 0);
        brief.update(unarmed, 10_000);
        brief.update(ready, 20_000);
        assertEquals(Phase.CONSOLIDATION, brief.update(ready, PhaseTracker.DWELL_MILLIS), "20 s unmet in a day");

        PhaseTracker long_ = new PhaseTracker();
        long_.update(ready, 0);
        long_.update(unarmed, 10_000);
        long_.update(unarmed, 10_000 + PhaseTracker.DWELL_MILLIS / 5);
        long_.update(ready, 20_000 + PhaseTracker.DWELL_MILLIS / 5);
        assertEquals(Phase.SEIZURE, long_.update(ready, PhaseTracker.DWELL_MILLIS), "a fifth of the day unmet: started over");
    }
}
