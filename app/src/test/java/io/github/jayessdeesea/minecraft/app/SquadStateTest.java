package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadStateTest {

    /** Spare hands stay off a dig while it goes - a block back and dug again isn't going - and all come back when it stalls or is done. */
    @Test
    void spareHandsComeBackWhenTheDigStallsOrIsDone() {
        SquadState state = new SquadState();
        state.digSpare.add("Hal");

        assertFalse(state.spareDigTill(3, 0, List.of("Dana", "Hal")));
        assertFalse(state.spareDigTill(2, 30_000, List.of("Dana", "Hal")));
        assertFalse(state.spareDigTill(3, 80_000, List.of("Dana", "Hal")), "a block back: not fallen, but not a minute since it last fell");
        assertEquals(Set.of("Hal"), state.digSpare, "kept off");
        assertTrue(state.spareDigTill(2, 91_000, List.of("Dana", "Hal")), "a minute without a new least: stalled");
        assertEquals(Set.of(), state.digSpare, "every hand back on");
        state.digSpare.add("Hal");
        assertFalse(state.spareDigTill(0, 92_000, List.of("Dana", "Hal")));
        assertEquals(Set.of(), state.digSpare, "done: none spare");
    }
}
