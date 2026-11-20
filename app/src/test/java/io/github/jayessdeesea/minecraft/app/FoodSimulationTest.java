package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.supply.Pack;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Subsistence;
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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;

/** Class I in the squad simulation: bread baked, rations shared, the camp's farm grown and kept, the hungry fed. */
class FoodSimulationTest {

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

/** Flat, with two one-block ponds east of the camp, 10 apart: neither's plot alone the camp's farm. */
    static int twoPonds(int x, int y, int z) {
        return y == GROUND && z == 0 && (x == 14 || x == 24) ? SimServer.WATER : flat(x, y, z);
    }

    /** A squad by the camp, each soldier carrying the bread in {@code rations}, run for {@code millis}. */
    Shared sharing(String squads, Map<String, Integer> rations, long millis) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster(squads);
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(n -> new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, rations.get(n)})))
                .run();
        clock.advance(millis);
        return new Shared(server);
    }

    /** Flat, with three ponds 12 blocks apart north-east, south-east and north-west of the camp. */
    static int threePonds(int x, int y, int z) {
        boolean pond = y == GROUND && (Math.abs(x - 7) <= 1 && Math.abs(z - 7) <= 1 || Math.abs(x - 7) <= 1 && Math.abs(z + 5) <= 1
                || Math.abs(x + 5) <= 1 && Math.abs(z - 7) <= 1);
        return pond ? SimServer.WATER : flat(x, y, z);
    }

    /** As {@link #pondOffCamp}, the ground round the pond under a layer of dirt. */
    static int buriedPondOffCamp(int x, int y, int z) {
        boolean round = x >= 26 && x <= 34 && z >= -4 && z <= 4 && !(x >= 29 && x <= 31 && z >= -1 && z <= 1);
        return round && y == GROUND + 1 ? SimServer.DIRT : pondOffCamp(x, y, z);
    }

    /**
     * As {@link #pondByCamp}, the pond and the ground round it a block down in a hollow: the farm's
     * ground is a step below the ground round it, as live by the camp's pond.
     */
    static int pondInAHollow(int x, int y, int z) {
        boolean hollow = x >= 2 && x <= 12 && z >= 2 && z <= 12;
        if (hollow && y == GROUND) {
            return SimServer.AIR;
        }
        return hollow && x >= 6 && x <= 8 && z >= 6 && z <= 8 && y == GROUND - 1 ? SimServer.WATER : flat(x, y, z);
    }

    record Shared(SimServer server) {
    }

    /** Flat, with a pond by the camp: farm ground by water. */
    static int pondByCamp(int x, int y, int z) {
        return x >= 6 && x <= 8 && z >= 6 && z <= 8 && y == GROUND ? SimServer.WATER : flat(x, y, z);
    }

    /** Flat, with a pond 30 blocks east of the camp: the camp's farm, a walk off. */
    static int pondOffCamp(int x, int y, int z) {
        return x >= 29 && x <= 31 && z >= -1 && z <= 1 && y == GROUND ? SimServer.WATER : flat(x, y, z);
    }

    /** Flat, with the camp on a 21x21 mound of bedrock three high - no step cut in it - its workshop at the middle: stand, table, furnace. */
    static int campOnAMound(int x, int y, int z) {
        boolean mound = Math.abs(x) <= 10 && Math.abs(z) <= 10;
        return mound && y > GROUND && y <= GROUND + 3 ? SimServer.BEDROCK
                : x == 1 && z == 0 && y == GROUND + 4 ? SimServer.CRAFTING_TABLE
                : x == 0 && z == 1 && y == GROUND + 4 ? SimServer.FURNACE : flat(x, y, z);
    }

    /**
     * The camp's stockpile out of reach (operator, 2026-11-20: a different stockpile site, not someone else sent to it):
     * the camp up a mound nobody climbs, as live (field497-499) on its hill, two soldiers off it with bread to put in its
     * chest can't, and the stockpile moves to where they stand - a chest there, the bread in it.
     */
    @Test
    void aStockpileTwoSoldiersCantReachMovesToWhereTheyStand() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::campOnAMound, 1000);
        server.stockedChest(2, GROUND + 4, 0, SimServer.ITEM_COBBLESTONE, 1);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(name -> new PlayerPosition(name.equals("Alice") ? -14.5 : -15.5, GROUND + 1, -14.5), name -> List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.ITEM_BREAD, 64}, new int[]{39, SimServer.ITEM_BREAD, 64},
                        new int[]{40, SimServer.ITEM_OAK_LOG, 8})));
        runner.run();

        clock.advance(15 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        int bread = server.chests.entrySet().stream().filter(e -> e.getKey() != SimServer.key(2, GROUND + 4, 0))
                .mapToInt(e -> java.util.Arrays.stream(e.getValue()).filter(st -> st != null && st[0] == SimServer.ITEM_BREAD).mapToInt(st -> st[1]).sum()).sum();
        assertTrue(bread >= 64, "bread in a stockpile they could reach: " + bread + "; " + events);
    }

    private static List<String> readLog(String file) {
        try {
            return Files.readAllLines(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A FARM objective with no water near is looked at again a minute on, not each squad cycle: its flood was a twelfth
     * of the garrison's time (p864), the squads on FARM at the old hilltop sites.
     */
    @Test
    void aFarmObjectiveWithNoWaterIsLookedAtAgainAMinuteOn() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Farm", camp, "Food that grows",
                new Objective.Task.Farm())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1})));
        runner.run();

        clock.advance(3 * 60_000);

        long looks = readLog("target/sim/aFarmObjectiveWithNoWaterIsLookedAtAgainAMinuteOn.log").stream()
                .filter(l -> l.contains("[Alpha] no ground to farm by water within")).count();
        assertTrue(looks >= 1 && looks <= 4, "looked at once a minute: " + looks);
    }

    /** The pond by the camp, its ground round it farmland with wheat sprouting: the camp's farm growing, its bread days off. */
    static int farmGrowingByCamp(int x, int y, int z) {
        boolean ground = x >= 3 && x <= 11 && z >= 3 && z <= 11 && !(x >= 6 && x <= 8 && z >= 6 && z <= 8);
        return ground && y == GROUND ? SimServer.FARMLAND : ground && y == GROUND + 1 ? SimServer.WHEAT_SPROUT : pondByCamp(x, y, z);
    }

    /**
     * A soldier on the camp's work carries the surface load - two shovels - and puts the rest in the camp's chest (LoadPlan).
     * Live (field505), with no load but the keep's and the mine's, Gus carried 11 shovels and the salvage went from one
     * full pack to the next.
     */
    @Test
    void aSoldierOnTheCampsWorkPutsItsSpareShovelsInTheCampsChest() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 1 && z == 0 && y == GROUND + 1 ? SimServer.CRAFTING_TABLE
                : x == 0 && z == 1 && y == GROUND + 1 ? SimServer.FURNACE : flat(x, y, z), 1000);
        server.stockedChest(2, GROUND + 1, 0, SimServer.ITEM_COBBLESTONE, 1); // the camp's workshop standing, as live
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        List<int[]> kit = new ArrayList<>(List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                new int[]{38, SimServer.ITEM_OAK_LOG, 8}));
        for (int slot = 9; slot < 15; slot++) {
            kit.add(new int[]{slot, SimServer.itemNamedOrMinus("stone_shovel"), 1});
        }
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), kit));
        runner.run();

        clock.advance(6 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        int shovels = server.chests.values().stream().mapToInt(c -> java.util.Arrays.stream(c)
                .filter(st -> st != null && st[0] == SimServer.itemNamedOrMinus("stone_shovel")).mapToInt(st -> st[1]).sum()).sum();
        assertEquals(4, shovels, "four of six shovels in the camp's chest; " + events);
    }

    /** The pond by the camp, its ground round it farmland under ripe wheat: a harvest waiting. */
    static int farmRipeByCamp(int x, int y, int z) {
        int b = farmGrowingByCamp(x, y, z);
        return b == SimServer.WHEAT_SPROUT ? SimServer.WHEAT_RIPE : b;
    }

    /**
     * A camp with no chest yet - moved, its workshop just sited: its harvesters, with their yield and no dump, fit the
     * camp's chest and put the wheat in it. Live (field503), every harvester's order "no dump yet", 83 harvests and
     * nothing stockpiled, as only a full load fitted a chest.
     */
    @Test
    void harvestersWithNoDumpFitTheCampsChestAndStockIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::farmRipeByCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        Position farm = new Position(7, GROUND + 1, 7);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Farm", farm, "Food that grows",
                new Objective.Task.Farm())), squadLeader(farm),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64}, new int[]{38, SimServer.ITEM_OAK_LOG, 8})));
        runner.run();

        clock.advance(10 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        int wheat = server.chests.values().stream().mapToInt(c -> java.util.Arrays.stream(c)
                .filter(st -> st != null && st[0] == SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT)).mapToInt(st -> st[1]).sum()).sum();
        assertTrue(wheat > 0, "wheat in the camp's chest: " + wheat + "; " + events);
    }

    /**
     * By day, the camp's farm growing is no means for a soldier with no food of its own: it says so - its squad's Class I
     * RED - for the platoon leader's FORAGE (operator, 2026-11-20). Live (field500), all sixteen short with the stores at 0
     * and every squad AMBER on the farm's sprouts, so nobody was sent to forage.
     */
    @Test
    void byDayAFarmGrowingLeavesASoldierWithNoFoodRed() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::farmGrowingByCamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1})));
        runner.run();

        clock.advance(2 * 60_000);

        List<String> log = readLog("target/sim/byDayAFarmGrowingLeavesASoldierWithNoFoodRed.log");
        assertTrue(log.stream().anyMatch(l -> l.contains("Class I RED - request: Alice")), "Alice says she has no food, the farm growing");
    }

    /** Flat, with a field of ripe wheat 20 to 27 east of the camp, three rows wide: wheat standing off the camp's own farm. */
    static int ripeFieldOffCamp(int x, int y, int z) {
        boolean field = x >= 20 && x <= 27 && z >= 0 && z <= 2;
        return field && y == GROUND ? SimServer.FARMLAND : field && y == GROUND + 1 ? SimServer.WHEAT_RIPE : flat(x, y, z);
    }

    /**
     * As live (field497-499): the stores at 0, a squad with no food put on FORAGE - the platoon leader's answer to its
     * Class I RED - harvests the ripe wheat standing off the camp and plants the seeds back in it (operator, 2026-11-20:
     * starving soldiers get their rations within 100 blocks), not stood waiting.
     */
    @Test
    void aSquadOnForageHarvestsRipeWheatOffTheCampAndReplantsIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::ripeFieldOffCamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Forage", camp, "Rations off the land",
                new Objective.Task.Forage())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1})));
        runner.run();

        clock.advance(6 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        long harvested = server.log.stream().filter(l -> l.startsWith("Alice dug " + SimServer.WHEAT_RIPE + " at ")).count();
        int replanted = 0;
        for (int x = 20; x <= 27; x++) {
            for (int z = 0; z <= 2; z++) {
                int b = server.block(x, GROUND + 1, z);
                replanted += b >= SimServer.WHEAT_SPROUT && b <= SimServer.WHEAT_RIPE ? 1 : 0; // harvested, any wheat there is sown again
            }
        }
        assertTrue(harvested >= 6, "ripe wheat harvested: " + harvested + "; " + events);
        assertTrue(replanted >= 3, "and planted back: " + replanted + "; " + events);
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
     * The camp's farm takes in the ground round the next water too, to feed the camp: live, one
     * pond's 44 cells fed eight soldiers a few loaves an hour, and they went hungry.
     */
    @Test
    void theCampsFarmIsGrownRoundTheNextWater() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::twoPonds, 1000);
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

    /**
     * Running low, a soldier asks its squadmates before it goes hungry: Hal, fed but carrying 20 of
     * a basic load of 64 - below its resupply mark, not yet its food-first one - is handed rations
     * by Fran, who carries 40.
     */
    @Test
    void aSoldierRunningLowIsSharedRationsByASquadmateBeforeItGoesHungry() {
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
                        n -> List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, n.equals("Fran") ? 40 : 20})));
        runner.run();

        clock.advance(60_000);

        int hal = server.count("Hal", ItemClassRegistry.ItemClass.FOOD);
        assertTrue(hal > 20, "Hal shared some of Fran's: " + hal + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** Running low with no squadmate to share, a soldier is given rations by a soldier of the other squad. */
    @Test
    void aSoldierNoSquadmateCanFeedIsSharedRationsAcrossSquads() {
        Shared run = sharing("Alpha:Fran;Bravo:Hal", Map.of("Fran", 40, "Hal", 20), 60_000);

        assertTrue(run.server().count("Hal", ItemClassRegistry.ItemClass.FOOD) > 20, "Hal shared some of Fran's; last server events: "
                + run.server().log.subList(Math.max(0, run.server().log.size() - 10), run.server().log.size()));
    }

    /**
     * One ration at a time, no more often than every second, to whoever carries fewest: Fran, with
     * 40, feeds both her low squadmates rather than unloading on one (the operator's; a second a
     * ration, 2026-11-17).
     */
    @Test
    void aGiverSharesOneRationAtATimeRoundEveryoneShort() {
        Shared run = sharing("Alpha:Fran,Gus,Hal", Map.of("Fran", 40, "Gus", 5, "Hal", 5), 20_000);

        int gus = run.server().count("Gus", ItemClassRegistry.ItemClass.FOOD), hal = run.server().count("Hal", ItemClassRegistry.ItemClass.FOOD);
        int given = 40 - run.server().count("Fran", ItemClassRegistry.ItemClass.FOOD);
        assertTrue(gus > 5 && hal > 5, "both fed: Gus " + gus + ", Hal " + hal);
        assertTrue(given <= 20_000 / 1_000 + 1, given + " given in 20 s");
    }

    /**
     * Bread beyond what a soldier carries to share is the force's: Alice, carrying 144, puts a stack of 16
     * in the camp's chest, fitting it at the workshop, and keeps her 128 (Subsistence.CARRY_MOST). (Drawn out by whoever's short:
     * DepositorTest; given across squads first, near: aSoldierNoSquadmateCanFeedIsSharedRationsAcrossSquads.)
     */
    @Test
    void surplusBreadGoesIntoTheCampsChest() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.ITEM_BREAD, 64}, new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{39, SimServer.ITEM_BREAD, 64},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 4})))
                .run();

        clock.advance(2 * 60_000);

        assertEquals(Subsistence.CARRY_MOST, server.count("Alice", ItemClassRegistry.ItemClass.FOOD), "Alice keeps what she carries to share; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * The camp's farm is grown to feed the force a basic load, not only its meals: by three ponds,
     * all three are farmed. Live, a 97-cell farm made about as much bread as eight soldiers ate, and
     * none of them ever carried more than a few rations.
     */
    @Test
    void theCampsFarmIsGrownRoundEveryPondNearItsOwn() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FoodSimulationTest::threePonds, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(30_000);

        String food = chain.foothold().orElseThrow().standards().stream().filter(st -> st.name().equals("food"))
                .findFirst().orElseThrow().status();
        java.util.regex.Matcher cells = java.util.regex.Pattern.compile("of (\\d+) cells of the camp's farm ground").matcher(food);
        assertTrue(cells.find() && Integer.parseInt(cells.group(1)) > 150, "food: " + food);
    }

    /**
     * Safety first: the camp's ground lit, its wall and every soldier armed are its first phase of
     * work, and its food is buildup with its wood and stone - while the force eats.
     */
    @Test
    void shelterAndWeaponsComeFirstAndFoodIsBuildupWhileTheForceEats() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(30_000);

        Map<String, Integer> phases = new HashMap<>();
        chain.foothold().orElseThrow().standards().forEach(st -> phases.put(st.name(), st.phase()));
        assertEquals(Map.of("the camp's ground lit", 2, "food", 3, "every soldier armed", 2,
                "the camp's floor paved", 3, "wood that grows back", 3, "stone and iron", 3, "every soldier armoured", 4), phases);
    }

    /**
     * When survival is at stake - a soldier starving one hit from death with nothing to eat - food
     * stands beside the wall in the first phase of work: the force does what it takes to eat.
     */
    @Test
    void aSoldierStarvingWithNothingToEatPutsFoodBesideTheWall() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1})))
                .run();
        clock.advance(5_000);
        server.starve("Alice", 4);

        clock.advance(30_000);

        Foothold.Standard food = chain.foothold().orElseThrow().standards().stream().filter(st -> st.name().equals("food"))
                .findFirst().orElseThrow();
        assertEquals(2, food.phase(), "food: " + food.status());
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
        SimServer server = new SimServer(clock, FoodSimulationTest::pondOffCamp, 1000);
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
        hungrySquadFarms(FoodSimulationTest::pondByCamp, 0);
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
        hungrySquadFarms(FoodSimulationTest::pondByCamp, 0);
    }

    /**
     * Bread is baked on an errand of its own, not held up by kit it can't make yet: a soldier sent
     * for stone with no pickaxe, no tree in reach, four planks and six wheat makes a table and two
     * loaves. Live, bread asked with copper boots it couldn't smelt was given up with them.
     */
    @Test
    void breadIsBakedWhateverElseIsWaitingOnMaterials() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Quarry stone", camp, "Stone for the wall",
                new Objective.Task.Gather(Resource.STONE))), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{9, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 6},
                        new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.PLANKS), 4})))
                .run();

        clock.advance(90_000);

        assertTrue(server.count("Alice", ItemClassRegistry.ItemClass.FOOD) >= 2, "bread baked; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * A pack isn't filled with seeds: a soldier carrying five stacks throws away all but two. Live,
     * farmers carried 200 to 580 seeds, packs full, and a harvest's wheat had no slot to go to.
     */
    @Test
    void aSoldierThrowsAwayTheSeedsItDoesntNeed() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        int seeds = SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16},
                        new int[]{9, seeds, 64}, new int[]{10, seeds, 64}, new int[]{11, seeds, 64},
                        new int[]{12, seeds, 64}, new int[]{13, seeds, 64})))
                .run();

        clock.advance(20_000);

        assertEquals(Pack.SEEDS_KEPT, server.count("Alice", ItemClassRegistry.ItemClass.SEEDS), "last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 8), server.log.size()));
    }

    /**
     * A farm kept on disk, its ground since buried under dirt: its farmers dig it off, then till,
     * sow and harvest it. Live, a third of the camp's farm lay under blocks the soldiers had put
     * down on it, and nobody took them off again.
     */
    @Test
    void aHungrySquadDigsTheBlocksOffItsBuriedFarm() {
        new WallStore(state, "farm").save("0,0", List.of(new BlockPos(30, GROUND, 0),
                new BlockPos(28, GROUND, -1), new BlockPos(28, GROUND, 0), new BlockPos(28, GROUND, 1),
                new BlockPos(32, GROUND, -1), new BlockPos(32, GROUND, 0), new BlockPos(32, GROUND, 1)));
        hungrySquadFarms(FoodSimulationTest::buriedPondOffCamp, 0);
    }

    /**
     * Live, about half the camp's farm stood bare: farmland trampled back to dirt as fast as it was
     * tilled, by farmers coming down onto it off the step round the farm. A farm in a hollow is
     * farmed without trampling it.
     */
    @Test
    void aFarmInAHollowIsFarmedWithoutTramplingIt() {
        hungrySquadFarms(FoodSimulationTest::pondInAHollow, 1);
    }

    /**
     * A hungry squad keeps the farm on {@code ground}, trampling at most {@code trampledAtMost} of
     * its cells - a farm in a hollow, one: a soldier stepping up out of it off farmland that falls
     * back lands on it from a jump's height (backlog/farm-trampling.md).
     */
    void hungrySquadFarms(SimServer.Terrain ground, int trampledAtMost) {
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
        server.dumpLog("hungrySquadFarms-" + trampledAtMost);
        List<String> trampled = server.log.stream().filter(l -> l.contains("trampled")).toList();
        assertTrue(trampled.size() <= trampledAtMost, "farmland trampled: " + trampled);
    }
}
