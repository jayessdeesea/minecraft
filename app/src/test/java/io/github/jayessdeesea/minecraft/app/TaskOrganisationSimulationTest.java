package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

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
 * out by its detachment while the rest of the squad holds with the platoon leader's objective
 * (docs/military-doctrine.md § 18).
 */
@Tag("slow")
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
        return squadLeader(roles, orders, false);
    }

    /** As {@link #squadLeader(String, String)}, its order giving leave to rearm or not. */
    private static ChatClient squadLeader(String roles, String orders, boolean rearm) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": %s,
                 "intent": "hold the camp", "cantco": "", "roleChanges": %s, "detached": %s}
                """, CAMP.x(), CAMP.y(), CAMP.z(), rearm, roles, orders);
        return ChatClient.create((ChatModel) prompt ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /** Alpha, Alice and Bob, holding the camp for the platoon leader, their squad leader's reply as given; started. */
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
        return run(clock, ground, roles, orders, more, camp, timeOfDay, shelterAtNight,
                new Objective("Hold the camp", CAMP, "A foothold", new Objective.Task.None()));
    }

    /** As {@link #run(SimClock, SimServer.Terrain, String, String, List, String, long, boolean)}, the platoon leader's {@code objective} given. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, String roles, String orders, List<int[]> more, String camp,
                          long timeOfDay, boolean shelterAtNight, Objective objective) {
        List<int[]> kit = new ArrayList<>(List.of(
                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                new int[]{38, SimServer.ITEM_BREAD, 16}));
        kit.addAll(more);
        SimServer server = new SimServer(clock, ground, timeOfDay);
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> objective).leader(squadLeader(roles, orders)).home(camp).shelterAtNight(shelterAtNight)
                .kit(kit)
                .build(state);
        Optional.ofNullable(chunkFarm).ifPresent(sim.runner.campFarms()::take); // as a farm mission gives it, its ground already at its level
        sim.run();
        return server;
    }

    /** The chunk farm the camp's farm is given, as a farm mission gives it - the camp's farm sites none of its own; none for most scenarios. */
    private ChunkFarm chunkFarm;

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

    /**
     * By day on the keep, a miner is one of its builders: the squads mine by night (operator,
     * 2026-11-13). Live, a miner kept the camp alone all day while its squad dug the keep.
     */
    @Test
    void aMinerOfASquadOnTheKeepBuildsTheKeepByDay() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, SquadSimulationTest::flat, MINER_ALICE, "[]", List.of(), "", 1000, false,
                new Objective("Build the keep", Keep.CENTRE, "The main base's keep", new Objective.Task.Keep()));

        clock.advance(3 * 60_000);

        assertEquals(0, cutBelow(server), "no mine cut by day: its miner is on the keep");
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

        // In the camp, or down its mine - a well centred in a chunk beside the camp's centre, a chunk off.
        PlayerPosition alice = server.position("Alice");
        assertTrue(Math.hypot(alice.x(), alice.z()) <= 16, "in the camp or its mine by night, not at " + alice);
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

    /**
     * The camp's farm grows along a trench off its one-block pond, short of ground before the bucket: the farmer
     * cuts it at the farmland's level, the pond's water flows down it, and the ground beside it is tilled.
     */
    @Test
    void theCampsFarmGrowsAlongATrenchOffItsPond() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.SEEDS), 32}), "0,0");
        clock.advance(6 * 60_000);

        List<BlockPos> water = new ArrayList<>(), tilled = new ArrayList<>();
        for (int x = -12; x <= 24; x++) {
            for (int z = -12; z <= 12; z++) {
                int b = server.block(x, GROUND, z);
                if (b > SimServer.WATER && b <= SimServer.WATER + 7) {
                    water.add(new BlockPos(x, GROUND, z));
                } else if (b == SimServer.FARMLAND && (Math.abs(x - 6) > FarmPlan.HYDRATION || Math.abs(z) > FarmPlan.HYDRATION)) {
                    tilled.add(new BlockPos(x, GROUND, z));
                }
            }
        }
        assertTrue(water.size() >= 3, "a trench cut and the pond's water down it: " + water);
        assertTrue(!tilled.isEmpty(), "ground past the pond's own reach tilled beside it, along " + water);
    }

    /** Flat, with a pond of three sources in a row six blocks east of the camp. */
    private static int pondOfThreeByTheCamp(int x, int y, int z) {
        return x >= 6 && x <= 8 && z == 0 && y == GROUND ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
    }

    /**
     * A chunk farm given the camp's farm beside the camp, its farmer with a bucket digs the water holes in the
     * farmland's layer and fills them from the pond, a bucket at a time.
     */
    @Test
    void aFarmerWithABucketWatersTheChunkFarmsHoles() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondOfThreeByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.SEEDS), 32},
                        new int[]{41, SimServer.itemOf(ItemClass.BUCKET), 1}), "0,0");
        clock.advance(8 * 60_000);

        List<BlockPos> watered = new ArrayList<>();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                new ChunkFarm(cx, cz, GROUND).holes().stream().filter(h -> server.block(h.x(), h.y(), h.z()) == SimServer.WATER).forEach(watered::add);
            }
        }
        assertTrue(watered.size() >= 2, "the chunk farm's holes filled: " + watered + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * The chunk farm's fixtures put up by its farmer from what it carries: the fence on the chunk's lines, the gates
     * beside its corners - put up shut, and kept so - and its chest.
     */
    @Test
    void aChunkFarmIsFencedItsGatesPutUpShutAndItsChestStood() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondOfThreeByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.BUCKET), 1},
                        new int[]{9, SimServer.itemNamedOrMinus("oak_fence"), 64}, new int[]{10, SimServer.itemNamedOrMinus("oak_fence_gate"), 4},
                        new int[]{11, SimServer.itemOf(ItemClass.CHEST), 1}, new int[]{12, SimServer.ITEM_COBBLESTONE, 64},
                        new int[]{13, SimServer.ITEM_COBBLESTONE, 32}), "0,0");
        clock.advance(22 * 60_000); // its footings first, then what stands on them

        ChunkFarm farm = new ChunkFarm(-1, 0, GROUND);
        // What they stand on is cobblestone (operator, 2026-11-24: under the fence, and where the torches are).
        long paved = farm.footings().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).count();
        assertTrue(paved >= 60, "its footings paved: " + paved + " of " + farm.footings().size()
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        long posts = farm.fence().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimItems.FENCE).count();
        long open = farm.gates().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimItems.GATE_OPEN).count();
        long gates = farm.gates().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimItems.GATE || server.block(c.x(), c.y(), c.z()) == SimItems.GATE_OPEN).count();
        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        assertTrue(posts >= 40, "the fence up: " + posts + " of " + farm.fence().size() + events);
        // Kept shut (operator, 2026-11-22): a soldier opens one on its way through and shuts it behind (bot.GateHabit).
        assertTrue(gates >= 2, "gates up: " + gates + events);
        assertEquals(0, open, "none left open" + events);
        assertEquals(SimServer.CHEST, server.block(farm.chest().x(), farm.chest().y(), farm.chest().z()), "its chest" + events);
    }

    /**
     * Its tender with logs and no iron: the fence and chest made from the logs and put up, the bucket set aside for want
     * of its iron - not the whole batch given up. Live (field508-512), Olga with 56 logs gave up bucket, chest and fence
     * together, short of 3 raw iron, and the chunk farm stood unfenced for days.
     */
    @Test
    void aChunkFarmsTenderWithLogsAndNoIronStillFencesIt() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondOfThreeByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{9, SimServer.ITEM_OAK_LOG, 64}), "0,0");
        clock.advance(30 * 60_000); // the fence first, post by post, then the chest

        ChunkFarm farm = new ChunkFarm(-1, 0, GROUND);
        long posts = farm.fence().stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimItems.FENCE).count();
        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        assertTrue(posts >= 10, "fence posts up from its logs: " + posts + events);
        assertEquals(SimServer.CHEST, server.block(farm.chest().x(), farm.chest().y(), farm.chest().z()), "its chest" + events);
    }

    /**
     * Its torches put up - four in the field on the diagonals in from its holes, and one on every fourth post's top - so
     * nothing spawns in it or by it by night (operator, 2026-11-20). The server takes a torch on a fence's top; the world
     * model read the cell over a fence as solid, and none was ever offered there.
     */
    @Test
    void aChunkFarmHasItsTorchesInTheField() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondOfThreeByTheCamp, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.BUCKET), 1},
                        new int[]{9, SimServer.itemNamedOrMinus("oak_fence"), 64}, new int[]{10, SimServer.itemNamedOrMinus("oak_fence_gate"), 4},
                        new int[]{11, SimServer.itemOf(ItemClass.CHEST), 1}, new int[]{12, SimServer.ITEM_TORCH, 32}), "0,0");
        clock.advance(30 * 60_000);

        ChunkFarm farm = new ChunkFarm(-1, 0, GROUND);
        long field = farm.torches().subList(0, 4).stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.TORCH).count();
        long onPosts = farm.torches().subList(4, farm.torches().size()).stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.TORCH).count();
        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        // Three at least: a cell whose stand the builder once failed to reach it skips for good (backlog/renewables.md).
        assertTrue(field >= 3, "the field's torches: " + farm.torches().stream().map(c -> c + "=" + server.block(c.x(), c.y(), c.z())
                + "/" + server.block(c.x(), c.y() - 1, c.z())).toList() + events);
        assertTrue(onPosts >= 10, "on its posts' tops: " + onPosts + " of " + (farm.torches().size() - 4) + events);
        BlockPos under = farm.torches().getFirst().offset(0, -1, 0);
        assertTrue(server.block(under.x(), under.y(), under.z()) != SimServer.FARMLAND, "under it, untilled" + events);
    }

    /** As {@link #pondOfThreeByTheCamp}, a cave under the chunk farm's first water hole: open under its cell. */
    private static int caveUnderAHole(int x, int y, int z) {
        BlockPos hole = new ChunkFarm(-1, 0, GROUND).holes().getFirst();
        return x == hole.x() && z == hole.z() && y < hole.y() && y >= hole.y() - 3 ? SimServer.AIR : pondOfThreeByTheCamp(x, y, z);
    }

    /**
     * A water hole over a cave has a block put under it before it's filled - the operator (2026-11-20): something under the
     * farm's water - and the water stands in it, not run down into the cave.
     */
    @Test
    void aWaterHoleOverACaveIsFlooredThenFilled() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::caveUnderAHole, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.BUCKET), 1},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 16}), "0,0");
        clock.advance(14 * 60_000);

        BlockPos hole = new ChunkFarm(-1, 0, GROUND).holes().getFirst();
        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        assertTrue(server.block(hole.x(), hole.y() - 1, hole.z()) != SimServer.AIR, "a floor under the hole" + events);
        assertEquals(SimServer.WATER, server.block(hole.x(), hole.y(), hole.z()), "its water in it" + events);
    }

    /** As {@link #pondOfThreeByTheCamp}, the chunk farm west of the camp's chest stood at its local (13, 13). */
    private static int pondAndAFarmChest(int x, int y, int z) {
        return x == -3 && y == GROUND + 1 && z == 13 ? SimServer.CHEST : pondOfThreeByTheCamp(x, y, z);
    }

    /**
     * The chunk farm's tender puts its wheat in the farm's own chest, its duty order's dump (operator, 2026-11-19: a chest
     * on the farm for its seed and wheat): of two keeping the camp's farm, the second tends the chunk.
     */
    @Test
    void theChunkFarmsTenderPutsItsWheatInTheFarmsChest() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::pondAndAFarmChest, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40},
                 {"soldier": "Bob", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1, GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.BUCKET), 1},
                        new int[]{9, SimServer.itemOf(ItemClass.WHEAT), 64}), "0,0");
        clock.advance(8 * 60_000);

        int[][] chest = server.chests.get(SimServer.key(-3, GROUND + 1, 13));
        int wheat = chest == null ? 0 : java.util.Arrays.stream(chest).filter(st -> st != null && st[0] == SimServer.itemOf(ItemClass.WHEAT))
                .mapToInt(st -> st[1]).sum();
        assertTrue(wheat > 0, "wheat in the farm's chest: " + wheat + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
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

        // Got there and went on to mine: its stairs cut, and its role kept. Not its directives at the
        // end - the scripted squad leader gives the GO_TO again with every answer, so whether it's
        // there at the last look is the timing of the last answer, not the soldier's.
        assertTrue(cutBelow(server) >= 20, "the miner cut its stairs: " + cutBelow(server) + " cells cut below y=53");
        assertEquals(Role.MINER, new PlanBook(state).of("Alice").role(), "still the miner");
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
     * Without its squad leader's leave a soldier climbs no kit ladder: a wooden sword and the makings of a stone one
     * carried, no "rearm" said - no stone sword made, nor a table put down for one. Live (field692-693), soldiers on a
     * task walked off to make spare swords their loads then put away.
     */
    @Test
    void withoutItsSquadLeadersLeaveASoldierMakesNoKitButWhatItLacks() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())).leader(squadLeader("[]", "[]")).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 4},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 4}))
                .start(state);
        clock.advance(5 * 60_000);

        for (String soldier : List.of("Alice", "Bob")) {
            assertEquals(0, server.count(soldier, SimServer.itemNamedOrMinus("stone_sword")), soldier + " made a stone sword unordered");
            assertEquals(4, server.count(soldier, SimServer.ITEM_OAK_LOG), soldier + "'s logs spent on kit nobody ordered");
        }
    }

    /**
     * The kit ladder is the squad leader's leave (operator, 2026-11-22: soldiers do nothing without a command): with a
     * wooden sword and the makings of a stone one - two cobblestone and a stick - a soldier makes the stone sword once its
     * squad leader says "rearm".
     */
    @Test
    void onItsSquadLeadersLeaveASoldierMakesItsStoneSword() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())).leader(squadLeader("[]", "[]", true)).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 4},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 4}))
                .start(state);
        clock.advance(5 * 60_000);

        int stoneSword = SimServer.itemNamedOrMinus("stone_sword");
        for (String soldier : List.of("Alice", "Bob")) {
            assertTrue(server.count(soldier, stoneSword) > 0,
                    soldier + " has no stone sword; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
        }
    }

    /**
     * A spare of each is in the basic load: with a stone sword and pickaxe, and the makings of
     * another of each, a soldier makes them unasked - a sword breaks in a fight, a pickaxe halfway
     * down the mine.
     */
    @Test
    void onItsSquadLeadersLeaveASoldierMakesASparePickaxeAndNoSwordItsLoadPutsAway() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())).leader(squadLeader("[]", "[]", true)).home("")
                .kit(List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1},
                        new int[]{37, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 8},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 4}))
                .start(state);
        clock.advance(5 * 60_000);

        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size());
        assertEquals(1, server.count("Alice", SimServer.itemNamedOrMinus("stone_sword")), "no spare sword: its surface load keeps one (LoadPlan.putsAway)" + events);
        assertEquals(2, server.count("Alice", SimServer.ITEM_STONE_PICKAXE), "a spare pickaxe" + events);
    }

    /**
     * Armour from ore is a soldier's basic load too: with iron to hand it makes its iron armour,
     * though its squad leader hasn't said "rearm". Live, the camp's armoured standard stood at 3 of
     * 32 slots for days with ore in the soldiers' packs: in 700 FRAGOs no squad leader said "rearm".
     */
    @Test
    void onItsSquadLeadersLeaveASoldierMakesItsIronArmour() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())).leader(squadLeader("[]", "[]", true)).home("")
                .kit(List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1},
                        new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.itemNamedOrMinus("iron_ingot"), 24},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 8}))
                .start(state);
        clock.advance(8 * 60_000);

        int ironPieces = List.of("iron_chestplate", "iron_leggings", "iron_boots", "iron_helmet").stream()
                .mapToInt(n -> server.count("Alice", SimServer.itemNamedOrMinus(n))).sum();
        assertTrue(ironPieces > 0, "no iron armour made; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * Ore the force carries in pieces too small for a piece of armour is pooled in the stockpile and
     * drawn by whoever it completes one for, whichever squad dug it: Alice's five raw copper and Bob's
     * three make one chestplate. Live, eleven raw copper lay in four packs, none enough for a piece.
     */
    @Test
    void copperInPiecesTooSmallForArmourIsPooledAndMadeIntoOne() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice;Bravo:Bob");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP,
                "A foothold", new Objective.Task.None())).leader(squadLeader("[]", "[]", true))
                .connector(n -> new PlayerPosition(0.5, GROUND + 1, 0.5), n -> List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1},
                        new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.itemNamedOrMinus("raw_copper"), n.equals("Alice") ? 5 : 3},
                        new int[]{11, SimServer.ITEM_OAK_LOG, 12},
                        new int[]{12, SimServer.ITEM_COBBLESTONE, 16}))
                .start(state);
        clock.advance(10 * 60_000);

        int copper = List.of("Alice", "Bob").stream().mapToInt(n -> List.of("copper_chestplate", "copper_leggings", "copper_boots",
                "copper_helmet").stream().mapToInt(p -> server.count(n, SimServer.itemNamedOrMinus(p))).sum()).sum();
        assertTrue(copper > 0, "no copper armour made; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
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

    /** The chunks round the camp, each broken alike by its local x and z, so whichever the chunk farm is sited in is broken. */
    private static final int[][] CHUNKS = java.util.stream.IntStream.rangeClosed(-3, 3).boxed()
            .flatMap(cx -> java.util.stream.IntStream.rangeClosed(-3, 3).mapToObj(cz -> new int[]{cx, cz})).toArray(int[][]::new);

    /** As {@link #pondOfThreeByTheCamp}, the chunk farms' ground by the camp broken: a dirt mound two high, and a pit a block deep. */
    private static int brokenChunkGround(int x, int y, int z) {
        int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
        boolean inOne = Math.abs(Math.floorDiv(x, 16)) <= 3 && Math.abs(Math.floorDiv(z, 16)) <= 3;
        boolean mound = inOne && lx >= 6 && lx <= 8 && lz >= 6 && lz <= 8 && (y == GROUND + 1 || y == GROUND + 2);
        boolean pit = inOne && lx >= 10 && lx <= 11 && lz >= 2 && lz <= 3 && y == GROUND;
        return mound ? SimServer.DIRT : pit ? SimServer.AIR : pondOfThreeByTheCamp(x, y, z);
    }

    /**
     * The chunk farm's ground levelled to its template by its tender: the mound over it dug, and its dirt filling the pit
     * (FarmSite's SURVEYED stage). Live (field516-543), the camp's chunk farm stood a day SURVEYED, 64 blocks over its
     * ground and 52 cells under, its tender farming only what lay level.
     */
    @Test
    void aChunkFarmsTenderLevelsItsGround() {
        chunkFarm = new ChunkFarm(-1, 0, GROUND);
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::brokenChunkGround, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.BUCKET), 1}), "0,0");
        clock.advance(30 * 60_000);

        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size());
        long best = Long.MAX_VALUE;
        for (int[] c : CHUNKS) {
            long left = 0;
            for (int lx = 6; lx <= 8; lx++) {
                for (int lz = 6; lz <= 8; lz++) {
                    for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                        left += server.block(c[0] * 16 + lx, y, c[1] * 16 + lz) == SimServer.DIRT ? 1 : 0;
                    }
                }
            }
            for (int lx = 10; lx <= 11; lx++) {
                for (int lz = 2; lz <= 3; lz++) {
                    left += server.block(c[0] * 16 + lx, GROUND, c[1] * 16 + lz) == SimServer.AIR ? 1 : 0;
                }
            }
            best = Math.min(best, left);
        }
        assertEquals(0, best, "the sited chunk farm's mound dug and pit filled" + events);
    }

    /** A pond three long by the camp, and round it a field of ripe wheat on farmland, 11 by 9 less the water. */
    private static int ripeFieldByThePond(int x, int y, int z) {
        boolean field = x >= 2 && x <= 12 && z >= -4 && z <= 4 && !(x >= 6 && x <= 8 && z == 0);
        if (field && y == GROUND) {
            return SimServer.FARMLAND;
        }
        if (field && y == GROUND + 1) {
            return SimServer.WHEAT_RIPE;
        }
        return pondOfThreeByTheCamp(x, y, z);
    }

    /**
     * How fast a farmer brings in a ripe field (operator, 2026-11-20: harvest in passing): the ticks to its last crop,
     * against the ideal - its rows walked once at a walking pace, every crop in reach broken on the way. Printed; the
     * eval's measure of harvesting.
     */
    @Test
    void howFastAFarmerBringsInARipeField() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::ripeFieldByThePond, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}), "0,0");
        long crops = 96;
        long start = clock.millis(), start0 = start;
        long firstAt = -1, lastAt = -1, brought = 0;
        for (int step = 0; step < 20 * 60; step++) {
            clock.advance(1_000);
            long now = server.log.stream().filter(l -> l.contains(" dug " + SimServer.WHEAT_RIPE + " ")).count();
            if (now > 0 && firstAt < 0) {
                firstAt = clock.millis();
            }
            if (now > brought) {
                lastAt = clock.millis();
                brought = now;
            }
            if (brought >= crops) {
                break;
            }
        }
        double perMinute = brought * 60_000.0 / Math.max(1, lastAt - firstAt);
        clock.advance(12 * 60_000L - (clock.millis() - start0));
        System.out.printf(java.util.Locale.ROOT, "[Harvest] %d of %d crops, %.0f s from the first to the last: %.0f a minute%n",
                brought, crops, (lastAt - firstAt) / 1000.0, perMinute);
        assertTrue(brought > 0, "nothing harvested");
    }

    /** The ripe field's ground, tilled and bare: farmland round the pond, nothing on it. */
    private static int bareFieldByThePond(int x, int y, int z) {
        int state = ripeFieldByThePond(x, y, z);
        return state == SimServer.WHEAT_RIPE ? SimServer.AIR : state;
    }

    /**
     * How fast a farmer sows a tilled field (operator, 2026-11-20: tilling and sowing in passing, as harvesting): the
     * seconds from its first seed to its last, printed as cells a minute - the eval's measure of sowing.
     */
    @Test
    void howFastAFarmerSowsATilledField() {
        SimClock clock = new SimClock();
        SimServer server = run(clock, TaskOrganisationSimulationTest::bareFieldByThePond, """
                [{"soldier": "Alice", "role": "FARMER", "x": 40, "y": %d, "z": 40}]""".formatted(GROUND + 1), "[]",
                List.<int[]>of(new int[]{39, SimServer.itemOf(ItemClass.HOE), 1}, new int[]{40, SimServer.itemOf(ItemClass.SEEDS), 64},
                        new int[]{41, SimServer.itemOf(ItemClass.SEEDS), 64}), "0,0");
        long cells = 96;
        long start0 = clock.millis();
        long firstAt = -1, lastAt = -1, sown = 0;
        for (int step = 0; step < 20 * 60; step++) {
            clock.advance(1_000);
            long now = server.log.stream().filter(l -> l.contains(" sowed ")).count();
            if (now > 0 && firstAt < 0) {
                firstAt = clock.millis();
            }
            if (now > sown) {
                lastAt = clock.millis();
                sown = now;
            }
            if (sown >= cells) {
                break;
            }
        }
        clock.advance(12 * 60_000L - (clock.millis() - start0));
        System.out.printf(java.util.Locale.ROOT, "[Sowing] %d of %d cells, %.0f s from the first to the last: %.0f a minute%n",
                sown, cells, (lastAt - firstAt) / 1000.0, sown * 60_000.0 / Math.max(1, lastAt - firstAt));
        assertTrue(sown > 0, "nothing sown");
    }
}
