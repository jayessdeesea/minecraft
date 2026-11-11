package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Perimeter;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.TaskRegister;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
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
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A squad run through a task with the app's own code - runner, chain of command, drills, routines -
 * on simulated time, against {@link SimServer}, with a scripted general and squad leader: no
 * Docker, no model (backlog/echelon-contracts.md). A simulated hour runs in seconds.
 */
class SquadSimulationTest {

    @TempDir
    Path state;

    /**
     * Each scenario logs to its own file, target/sim/&lt;scenario&gt;.log (log4j2-test.xml): scenarios
     * run in parallel, and one log shared by all of them would interleave.
     */
    @BeforeEach
    void nameTheScenario(TestInfo test) {
        ThreadContext.put("scenario", test.getTestMethod().map(Method::getName).orElse("unnamed"));
    }

    @AfterEach
    void endTheScenario() {
        ThreadContext.remove("scenario");
    }

    static final int GROUND = 63;

    /** Flat: bedrock, stone, dirt, grass at y=63, and iron ore in a band round y=16. */
    static int flat(int x, int y, int z) {
        if (y <= -64) {
            return SimServer.BEDROCK;
        }
        if (y < 60) {
            return y >= 14 && y <= 18 && Math.floorMod(x * 7 + z * 13 + y, 11) == 0 ? SimServer.IRON_ORE : SimServer.STONE;
        }
        if (y < GROUND) {
            return SimServer.DIRT;
        }
        return y == GROUND ? SimServer.GRASS : SimServer.AIR;
    }

    /** A general that gives every squad the same objective, every time it's asked. */
    static General scripted(Function<SquadStatus, Objective> objective) {
        return new General() {
            @Override
            public void receiveMission(String mission) {
            }

            @Override
            public void receiveStockpile(Stockpile stockpile) {
            }

            @Override
            public void receiveRemark(String remark) {
            }

            @Override
            public void receiveFoothold(Foothold foothold) {
            }

            @Override
            public void receiveTime(TimeOfDay time) {
            }

            @Override
            public List<Objective> decide(List<SquadStatus> squads) {
                return squads.stream().map(objective).toList();
            }
        };
    }

