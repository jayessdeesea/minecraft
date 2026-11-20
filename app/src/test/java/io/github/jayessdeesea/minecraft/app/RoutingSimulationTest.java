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

    /** Flat, with a stone wall three high across x=6 from z=-60 to 60, a gap in it at z=30. */
    static int wallWithAFarGap(int x, int y, int z) {
        return x == 6 && y > GROUND && y <= GROUND + 3 && Math.abs(z) <= 60 && z != 30 ? SimServer.STONE : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * The gap 30 blocks off, the walk round some 50 blocks longer than over: still walked, no block placed (operator,
     * 2026-11-20: a block is placed with intent - live, the steps routes put down and left littered the camp).
     */
    @Test
    void aSoldierWalksFarRoundAWallRatherThanLeaveStepsOverIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RoutingSimulationTest::wallWithAFarGap, 1000);
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

        clock.advance(3 * 60_000);

        PlayerPosition alice = server.position("Alice");
        assertTrue(alice.x() > 7, "Alice beyond the wall: at " + alice + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertEquals(List.of(), List.copyOf(server.log).stream().filter(l -> l.startsWith("Alice placed")).toList(), "no block placed on the way");
    }

    /** Stone all round, the surface 12 up, but a gallery two high along x at z=0, from x=-2 to 14; a hole in its floor at x=6, over a cave. */
    static int galleryWithAGap(int x, int y, int z) {
        boolean gallery = z == 0 && x >= -2 && x <= 14 && (y == GROUND + 1 || y == GROUND + 2);
        boolean hole = z == 0 && x == 6 && y <= GROUND && y > GROUND - 10;
        // The surface 12 over it, as a mine's: with sky above, the recovery drill can lay a stair up (field533).
        return y <= -64 ? SimServer.BEDROCK : gallery || hole || y > GROUND + 12 ? SimServer.AIR : SimServer.STONE;
    }

    /**
     * Sent along its gallery past a hole in the floor, a pickaxe in hand and no block: it cuts a stone from the wall and
     * bridges the hole with it (field512-532: Eli stood a day at such a gap at y=20, the fixes passing their unit tests
     * and failing live four times over).
     */
    @Test
    void aMinerWithNoBlockCutsOneAndBridgesTheGapInItsGallery() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RoutingSimulationTest::galleryWithAGap, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position beyond = new Position(11.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Along the gallery", beyond, "There",
                new Objective.Task.None())), squadLeader(beyond),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(1.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(5 * 60_000);

        server.dumpLog("aMinerWithNoBlockCutsOneAndBridgesTheGapInItsGallery");
        PlayerPosition alice = server.position("Alice");
        assertTrue(alice.x() > 7 && alice.y() >= GROUND + 1, "Alice across the gap: at " + alice + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }
}
