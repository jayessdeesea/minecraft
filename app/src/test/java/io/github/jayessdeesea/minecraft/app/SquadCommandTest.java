package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadCommandTest {

    private final SquadCommand squad = new SquadCommand("Alpha", (SquadLeader) Proxy.newProxyInstance(SquadLeader.class.getClassLoader(),
            new Class<?>[]{SquadLeader.class}, (proxy, method, args) -> method.getName().equals("report") ? new Report.Idle() : null),
            line -> { }, new ModelCalls());

    private static List<SoldierStatus> soldiers(Report alice) {
        return List.of(new SoldierStatus("Alice", new Position(0, 64, 0), new Order.Idle(), alice, Optional.empty()),
                new SoldierStatus("Bob", new Position(2, 64, 0), new Order.Idle(), new Report.Moving(), Optional.empty()));
    }

    @Test
    void aContactIsReportedUpOnceWhenItStartsNotEverySitrepWhileItLasts() {
        assertFalse(squad.newContact(soldiers(new Report.Moving())));
        assertTrue(squad.newContact(soldiers(new Report.InContact(2))), "contact: the general hears of it now");
        assertFalse(squad.newContact(soldiers(new Report.InContact(1))), "the same fight, still going");
        assertFalse(squad.newContact(soldiers(new Report.InPosition())));
        assertTrue(squad.newContact(soldiers(new Report.InContact(1))), "a new contact after the last ended");
    }

    @Test
    void aSquadEveryOneOfWhoseSoldiersHasAnotherJobHasNobodyFreeForItsTask() {
        List<Role> detached = List.of(Role.MINER, Role.FARMER, Role.SENTRY);
        assertTrue(SquadCommand.noneFree(detached, new Objective.Task.Fortify()));
        // A soldier on the task's own job does it; a rifleman goes with the squad.
        assertFalse(SquadCommand.noneFree(List.of(Role.MINER, Role.MASON), new Objective.Task.Fortify()));
        assertFalse(SquadCommand.noneFree(List.of(Role.MINER, Role.RIFLEMAN), new Objective.Task.Fortify()));
        assertFalse(SquadCommand.noneFree(List.of(Role.FARMER, Role.QUARRYMAN), new Objective.Task.Gather(Resource.STONE)));
        assertTrue(SquadCommand.noneFree(List.of(Role.FARMER, Role.QUARRYMAN), new Objective.Task.Gather(Resource.WOOD)));
        // Everyone comes in for the night, and a plain move takes the squad's leader there.
        assertFalse(SquadCommand.noneFree(detached, new Objective.Task.PatrolBase()));
        assertFalse(SquadCommand.noneFree(detached, new Objective.Task.None()));
        assertFalse(SquadCommand.noneFree(List.of(), new Objective.Task.Fortify()));
    }

    @Test
    void theCantcoNamesEachSoldiersJob() {
        Objective wall = new Objective("Wall the camp in", new Position(0, 64, 0), "Nothing walks in", new Objective.Task.Fortify());
        assertEquals(new Report.Cantco("no soldier free for \"Wall the camp in\": every soldier has a standing job of its own "
                        + "(Bob farmer, Dana miner)"),
                SquadCommand.noneFree(Map.of("Dana", Role.MINER, "Bob", Role.FARMER), wall));
    }
}
