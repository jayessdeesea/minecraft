package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;

/** The camp lit in the squad simulation: torches made and shared, the camp's own ground lit, kept lit by night. */
class CampLightSimulationTest {

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

/** The mound in the camp, and torches standing on a 7-block lattice round it: the camp's ground lit. */
    static int moundInALitCamp(int x, int y, int z) {
        boolean torch = y == GROUND + 1 && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0 && Math.abs(x) <= 14 && Math.abs(z) <= 14;
        return torch ? SimServer.TORCH : moundInCamp(x, y, z);
    }

    /**
     * A squad told to light a camp it has no torches for, nor the makings, keeps the camp while it
     * waits - and says so once, not every cycle. Live, a soldier between its upkeep's cuts reported
     * its lamplighter's CANTCO anew each cycle, 20 requests in 24 SITREPs.
     */
    @Test
    void aSquadWaitingOnTorchesKeepsTheCampWithoutRepeatingItself() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})));
        runner.run();

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
        assertEquals(0, mound, "blocks of the mound left");
        List<String> review = chain.requestReview();
        String toSquadLeader = review.stream().filter(l -> l.startsWith("  squad leader:")).findFirst().orElseThrow(() -> new AssertionError("no requests to the squad leader: " + review));
        Matcher m = Pattern.compile("(\\d+) answered.*?(\\d+) settled").matcher(toSquadLeader);
        assertTrue(m.find(), toSquadLeader);
        int raised = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
        assertTrue(raised <= 6, "requests to the squad leader in 4 minutes: " + review);
    }

    /**
     * A squad told to light the camp, carrying the makings, goes and makes its torches and puts them
     * down: the camp's upkeep doesn't take it off to dig. Live, a torchmaker walking to the workshop
     * was handed the upkeep's dig and set out for the workshop again every few ticks.
     */
    @Test
    void aSquadWithTheMakingsLightsTheCampBeforeKeepingIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 16},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(6 * 60_000);

        long torches = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    torches += server.block(x, y, z) == SimServer.TORCH ? 1 : 0;
                }
            }
        }
        assertTrue(torches >= 4, torches + " torches down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * SECURE ordered at the camp, the platoon leader's point a few blocks off its centre and above its
     * ground, as live: the squad lights the camp's ground, and the camp's standard is met. Live,
     * SECURE lit a disc round the point and reported DONE, while the camp's own disc had 8 cells dark
     * for days - two measures of one standard, and nobody working the one that was open.
     */
    @Test
    void aSquadSentToLightTheCampLightsTheCampsOwnGround() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position point = new Position(4.5, GROUND + 3, 4.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", point, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(point),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 15},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(30 * 60_000);

        Foothold.Standard lit = chain.foothold().orElseThrow().standards().stream()
                .filter(s -> s.name().equals("the camp's ground lit")).findFirst().orElseThrow();
        assertTrue(lit.met(), "the camp's ground: " + lit.status() + "; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 8), server.log.size()));
    }

    /**
     * A squad sent for wood where there's none to find, carrying torches, keeps the camp's light:
     * its gather found nothing, and that's no work. Live, two soldiers stood a day in a dark camp
     * with 7 and 13 torches, each "on" a wood gather with nothing left to gather.
     */
    @Test
    void aSquadWithNoWoodToFindLightsTheCampWithTheTorchesItCarries() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Gather wood", camp, "Logs for the camp",
                new Objective.Task.Gather(Resource.WOOD))), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_TORCH, 32})));
        runner.run();

        clock.advance(30 * 60_000);

        Foothold.Standard lit = chain.foothold().orElseThrow().standards().stream()
                .filter(st -> st.name().equals("the camp's ground lit")).findFirst().orElseThrow();
        assertTrue(lit.met(), "the camp's ground: " + lit.status());
    }

    /**
     * Four soldiers with the makings and one workshop: they make their torches there, and the camp
     * is lit. Live, four crowded the workshop's one stand together; the simulation doesn't push one
     * body off another, so this is the throughput a fix for that must keep.
     */
    @Test
    void fourTorchmakersShareTheWorkshop() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::moundInCamp, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        Position camp = new Position(0, GROUND + 1, 0);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Light the camp", camp, "Nothing spawns inside",
                new Objective.Task.Secure())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.ITEM_OAK_LOG, 16},
                        new int[]{11, SimServer.ITEM_COBBLESTONE, 16})));
        runner.run();

        clock.advance(6 * 60_000);

        long torches = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    torches += server.block(x, y, z) == SimServer.TORCH ? 1 : 0;
                }
            }
        }
        assertTrue(torches >= 8, torches + " torches down; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }

    /**
     * A camp lit and an idle squad in it keeps it by night too: monsters don't spawn on lit ground,
     * so the night isn't lost standing about. Live, squads stood every night at their slots.
     */
    @Test
    void anIdleSquadKeepsALitCampByNight() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, CampLightSimulationTest::moundInALitCamp, 14_000); // an hour after dusk
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
        assertEquals(0, mound, "blocks of the mound left by night");
    }

    /**
     * By night only the fit keep the camp: lit ground spawns nothing, but phantoms come out of the
     * sky. Live, an unarmed soldier at 4 health digging the camp by night was killed by one.
     */
    @Test
    void byNightOneAtLowHealthDoesntKeepTheCamp() throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, CampLightSimulationTest::moundInALitCamp, 14_000); // an hour after dusk
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
        clock.advance(5_000); // joined, and the camp's work handed out
        Path log = Path.of("target/sim/byNightOneAtLowHealthDoesntKeepTheCamp.log");
        int before = Files.readAllLines(log).size();
        server.starve("Bob", 4);

        clock.advance(2 * 60_000);

        List<String> after = Files.readAllLines(log);
        after = after.subList(before, after.size());
        assertTrue(after.stream().anyMatch(l -> l.contains("Bob taken off dig the camp's ground")), "Bob, at 4 health, taken off the camp's work");
        assertTrue(after.stream().noneMatch(l -> l.contains("Bob has nothing else to do - keeps the camp")), "and not given it again");
        assertTrue(after.stream().noneMatch(l -> l.contains("Alice taken off dig the camp's ground")), "Alice, fit, keeps at it");
    }
}
