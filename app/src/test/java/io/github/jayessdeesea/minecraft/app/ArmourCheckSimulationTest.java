package io.github.jayessdeesea.minecraft.app;

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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.GROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platoon leader's armour check (docs/echelons/platoon-leader/decisions.md, "Armour"): a soldier on its squad's
 * work, with no armour on and copper for a set in its pack, is ordered to make it and wear it - though its squad
 * leader gives no leave to rearm, and a lower rung of its ladder (a spare pickaxe, its makings to hand) is one a soldier
 * on a task doesn't make. Live (2026-11-26), Hal patrolled with 533 copper ingots and no armour on; and no soldier had a
 * shield, 384 iron ingots in the stores.
 */
@Tag("slow")
class ArmourCheckSimulationTest {

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

    /** A squad leader keeping the squad at its work, never giving leave to rearm. */
    private static ChatClient squadLeaderOnTask() {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": false,
                 "intent": "secure the camp", "cantco": "", "roleChanges": [], "detached": []}
                """, CAMP.x(), CAMP.y(), CAMP.z());
        return ChatClient.create((ChatModel) prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    @Test
    void aSoldierOnATaskWithNoArmourOnIsOrderedToMakeItAndWearsIt() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        run(clock, server, "Alpha:Alice", "", n -> List.of(new int[]{10, SimServer.itemNamedOrMinus("copper_ingot"), 24}));
        assertWorn(server);
        assertTrue(Files.readAllLines(Path.of("target/sim/aSoldierOnATaskWithNoArmourOnIsOrderedToMakeItAndWearsIt.log")).stream()
                .anyMatch(l -> l.contains("[Armour] [Alpha -> PL] Alice:") && l.contains("[PL -> Alpha] Alice: make and put on")), "the order said");
    }

    /**
     * Alice's pack empty, the copper in the camp's chest - put there by Bob of Bravo, armoured, from what he carries past
     * it: only the order lets a soldier on a task draw it for armour; one on a task makes armour of what it carries alone.
     */
    @Test
    void aSoldierOrderedToArmourDrawsTheStoresCopper() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        run(clock, server, "Alpha:Alice;Bravo:Bob", "0,0", n -> n.equals("Bob") ? List.of(
                new int[]{5, SimServer.itemNamedOrMinus("copper_helmet"), 1}, new int[]{6, SimServer.itemNamedOrMinus("copper_chestplate"), 1},
                new int[]{7, SimServer.itemNamedOrMinus("copper_leggings"), 1}, new int[]{8, SimServer.itemNamedOrMinus("copper_boots"), 1},
                new int[]{10, SimServer.itemNamedOrMinus("raw_copper"), 24}) : List.of(new int[]{13, SimServer.itemNamedOrMinus("coal"), 8}));
        assertWorn(server);
    }

    /**
     * The shield, one rule with the armour: Alice in copper with no shield, an iron ingot and logs in her pack, is ordered
     * to make one and carries it in her off hand. No camp, so no basis of issue: at SHELTER and AIRHEAD the shield isn't
     * issued (military-doctrine.md § 11) and isn't ordered.
     */
    @Test
    void aSoldierOnATaskWithNoShieldIsOrderedToMakeOneAndCarriesItInTheOffHand() throws java.io.IOException {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, SquadSimulationTest::flat, 1000);
        run(clock, server, "Alpha:Alice", "", n -> List.of(
                new int[]{5, SimServer.itemNamedOrMinus("copper_helmet"), 1}, new int[]{6, SimServer.itemNamedOrMinus("copper_chestplate"), 1},
                new int[]{7, SimServer.itemNamedOrMinus("copper_leggings"), 1}, new int[]{8, SimServer.itemNamedOrMinus("copper_boots"), 1},
                new int[]{10, SimServer.itemNamedOrMinus("iron_ingot"), 1}));
        assertEquals(SimServer.itemNamedOrMinus("shield"), server.itemAt("Alice", 45),
                "a shield in the off hand; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
        assertTrue(Files.readAllLines(Path.of("target/sim/aSoldierOnATaskWithNoShieldIsOrderedToMakeOneAndCarriesItInTheOffHand.log")).stream()
                .anyMatch(l -> l.contains("[PL -> Alpha] Alice: make and put on [shield]")), "the order said");
    }

    /** {@code roster} on a task at the camp, no leave to rearm, each carrying a stone sword and pickaxe, bread, logs, cobblestone and {@code more}; run. */
    private void run(SimClock clock, SimServer server, String roster, String camp, java.util.function.Function<String, List<int[]>> more) {
        Roster soldiers = new Roster(roster);
        SimSquad.of(clock, server, soldiers)
                .orders(s -> new Objective("Secure the camp", CAMP,
                "Security", new Objective.Task.Secure())).leader(squadLeaderOnTask()).home(camp)
                .connector(n -> new PlayerPosition(0.5, GROUND + 1, 0.5), n -> {
                    List<int[]> kit = new java.util.ArrayList<>(List.of(
                            new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1},
                            new int[]{37, SimServer.ITEM_STONE_PICKAXE, 1},
                            new int[]{38, SimServer.ITEM_BREAD, 16},
                            new int[]{11, SimServer.ITEM_OAK_LOG, 8},
                            new int[]{12, SimServer.ITEM_COBBLESTONE, 16}));
                    kit.addAll(more.apply(n));
                    return kit;
                })
                .start(state);
        clock.advance(10 * 60_000);
    }

    private static void assertWorn(SimServer server) {
        // Worn: the four armour slots, 5 to 8, hold the four copper pieces.
        List<Integer> worn = List.of(5, 6, 7, 8).stream().map(slot -> server.itemAt("Alice", slot)).toList();
        assertEquals(List.of("copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots").stream()
                        .map(SimServer::itemNamedOrMinus).toList(), worn,
                "a full set of copper on; last server events: " + server.log.subList(Math.max(0, server.log.size() - 12), server.log.size()));
    }
}
