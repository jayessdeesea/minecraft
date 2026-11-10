package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
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
import java.util.function.Function;

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
    @org.junit.jupiter.api.BeforeEach
    void nameTheScenario(org.junit.jupiter.api.TestInfo test) {
        org.apache.logging.log4j.ThreadContext.put("scenario", test.getTestMethod().map(java.lang.reflect.Method::getName).orElse("unnamed"));
    }

    @org.junit.jupiter.api.AfterEach
    void endTheScenario() {
        org.apache.logging.log4j.ThreadContext.remove("scenario");
    }

    private static final int GROUND = 63;

    /** Flat: bedrock, stone, dirt, grass at y=63, and iron ore in a band round y=16. */
    private static int flat(int x, int y, int z) {
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
    private static General scripted(Function<SquadStatus, Objective> objective) {
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
                 "intent": "work the objective", "cantco": ""}
                """, at.x(), at.y(), at.z());
        return ChatClient.create((org.springframework.ai.chat.model.ChatModel) prompt ->
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                new int[]{9, SimServer.itemOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.CRAFTING_TABLE), 1},
                new int[]{10, SimServer.itemOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.STICK), 8}), 12);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, true, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{9, SimServer.itemOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.WHEAT), 9},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 2})));
        runner.run();

        clock.advance(3 * 60_000);

        io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass food = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.FOOD;
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16})));
        runner.run();

        java.util.Map<String, java.util.Set<String>> sides = new java.util.HashMap<>();
        for (int second = 0; second < 240; second++) {
            clock.advance(1000);
            for (String name : List.of("Alice", "Bob")) {
                PlayerPosition at = server.position(name);
                // Each side's line is PATROL_RADIUS out from the camp's cell centre, turned 2.5 short of.
                double near = SoldierRunner.PATROL_RADIUS - 2.5;
                String side = at.z() < 0.5 - near ? "N" : at.z() > 0.5 + near ? "S" : at.x() < 0.5 - near ? "W" : at.x() > 0.5 + near ? "E" : null;
                if (side != null) {
                    sides.computeIfAbsent(name, n -> new java.util.TreeSet<>()).add(side);
                }
            }
        }
        assertEquals(java.util.Map.of("Alice", java.util.Set.of("E", "N", "S", "W"), "Bob", java.util.Set.of("E", "N", "S", "W")), sides,
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 40},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4})));
        runner.run();

        clock.advance(3 * 60_000);

        int stored = server.chests.values().stream().flatMap(java.util.Arrays::stream)
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::floodedPitInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(4.5, GROUND - 4, 4.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_DIRT, 64})));
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(8 * 60_000);

        // Level with the ground, nothing open to the sky: a step a soldier built to climb out can
        // seal a pocket under it, underground and harmless.
        List<String> open = new java.util.ArrayList<>();
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) answered.*?(\\d+) settled").matcher(toSquadLeader);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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

    /** Flat, with a pond by the camp: farm ground by water. */
    private static int pondByCamp(int x, int y, int z) {
        return x >= 6 && x <= 8 && z >= 6 && z <= 8 && y == GROUND ? SimServer.WATER : flat(x, y, z);
    }

    /**
     * Field feeding: a squad with no food and nothing to hunt, under another task, keeps the camp's
     * farm - tills, sows, harvests the wheat as it ripens and bakes it - without a FARM order. Live,
     * the farm was tended only under one, and the squads went hungry for hours.
     */
    @Test
    void aHungrySquadKeepsTheCampsFarmWithoutBeingOrderedTo() {
        hungrySquadFarms();
    }

    /**
     * The same after a restart: the camp's farm as it was kept on disk, by its water. Live, a farm
     * loaded from disk was never handed to the squads, and both starved under SECURE beside it.
     */
    @Test
    void aHungrySquadKeepsTheCampsFarmAfterARestart() {
        new WallStore(state, "farm").save("0,0", List.of(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(7, GROUND, 7),
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(5, GROUND, 6), new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(5, GROUND, 7),
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(5, GROUND, 8), new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(6, GROUND, 5),
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(7, GROUND, 5), new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(8, GROUND, 5)));
        hungrySquadFarms();
    }

    private void hungrySquadFarms() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::pondByCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{9, SimServer.itemOf(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.SEEDS), 12},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4})));
        runner.run();

        clock.advance(8 * 60_000);

        io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass wheat = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.WHEAT;
        io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass food = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.FOOD;
        int harvested = server.count("Alice", wheat) + server.count("Bob", wheat) + server.count("Alice", food) + server.count("Bob", food);
        assertTrue(harvested > 0, "wheat harvested, or baked; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
        io.github.jayessdeesea.minecraft.bot.world.WorldView ground = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return flat(x, y, z) != SimServer.AIR;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> layout = io.github.jayessdeesea.minecraft.bot.supply.Perimeter.layout(ground,
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(0, GROUND + 1, 0), 8);
        new WallStore(state).save("0,0", layout);
        runner.run();

        clock.advance(9 * 60_000); // by dusk

        long dirt = layout.stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.DIRT).count();
        assertEquals(0, dirt, "dirt left in the wall; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(4 * 60_000);

        List<io.github.jayessdeesea.minecraft.command.TaskRegister.Tasking> open = runner.taskings("Alpha");
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
        assertEquals(java.util.OptionalInt.of(0), runner.wallLeft("Alpha"), walled.status());
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

        clock.advance(25 * 60_000);

        assertEquals(java.util.OptionalInt.of(0), runner.levellingLeft("Alpha"), "high ground and hollows left inside the wall");
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
                SquadLeaderPrompts.read(new org.springframework.core.io.ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new org.springframework.core.io.ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
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
