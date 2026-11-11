package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The squad leader's task organisation, run as {@link SquadSimulationTest} runs a squad: a
 * soldier's role and directives, set in the squad leader's reply, kept in its plan, and carried
 * out by its detachment while the rest of the squad holds with the general's objective
 * (docs/military-doctrine.md § 18).
 */
class TaskOrganisationSimulationTest {

    @TempDir
    Path state;

    @org.junit.jupiter.api.BeforeEach
    void nameTheScenario(org.junit.jupiter.api.TestInfo test) {
        org.apache.logging.log4j.ThreadContext.put("scenario", test.getTestMethod().map(java.lang.reflect.Method::getName).orElse("unnamed"));
    }

    @org.junit.jupiter.api.AfterEach
    void endTheScenario() {
        org.apache.logging.log4j.ThreadContext.remove("scenario");
    }

    private static final Position CAMP = new Position(0.5, GROUND + 1, 0.5);

    /** A squad leader holding the squad at the camp, every reply with these {@code roles} and {@code orders}. */
    private static ChatClient squadLeader(String roles, String orders) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": false,
                 "intent": "hold the camp", "cantco": "", "roleChanges": %s, "detached": %s}
                """, CAMP.x(), CAMP.y(), CAMP.z(), roles, orders);
        return ChatClient.create((org.springframework.ai.chat.model.ChatModel) prompt ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /** Alpha, Alice and Bob, holding the camp for the general, their squad leader's reply as given; started. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders) {
        SimServer server = new SimServer(clock, ground, 1000); // morning
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())), squadLeader(roles, orders),
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();
        return server;
    }

    private static final String MINER_ALICE = """
            [{"soldier": "Alice", "role": "MINER", "x": 8, "y": 40, "z": 8}]""";

    /** How many cells deep under the miner's site have been cut out: its stairs, and what's below them. */
    private static int cutBelow(SimServer server) {
        int cut = 0;
        for (int x = 2; x <= 14; x++) {
            for (int z = 2; z <= 14; z++) {
                for (int y = 40; y <= GROUND - 10; y++) {
                    cut += server.block(x, y, z) == SimServer.AIR ? 1 : 0;
                }
            }
        }
        return cut;
    }

    private static double fromCamp(SimServer server, String soldier) {
        PlayerPosition at = server.position(soldier);
        return Math.hypot(at.x() - CAMP.x(), at.z() - CAMP.z());
    }

    /** The squad leader makes one soldier its miner: it cuts the mine at its site, and the other holds the camp. */
    @Test
    void aSoldierMadeTheMinerCutsTheMineWhileTheRestHoldTheCamp() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, MINER_ALICE, "[]");
        clock.advance(10 * 60_000);

        assertTrue(cutBelow(server) >= 20, "the miner cut its stairs down to y=40: " + cutBelow(server) + " cells cut below y=53");
        assertTrue(server.count("Alice", SimServer.ITEM_COBBLESTONE) > 0, "the miner carries what it cut");
        assertEquals(0, server.count("Bob", SimServer.ITEM_COBBLESTONE), "the rifleman cut nothing");
        assertTrue(fromCamp(server, "Bob") <= 8 && server.position("Bob").y() > GROUND - 1,
                "the rifleman holds the camp with the squad: " + server.position("Bob"));
        assertEquals(new Plan(Role.MINER, java.util.Optional.of(new Position(8, 40, 8)), List.of()), new PlanBook(state).of("Alice"));
    }

    /**
     * As the model gives it: a new miner also sent to its site, at the mine's depth. It gets there on
     * the ground over it, and goes on to mine.
     */
    @Test
    void aMinerSentToItsSiteFirstGoesOnToMineThere() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, MINER_ALICE, """
                [{"soldier": "Alice", "order": "GO_TO", "x": 8, "y": 40, "z": 8,
                  "resource": "WOOD", "amount": 0, "x2": 0, "z2": 0}]""");
        clock.advance(10 * 60_000);

        assertTrue(cutBelow(server) >= 20, "the miner cut its stairs: " + cutBelow(server) + " cells cut below y=53");
        assertEquals(List.of(), new PlanBook(state).of("Alice").directives(), "the GO_TO is done");
    }

    /** A role is kept on disk: after a restart, with nothing new said, the miner goes on mining. */
    @Test
    void aSoldiersRoleOutlivesARestart() {
        SimClock first = new SimClock();
        run(first, SquadSimulationTest::flat, MINER_ALICE, "[]");
        first.advance(30_000);
        assertEquals(Role.MINER, new PlanBook(state).of("Alice").role());

        SimClock second = new SimClock();
        SimServer server = run(second, SquadSimulationTest::flat, "[]", "[]");
        second.advance(10 * 60_000);
        assertTrue(cutBelow(server) >= 20, "the miner went on mining: " + cutBelow(server) + " cells cut below y=53");
        assertTrue(fromCamp(server, "Bob") <= 8, "the rifleman holds the camp: " + server.position("Bob"));
    }

    /** "Go to x, y, z": the soldier goes there and holds, whatever its squad does, until told otherwise. */
    @Test
    void aSoldierSentToAPointHoldsThere() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, "[]", """
                [{"soldier": "Bob", "order": "GO_TO", "x": 24.5, "y": 64, "z": 0.5,
                  "resource": "WOOD", "amount": 0, "x2": 0, "z2": 0}]""");
        clock.advance(3 * 60_000);

        PlayerPosition bob = server.position("Bob");
        assertTrue(Math.hypot(bob.x() - 24.5, bob.z() - 0.5) <= Directive.GO_TO_TOLERANCE + 0.5, "Bob holds the point: " + bob);
        assertTrue(fromCamp(server, "Alice") <= 8, "Alice holds the camp: " + server.position("Alice"));
        assertTrue(new PlanBook(state).of("Bob").current().isPresent(), "a GO_TO holds until it's replaced");
    }

    /** Flat, with an oak twelve blocks east of the camp. */
    private static int treeEast(int x, int y, int z) {
        return x == 12 && z == 0 && y > GROUND && y <= GROUND + 6 ? SimServer.OAK_LOG : SquadSimulationTest.flat(x, y, z);
    }

    /** "Fetch 4 wood from there": the soldier chops until it carries 4, and the fetch is done. */
    @Test
    void aFetchIsDoneOnceTheSoldierCarriesTheAmount() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::treeEast, "[]", """
                [{"soldier": "Bob", "order": "FETCH", "x": 12, "y": 64, "z": 0,
                  "resource": "WOOD", "amount": 4, "x2": 0, "z2": 0}]""");
        clock.advance(5 * 60_000);

        assertTrue(server.count("Bob", SimServer.ITEM_OAK_LOG) >= 4, "Bob carries the logs: " + server.count("Bob", SimServer.ITEM_OAK_LOG)
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertEquals(Plan.RIFLEMAN, new PlanBook(state).of("Bob"), "the fetch is done, and Bob back with the squad");
    }

    /**
     * A fetch from where nothing grows: no tree in reach, so nothing it carries changes. The work
     * order times out, and the soldier goes back to its squad rather than stand there for good.
     */
    @Test
    void aFetchThatCantBeDoneTimesOutAndTheSoldierGoesBack() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, "[]", """
                [{"soldier": "Bob", "order": "FETCH", "x": 12, "y": 64, "z": 0,
                  "resource": "WOOD", "amount": 4, "x2": 0, "z2": 0}]""");
        clock.advance(4 * 60_000);

        assertEquals(Plan.RIFLEMAN, new PlanBook(state).of("Bob"), "the fetch timed out");
        // The scripted squad leader gives it again every decision; once timed out it isn't taken up again at once.
        clock.advance(60_000);
        assertTrue(fromCamp(server, "Bob") <= 8, "Bob back with the squad: " + server.position("Bob"));
    }

    /**
     * A soldier's basic load is its own: with a wooden sword and the makings of a stone one - two
     * cobblestone and a stick - it makes the stone sword, though its squad leader hasn't said
     * "rearm". Live, 2 of 8 carried a stone sword or better for days, every upgrade waiting on it.
     */
    @Test
    void aSoldierMakesItsStoneSwordWithoutBeingToldTo() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())), squadLeader("[]", "[]"),
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 4},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 4})))
                .run();
        clock.advance(5 * 60_000);

        int stoneSword = SimServer.itemNamedOrMinus("stone_sword");
        for (String soldier : List.of("Alice", "Bob")) {
            assertTrue(server.count(soldier, stoneSword) > 0,
                    soldier + " has no stone sword; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        }
    }

    /** Flat, with a mound two high at x=10..12, z=-1..1 and a hole two deep at x=14..15, z=-1..1. */
    private static int moundAndHole(int x, int y, int z) {
        boolean inZ = z >= -1 && z <= 1;
        if (inZ && x >= 10 && x <= 12 && y > GROUND && y <= GROUND + 2) {
            return SimServer.DIRT;
        }
        if (inZ && x >= 14 && x <= 15 && y > GROUND - 2 && y <= GROUND) {
            return SimServer.AIR;
        }
        return SquadSimulationTest.flat(x, y, z);
    }

    /** "Level x=9..16, z=-2..2 at y=64": the mound dug off, the hole filled, the ground's top at 63. */
    @Test
    void aLevelOrderMakesTheGroundLevel() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::moundAndHole, "[]", """
                [{"soldier": "Alice", "order": "LEVEL", "x": 9, "y": 64, "z": -2,
                  "resource": "WOOD", "amount": 0, "x2": 16, "z2": 2}]""");
        clock.advance(10 * 60_000);

        for (int x = 9; x <= 16; x++) {
            for (int z = -2; z <= 2; z++) {
                assertTrue(server.block(x, GROUND, z) != SimServer.AIR, "the ground at " + x + "," + z + " is filled");
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    assertEquals(SimServer.AIR, server.block(x, y, z), "the ground over " + x + "," + y + "," + z + " is dug off");
                }
            }
        }
        assertEquals(Plan.RIFLEMAN, new PlanBook(state).of("Alice"), "the level is done, and Alice back with the squad");
    }
}
