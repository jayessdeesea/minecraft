package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.FarmMission;
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flatten's high blocks, in the squad simulation: the blocks a clearing leaves standing over its level, by kind - each
 * cut, by the clearing's rules (docs/military-doctrine.md § 17: highest first, scaffold to reach, never move the level
 * past a block over it), or said up the chain by name. Live (field673-677), the operator's farm at chunk (1, 3) stalled
 * on its slope's top, and roofed logs were given up nine times in a row, then as "the OTHER is gone": a high block left
 * is where a clearing stops, and where zombies hide. The operator (2026-11-22): build the scaffolding in the dig area,
 * so it's easy to clean up, and take it down after.
 */
@Tag("slow")
class FlattenTopsSimulationTest {

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

    /** The mission's chunk, (2, 0), x 32..47 z 0..15: flattened to a block under sea level. */
    static final FarmMission FARM = FarmMissionSimulationTest.FARM;
    static final int FLOOR = FarmMission.SEA_LEVEL - 1;

    /** Flat, the camp's pond by it, and {@code top} standing in the chunk. */
    private static SimServer.Terrain with(SimServer.Terrain top) {
        return (x, y, z) -> {
            if (SimSquad.isCampPond(x, y, z)) {
                return SimServer.WATER;
            }
            int b = top.at(x, y, z);
            return b >= 0 ? b : SquadSimulationTest.flat(x, y, z);
        };
    }

