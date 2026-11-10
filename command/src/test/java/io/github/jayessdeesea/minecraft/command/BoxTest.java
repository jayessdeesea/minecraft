package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoxTest {

    @Test
    void aBoxIsInclusiveAndReadsAsTheSquadLeaderWouldSayIt() {
        Box box = new Box(0, 63, 0, 31, 63, 31);

        assertTrue(box.contains(31, 63, 0));
        assertEquals(1024, box.volume());
        assertEquals("x=0..31, y=63..63, z=0..31", box.toString());
    }

    @Test
    void itSplitsIntoAStripPerSoldierAlongItsLongerSide() {
        List<Box> strips = new Box(0, 64, 0, 31, 88, 7).strips(4);

        assertEquals(List.of(new Box(0, 64, 0, 7, 88, 7), new Box(8, 64, 0, 15, 88, 7),
                new Box(16, 64, 0, 23, 88, 7), new Box(24, 64, 0, 31, 88, 7)), strips);
        assertEquals(2, new Box(0, 64, 0, 1, 64, 0).strips(4).size(), "no more strips than columns");
    }

    @Test
    void anEmptyBoxIsAFault() {
        assertThrows(IllegalArgumentException.class, () -> new Box(5, 0, 0, 4, 0, 0));
    }
}