    /** A squad leader that moves the squad on {@code at} in a wedge, at low risk, free to rearm. */
    private static ChatClient squadLeader(Position at) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": true,
                 "intent": "work the objective", "cantco": "", "roleChanges": [], "detached": []}
                """, at.x(), at.y(), at.z());
        return ChatClient.create((ChatModel) prompt ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
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

    private static final List<String> ALPHA = List.of("Alice", "Bob", "Carol", "Dana");

    private static void assertMinedToTheIron(SimServer server) {
        int iron = ALPHA.stream().mapToInt(n -> server.count(n, SimServer.ITEM_RAW_IRON)).sum();
        int cobblestone = ALPHA.stream().mapToInt(n -> server.count(n, SimServer.ITEM_COBBLESTONE)).sum();
        assertTrue(iron > 0, "raw iron carried: " + iron + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        assertTrue(cobblestone > 0, "cobblestone carried: " + cobblestone);
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
     * the camp's mine (docs/echelons/general/operator-rules.md). In the dark nobody of Bravo stands
     * at the top of the stairs.
     */
    @Test
    void atNightTheSecondSquadWorksTheCampsMineBelowGround() {
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
     * As live: a soldier with 4 wheat - a loaf's and one over - started a loaf 518 times and made
     * none, the loaf asked for counted again from the wheat left as each run went.
     */
    @Test
    void aSoldierWithWheatForOneLoafAndOneOverBakesIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 4},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 2})));
        runner.run();

        clock.advance(3 * 60_000);

        assertEquals(1, server.count("Alice", ItemClassRegistry.ItemClass.FOOD),
                "a loaf baked; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * As live: a soldier carried 22 wheat for days while its squad starved, since only a farmer
     * baked. Here nobody carries food, only wheat and the logs for a table: each, short of its basic
     * load, bakes its own bread at the camp's workshop, fitting the table there first.
     */
    @Test
    void soldiersShortOfFoodBakeTheWheatTheyCarryAtTheWorkshop() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 9},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 2})));
        runner.run();

        clock.advance(3 * 60_000);

        ItemClassRegistry.ItemClass food = ItemClassRegistry.ItemClass.FOOD;
        int bread = server.count("Alice", food) + server.count("Bob", food);
        assertTrue(bread >= 6, "bread baked from the wheat carried: " + bread + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * As live: soldiers holding the camp stood still all day. On PATROL each walks the round inside
     * the wall line, at its own point of it, and keeps walking: over a few minutes every soldier
     * covers the round's four sides.
     */
    @Test
    void aSquadOnPatrolWalksTheRoundAndKeepsMoving() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Patrol the camp", camp, "Trouble met before it arrives",
                new Objective.Task.Patrol())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16})));
        runner.run();

        Map<String, Set<String>> sides = new HashMap<>();
        for (int second = 0; second < 240; second++) {
            clock.advance(1000);
            for (String name : List.of("Alice", "Bob")) {
                PlayerPosition at = server.position(name);
                // Each side's line is PATROL_RADIUS out from the camp's cell centre, turned 2.5 short of.
                double near = SoldierRunner.PATROL_RADIUS - 2.5;
                String side = at.z() < 0.5 - near ? "N" : at.z() > 0.5 + near ? "S" : at.x() < 0.5 - near ? "W" : at.x() > 0.5 + near ? "E" : null;
                if (side != null) {
                    sides.computeIfAbsent(name, n -> new TreeSet<>()).add(side);
                }
            }
        }
        assertEquals(Map.of("Alice", Set.of("E", "N", "S", "W"), "Bob", Set.of("E", "N", "S", "W")), sides,
                "each soldier round all four sides");
    }

    /**
     * As live: a soldier carried 627 cobblestone while the other squad's masons stood idle for want
     * of blocks, there being nowhere to leave it. With no supply point, the camp's workshop chest is
     * the stockpile: fitted by the first soldier with a full load, and the loads go into it.
     */
    @Test
    void fullLoadsGoIntoTheCampsChestFittedAtItsWorkshop() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 40},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4})));
        runner.run();

        clock.advance(3 * 60_000);

        int stored = server.chests.values().stream().flatMap(Arrays::stream)
                .filter(st -> st != null && st[0] == SimServer.ITEM_COBBLESTONE).mapToInt(st -> st[1]).sum();
        assertEquals(1, server.chests.size(), "one chest, the camp's");
        assertEquals(80, stored, "both loads in it; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** Flat, with an oak six logs tall inside the camp's wall line at x=3 z=3. */
    private static int treeInCamp(int x, int y, int z) {
        return x == 3 && z == 3 && y > GROUND && y <= GROUND + 6 ? SimServer.OAK_LOG : flat(x, y, z);
    }

    /** FORTIFY starts with the sky: a tree inside the wall line is felled to its last log. */
    @Test
    void aSquadFellsTheTreesOverItsCampBeforeWallingIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::treeInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(5 * 60_000);

        for (int y = GROUND + 1; y <= GROUND + 6; y++) {
            assertEquals(SimServer.AIR, server.block(3, y, 3), "the log at y=" + y + "; last server events: "
                    + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        }
        List<String> review = runner.taskingReview("Alpha");
        assertTrue(review.stream().anyMatch(l -> l.matches("  dig the camp's ground: [12] done, 0 taken off unfinished")), review.toString());
    }

    /** Flat, with a pit 6 deep and 3 across inside the camp's wall line. */
    private static int pitInCamp(int x, int y, int z) {
        return x >= 3 && x <= 5 && z >= 3 && z <= 5 && y > GROUND - 6 && y <= GROUND ? SimServer.AIR : flat(x, y, z);
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

    /** The camp's mine as kept on disk in {@link #aRestartCutsTheCampsKeptStaircaseOnDown}: from (4, 64, 4) down to y=16. */
    private static final Staircase KEPT = new Staircase(
            new BlockPos(4, GROUND + 1, 4), 16);

    /**
     * Flat, with {@link #KEPT}'s first six steps cut, and a block of dirt dropped on its head - as
     * live, where the ground over an old shaft read a block higher than when it was sited.
     */
    private static int keptStairsCut(int x, int y, int z) {
        if (x == 4 && y == GROUND + 1 && z == 4) {
            return SimServer.DIRT;
        }
        for (int k = 0; k < 6; k++) {
            if (KEPT.cells(k).contains(new BlockPos(x, y, z))) {
                return SimServer.AIR;
            }
        }
        return flat(x, y, z);
    }

    /**
     * After a restart the camp's mine is the staircase kept on disk, cut on down: live, sited again
     * from the ground as it then read, a new one was started over the old each time - until none
     * could be.
     */
    @Test
    void aRestartCutsTheCampsKeptStaircaseOnDown() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsCut, 1000);
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
            for (BlockPos c : KEPT.cells(k)) {
                assertEquals(SimServer.AIR, server.block(c.x(), c.y(), c.z()), "step " + (k + 1) + " of the kept staircase at " + c
                        + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
            }
        }
    }

    /**
     * A mine ordered anywhere near the camp is the camp's: live, the general moved its order to the
     * camp's centre, 13 blocks from the mine already cut, and a new staircase was started inside the
     * wall and kept in the cut one's place.
     */
    @Test
    void aMineOrderedElsewhereNearTheCampIsTheCampsKeptStaircase() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsCut, 1000);
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

        assertEquals(Optional.of(List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1))), new WallStore(state, "stairs").load("0,0"),
                "the camp's staircase still the one kept");
    }

    /** Every cell of {@link #KEPT}'s cuts - stairs, landing, tunnels - as air: a mine cut to the bottom. */
    private static final Set<BlockPos> KEPT_CUT = KEPT.cuts().stream()
            .flatMap(c -> c.cells().stream()).collect(Collectors.toUnmodifiableSet());

    private static int keptStairsAllCut(int x, int y, int z) {
        return KEPT_CUT.contains(new BlockPos(x, y, z)) ? SimServer.AIR : flat(x, y, z);
    }

    /**
     * By night, with the camp's mine cut to the bottom and nothing left in it to cut, the squad
     * waits at the landing, under the ground. Live, it stood all night at the head, under the sky.
     */
    @Test
    void byNightASquadWithItsMineCutWaitsAtTheLanding() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsAllCut, 14_000); // an hour after dusk
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

        clock.advance(4 * 60_000);

        for (String name : List.of("Alice", "Bob")) {
            PlayerPosition at = server.position(name);
            assertTrue(at.y() < KEPT.head().y() - 24, name + " under the ground by night, not at " + at);
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

    /** As {@link #keptStairsAllCut}, the camp's ground lit by torches every 3 blocks, and a mound on it to dig. */
    private static int keptStairsAllCutInALitCamp(int x, int y, int z) {
        if (y == GROUND + 1 && Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0 && Math.abs(x) <= 15 && Math.abs(z) <= 15
                && !KEPT_CUT.contains(new BlockPos(x, y, z)) && Math.abs(x - 4) > 2 && Math.abs(z - 4) > 2) {
            return SimServer.TORCH;
        }
        if (x >= -6 && x <= -5 && z >= -6 && z <= -5 && y > GROUND && y <= GROUND + 2) {
            return SimServer.DIRT;
        }
        // And its stairs lit, as live.
        if (KEPT.lights().contains(new BlockPos(x, y, z))) {
            return SimServer.TORCH;
        }
        return keptStairsAllCut(x, y, z);
    }

    /**
     * By night, in a lit camp with ground work to do, the squad down the mine stays down: the camp's
     * upkeep is for those at the camp, and a soldier at the landing 48 blocks under it isn't. Live, one
     * at the landing was handed the camp's dig, wall and lights, out of reach, all night - each
     * stalled or taken back within seconds.
     */
    @Test
    void byNightASquadDownTheMineIsntSentUpToKeepALitCamp() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsAllCutInALitCamp, 14_000); // an hour after dusk
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

        clock.advance(6 * 60_000);

        // Handed the camp's dig on the surface, as they joined, is fair; held on to down the mine isn't.
        List<String> held = runner.taskings("Alpha").stream().filter(t -> t.task().equals("dig the camp's ground"))
                .map(t -> t.line(0)).toList();
        assertEquals(List.of(), held, "the camp's dig held by soldiers down the mine");
        for (String name : List.of("Alice", "Bob")) {
            assertTrue(server.position(name).y() < KEPT.head().y() - 24, name + " under the ground by night, not at " + server.position(name));
        }
    }

    /** Flat, with two one-block ponds east of the camp, 10 apart: neither's plot alone the camp's farm. */
    private static int twoPonds(int x, int y, int z) {
        return y == GROUND && z == 0 && (x == 14 || x == 24) ? SimServer.WATER : flat(x, y, z);
    }

    /**
     * The camp's farm takes in the ground round the next water too, to feed the camp: live, one
     * pond's 44 cells fed eight soldiers a few loaves an hour, and they went hungry.
     */
    @Test
    void theCampsFarmIsGrownRoundTheNextWater() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::twoPonds, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(60_000);

        List<BlockPos> farm = new WallStore(state, "farm").load("0,0").orElseThrow();
        List<BlockPos> plot = farm.subList(1, farm.size());
        assertTrue(plot.stream().anyMatch(c -> c.x() < 19) && plot.stream().anyMatch(c -> c.x() > 19),
                "round both ponds: " + plot.size() + " cells, x " + plot.stream().mapToInt(c -> c.x()).summaryStatistics());
        assertTrue(plot.size() > 80, "more than one water's plot: " + plot.size());
    }

    /**
     * The camp's mine cut to the bottom before a restart, and no squad told to MINE after it: the
     * standard still says the mine is there. Live, after a restart it read "no mine yet" - only a
     * cutter at work that run counted the steps.
     */
    @Test
    void aMineCutBeforeARestartStillCountsAfterIt() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsAllCut, 1000);
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

    /** As {@link #keptStairsAllCut}, with the landing's stand's floor gone: ore taken from under it. */
    private static int keptStairsCutLandingHoled(int x, int y, int z) {
        BlockPos stand = KEPT.landingStand();
        return x == stand.x() && y == stand.y() - 1 && z == stand.z() ? SimServer.AIR : keptStairsAllCut(x, y, z);
    }

    /**
     * As live: the landing's stand lost its floor, and the cutter, held by night to exactly that spot,
     * stood in the hole under it reporting no route - stranded, to the recovery drill. With nothing
     * to cut, it waits there as loosely as the rest of the squad.
     */
    @Test
    void byNightTheCutterWaitsAtALandingWhoseStandLostItsFloor() {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::keptStairsCutLandingHoled, 14_000); // an hour after dusk
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
            assertTrue(server.position(name).y() < KEPT.head().y() - 24, name + " under the ground by night, not at " + server.position(name));
        }
    }

    /**
     * As live: a squad ordered from building its patrol base to the camp's mine goes to the mine, the
     * base's plan let go - it went on building the base at the stairs' head, reporting Fortifying.
     */
    @Test
    void aSquadSentFromItsPatrolBaseToTheMineStopsBuildingTheBase() throws IOException {
        AtomicBoolean mine = new AtomicBoolean();
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> mine.get()
                ? new Objective("Cut the mine down to the iron", new Position(4, 16, 4), "Stone and iron for the camp", new Objective.Task.Mine())
                : new Objective("Build a patrol base where you stand", camp, "Shelter", new Objective.Task.PatrolBase())),
                squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 64})));
        runner.run();

        clock.advance(20_000); // the base laid out and begun
        Path log = Path.of("target/sim/aSquadSentFromItsPatrolBaseToTheMineStopsBuildingTheBase.log");
        assertTrue(Files.readAllLines(log).stream().anyMatch(l -> l.contains("Fortifying")), "the base begun");
        mine.set(true);
        clock.advance(6 * 60_000); // the general's next call, and the squad on its way
        List<String> lines = Files.readAllLines(log);
        List<String> last = lines.subList(Math.max(0, lines.size() - 40), lines.size()).stream()
                .filter(l -> l.contains("Fortifying")).toList();
        assertEquals(List.of(), last, "still building the base");
    }

    /**
     * As live: a soldier at a third of a heart and food 0 beside a squadmate with one ration, which it
     * kept as its own - none had more than two to spare. Buddy aid: the squadmate hands it over.
     */
    @Test
    void aSoldierStarvingAtDeathsDoorIsHandedASquadmatesLastRation() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Fran,Hal");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(n -> new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Fran") ? List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 1})
                                : List.<int[]>of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1})));
        runner.run();
        clock.advance(1_000); // joined
        server.starve("Hal", 0.5f);

        clock.advance(60_000);

        assertEquals(1, server.count("Hal", ItemClassRegistry.ItemClass.FOOD),
                "Fran's ration handed to Hal; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** Flat, with a shaft 6 deep down the 3x3 columns round the camp's mine head, (4, -4) from its centre. */
    private static int shaftAtTheMineHead(int x, int y, int z) {
        return x >= 3 && x <= 5 && z >= -5 && z <= -3 && y > GROUND - 6 && y <= GROUND ? SimServer.AIR : flat(x, y, z);
    }

    /** With nothing on disk - no squad on MINE since a restart - the mine head's shaft is still nobody's fill. */
    @Test
    void theShaftAtTheCampsMineHeadIsLeftOpenWithNothingOnDisk() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::shaftAtTheMineHead, 1000);
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

    /** As {@link #pitInCamp}, with water in its bottom two layers: a flooded shaft. */
    private static int floodedPitInCamp(int x, int y, int z) {
        boolean pit = x >= 3 && x <= 5 && z >= 3 && z <= 5;
        return pit && y > GROUND - 6 && y <= GROUND - 4 ? SimServer.WATER : pitInCamp(x, y, z);
    }

    /**
     * Live, the camp's mine shaft flooded with soldiers in it. The pit is capped over the water and
     * filled up to the camp's ground, as any hollow is.
     */
    @Test
    void aFloodedPitInTheCampIsCappedAndFilled() {
        assertPitFilled(SquadSimulationTest::floodedPitInCamp);
    }

    /**
     * Live, two soldiers floated for hours in a flooded shaft, reporting the wood they meant to
     * gather. Stuck in the water, a soldier with blocks plugs the water round it - and says it's
     * stuck, so the squad's recovery drill hears it.
     */
    @Test
    void aSoldierStuckInWaterPlugsIt() {
        assertPlugsItsWayOut(SimServer.ITEM_DIRT, 64);
    }

    /**
     * With logs, the only blocks it carries, it plugs the water just the same: live, a farmer with
     * two logs and no dirt floated for minutes beside its farm, reporting no route out.
     */
    @Test
    void aSoldierStuckInWaterWithOnlyLogsPlugsIt() {
        assertPlugsItsWayOut(SimServer.ITEM_OAK_LOG, 8);
    }

    private void assertPlugsItsWayOut(int blocks, int count) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::floodedPitInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(4.5, GROUND - 4, 4.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, blocks, count})));
        runner.run();

        clock.advance(3 * 60_000);

        // Out: plugging the water round it gave it footing, and it built its way up out of the pit.
        assertTrue(server.log.stream().anyMatch(e -> e.startsWith("Alice placed")), "it plugged the water: " + server.log);
        assertTrue(server.position("Alice").y() >= GROUND - 2, "out of the water, at y=" + server.position("Alice").y()
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * FORTIFY fills the camp's hollows from the bottom before walling: live, a pit under the wall
     * line left the wall nothing to stand on.
     */
    @Test
    void aSquadFillsAPitInTheCampFromTheBottom() {
        assertPitFilled(SquadSimulationTest::pitInCamp);
    }

    private void assertPitFilled(SimServer.Terrain terrain) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, terrain, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(8 * 60_000);

        // Level with the ground, nothing open to the sky: a step a soldier built to climb out can
        // seal a pocket under it, underground and harmless.
        List<String> open = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                if (server.block(x, GROUND, z) == SimServer.AIR) {
                    open.add(x + "," + GROUND + "," + z);
                }
            }
        }
        assertEquals(List.of(), open, "the pit's top still open; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /** Flat, with a mound of dirt two high in the camp. */
    private static int moundInCamp(int x, int y, int z) {
        return x >= 3 && x <= 4 && z >= 3 && z <= 4 && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT : flat(x, y, z);
    }

    /**
     * A squad with nothing to do keeps the camp: it digs the high ground down. Live, three soldiers
     * stood a day in a lit camp with their task all but done.
     */
    @Test
    void anIdleSquadFlattensTheCamp() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(4 * 60_000);

        long mound = 0;
        for (int x = 3; x <= 4; x++) {
            for (int z = 3; z <= 4; z++) {
                for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                    if (server.block(x, y, z) != SimServer.AIR) {
                        mound++;
                    }
                }
            }
        }
        assertEquals(0, mound, "blocks of the mound left; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * A squad told to light a camp it has no torches for, nor the makings, keeps the camp while it
     * waits - and says so once, not every cycle. Live, a soldier between its upkeep's cuts reported
     * its lamplighter's CANTCO anew each cycle, 20 requests in 24 SITREPs.
     */
    @Test
    void aSquadWaitingOnTorchesKeepsTheCampWithoutRepeatingItself() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(4 * 60_000);

        long mound = 0;
        for (int x = 3; x <= 4; x++) {
            for (int z = 3; z <= 4; z++) {
                for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                    if (server.block(x, y, z) != SimServer.AIR) {
                        mound++;
                    }
                }
            }
        }
        assertEquals(0, mound, "blocks of the mound left");
        List<String> review = chain.requestReview();
        String toSquadLeader = review.stream().filter(l -> l.startsWith("  squad leader:")).findFirst().orElseThrow(() -> new AssertionError("no requests to the squad leader: " + review));
        Matcher m = Pattern.compile("(\\d+) answered.*?(\\d+) settled").matcher(toSquadLeader);
        assertTrue(m.find(), toSquadLeader);
        int raised = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
        assertTrue(raised <= 6, "requests to the squad leader in 4 minutes: " + review);
    }

    /**
     * A squad told to light the camp, carrying the makings, goes and makes its torches and puts them
     * down: the camp's upkeep doesn't take it off to dig. Live, a torchmaker walking to the workshop
     * was handed the upkeep's dig and set out for the workshop again every few ticks.
     */
    @Test
    void aSquadWithTheMakingsLightsTheCampBeforeKeepingIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 16},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(6 * 60_000);

        long torches = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    torches += server.block(x, y, z) == SimServer.TORCH ? 1 : 0;
                }
            }
        }
        assertTrue(torches >= 4, torches + " torches down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /** The camp's wall, as laid out round the camp's ground on flat ground: two high, eight out. */
    private static final Set<BlockPos> CAMP_WALL = Set.copyOf(
            Perimeter.layout(new WorldView() {
                @Override
                public boolean isSolid(int x, int y, int z) {
                    return y <= GROUND;
                }

                @Override
                public boolean isHazard(int x, int y, int z) {
                    return false;
                }

                @Override
                public List<TrackedEntity> nearbyHostiles(
                        Position center, double radius) {
                    return List.of();
                }
            }, new BlockPos(0, GROUND + 1, 0), SoldierRunner.WALL_RADIUS));

    /** Flat, the camp's wall standing round it in cobblestone. */
    private static int walledCamp(int x, int y, int z) {
        return CAMP_WALL.contains(new BlockPos(x, y, z)) ? SimServer.COBBLESTONE : flat(x, y, z);
    }

    /**
     * A camp walled in before a restart, and no squad told to FORTIFY after it: the camp's wall still
     * stands, and the standard says so. Live, after a restart it read "not begun" - the layout is
     * kept on disk, but the measure read only the walls a squad had been ordered to that run.
     */
    @Test
    void aWallBuiltBeforeARestartStillCountsAfterIt() {
        new WallStore(state).save("0,0", List.copyOf(CAMP_WALL));
        // And a layout from another FORTIFY order near the camp, never built: the camp's wall is the built one.
        new WallStore(state).save("2,2", CAMP_WALL.stream().map(c -> c.offset(2, 0, 2)).toList());
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::walledCamp, 1000);
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

        Foothold.Standard walled = chain.foothold().orElseThrow().standards().stream()
                .filter(s -> s.name().equals("the camp walled in")).findFirst().orElseThrow();
        assertTrue(walled.met(), "the camp's wall: " + walled.status());
    }

    /** One block of the camp's wall, its first by layout order, of dirt. */
    private static final BlockPos DIRT_IN_WALL = CAMP_WALL.stream().min(Comparator.comparingInt(BlockPos::x)
            .thenComparingInt(BlockPos::z).thenComparingInt(BlockPos::y)).orElseThrow();

    /** As {@link #walledCamp}, one block of the wall dirt. */
    private static int walledCampOneDirt(int x, int y, int z) {
        return DIRT_IN_WALL.equals(new BlockPos(x, y, z)) ? SimServer.DIRT : walledCamp(x, y, z);
    }

    /**
     * FORTIFY ordered a little off the camp's centre: the squad finishes the camp's own wall, the
     * one its standard measures, not a new one round the general's point. Live, the masons
     * repointed a wall with 23 dirt blocks while the camp's, with 1, stayed open for days.
     */
    @Test
    void aSquadSentToWallTheCampFinishesTheCampsOwnWall() {
        new WallStore(state).save("0,0", List.copyOf(CAMP_WALL));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::walledCampOneDirt, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position point = new Position(2.5, GROUND + 1, 2.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Wall the camp in", point, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(point),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64})))
                .run();
        clock.advance(5 * 60_000);

        Foothold.Standard walled = chain.foothold().orElseThrow().standards().stream()
                .filter(st -> st.name().equals("the camp walled in")).findFirst().orElseThrow();
        assertTrue(walled.met(), "the camp's wall: " + walled.status());
    }

    /**
     * SECURE ordered at the camp, the general's point a few blocks off its centre and above its
     * ground, as live: the squad lights the camp's ground, and the camp's standard is met. Live,
     * SECURE lit a disc round the point and reported DONE, while the camp's own disc had 8 cells dark
     * for days - two measures of one standard, and nobody working the one that was open.
     */
    @Test
    void aSquadSentToLightTheCampLightsTheCampsOwnGround() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position point = new Position(4.5, GROUND + 3, 4.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", point, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(point),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 15},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(30 * 60_000);

        Foothold.Standard lit = chain.foothold().orElseThrow().standards().stream()
                .filter(s -> s.name().equals("the camp's ground lit")).findFirst().orElseThrow();
        assertTrue(lit.met(), "the camp's ground: " + lit.status() + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 8), server.log.size()));
    }

    /**
     * A squad sent for wood where there's none to find, carrying torches, keeps the camp's light:
     * its gather found nothing, and that's no work. Live, two soldiers stood a day in a dark camp
     * with 7 and 13 torches, each "on" a wood gather with nothing left to gather.
     */
    @Test
    void aSquadWithNoWoodToFindLightsTheCampWithTheTorchesItCarries() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Gather wood", camp, "Logs for the camp",
                new Objective.Task.Gather(Resource.WOOD))), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_TORCH, 32})));
        runner.run();

        clock.advance(30 * 60_000);

        Foothold.Standard lit = chain.foothold().orElseThrow().standards().stream()
                .filter(st -> st.name().equals("the camp's ground lit")).findFirst().orElseThrow();
        assertTrue(lit.met(), "the camp's ground: " + lit.status());
    }

    /**
     * Flat, with a pit 3 deep and two across at (30, 30), away from the camp, one cell of it a block
     * deeper - a step down, so it's no room to cut a door out of, as live - and a torch on every
     * floor of it.
     */
    private static int torchInAPit(int x, int y, int z) {
        if (x >= 30 && x <= 31 && z >= 30 && z <= 31) {
            int floor = x == 31 && z == 31 ? GROUND - 4 : GROUND - 3;
            if (y > floor && y <= GROUND) {
                return y == floor + 1 ? SimServer.TORCH : SimServer.AIR;
            }
        }
        return flat(x, y, z);
    }

    /**
     * A soldier in a pit with a torch at its feet pillars out: the torch broken first, since the
     * server won't put a block where a torch is. Live, a soldier jumped in place for good in a pocket
     * it could only climb out of, standing in a torch.
     */
    @Test
    void aSoldierStandingInATorchPillarsOutOfAPit() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::torchInAPit, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(6.5, GROUND + 1, 6.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(30.5, GROUND - 2, 30.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 16})));
        runner.run();

        clock.advance(2 * 60_000);

        assertTrue(server.position("Alice").y() >= GROUND + 1, "Alice at " + server.position("Alice") + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * Four soldiers with the makings and one workshop: they make their torches there, and the camp
     * is lit. Live, four crowded the workshop's one stand together; the simulation doesn't push one
     * body off another, so this is the throughput a fix for that must keep.
     */
    @Test
    void fourTorchmakersShareTheWorkshop() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 16},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(6 * 60_000);

        long torches = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    torches += server.block(x, y, z) == SimServer.TORCH ? 1 : 0;
                }
            }
        }
        assertTrue(torches >= 8, torches + " torches down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /** The mound in the camp, and torches standing on a 7-block lattice round it: the camp's ground lit. */
    private static int moundInALitCamp(int x, int y, int z) {
        boolean torch = y == GROUND + 1 && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0 && Math.abs(x) <= 14 && Math.abs(z) <= 14;
        return torch ? SimServer.TORCH : moundInCamp(x, y, z);
    }

    /**
     * A camp lit and an idle squad in it keeps it by night too: monsters don't spawn on lit ground,
     * so the night isn't lost standing about. Live, squads stood every night at their slots.
     */
    @Test
    void anIdleSquadKeepsALitCampByNight() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInALitCamp, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(4 * 60_000);

        long mound = 0;
        for (int x = 3; x <= 4; x++) {
            for (int z = 3; z <= 4; z++) {
                for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                    if (server.block(x, y, z) != SimServer.AIR) {
                        mound++;
                    }
                }
            }
        }
        assertEquals(0, mound, "blocks of the mound left by night");
    }

    /**
     * By night only the fit keep the camp: lit ground spawns nothing, but phantoms come out of the
     * sky. Live, an unarmed soldier at 4 health digging the camp by night was killed by one.
     */
    @Test
    void byNightOneAtLowHealthDoesntKeepTheCamp() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInALitCamp, 14_000); // an hour after dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();
        clock.advance(5_000); // joined, and the camp's work handed out
        Path log = Path.of("target/sim/byNightOneAtLowHealthDoesntKeepTheCamp.log");
        int before = Files.readAllLines(log).size();
        server.starve("Bob", 4);

        clock.advance(2 * 60_000);

        List<String> after = Files.readAllLines(log);
        after = after.subList(before, after.size());
        assertTrue(after.stream().anyMatch(l -> l.contains("Bob taken off dig the camp's ground")), "Bob, at 4 health, taken off the camp's work");
        assertTrue(after.stream().noneMatch(l -> l.contains("Bob has nothing else to do - keeps the camp")), "and not given it again");
        assertTrue(after.stream().noneMatch(l -> l.contains("Alice taken off dig the camp's ground")), "Alice, fit, keeps at it");
    }

    /** Flat stone, its only soil a patch 30 blocks east. */
    private static int stoneWithSoilFar(int x, int y, int z) {
        if (y > GROUND) {
            return SimServer.AIR;
        }
        return y == GROUND && x >= 28 && x <= 32 && z >= -2 && z <= 2 ? SimServer.DIRT : SimServer.STONE;
    }

    /**
     * Dirt is everywhere, so a soldier with none near isn't sent a long way for it. Live, one digging
     * dirt for its squad's hut by night went forty blocks off for it, met a witch, and died.
     */
    @Test
    void noSoldierGoesALongWayForDirtForTheHut() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::stoneWithSoilFar, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build a patrol base where you stand", camp, "Shelter",
                new Objective.Task.PatrolBase())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        double farthest = 0;
        for (int step = 0; step < 36; step++) {
            clock.advance(5_000);
            for (String name : List.of("Alice", "Bob")) {
                PlayerPosition at = server.position(name);
                farthest = Math.max(farthest, Math.hypot(at.x(), at.z()));
            }
        }
        assertTrue(farthest < 22, "a soldier went " + farthest + " blocks off for dirt");
    }

    /** Flat, with a pond by the camp: farm ground by water. */
    private static int pondByCamp(int x, int y, int z) {
        return x >= 6 && x <= 8 && z >= 6 && z <= 8 && y == GROUND ? SimServer.WATER : flat(x, y, z);
    }

    /** Flat, with a pond 30 blocks east of the camp: the camp's farm, a walk off. */
    private static int pondOffCamp(int x, int y, int z) {
        return x >= 29 && x <= 31 && z >= -1 && z <= 1 && y == GROUND ? SimServer.WATER : flat(x, y, z);
    }

    /**
     * A hungry squad's farmers keep the farm, and the camp's dark ground doesn't pull them off it.
     * Live, a farmer carrying torches was handed the camp's lighting each time it came within 24
     * blocks of the camp and its farm each time it walked out again - 281 taskings in half an hour
     * ended unfinished within 30 s, as it walked between the two and finished neither.
     */
    @Test
    void aFarmerWithTorchesKeepsTheFarmAndIsntPulledOffItToLightTheCamp() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::pondOffCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 12},
                        new int[]{10, SimServer.ITEM_TORCH, 32})))
                .run();
        clock.advance(1_000);
        List.of("Alice", "Bob").forEach(n -> server.starve(n, 20));

        clock.advance(8 * 60_000);

        List<String> churned;
        try {
            churned = Files.readAllLines(Path.of("target/sim/aFarmerWithTorchesKeepsTheFarmAndIsntPulledOffItToLightTheCamp.log"))
                    .stream().filter(l -> l.matches(".*\\[Register\\] \\w+ taken off .* unfinished, after ([0-9]|[12][0-9]) s.*")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(churned.size() <= 4, churned.size() + " taskings ended unfinished within 30 s: "
                + churned.subList(0, Math.min(6, churned.size())));
    }

    /**
     * Field feeding: a squad with no food and nothing to hunt, under another task, keeps the camp's
     * farm - tills, sows, harvests the wheat as it ripens and bakes it - without a FARM order. Live,
     * the farm was tended only under one, and the squads went hungry for hours.
     */
    @Test
    void aHungrySquadKeepsTheCampsFarmWithoutBeingOrderedTo() {
        hungrySquadFarms(SquadSimulationTest::pondByCamp);
    }

    /**
     * The same after a restart: the camp's farm as it was kept on disk, by its water. Live, a farm
     * loaded from disk was never handed to the squads, and both starved under SECURE beside it.
     */
    @Test
    void aHungrySquadKeepsTheCampsFarmAfterARestart() {
        new WallStore(state, "farm").save("0,0", List.of(new BlockPos(7, GROUND, 7),
                new BlockPos(5, GROUND, 6), new BlockPos(5, GROUND, 7),
                new BlockPos(5, GROUND, 8), new BlockPos(6, GROUND, 5),
                new BlockPos(7, GROUND, 5), new BlockPos(8, GROUND, 5)));
        hungrySquadFarms(SquadSimulationTest::pondByCamp);
    }

    private void hungrySquadFarms(SimServer.Terrain ground) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, ground, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 12},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4})));
        runner.run();

        clock.advance(8 * 60_000);

        ItemClassRegistry.ItemClass wheat = ItemClassRegistry.ItemClass.WHEAT;
        ItemClassRegistry.ItemClass food = ItemClassRegistry.ItemClass.FOOD;
        int harvested = server.count("Alice", wheat) + server.count("Bob", wheat) + server.count("Alice", food) + server.count("Bob", food);
        assertTrue(harvested > 0, "wheat harvested, or baked; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        // Nobody lands on the farmland: on flat ground by a pond, nobody comes down onto it.
        List<String> trampled = server.log.stream().filter(l -> l.contains("trampled")).toList();
        assertEquals(List.of(), trampled, "farmland trampled");
    }

    /** Flat, ringed 8 blocks out by a dirt wall two high: closed, but an enderman's to open. */
    private static int dirtWall(int x, int y, int z) {
        boolean ring = Math.max(Math.abs(x), Math.abs(z)) == 8;
        return ring && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT : flat(x, y, z);
    }

    /** A closed wall of dirt isn't done: masons with cobblestone replace it block by block. */
    @Test
    void aSquadRebuildsADirtWallInStone() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::dirtWall, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 64},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 64})));
        // The wall as it was laid out, on the flat ground, before it was built of dirt - as the
        // garrison keeps it on disk; laid out over the dirt, it would stand on top of it.
        WorldView ground = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return flat(x, y, z) != SimServer.AIR;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        List<BlockPos> layout = Perimeter.layout(ground,
                new BlockPos(0, GROUND + 1, 0), 8);
        new WallStore(state).save("0,0", layout);
        runner.run();

        clock.advance(9 * 60_000); // by dusk

        long dirt = layout.stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.DIRT).count();
        assertEquals(0, dirt, "dirt left in the wall; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** The camp's wall closed, lip and all, every block of it dirt; an outcrop of stone 12 blocks east, outside it. */
    private static int dirtWallAndStone(int x, int y, int z) {
        if (x >= 12 && x <= 14 && z >= -1 && z <= 1 && y > GROUND && y <= GROUND + 3) {
            return SimServer.STONE;
        }
        return CAMP_WALL.contains(new BlockPos(x, y, z)) ? SimServer.DIRT : flat(x, y, z);
    }

    /**
     * A mason with no cobblestone to repoint the wall with quarries some, rather than stand about.
     * Live, the camp's wall's last dirt block stood for a day: its masons carried none, and nothing
     * sent them for it - a wall going up has its masons dig dirt when short, a wall being repointed
     * had nothing.
     */
    @Test
    void masonsWithNoStoneQuarrySomeToRepointTheWall() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::dirtWallAndStone, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        List<BlockPos> layout = List.copyOf(CAMP_WALL);
        new WallStore(state).save("0,0", layout);
        runner.run();

        clock.advance(8 * 60_000);

        long stone = layout.stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).count();
        assertTrue(stone > 0, "none of the wall's dirt replaced with stone; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * At dusk the squad shelters in the camp - but not over its workshop. Live, the night's hut at
     * the camp's centre sealed the workshop's chest inside it, and the miners stood all night unable
     * to put their loads away.
     */
    @Test
    void theNightsBaseInTheCampLeavesItsWorkshopClear() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 12_300); // just before dusk
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, true, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(4.5, GROUND + 1, 9.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64})))
                .run();

        clock.advance(4 * 60_000);

        List<String> log;
        try {
            log = Files.readAllLines(Path.of("target/sim/theNightsBaseInTheCampLeavesItsWorkshopClear.log"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        java.util.regex.Matcher shop = java.util.regex.Pattern.compile("the camp's workshop: stand BlockPos\\[x=(-?\\d+), y=-?\\d+, z=(-?\\d+)\\], "
                + "table BlockPos\\[x=(-?\\d+), y=-?\\d+, z=(-?\\d+)\\], furnace BlockPos\\[x=(-?\\d+), y=-?\\d+, z=(-?\\d+)\\]")
                .matcher(log.stream().filter(l -> l.contains("the camp's workshop:")).findFirst().orElseThrow(() -> new AssertionError("no workshop")));
        assertTrue(shop.find());
        java.util.regex.Matcher base = java.util.regex.Pattern.compile("PATROL BASE at Position\\[x=(-?[\\d.]+), y=-?[\\d.]+, z=(-?[\\d.]+)\\]")
                .matcher(log.stream().filter(l -> l.contains("PATROL BASE at")).reduce((a, b) -> b).orElseThrow(() -> new AssertionError("no base")));
        assertTrue(base.find());
        int bx = (int) Math.floor(Double.parseDouble(base.group(1)));
        int bz = (int) Math.floor(Double.parseDouble(base.group(2)));
        // A hut for two: its floor at the site and one beyond, its walls one out round that.
        for (int k = 1; k <= 5; k += 2) {
            int x = Integer.parseInt(shop.group(k));
            int z = Integer.parseInt(shop.group(k + 1));
            assertTrue(x < bx - 1 || x > bx + 2 || z < bz - 1 || z > bz + 2,
                    "the hut at " + bx + "," + bz + " is over the workshop's " + x + "," + z);
        }
    }

    /** Flat, with one log too high over the camp to cut from the ground. */
    private static int logOutOfReach(int x, int y, int z) {
        return x == 3 && z == 3 && y == GROUND + 10 ? SimServer.OAK_LOG : flat(x, y, z);
    }

    /**
     * Trust but verify: the squad reports working, but the sky over the camp isn't clearing - the
     * register marks the task STALLED rather than taking the report's word for it, and the squad
     * takes its soldiers off it for their next work, the wall.
     */
    @Test
    void workTheWorldDoesntBearOutIsMarkedStalled() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::logOutOfReach, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(4 * 60_000);

        List<TaskRegister.Tasking> open = runner.taskings("Alpha");
        assertTrue(open.stream().noneMatch(t -> t.task().equals("dig the camp's ground")), "taken off it: " + open);
        assertTrue(open.stream().anyMatch(t -> t.task().equals("wall the camp")), "on to the wall: " + open);
        List<String> review = runner.taskingReview("Alpha");
        assertTrue(review.stream().anyMatch(l -> l.matches("  dig the camp's ground: 0 done, [12] taken off unfinished")), review.toString());
    }

    /** Ground rising a block every 3 east of x=-6: the camp's wall stands on many heights. */
    private static int slope(int x, int y, int z) {
        int top = GROUND + Math.max(0, Math.floorDiv(x + 6, 3));
        return y <= -64 ? SimServer.BEDROCK : y < 55 ? SimServer.STONE : y < top ? SimServer.DIRT
                : y == top ? SimServer.GRASS : SimServer.AIR;
    }

    /** As {@link #slope}, but level inside the wall line at the camp's height: only the wall's ring slopes. */
    private static int slopeLevelInside(int x, int y, int z) {
        if (Math.max(Math.abs(x), Math.abs(z)) <= 7) {
            int top = GROUND + 2;
            return y <= -64 ? SimServer.BEDROCK : y < 55 ? SimServer.STONE : y < top ? SimServer.DIRT
                    : y == top ? SimServer.GRASS : SimServer.AIR;
        }
        return slope(x, y, z);
    }

    /**
     * FORTIFY on a slope: the wall's ring stands at many heights, each column from its own ground.
     * The camp inside is already level (levelling is {@link #aSquadLevelsTheCampOnASlope}); the
     * squad digs its own dirt off base, as it would live.
     */
    @Test
    void aSquadWallsInTheCampOnASlope() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::slopeLevelInside, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 3, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Wall the camp in", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(30 * 60_000); // its dirt dug off base, a trip at a time

        // Closed - of dirt, with no stone to hand, so the standard itself waits on stone.
        Foothold.Standard walled = chain.foothold().orElseThrow().standards().stream()
                .filter(st -> st.name().equals("the camp walled in")).findFirst().orElseThrow();
        assertEquals(OptionalInt.of(0), runner.wallLeft("Alpha"), walled.status());
    }

    /**
     * FORTIFY's levelling on a slope: the high ground inside the wall line dug down to the camp's
     * height and the low ground filled up to it, before the wall.
     */
    @Test
    void aSquadLevelsTheCampOnASlope() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::slope, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 3, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Wall the camp in", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(25 * 60_000);

        assertEquals(OptionalInt.of(0), runner.levellingLeft("Alpha"), "high ground and hollows left inside the wall");
    }

    /**
     * A patrol base on a slope, two zombies on the squad before the hut is up: the squad fights, then
     * goes back to the same hut and seals itself in. Live, each fight re-sited the base and the
     * squad laid out hut after hut in the open.
     */
    @Test
    void aFightWhileTheHutGoesUpDoesntMoveIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::slope, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position site = new Position(0, GROUND + 3, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build a patrol base here", site,
                "Squad safe from monsters until dawn", new Objective.Task.PatrolBase())), squadLeader(site),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{38, SimServer.ITEM_DIRT, 64})));
        runner.run();

        clock.advance(3000); // the squad on its way to the site
        server.spawnZombie(3.5, GROUND + 4, 3.5);
        server.spawnZombie(-2.5, GROUND + 3, -2.5);
        clock.advance(5 * 60_000);

        assertTrue(server.zombiesLeft() == 0, server.zombiesLeft() + " zombies still standing");
        SoldierRunner.BaseReport base = runner.baseReport("Alpha").orElseThrow(() -> new AssertionError("no patrol base"));
        assertTrue(base.allInside() && base.sealed(), "after the fight: " + base + "; deaths " + server.deaths);
    }

    /**
     * Flat, with a closed pocket in a block of stone below the ground at (10, 0) - feet room at y=61
     * and a cell beside it a block deeper, at (11, 0) - roofed by the stone itself.
     */
    private static int pocketBelow(int x, int y, int z) {
        boolean open = z == 0 && (x == 10 && y >= GROUND - 2 && y <= GROUND - 1 || x == 11 && y >= GROUND - 3 && y <= GROUND - 1);
        // In stone, as live: a pickaxe digs it, bare hands don't.
        boolean rock = x >= 6 && x <= 15 && z >= -4 && z <= 4 && y >= GROUND - 6 && y <= GROUND;
        return open ? SimServer.AIR : rock ? SimServer.STONE : flat(x, y, z);
    }

    /**
     * As live (field35 to field38): Dana boxed in a pocket below the ground with nothing to dig or
     * build with, no stair down to lay. The engineer lays a stair up, a squadmate with a pickaxe digs
     * it from the top, and she walks out.
     */
    @Test
    void aSoldierBoxedInBelowIsDugOutByASquadmateWithAPickaxe() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::pocketBelow, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(
                        n -> n.equals("Dana") ? new PlayerPosition(10.5, GROUND - 2, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.of(new int[]{37, SimServer.ITEM_BREAD, 16}) : List.of(
                                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() >= GROUND + 1, "Dana still below at " + dana + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * As live round the camp: a soldier on a roof 8 blocks up - a drop costing 5 health of 20, more
     * than the squad's LOW risk takes - with nothing to build with, and nobody carrying any. The
     * recovery drill sends it down to its squad at MEDIUM, and it jumps.
     */
    @Test
    void aSoldierStrandedWithNobodyToFetchItJumpsDownToItsSquad() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> y == GROUND + 8 && x >= 6 && x <= 8 && z >= -1 && z <= 1
                ? SimServer.STONE : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(
                        n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 9, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(3 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 4, "Dana still up at " + dana + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertTrue(server.health("Dana") >= 10 && server.deaths.isEmpty(), "Dana came down at health "
                + server.health("Dana") + ", deaths " + server.deaths);
    }

    /**
     * As live: Eli on a ledge, its only way down a fall costing most of its health, nobody in its
     * squad carrying blocks enough to come up for it - but logs in its own pack. It builds its own
     * stair from the top down, and walks down it.
     */
    @Test
    void aSoldierStrandedWithLogsBuildsItsOwnStairDown() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> y == GROUND + 12 && x >= 6 && x <= 8 && z >= -1 && z <= 1
                ? SimServer.STONE : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(
                        n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 13, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.<int[]>of(new int[]{38, SimServer.ITEM_OAK_LOG, 32}) : List.of(
                                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 4, "Dana still up at " + dana + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertTrue(server.health("Dana") == 20 && server.deaths.isEmpty(), "Dana came down hurt: health "
                + server.health("Dana") + ", deaths " + server.deaths);
    }

    /**
     * As at the live respawn point: Dana on a platform 17 blocks over the ground with nothing to
     * build with, her only way down a fall costing most of her health. A squadmate carrying dirt
     * builds steps up to her, and she walks down them.
     */
    @Test
    void aSoldierStrandedOnAPlatformIsFetchedDownByASquadmateWithBlocks() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> y == GROUND + 17 && x >= 6 && x <= 8 && z >= -1 && z <= 1
                ? SimServer.STONE : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(
                        n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 18, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.of() : List.of(
                                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16},
                                new int[]{38, SimServer.ITEM_DIRT, 64})));
        runner.run();

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 4, "Dana still up at " + dana);
        assertTrue(server.health("Dana") == 20 && server.deaths.isEmpty(), "Dana came down hurt: health "
                + server.health("Dana") + ", deaths " + server.deaths);
    }
}
