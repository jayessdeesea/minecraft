package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How soldiers route across ground, in the squad simulation (bot.nav.Pathfinder). */
class RoutingSimulationTest {

    @TempDir
    Path state;

    @BeforeEach
    void nameTheScenario(TestInfo test) {
        ThreadContext.put("scenario", test.getTestMethod().map(Method::getName).orElse("unnamed"));
    }

    @AfterEach
    void endTheScenario() {
        ThreadContext.remove("scenario");
    }

    /** Flat, with a stone wall three high across x=6, a gap in it at z=13. */
    static int wallWithAGap(int x, int y, int z) {
        return x == 6 && y > GROUND && y <= GROUND + 3 && Math.abs(z) <= 30 && z != 13 ? SimServer.STONE : SquadSimulationTest.flat(x, y, z);
    }

    /** A squad leader that sends the squad to {@code at}. */
    private static ChatClient squadLeader(Position at) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "FILE", "interval": "NORMAL",
                 "verticalTolerance": 1, "risk": "LOW", "soldierRisks": [], "rearm": true,
                 "intent": "beyond the wall", "cantco": "", "roleChanges": [], "detached": []}
                """, at.x(), at.y(), at.z());
        return ChatClient.create((ChatModel) prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /**
     * Sent beyond a wall it could pillar over, with cobblestone to do it, a soldier walks round through the gap 13
     * blocks off instead (operator, 2026-11-18: if there are several ways somewhere, walk a bit more rather than place
     * blocks).
     */
    @Test
    void aSoldierWalksRoundAWallRatherThanPillarOverIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RoutingSimulationTest::wallWithAGap, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position beyond = new Position(10.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Beyond the wall", beyond, "There",
                new Objective.Task.None())), squadLeader(beyond),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{38, SimServer.ITEM_COBBLESTONE, 32})))
                .run();

        clock.advance(2 * 60_000);

        PlayerPosition alice = server.position("Alice");
        assertTrue(alice.x() > 7, "Alice beyond the wall: at " + alice + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertEquals(List.of(), List.copyOf(server.log).stream().filter(l -> l.startsWith("Alice placed")).toList(), "no block placed on the way");
    }
}
