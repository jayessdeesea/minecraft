package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Keep;
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
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.flat;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.scripted;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.squadLeader;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.pitInCamp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mine in the squad simulation: cut to the iron, kept across restarts, worked by night, its landing and its shaft. */
class MineSimulationTest {

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

    /** Runs Alpha, four soldiers with stone pickaxes, on MINE at x=4 z=4 for 15 simulated minutes. */
    private SimServer mine(SimServer.Terrain ground) {
        return mine(ground, List.of(
                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                new int[]{38, SimServer.ITEM_BREAD, 16}));
    }

    /** As {@link #mine(SimServer.Terrain)}, each soldier carrying {@code kit}: [slot, item, count] each. */
    private SimServer mine(SimServer.Terrain ground, List<int[]> kit) {
        return mine(ground, kit, Integer.MAX_VALUE);
    }

    /** As {@link #mine(SimServer.Terrain, List)}, each pickaxe carried worn down to {@code usesLeft}. */
    private SimServer mine(SimServer.Terrain ground, List<int[]> kit, int usesLeft) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, ground, 1000); // morning
        Position head = new Position(4, 16, 4);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", head,
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), kit));
        runner.run();
        if (usesLeft != Integer.MAX_VALUE) {
            clock.advance(1_000); // joined
            ALPHA.forEach(n -> server.wear(n, usesLeft));
        }
        clock.advance(15 * 60_000);
        return server;
    }

    static final List<String> ALPHA = List.of("Alice", "Bob", "Carol", "Dana");

