package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Perimeter;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.flat;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.scripted;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.squadLeader;

/** The camp's wall in the squad simulation, where FORTIFY is ordered: paved, rebuilt in stone, quarried for, on a slope. */
class WallSimulationTest {

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

    /** The camp's wall, as laid out round the camp's ground on flat ground: two high, eight out. */
    static final Set<BlockPos> CAMP_WALL = Set.copyOf(
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

    /** One block of the camp's wall, its first by layout order, of dirt. */
    static final BlockPos DIRT_IN_WALL = CAMP_WALL.stream().min(Comparator.comparingInt(BlockPos::x)
            .thenComparingInt(BlockPos::z).thenComparingInt(BlockPos::y)).orElseThrow();

    /** A cell of the wall's lower course in the middle of its east side, and the block inside it, toward the camp. */
    static final BlockPos BURIED_DIRT = CAMP_WALL.stream().filter(c -> c.y() == GROUND + 1 && c.z() == 0)
            .max(Comparator.comparingInt(BlockPos::x)).orElseThrow();

    static final BlockPos FILLED_INSIDE = BURIED_DIRT.offset(-1, 0, 0);

    /** Flat, the camp's wall standing round it in cobblestone. */
    static int walledCamp(int x, int y, int z) {
        return CAMP_WALL.contains(new BlockPos(x, y, z)) ? SimServer.COBBLESTONE : flat(x, y, z);
    }

    /**
     * The wall closed and of stone, FORTIFY paves the camp's floor inside it at its own height: its
     * grass, block by block, replaced with cobblestone (backlog/cells.md).
     */
    @Test
    void aWalledCampsFloorIsPavedWithStoneAtItsOwnHeight() {
        new WallStore(state).save("0,0", List.copyOf(CAMP_WALL));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::walledCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Fortify the camp", camp, "Nothing walks in",
                new Objective.Task.Fortify())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1},
                        new int[]{37, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64},
                        new int[]{10, SimServer.ITEM_COBBLESTONE, 64})))
                .run();

        clock.advance(6 * 60_000);

        int paved = 0;
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                paved += server.block(x, GROUND, z) == SimServer.COBBLESTONE ? 1 : 0;
            }
        }
        Foothold.Standard floor = chain.foothold().orElseThrow().standards().stream()
                .filter(s -> s.name().equals("the camp's floor paved")).findFirst().orElseThrow();
        assertTrue(paved >= 20, paved + " of the floor's cells paved; the standard: " + floor.status() + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** As {@link #walledCamp}, one block of the wall dirt. */
    static int walledCampOneDirt(int x, int y, int z) {
        return DIRT_IN_WALL.equals(new BlockPos(x, y, z)) ? SimServer.DIRT : walledCamp(x, y, z);
    }

    /**
     * FORTIFY ordered a little off the camp's centre: the squad finishes the camp's own wall, not a
     * new one round the platoon leader's point. Live, the masons
     * repointed a wall with 23 dirt blocks while the camp's, with 1, stayed open for days.
     */
    @Test
    void aSquadSentToWallTheCampFinishesTheCampsOwnWall() {
        new WallStore(state).save("0,0", List.copyOf(CAMP_WALL));
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::walledCampOneDirt, 1000);
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

        assertEquals(SimServer.COBBLESTONE, server.block(DIRT_IN_WALL.x(), DIRT_IN_WALL.y(), DIRT_IN_WALL.z()),
                "the camp's own wall repointed");
    }

    /** Flat, ringed 8 blocks out by a dirt wall two high: closed, but an enderman's to open. */
    static int dirtWall(int x, int y, int z) {
        boolean ring = Math.max(Math.abs(x), Math.abs(z)) == 8;
        return ring && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT : flat(x, y, z);
    }

    /** A closed wall of dirt isn't done: masons with cobblestone replace it block by block. */
    @Test
    void aSquadRebuildsADirtWallInStone() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::dirtWall, 1000);
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
    static int dirtWallAndStone(int x, int y, int z) {
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
        SimServer server = new SimServer(clock, WallSimulationTest::dirtWallAndStone, 1000);
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

    /** As {@link #dirtWallAndStone}, with a pit in the camp to fill. */
    static int dirtWallStoneAndPit(int x, int y, int z) {
        return x >= 3 && x <= 5 && z >= 3 && z <= 5 && y > GROUND - 6 && y <= GROUND ? SimServer.AIR : dirtWallAndStone(x, y, z);
    }

    /**
     * The wall's stone isn't starved by the camp's ground work: one soldier is the wall's until it's
     * stone, while the rest fill the camp. Live, the ground work - never done, its last cells out of
     * reach - held every soldier's hands all day, the quarry never ran, and the wall's last dirt
     * block stood for days.
     */
    @Test
    void oneSoldierQuarriesForTheWallWhileTheRestFillTheCamp() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WallSimulationTest::dirtWallStoneAndPit, 1000);
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
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{9, SimServer.ITEM_DIRT, 64})));
        List<BlockPos> layout = List.copyOf(CAMP_WALL);
        new WallStore(state).save("0,0", layout);
        runner.run();

        clock.advance(8 * 60_000);

        long stone = layout.stream().filter(c -> server.block(c.x(), c.y(), c.z()) == SimServer.COBBLESTONE).count();
        assertTrue(stone > 0, "none of the wall's dirt replaced with stone in 8 minutes; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /** Ground rising a block every 3 east of x=-6: the camp's wall stands on many heights. */
    static int slope(int x, int y, int z) {
        int top = GROUND + Math.max(0, Math.floorDiv(x + 6, 3));
        return y <= -64 ? SimServer.BEDROCK : y < 55 ? SimServer.STONE : y < top ? SimServer.DIRT
                : y == top ? SimServer.GRASS : SimServer.AIR;
    }

    /** As {@link #slope}, but level inside the wall line at the camp's height: only the wall's ring slopes. */
    static int slopeLevelInside(int x, int y, int z) {
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
        SimServer server = new SimServer(clock, WallSimulationTest::slopeLevelInside, 1000);
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

        // Closed - of dirt, with no stone to hand.
        server.dumpLog("aSquadWallsInTheCampOnASlope");
        assertEquals(OptionalInt.of(0), runner.wallLeft("Alpha"));
    }
}
