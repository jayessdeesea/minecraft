package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    private static final Position SITE = new Position(8, 16, 8);

    private static SoldierStatus carrying(int logs) {
        return new SoldierStatus("Bob", new Position(0, 64, 0), new Order.Idle(), new Report.Idle(), Optional.of(new Condition(20, 20, 0,
                Optional.of(new Kit(Optional.empty(), 0, 0, 0, logs, 0, 0, false, false, 0, false)))));
    }

    @Test
    void aRiflemanWithNothingDirectedIsInTheMainBody() {
        assertEquals("Alpha", Plan.RIFLEMAN.unit("Alpha", "Bob"));
        assertEquals(Optional.empty(), Plan.RIFLEMAN.objective("hold"));
    }

    @Test
    void aRoleIsItsSquadsDetachmentForThatRoleWorkingItsTaskAtItsSite() {
        Plan miner = Plan.RIFLEMAN.with(Plan.Change.role("Bob", Role.MINER, SITE));
        assertEquals("Alpha/miner", miner.unit("Alpha", "Bob"));
        Objective objective = miner.objective("ore for the camp").orElseThrow();
        assertEquals(SITE, objective.location());
        assertEquals(new Objective.Task.Mine(), objective.task());
        assertEquals("ore for the camp", objective.intent());
    }

    @Test
    void aDirectiveMakesADetachmentOfOneUntilItsDoneThenItsRoleIsBack() {
        Directive fetch = new Directive.Fetch(Resource.WOOD, 4, new Position(12, 64, 0));
        Plan plan = Plan.RIFLEMAN.with(Plan.Change.role("Bob", Role.MINER, SITE)).with(Plan.Change.directives("Bob", List.of(fetch)));
        assertEquals("Alpha/Bob", plan.unit("Alpha", "Bob"));
        assertEquals(new Objective.Task.Gather(Resource.WOOD), plan.objective("").orElseThrow().task());
        assertFalse(fetch.doneBy(carrying(3)));
        assertTrue(fetch.doneBy(carrying(4)));
        assertEquals("Alpha/miner", plan.done().unit("Alpha", "Bob"));
    }

    @Test
    void aGoToHoldsUntilItsReplaced() {
        Directive goTo = new Directive.GoTo(new Position(0, 64, 0));
        assertFalse(goTo.doneBy(carrying(0)), "standing on the point");
        Plan plan = Plan.RIFLEMAN.with(Plan.Change.directives("Bob", List.of(goTo)));
        assertEquals(Plan.RIFLEMAN, plan.with(Plan.Change.directives("Bob", List.of())), "cancelled");
    }

    @Test
    void oneWithARoleGoesOnToItsWorkOnceItsThere() {
        Directive goTo = new Directive.GoTo(new Position(0, 64, 0));
        Plan miner = Plan.RIFLEMAN.with(Plan.Change.role("Bob", Role.MINER, SITE)).with(Plan.Change.directives("Bob", List.of(goTo)));
        assertTrue(miner.currentDoneBy(carrying(0)), "at the point, the miner goes on to mine");
        Plan rifleman = Plan.RIFLEMAN.with(Plan.Change.directives("Bob", List.of(goTo)));
        assertFalse(rifleman.currentDoneBy(carrying(0)), "a rifleman holds");
        Plan away = Plan.RIFLEMAN.with(Plan.Change.role("Bob", Role.MINER, SITE))
                .with(Plan.Change.directives("Bob", List.of(new Directive.GoTo(new Position(30, 64, 0)))));
        assertFalse(away.currentDoneBy(carrying(0)), "not there yet");
    }

    @Test
    void aChangeLeavesWhatItDoesntSay() {
        Directive goTo = new Directive.GoTo(new Position(0, 64, 0));
        Plan plan = Plan.RIFLEMAN.with(Plan.Change.directives("Bob", List.of(goTo))).with(Plan.Change.role("Bob", Role.FARMER, SITE));
        assertEquals(Role.FARMER, plan.role());
        assertEquals(List.of(goTo), plan.directives());
        assertEquals(Plan.RIFLEMAN, plan.with(Plan.Change.role("Bob", Role.RIFLEMAN, SITE)).with(Plan.Change.directives("Bob", List.of())),
                "a rifleman has no site");
    }

    @Test
    void aRoleNeedsASiteAndAFetchWhatAKitCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Plan(Role.MINER, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Directive.Fetch(Resource.FLINT, 4, SITE));
        assertThrows(IllegalArgumentException.class, () -> new Directive.Fetch(Resource.WOOD, 0, SITE));
        assertThrows(IllegalArgumentException.class, () -> new Directive.Level(0, 0, 32, 4, 64));
    }

    @Test
    void aLevelIsWorkedFromItsMiddleAtItsHeight() {
        assertEquals(new Position(13, 64, 0.5), new Directive.Level(9, -2, 16, 2, 64).where());
    }
}
