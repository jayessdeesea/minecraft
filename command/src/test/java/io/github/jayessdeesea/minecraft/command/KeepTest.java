package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeepTest {

    /**
     * Short of logs between the builders and the stockpile, the keep's Class IV is a request to the
     * general, naming what can't be made; with enough, nothing is said. Live, the builders worked a
     * day with no tree within reach, no chest and no pickaxe, and nobody was asked for logs.
     */
    @Test
    void theKeepsBuildersAskForLogsWhenTheyAndTheStockpileAreShort() {
        String line = Keep.classFour(1, 0, false).orElseThrow();
        assertTrue(line.startsWith("Class IV RED - request:"), line);
        assertTrue(line.contains("1 log between them") && line.contains("no chest at the keep's depot") && line.contains("WOOD"), line);
        assertTrue(!Keep.classFour(3, 0, true).orElseThrow().contains("chest"), "the chest stands: the pickaxes and torches are the want");
        assertTrue(Keep.classFour(6, 6, false).isPresent(), "a dozen logs isn't a depot's: chests, a table, handles");
        assertEquals(Optional.empty(), Keep.classFour(8, 8, false), "enough between them and the stockpile");
        assertEquals(Optional.empty(), Keep.classFour(Keep.LOGS_WANTED, 0, false));
    }
}
