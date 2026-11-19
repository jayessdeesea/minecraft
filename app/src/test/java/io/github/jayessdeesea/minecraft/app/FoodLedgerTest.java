package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodLedgerTest {

    private static Optional<Condition> at(double health, int food) {
        return Optional.of(new Condition(health, food, 0, Optional.empty()));
    }

    /** A loaf eaten, health healed, a jump and a sprint and a blow: each burnt at the server's rate, the loaf with its saturation. */
    @Test
    void aSoldiersDayIsWhatItAteAgainstWhatItBurntOnWhat() {
        FoodLedger ledger = new FoodLedger();
        ledger.sample("Alice", at(10, 14), new Position(0, 64, 0), false, true);
        ledger.sample("Alice", at(10, 19), new Position(0, 64, 0), false, true); // a loaf: +5
        ledger.sample("Alice", at(20, 19), new Position(0, 64, 0), false, true); // healed 10
        ledger.sample("Alice", at(20, 19), new Position(0, 64.4, 0), false, false); // a jump
        ledger.sample("Alice", at(20, 19), new Position(0, 64, 0), true, true);
        ledger.sample("Alice", at(20, 19), new Position(40, 64, 0), true, true); // 40 m sprinted
        ledger.sample("Alice", at(16, 19), new Position(40, 64, 0), false, true); // a hit taken
        ledger.attack("Alice");

        FoodLedger.Day day = ledger.days().getFirst();

        assertEquals(11, day.eaten(), 1e-9, "a loaf: 5 hunger and 6 saturation");
        assertEquals(4, day.hurt(), 1e-9);
        assertEquals(10 * 6.0 / 4, day.healing(), 1e-9, "6 exhaustion a health point");
        assertEquals(0.05 / 4, day.jumping(), 1e-9);
        assertEquals(40 * 0.1 / 4, day.sprinting(), 1e-9);
        assertEquals((0.1 + 0.1) / 4, day.fighting(), 1e-9, "the blow struck and the hit taken");
        assertEquals(0, ledger.days().getFirst().eaten(), "the day started again");
    }

    /** A fall off a ledge isn't a jump: only a take-off that rises. */
    @Test
    void aFallIsNotAJump() {
        FoodLedger ledger = new FoodLedger();
        ledger.sample("Bob", at(20, 20), new Position(0, 64, 0), false, true);
        ledger.sample("Bob", at(20, 20), new Position(1, 63.5, 0), false, false);
        assertEquals(0, ledger.days().getFirst().jumping());
    }

    /** The review says the force's totals first, then each soldier, most eaten first. */
    @Test
    void theReviewLeadsWithTheForceThenWhoAteMost() {
        FoodLedger ledger = new FoodLedger();
        ledger.sample("Alice", at(20, 10), new Position(0, 64, 0), false, true);
        ledger.sample("Alice", at(20, 15), new Position(0, 64, 0), false, true);
        ledger.sample("Bob", at(20, 10), new Position(0, 64, 0), false, true);
        ledger.sample("Bob", at(20, 20), new Position(0, 64, 0), false, true);

        List<String> review = ledger.review();

        assertTrue(review.getFirst().startsWith("[Food] the force ate 3 loaves"), review.getFirst());
        assertTrue(review.get(1).startsWith("[Food] Bob ate 2.0 loaves"), review.get(1));
        assertTrue(review.get(2).startsWith("[Food] Alice ate 1.0 loaves"), review.get(2));
    }
}
