package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
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
import java.util.List;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operator's scout mission in the squad simulation (operator, 2026-11-25: look here for {animal} and bring them
 * back to {location}): given through the chat's command, a squad put on it, each soldier its sector, the animals led
 * in on their food by soldiers walking as soldiers walk, and the mission's end said.
 */
@Tag("slow")
class ScoutMissionSimulationTest {

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

    /** A squad of two at the camp, each joining with {@code kit}, the scout mission for sheep round (40, 8) given once they stand; then {@code minutes}. */
    private SimServer run(SimClock clock, SimServer.Terrain ground, List<int[]> kit, List<double[]> sheep, long minutes) {
        SimServer server = new SimServer(clock, ground, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP, "Nothing else to do")).leaderAt(CAMP)
                .kit(kit)
                .build(state);
        sim.run();
        clock.advance(20_000);
        sheep.forEach(at -> server.spawnSheep(at[0], GROUND + 1, at[1]));
        String wilco = new ChatCommands(sim.chain).giveScoutMission("sheep", 40, 8, 4, 4);
        assertTrue(wilco.startsWith("WILCO: scout for sheep round (40, 8), bring them to (4, 4)"), wilco);
        clock.advance(minutes * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));
        return server;
    }

    private static List<String> log(String scenario) throws IOException {
        return Files.readAllLines(Path.of("target", "sim", scenario + ".log"));
    }

    private static long near(SimServer server, double x, double z, double within) {
        return server.animals.tracked().stream().map(TrackedEntity::position).filter(p -> Math.hypot(p.x() - x, p.z() - z) <= within).count();
    }

    /**
     * The eval: two sheep, one in each soldier's sector, 30 and 50 blocks from where they're wanted. Each soldier finds
     * its own, leads it in with wheat in its hand, and the mission ends ACCOMPLISHED with both brought of both found.
     */
    @Test
    void aSquadSentForSheepLeadsThemInAndSaysSo() throws IOException {
        SimClock clock = new SimClock();
        List<int[]> kit = List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{40, SimServer.ITEM_BREAD, 64},
                new int[]{9, SimServer.itemOf(ItemClass.WHEAT), 2});
        SimServer server = run(clock, SquadSimulationTest::flat, kit, List.of(new double[]{30.5, 20.5}, new double[]{55.5, -4.5}), 12);

        List<String> log = log("aSquadSentForSheepLeadsThemInAndSaysSo");
        String scouting = "; the scouting: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Outcome]") || l.contains("[Guardrail]")
                || l.contains(" leaves the ") || l.contains(" brought ") || l.contains("gives up")).toList()
                + "; sheep at " + server.animals.tracked().stream().map(TrackedEntity::position).toList();
        assertEquals(2, near(server, 4.5, 4.5, 12), "both sheep stand where they're wanted" + scouting);
        assertTrue(log.stream().anyMatch(l -> l.contains("[Outcome] [Alpha]") && l.contains("2 of 2 sheep found brought in")), "the outcome said" + scouting);
        assertTrue(log.stream().anyMatch(l -> l.contains("[Mission] scout for sheep") && l.contains("ACCOMPLISHED")), "the mission closed" + scouting);
        assertTrue(server.log.stream().noneMatch(l -> l.contains("killed sheep")), "none hunted: " + server.log.stream().filter(l -> l.contains("killed sheep")).toList());
    }

    /**
     * The eval of the food drawn first: nobody joins with wheat, the camp's chest holds some. Each soldier draws its
     * wheat before it goes, and the sheep is brought in all the same.
     */
    @Test
    void aSquadWithNoWheatDrawsItFromTheCampsChestBeforeItScouts() throws IOException {
        SimClock clock = new SimClock();
        List<int[]> kit = List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{40, SimServer.ITEM_BREAD, 64});
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        // A stack to a chest, and a draw takes a stack: one each.
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClass.WHEAT), 64);
        server.stockedChest(3, GROUND + 1, 3, SimServer.itemOf(ItemClass.WHEAT), 64);
        Files.writeString(state.resolve("chests_stores_by_chest.json"), "[[3,64,1,0,0,0,0,0,0,0,0,64,0,0],[3,64,3,0,0,0,0,0,0,0,0,64,0,0]]"); // as the force last saw them
        Roster roster = new Roster("Alpha:Alice,Bob");
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Hold the camp", CAMP, "Nothing else to do")).leaderAt(CAMP)
                .kit(kit)
                .build(state);
        sim.run();
        clock.advance(60_000);
        server.spawnSheep(30.5, GROUND + 1, 20.5);
        new ChatCommands(sim.chain).giveScoutMission("sheep", 40, 8, 4, 4);
        clock.advance(14 * 60_000);
        server.dumpLog(String.valueOf(ThreadContext.get("scenario")));

        List<String> log = log("aSquadWithNoWheatDrawsItFromTheCampsChestBeforeItScouts");
        String scouting = "; the scouting: " + log.stream().filter(l -> l.contains("[Mission]") || l.contains("[Outcome]") || l.contains(" to lead ")
                || l.contains(" leaves the ") || l.contains(" brought ")).toList() + "; sheep at " + server.animals.tracked().stream().map(TrackedEntity::position).toList();
        assertEquals(1, near(server, 4.5, 4.5, 12), "the sheep stands where it's wanted" + scouting);
        assertTrue(log.stream().anyMatch(l -> l.contains("[Outcome] [Alpha]") && l.contains("1 of 1 sheep found brought in")), "the outcome said" + scouting);
        assertTrue(server.log.stream().noneMatch(l -> l.contains("killed sheep")), "none hunted, the mission over: " + server.log.stream().filter(l -> l.contains("killed sheep")).toList());
    }
}
