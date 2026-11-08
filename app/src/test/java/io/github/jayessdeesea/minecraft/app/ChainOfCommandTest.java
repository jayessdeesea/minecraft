package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainOfCommandTest {

    private final ChainOfCommand chain = new ChainOfCommand(
            (General) Proxy.newProxyInstance(General.class.getClassLoader(), new Class<?>[]{General.class}, (proxy, method, args) -> null),
            ChatClient.create((org.springframework.ai.chat.model.ChatModel) prompt -> {
                throw new AssertionError("no model call expected");
            }), new SquadLeaderPrompts("", ""),
            new Roster("Alpha:Alice"), true);

    private static final Objective WALL = new Objective("Wall the camp in", new Position(10, 64, 10), "Nothing walks in",
            new Objective.Task.Fortify());

    @Test
    void atNightASquadSealedInIsKeptInNotOrderedOut() {
        chain.setGround((x, z, nearY) -> java.util.OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(18000, true));
        SquadStatus sealed = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), true);

        Objective given = chain.sheltered(WALL, sealed);

        assertTrue(given.patrolBase(), given.toString());
        assertEquals(new Position(0, 64, 0), given.location());
    }
}
