package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Role;
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    @BeforeEach
    void nameTheScenario(TestInfo test) {
        ThreadContext.put("scenario", test.getTestMethod().map(Method::getName).orElse("unnamed"));
    }

    @AfterEach
    void endTheScenario() {
        ThreadContext.remove("scenario");
    }

    private static final Position CAMP = new Position(0.5, GROUND + 1, 0.5);

    /** A squad leader holding the squad at the camp, every reply with these {@code roles} and {@code orders}. */
    private static ChatClient squadLeader(String roles, String orders) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": false,
                 "intent": "hold the camp", "cantco": "", "roleChanges": %s, "detached": %s}
                """, CAMP.x(), CAMP.y(), CAMP.z(), roles, orders);
        return ChatClient.create((ChatModel) prompt ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /** Alpha, Alice and Bob, holding the camp for the general, their squad leader's reply as given; started. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders) {
        return run(clock, ground, roles, orders, List.of());
    }

    /** As {@link #run(SimClock, SimServer.Terrain, String, String)}, each soldier also carrying {@code more}. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders, List<int[]> more) {
        return run(clock, ground, roles, orders, more, "");
    }

    /** As {@link #run(SimClock, SimServer.Terrain, String, String, List)}, with the camp at {@code camp} ("x,z"; "" for none). */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders, List<int[]> more, String camp) {
        return run(clock, ground, roles, orders, more, camp, 1000, false);
    }

    /** As {@link #run(SimClock, SimServer.Terrain, String, String, List, String)}, from {@code timeOfDay}, sheltering by night or not. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders, List<int[]> more, String camp,
                          long timeOfDay, boolean shelterAtNight) {
        List<int[]> kit = new ArrayList<>(List.of(
                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                new int[]{38, SimServer.ITEM_BREAD, 16}));
        kit.addAll(more);
        SimServer server = new SimServer(clock, ground, timeOfDay);
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())), squadLeader(roles, orders),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, shelterAtNight, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, camp, state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), kit))
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
        assertEquals(new Plan(Role.MINER, Optional.of(new Position(8, 40, 8)), List.of()), new PlanBook(state).of("Alice"));
    }

    /**
     * A miner sited on the ground, as the model gives it, cuts down to the iron: live, the site's
     * height was taken for the mine's depth, and three miners stood over a one-step staircase.
     */
    @Test
    void aMinerSitedOnTheGroundCutsDownToTheIron() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, """
                [{"soldier": "Alice", "role": "MINER", "x": 8, "y": %d, "z": 8}]""".formatted(GROUND + 1), "[]");
        clock.advance(10 * 60_000);

        assertTrue(cutBelow(server) >= 20, "the miner cut its stairs down: " + cutBelow(server) + " cells cut below y=53");
    }

    /** Flat, with a one-block pond thirty blocks east of the camp. */
    private static int pondFarOff(int x, int y, int z) {
        return x == 30 && z == 0 && y == GROUND ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * By night a farmer comes in to the camp with its squad: live, two farmers working farms 40
     * blocks out were shot by skeletons, one in the dark and one at first light. Only miners work
     * on through the night, underground.
     */
    @Test
    void byNightAFarmerComesInToTheCamp() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondFarOff, """
                [{"soldier": "Alice", "role": "FARMER", "x": 30, "y": %d, "z": 0}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}), "0,0", 14_000, true);
        clock.advance(3 * 60_000);

        PlayerPosition alice = server.position("Alice");
        assertTrue(Math.hypot(alice.x(), alice.z()) <= 8, "in the camp by night, not at " + alice);
    }

    /** Flat, with a one-block pond six blocks east of the camp. */
    private static int pondByTheCamp(int x, int y, int z) {
        return x == 6 && z == 0 && y == GROUND ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * A farmer sited where there's no ground to farm works the camp's farm, and says so: live, the
     * squad leader left a farmer's CANTCO unanswered all day, and it stood at a dry site.
     */
    @Test
    void aFarmerSitedWithNoWaterWorksTheCampsFarm() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.SEEDS), 32}), "0,0");
        clock.advance(4 * 60_000);

        int farmland = 0;
        for (int x = 0; x <= 12; x++) {
            for (int z = -6; z <= 6; z++) {
                farmland += server.block(x, GROUND, z) == SimServer.FARMLAND ? 1 : 0;
            }
        }
        assertTrue(farmland >= 4, "the camp's farm tilled: " + farmland + " cells");
        List<String> log = Files.readAllLines(Path.of("target/sim/aFarmerSitedWithNoWaterWorksTheCampsFarm.log"));
        assertTrue(log.stream().anyMatch(l -> l.contains("no ground to farm") && l.contains("the camp's farm")), "and says why it's there");
    }

    /** Flat, with a pool of water at the ground eight blocks south and one east of (8, 8). */
    private static int poolSouth(int x, int y, int z) {
        return x == 9 && z == 16 && y == GROUND ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * A farm is kept for its site while the site stays put: live, the farm's water lay just over 8
     * blocks from its farmer's site, and it was sited afresh every cycle, found or not by where a
     * soldier stood - and the farmer, farmless half the time, reported it couldn't farm there.
     */
    @Test
    void aFarmIsKeptForItsSite() throws IOException {
        SimClock clock = new SimClock();
        run(clock, TaskOrganisationSimulationTest::poolSouth, """
                [{"soldier": "Alice", "role": "FARMER", "x": 8, "y": %d, "z": 8}]""".formatted(GROUND + 1), "[]");
        clock.advance(2 * 60_000);

        List<String> log = Files.readAllLines(Path.of("target/sim/aFarmIsKeptForItsSite.log"));
        assertEquals(1, log.stream().filter(l -> l.contains("FARM by the water")).count(), "sited once");
        assertEquals(0, log.stream().filter(l -> l.contains("no ground to farm")).count(), "and kept");
    }

    /**
     * A farmer sited where there's no ground to farm by water says so, so its squad leader can site
     * it again: live, one stood a day at its site reporting it was in position.
     */
    @Test
    void aFarmerSitedWithNoWaterSaysItCantFarmThere() throws IOException {
        SimClock clock = new SimClock();
        run(clock, SquadSimulationTest::flat, """
                [{"soldier": "Alice", "role": "FARMER", "x": 8, "y": %d, "z": 8}]""".formatted(GROUND + 1), "[]");
        clock.advance(2 * 60_000);

        List<String> log = Files.readAllLines(Path.of("target/sim/aFarmerSitedWithNoWaterSaysItCantFarmThere.log"));
        assertTrue(log.stream().anyMatch(l -> l.contains("Alice") && l.contains("CANTCO") && l.contains("no ground to farm")),
                "Alice says she can't farm there");
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

    /**
     * The mine's stairs are lit as they're cut, by the squad holding the top. Live, every stair cell
     * was out of the lamplighter's reach: torches shared their claims with the diggers', and the
     * staircase claims its own walkway so nobody digs in it.
     */
    @Test
    void theMinesStairsAreLitAsTheyreCut() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, MINER_ALICE, "[]", List.<int[]>of(new int[]{39, SimServer.ITEM_TORCH, 16}));
        clock.advance(10 * 60_000);

        int torches = 0;
        for (int x = 2; x <= 14; x++) {
            for (int z = 2; z <= 14; z++) {
                for (int y = 40; y <= GROUND; y++) {
                    torches += server.block(x, y, z) == SimServer.TORCH ? 1 : 0;
                }
            }
        }
        assertTrue(cutBelow(server) >= 20, "the miner cut its stairs: " + cutBelow(server));
        assertTrue(torches >= 2, "the stairs lit: " + torches + " torches down them");
    }

    /**
     * A soldier killed and respawned goes to the back of its squad's order while it links up: its
     * units still pair each soldier with its own status. Live, the miner's death left Alpha's orders
     * failing every tick - each unit was handed the soldier at its member's place in the other order.
     */
    @Test
    void aSoldierKilledAndRespawnedLeavesItsSquadsOrdersWhole() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, MINER_ALICE, "[]");
        clock.advance(60_000);
        server.kill("Alice", "death.fell.accident.generic[Alice]");
        clock.advance(2 * 60_000);

        List<String> faults = Files.readAllLines(Path.of("target/sim/aSoldierKilledAndRespawnedLeavesItsSquadsOrdersWhole.log")).stream()
                .filter(l -> l.contains("[Fault]")).toList();
        assertEquals(List.of(), faults);
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
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
