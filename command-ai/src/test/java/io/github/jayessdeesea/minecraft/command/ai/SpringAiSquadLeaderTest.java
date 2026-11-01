package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpringAiSquadLeader} per AGENTS.md's "Behavior modeling" section: the pure prompt and
 * validation steps tested directly, and the whole structured-output round trip exercised against
 * a fake model that answers canned JSON - no live LLM.
 */
class SpringAiSquadLeaderTest {

    private static final SoldierStatus ALICE =
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Cantco("no route out of the pit"), java.util.Optional.empty());
    private static final SoldierStatus BOB =
            new SoldierStatus("Bob", new Position(3, 64, 4), new Order.Idle(), new Report.Moving(),
                    java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Condition(6, 12, 1)));
    private static final Objective HILL =
            new Objective("seize the hill", new Position(10, 70, -40), "observe the valley to the north");

    private static final String GOOD_REPLY = """
            {"x": 12, "y": 71, "z": -38, "formation": "FILE", "interval": "TIGHT",
             "verticalTolerance": 3, "risk": "MEDIUM", "intent": "take the hilltop through the trees", "cantco": ""}
            """;

    /** A 3x3 survey (radius 8, spacing 8) around (0, 0): reachable land, an unreachable ridge, water, unknown. */
    private static final TerrainSurvey TERRAIN = new TerrainSurvey(0, 0, 8, 8, List.of(
            new TerrainSurvey.Sample(-8, -8, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(0, -8, TerrainSurvey.Ground.LAND, 80, false),
            new TerrainSurvey.Sample(8, -8, TerrainSurvey.Ground.WATER, 62, false),
            new TerrainSurvey.Sample(-8, 0, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(0, 0, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(8, 0, TerrainSurvey.Ground.LAND, 65, true),
            new TerrainSurvey.Sample(-8, 8, TerrainSurvey.Ground.UNKNOWN, 64, false),
            new TerrainSurvey.Sample(0, 8, TerrainSurvey.Ground.LAND, 63, true),
            new TerrainSurvey.Sample(8, 8, TerrainSurvey.Ground.LAND, 63, true)));

    private static SquadOrderReply reply(double y, Formation formation, double verticalTolerance, String intent) {
        return new SquadOrderReply(0, y, 0, formation, Interval.NORMAL, verticalTolerance, Risk.LOW, intent, "");
    }

    @Test
    void decideReturnsNothingAndSkipsTheModelBeforeAnyObjective() {
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha", ChatClient.create(FakeChatModels.THROWING));

        assertEquals(Optional.empty(), leader.decide(List.of(ALICE, BOB), TERRAIN));
    }

    @Test
    void buildContextGivesTheSituationInTaggedSectionsWithReports() {
        String prompt = SpringAiSquadLeader.buildContext("Alpha", HILL, null, List.of(ALICE, BOB), TERRAIN).prompt();

        assertTrue(prompt.contains("<objective>") && prompt.contains("seize the hill") && prompt.contains("observe the valley"));
        assertTrue(prompt.contains("Alice (point man) at (1, 64, 2): Cantco: no route out of the pit"));
        assertTrue(prompt.contains("Bob at (3, 64, 4), 3 blocks from the point man: Moving; health 6/20, food 12/20, died 1x"));
        assertTrue(prompt.contains("no route out of the pit; condition not reported yet"));
        assertTrue(prompt.contains("<current_order>\nNone yet."));
        assertTrue(prompt.contains("<terrain>"));
    }

    @Test
    void terrainRendersAsATableNorthAtTheTop() {
        String table = SpringAiSquadLeader.describe(TERRAIN);

        assertEquals("""
                <terrain>
                        x=-8   x=0   x=8
                z=-8      64   80x    ~~
                z=0       64    64    65
                z=8       ??    63    63
                </terrain>
                """, table);
    }

    @Test
    void objectiveGroundListsReachableAlternativesWhenTheObjectiveIsNot() {
        // (0, -8) is the unreachable ridge in TERRAIN.
        String ground = SpringAiSquadLeader.describeObjectiveGround(TERRAIN, new Position(0, 80, -8));

        assertEquals("""
                <objective_ground>
                The objective is NOT reachable: land the point man cannot get to.
                Reachable spots nearest the objective:
                - x=-8 y=64 z=-8: 8 blocks from the objective
                - x=0 y=64 z=0: 8 blocks from the objective
                - x=-8 y=64 z=0: 11 blocks from the objective
                - x=8 y=65 z=0: 11 blocks from the objective
                - x=0 y=63 z=8: 16 blocks from the objective
                Highest reachable spot: x=8 y=65 z=0.
                </objective_ground>
                """, ground);
    }

    @Test
    void objectiveGroundSaysWhenTheObjectiveIsBeyondTheSurvey() {
        assertTrue(SpringAiSquadLeader.describeObjectiveGround(TERRAIN, new Position(0, 64, -40))
                .contains("outside the surveyed area, 32 blocks beyond its edge"));
    }

    @Test
    void decideRoundTripsStructuredOutputIntoASquadOrder() {
        AtomicReference<Prompt> sent = new AtomicReference<>();
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha",
                ChatClient.builder(FakeChatModels.replying(GOOD_REPLY, sent)).defaultSystem("You are a squad leader.").build());
        leader.receiveObjective(HILL);

        SquadOrder order = leader.decide(List.of(ALICE, BOB), TERRAIN).orElseThrow();

        assertEquals(new SquadOrder(new Position(12, 71, -38), Formation.FILE, Interval.TIGHT, 3, Risk.MEDIUM,
                "take the hilltop through the trees"), order);
        assertEquals(new Report.Moving(), leader.report());
        assertEquals(MessageType.SYSTEM, sent.get().getInstructions().getFirst().getMessageType(), "role goes in the system prompt");
    }

    @Test
    void aCantcoInTheReplyBecomesTheSquadLeadersReport() {
        String cantco = GOOD_REPLY.replace("\"cantco\": \"\"", "\"cantco\": \"the hill is behind a ravine\"");
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha",
                ChatClient.create(FakeChatModels.replying(cantco, new AtomicReference<>())));
        leader.receiveObjective(HILL);

        leader.decide(List.of(ALICE, BOB), TERRAIN);

        assertEquals(new Report.Cantco("the hill is behind a ravine"), leader.report());
    }

    @Test
    void validationRejectsOrdersThatCantBeCarriedOut() {
        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.toSquadOrder(null));
        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.toSquadOrder(reply(400, Formation.WEDGE, 2, "x")));
        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.toSquadOrder(reply(64, null, 2, "x")));
        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.toSquadOrder(reply(64, Formation.WEDGE, 0, "x")));
        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.toSquadOrder(reply(64, Formation.WEDGE, 2, " ")));
        assertInstanceOf(SquadOrder.class, SpringAiSquadLeader.toSquadOrder(reply(64, Formation.WEDGE, 2, "hold")));
    }
}
