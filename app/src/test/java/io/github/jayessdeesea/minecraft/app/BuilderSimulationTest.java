package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import io.github.jayessdeesea.minecraft.command.Keep;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;
import static io.github.jayessdeesea.minecraft.app.KeepSimulationTest.*;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;
import static io.github.jayessdeesea.minecraft.app.KeepSimulationTest.*;

/** The keep's builders in the squad simulation: their steps and pillars, their dirt, the highest block first, a pocket. */
@Tag("slow")
class BuilderSimulationTest {

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

/**
     * Top down, level by level, whatever's up there: a pane of glass ten blocks over the keep's
     * ground is cut first, from dirt steps built up to it - no ground stand reaches it - while a
     * mound on the site waits its layer; and the steps come down after. Live, a hilltop over the
     * keep stood while its builders stood under it.
     */
    @Test
    void theKeepsTopLayerIsCutFromStepsBuiltUpToItAndTheStepsComeDown() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 10 ? SimServer.GLASS
                : x == -5 && z == -5 && y == GROUND + 1 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64}))
                .start(state);

        clock.advance(4 * 60_000);

        assertEquals(SimServer.AIR, server.block(3, GROUND + 10, 3), "the glass; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        int glass = server.log.indexOf(server.log.stream().filter(e -> e.contains("dug " + SimServer.GLASS + " ")).findFirst().orElseThrow());
        int mound = server.log.stream().filter(e -> e.contains("dug " + SimServer.DIRT + " at -5," + (GROUND + 1) + ",-5")).findFirst()
                .map(server.log::indexOf).orElse(Integer.MAX_VALUE);
        assertTrue(glass < mound, "the top layer first: the mound waited for the glass");

        clock.advance(6 * 60_000);

        List<String> left = new ArrayList<>();
        for (int x = Keep.MIN; x <= Keep.MAX; x++) {
            for (int z = Keep.MIN; z <= Keep.MAX; z++) {
                for (int y = Keep.FLOOR; y <= GROUND + 12; y++) {
                    if (server.block(x, y, z) != SimServer.AIR) {
                        left.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        // Over the slab: in it, the simulation's server answers a step at once and the soldier's
        // model keeps it as the cobblestone stand-in, the slab's own (backlog/cells.md).
        assertEquals(List.of(), left, "its steps dug back down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * Kit by role (operator, 2026-11-14: protection and work apart): a keep builder with a stone sword,
     * one pickaxe and one shovel, a big dig ahead, and the makings of copper armour in its pack. It
     * makes its spare pickaxes and shovels before any armour past the protection floor. Live, builders
     * climbed through copper armour while the dig's tools wore out with no spare.
     */
    @Test
    void aKeepBuilderCarriesItsWorkLoadBeforeArmourPastTheFloor() throws IOException {
        SimClock clock = new SimClock();
        int dx = (int) Math.floor(Keep.DEPOT.x()), dz = (int) Math.floor(Keep.DEPOT.z());
        // The depot's table standing, east of its stand, as in the depot's own scenario.
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -12 && x <= 11 && z >= -12 && z <= 11 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT : x == dx + 1 && y == GROUND + 1 && z == dz ? SimServer.CRAFTING_TABLE : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(st -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaders(DrillSquadLeader::new).home("")
                .connector(new PlayerPosition(-14.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{39, SimServer.itemNamedOrMinus("stone_shovel"), 1},
                        new int[]{10, SimServer.itemNamedOrMinus("cobblestone"), 64},
                        new int[]{11, SimServer.itemNamedOrMinus("oak_log"), 8},
                        new int[]{12, SimServer.itemNamedOrMinus("copper_ingot"), 24}))
                .start(state);

        clock.advance(4 * 60_000);

        List<String> made = Files.readAllLines(Path.of("target/sim/aKeepBuilderCarriesItsWorkLoadBeforeArmourPastTheFloor.log")).stream()
                .filter(l -> l.contains("Crafter") && l.contains(" made "))
                .map(l -> l.substring(l.indexOf(" made ") + 6)).map(m -> m.substring(m.indexOf(' ') + 1, m.indexOf(" - "))).toList();
        int firstArmour = java.util.stream.IntStream.range(0, made.size()).filter(i -> made.get(i).startsWith("copper")).findFirst().orElse(made.size());
        long tools = made.subList(0, firstArmour).stream().filter(m -> m.equals("stone pickaxe") || m.equals("stone shovel")).count();
        assertTrue(tools >= 2, "spare pickaxes and shovels before any armour: " + made);
    }

    /**
     * Stranded up a pillar of its own - too high to jump, nothing beside it - a soldier digs it down
     * from on top, a block's drop at a time onto known ground. Live, a builder called off its pillar
     * to feed itself stood at y=98 with no route down.
     */
    @Test
    void aSoldierStrandedUpItsOwnPillarDigsItDown() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 6 && z == 6 && y > GROUND && y <= GROUND + 16 ? SimServer.DIRT
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())).leaderAt(camp).home("")
                .connector(name -> name.equals("Alice") ? new PlayerPosition(6.5, GROUND + 17, 6.5)
                        : new PlayerPosition(0.5, GROUND + 1, 0.5), name -> List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .start(state);

        clock.advance(4 * 60_000);

        List<String> left = new ArrayList<>();
        // Down to a stub it steps off - a pillar no more: nothing over a jump's height is left.
        for (int y = GROUND + 3; y <= GROUND + 16; y++) {
            if (server.block(6, y, 6) != SimServer.AIR) {
                left.add(String.valueOf(y));
            }
        }
        assertEquals(List.of(), left, "its pillar dug down; Alice at " + server.position("Alice") + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertTrue(server.position("Alice").y() <= GROUND + 2, "down on the ground, or its stub: " + server.position("Alice"));
    }

    /**
     * A builder with nothing to climb on fetches a load of dirt before it pillars up to its strip's
     * top. Live, one carrying a single block stood 15 blocks under its strip's top all day.
     */
    @Test
    void aBuilderWithNothingToClimbOnFetchesDirtForItsPillar() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 10 ? SimServer.GLASS : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}))
                .start(state);

        clock.advance(6 * 60_000);

        assertEquals(SimServer.AIR, server.block(3, GROUND + 10, 3), "the glass; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * The rules of a clearing (docs/military-doctrine.md § 17; operator, 2026-11-15): the highest
     * block first, built up to on scaffolding, and nothing lower till it's gone - a block left high
     * is where zombies hide under. A block ten up over the keep is taken before a mound a block high
     * elsewhere on it.
     */
    @Test
    void theHighestBlockIsTakenFirstOnScaffoldingAndNothingLowerBeforeIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 10 ? SimServer.GLASS
                : x >= 6 && x <= 7 && z >= 6 && z <= 7 && y == GROUND + 1 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{10, SimServer.ITEM_DIRT, 64}))
                .start(state);

        clock.advance(8 * 60_000);

        List<String> log = List.copyOf(server.log);
        int high = indexOf(log, " dug " + SimServer.GLASS + " at 3," + (GROUND + 10) + ",3");
        int low = java.util.stream.IntStream.range(0, log.size())
                .filter(i -> log.get(i).matches(".* dug " + SimServer.DIRT + " at [67]," + (GROUND + 1) + ",[67]")).findFirst().orElse(Integer.MAX_VALUE);
        assertTrue(high >= 0, "the block ten up taken, on scaffolding; last server events: " + log.subList(Math.max(0, log.size() - 15), log.size()));
        assertTrue(high < low, "the high block before any of the mound under it: high at " + high + ", the mound's first at " + low);
    }

    /**
     * A builder's own steps are no ground: they don't hold the site at their level, but they're
     * still taken down with the level they stand over, so none is left high. Live, a step put down
     * to climb to the rim made the keep's level 72, 73, 77 for one block each, the rest standing by.
     * A wall seven high on the rim, climbed to on steps: when it's down, nothing stands over the keep's
     * floor - not the wall, not a step.
     */
    @Test
    void aBuildersStepsDontHoldTheLevelAndAreTakenDownWithIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> z == Keep.MAX && x >= -1 && x <= 1 && y > GROUND && y <= GROUND + 7
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{10, SimServer.ITEM_DIRT, 64}))
                .start(state);

        clock.advance(10 * 60_000);

        List<String> log = List.copyOf(server.log);
        List<String> left = new ArrayList<>();
        for (int x = Keep.MIN; x <= Keep.MAX; x++) {
            for (int z = Keep.MIN; z <= Keep.MAX; z++) {
                for (int y = GROUND + 1; y <= GROUND + 10; y++) {
                    int b = server.block(x, y, z);
                    if (b != SimServer.AIR && b != SimServer.TORCH && b != SimServer.CRAFTING_TABLE && b != SimServer.FURNACE && b != SimServer.CHEST) {
                        left.add(x + "," + y + "," + z + "=" + b);
                    }
                }
            }
        }
        assertTrue(log.stream().anyMatch(e -> e.contains(" placed ")), "the wall climbed on steps; last server events: "
                + log.subList(Math.max(0, log.size() - 15), log.size()));
        assertTrue(left.isEmpty(), "nothing over the floor, the steps taken down too: " + left + "; last server events: "
                + log.subList(Math.max(0, log.size() - 15), log.size()));
    }

    /**
     * The borrow pit for a builder's dirt is on the ground's surface, wherever that is: live, the pit
     * was put at a guessed y=68 under a hill, and every fetch found no soil all day.
     */
    @Test
    void aBuildersDirtComesFromTheSurfaceOfAHillBesideTheKeep() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 10 ? SimServer.GLASS
                : z < Keep.MIN - 1 && y > GROUND && y <= GROUND + 12 ? SimServer.DIRT // the hill, north of the keep
                : z == Keep.MIN - 1 && x > Keep.MAX + 2 && y > GROUND && y <= GROUND + Math.min(12, x - Keep.MAX - 2) ? SimServer.DIRT // its ramp
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}))
                .start(state);

        clock.advance(8 * 60_000);

        assertTrue(server.log.stream().anyMatch(e -> e.matches("Alice dug " + SimServer.DIRT + " at -?[0-9]+,[0-9]+,-[0-9]+")
                        && Integer.parseInt(e.replaceAll(".*,(-[0-9]+)$", "$1")) < Keep.MIN - 1),
                "dirt dug off the hill north of the keep; last server events: "
                        + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * With nothing to dig at its borrow pit, a builder's next pit is off another side of the keep:
     * live, two spent a day at a pit dug out, "nothing left to gather".
     */
    @Test
    void aBuildersDirtComesFromAnotherSideWhenItsPitHasNone() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 10 ? SimServer.GLASS
                : x < 0 && y <= GROUND && y > -64 ? SimServer.STONE // west, as far as a fetch there looks: rock, no soil
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64}))
                .start(state);

        clock.advance(8 * 60_000);

        assertEquals(SimServer.AIR, server.block(3, GROUND + 10, 3), "the glass, from a pillar of dirt from another side; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        // And moved on only as often as it's tried and found wanting - a minute at each at least: live,
        // a gatherer's "nothing found" every tick moved the pit round the keep every second.
        long moves = Files.readAllLines(Path.of("target/sim/aBuildersDirtComesFromAnotherSideWhenItsPitHasNone.log")).stream()
                .filter(l -> l.contains("borrow pit is dug out")).count();
        assertTrue(moves <= 4, moves + " moves of the pit in 8 minutes");
    }

    /**
     * The same pocket, the squad on the keep with work in the stranded builder's strip: its dig held
     * over the stair out, live, a whole day, and its report flickered between "no route" and the dig
     * - a pillar it meant to build up to its strip's top - so the drill never counted it stranded
     * twenty reports running. The drill's order outranks ground work, and a soldier in a pocket is
     * stranded whatever its routine says.
     */
    @Test
    void aKeepBuilderInAPocketTunnelsUpBeforeItDigsItsStrip() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, KeepSimulationTest::deepPocketBelowAMound, 1000);
        Roster roster = new Roster("Alpha:Alice,Dana");
        SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())).leaderAt(Keep.CENTRE).home("")
                .connector(n -> n.equals("Dana") ? new PlayerPosition(10.5, GROUND - 25, 0.5) : new PlayerPosition(0.5, GROUND + 1, 0.5),
                        n -> List.of(new int[]{36, SimServer.ITEM_STONE_PICKAXE, 1}, new int[]{37, SimServer.ITEM_BREAD, 16}))
                .start(state);

        clock.advance(10 * 60_000);

        server.dumpLog("aKeepBuilderInAPocketTunnelsUpBeforeItDigsItsStrip");
        assertTrue(server.position("Dana").y() >= GROUND, "Dana out of the pocket: at " + server.position("Dana")
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }
}
