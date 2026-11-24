package io.github.jayessdeesea.minecraft.bot.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The columns a body's box overlaps, as the hand-written copies had them. */
class FootprintTest {

    @Test
    void aBodyAtAColumnsMiddleIsInThatColumnAlone() {
        assertEquals(new Footprint(33, 33, 34, 34), Footprint.of(33.5, 34.5));
    }

    @Test
    void aBodyOverAFaceIsInBothColumns() {
        assertEquals(new Footprint(32, 33, 34, 35), Footprint.of(33.2, 34.8));
        assertEquals(new Footprint(-1, 0, -2, -1), Footprint.of(0.1, -1.1), "west and north of the origin too");
    }

    /** Up against a face: not in the column beyond; a hair over counts on the low side only, the behaviour kept. */
    @Test
    void aBodyAgainstAFaceIsNotInTheColumnBeyond() {
        assertEquals(new Footprint(33, 33, 34, 34), Footprint.of(33.3, 34.7), "its west and south faces on the columns' faces");
        assertEquals(new Footprint(32, 33, 34, 34), Footprint.of(33.29999999999999, 34.70000000000001), "a hair over its high side is none; over its low side it is over - as it was");
        assertEquals(new Footprint(32, 33, 34, 34), Footprint.of(33.299, 34.5), "a thousandth over is over");
    }
}
