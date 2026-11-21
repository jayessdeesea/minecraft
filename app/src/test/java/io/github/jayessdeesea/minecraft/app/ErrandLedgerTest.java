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
}