    /** Runs the farm mission on {@link #FARM} for {@code minutes}, the squad carrying {@code kit}; its log's lines. */
    private List<String> run(SimServer server, SimClock clock, List<int[]> kit, long minutes) throws IOException {
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = FARM.centre(FARM.steps().getFirst());
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())).leaderAt(chunk)
                .connector(new PlayerPosition(30.5, GROUND + 1, 8.5), kit)
                .build(state);
        sim.chain.onOperatorFarm("Pat", FARM);
        sim.run();
        clock.advance(minutes * 60_000);
        String scenario = String.valueOf(ThreadContext.get("scenario"));
        server.dumpLog(scenario);
        return Files.readAllLines(Path.of("target", "sim", scenario + ".log"));
    }

    /**
     * What ground stands over the farm's own ground in the chunk, by cell and block: the flatten's leftovers - soil, rock,
     * a log, or a step put up. The mission runs on to its template, whose ground is at sea level; its crops, fence and
     * torches over it are no ground.
     */
    private static List<String> over(SimServer server) {
        List<String> left = new ArrayList<>();
        for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
            for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                for (int y = FarmMission.SEA_LEVEL + 1; y <= GROUND + 12; y++) {
                    int b = server.block(x, y, z);
                    if (b == SimServer.DIRT || b == SimServer.GRASS || b == SimServer.STONE || b == SimServer.COBBLESTONE || b == SimServer.OAK_LOG) {
                        left.add(x + "," + y + "," + z + "=" + b);
                    }
                }
            }
        }
        return left;
    }

    private static String mission(List<String> log) {
        return "; the mission: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("builds up to the top")
                || l.contains("leaves that cut")).limit(30).toList();
    }

    /**
     * A spike ten over the ground, one column, in the middle of the chunk: no stand on the ground reaches its top. Cut
     * from scaffolding built in the dig, top first, and the scaffolding gone with the ground under it. The scaffolding the
     * cheapest block carried (operator, 2026-11-22): dirt, not the cobblestone the floor wants.
     */
    @Test
    void aSpikeOutOfReachIsCutFromScaffoldingInTheDigAndTheScaffoldingTakenDown() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y > GROUND && y <= GROUND + 10 ? SimServer.DIRT : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        assertEquals(List.of(), over(server), "no ground over the farm's at y=" + FarmMission.SEA_LEVEL + " - the spike, nor a step put up to it" + mission(log));
        // Scaffolding put up - the spike's top is out of a body's reach from the ground - and all of it in the dig.
        List<int[]> scaffold = server.log.stream().filter(e -> e.contains(" placed ") && !e.contains(" placed a ")).map(e -> {
            String[] at = e.substring(e.lastIndexOf(" at ") + 4).split(",");
            return new int[]{Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2])};
        }).filter(c -> c[1] > GROUND + 1).toList();
        assertTrue(!scaffold.isEmpty(), "scaffolding put up to the top" + mission(log));
        assertEquals(List.of(), scaffold.stream().filter(c -> c[0] < FARM.minX() || c[0] > FARM.minX() + 15 || c[2] < FARM.minZ() || c[2] > FARM.minZ() + 15)
                .map(c -> c[0] + "," + c[1] + "," + c[2]).toList(), "scaffolding only in the dig area");
        List<String> cobbled = server.log.stream().filter(e -> e.contains(" placed " + SimServer.COBBLESTONE + " at ")).filter(e -> {
            String[] at = e.substring(e.lastIndexOf(" at ") + 4).split(",");
            return Integer.parseInt(at[1]) > GROUND; // over the ground: at it, a farm's footings are paved in cobblestone (ChunkFarm.footingsToPave)
        }).toList();
        assertEquals(List.of(), cobbled, "no cobblestone put up for scaffolding, with dirt carried");
    }

    /**
     * A tree under an overhang - a log with a block over it, no gatherer's wood - in the chunk: dug as a log. Live
     * (field674-677), the camp's clearers gave the same roofed logs up nine times in a row.
     */
    @Test
    void aLogUnderAnOverhangIsDug() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y > GROUND && y <= GROUND + 2 ? SimServer.OAK_LOG
                : x >= 39 && x <= 41 && z >= 7 && z <= 9 && y == GROUND + 3 ? SimServer.DIRT : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        assertEquals(List.of(), over(server), "the roofed tree and its roof dug" + mission(log));
        assertTrue(log.stream().noneMatch(l -> l.contains("is gone") && l.contains("x=40") && l.contains("z=8")), "no walk to it given up as gone"
                + mission(log));
    }

    /**
     * Emerald ore at the top of a mound, the squad's best a stone pickaxe: the ore's drop wants iron. Left, and reported up
     * for help by name and the tool it wants (operator, 2026-11-22: as with every job, the tools for it in hand, re-checked
     * as things change; learning an iron pickaxe is wanted, ask for help - someone else may be assigned it). Not dug for
     * nothing, and not a silent stall: what's over it cut, the level not moved past it.
     */
    @Test
    void oreBeyondTheSquadsToolsAtTheTopIsNotASilentStall() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y == GROUND + 2 ? SimServer.EMERALD_ORE
                : x >= 38 && x <= 42 && z >= 6 && z <= 10 && y > GROUND && y <= GROUND + 1 ? SimServer.DIRT : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        assertEquals(SimServer.EMERALD_ORE, server.block(40, GROUND + 2, 8), "the ore left for the tool it wants" + mission(log));
        assertTrue(log.stream().anyMatch(l -> l.contains("[Request]") && l.contains("emerald") && l.contains("an iron pickaxe")),
                "said up the chain for help, by name and the tool wanted: " + log.stream().filter(l -> l.contains("[Request]")).limit(8).toList());
    }

    /** A block state past the client's tables - as a newer server's block would be: solid, of no known break time. */
    static final int UNKNOWN_STATE = 40_000;

    /**
     * A block of a kind the client doesn't know, in the dig: an alert naming it, for what's known of blocks to be put right
     * (operator, 2026-11-22: the coding AI updates them from the log), left, and said up the chain - and the flatten not
     * measured done round it.
     */
    @Test
    void anUnknownBlockInTheDigIsAlertedAndSaidUp() throws IOException {
        SimClock clock = new SimClock();
        // And a fence post beside it: the cell over a fence reads solid to a body, and live (field679) the air and torches over
        // the farm's posts were alerted as unknown, 36 of them.
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y == GROUND + 1 ? UNKNOWN_STATE
                : x == 42 && z == 8 && y == GROUND + 1 ? SimItems.FENCE : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        assertEquals(List.of(), log.stream().filter(l -> l.contains("[Alert] unknown block") && l.contains("PASSABLE")).limit(4).toList(),
                "nothing alerted that a body walks through");
        assertTrue(log.stream().anyMatch(l -> l.contains("[Alert] unknown block state " + UNKNOWN_STATE)), "the unknown block alerted, by its state: "
                + log.stream().filter(l -> l.contains("[Alert] unknown")).limit(4).toList());
        assertTrue(log.stream().anyMatch(l -> l.contains("[Request]") && l.contains("state " + UNKNOWN_STATE) && l.contains("no break time known")),
                "said up the chain by name: " + log.stream().filter(l -> l.contains("[Request]")).limit(8).toList());
        assertTrue(log.stream().noneMatch(l -> l.contains("[Objectives] Alpha: " + FARM.said(FARM.steps().getFirst()) + " DONE")),
                "the flatten not measured done round it" + mission(log));
    }
    /**
     * Fence gates in the dig - one shut on the ground, one open and left in the air by ground dug from under it - cut with
     * the rest: an open gate reads passable and a shut one has no break time the clear knew, and a gate needs nothing
     * under it, so live (operator, 2026-11-22: "the crew is leaving the gates alone, there are some random gates in the
     * air") the old y=67 farm's gates hung over the flatten at chunk (1, 3).
     */
    @Test
    void fenceGatesInTheDigAreCutOpenOrShut() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y == GROUND + 1 ? SimItems.GATE
                : x == 42 && z == 8 && y == GROUND + 3 ? SimItems.GATE_OPEN : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        List<String> gates = new ArrayList<>();
        for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
            for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                for (int y = FLOOR + 1; y <= GROUND + 6; y++) {
                    int b = server.block(x, y, z);
                    if ((b == SimItems.GATE || b == SimItems.GATE_OPEN) && !(y == FarmMission.SEA_LEVEL + 1 && new ChunkFarm(
                            FARM.chunkX(), FARM.chunkZ(), FarmMission.SEA_LEVEL).gates().contains(new BlockPos(x, y, z)))) {
                        gates.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(List.of(), gates, "no gate left but the new template's own" + mission(log));
    }

    /**
     * A water source in the dig, over the level: filled with dirt, then dug out with the ground round it (operator,
     * 2026-11-22: "there are blocks of water the crew does not know how to deal with - fill the water block with dirt, then
     * rip it down"). The clear never breaks a block beside water, lest it flood the dig, and the fill counted water as floor:
     * live (field684), the flatten at chunk (1, 3) stood at its levels round the old y=67 template's water holes, and its
     * builders fell in. A mound two over the ground, a source in a hole in its top: nothing of either over the level after.
     */
    @Test
    void aWaterSourceInTheDigIsFilledThenDug() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x == 40 && z == 8 && y == GROUND + 2 ? SimServer.WATER
                : x >= 37 && x <= 43 && z >= 5 && z <= 11 && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT : -1), 1000);
        List<String> log = run(server, clock, FarmMissionSimulationTest.kit(), 40);
        assertEquals(List.of(), over(server), "no ground over the farm's level" + mission(log));
        List<String> water = new ArrayList<>();
        for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
            for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                for (int y = FarmMission.SEA_LEVEL + 1; y <= GROUND + 4; y++) {
                    if (server.block(x, y, z) == SimServer.WATER) {
                        water.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(List.of(), water, "no water left over the level - filled and dug" + mission(log));
    }

    /**
     * A block put down on ground already cut below it, partway through: seen at the next look over the whole area, and the
     * level goes back up to it (operator, 2026-11-22: before each next level, find the highest anything in the area - it
     * could have been added since the last look).
     */
    @Test
    void aBlockAddedOverCutGroundIsSeenAndCut() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, with((x, y, z) -> x >= 37 && x <= 43 && z >= 5 && z <= 11 && y > GROUND && y <= GROUND + 2
                ? SimServer.DIRT : -1), 1000);
        run(server, clock, FarmMissionSimulationTest.kit(), 15);
        server.set(34, GROUND + 2, 3, SimServer.DIRT); // put down where the ground's been cut from
        clock.advance(25 * 60_000);
        String scenario = String.valueOf(ThreadContext.get("scenario"));
        server.dumpLog(scenario);
        List<String> log = Files.readAllLines(Path.of("target", "sim", scenario + ".log"));
        assertEquals(List.of(), over(server), "the block put down cut with the rest" + mission(log));
    }
}
