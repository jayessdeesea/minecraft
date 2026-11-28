package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.supply.Pack;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Perimeter;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.TaskRegister;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A squad run through a task with the app's own code - runner, chain of command, drills, routines -
 * on simulated time, against {@link SimServer}, with a scripted platoon leader and squad leader: no
 * Docker, no model (backlog/echelon-contracts.md). A simulated hour runs in seconds.
 */
@Tag("slow")
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Patrol the camp", camp, "Trouble met before it arrives",
                new Objective.Task.Patrol())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 40},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4}))
                .build(state);
        sim.runner.run();

        clock.advance(3 * 60_000);

        int stored = server.chests.values().stream().flatMap(Arrays::stream)
                .filter(st -> st != null && st[0] == SimServer.ITEM_COBBLESTONE).mapToInt(st -> st[1]).sum();
        assertEquals(1, server.chests.size(), "one chest, the camp's");
        assertEquals(80, stored, "both loads in it; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * The camp's key blocks are logged as the model holds them, for a console sweep against the
     * server's: its stockpile's chest among them, at its cell and with its state.
     */
    @Test
    void theCampsKeyBlocksAreLoggedForASweepAgainstTheServer() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 40},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4}))
                .start(state);

        clock.advance(SoldierRunner.SWEEP_EVERY_MILLIS + 3 * 60_000);

        List<String> sweeps = Files.readAllLines(Path.of("target/sim/theCampsKeyBlocksAreLoggedForASweepAgainstTheServer.log")).stream()
                .filter(l -> l.contains("[Sweep]")).toList();
        long key = server.chests.keySet().iterator().next();
        int x = (int) (key >> 38), z = (int) (key << 26 >> 38), y = (int) (key & 0xFFF) - 64;
        String cell = x + "," + y + "," + z + "=" + server.block(x, y, z);
        assertTrue(sweeps.size() >= 2 && sweeps.getLast().contains(cell), "the chest " + cell + " in the last of " + sweeps);
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(5 * 60_000);

        for (int y = GROUND + 1; y <= GROUND + 6; y++) {
            assertEquals(SimServer.AIR, server.block(3, y, 3), "the log at y=" + y + "; last server events: "
                    + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        }
        List<String> review = sim.runner.taskingReview("Alpha");
        assertTrue(review.stream().anyMatch(l -> l.matches("  dig the camp's ground: [12] done, 0 taken off unfinished")), review.toString());
    }

    /** Flat, with a pit 6 deep and 3 across inside the camp's wall line. */
    static int pitInCamp(int x, int y, int z) {
        return x >= 3 && x <= 5 && z >= 3 && z <= 5 && y > GROUND - 6 && y <= GROUND ? SimServer.AIR : flat(x, y, z);
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> mine.get()
                ? new Objective("Cut the mine down to the iron", new Position(4, 16, 4), "Stone and iron for the camp", new Objective.Task.Mine())
                : new Objective("Build a patrol base where you stand", camp, "Shelter", new Objective.Task.PatrolBase())).leaderAt(camp).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 64}))
                .build(state);
        sim.runner.run();

        clock.advance(20_000); // the base laid out and begun
        Path log = Path.of("target/sim/aSquadSentFromItsPatrolBaseToTheMineStopsBuildingTheBase.log");
        assertTrue(Files.readAllLines(log).stream().anyMatch(l -> l.contains("Fortifying")), "the base begun");
        mine.set(true);
        clock.advance(6 * 60_000); // the platoon leader's next call, and the squad on its way
        List<String> lines = Files.readAllLines(log);
        List<String> last = lines.subList(Math.max(0, lines.size() - 40), lines.size()).stream()
                .filter(l -> l.contains("Fortifying")).toList();
        assertEquals(List.of(), last, "still building the base");
    }

    /** As {@link #pitInCamp}, with water in its bottom two layers: a flooded shaft. */
    static int floodedPitInCamp(int x, int y, int z) {
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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

    /** Flat, with an old hut's wall of cobblestone two high in the camp. */
    private static int oldHutInCamp(int x, int y, int z) {
        return x >= 3 && x <= 5 && z == 3 && y > GROUND && y <= GROUND + 2 ? SimServer.COBBLESTONE : flat(x, y, z);
    }

    /**
     * The camp's floor is kept clear of what was put on it: an idle squad digs out an old hut's
     * cobblestone, as it digs high ground. Live, old huts' blocks round the camp's chest shut it in.
     */
    @Test
    void anIdleSquadClearsAnOldHutsCobblestoneOffTheCampsFloor() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::oldHutInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(4 * 60_000);

        long hut = 0;
        for (int x = 3; x <= 5; x++) {
            for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                if (server.block(x, y, 3) != SimServer.AIR) {
                    hut++;
                }
            }
        }
        assertEquals(0, hut, "blocks of the old hut left; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * Soldiers with no pickaxe handed the old hut's cobblestone make one from the logs they carry and
     * dig it. Live, each failed at it three times - "no tool to dig the COBBLESTONE" - and reported CANTCO.
     */
    @Test
    void soldiersWithNoPickaxeMakeOneToClearTheCampsFloor() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::oldHutInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4}))
                .start(state);

        clock.advance(5 * 60_000);

        long hut = 0;
        for (int x = 3; x <= 5; x++) {
            for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                hut += server.block(x, y, 3) != SimServer.AIR ? 1 : 0;
            }
        }
        long noTool = Files.readAllLines(Path.of("target/sim/soldiersWithNoPickaxeMakeOneToClearTheCampsFloor.log")).stream()
                .filter(l -> l.contains("no tool to dig")).count();
        assertEquals(0, hut, "blocks of the old hut left; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertEquals(0, noTool, "tries at the cobblestone with no tool to dig it");
    }

    /** Flat, with a mound of dirt two high in the camp. */
    static int moundInCamp(int x, int y, int z) {
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build a patrol base where you stand", camp, "Shelter",
                new Objective.Task.PatrolBase())).leaderAt(camp).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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

    /**
     * A full pack sheds what no routine uses: a soldier whose pack is full of rotten flesh throws some
     * away, keeping room for a harvest's wheat - and keeps its sword and bread. Live, packs held rotten
     * flesh, bones, gravel and phantom membranes, 36 of 36 slots used.
     */
    @Test
    void aFullPackShedsWhatNoRoutineUses() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        int flesh = 1264; // rotten flesh, of no item class: registries.json at the pinned version
        List<int[]> kit = new java.util.ArrayList<>(List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}));
        for (int slot = 9; slot <= 44; slot++) {
            if (slot != 36 && slot != 37) {
                kit.add(new int[]{slot, flesh, 1});
            }
        }
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .kit(kit)
                .start(state);

        clock.advance(20_000);

        assertTrue(server.count("Alice", flesh) <= 34 - Pack.FULL_AT - 1, server.count("Alice", flesh) + " rotten flesh");
        assertEquals(16, server.count("Alice", ItemClassRegistry.ItemClass.FOOD), "its bread kept");
        assertEquals(1, server.count("Alice", SimServer.ITEM_WOODEN_SWORD), "its sword kept");
    }

    /**
     * A tree felled whole and replanted (operator, 2026-11-17: get all the wood, so the trees fall,
     * so you get saplings and replant): one spruce six logs tall west of the keep, a cutter with
     * saplings. Every log comes down - the top ones at a body's reach - and a sapling goes in where it
     * stood. Live, cutters took what was in reach and walked on, and nothing was replanted.
     */
    @Test
    void aTreeIsFelledWholeAndReplanted() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -6 && x <= 5 && z >= -6 && z <= 5 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT
                : x == -40 && z == 0 && y > GROUND && y <= GROUND + 6 ? SimServer.SPRUCE_LOG
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .connector(new PlayerPosition(-8.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{39, SimServer.itemNamedOrMinus("stone_shovel"), 1},
                        new int[]{40, SimServer.itemNamedOrMinus("stone_axe"), 1},
                        new int[]{41, SimServer.itemNamedOrMinus("spruce_sapling"), 8},
                        new int[]{10, SimServer.ITEM_DIRT, 64}))
                .start(state);

        clock.advance(10 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        for (int y = GROUND + 1; y <= GROUND + 6; y++) {
            assertTrue(server.block(-40, y, 0) != SimServer.SPRUCE_LOG, "the log at y=" + y + " cut; " + events);
        }
        assertEquals(SimServer.SPRUCE_SAPLING, server.block(-40, GROUND + 1, 0), "a sapling where it stood; " + events);
    }

    /**
     * Sent to grow trees on ground of the platoon leader's choosing, a squad plants the camp's tree chunk when it has one
     * (operator, 2026-11-21: one small flat base, the renewables close by): live (field645), Charlie was sent onto the
     * camp's hill at y=69, inside the ground being flattened, its 7 cells shared among four and Kate stood 3 minutes
     * without a share, while the camp's tree chunk stood sited and bare.
     */
    @Test
    void aSquadGrowingTreesPlantsTheCampsTreeChunk() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position elsewhere = new Position(0.5, GROUND + 1, 60.5);
        SimSquad.of(clock, server, roster)
                .orders(st -> new Objective("Grow trees", elsewhere, "Wood that grows back",
                new Objective.Task.TreeFarm())).leaders(DrillSquadLeader::new)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_axe"), 1},
                        new int[]{39, SimServer.itemNamedOrMinus("spruce_sapling"), 32}))
                .start(state);

        clock.advance(6 * 60_000);

        List<String> log = Files.readAllLines(Path.of("target/sim/aSquadGrowingTreesPlantsTheCampsTreeChunk.log"));
        java.util.regex.Matcher sited = log.stream().map(l -> java.util.regex.Pattern.compile("tree chunk at chunk \\((-?\\d+), (-?\\d+)\\)").matcher(l))
                .filter(java.util.regex.Matcher::find).findFirst().orElseThrow(() -> new AssertionError("no tree chunk sited"));
        int cx = Integer.parseInt(sited.group(1)) * 16, cz = Integer.parseInt(sited.group(2)) * 16;
        int inChunk = 0, there = 0;
        for (int x = -80; x <= 80; x++) {
            for (int z = -80; z <= 80; z++) {
                if (server.block(x, GROUND + 1, z) == SimServer.SPRUCE_SAPLING) {
                    inChunk += x >= cx && x < cx + 16 && z >= cz && z < cz + 16 ? 1 : 0;
                    there += Math.hypot(x - elsewhere.x(), z - elsewhere.z()) <= 8 ? 1 : 0;
                }
            }
        }
        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertTrue(inChunk >= 4, inChunk + " saplings in the tree chunk at (" + cx + ", " + cz + "); " + events);
        assertEquals(0, there, "none on the ground the order pointed at");
    }

    /**
     * The camp's tree chunk planted, then lit and its chest put up by the squad growing it, with what it carries
     * (operator, 2026-11-21: nine ground torches, a chest for its saplings): the stage PLANTED to LIT.
     */
    @Test
    void theTreeChunkIsLitAndItsChestPutUpOncePlanted() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad.of(clock, server, roster)
                .orders(st -> new Objective("Grow trees", new Position(0.5, GROUND + 1, 60.5),
                "Wood that grows back", new Objective.Task.TreeFarm())).leaders(DrillSquadLeader::new)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_axe"), 1},
                        new int[]{39, SimServer.itemNamedOrMinus("spruce_sapling"), 16},
                        new int[]{40, SimServer.itemNamedOrMinus("torch"), 16},
                        new int[]{41, SimServer.itemNamedOrMinus("chest"), 1}))
                .start(state);

        clock.advance(10 * 60_000);

        List<String> log = Files.readAllLines(Path.of("target/sim/theTreeChunkIsLitAndItsChestPutUpOncePlanted.log"));
        java.util.regex.Matcher sited = log.stream().map(l -> java.util.regex.Pattern.compile("tree chunk at chunk \\((-?\\d+), (-?\\d+)\\)").matcher(l))
                .filter(java.util.regex.Matcher::find).findFirst().orElseThrow(() -> new AssertionError("no tree chunk sited"));
        io.github.jayessdeesea.minecraft.bot.farm.TreeChunk chunk = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(
                Integer.parseInt(sited.group(1)), Integer.parseInt(sited.group(2)), GROUND);
        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        long lit = chunk.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.TORCH).count();
        assertEquals(9, lit, "its nine ground torches; " + events);
        assertEquals(SimServer.CHEST, server.block(chunk.chest().x(), chunk.chest().y(), chunk.chest().z()), "its chest; " + events);
    }

    /**
     * The platoon leader wakes on events, not on a clock (backlog/fleet-command.md): a squad digging a
     * big mound on the keep by day for eight minutes, nothing finished, nothing asked - it's woken once,
     * for the squad that had no objective, where every two minutes it was asked again.
     */
    @Test
    void aQuietDaysWorkWakesThePlatoonLeaderOnlyForWhatNeedsIt() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -12 && x <= 11 && z >= -12 && z <= 11 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        SimSquad.of(clock, server, roster)
                .orders(st -> {
            asked.incrementAndGet();
            return new Objective("Build the keep", Keep.CENTRE, "The main base's keep", new Objective.Task.Keep());
        }).leaders(DrillSquadLeader::new).home("")
                .connector(new PlayerPosition(-14.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64}))
                .start(state);

        clock.advance(8 * 60_000);

        List<String> wakes = Files.readAllLines(Path.of("target/sim/aQuietDaysWorkWakesThePlatoonLeaderOnlyForWhatNeedsIt.log")).stream()
                .filter(l -> l.contains("[PL] wakes:")).toList();
        assertEquals(1, wakes.size(), "woken once, for the squad with no objective: " + wakes);
        assertTrue(wakes.getFirst().contains("has no objective"), wakes.toString());
    }

    /**
     * The camp's ground is its surface, whoever is wherever: live, restarted at night with every
     * soldier down the mine, it was read off a cave floor at y=28.
     */
    @Test
    void theCampsGroundIsItsSurfaceThoughTheSquadIsDownACaveUnderIt() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> Math.abs(x) <= 6 && Math.abs(z) <= 6 && y >= 28 && y <= 31 ? SimServer.AIR
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .connector(new PlayerPosition(0.5, 28, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .start(state);

        clock.advance(30_000);

        String said = Files.readAllLines(Path.of("target/sim/theCampsGroundIsItsSurfaceThoughTheSquadIsDownACaveUnderIt.log")).stream()
                .filter(l -> l.contains("the camp's ground is")).findFirst().orElseThrow(() -> new AssertionError("no camp's ground"));
        assertTrue(said.contains("y=" + (GROUND + 1)), said);
    }

    /**
     * A soldier without a bed hunts sheep for their wool and makes one: three white wool over three
     * planks (backlog/rest-plan.md).
     */
    @Test
    void aSoldierWithoutABedHuntsSheepForOne() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.PLANKS), 8}))
                .start(state);
        for (int i = 0; i < 3; i++) {
            server.spawnSheep(6.5 + 2 * i, GROUND + 1, 4.5);
        }

        clock.advance(90_000);

        assertEquals(1, server.count("Alice", ItemClassRegistry.ItemClass.BED), "a bed made; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertEquals(0, server.count("Alice", ItemClassRegistry.ItemClass.WOOL), "its wool all in the bed");
    }

    /**
     * A patrol base in the camp is sited off its workshop. Live, the night's hut at the camp's
     * centre sealed the workshop's chest inside it, and the miners stood all night unable to put
     * their loads away.
     */
    @Test
    void theNightsBaseInTheCampLeavesItsWorkshopClear() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build a patrol base in the camp", camp, "Safe",
                new Objective.Task.PatrolBase())).leaderAt(camp)
                .connector(new PlayerPosition(4.5, GROUND + 1, 9.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64}))
                .start(state);

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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())).leaderAt(camp)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(4 * 60_000);

        List<TaskRegister.Tasking> open = sim.runner.taskings("Alpha");
        assertTrue(open.stream().noneMatch(t -> t.task().equals("dig the camp's ground")), "taken off it: " + open);
        assertTrue(open.stream().anyMatch(t -> t.task().equals("wall the camp")), "on to the wall: " + open);
        List<String> review = sim.runner.taskingReview("Alpha");
        assertTrue(review.stream().anyMatch(l -> l.matches("  dig the camp's ground: 0 done, [12] taken off unfinished")), review.toString());
    }

    /**
     * FORTIFY's levelling on a slope: the high ground inside the wall line dug down to the camp's
     * height and the low ground filled up to it, before the wall.
     */
    @Test
    void aSquadLevelsTheCampOnASlope() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::slope, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 3, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Wall the camp in", camp, "Nothing walks in",
                new Objective.Task.Fortify())).leaderAt(camp)
                .connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(25 * 60_000);

        server.dumpLog("aSquadLevelsTheCampOnASlope");
        assertEquals(OptionalInt.of(0), sim.runner.levellingLeft("Alpha"), "high ground and hollows left inside the wall");
    }

    /**
     * A patrol base on a slope, two zombies on the squad before the hut is up: the squad fights, then
     * goes back to the same hut and seals itself in. Live, each fight re-sited the base and the
     * squad laid out hut after hut in the open.
     */
    @Test
    void aFightWhileTheHutGoesUpDoesntMoveIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::slope, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position site = new Position(0, GROUND + 3, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build a patrol base here", site,
                "Squad safe from monsters until dawn", new Objective.Task.PatrolBase())).leaderAt(site).home("")
                .connector(new PlayerPosition(0.5, GROUND + 3, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{38, SimServer.ITEM_DIRT, 64}))
                .build(state);
        sim.runner.run();

        clock.advance(3000); // the squad on its way to the site
        server.spawnZombie(3.5, GROUND + 4, 3.5);
        server.spawnZombie(-2.5, GROUND + 3, -2.5);
        clock.advance(5 * 60_000);

        assertTrue(server.zombiesLeft() == 0, server.zombiesLeft() + " zombies still standing");
        SoldierRunner.BaseReport base = sim.runner.baseReport("Alpha").orElseThrow(() -> new AssertionError("no patrol base"));
        assertTrue(base.allInside() && base.sealed(), "after the fight: " + base + "; deaths " + server.deaths);
    }

    /**
     * Flat, with a dead end 25 blocks down in a block of stone: a gallery two high from (10, 0) to
     * (30, 0), roofed by the stone - as at the bottom of a mine, too long a room for the shut-in
     * drill (§14), which cuts out of a hut.
     */
    static int deepPocketBelow(int x, int y, int z) {
        boolean open = x >= 10 && x <= 30 && z == 0 && y >= GROUND - 25 && y <= GROUND - 24;
        boolean rock = x >= -20 && x <= 40 && z >= -30 && z <= 30 && y >= GROUND - 30 && y <= GROUND;
        return open ? SimServer.AIR : rock ? SimServer.STONE : flat(x, y, z);
    }

}
