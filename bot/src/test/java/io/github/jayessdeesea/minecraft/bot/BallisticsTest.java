package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticsTest {

    private static final Position ARCHER = new Position(0.5, 64, 0.5);
    private static final Position MOB = new Position(12.5, 64, 0.5);

    /** A squadmate between the archer and its mark holds the arrow: live (field463), Ned killed by Alice's. */
    @Test
    void aSquadmateInTheLineHoldsTheShot() {
        assertTrue(Ballistics.inTheLine(ARCHER, MOB, List.of(new Position(6.5, 64, 0.8))));
    }

    /** One well to the side, or behind the mark, or behind the archer, doesn't. */
    @Test
    void oneOffTheLineDoesnt() {
        assertFalse(Ballistics.inTheLine(ARCHER, MOB, List.of(new Position(6.5, 64, 4.5))), "to the side");
        assertFalse(Ballistics.inTheLine(ARCHER, MOB, List.of(new Position(16.5, 64, 0.5))), "beyond the mark");
        assertFalse(Ballistics.inTheLine(ARCHER, MOB, List.of(new Position(-3.5, 64, 0.5))), "behind the archer");
    }
}
