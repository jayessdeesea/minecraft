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

        Map<String, WoodDuties.Duty> duties = WoodDuties.of(Optional.of(forWood), squad);
        Pattern dug = Pattern.compile("^(\\w+) dug " + SimServer.OAK_LOG + " at (-?\\d+),(-?\\d+),(-?\\d+)$");
        Map<String, Integer> felled = new HashMap<>();
        for (String line : List.copyOf(server.log)) {
            Matcher m = dug.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String who = m.group(1);
            int x = Integer.parseInt(m.group(2));
            int z = Integer.parseInt(m.group(4));
            felled.merge(who, 1, Integer::sum);
            Box strip = duties.get(who).area();
            assertTrue(x >= strip.minX() - 1 && x <= strip.maxX() + 1 && z >= strip.minZ() - 1 && z <= strip.maxZ() + 1,
                    who + " felled a log at " + x + ", " + z + " outside its strip " + strip);
        }
        squad.subList(0, 3).forEach(w -> assertTrue(felled.getOrDefault(w, 0) > 0, w + " felled nothing: " + felled));
        assertEquals(0, felled.getOrDefault("Dana", 0), "the planter fells none: " + felled);
    }

    /** As {@link #wood}, with no tree east of x=8: the third woodcutter's strip (x 8..24) cut out before it starts. */
    static int woodCutOutEast(int x, int y, int z) {
        return x > 8 ? SquadSimulationTest.flat(x, y, z) : wood(x, y, z);
    }

    /**
     * A woodcutter whose strip has nothing to fell makes nothing: its squad leader alarms that it's behind its share,
     * and the ones felling aren't (operator, 2026-11-18: the squad leader alarms if the soldiers aren't completing
     * their task in a timely way).
     */
    @Test
    void aWoodcutterWithNothingToFellIsAlarmedBehindItsShare() throws java.io.IOException {
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

        clock.advance(4 * 60_000); // Carl's due, the others' strips still standing

        List<String> alarms = java.nio.file.Files.readAllLines(Path.of("target", "sim", "aWoodcutterWithNothingToFellIsAlarmedBehindItsShare.log"))
                .stream().filter(l -> l.contains("[Alarm]")).toList();
        assertTrue(alarms.stream().anyMatch(l -> l.contains("[Alarm] [Alpha] Carl behind its share: woodcutter over x 8..24")), "Carl: " + alarms);
        assertTrue(alarms.stream().noneMatch(l -> l.contains("Alice behind") || l.contains("Bob behind")), "the ones felling: " + alarms);
        // And its squad leader, told the strip found nothing, shares the wood again without it (command.WoodDuties.worked).
        assertTrue(java.nio.file.Files.readAllLines(Path.of("target", "sim", "aWoodcutterWithNothingToFellIsAlarmedBehindItsShare.log")).stream()
                .anyMatch(l -> l.contains("[Alpha] the strip over x 8..24, z -24..24 is cut out - the wood shared again without it")), "the east re-shared");
    }
}
