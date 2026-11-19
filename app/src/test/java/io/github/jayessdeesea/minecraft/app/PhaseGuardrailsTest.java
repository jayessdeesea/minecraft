package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Phase;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseGuardrailsTest {

    private static final Objective MINE = new Objective("Mine at the stairs", new Position(0, 12, 0), "ore", new Objective.Task.Mine());
    private static final Objective FARM = new Objective("Farm the camp's plots", new Position(-114, 64, 66), "wheat", new Objective.Task.Farm());

    private static SquadStatus standing(Objective objective, Optional<Progress> progress) {
        return new SquadStatus("Charlie", new Report.InPosition(), List.of(), List.of(), Optional.of(objective), false, OptionalInt.empty(), progress);
    }

    /** Before the buildup a MINE is the food work instead; from the buildup it stands. */
    @Test
    void noMiningBeforeTheBuildup() {
        assertEquals(FARM, PhaseGuardrails.noMineBeforeBuildup(MINE, Phase.SEIZURE, "Alpha", Optional.of(FARM)));
        assertEquals(FARM, PhaseGuardrails.noMineBeforeBuildup(MINE, Phase.CONSOLIDATION, "Alpha", Optional.of(FARM)));
        assertEquals(MINE, PhaseGuardrails.noMineBeforeBuildup(MINE, Phase.BUILDUP, "Alpha", Optional.of(FARM)));
        assertEquals(FARM, PhaseGuardrails.noMineBeforeBuildup(FARM, Phase.SEIZURE, "Alpha", Optional.empty()), "only a MINE is turned");
    }

    /** The same task a little way off keeps the squad where it works; far off, another task, or stalled, the new one stands. */
    @Test
    void aSquadOnATaskKeepsItsSpot() {
        Objective nearby = new Objective("Farm the camp's plots", new Position(-87, 63, 58), "wheat", new Objective.Task.Farm());
        Objective farOff = new Objective("Farm the camp's plots", new Position(40, 63, 58), "wheat", new Objective.Task.Farm());
        assertEquals(FARM, PhaseGuardrails.steady(nearby, standing(FARM, Optional.empty())));
        assertEquals(farOff, PhaseGuardrails.steady(farOff, standing(FARM, Optional.empty())));
        assertEquals(MINE, PhaseGuardrails.steady(MINE, standing(FARM, Optional.empty())));
        Objective mineOn = new Objective("Mine at the stairs", new Position(4, 12, 4), "ore", new Objective.Task.Mine());
        assertEquals(mineOn, PhaseGuardrails.steady(mineOn, standing(MINE, Optional.empty())), "a mine's point moves on with the work");
        assertEquals(nearby, PhaseGuardrails.steady(nearby, standing(FARM, Optional.of(new Progress(Progress.State.STALLED, "nobody at it")))));
    }

    /** Holding where sent by day with a task to do is without work; sheltering, in the patrol base or down the mine by night isn't. */
    @Test
    void holdingWithATaskToDoIsWithoutWork() {
        assertTrue(IdleWatch.withoutWork(true, Optional.of(FARM), false));
        assertFalse(IdleWatch.withoutWork(false, Optional.of(FARM), false), "working");
        assertFalse(IdleWatch.withoutWork(true, Optional.of(FARM), true), "down the mine for the night");
        assertFalse(IdleWatch.withoutWork(true, Optional.of(new Objective("Shelter", new Position(0, 64, 0), "", new Objective.Task.None())), false));
        assertFalse(IdleWatch.withoutWork(true, Optional.empty(), false));
        IdleWatch watch = new IdleWatch("without work");
        assertEquals(Optional.empty(), watch.update("Alice", new Position(0, 64, 0), 0, false, () -> ""));
        assertTrue(watch.update("Alice", new Position(0, 64, 0), IdleWatch.IDLE_MILLIS, false, () -> "order Idle").orElseThrow()
                .startsWith("Alice without work 60 s"));
    }
}