static void assertMinedToTheIron(SimServer server) {
        int iron = ALPHA.stream().mapToInt(n -> server.count(n, SimServer.ITEM_RAW_IRON)).sum();
        int cobblestone = ALPHA.stream().mapToInt(n -> server.count(n, SimServer.ITEM_COBBLESTONE)).sum();
        assertTrue(iron > 0, "raw iron carried: " + iron + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertTrue(cobblestone > 0, "cobblestone carried: " + cobblestone);
    }

    /** Flat, with a shaft 6 deep down the 3x3 columns round the camp's mine head, (4, -4) from its centre. */
    static int shaftAtTheMineHead(int x, int y, int z) {
        return x >= 3 && x <= 5 && z >= -5 && z <= -3 && y > GROUND - 6 && y <= GROUND ? SimServer.AIR : flat(x, y, z);
    }

    /**
     * A mining squad's soldier sealed in a pocket away from the mine digs the drill's stair up out of it: the mine's orders
     * stand over the drill's for its miners in their galleries, not for one cut off elsewhere. Live (field671-676), Eli in
     * a pocket at y=33, 60 blocks from Bravo's mine, had a stair up of 94 blocks laid out at every restart and never cut
     * a block of it - each turn given the mine's walk instead, at LOW, "no route" - three hours where he stood.
     */
    @Test
    void aMinerSealedInAPocketAwayFromTheMineDigsItsStairUp() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 40 && x <= 45 && z == 40 && y >= GROUND - 25 && y <= GROUND - 24
                ? SimServer.AIR : flat(x, y, z), 1000);
        Position head = new Position(4, 16, 4);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", head,
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(
                        n -> n.equals("Dana") ? new PlayerPosition(40.5, GROUND - 25, 40.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(15 * 60_000);

        List<String> log = Files.readAllLines(Path.of("target", "sim", "aMinerSealedInAPocketAwayFromTheMineDigsItsStairUp.log"));
        // Out, then down the mine with its squad: where it ends is the mine's, so the drill's own word for it.
        assertTrue(log.stream().anyMatch(l -> l.contains("RECOVERY: the stair up for Dana is dug")), "Dana's stair up dug: "
                + server.log.stream().filter(e -> e.contains("Dana")).limit(12).toList() + "; at " + server.position("Dana"));
        assertTrue(server.log.stream().anyMatch(e -> e.startsWith("Dana dug") && e.endsWith(",40") && e.matches(".* at -?\\d+,6[0-3],40")),
                "its top cut at the surface: " + server.log.stream().filter(e -> e.startsWith("Dana dug")).reduce((x, y) -> y).orElse("none"));
    }

    @Test
    void aSquadWithPickaxesCutsTheMineDownToTheIron() {
        assertMinedToTheIron(mine(SquadSimulationTest::flat));
    }

    /**
     * As at the camp, live: the objective's x and z beside a pond. The stairs can't be cut next to
     * water, so the mine has to go where they can.
     */
    @Test
    void aMineObjectiveBesideAPondIsCutClearOfTheWater() {
        assertMinedToTheIron(mine((x, y, z) -> x >= 5 && x <= 9 && z >= 0 && z <= 8 && y >= 60 && y <= GROUND
                ? SimServer.WATER : flat(x, y, z)));
    }

    /** As live: nobody carries a pickaxe. Each has logs to make one, and the squad has to before it can mine. */
    @Test
    void aSquadWithNoPickaxeMakesOneAndCutsTheMine() {
        assertMinedToTheIron(mine(SquadSimulationTest::flat, List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                new int[]{37, SimServer.ITEM_BREAD, 16},
                new int[]{9, SimServer.ITEM_OAK_LOG, 8})));
    }

    /**
     * As live: the cutter's pickaxe wore out halfway down, and with no room on the stairs for a
     * table, a soldier with cobblestone, sticks and a table couldn't make another. Every pickaxe
     * here is a stone one with a few blocks left in it, and nobody carries logs: the squad goes
     * through all four before the bottom, and makes the next from the stairs' own cobblestone.
     */
    @Test
    void pickaxesWornOutOnTheWayDownAreMadeAgainFromTheStairsOwnStone() {
        SimServer server = mine(SquadSimulationTest::flat, List.of(
                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                new int[]{37, SimServer.ITEM_BREAD, 16},
                new int[]{38, SimServer.ITEM_WOODEN_SWORD, 1},
                new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.CRAFTING_TABLE), 1},
                new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.STICK), 8}), 12);
        assertTrue(server.log.stream().filter(l -> l.endsWith("'s pickaxe broke")).count() >= 4,
                "every pickaxe wore out: " + server.log.stream().filter(l -> l.endsWith("'s pickaxe broke")).toList());
        assertMinedToTheIron(server);
    }

    /**
     * The operator's night rule with two squads: dusk falls, Alpha holds the camp and Bravo goes down
     * the camp's mine (docs/echelons/platoon-leader/operator-rules.md). In the dark nobody of Bravo stands at the top.
     */
    @Test
    void atNightTheSecondSquadWorksTheCampsMineBelowGround() {
        new PhaseBook(new WallStore(state, "chests")).keep(io.github.jayessdeesea.minecraft.command.Phase.BUILDUP, 0); // the night shift mines
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 11_000); // an hour before dusk
        Roster roster = new Roster("Alpha:Alice,Bob;Bravo:Carol,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, true, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_OAK_LOG, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 64})));
        runner.run();

        // To midnight: dusk is at tick 13178, an hour of game time is 1000 ticks.
        clock.advance((18_000 - 11_000) * 50L);

        List<String> atTheTop = List.of("Carol", "Dana").stream()
                .filter(n -> server.position(n).y() >= GROUND).toList();
        assertTrue(atTheTop.isEmpty(), "Bravo at the top at midnight: " + atTheTop + " - "
                + List.of("Carol", "Dana").stream().map(n -> n + " at " + server.position(n)).toList());
    }

    /**
     * The camp's mine is the camp's, whatever the squads' tasks: a squad keeping the camp with no
     * MINE order leaves the shaft open. Without it, the camp's fill would have poured dirt down it.
     */
    @Test
    void theCampsMineIsLeftOpenByAnIdleSquadKeepingTheCamp() {
        List<BlockPos> shaft = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = GROUND - 5; y <= GROUND; y++) {
                    shaft.add(new BlockPos(x, y, z));
                }
            }
        }
        Staircase kept = new Staircase(
                new BlockPos(4, GROUND + 1, 4), GROUND - 5);
        new WallStore(state, "stairs").save("0,0", List.of(kept.head(), kept.step(kept.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::pitInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 64})));
        runner.run();

        clock.advance(4 * 60_000);

        long filled = shaft.stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.AIR).count();
        assertEquals(0, filled, "cells of the mine filled in");
    }

    /**
     * After a restart the camp's mine is the staircase kept on disk, cut on down: live, sited again
     * from the ground as it then read, a new one was started over the old each time - until none
     * could be.
     */
    @Test
    void aRestartCutsTheCampsKeptStaircaseOnDown() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsCut, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", new Position(4, 16, 4),
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(10 * 60_000);

        for (int k = 6; k < 16; k++) {
            for (BlockPos c : KeptStairs.KEPT.cells(k)) {
                assertEquals(SimServer.AIR, server.block(c.x(), c.y(), c.z()), "step " + (k + 1) + " of the kept staircase at " + c
                        + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
            }
        }
    }

    /**
     * A mine ordered anywhere near the camp is the camp's: live, the platoon leader moved its order to the
     * camp's centre, 13 blocks from the mine already cut, and a new staircase was started inside the
     * wall and kept in the cut one's place.
     */
    @Test
    void aMineOrderedElsewhereNearTheCampIsTheCampsKeptStaircase() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsCut, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        // 16 blocks from the kept head each way - beyond where a staircase is sited round a point.
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut a mine shaft near the camp", new Position(-12, 16, -12),
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(60_000);

        assertEquals(Optional.of(List.of(KeptStairs.KEPT.head(), KeptStairs.KEPT.step(KeptStairs.KEPT.steps() - 1))), new WallStore(state, "stairs").load("0,0"),
                "the camp's staircase still the one kept");
    }

    /**
     * By night, with the camp's mine cut to the bottom and nothing left in it to cut, the squad
     * waits at the landing, under the ground. Live, it stood all night at the head, under the sky.
     */
    @Test
    void byNightASquadWithItsMineCutWaitsAtTheLanding() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsAllCut, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", new Position(4, 16, 4),
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        // Spares: a pickaxe worn out by the day's dig leaves one up top by night (no pickaxe, no mine).
                        new int[]{39, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{40, SimServer.ITEM_STONE_PICKAXE, 1})));
        runner.run();

        clock.advance(4 * 60_000);

        for (String name : List.of("Alice", "Bob")) {
            PlayerPosition at = server.position(name);
            assertTrue(at.y() < KeptStairs.KEPT.head().y() - 24, name + " under the ground by night, not at " + at);
        }
        // Waiting the night out down the mine is hiding, not a soldier stuck: no alert for it.
        List<String> alerts;
        try {
            alerts = Files.readAllLines(Path.of("target/sim/byNightASquadWithItsMineCutWaitsAtTheLanding.log")).stream()
                    .filter(l -> l.contains("[Alert]") && l.contains("stationary")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(List.of(), alerts);
    }

    /**
     * By night, in a lit camp with ground work to do, the squad down the mine stays down: the camp's
     * upkeep is for those at the camp, and a soldier at the landing 48 blocks under it isn't. Live, one
     * at the landing was handed the camp's dig, wall and lights, out of reach, all night - each
     * stalled or taken back within seconds.
     */
    @Test
    void byNightASquadDownTheMineIsntSentUpToKeepALitCamp() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsAllCutInALitCamp, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", new Position(4, 16, 4),
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        // Spares: a pickaxe worn out by the day's dig leaves one up top by night (no pickaxe, no mine).
                        new int[]{39, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{40, SimServer.ITEM_STONE_PICKAXE, 1})));
        runner.run();

        clock.advance(6 * 60_000);

        // Handed the camp's dig on the surface, as they joined, is fair; held on to down the mine isn't.
        List<String> held = runner.taskings("Alpha").stream().filter(t -> t.task().equals("dig the camp's ground"))
                .map(t -> t.line(0)).toList();
        assertEquals(List.of(), held, "the camp's dig held by soldiers down the mine");
        for (String name : List.of("Alice", "Bob")) {
            assertTrue(server.position(name).y() < KeptStairs.KEPT.head().y() - 24, name + " under the ground by night, not at " + server.position(name));
        }
    }

    /**
     * The camp's mine cut to the bottom before a restart, and no squad told to MINE after it: the
     * standard still says the mine is there. Live, after a restart it read "no mine yet" - only a
     * cutter at work that run counted the steps.
     */
    @Test
    void aMineCutBeforeARestartStillCountsAfterIt() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsAllCut, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();
        clock.advance(2 * 60_000);

        Foothold.Standard mined = chain.foothold().orElseThrow().standards().stream()
                .filter(s -> s.name().equals("stone and iron")).findFirst().orElseThrow();
        assertTrue(mined.met(), "the camp's mine: " + mined.status());
    }

    /**
     * As live: the landing's stand lost its floor, and the cutter, held by night to exactly that spot,
     * stood in the hole under it reporting no route - stranded, to the recovery drill. With nothing
     * to cut, it waits there as loosely as the rest of the squad.
     */
    @Test
    void byNightTheCutterWaitsAtALandingWhoseStandLostItsFloor() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsCutLandingHoled, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Cut the mine down to the iron", new Position(4, 16, 4),
                "Stone and iron for the camp", new Objective.Task.Mine())), squadLeader(new Position(4, GROUND + 1, 4)),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        // Spares: a pickaxe worn out by the day's dig leaves one up top by night (no pickaxe, no mine).
                        new int[]{39, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{40, SimServer.ITEM_STONE_PICKAXE, 1})));
        runner.run();

        clock.advance(5 * 60_000);

        List<String> stranded;
        try {
            stranded = Files.readAllLines(Path.of("target/sim/byNightTheCutterWaitsAtALandingWhoseStandLostItsFloor.log")).stream()
                    .filter(l -> l.contains("RECOVERY") && l.contains("is stranded")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(List.of(), stranded);
        for (String name : List.of("Alice", "Bob")) {
            assertTrue(server.position(name).y() < KeptStairs.KEPT.head().y() - 24, name + " under the ground by night, not at " + server.position(name));
        }
    }

    /**
     * A keep's mine kept on disk whose well is dead - found dried up before a restart - moves on as the
     * runner starts, and the runner starts: live (field383, field384), the move at start-up read a field
     * the constructor hadn't set, and the garrison failed to start.
     */
    @Test
    void aKeptMineWhoseWellIsDeadMovesOnAtStartUp() {
        WallStore chests = new WallStore(state, "chests");
        chests.save("keepmine", List.of(new BlockPos(-26, 16, -24)));
        chests.save("deadwells", List.of(new BlockPos(-26, 70, -24)));
        SimClock clock = new SimClock();
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);

        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString());

        assertNotEquals(new Position(-26, 16, -24), chain.keepMine(), "moved off the dead well");
        assertNotEquals(List.of(new BlockPos(-26, 16, -24)), chests.load("keepmine").orElseThrow(), "and kept on disk moved");
    }

    /**
     * By night, the camp's mine cut out and no ore wanted - the iron pickaxe carried, every soldier in a full set of
     * armour - its MINE reads DONE and the camp's mine moves to its next site, and the night's objective with it. Live
     * (field663), with nowhere for it to go, every squad was given the same order for the cut-out mine each minute all
     * night, DONE within a second each time, and the night's mining yielded nothing.
     */
    @Test
    void byNightTheCampsMineCutOutMovesToItsNextSite() {
        KeptStairs.keptInTheBuildup(state);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeptStairs::keptStairsAllCut, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, true, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("iron_pickaxe"), 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{5, SimServer.itemNamedOrMinus("iron_helmet"), 1},
                        new int[]{6, SimServer.itemNamedOrMinus("iron_chestplate"), 1},
                        new int[]{7, SimServer.itemNamedOrMinus("iron_leggings"), 1},
                        new int[]{8, SimServer.itemNamedOrMinus("iron_boots"), 1})));
        runner.run();
        Position first = new Position(ChainOfCommand.MINE_HEAD_DX, ChainOfCommand.MINE_DEPTH, ChainOfCommand.MINE_HEAD_DZ);

        clock.advance(6 * 60_000);

        String mission = "Cut the camp's mine down to the iron through the night";
        List<io.github.jayessdeesea.minecraft.command.ObjectiveRecord> given = chain.objectiveBook.orElseThrow().registers().objectives().stream()
                .filter(r -> r.mission().equals(mission)).toList();
        long atFirst = given.stream().filter(r -> r.chunkX() == Math.floorDiv(ChainOfCommand.MINE_HEAD_DX, 16)
                && r.chunkZ() == Math.floorDiv(ChainOfCommand.MINE_HEAD_DZ, 16)).count();
        assertTrue(atFirst <= 1, "the night's mining given for the cut-out mine " + atFirst + " times: " + given);
        Position next = chain.campMines.site(camp);
        assertNotEquals(first, next, "the camp's mine moved on");
        assertTrue(given.stream().anyMatch(r -> r.chunkX() == Math.floorDiv((int) next.x(), 16) && r.chunkZ() == Math.floorDiv((int) next.z(), 16)),
                "the squad given the next site, " + next + ": " + given);
        assertEquals(List.of(new BlockPos((int) next.x(), (int) next.y(), (int) next.z())), new WallStore(state, "chests").load("campmine").orElseThrow(),
                "the camp's mine as it stands kept on disk");
        // Its well sited there and kept as the camp's - in its chunk, the cut-out one let go.
        BlockPos head = new WallStore(state, "stairs").load("well 0,0").map(List::getFirst).orElseThrow();
        assertEquals(List.of(Math.floorDiv((int) next.x(), 16), Math.floorDiv((int) next.z(), 16)),
                List.of(Math.floorDiv(head.x(), 16), Math.floorDiv(head.z(), 16)), "the camp's well at " + head);
        assertEquals(Optional.of(List.of()), new WallStore(state, "stairs").load("0,0"), "the cut-out staircase let go");
    }

    /** The camp's mine as it stands, and the sites it has left, are read back at start-up: not sent back to the first. */
    @Test
    void theCampsMineMovedOnIsKeptAcrossARestart() {
        WallStore chests = new WallStore(state, "chests");
        chests.save("campmine", List.of(new BlockPos(-40, 16, -8)));
        chests.save("campminestried", List.of(new BlockPos(4, 16, -4)));
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, true, new SimClock());

        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());

        assertEquals(new Position(-40, 16, -8), chain.campMines.site(camp));
        assertEquals(java.util.Set.of(new Position(4, 16, -4)), chain.campMines.tried());
    }

    /** With nothing on disk - no squad on MINE since a restart - the mine head's shaft is still nobody's fill. */
    @Test
    void theShaftAtTheCampsMineHeadIsLeftOpenWithNothingOnDisk() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, MineSimulationTest::shaftAtTheMineHead, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 64})));
        runner.run();

        clock.advance(4 * 60_000);

        long filled = 0;
        for (int x = 3; x <= 5; x++) {
            for (int z = -5; z <= -3; z++) {
                for (int y = GROUND - 5; y <= GROUND; y++) {
                    filled += server.block(x, y, z) != SimServer.AIR ? 1 : 0;
                }
            }
        }
        assertEquals(0, filled, "cells of the shaft filled in");
    }
}
