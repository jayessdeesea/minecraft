package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad on the farm, worked by duty (command.FarmDuties), in the squad simulation. */
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

    /** Flat, with a pond of one block ten east of the camp. */
    static int pond(int x, int y, int z) {
        return y == GROUND && x == 10 && z == 0 ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
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
     * Four on the farm: the first three harvest, none of them a crop another took, and the fourth, the
     * baker, harvests none (operator, 2026-11-18: farmers harvesting, a baker turning the wheat into bread).
     */
    @Test
    void harvestersWorkTheirOwnSharesAndTheBakerNone() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, FarmDutiesSimulationTest::pond, 24_000);
        List<String> squad = List.of("Alice", "Bob", "Carl", "Dana");
        Roster roster = new Roster("Alpha:" + String.join(",", squad));
        Position farm = new Position(10.5, GROUND + 1, 2.5);
        Objective toFarm = new Objective("Farm", farm, "Bread for the depot", new Objective.Task.Farm());
        ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> toFarm), squadLeader(farm),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "0,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{37, SimServer.itemOf(ItemClassRegistry.ItemClass.HOE), 1},
                        new int[]{38, SimServer.ITEM_BREAD, 16},
                        new int[]{10, SimServer.itemOf(ItemClassRegistry.ItemClass.SEEDS), 32})))
                .run();

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
        squad.subList(0, 3).forEach(h -> assertTrue(harvested.getOrDefault(h, 0) > 0, h + " harvested nothing: " + harvested));
        assertEquals(0, harvested.getOrDefault("Dana", 0), "the baker harvests none: " + harvested);
    }
}
