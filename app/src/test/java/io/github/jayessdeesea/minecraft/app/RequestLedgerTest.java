package io.github.jayessdeesea.minecraft.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequestLedgerTest {

    private final RequestLedger ledger = new RequestLedger();

    @Test
    void aRequestIsRaisedOnceAndItsAnswerTimed() {
        assertEquals(Optional.of("[Request] Alpha -> General: Class I RED"),
                ledger.raise("Alpha:food", RequestLedger.Echelon.GENERAL, "Alpha", "Class I RED", 0));
        assertEquals(Optional.empty(), ledger.raise("Alpha:food", RequestLedger.Echelon.GENERAL, "Alpha", "Class I RED", 5_000),
                "still the same request");
        assertEquals(Optional.of("[Request] General answered Alpha in 90 s: Class I RED"), ledger.answer("Alpha:food", 90_000));
        assertEquals(Optional.empty(), ledger.answer("Alpha:food", 91_000));
    }

    @Test
    void oneUnansweredPastItsDeadlineAlarmsOnce() {
        ledger.raise("Bob", RequestLedger.Echelon.SQUAD_LEADER, "Bob", "CANTCO: no route", 0);

        assertEquals(List.of(), ledger.alarms(59_000));
        assertEquals(List.of("[Request] ALARM: Bob's request to squad leader unanswered after 61 s: CANTCO: no route"), ledger.alarms(61_000));
        assertEquals(List.of(), ledger.alarms(120_000), "said once");
    }

    @Test
    void theReviewGivesEachEchelonsAnswersAndWhatsStillOpen() {
        ledger.raise("a", RequestLedger.Echelon.GENERAL, "Alpha", "task DONE", 0);
        ledger.answer("a", 30_000);
        ledger.raise("b", RequestLedger.Echelon.GENERAL, "Bravo", "task STALLED", 0);
        ledger.answer("b", 150_000);
        ledger.raise("c", RequestLedger.Echelon.SQUAD_LEADER, "Carol", "CANTCO", 0);
        ledger.settle("c");
        ledger.raise("d", RequestLedger.Echelon.GENERAL, "Alpha", "Class I RED", 100_000);
        ledger.alarms(500_000);

        List<String> review = ledger.review(500_000);

        assertTrue(review.contains("  General: 2 answered (median 150 s, longest 150 s), 0 settled by themselves, 1 alarms, 1 open"), review.toString());
        assertTrue(review.contains("    open 400 s: Alpha - Class I RED"), review.toString());
        assertTrue(review.contains("  squad leader: 0 answered, 1 settled by themselves, 0 alarms, 0 open"), review.toString());
    }
}
