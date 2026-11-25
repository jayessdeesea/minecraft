package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chunks the simulated server sends one soldier: its view where it joins, then what comes into it, each once. */
class SimChunksTest {

    @Test
    void joiningItIsSentTheSquareRoundItsChunk() {
        List<int[]> sent = new SimChunks().comeIntoView(-0.5, 20.5, 3);

        assertEquals(49, sent.size());
        assertTrue(sent.stream().allMatch(c -> c[0] >= -4 && c[0] <= 2 && c[1] >= -2 && c[1] <= 4), "round chunk (-1, 1)");
    }

    @Test
    void walkingInItsChunkItIsSentNothingAndCrossingALineTheRowAhead() {
        SimChunks served = new SimChunks();
        served.comeIntoView(8.5, 8.5, 3);

        assertEquals(List.of(), served.comeIntoView(15.9, 0.1, 3), "the same chunk");
        List<int[]> east = served.comeIntoView(16.1, 0.1, 3);
        assertEquals(7, east.size(), "a row of seven");
        assertTrue(east.stream().allMatch(c -> c[0] == 4), "four chunks east of where it joined");
        assertEquals(List.of(), served.comeIntoView(15.9, 0.1, 3), "back over the line: all of it sent already");
        assertEquals(List.of(), served.comeIntoView(16.1, 0.1, 3), "and over again");
    }
}
