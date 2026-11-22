package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A trip that walked far for little is said, with where it went; one that paid isn't (operator, 2026-11-21). */
class ErrandLedgerTest {

    @Test
    void aBakeRunOfOneLoafThirtyBlocksAwayIsSaidWithWhereItWent() {
        ErrandLedger ledger = new ErrandLedger();
        List<String> lines = new ArrayList<>();
        Map<String, Integer> before = Map.of("wheat", 5, "food", 15);
        Map<String, Integer> after = Map.of("wheat", 2, "food", 16);
        long now = 0;
        lines.addAll(ledger.tick("Ned", Optional.empty(), new Position(3, 63, 95), before, now));
        // Field615: Ned from the farm at (3, 95) to the table at (28, 88) and back, for one loaf.
        for (int x = 3; x <= 28; x++, now += 250) {
            lines.addAll(ledger.tick("Ned", Optional.of("ARMOURER"), new Position(x, 63, 95), before, now));
        }
        for (int x = 28; x >= 3; x--, now += 250) {
            lines.addAll(ledger.tick("Ned", Optional.of("ARMOURER"), new Position(x, 63, 95), after, now));
        }
        lines.addAll(ledger.tick("Ned", Optional.empty(), new Position(3, 63, 95), after, now));
        assertEquals(1, lines.size(), lines.toString());
        List<String> review = ledger.review();
        assertTrue(review.contains("  unpaid: Ned's ARMOURER 1 times, last out to (28, 63, 95), 25 out"), "at dawn, up the chain: " + review);
        assertTrue(lines.getFirst().startsWith("Ned's ARMOURER: 50 blocks walked") && lines.getFirst().contains("for 3 item(s) moved (+1 food, -3 wheat)")
                && lines.getFirst().endsWith("from (3, 63, 95), farthest (28, 63, 95), 25 blocks out"), lines.getFirst());
    }

    @Test
    void aTripThatPaysIsNotSaid() {
        ErrandLedger ledger = new ErrandLedger();
        List<String> lines = new ArrayList<>();
        long now = 0;
        for (int x = 0; x <= 50; x++, now += 250) {
            lines.addAll(ledger.tick("Kate", Optional.of("DEPOSITOR"), new Position(x, 63, 0), Map.of("cobblestone", 64), now));
        }
        lines.addAll(ledger.tick("Kate", Optional.empty(), new Position(50, 63, 0), Map.of(), now));
        assertEquals(List.of(), lines, "64 put away for 50 blocks");
        assertEquals(64, ErrandLedger.moved(Map.of("cobblestone", 64), Map.of()));
    }

    /** What a crafting spell uses up is consumed; what a deposit carries off isn't (field641: deposits read as burn). */
    @Test
    void whatACraftUsesIsConsumedAndADepositIsNot() {
        ErrandLedger ledger = new ErrandLedger();
        Position at = new Position(0, 63, 0);
        ledger.tick("Ned", Optional.of("ARMOURER"), at, Map.of("log", 5), 0);
        ledger.tick("Ned", Optional.empty(), at, Map.of("log", 2, "planks", 12), 50);
        ledger.tick("Ned", Optional.of("DEPOSITOR"), at, Map.of("log", 2, "planks", 12), 100);
        ledger.tick("Ned", Optional.empty(), at, Map.of(), 150);
        assertEquals(3, ledger.consumed("log"), "three logs crafted into planks; the rest put away, not burnt");
        assertEquals(0, ledger.consumed("planks"));
    }

    /** Rations eaten with hands free are used up too; a routine's spell isn't counted twice (field644: burn 2 a day). */
    @Test
    void rationsEatenBetweenRoutinesAreUsedUp() {
        ErrandLedger ledger = new ErrandLedger();
        Position at = new Position(0, 63, 0);
        ledger.tick("Ned", Optional.empty(), at, Map.of("food", 10), 0);
        ledger.tick("Ned", Optional.empty(), at, Map.of("food", 9), 50);
        ledger.tick("Ned", Optional.of("FARMER"), at, Map.of("food", 9), 100);
        ledger.tick("Ned", Optional.empty(), at, Map.of("food", 8), 150);
        ledger.tick("Ned", Optional.empty(), at, Map.of("food", 7), 200);
        assertEquals(3, ledger.consumed("food"), "one eaten free, one in the farmer's spell, one free after it");
    }

    /**
     * What a routine makes is counted as it comes, tick by tick - a duty's yield (command.ShareWatch) - put to the routine
     * whose its hands were; a draw's or a deposit's isn't made, and the time its hands were each routine's is kept.
     */
    @Test
    void whatARoutineMakesIsCountedAsItComesAndATransferIsNot() {
        ErrandLedger ledger = new ErrandLedger();
        Position at = new Position(0, 63, 0);
        ledger.tick("Dana", Optional.of("DEPOSITOR"), at, Map.of("food", 64), 0);
        ledger.tick("Dana", Optional.of("ARMOURER"), at, Map.of("food", 64, "wheat", 48), 50);
        ledger.tick("Dana", Optional.of("ARMOURER"), at, Map.of("food", 72, "wheat", 24), 100);
        assertEquals(0, ledger.made("Dana", "DEPOSITOR", "wheat"), "drawn, not made");
        assertEquals(8, ledger.made("Dana", "ARMOURER", "food"), "counted mid-spell, not at its end");
        assertEquals(24, ledger.spent("Dana", "ARMOURER", "wheat"));
        ledger.tick("Dana", Optional.empty(), at, Map.of("food", 80), 150);
        ledger.tick("Dana", Optional.empty(), at, Map.of("food", 90), 100 + ErrandLedger.PICKUP_MILLIS + 1);
        assertEquals(16, ledger.made("Dana", "ARMOURER", "food"), "picked up just after its hands came free is the routine's; free a while, nobody's");
        assertEquals(100, ledger.held("Dana", "ARMOURER"));
        assertEquals(ErrandLedger.PICKUP_MILLIS + 1 - 50, ledger.held("Dana", ErrandLedger.FREE));
    }
}
