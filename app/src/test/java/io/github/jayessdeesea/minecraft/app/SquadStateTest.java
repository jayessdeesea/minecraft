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

    /**
     * Work is dealt to none the recovery drill has; a ground dealt in strips, to the whole squad when the drill has every
     * one of it.
     */
    @Test
    void workIsDealtToNoneTheRecoveryDrillHas() {
        SquadState state = new SquadState();
        io.github.jayessdeesea.minecraft.command.Report stuck = new io.github.jayessdeesea.minecraft.command.Report.Cantco("no route from (7, 81, 0) to (0, 64, 0)");
        java.util.function.BiFunction<String, io.github.jayessdeesea.minecraft.command.Report, io.github.jayessdeesea.minecraft.command.SoldierStatus> at =
                (name, report) -> new io.github.jayessdeesea.minecraft.command.SoldierStatus(name, new io.github.jayessdeesea.minecraft.command.Position(7.5, 81, 0.5),
                        new io.github.jayessdeesea.minecraft.command.Order.Idle(), report, java.util.Optional.empty());
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> one = List.of(at.apply("Dana", stuck), at.apply("Hal", new io.github.jayessdeesea.minecraft.command.Report.Idle()));
        List<io.github.jayessdeesea.minecraft.command.Order> idle = java.util.Collections.nCopies(2, new io.github.jayessdeesea.minecraft.command.Order.Idle());

        assertEquals(one, state.hands(one), "nobody in the drill: every hand");
        for (int report = 0; report < SoldierRunner.STRANDED_REPORTS; report++) {
            state.recovery.orders(one, idle, s -> java.util.Optional.empty());
        }
        assertEquals(List.of("Hal"), state.hands(one).stream().map(io.github.jayessdeesea.minecraft.command.SoldierStatus::name).toList());
        assertEquals(state.hands(one), state.crew(one));

        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> both = List.of(at.apply("Dana", stuck), at.apply("Hal", stuck));
        for (int report = 0; report < SoldierRunner.STRANDED_REPORTS; report++) {
            state.recovery.orders(both, idle, s -> java.util.Optional.empty());
        }
        assertEquals(List.of(), state.hands(both), "the drill has both: no hands");
        assertEquals(both, state.crew(both), "a ground in strips is still the whole squad's");
    }
}
