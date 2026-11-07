package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

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
}
