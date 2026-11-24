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
     * chunk). Nothing of the ground left over its level; a cobblestone slab one under it, and its level cobblestone too
     * but for the dirt under each tree (operator, 2026-11-23: cobblestone one under the level and at it unless it can't
     * be - dirt under the trees, cobblestone everywhere else); a sapling at each of its sixteen trunks, its chest and its
     * nine torches in; and its four steps in the objective register DONE, in order.
     * A stack of saplings each: while the slab goes in, the squad's foresters plant the camp's own tree chunk and the
     * keep's grove with what they carry, and the mission's sixteen come after.
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
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.itemNamedOrMinus("spruce_sapling"), 64},
                new int[]{10, SimServer.itemNamedOrMinus("torch"), 16}, new int[]{11, SimServer.itemNamedOrMinus("chest"), 1},
                new int[]{12, SimServer.ITEM_COBBLESTONE, 64}, new int[]{13, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{14, SimServer.ITEM_COBBLESTONE, 64}, new int[]{15, SimServer.ITEM_DIRT, 64}))).run();
        clock.advance(120 * 60_000);
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
        assertEquals(List.of(), template.ground().stream().filter(c -> server.block(c.x(), c.y() - 1, c.z()) != SimServer.COBBLESTONE).toList(),
                "its slab cobblestone, one under its level" + missionLines);
        java.util.Set<BlockPos> soil = java.util.Set.copyOf(template.soil());
        assertEquals(List.of(), template.ground().stream().filter(c -> !soil.contains(c) && server.block(c.x(), c.y(), c.z()) != SimServer.COBBLESTONE).toList(),
                "its level cobblestone, but under its trees" + missionLines);
        assertEquals(List.of(), template.soil().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.AIR || server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).toList(),
                "dirt under each tree" + missionLines);
        // A sapling, or the tree it has grown into since: in the simulation a sapling grows in minutes.
        long planted = template.trunks().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_SAPLING
                || server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_LOG).count();
        assertEquals(16, planted, "a sapling or its tree at each trunk" + missionLines);
        assertEquals(9, template.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.TORCH).count(), "its nine torches" + missionLines);
        assertEquals(SimServer.CHEST, server.block(template.chest().x(), template.chest().y(), template.chest().z()), "its chest" + missionLines);

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(TREES.name())).toList();
        assertEquals(List.of(0, 1, 2, 3), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's four steps: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.DONE), "each DONE: " + rows + missionLines);
    }

    /** As {@link #hills}, with a chest left standing in the chunk's flat ground, at (34, 64, 13): a depot's, sited there before the farm was. */
    static int hillsWithAChest(int x, int y, int z) {
        return x == 34 && y == GROUND + 1 && z == 13 ? SimServer.CHEST : hills(x, y, z);
    }

    /**
     * The eval: a chest standing in a farm mission's chunk is taken up, not dug round (operator, 2026-11-22: random chests
     * in the wheat farm - who cleans this up?). Live (field696) five of a depot's stood in the wheat farm, counted "to dig"
     * for hours and nobody's, its template never done. Here the chest is gone, the floor laid under where it stood, and the
     * farm sown; by night too (the squad sheltering by rule but for its mission): its steps still worked.
     */
    @Test
    void aChestLeftInAFarmsChunkIsTakenUpAndTheFarmMade() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = run(clock, FarmMissionSimulationTest::hillsWithAChest, new PlayerPosition(30.5, GROUND + 1, 8.5), "0,0", 90);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aChestLeftInAFarmsChunkIsTakenUpAndTheFarmMade.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        assertTrue(server.block(34, GROUND + 1, 13) != SimServer.CHEST, "the chest taken up" + missionLines);
        assertEquals(SimServer.COBBLESTONE, server.block(34, LEVEL - 1, 13), "and the floor laid where it stood" + missionLines);
        Set<String> ground = new HashSet<>();
        new ChunkFarm(FARM.chunkX(), FARM.chunkZ(), LEVEL).ground().forEach(g -> ground.add(g.x() + "," + (g.y() + 1) + "," + g.z()));
        long sown = List.copyOf(server.log).stream().filter(l -> l.contains(" sowed ")).map(l -> l.substring(l.indexOf(" sowed ") + 7)).filter(ground::contains)
                .distinct().count();
        assertTrue(sown >= 150, "wheat sown on the template's ground: " + sown + " of " + ground.size() + missionLines);
    }

    /**
     * Flat at sea level but for a pit in the chunk: x 36..40, z 4..8, three deep - free space under where a template's
     * ground goes, with a trunk's cell (38, 6) and a chest row's over it.
     */
    static int pit(int x, int y, int z) {
        boolean inPit = x >= 36 && x <= 40 && z >= 4 && z <= 8 && y <= GROUND && y > GROUND - 3;
        return inPit ? SimServer.AIR : SquadSimulationTest.flat(x, y, z);
    }

    /** A squad of four given {@code mission} on {@link #pit} ground, each carrying {@code kit}, for {@code minutes}. */
    private SimServer overAPit(FarmMission mission, List<int[]> kit, long minutes) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::pit, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = mission.centre(mission.steps().getFirst());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        chain.onOperatorFarm("Pat", mission);
        runner.simulated(clock, server.connector(new PlayerPosition(30.5, GROUND + 1, 8.5), kit)).run();
        clock.advance(minutes * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        return server;
    }

    /**
     * The eval: a template's ground built out over free space (operator, 2026-11-23: the soldiers don't know how to build
     * out the cobblestone base as there is free space under - from underneath, or placing blocks sideways out over it;
     * either works). A supply point given on a chunk with a pit three deep: its floor is laid across the pit, every cell
     * of it cobblestone, and its mission's flatten and floor are DONE.
     */
    @Test
    void aFloorIsLaidOutOverAPit() throws IOException {
        SimServer server = overAPit(STORES, List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{40, SimServer.ITEM_BREAD, 64},
                new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_COBBLESTONE, 64}, new int[]{11, SimServer.ITEM_OAK_LOG, 64},
                new int[]{12, SimServer.ITEM_TORCH, 16}, new int[]{13, SimServer.ITEM_COBBLESTONE, 64}, new int[]{14, SimServer.ITEM_COBBLESTONE, 64}), 100);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aFloorIsLaidOutOverAPit.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();
        io.github.jayessdeesea.minecraft.bot.farm.StoresChunk template = new io.github.jayessdeesea.minecraft.bot.farm.StoresChunk(STORES.chunkX(), STORES.chunkZ(), GROUND);
        List<BlockPos> open = template.ground().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.COBBLESTONE).toList();
        assertEquals(List.of(), open, "its floor cobblestone across the pit too" + missionLines);
    }

    /**
     * The eval: a tree farm given on a chunk with a pit under one of its trunks. Its slab and its paving are laid across
     * the pit and dirt put under each tree, so a sapling, or its tree, stands at each of its sixteen trunks - the one over
     * the pit among them.
     */
    @Test
    void aTreeFarmsTrunkOverAPitIsGivenGroundAndPlanted() throws IOException {
        SimServer server = overAPit(TREES, List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.itemNamedOrMinus("spruce_sapling"), 64},
                new int[]{10, SimServer.itemNamedOrMinus("torch"), 16}, new int[]{11, SimServer.itemNamedOrMinus("chest"), 1},
                new int[]{12, SimServer.ITEM_DIRT, 64}, new int[]{13, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{14, SimServer.ITEM_COBBLESTONE, 64}, new int[]{15, SimServer.ITEM_COBBLESTONE, 64}), 120);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aTreeFarmsTrunkOverAPitIsGivenGroundAndPlanted.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]") || l.contains("[Trees]")).toList();
        io.github.jayessdeesea.minecraft.bot.farm.TreeChunk template = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(TREES.chunkX(), TREES.chunkZ(), GROUND);
        assertEquals(SimServer.COBBLESTONE, server.block(38, GROUND - 1, 6), "the slab under the trunk over the pit" + missionLines);
        assertTrue(server.block(38, GROUND, 6) != SimServer.AIR && server.block(38, GROUND, 6) != SimServer.COBBLESTONE, "and dirt on it, under the tree" + missionLines);
        long planted = template.trunks().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_SAPLING
                || server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_LOG).count();
        assertEquals(16, planted, "a sapling or its tree at each trunk, the one over the pit too" + missionLines);
    }

    /** The mineshaft's chunk: (2, 0) too, its head on the ground at sea level. */
    static final FarmMission MINESHAFT = new FarmMission(2, 0, GROUND, FarmMission.Crop.MINE);

    /**
     * The eval: a chunk given the mineshaft mission (operator, 2026-11-22: the mineshaft template - fenced, lit, chests at
     * its head; a squad goes down it and mines it till dry; 2026-11-24: its chests in rows, its mouth ringed and gated). Its
     * head's torches, chests, ring and workshop in, each where the layout has it, and no gate left open; its head step DONE and its shaft's step under way; and the squad cutting down
     * under the chunk's middle - blocks dug well below the ground.
     */
    @Test
    void aMineshaftsHeadIsPutUpAndItsSquadGoesDownIt() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        io.github.jayessdeesea.minecraft.bot.farm.MineHead head = new io.github.jayessdeesea.minecraft.bot.farm.MineHead(MINESHAFT.chunkX(), MINESHAFT.chunkZ(), GROUND);
        Position chunk = new Position(head.shaft().x() + 0.5, GROUND + 1, head.shaft().z() + 0.5);
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        chain.onOperatorFarm("Pat", MINESHAFT);
        runner.simulated(clock, server.connector(new PlayerPosition(30.5, GROUND + 1, 8.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{11, SimServer.ITEM_OAK_LOG, 64}, new int[]{12, SimServer.ITEM_TORCH, 32}, new int[]{10, SimServer.ITEM_OAK_LOG, 64},
                new int[]{13, SimServer.ITEM_COBBLESTONE, 64}, new int[]{14, SimServer.ITEM_COBBLESTONE, 64}, new int[]{15, SimServer.ITEM_COBBLESTONE, 64}))).run();
        clock.advance(120 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aMineshaftsHeadIsPutUpAndItsSquadGoesDownIt.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        assertEquals(List.of(), head.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) != SimServer.TORCH).toList(), "its torches, corners and ring" + missionLines);
        assertEquals(List.of(), head.chests().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.CHEST).toList(), "its head's eight double chests" + missionLines);
        assertEquals(List.of(), head.ring().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimItems.FENCE).toList(), "its mouth's ring" + missionLines);
        assertEquals(SimServer.CRAFTING_TABLE, server.block(head.table().x(), head.table().y(), head.table().z()), "its workshop's table" + missionLines);
        assertEquals(List.of(), head.furnaces().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.FURNACE).toList(), "its two furnaces" + missionLines);
        assertEquals(List.of(), java.util.stream.Stream.concat(head.grid().gates().stream(), java.util.stream.Stream.of(head.mouthGate())).filter(g -> server.block(g.x(), g.y(), g.z()) == SimItems.GATE_OPEN).toList(), "no gate left open" + missionLines);
        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(MINESHAFT.name())).toList();
        // Its ground as every template's (operator, 2026-11-23): cobblestone one under its level, all of it, and at its level
        // but where the shaft has since been cut down through it.
        for (int y : new int[]{GROUND - 1, GROUND}) {
            List<String> open = new ArrayList<>();
            for (int x = MINESHAFT.minX(); x <= MINESHAFT.minX() + 15; x++) {
                for (int z = MINESHAFT.minZ(); z <= MINESHAFT.minZ() + 15; z++) {
                    if (server.block(x, y, z) != SimServer.COBBLESTONE && Math.max(Math.abs(x - head.shaft().x()), Math.abs(z - head.shaft().z())) > 3) {
                        open.add(x + "," + z);
                    }
                }
            }
            assertEquals(List.of(), open, "cobblestone at y=" + y + ", clear of the shaft" + missionLines);
        }
        assertEquals(List.of(ObjectiveRecord.State.DONE, ObjectiveRecord.State.DONE, ObjectiveRecord.State.DONE, ObjectiveRecord.State.DONE),
                rows.stream().filter(r -> r.step() < 4).map(ObjectiveRecord::state).toList(), "its ground and its head done: " + rows + missionLines);
        assertEquals(ObjectiveRecord.State.UNDER_WAY, rows.stream().filter(r -> r.step() == 4).findFirst().orElseThrow().state(), "its shaft under way: " + rows + missionLines);
        long below = 0;
        for (int x = MINESHAFT.minX(); x <= MINESHAFT.minX() + 15; x++) {
            for (int z = MINESHAFT.minZ(); z <= MINESHAFT.minZ() + 15; z++) {
                for (int y = GROUND - 12; y <= GROUND - 4; y++) {
                    if (server.block(x, y, z) == SimServer.AIR || server.block(x, y, z) == SimServer.TORCH) {
                        below++;
                    }
                }
            }
        }
        assertTrue(below >= 12, "its squad cutting down under the chunk: " + below + " cells open 4 to 12 under the ground" + missionLines);
    }

    /** The supply point's chunk: (2, 0) too, its ground at sea level. */
    static final FarmMission STORES = new FarmMission(2, 0, GROUND, FarmMission.Crop.STORES);

    /**
     * The eval: a chunk with a mound given a sheep farm's mission (operator, 2026-11-24: animal farms - cobblestone one
     * under the level, dirt at it, fenced and lit so nothing spawns, gates at the corners). Its slab cobblestone, its
     * ground dirt, its footings cobblestone, its fence, gates, torches and chest each in the cell its layout names, and
     * its three steps DONE in order.
     */
    @Test
    void aSheepFarmIsFlattenedFlooredLaidInDirtFencedGatedAndLit() throws IOException {
        FarmMission sheepFarm = new FarmMission(STORES.chunkX(), STORES.chunkZ(), GROUND, FarmMission.Crop.SHEEP);
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::hills, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = sheepFarm.centre(sheepFarm.steps().getFirst());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(chunk),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        chain.onOperatorFarm("Pat", sheepFarm);
        runner.simulated(clock, server.connector(new PlayerPosition(30.5, GROUND + 1, 8.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{11, SimServer.ITEM_OAK_LOG, 64}, new int[]{12, SimServer.ITEM_TORCH, 32},
                new int[]{13, SimServer.ITEM_COBBLESTONE, 64}, new int[]{14, SimServer.ITEM_DIRT, 64}, new int[]{15, SimServer.ITEM_DIRT, 64}))).run();
        clock.advance(120 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aSheepFarmIsFlattenedFlooredLaidInDirtFencedGatedAndLit.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        io.github.jayessdeesea.minecraft.bot.farm.PastureChunk template = new io.github.jayessdeesea.minecraft.bot.farm.PastureChunk(
                sheepFarm.chunkX(), sheepFarm.chunkZ(), GROUND, io.github.jayessdeesea.minecraft.bot.farm.PastureChunk.Animal.SHEEP);
        assertEquals(List.of(), template.ground().stream().filter(c -> server.block(c.x(), c.y() - 1, c.z()) != SimServer.COBBLESTONE).limit(8).toList(),
                "its slab cobblestone, one under its level" + missionLines);
        assertEquals(List.of(), template.ground().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.DIRT
                && server.block(c.x(), c.y(), c.z()) != SimServer.GRASS).limit(8).toList(), "its ground dirt" + missionLines);
        assertEquals(List.of(), template.footings().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimServer.COBBLESTONE).limit(8).toList(),
                "its footings cobblestone" + missionLines);
        assertEquals(List.of(), template.grid().fence().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimItems.FENCE).limit(8).toList(), "its fence" + missionLines);
        assertEquals(List.of(), template.grid().gates().stream().filter(c -> server.block(c.x(), c.y(), c.z()) != SimItems.GATE).limit(8).toList(),
                "its gates, shut" + missionLines);
        assertEquals(List.of(), template.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) != SimServer.TORCH).limit(8).toList(), "its torches" + missionLines);
        assertEquals(SimServer.CHEST, server.block(template.chest().x(), template.chest().y(), template.chest().z()), "its chest" + missionLines);

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(sheepFarm.name())).toList();
        assertEquals(List.of(0, 1, 2), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's three steps: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.DONE), "each DONE: " + rows + missionLines);
    }

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
                new int[]{11, SimServer.ITEM_OAK_LOG, 64}, new int[]{12, SimServer.ITEM_TORCH, 16},
                new int[]{13, SimServer.ITEM_COBBLESTONE, 64}, new int[]{14, SimServer.ITEM_COBBLESTONE, 64}))).run();
        clock.advance(120 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aSupplyPointIsFlattenedPavedLitAndItsFirstRowsPutUp.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]")).toList();

        io.github.jayessdeesea.minecraft.bot.farm.StoresChunk template = new io.github.jayessdeesea.minecraft.bot.farm.StoresChunk(STORES.chunkX(), STORES.chunkZ(), GROUND);
        long paved = template.ground().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).count();
        assertEquals(256, paved, "its ground cobblestone" + missionLines);
        // And the slab one under it, as every template has (operator, 2026-11-23).
        assertEquals(List.of(), template.ground().stream().filter(c -> server.block(c.x(), c.y() - 1, c.z()) != SimServer.COBBLESTONE).toList(),
                "its slab cobblestone, one under its level" + missionLines);
        assertEquals(9, template.torches().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.TORCH).count(), "its nine torches" + missionLines);
        assertEquals(SimServer.CRAFTING_TABLE, server.block(template.table().x(), template.table().y(), template.table().z()), "its table" + missionLines);
        assertEquals(4, template.furnaces().stream().filter(f -> server.block(f.x(), f.y(), f.z()) == SimServer.FURNACE).count(), "its four furnaces" + missionLines);
        long chests = io.github.jayessdeesea.minecraft.bot.farm.StoresChunk.FIRST_ROWS.stream().flatMap(row -> template.chests(row).stream())
                .filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.CHEST).count();
        assertEquals(24, chests, "the chests of its food and building rows" + missionLines);
        assertEquals(0, runner.afterActionReview.errands.loops(), "no soldier walking trip after trip for nothing");
        // Its gates kept shut (operator, 2026-11-22: a habit of keeping them shut): put up shut, opened only on the way through.
        assertEquals(List.of(), template.grid().gates().stream().filter(g -> server.block(g.x(), g.y(), g.z()) == SimItems.GATE_OPEN).toList(), "no gate left open" + missionLines);

        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(STORES.name())).toList();
        assertEquals(List.of(0, 1, 2, 3), rows.stream().map(ObjectiveRecord::step).toList(), "the mission's four steps: " + rows);
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
            // And what its fence and torches stand on is paved in cobblestone by whoever keeps it (ChunkFarm.footingsToPave).
            boolean paved = now == SimServer.COBBLESTONE && c.y() == GROUND;
            if (now != b && !grown && !paved && !(b == SimItems.GATE && now == SimItems.GATE_OPEN && c.x() == FARM.minX())) {
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

    /**
     * The eval: a tree farm built beside a farm leaves the farm as it was - its flatten a block under its level, its
     * slab, its level's paving and its template each keeping off the line the two share. Live (field705), a wheat farm's
     * review found "its ground is gone in places ... fill=10 ... posts=14" along x=32, the line it shares with the tree
     * farm east of it, and reopened it again and again.
     */
    @Test
    void aTreeFarmBuiltBesideAFarmLeavesTheFarmAsItWas() {
        SimClock clock = new SimClock();
        Registers registers = new Registers(state.resolve("registers"));
        registers.put(new ChunkRecord(BESIDE.chunkX(), BESIDE.chunkZ(), ChunkRecord.Kind.WHEAT, LEVEL, Optional.of("FENCED"), 0));
        Map<BlockPos, Integer> before = new HashMap<>();
        for (int x = 16; x <= 32; x++) {
            for (int z = 0; z <= 16; z++) {
                for (int y = LEVEL - 2; y <= LEVEL + 3; y++) {
                    before.put(new BlockPos(x, y, z), besideAFarm(x, y, z));
                }
            }
        }
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::besideAFarm, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())), SquadSimulationTest.squadLeader(TREES.centre(TREES.steps().getFirst())),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "72,8", state.toString());
        chain.onOperatorFarm("Pat", TREES);
        runner.simulated(clock, server.connector(new PlayerPosition(68.5, GROUND + 1, 8.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_axe"), 1},
                new int[]{40, SimServer.ITEM_BREAD, 64}, new int[]{9, SimServer.itemNamedOrMinus("spruce_sapling"), 64},
                new int[]{10, SimServer.itemNamedOrMinus("torch"), 16}, new int[]{11, SimServer.itemNamedOrMinus("chest"), 1},
                new int[]{12, SimServer.ITEM_COBBLESTONE, 64}, new int[]{13, SimServer.ITEM_COBBLESTONE, 64},
                new int[]{14, SimServer.ITEM_COBBLESTONE, 64}, new int[]{15, SimServer.ITEM_DIRT, 64}))).run();
        clock.advance(120 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));

        List<String> changed = new ArrayList<>();
        before.forEach((c, b) -> {
            int now = server.block(c.x(), c.y(), c.z());
            boolean grown = b == SimServer.WHEAT_SPROUT && now >= SimServer.WHEAT_SPROUT && now <= SimServer.WHEAT_RIPE;
            // And what its fence and torches stand on is paved in cobblestone by whoever keeps it (ChunkFarm.footingsToPave).
            boolean paved = now == SimServer.COBBLESTONE && c.y() == GROUND;
            if (now != b && !grown && !paved && !(b == SimItems.GATE && now == SimItems.GATE_OPEN && c.x() == TREES.minX())) {
                changed.add(c + " " + b + " -> " + now);
            }
        });
        assertEquals(List.of(), changed.stream().sorted().limit(20).toList(), "the farm beside unchanged");
        io.github.jayessdeesea.minecraft.bot.farm.TreeChunk template = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(TREES.chunkX(), TREES.chunkZ(), GROUND);
        long planted = template.trunks().stream().filter(t -> server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_SAPLING
                || server.block(t.x(), t.y(), t.z()) == SimServer.SPRUCE_LOG).count();
        assertEquals(16, planted, "and the tree farm built beside it");
    }

    static int indexOf(List<String> log, String part) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The eval: a platoon deciding by rules, with nothing but the base's plan (operator, 2026-11-23: soldier, squad leader
     * and platoon leader deterministic - no model in the chain of command; a strategy of expanding influence, the base
     * built a cluster at a time, never over-extended). Nobody orders anything. The plan's first mission is given unasked -
     * a wheat farm, food first, in the cluster nearest the origin - a squad is put on it and its ground is cut; and the model
     * the operator talks to is told what the platoon is told and never asked to decide.
     */
    @Test
    void aPlatoonDecidingByRulesBeginsTheBaseUnasked() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmMissionSimulationTest::hills, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob;Bravo:Carl,Dana");
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        io.github.jayessdeesea.minecraft.command.PlatoonLeader model = SquadSimulationTest.scripted(s -> {
            asked.incrementAndGet();
            return new Objective("Mine stone", new Position(0.5, GROUND + 1, 0.5), "a model's order", new Objective.Task.Mine());
        });
        io.github.jayessdeesea.minecraft.command.BasePlan base = new io.github.jayessdeesea.minecraft.command.BasePlan(-4, 3, GROUND);
        FarmMission first = base.next(Set.of(), Set.of(), Set.of()).orElseThrow();
        assertEquals(new FarmMission(-1, -1, GROUND, FarmMission.Crop.WHEAT), first);
        ChainOfCommand chain = new ChainOfCommand(new RulesPlatoonLeader(model), SquadSimulationTest.squadLeader(first.centre(first.steps().getFirst())),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        chain.farmMissions.works(base);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString());
        runner.simulated(clock, server.connector(new PlayerPosition(4.5, GROUND + 1, 4.5), List.of(
                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{40, SimServer.ITEM_BREAD, 64}))).run();
        clock.advance(20 * 60_000);
        List<String> log = Files.readAllLines(Path.of("target", "sim", "aPlatoonDecidingByRulesBeginsTheBaseUnasked.log"));
        String missionLines = "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Objectives]") || l.contains("[Plan]")).limit(40).toList();

        assertEquals(0, asked.get(), "the model never asked to decide");
        assertTrue(log.stream().anyMatch(l -> l.contains("[Plan] the base's next: " + first.name())), "the plan's first mission given unasked" + missionLines);
        List<ObjectiveRecord> rows = new Registers(state.resolve("registers")).objectives().stream().filter(r -> r.mission().equals(first.name())).toList();
        assertEquals(List.of(0, 1, 2), rows.stream().map(ObjectiveRecord::step).toList(), "its steps kept: " + rows + missionLines);
        assertTrue(rows.getFirst().state() != ObjectiveRecord.State.PLANNED, "its flatten begun: " + rows + missionLines);
        assertTrue(server.log.stream().anyMatch(l -> l.contains(" dug ")), "and its ground being cut" + missionLines);
        assertTrue(new Registers(state.resolve("registers")).objectives().stream().map(ObjectiveRecord::mission).distinct().count() <= 3,
                "not over-extended: no more missions than squads" + missionLines);
    }
}
