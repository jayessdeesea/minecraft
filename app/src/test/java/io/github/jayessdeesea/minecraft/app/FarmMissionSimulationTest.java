package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.FlattenPlan;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Slopes;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operator's farm mission in the squad simulation (operator, 2026-11-21): a farm at a chunk carried out by doctrine -
 * the chunk flattened a block below the farm's level, its slopes a block a block out, floored in cobblestone there, the
 * farm template on the floor - each step an objective in the platoon leader's register, started when the one before is
 * measured done.
 */
@Tag("slow")
class FarmMissionSimulationTest {

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

    /** The mission's chunk: (2, 0), x 32..47 z 0..15, its farm at sea level. */
    static final FarmMission FARM = FarmMission.atSeaLevel(2, 0);
    static final int LEVEL = FarmMission.SEA_LEVEL;

    /**
     * Flat at sea level with a pond by the camp - the camp's own farm - and hills: a mound in the chunk three over the
     * ground, and a ridge four over it from a block east of the chunk out, which the east slope cuts into.
     */
    static int hills(int x, int y, int z) {
        if (y == GROUND && x == 10 && z == 0) {
            return SimServer.WATER;
        }
        boolean mound = x >= 36 && x <= 44 && z >= 4 && z <= 12 && y > GROUND && y <= GROUND + 3;
        boolean ridge = x >= 48 && x <= 58 && z >= 0 && z <= 15 && y > GROUND && y <= GROUND + 4;
        return mound || ridge ? SimServer.DIRT : SquadSimulationTest.flat(x, y, z);
    }

