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
    void atNightTheFirstSquadHoldsTheCampAndTheRestGoDownItsMine() {
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

        assertTrue(alpha.patrolBase(), alpha.toString());
        assertTrue(bravo.mine(), bravo.toString());
        assertEquals(new Position(-44, ChainOfCommand.MINE_DEPTH, 40), bravo.location());
        assertEquals(bravo, two.sheltered(bravo, squad("Bravo", "Bob")), "a squad down the mine stays there");
        Objective hut = new Objective("Build a patrol base", new Position(0, 64, 0), "Safe", new Objective.Task.PatrolBase());
        assertEquals(bravo, two.sheltered(hut, squad("Bravo", "Bob")), "the general's patrol base doesn't take it off the night shift");
    }

    /**
     * At dusk, a squad near the camp shelters in it, behind its wall and in its light: live, one on
     * the wall's last block built its hut five blocks down outside the wall, and skeletons killed all
     * four as they built it. A base the general sites out there is moved in too; one a squad is
     * sealed in, or one away from the camp, isn't.
     */
    @Test
    void atNightASquadNearTheCampSheltersInIt() {
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(18000, true));
        chain.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        SquadStatus outsideTheWall = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(-50.5, 64, 56.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);

        Objective given = chain.sheltered(WALL, outsideTheWall);
        assertTrue(given.patrolBase(), given.toString());
        assertEquals(new Position(-48, 64, 44), given.location(), "in the camp");

        Objective whereItStands = new Objective("Build a patrol base where you stand", new Position(-50, 64, 56), "Safe",
                new Objective.Task.PatrolBase());
        assertEquals(new Position(-48, 64, 44), chain.sheltered(whereItStands, outsideTheWall).location(), "the general's, moved in");
        SquadStatus sealed = new SquadStatus("Alpha", new Report.InPosition(), outsideTheWall.soldiers(), List.of(), Optional.empty(), true);
        assertEquals(whereItStands, chain.sheltered(whereItStands, sealed), "a base it's sealed in, kept");
    }

    private static SquadStatus squad(String name, String soldier) {
        return new SquadStatus(name, new Report.InPosition(),
                List.of(new SoldierStatus(soldier, new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);
    }
}
