package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad on the farm, worked by duty (command.FarmDuties), in the squad simulation. */
@Tag("slow")
class FarmDutiesSimulationTest {

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
     * Four on the farm: the first three harvest, none of them a crop another took, and the fourth, the
     * baker, harvests none (operator, 2026-11-18: farmers harvesting, a baker turning the wheat into bread).
     */
    @Test
    void harvestersWorkTheirOwnSharesAndTheBakerNone() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SimSquad::pond, 24_000);
        List<String> squad = SimSquad.FARM_SQUAD;
        SimSquad.farmSquad(clock, server)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32}))
                .start(state);

        clock.advance(20 * 60_000);

        Pattern dug = Pattern.compile("^(\\w+) dug " + SimServer.WHEAT_RIPE + " at (-?\\d+),(-?\\d+),(-?\\d+)$");
        Map<String, Integer> harvested = new HashMap<>();
        Map<String, String> byCell = new HashMap<>();
        for (String line : List.copyOf(server.log)) {
            Matcher m = dug.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String who = m.group(1);
            String cell = m.group(2) + "," + m.group(4);
            harvested.merge(who, 1, Integer::sum);
            String before = byCell.putIfAbsent(cell, who);
            assertTrue(before == null || before.equals(who), who + " harvested " + cell + ", " + before + "'s share");
        }
        // The platoon leader's order names the ground, computed for it: the farm point's chunk.
        assertTrue(List.copyOf(server.log).stream().anyMatch(l -> l.contains("OPORD: Farm at") && l.contains("in 1 box over x 0..15, z 0..15")),
                "the farm's OPORD names its boxes: " + List.copyOf(server.log).stream().filter(l -> l.contains("OPORD")).findFirst().orElse("none"));
        squad.subList(0, 3).forEach(h -> assertTrue(harvested.getOrDefault(h, 0) > 0, h + " harvested nothing: " + harvested));
        // Each one's work order from its squad leader, said in its log: its columns of the plot, or the baking.
        List<String> work = java.nio.file.Files.readAllLines(Path.of("target", "sim", "harvestersWorkTheirOwnSharesAndTheBakerNone.log"))
                .stream().filter(l -> l.contains("DUTY:")).toList();
        squad.subList(0, 3).forEach(h -> assertTrue(work.stream().anyMatch(l -> l.contains("[Alpha -> " + h + "] DUTY: harvester over x")), h + ": " + work));
        assertTrue(work.stream().anyMatch(l -> l.contains("[Alpha -> Dana] DUTY: baker")), "Dana: " + work);
        // And none of them alarmed behind its share while its plot grows: a share with nothing to do isn't behind.
        List<String> behind = java.nio.file.Files.readAllLines(Path.of("target", "sim", "harvestersWorkTheirOwnSharesAndTheBakerNone.log"))
                .stream().filter(l -> l.contains("behind its share")).toList();
        assertEquals(List.of(), behind.stream().filter(l -> l.contains("harvester")).toList());
        assertEquals(0, harvested.getOrDefault("Dana", 0), "the baker harvests none: " + harvested);
    }

    /** The camp's table, at (1, 64, -2), on the pond's ground: the one a farm's baker's order names. */
    static int campTable(int x, int y, int z) {
        return x == 1 && z == -2 && y == GROUND + 1 ? SimServer.CRAFTING_TABLE : SimSquad.pond(x, y, z);
    }

    /** A baker's kit, as live: a full basic load - the batches past it are the stores' - and logs for its workshop's chest. */
    private static List<int[]> bakersKit(int wheat) {
        return List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                new int[]{38, SimServer.ITEM_BREAD, 64}, new int[]{39, SimServer.ITEM_OAK_LOG, 8},
                new int[]{12, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), wheat}, new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32});
    }

    /** The scenario's log, as the runner wrote it. */
    private static List<String> log(String scenario) throws java.io.IOException {
        // Not there yet is nothing said yet: its appender makes the file at its first line, which under a full verify's
        // parallel load can come after the first look (merge verify of duty-orders: NoSuchFileException 8 ms in).
        Path file = Path.of("target", "sim", scenario + ".log");
        return java.nio.file.Files.exists(file) ? java.nio.file.Files.readAllLines(file) : List.of();
    }

    /**
     * The duty orders' eval (backlog/duty-orders.md): the baker bakes at the table its order names, not the one nearest it -
     * a table beside the dump's chest it draws at. Where it's worked is its squad leader's.
     */
    @Test
    void aBakerBakesAtTheTableItsOrderNames() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 5 && z == 1 && y == GROUND + 1 ? SimServer.CRAFTING_TABLE : campTable(x, y, z), 24_000);
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 64);
        java.nio.file.Files.writeString(state.resolve("chests_stores_by_chest.json"), "[[3,64,1,0,0,0,0,0,0,0,0,64,0,0]]");
        SimSquad.farmSquad(clock, server).kit(bakersKit(15)).start(state);

        clock.advance(10 * 60_000);

        List<String> baked = List.copyOf(server.log).stream().filter(l -> l.startsWith("Dana crafted bread")).toList();
        assertTrue(!baked.isEmpty(), "the baker baked: " + log("aBakerBakesAtTheTableItsOrderNames").stream().filter(l -> l.contains("Dana crafts")).toList());
        assertEquals(List.of(), baked.stream().filter(l -> !l.endsWith(" at " + SimServer.key(1, GROUND + 1, -2))).toList(),
                "every loaf at its order's table, " + SimServer.key(1, GROUND + 1, -2) + ", none at the one by the chest, " + SimServer.key(5, GROUND + 1, 1));
    }

    /** The lines of {@code scenario}'s log holding {@code text}, for the eval's record: what was said, and when. */
    private static List<String> said(String scenario, String text) throws java.io.IOException {
        List<String> found = log(scenario).stream().filter(l -> l.contains(text)).toList();
        found.stream().limit(3).forEach(l -> System.out.println("[Eval] duty-orders/" + scenario + ": " + l.substring(Math.max(0, l.indexOf('['))))); // its yield and time to detection, as a number
        return found;
    }

    /**
     * The duty orders' eval, no wheat anywhere: the baker idle, its squad leader's report up the chain names it - "no wheat
     * stored" - and it isn't behind its share: nothing in its share to work.
     */
    @Test
    void aBakerWithNoWheatAnywhereIsReportedIdleNotBehind() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::campTable, 24_000);
        SimSquad.farmSquad(clock, server).kit(bakersKit(0)).start(state);

        clock.advance(4 * 60_000);

        String scenario = "aBakerWithNoWheatAnywhereIsReportedIdleNotBehind";
        assertTrue(!said(scenario, "[Alpha -> PL] Dana, baker, idle: no wheat stored").isEmpty(), "said up the chain: "
                + log(scenario).stream().filter(l -> l.contains("Dana") && (l.contains("[Alarm]") || l.contains("-> PL]"))).toList());
        assertEquals(List.of(), said(scenario, "Dana behind its share"), "idle, not behind");
    }

    /**
     * The duty orders' eval, the table gone: the camp's table dug once its baker's order names it, with no other standing
     * and no logs to fit one - the baker's CANTCO, and its squad leader's answer: its order says no table stands, and the CANTCO goes up
     * the chain.
     */
    @Test
    void aBakersTableDugIsItsCantcoAnsweredUpTheChain() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::campTable, 24_000);
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 64);
        java.nio.file.Files.writeString(state.resolve("chests_stores_by_chest.json"), "[[3,64,1,0,0,0,0,0,0,0,0,64,0,0]]");
        SimSquad.farmSquad(clock, server).kit(List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                new int[]{38, SimServer.ITEM_BREAD, 64}, new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32},
                new int[]{12, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 15})).start(state);

        String scenario = "aBakersTableDugIsItsCantcoAnsweredUpTheChain";
        for (int s = 0; s < 60 && log(scenario).stream().noneMatch(l -> l.contains("-> Dana] DUTY: baker") && l.contains("at the table at (1, 64, -2)")); s++) {
            clock.advance(1_000); // till its order names the camp's table, or a minute: a camp's workshop sited before its table's chunk is seen names none
        }
        server.set(1, GROUND + 1, -2, SimServer.AIR);
        clock.advance(8 * 60_000);

        assertTrue(!said(scenario, "-> Dana] DUTY: baker").stream().filter(l -> l.contains("no table standing")).toList().isEmpty(), "its order: "
                + log(scenario).stream().filter(l -> l.contains("-> Dana] DUTY")).toList());
        assertTrue(!said(scenario, "[Alpha -> PL] Dana, baker, CANTCO").isEmpty(), "up the chain: "
                + log(scenario).stream().filter(l -> l.contains("Dana") && (l.contains("[Alarm]") || l.contains("-> PL]") || l.contains("gives up"))).toList());
    }

    /**
     * The duty orders' eval, the baker pulled off: a fight at its table from the first, the spell long - its yield short, the
     * cause said, and its order the same: not re-ordered. A known failure (below).
     */
    @Test
    void aBakerPulledOffByAFightIsShortForThatCauseNotReordered() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::campTable, 24_000);
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 64);
        java.nio.file.Files.writeString(state.resolve("chests_stores_by_chest.json"), "[[3,64,1,0,0,0,0,0,0,0,0,64,0,0]]");
        SimSquad.farmSquad(clock, server).kit(bakersKit(15)).start(state);

        for (int i = 0; i < 240; i++) { // a zombie by its table every second, from the start, for four minutes: the fight unbroken
            server.spawnZombie(2.5, GROUND + 1, -0.5);
            clock.advance(1_000);
        }
        clock.advance(60_000);

        String scenario = "aBakerPulledOffByAFightIsShortForThatCauseNotReordered";
        boolean correct = said(scenario, "[Alpha] Dana short of its share").stream().anyMatch(l -> l.contains("its hands CONTACT's"));
        System.out.println("[Eval] duty-orders/baker-pulled-off: " + (correct ? "PASS" : "FAIL (known)") + " - "
                + log(scenario).stream().filter(l -> l.contains("Dana drew") || l.contains("Dana made bread") || l.contains("Dana short of")).count()
                + " draws, bakes and misses; " + log(scenario).stream().filter(l -> l.contains("[Contact] Dana at")).count() + " contacts");
        // Known failure: in the simulation a fight doesn't pull the baker off - it draws and bakes in contact, its yield met -
        // so the miss and its cause (command.MissAnswer, its rule unit-tested) aren't reached here yet.
        assertTrue(!correct, "baker-pulled-off passes now: take its known-failure mark off, so it holds the answer");
    }

    /**
     * A baker with wheat for less than a batch, and more in a chest, draws the rest and bakes: live (field657), Pete
     * carried 15 wheat beside chests of 544 and baked nothing - it baked only a batch of 8 loaves' worth (24) and drew
     * only below one loaf's (3), so between the two it waited for good, the stores at no bread.
     */
    @Test
    void aBakerShortOfABatchDrawsTheRestAndBakes() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::campTable, 24_000); // the camp's table
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 64); // the dump's chest, wheat in it
        java.nio.file.Files.writeString(state.resolve("chests_stores_by_chest.json"), "[[3,64,1,0,0,0,0,0,0,0,0,64,0,0]]"); // known from before a restart, as live
        SimSquad.farmSquad(clock, server)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 64}, // a full basic load, as live: the batches past it are the stores'
                        new int[]{39, SimServer.ITEM_OAK_LOG, 8}, // the workshop's chest, the stockpile - as live
                        new int[]{12, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 15},
                        new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32}))
                .start(state);

        clock.advance(10 * 60_000);

        List<String> log = java.nio.file.Files.readAllLines(Path.of("target", "sim", "aBakerShortOfABatchDrawsTheRestAndBakes.log"));
        assertTrue(log.stream().anyMatch(l -> l.contains("Dana drew WHEAT")), "the baker drew wheat to a batch: "
                + log.stream().filter(l -> l.contains("[Baker]") || l.contains("Dana drew")).reduce((a, b) -> b).orElse("no baker line"));
        assertTrue(log.stream().anyMatch(l -> l.contains("Dana crafts") && l.contains(" bread")), "and baked it");
        // And took what's past its own load to the stores: live (field664), its baker carried 162, the stores at none.
        assertTrue(log.stream().anyMatch(l -> l.contains("Dana stockpiled its load") && !l.contains(", 0 rations")), "its bread in the stores: "
                + log.stream().filter(l -> l.contains("Dana stockpiled")).toList());
        assertTrue(log.stream().anyMatch(l -> l.contains("[Alpha -> Dana] DUTY: baker") && l.contains("drawing from (3, 64, 1)")
                && l.contains("at the table at (1, 64, -2)") && l.contains("8 a batch")), "its order names its makings: "
                + log.stream().filter(l -> l.contains("-> Dana] DUTY")).toList());
    }

    /**
     * The duty orders' eval (backlog/duty-orders.md), its source running dry: the baker's chest emptied, wheat in another -
     * its order re-pointed by its squad leader, said down the chain, and the baker draws from the next. The soldier picks
     * no chest of its own.
     */
    @Test
    void aBakersSourceRunDryIsRepointedToTheNextChest() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::campTable, 24_000);
        server.stockedChest(3, GROUND + 1, 1, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 20);
        server.stockedChest(6, GROUND + 1, -3, SimServer.itemOf(ItemClassRegistry.ItemClass.WHEAT), 64);
        java.nio.file.Files.writeString(state.resolve("chests_stores_by_chest.json"),
                "[[3,64,1,0,0,0,0,0,0,0,0,20,0,0],[6,64,-3,0,0,0,0,0,0,0,0,64,0,0]]"); // both known from before a restart
        SimSquad.farmSquad(clock, server)
                .kit(List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32}))
                .start(state);

        clock.advance(12 * 60_000);

        List<String> log = java.nio.file.Files.readAllLines(Path.of("target", "sim", "aBakersSourceRunDryIsRepointedToTheNextChest.log"));
        List<String> orders = log.stream().filter(l -> l.contains("-> Dana] DUTY: baker")).toList();
        assertTrue(orders.stream().anyMatch(l -> l.contains("drawing from (3, 64, 1)")), "first the nearer chest: " + orders);
        assertTrue(orders.stream().anyMatch(l -> l.contains("drawing from (6, 64, -3)")), "then, it run dry, the next: " + orders);
        assertTrue(log.stream().filter(l -> l.contains("Dana drew WHEAT")).count() >= 2, "drawn from both: "
                + log.stream().filter(l -> l.contains("Dana drew")).toList());
    }
}
