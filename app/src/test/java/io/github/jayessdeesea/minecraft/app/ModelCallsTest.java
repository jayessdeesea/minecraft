package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCallsTest {

    @Test
    void countsWhatsInFlightAndShedsRoutineDecisionsOnceBackedUp() {
        ModelCalls calls = new ModelCalls();
        ModelCalls.Ticket general = calls.queued("General");
        ModelCalls.Ticket alpha = calls.queued("Alpha's squad leader");

        assertEquals(2, calls.inFlight());
        assertFalse(calls.shed("Bravo's squad leader"), "two in flight: room for a routine one");
        ModelCalls.Ticket bravo = calls.queued("Bravo's squad leader");
        assertTrue(calls.shed("Charlie's squad leader"), "three in flight: a routine one waits");

        general.started();
        general.finished();
        alpha.finished(); // failed before it started: still out of flight
        bravo.started();
        bravo.finished();
        assertEquals(0, calls.inFlight());
    }
}
