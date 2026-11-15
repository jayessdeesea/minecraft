package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Resource;
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
            (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
            ChatClient.create((ChatModel) prompt -> {
                throw new AssertionError("no model call expected");
            }), new SquadLeaderPrompts("", ""),
            new Roster("Alpha:Alice"), true, "model");

    private static final Objective WALL = new Objective("Wall the camp in", new Position(10, 64, 10), "Nothing walks in",
            new Objective.Task.Fortify());

    /**
     * From a squad down a mine, the ground near its height at the objective's column is the shaft's
     * floor: the objective goes on the surface. Live, a wood-gathering objective at the keep mine's
     * head was put at the shaft's bottom, 40 blocks down, and the squad found no tree there.
     */
    @Test
    void anObjectiveOverAMineGoesOnTheSurfaceNotTheShaftsFloor() {
        chain.setGround(new ChainOfCommand.Ground() {
            @Override
            public OptionalInt heightAt(int x, int z, int nearY) {
                return OptionalInt.of(24); // the shaft's floor, near the squad's height
            }

            @Override
            public OptionalInt surfaceAt(int x, int z, int nearY) {
                return OptionalInt.of(70);
            }
        });
        Objective wood = new Objective("Logs for the keep", new Position(-27, 65, -23), "The keep's chest",
                new Objective.Task.Gather(Resource.WOOD));
        List<SoldierStatus> down = List.of(new SoldierStatus("Alice", new Position(-26.5, 24, -22.5), new Order.Idle(), new Report.Mining(1, 9), Optional.empty()));

        assertEquals(70, (int) chain.grounded(wood, down).location().y(), "the surface, not the shaft's floor");
        chain.setGround((x, z, nearY) -> OptionalInt.of(24));
        assertEquals(24, (int) chain.grounded(wood, down).location().y(), "with no surface known, the ground near the squad");
    }

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

    /**
     * A request outranks the standing rule, or it's never answered: under the keep rule the platoon leader's
     * WOOD for a squad whose Class IV is RED stands; the same order for one with no request is put
     * on the keep. Live, the keep's builders asked for logs all day and the rule put every squad back.
     */
    @Test
    void underTheKeepRuleTheGeneralsAnswerToARequestStands() {
        chain.setKeepByDay(true);
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(1000, true));
        Objective wood = new Objective("Logs for the keep", new Position(40, 64, 40), "The keep's chest and pickaxes",
                new Objective.Task.Gather(Resource.WOOD));
        SquadStatus asking = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                PlatoonSergeant.classFour(1, 0, false));

        assertEquals(wood, chain.byDay(wood, asking), "its request answered: the order stands");
        assertTrue(chain.byDay(wood, squad("Bravo", "Bob")).task() instanceof Objective.Task.Keep, "no request: the keep");
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
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
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
        assertEquals(bravo, two.sheltered(hut, squad("Bravo", "Bob")), "the platoon leader's patrol base doesn't take it off the night shift");

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
