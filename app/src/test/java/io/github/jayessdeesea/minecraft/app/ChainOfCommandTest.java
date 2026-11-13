package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
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
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainOfCommandTest {

    private final ChainOfCommand chain = new ChainOfCommand(
            (General) Proxy.newProxyInstance(General.class.getClassLoader(), new Class<?>[]{General.class}, (proxy, method, args) -> null),
            ChatClient.create((ChatModel) prompt -> {
                throw new AssertionError("no model call expected");
            }), new SquadLeaderPrompts("", ""),
            new Roster("Alpha:Alice"), true);

    private static final Objective WALL = new Objective("Wall the camp in", new Position(10, 64, 10), "Nothing walks in",
            new Objective.Task.Fortify());

    @Test
    void underTheKeepRuleEverySquadBuildsTheKeepByDayAndShelterAtNight() {
        chain.setKeepByDay(true);
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        SquadStatus alpha = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);
        chain.onTime(TimeOfDay.of(1000, true));

        Objective byDay = chain.byDay(WALL, alpha);

        assertTrue(byDay.task() instanceof Objective.Task.Keep, byDay.toString());
        assertEquals(byDay, chain.byDay(byDay, alpha), "a squad on the keep is kept on it");
        chain.onTime(TimeOfDay.of(18000, true));
        assertEquals(WALL, chain.byDay(WALL, alpha), "the night is the night rule's");
    }

    @Test
    void atNightASquadSealedInIsKeptInNotOrderedOut() {
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(18000, true));
        SquadStatus sealed = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), true);

        Objective given = chain.sheltered(WALL, sealed);

        assertTrue(given.patrolBase(), given.toString());
        assertEquals(new Position(0, 64, 0), given.location());
    }

    @Test
    void atNightEverySquadGoesDownTheCampsMineAndComesUpBeforeSunrise() {
        ChainOfCommand two = new ChainOfCommand(
                (General) Proxy.newProxyInstance(General.class.getClassLoader(), new Class<?>[]{General.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true);
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(18000, true));
        two.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));

        Objective alpha = two.sheltered(WALL, squad("Alpha", "Alice"));
        Objective bravo = two.sheltered(WALL, squad("Bravo", "Bob"));

        assertTrue(alpha.mine(), alpha.toString());
        assertTrue(bravo.mine(), bravo.toString());
        assertEquals(new Position(-44, ChainOfCommand.MINE_DEPTH, 40), bravo.location());
        assertEquals(bravo, two.sheltered(bravo, squad("Bravo", "Bob")), "a squad down the mine stays there");
        Objective hut = new Objective("Build a patrol base", new Position(0, 64, 0), "Safe", new Objective.Task.PatrolBase());
        assertEquals(bravo, two.sheltered(hut, squad("Bravo", "Bob")), "the general's patrol base doesn't take it off the night shift");

        two.setKeepByDay(true);
        assertEquals(ChainOfCommand.KEEP_MINE, two.sheltered(WALL, squad("Bravo", "Bob")).location(),
                "building the keep, the night's mine is the keep's own, beside it");
        two.onTime(TimeOfDay.of(TimeOfDay.DAWN - ChainOfCommand.SURFACE_LEAD_TICKS + 1, true));
        Objective climbingOut = two.sheltered(two.byDay(WALL, squad("Bravo", "Bob")), squad("Bravo", "Bob"));
        assertTrue(climbingOut.task() instanceof Objective.Task.Keep, "just before sunrise it climbs out to the day's work: " + climbingOut);
    }

    private static SquadStatus squad(String name, String soldier) {
        return new SquadStatus(name, new Report.InPosition(),
                List.of(new SoldierStatus(soldier, new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);
    }
}
