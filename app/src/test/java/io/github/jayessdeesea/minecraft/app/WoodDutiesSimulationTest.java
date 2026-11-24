package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.WoodDuties;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
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
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad sent for wood, worked by duty (command.WoodDuties), in the squad simulation. */
@Tag("slow")
class WoodDutiesSimulationTest {

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

    /** Flat, with a wood of sixteen oaks, six high, twelve blocks apart round the camp. */
    static int wood(int x, int y, int z) {
        boolean column = Math.floorMod(x + 18, 12) == 0 && Math.floorMod(z + 18, 12) == 0 && Math.abs(x) <= 18 && Math.abs(z) <= 18;
        return column && y > GROUND && y <= GROUND + 6 ? SimServer.OAK_LOG : SquadSimulationTest.flat(x, y, z);
    }

    /** A squad leader that keeps the squad at {@code at}. */
    private static ChatClient squadLeader(Position at) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": true,
                 "intent": "work the objective", "cantco": "", "roleChanges": [], "detached": []}
                """, at.x(), at.y(), at.z());
        return ChatClient.create((ChatModel) prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /**
     * Four sent for wood: the first three each fell the trees of their own strip and nobody else's, and
     * the fourth, the planter, fells none (operator, 2026-11-18: woodcutters each in a specific area). Each
     * carries its shovel: short of one, its armourer fetches a handle's log wherever one is - the kit is
     * still the soldier's own errand (backlog/task-organisation.md, a basic load per role).
     */
    @Test
    void woodcuttersFellOnlyTheirOwnStripsAndThePlanterNone() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WoodDutiesSimulationTest::wood, 24_000);
        List<String> squad = List.of("Alice", "Bob", "Carl", "Dana");
        Roster roster = new Roster("Alpha:" + String.join(",", squad));
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        Objective forWood = new Objective("Cut wood", camp, "Logs for the depot", new Objective.Task.Gather(Resource.WOOD));
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> forWood), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.SHOVEL), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(10 * 60_000);

        // Each tree is one woodcutter's - or, its strip's half shared out with the tree begun in it, two's: never the whole
        // squad's (backlog/wood-reshare.md: a tree whose foot is cut and whose rest waits on its feller still goes with the
        // half it stands in). And the strips don't go round: each is cut out once, a handful in all, where 28 were said.
        Pattern dug = Pattern.compile("^(\\w+) dug " + SimServer.OAK_LOG + " at (-?\\d+),(-?\\d+),(-?\\d+)$");
        Map<String, Integer> felled = new HashMap<>();
        Map<String, java.util.Set<String>> fellers = new HashMap<>();
        for (String line : List.copyOf(server.log)) {
            Matcher m = dug.matcher(line);
            if (!m.matches()) {
                continue;
            }
            felled.merge(m.group(1), 1, Integer::sum);
            fellers.computeIfAbsent(m.group(2) + "," + m.group(4), t -> new java.util.TreeSet<>()).add(m.group(1));
        }
        fellers.forEach((tree, who) -> assertTrue(who.size() <= 2, "the tree at " + tree + " was felled by " + who));
        long shared = fellers.values().stream().filter(who -> who.size() == 2).count();
        assertTrue(shared <= 2, shared + " trees felled by two: " + fellers);
        List<String> cutOut;
        try {
            cutOut = java.nio.file.Files.readAllLines(java.nio.file.Path.of("target", "sim", "woodcuttersFellOnlyTheirOwnStripsAndThePlanterNone.log"))
                    .stream().filter(l -> l.contains("is cut out") && l.contains("'s strip over")).map(l -> l.substring(l.indexOf("[Alpha]"))).toList();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        // Once - and once more for a strip of the wood dealt out whole a second time, logs left standing after the first.
        assertTrue(cutOut.stream().allMatch(l -> java.util.Collections.frequency(cutOut, l) <= 2), "a strip cut out is said once a deal: " + cutOut);
        assertTrue(cutOut.size() <= 14, "the strips don't go round: " + cutOut);
        squad.subList(0, 3).forEach(w -> assertTrue(felled.getOrDefault(w, 0) > 0, w + " felled nothing: " + felled));
        assertEquals(0, felled.getOrDefault("Dana", 0), "the planter fells none: " + felled);
    }

    /** As {@link #wood}, with no tree east of x=8: the third woodcutter's strip (x 8..24) cut out before it starts. */
    static int woodCutOutEast(int x, int y, int z) {
        return x > 8 ? SquadSimulationTest.flat(x, y, z) : wood(x, y, z);
    }

    /**
     * A woodcutter whose strip has nothing to fell is moved, and only it: its squad leader, told the strip was looked
     * over and found empty, gives it half of the strip with the most standing (operator, 2026-11-18: the squad leader
     * closes the gap) - and it fells there, where before it stood behind its share while the strips went round.
     */
    @Test
    void aWoodcutterWithNothingToFellIsGivenAShareOfAnothersStrip() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, WoodDutiesSimulationTest::woodCutOutEast, 24_000);
        List<String> squad = List.of("Alice", "Bob", "Carl", "Dana");
        Roster roster = new Roster("Alpha:" + String.join(",", squad));
        Position camp = new Position(0.5, GROUND + 1, 0.5);
        Objective forWood = new Objective("Cut wood", camp, "Logs for the depot", new Objective.Task.Gather(Resource.WOOD));
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> forWood), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.SHOVEL), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16})))
                .run();

        clock.advance(6 * 60_000);

        List<String> log = java.nio.file.Files.readAllLines(Path.of("target", "sim", "aWoodcutterWithNothingToFellIsGivenAShareOfAnothersStrip.log"));
        List<String> cutOut = log.stream().filter(l -> l.contains("is cut out")).toList();
        assertTrue(cutOut.stream().anyMatch(l -> l.contains("[Alpha] Carl's strip over x 8..24, z -24..24 is cut out")), "Carl's, the east: " + cutOut);
        assertTrue(log.stream().anyMatch(l -> l.contains("[Alpha] Carl shares ")), "and given a share of another's: " + cutOut);
        assertTrue(server.log.stream().anyMatch(l -> l.startsWith("Carl dug " + SimServer.OAK_LOG)), "and it fells there; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }
}
