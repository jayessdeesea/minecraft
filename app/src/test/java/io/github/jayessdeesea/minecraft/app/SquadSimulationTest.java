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
        assertTrue(review.stream().anyMatch(l -> l.matches("  clear the camp's sky: [12] done, 0 taken off unfinished")), review.toString());
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
        assertTrue(open.stream().noneMatch(t -> t.task().equals("clear the camp's sky")), "taken off it: " + open);
        assertTrue(open.stream().anyMatch(t -> t.task().equals("wall the camp")), "on to the wall: " + open);
        List<String> review = runner.taskingReview("Alpha");
        assertTrue(review.stream().anyMatch(l -> l.matches("  clear the camp's sky: 0 done, [12] taken off unfinished")), review.toString());
    }

    /** Ground rising a block every 3 east of x=-6: the camp's wall stands on many heights. */
    private static int slope(int x, int y, int z) {
        int top = GROUND + Math.max(0, Math.floorDiv(x + 6, 3));
        return y <= -64 ? SimServer.BEDROCK : y < 55 ? SimServer.STONE : y < top ? SimServer.DIRT
                : y == top ? SimServer.GRASS : SimServer.AIR;
    }

    /**
     * FORTIFY on a slope: the squad levels the high ground inside the wall line first, then digs its
     * own dirt and closes the wall round the camp - in a day and a few minutes.
     */
    @Test
    void aSquadWallsInTheCampOnASlope() {
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

        clock.advance(25 * 60_000); // levelled, then walled

        // Closed - of dirt, with no stone to hand, so the standard itself waits on stone.
        Foothold.Standard walled = chain.foothold().orElseThrow().standards().stream()
                .filter(st -> st.name().equals("the camp walled in")).findFirst().orElseThrow();
        assertEquals(java.util.OptionalInt.of(0), runner.wallLeft("Alpha"), "after a day: " + walled.status());
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
