package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads of main's shared ref: an absent one is no answer, read again and counted; a present one starts the count
 * again; five absent in a row and the reader gives up.
 */
class MainReadsTest {

    @Test
    void anAbsentReadIsCountedAndReadAgain() {
        MainReads reads = MainReads.NONE.read(false);
        assertEquals(1, reads.absent());
        assertFalse(reads.givenUp());
        assertEquals("main's ref read absent (1 of 5); reading again", reads.said());
    }

    @Test
    void aPresentReadStartsTheCountAgain() {
        MainReads reads = MainReads.NONE.read(false).read(false).read(false).read(false).read(true);
        assertEquals(MainReads.NONE, reads);
        assertFalse(reads.read(false).givenUp());
        assertEquals(1, reads.read(false).absent());
    }

    @Test
    void fiveAbsentInARowGivesUp() {
        MainReads reads = MainReads.NONE;
        for (int i = 1; i <= 4; i++) {
            reads = reads.read(false);
            assertFalse(reads.givenUp(), i + " absent");
        }
        reads = reads.read(false);
        assertTrue(reads.givenUp());
        assertEquals("main's ref read absent 5 of 5 in a row", reads.said());
    }

    @Test
    void presentReadsNeverGiveUp() {
        MainReads reads = MainReads.NONE;
        for (int i = 0; i < 10; i++) {
            reads = reads.read(true);
        }
        assertEquals(0, reads.absent());
        assertFalse(reads.givenUp());
    }
}
