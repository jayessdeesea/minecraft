package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;

/** A soldier stuck, in the squad simulation: in water, in a pit or a pocket, in a block, sealed in, stranded up or down. */
@Tag("slow")
class RecoverySimulationTest {

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

void assertPlugsItsWayOut(int blocks, int count) {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::floodedPitInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(new PlayerPosition(4.5, GROUND - 4, 4.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, blocks, count}))
                .build(state);
        sim.runner.run();

        clock.advance(3 * 60_000);

        // Out: plugging the water round it gave it footing, and it built its way up out of the pit.
        assertTrue(server.log.stream().anyMatch(e -> e.startsWith("Alice placed")), "it plugged the water: " + server.log);
        assertTrue(server.position("Alice").y() >= GROUND - 2, "out of the water, at y=" + server.position("Alice").y()
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * Flat, with a pit 3 deep and two across at (30, 30), away from the camp, one cell of it a block
     * deeper - a step down, so it's no room to cut a door out of, as live - and a torch on every
     * floor of it.
     */
    static int torchInAPit(int x, int y, int z) {
        if (x >= 30 && x <= 31 && z >= 30 && z <= 31) {
            int floor = x == 31 && z == 31 ? GROUND - 4 : GROUND - 3;
            if (y > floor && y <= GROUND) {
                return y == floor + 1 ? SimServer.TORCH : SimServer.AIR;
            }
        }
        return flat(x, y, z);
    }

    /**
     * Flat, with an L-shaped pocket of three cells at (0, 0), (-1, 0) and (-1, -1) in a solid block of
     * cobblestone 7 across and 3 high - no hut's shape, as live: a room no squad's record has.
     */
    static int sealedHut(int x, int y, int z) {
        boolean block = Math.abs(x) <= 3 && Math.abs(z) <= 3 && y > GROUND && y <= GROUND + 3;
        boolean pocket = y <= GROUND + 2 && (x == 0 && z == 0 || x == -1 && z == 0 || x == -1 && z == -1);
        return block && !pocket ? SimServer.COBBLESTONE : flat(x, y, z);
    }

    /**
     * Flat, with a closed pocket in a block of stone below the ground at (10, 0) - feet room at y=61
     * and a cell beside it a block deeper, at (11, 0) - roofed by the stone itself.
     */
    static int pocketBelow(int x, int y, int z) {
        boolean open = z == 0 && (x == 10 && y >= GROUND - 2 && y <= GROUND - 1 || x == 11 && y >= GROUND - 3 && y <= GROUND - 1);
        // In stone, as live: a pickaxe digs it, bare hands don't.
        boolean rock = x >= 6 && x <= 15 && z >= -4 && z <= 4 && y >= GROUND - 6 && y <= GROUND;
        return open ? SimServer.AIR : rock ? SimServer.STONE : flat(x, y, z);
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

    /**
     * With no blocks at all, only a pickaxe, it cuts one from the pit's side and plugs with that: live
     * (field387), a soldier floated 13 minutes in a pit with a pickaxe and nothing to build with.
     */
    @Test
    void aSoldierStuckInWaterWithNoBlocksCutsOneFromTheBank() {
        assertPlugsItsWayOut(SimServer.ITEM_STONE_PICKAXE, 1);
    }

    /**
     * A soldier in a pit with a torch at its feet pillars out: the torch broken first, since the
     * server won't put a block where a torch is. Live, a soldier jumped in place for good in a pocket
     * it could only climb out of, standing in a torch.
     */
    @Test
    void aSoldierStandingInATorchPillarsOutOfAPit() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RecoverySimulationTest::torchInAPit, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(6.5, GROUND + 1, 6.5);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp)
                .connector(new PlayerPosition(30.5, GROUND - 2, 30.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_DIRT, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(2 * 60_000);

        assertTrue(server.position("Alice").y() >= GROUND + 1, "Alice at " + server.position("Alice") + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * A soldier boxed in a pocket no one knows to open - as after a restart, no hut's shape - breaks
     * its own way out toward where it's sent. Live, three soldiers stood a day in a three-cell pocket,
     * every move "no route".
     */
    @Test
    void aSoldierSealedInAHutBreaksItsWayOut() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RecoverySimulationTest::sealedHut, 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(20.5, GROUND + 1, 0.5);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(3 * 60_000);

        PlayerPosition at = server.position("Alice");
        assertTrue(at.x() > 4, "Alice at " + at
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 8), server.log.size()));
    }

    /**
     * A soldier stood in a block - at its feet and its head - breaks it, whatever its orders: live,
     * four stood a day and a night in dirt at the farm, every route from there "no route".
     */
    @Test
    void aSoldierStoodInABlockBreaksIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 6 && z == 6 && (y == GROUND + 1 || y == GROUND + 2) ? SimServer.DIRT
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(new PlayerPosition(6.5, GROUND + 1, 6.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .start(state);

        clock.advance(60_000);

        assertEquals(SimServer.AIR, server.block(6, GROUND + 2, 6), "the block at its head; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertEquals(SimServer.AIR, server.block(6, GROUND + 1, 6), "and at its feet");
    }

    /**
     * As live (field161 to field168): Bob, with a pickaxe, stood a day in a dead end 30 blocks under
     * the camp, the stair up then capped at 12 steps; and the drill sent two squadmates down to him
     * in turn, each trapped beside him. Now the stranded one tunnels up itself along the engineer's
     * cut, and nobody without work laid out there is sent.
     */
    @Test
    void aSoldierWithAPickaxeInAPocketDeepUnderTheGroundTunnelsUpItself() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::deepPocketBelow, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(10.5, GROUND - 25, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.of(new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{37, SimServer.ITEM_BREAD, 16})
                                : List.of(new int[]{37, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(10 * 60_000);

        server.dumpLog("aSoldierWithAPickaxeInAPocketDeepUnderTheGroundTunnelsUpItself");
        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertTrue(server.position("Dana").y() >= GROUND, "Dana out of the pocket: at " + server.position("Dana") + "; " + events);
        assertTrue(server.position("Alice").y() >= GROUND && server.position("Bob").y() >= GROUND,
                "nobody sent down after her: Alice at " + server.position("Alice") + ", Bob at " + server.position("Bob"));
    }

    /**
     * A soldier stood in a block to its waist - a solid cell at its feet, air at its head - breaks it,
     * as one with a block at its head does. Live, gravel fallen into the pocket at the camp mine's
     * bottom held two soldiers a day that way: nothing to reach a stand from, every route "no route".
     */
    @Test
    void aSoldierStoodInABlockToItsWaistBreaksIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 0 && z == 0 && y == GROUND + 1 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Alice") ? new PlayerPosition(0.5, GROUND + 1, 0.5) : new PlayerPosition(4.5, GROUND + 1, 4.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .start(state);

        clock.advance(60_000);

        assertEquals(SimServer.AIR, server.block(0, GROUND + 1, 0), "the block at Alice's feet broken; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * As live (field35 to field38): Dana boxed in a pocket below the ground with nothing to dig or
     * build with, no stair down to lay. The engineer lays a stair up, a squadmate with a pickaxe digs
     * it from the top, and she walks out.
     */
    @Test
    void aSoldierBoxedInBelowIsDugOutByASquadmateWithAPickaxe() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, RecoverySimulationTest::pocketBelow, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(10.5, GROUND - 2, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.of(new int[]{37, SimServer.ITEM_BREAD, 16}) : List.of(
                                new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 9, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 13, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.<int[]>of(new int[]{38, SimServer.ITEM_OAK_LOG, 32}) : List.of(
                                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16}))
                .build(state);
        sim.runner.run();

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 4, "Dana still up at " + dana + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertTrue(server.health("Dana") == 20 && server.deaths.isEmpty(), "Dana came down hurt: health "
                + server.health("Dana") + ", deaths " + server.deaths);
    }

    /**
     * As live (field497-498): Dana on a ledge of dirt 5 over the ground, too hurt for its drop, no blocks for a stair
     * and nobody to fetch her - a pickaxe only. She digs down the ledge's edge a block at a time to the ground's level
     * and walks off, unhurt.
     */
    @Test
    void aSoldierTooHurtForALedgesDropDigsDownItsEdge() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> y > GROUND && y <= GROUND + 5 && x >= 5 && x <= 10 && z >= -3 && z <= 3
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 6, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.<int[]>of(new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1})
                                : List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}))
                .build(state);
        sim.runner.run();
        clock.advance(2_000);
        server.starve("Dana", 2);

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 3, "Dana still up at " + dana + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertTrue(server.health("Dana") == 2 && server.deaths.isEmpty(), "Dana came down hurt: health "
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
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(7.5, GROUND + 18, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> n.equals("Dana") ? List.of() : List.of(
                                new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 16},
                                new int[]{38, SimServer.ITEM_DIRT, 64}))
                .build(state);
        sim.runner.run();

        clock.advance(5 * 60_000);

        PlayerPosition dana = server.position("Dana");
        assertTrue(dana.y() < GROUND + 4, "Dana still up at " + dana);
        assertTrue(server.health("Dana") == 20 && server.deaths.isEmpty(), "Dana came down hurt: health "
                + server.health("Dana") + ", deaths " + server.deaths);
    }
}
