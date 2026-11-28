package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How fast a squad paves a chunk's slab, and where its time goes (operator, 2026-11-23: she's swapping out the level
 * under with cobblestone, which is correct, and being very inefficient with it - look into when transit time is more
 * than work time; build some evals and figure out the right thing to do). Not a pass-or-fail test and not in the suite:
 * run by name, it writes a table to {@code app/target/paving-rate.txt} - for each way of setting the squad up, how many
 * minutes its slab took, cells a soldier-minute, and for each routine the blocks walked for each item moved.
 *
 * <p>Live (field701) a slab's 240 cells went at seven a minute with four soldiers, its masons walking 140 blocks for
 * each block paved and its depositors 616 for each item, the chests by the camp full.
 */
@Tag("slow")
class PavingRateIT {

    private static final int GROUND = SquadSimulationTest.GROUND;
    private static final FarmMission FARM = FarmMission.atSeaLevel(2, 0);
    private static final Pattern SPELL = Pattern.compile("\\[Productivity] \\w+'s ([A-Z_]+): (\\d+) blocks walked in (\\d+) s for (\\d+) item");

    @TempDir
    Path state;

    @BeforeEach
    void nameTheScenario(TestInfo test) {
        ThreadContext.put("scenario", test.getTestMethod().orElseThrow().getName());
    }

    @AfterEach
    void endTheScenario() {
        ThreadContext.remove("scenario");
    }

    /** Tools, food and the cobblestone the slab takes: nothing else in the pack. */
    private static List<int[]> lean() {
        return List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                new int[]{38, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{40, SimServer.ITEM_BREAD, 64},
                new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_COBBLESTONE, 64});
    }

    /** As {@link #lean}, with the pack as a soldier's is after a week in the field: every other slot holding something, and logs for a chest. */
    private static List<int[]> loaded() {
        List<int[]> kit = new ArrayList<>(lean());
        List<Integer> stuff = List.of(SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), SimServer.itemNamedOrMinus("spruce_sapling"),
                SimServer.itemNamedOrMinus("torch"), SimServer.ITEM_DIRT, SimServer.itemNamedOrMinus("flint"), SimServer.itemNamedOrMinus("wheat"),
                SimServer.itemNamedOrMinus("stick"), SimServer.itemNamedOrMinus("coal"), SimServer.itemNamedOrMinus("copper_ingot"), SimServer.ITEM_OAK_LOG);
        for (int slot = 11; slot <= 35; slot++) {
            kit.add(new int[]{slot, stuff.get((slot - 11) % stuff.size()), 40});
        }
        return kit;
    }

    private String measure(String name, List<int[]> kit) throws IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carl,Dana");
        Position chunk = FARM.centre(FARM.steps().getFirst());
        SimSquad sim = SimSquad.of(clock, server, roster)
                .orders(s -> new Objective("Keep the camp's farm", new Position(10.5, GROUND + 1, 2.5),
                "Food for the force", new Objective.Task.Farm())).leaderAt(chunk)
                .connector(new PlayerPosition(30.5, GROUND + 1, 8.5), kit)
                .build(state.resolve(name));
        sim.chain.onOperatorFarm("Pat", FARM);
        sim.run();
        int began = -1, done = -1;
        for (int minute = 1; minute <= 120 && done < 0; minute++) {
            clock.advance(60_000);
            int paved = 0, open = 0;
            for (int x = FARM.minX(); x <= FARM.minX() + 15; x++) {
                for (int z = FARM.minZ(); z <= FARM.minZ() + 15; z++) {
                    paved += server.block(x, GROUND - 1, z) == SimServer.COBBLESTONE ? 1 : 0;
                    open += server.block(x, GROUND, z) == SimServer.AIR ? 1 : 0;
                }
            }
            if (began < 0 && open == 256) {
                began = minute; // its flatten cut: the slab's from here
            }
            if (paved == 256) {
                done = minute;
            }
        }
        Map<String, long[]> spells = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("target", "sim", ThreadContext.get("scenario") + ".log"))) {
            Matcher m = SPELL.matcher(line);
            if (m.find()) {
                long[] sum = spells.computeIfAbsent(m.group(1), k -> new long[4]);
                sum[0]++;
                sum[1] += Long.parseLong(m.group(2));
                sum[2] += Long.parseLong(m.group(3));
                sum[3] += Long.parseLong(m.group(4));
            }
        }
        StringBuilder row = new StringBuilder(String.format("%-8s flatten cut at minute %d, slab done at minute %d", name, began, done));
        if (began > 0 && done > 0) {
            row.append(String.format(" - %d minutes, %.1f cells a soldier-minute", done - began, 256.0 / (4 * Math.max(1, done - began))));
        }
        spells.forEach((routine, sum) -> row.append(String.format("%n    %-12s %3d spells, %5d blocks walked, %4d s, %4d items: %.1f blocks an item",
                routine, sum[0], sum[1], sum[2], sum[3], sum[1] / (double) Math.max(1, sum[3]))));
        return row.toString();
    }

    @Test
    void aSlabPavedByASquadCarryingOnlyWhatItTakes() throws IOException {
        Files.writeString(Path.of("target", "paving-rate-lean.txt"), measure("lean", lean()) + System.lineSeparator());
    }

    @Test
    void aSlabPavedByASquadWithItsPacksFull() throws IOException {
        Files.writeString(Path.of("target", "paving-rate-loaded.txt"), measure("loaded", loaded()) + System.lineSeparator());
    }
}