    /** Each soldier's kit: tools, food, two stacks of cobblestone for the floor, dirt for the template's ground, seeds. */
    static List<int[]> kit() {
        return List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{11, SimServer.ITEM_DIRT, 64}, new int[]{12, SimServer.ITEM_DIRT, 64},
                new int[]{13, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 64}, new int[]{14, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 64});
    }

    /** A squad of four on the camp's farm by the platoon leader's own order, the operator's farm given at the start. */
    private SimServer run(SimClock clock, SimServer.Terrain world, PlayerPosition spawn, String camp, long minutes) {
        return run(clock, world, spawn, camp, minutes, kit());
    }

    /** As {@link #run(SimClock, SimServer.Terrain, PlayerPosition, String, long)}, each soldier joining with {@code kit}. */
    private SimServer run(SimClock clock, SimServer.Terrain world, PlayerPosition spawn, String camp, long minutes, List<int[]> kit) {
        SimServer server = new SimServer(clock, world, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = FARM.centre(FARM.steps().getFirst());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, camp, state.toString());
        chain.onOperatorFarm("Pat", FARM);
        runner.simulated(clock, server.connector(spawn, kit)).run();
        clock.advance(minutes * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        assertEquals(0, runner.afterActionReview.errands.loops(), "no soldier walking trip after trip for nothing (ErrandLedger.LOOP_TRIPS)");
        return server;
    }

    /**
     * The eval: the template's ground is the whole squad's to lay (operator, 2026-11-22). The same hilly chunk, but nobody
     * joins carrying dirt: what the squad has is what its flatten dug, spread over the four of them. Live (field692), a
     * floored chunk wanted 247 cells of dirt, its one tender laid the 11 it carried and stood, and the farm never came.
     * Every cell of the template's ground laid, and wheat sown on it.
     */
    @Test
    void aSquadJoiningWithNoDirtLaysTheTemplatesGroundBetweenThem() throws IOException {
        SimClock clock = new SimClock();
        List<int[]> noDirt = kit().stream().filter(slot -> slot[1] != SimServer.ITEM_DIRT).toList();
        SimServer server = run(clock, FarmMissionSimulationTest::hills, new PlayerPosition(30.5, GROUND + 1, 8.5), "0,0", 90, noDirt);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aSquadJoiningWithNoDirtLaysTheTemplatesGroundBetweenThem.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]")).toList();

        ChunkFarm farm = new ChunkFarm(FARM.chunkX(), FARM.chunkZ(), LEVEL);
        List<String> bare = farm.ground().stream().filter(g -> server.block(g.x(), g.y(), g.z()) != SimServer.DIRT
                && server.block(g.x(), g.y(), g.z()) != SimServer.FARMLAND && server.block(g.x(), g.y(), g.z()) != SimServer.GRASS)
                .map(g -> g.x() + "," + g.z() + "=" + server.block(g.x(), g.y(), g.z())).toList();
        assertEquals(List.of(), bare, "every cell of the template's ground laid" + missionLines);
        long layers = log.stream().filter(l -> l.contains("[Register]") && l.contains("on lay the farm's ground")).map(l -> l.replaceAll(".*\\[Register\\] (\\w+) on .*", "$1"))
                .distinct().count();
        assertTrue(layers >= 3, "the squad laid it between them, not one tender: " + layers + " of 4 were on it" + missionLines);
        Set<String> ground = new HashSet<>();
        farm.ground().forEach(g -> ground.add(g.x() + "," + (g.y() + 1) + "," + g.z()));
        long sown = List.copyOf(server.log).stream().filter(l -> l.contains(" sowed ")).map(l -> l.substring(l.indexOf(" sowed ") + 7)).filter(ground::contains)
                .distinct().count();
        assertTrue(sown >= 150, "wheat sown on the template's ground: " + sown + " of " + ground.size() + missionLines);
    }

    /** The tree farm's chunk: (2, 0) too, its ground at sea level. */
    static final FarmMission TREES = new FarmMission(2, 0, GROUND, FarmMission.Crop.TREES);

    /**
     * The eval: a chunk with a mound given the tree farm mission (operator, 2026-11-22: a command for the templated tree
     * chunk). Nothing of the ground left over its level; a sapling at each of its sixteen trunks, its chest and its nine
     * torches in; and its two steps in the objective register DONE, in order.
     */
    @Test
    void aTreeFarmIsFlattenedPlantedLitAndItsChestPutUp() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::hills, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = TREES.centre(TREES.steps().getFirst());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        chain.onOperatorFarm("Pat", TREES);
        runner.simulated(clock, server.connector(new PlayerPosition(30.5, GROUND + 1, 8.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.itemNamedOrMinus("spruce_sapling"), 16},
                new int[]{10, SimServer.itemNamedOrMinus("torch"), 16}, new int[]{11, SimServer.itemNamedOrMinus("chest"), 1}))).run();
        clock.advance(45 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aTreeFarmIsFlattenedPlantedLitAndItsChestPutUp.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]") || l.contains("[Trees]")).toList();

        List<String> over = new ArrayList<>();
        for (int x = TREES.minX(); x <= TREES.minX() + 15; x++) {
            for (int z = TREES.minZ(); z <= TREES.minZ() + 15; z++) {
                if (groundTop(server, x, z) > GROUND) {
                    over.add(x + "," + z + " at " + groundTop(server, x, z));
                }
            }
        }
        assertEquals(List.of(), over, "nothing of the ground over the tree farm's level" + missionLines);

        io.github.jayessdeesea.minecraft.bot.farm.TreeChunk template = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(TREES.chunkX(), TREES.chunkZ(), GROUND);
        // A sapling, or the tree it has grown into since: in the simulation a sapling grows in minutes.
        long planted = template.trunks().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_SAPLING
                || server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_LOG).count();
        assertEquals(16, planted, "a sapling or its tree at each trunk" + missionLines);
        assertEquals(9, template.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.TORCH).count(), "its nine torches" + missionLines);
        assertEquals(SimServer.CHEST, server.block(template.chest().x(), template.chest().y(), template.chest().z()), "its chest" + missionLines);

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(TREES.name())).toList();
        assertEquals(List.of(0, 1), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's two steps: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.DONE), "each DONE: " + rows + missionLines);
    }

    /** The supply point's chunk: (2, 0) too, its ground at sea level. */
    static final FarmMission STORES = new FarmMission(2, 0, GROUND, FarmMission.Crop.STORES);

    /**
     * The eval: a chunk with a mound given the supply point mission (operator, 2026-11-22: a supply dump near the farms).
     * Its ground cobblestone at its level, nothing over it but what's put there; its nine torches, its table and four
     * furnaces, and the twenty-four chests of its first two rows, each in the cell the layout names; no soldier walking
     * trip after trip for nothing; and its three steps in the objective register DONE, in order.
     */
    @Test
    void aSupplyPointIsFlattenedPavedLitAndItsFirstRowsPutUp() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::hills, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = STORES.centre(STORES.steps().getFirst());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        chain.onOperatorFarm("Pat", STORES);
        runner.simulated(clock, server.connector(new PlayerPosition(30.5, GROUND + 1, 8.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{11, SimServer.ITEM_OAK_LOG, 64}, new int[]{12, SimServer.ITEM_TORCH, 16}))).run();
        clock.advance(90 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aSupplyPointIsFlattenedPavedLitAndItsFirstRowsPutUp.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        io.github.jayessdeesea.minecraft.bot.farm.StoresChunk template = new io.github.jayessdeesea.minecraft.bot.farm.StoresChunk(STORES.chunkX(), STORES.chunkZ(), GROUND);
        long paved = template.ground().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).count();
        assertEquals(256, paved, "its ground cobblestone" + missionLines);
        assertEquals(9, template.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.TORCH).count(), "its nine torches" + missionLines);
        assertEquals(SimServer.CRAFTING_TABLE, server.block(template.table().x(), template.table().y(), template.table().z()), "its table" + missionLines);
        assertEquals(4, template.furnaces().stream().filter(f -> server.block(f.x(), f.y(), f.z()) == SimServer.FURNACE).count(), "its four furnaces" + missionLines);
        long chests = io.github.jayessdeesea.minecraft.bot.farm.StoresChunk.FIRST_ROWS.stream().flatMap(row -> template.chests(row).stream())
                .filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.CHEST).count();
        assertEquals(24, chests, "the chests of its food and building rows" + missionLines);
        assertEquals(0, runner.afterActionReview.errands.loops(), "no soldier walking trip after trip for nothing");

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(STORES.name())).toList();
        assertEquals(List.of(0, 1, 2), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's three steps: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.DONE), "each DONE: " + rows + missionLines);

        // The review (operator, 2026-11-22: who reviews what's built, and fixes it?): a chest and a torch gone from the built
        // supply point - its template is opened again, and they're put back.
        BlockPos chest = template.chests(io.github.jayessdeesea.minecraft.bot.farm.StoresChunk.Row.FOOD).get(4);
        BlockPos torch = template.torches().get(4);
        server.set(chest.x(), chest.y(), chest.z(), SimServer.AIR);
        server.set(torch.x(), torch.y(), torch.z(), SimServer.AIR);
        clock.advance(30 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> after = Files.readAllLines(Path.of("target", "sim", "aSupplyPointIsFlattenedPavedLitAndItsFirstRowsPutUp.log")).stream()
                .filter(l -> l.contains("[Mission]") && (l.contains("reviewed") || l.contains("DONE") || l.contains("done"))).toList();
        assertTrue(after.stream().anyMatch(l -> l.contains("reviewed - back at")), "the review found it: " + after);
        assertEquals(SimServer.CHEST, server.block(chest.x(), chest.y(), chest.z()), "the chest put back: " + after);
        assertEquals(SimServer.TORCH, server.block(torch.x(), torch.y(), torch.z()), "the torch put back: " + after);
        assertTrue(new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(STORES.name()))
                .allMatch(r -> r.state() == ObjectiveRecord.State.DONE), "and its template done again: " + after);
    }

    /** The highest of the ground's own blocks - soil, stone, cobblestone - at a column: no fence, torch or crop. */
    static int groundTop(SimServer server, int x, int z) {
        for (int y = GROUND + 12; y > GROUND - 8; y--) {
            int b = server.block(x, y, z);
            if (b == SimServer.DIRT || b == SimServer.GRASS || b == SimServer.STONE || b == SimServer.COBBLESTONE || b == SimServer.FARMLAND) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The eval: a hilly chunk given the farm mission. Its y=62 layer all cobblestone; its east slope a block a block out
     * and the ridge past it standing; nothing of the ground - no step put down to climb - left over the farm's level in the
     * chunk; wheat sown on its ground at y=64; and its three steps in the objective register PLANNED, under way and DONE,
     * in order.
     */
    @Test
    void aHillyChunkIsFlattenedFlooredAndFarmed() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = run(clock, FarmMissionSimulationTest::hills, new PlayerPosition(30.5, GROUND + 1, 8.5), "0,0", 90);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aHillyChunkIsFlattenedFlooredAndFarmed.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        List<String> unpaved = new ArrayList<>();
        for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
            for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                if (server.block(x, LEVEL - 1, z) != SimServer.COBBLESTONE) {
                    unpaved.add(x + "," + z + "=" + server.block(x, LEVEL - 1, z));
                }
            }
        }
        assertEquals(List.of(), unpaved, "the floor at y=" + (LEVEL - 1) + " all cobblestone" + missionLines);

        // The slope: a block a block out east, the ridge past it its own; and the ground round the chunk on every side no higher.
        List<String> steep = new ArrayList<>();
        for (FlattenPlan.Column c : FlattenPlan.reach(FARM.chunkX(), FARM.chunkZ(), LEVEL - 1, Slopes.ALL, (x, z) -> false)) {
            boolean inside = c.x() >= FARM.minX() && c.x() <= FARM.minX() + 15 && c.z() >= FARM.minZ() && c.z() <= FARM.minZ() + 15;
            int top = groundTop(server, c.x(), c.z());
            if (!inside && top > c.to() && !(c.x() == FARM.minX() + 16 || c.z() == FARM.minZ() + 16) || inside && top > LEVEL) {
                steep.add(c.x() + "," + c.z() + " at " + top + " over " + (inside ? LEVEL : c.to()));
            }
        }
        assertEquals(List.of(), steep, "nothing over the slope a block a block out, nor over the farm's ground in the chunk - no step left" + missionLines);
        assertEquals(SimServer.DIRT, server.block(53, GROUND + 4, 8), "the ridge past the slope stands");
        assertEquals(GROUND + 1, groundTop(server, 49, 8), "two out east, a block over the farm's ground");

        // Sown, as the server saw it: in the simulation wheat ripens in minutes, and is harvested and sown again.
        Set<String> ground = new HashSet<>();
        new ChunkFarm(FARM.chunkX(), FARM.chunkZ(), LEVEL).ground().forEach(g -> ground.add(g.x() + "," + (g.y() + 1) + "," + g.z()));
        long sown = List.copyOf(server.log).stream().filter(l -> l.contains(" sowed ")).map(l -> l.substring(l.indexOf(" sowed ") + 7)).filter(ground::contains)
                .distinct().count();
        assertTrue(sown >= 150, "wheat sown on the template's ground at y=" + (LEVEL + 1) + ": " + sown + " of " + ground.size() + missionLines);

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(FARM.name())).toList();
        assertEquals(List.of(0, 1, 2), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's three steps: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.DONE && r.squad().equals(Optional.of("Alpha"))), "each DONE, Alpha's: " + rows);
        // In order: planned, then each step under way only once the one before it is done.
        int planned = indexOf(log, "[Objectives] " + FARM.name() + ": 3 step(s) PLANNED");
        int last = planned;
        for (FarmMission.Step step : FARM.steps()) {
            int underWay = indexOf(log, "[Objectives] Alpha: " + FARM.name() + " (" + step.task() + ") under way");
            int done = indexOf(log, "[Objectives] Alpha: " + FARM.said(step) + " DONE");
            assertTrue(planned >= 0 && last <= underWay && underWay < done, step.task() + ": planned " + planned + ", under way " + underWay
                    + ", done " + done + " after " + last + missionLines);
            last = done;
        }
        assertTrue(new Registers(state.resolve("registers")).chunk(FARM.chunkX(), FARM.chunkZ()).filter(c -> c.kind() == ChunkRecord.Kind.WHEAT).isPresent(),
                "the chunk register has it a wheat chunk");
    }

    /**
     * The chunk farm a mission at another level left on the mission's chunk - its template's ground there, farmland and
     * wheat: two over sea level, live's y=67 four, the same give-up a lighter dig.
     */
    static final ChunkFarm HIGH = new ChunkFarm(FARM.chunkX(), FARM.chunkZ(), 65);

    /** Flat by the camp's pond, the mission's chunk built up to {@link #HIGH}'s floor and its ground farmland, wheat on it. */
    static int raisedFarm(int x, int y, int z) {
        if (y == GROUND && x == 10 && z == 0) {
            return SimServer.WATER;
        }
        BlockPos c = new BlockPos(x, y, z);
        boolean inChunk = x >= FARM.minX() && x <= FARM.minX() + 15 && z >= FARM.minZ() && z <= FARM.minZ() + 15;
        if (inChunk && HIGH.ground().contains(c)) {
            return SimServer.FARMLAND;
        }
        if (inChunk && HIGH.ground().contains(c.offset(0, -1, 0))) {
            return SimServer.WHEAT_SPROUT;
        }
        if (inChunk && y > GROUND && y < HIGH.level()) {
            return SimServer.DIRT;
        }
        return SquadSimulationTest.flat(x, y, z);
    }

    /**
     * Live (field668-682): a farm mission given at y=67 was superseded by the operator's at sea level - but the y=67
     * mission's template had been taken into the camp's farm, and stood in it. The farm squad tilled and sowed it, the
     * flatten's dig kept the camp's farm out, and its builders cut round the wheat (operator, 2026-11-22: "he's working
     * around the blocks with wheat on them"). The camp's farm kept on disk with that chunk farm in it, and the sea-level
     * mission given: the chunk farm given up, and the old farm's ground - its farmland and the wheat on it - cut away. (The
     * layer under it, dug out and put back by its builders cell by cell here, is a stall of its own.)
     */
    @Test
    void aChunkFarmLeftByASupersededMissionIsGivenUpAndFlattened() throws IOException {
        WallStore farms = new WallStore(state, "farm");
        farms.save("0,0", List.of(new BlockPos(10, GROUND, 0), new BlockPos(11, GROUND, 0)));
        farms.save("0,0,chunks", List.of(new BlockPos(HIGH.chunkX(), HIGH.level(), HIGH.chunkZ())));
        SimClock clock = new SimClock();
        SimServer server = run(clock, FarmMissionSimulationTest::raisedFarm, new PlayerPosition(30.5, GROUND + 1, 8.5), "0,0", 60);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aChunkFarmLeftByASupersededMissionIsGivenUpAndFlattened.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("gives up")).limit(20).toList();
        assertTrue(log.stream().anyMatch(l -> l.contains("gives up the chunk farm") && l.contains("y=" + HIGH.level())), "the stale chunk farm given up" + missionLines);
        List<String> high = new ArrayList<>();
        for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
            for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                if (groundTop(server, x, z) >= HIGH.level()) {
                    high.add(x + "," + z + " at " + groundTop(server, x, z));
                }
            }
        }
        assertEquals(List.of(), high.stream().limit(12).toList(), "none of the old farm's ground left - its level and up" + missionLines);
    }

    /** The farm built beside the mission's chunk, on chunk (1, 0): its ground at sea level, fenced, watered and sown. */
    static final ChunkFarm BESIDE = new ChunkFarm(1, 0, LEVEL);

    /** {@link #hills}, with {@link #BESIDE}'s template standing west of the mission's chunk, the mound against their shared line. */
    static int besideAFarm(int x, int y, int z) {
        BlockPos c = new BlockPos(x, y, z);
        if (x == 76 && y == GROUND && z == 8) {
            return SimServer.WATER; // the camp's pond
        }
        if (BESIDE.holes().contains(c)) {
            return SimServer.WATER;
        }
        if (BESIDE.ground().contains(c)) {
            return SimServer.FARMLAND;
        }
        if (BESIDE.ground().contains(c.offset(0, -1, 0))) {
            return SimServer.WHEAT_SPROUT;
        }
        if (BESIDE.fence().contains(c)) {
            return SimItems.FENCE;
        }
        if (BESIDE.gates().contains(c)) {
            return SimItems.GATE;
        }
        if (BESIDE.torches().contains(c)) {
            return SimServer.TORCH;
        }
        boolean mound = x >= 33 && x <= 38 && z >= 2 && z <= 14 && y > GROUND && y <= GROUND + 3;
        return mound ? SimServer.DIRT : hills(x, y, z);
    }

    /**
     * A flatten beside a farm leaves every block of the farm as it was: its side unsloped, its shared fence line its own
     * (Slopes.around, FarmMissions' kept columns) - the mound against the line cut, the line and the farm past it whole,
     * but for the shared line's gates, opened by the new farm's farmers as its own.
     */
    @Test
    void aFlattenBesideAFarmLeavesTheFarmAsItWas() {
        SimClock clock = new SimClock();
        Registers registers = new Registers(state.resolve("registers"));
        registers.put(new ChunkRecord(BESIDE.chunkX(), BESIDE.chunkZ(), ChunkRecord.Kind.WHEAT, LEVEL, Optional.of("FENCED"), 0)); // the register has it
        Map<BlockPos, Integer> before = new HashMap<>();
        for (int x = 16; x <= 32; x++) {
            for (int z = 0; z <= 16; z++) {
                for (int y = LEVEL - 2; y <= LEVEL + 3; y++) {
                    before.put(new BlockPos(x, y, z), besideAFarm(x, y, z));
                }
            }
        }
        SimServer server = run(clock, FarmMissionSimulationTest::besideAFarm, new PlayerPosition(68.5, GROUND + 1, 8.5), "72,8", 60); // the camp east: nobody's way crosses the farm

        List<String> changed = new ArrayList<>();
        before.forEach((c, b) -> {
            int now = server.block(c.x(), c.y(), c.z());
            // Its wheat may grow, and a gate on the shared line is the new farm's too: opened, its way in, as every chunk farm's gates are.
            boolean grown = b == SimServer.WHEAT_SPROUT && now >= SimServer.WHEAT_SPROUT && now <= SimServer.WHEAT_RIPE;
            if (now != b && !grown && !(b == SimItems.GATE && now == SimItems.GATE_OPEN && c.x() == FARM.minX())) {
                changed.add(c + " " + b + " -> " + now);
            }
        });
        assertEquals(List.of(), changed.stream().sorted().limit(20).toList(), "the farm beside unchanged");
        List<String> left = new ArrayList<>();
        for (int x = 33; x <= 38; x++) {
            for (int z = 2; z <= 14; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    if (server.block(x, y, z) == SimServer.DIRT) {
                        left.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(List.of(), left.stream().limit(20).toList(), "the mound against the farm's line cut");
    }

    static int indexOf(List<String> log, String part) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }
}
