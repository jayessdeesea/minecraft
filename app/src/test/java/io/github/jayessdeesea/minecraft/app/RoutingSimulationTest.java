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
    static ChatClient squadLeader(Position at) {
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

    /** Flat, a ledge two high from x=6 on, and one block at its foot at z=12 - a ramp up it, 12 blocks along. */
    static int ledgeWithARamp(int x, int y, int z) {
        boolean ledge = x >= 6 && y > GROUND && y <= GROUND + 2 && Math.abs(z) <= 40;
        boolean ramp = x == 5 && y == GROUND + 1 && z == 12;
        return ledge || ramp ? SimServer.STONE : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * In the camp's area of influence - 32 blocks out from its wall line - a route up a ledge that places one step walks
     * to the ramp 12 blocks along instead (operator, 2026-11-21: blocks left round the camp for navigation are a debt
     * that's never paid off, when a slightly dearer route leaves none). The camp at (-20, 0): the ledge 26 out, past its
     * ground work's reach and in its area of influence. Out of that, one step is a builder's stride.
     */
    @Test
    void inTheCampsGroundASoldierWalksToTheRampRatherThanLeaveAStep() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RoutingSimulationTest::ledgeWithARamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position up = new Position(10.5, GROUND + 3, 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Up on the ledge", up, "There",
                new Objective.Task.None())), squadLeader(up),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "-20,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{38, SimServer.ITEM_COBBLESTONE, 32})))
                .run();

        clock.advance(2 * 60_000);

        PlayerPosition alice = server.position("Alice");
        assertTrue(alice.x() > 6 && alice.y() >= GROUND + 3, "Alice up on the ledge: at " + alice + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertEquals(List.of(), List.copyOf(server.log).stream().filter(l -> l.startsWith("Alice placed")).toList(), "no step left on the way");
    }

    /** Flat, a hill four high from x=6 on, and a stair up it at z=12 - one block a step, from x=2. */
    static int hillWithAStair(int x, int y, int z) {
        boolean hill = x >= 6 && y > GROUND && y <= GROUND + 4 && Math.abs(z) <= 40;
        boolean stair = z == 12 && x >= 2 && x <= 5 && y > GROUND && y <= GROUND + x - 1;
        return hill || stair ? SimServer.STONE : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * Up onto a hill on the camp's ground, a stair 12 blocks along: walked, not built (operator, 2026-11-21: a block left
     * round the camp for navigation is a debt never paid off). Live (field615), soldiers built 46 steps on the camp's
     * ground in thirteen minutes, staircases up its hill - not by this: with a stair to walk, the walk is taken. Each
     * step's [Place] line says whether a walk round was there.
     */
    @Test
    void onTheCampsGroundASoldierWalksUpTheStairRatherThanBuildOne() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RoutingSimulationTest::hillWithAStair, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position up = new Position(10.5, GROUND + 5, 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Up on the hill", up, "There",
                new Objective.Task.None())), squadLeader(up),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "-20,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{38, SimServer.ITEM_COBBLESTONE, 32})))
                .run();

        clock.advance(2 * 60_000);

        PlayerPosition alice = server.position("Alice");
        assertTrue(alice.x() > 6 && alice.y() >= GROUND + 5, "Alice up on the hill: at " + alice + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertEquals(List.of(), List.copyOf(server.log).stream().filter(l -> l.startsWith("Alice placed")).toList(), "no step left on the way");
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

    /**
     * Eli's own ground, kept live (field535; WorldRecords): his gallery at y=20 run into a granite cave, his branch's start
     * at (-19, 20, 97) over open air, a pickaxe in hand and no block. Sent there, he gets there - cutting the blocks he
     * floors it with.
     */
    @Test
    void eliOnHisOwnGroundGetsToHisBranch() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(java.nio.file.Path.of(
                RoutingSimulationTest.class.getResource("/worlds/eli-gallery-field535.json").toURI()));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SimWorlds.replay(ground), 1000);
        Roster roster = new Roster("Alpha:Eli");
        Position branch = new Position(-18.5, 20, 96.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("To the branch", branch, "There",
                new Objective.Task.None())), squadLeader(branch),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(-19.6, 20, 91.5), List.of(
                        // His kit live (field536): logs to make a stone sword with, and no cobblestone for its blade.
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_OAK_LOG, 62})))
                .run();

        clock.advance(15 * 60_000);

        server.dumpLog("eliOnHisOwnGroundGetsToHisBranch");
        PlayerPosition eli = server.position("Eli");
        assertTrue(Math.hypot(eli.x() - branch.x(), eli.z() - branch.z()) <= 2.5, "Eli at his branch: at " + eli + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * The camp at (24, 88) as kept live (field540, Mia's ground): heaped with spoil from its ground at y=63 up to y=71,
     * huts on huts among it. Set at sea level ("24,88,64"), its ground is there, not up the heaps - live, read off the
     * surface, it was y=69 - and its upkeep digs the heaps down.
     */
    @Test
    void aHeapedCampsGroundIsReadUnderItsHeaps() throws Exception {
        WorldRecords.Snapshot ground = WorldRecords.load(java.nio.file.Path.of(
                RoutingSimulationTest.class.getResource("/worlds/camp-heaped-field540.json").toURI()));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SimWorlds.replay(ground), 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(24.5, 72, 88.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Hold the camp", camp, "There",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "24,88,64", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(22.5, 72, 87.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();
        java.util.function.IntSupplier heaped = () -> {
            int n = 0;
            for (int x = 16; x <= 32; x++) {
                for (int z = 80; z <= 96; z++) {
                    for (int y = 65; y <= 72; y++) {
                        n += server.block(x, y, z) == SimServer.AIR ? 0 : 1;
                    }
                }
            }
            return n;
        };
        int before = heaped.getAsInt();

        clock.advance(10 * 60_000);

        assertEquals(64.0, chain.foothold().orElseThrow().camp().y(), "the camp's ground where it was set");
        assertTrue(heaped.getAsInt() < before, "the heaps dug down: " + heaped.getAsInt() + " of " + before + " blocks over the ground left");
    }

    /**
     * Flatter ground (operator, 2026-11-20): the camp's ground level inside its wall line, a heap of dirt two high just
     * outside it. Its ground work goes out a ring once its box is level (CampGround), and the heap is dug down.
     */
    @Test
    void theCampsGroundWorkGoesOutPastItsWallLine() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 10 && x <= 12 && z >= 0 && z <= 2 && y > GROUND && y <= GROUND + 2
                ? SimServer.DIRT : SquadSimulationTest.flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Hold the camp", camp, "There",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0," + (GROUND + 1), state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(10 * 60_000);

        int left = 0;
        for (int x = 10; x <= 12; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                    left += server.block(x, y, z) == SimServer.AIR ? 0 : 1;
                }
            }
        }
        assertEquals(0, left, "the heap past the wall line dug down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }
}
