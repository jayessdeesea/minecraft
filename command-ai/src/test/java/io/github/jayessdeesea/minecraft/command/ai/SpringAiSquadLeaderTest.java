package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
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
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Cantco("no route out of the pit"));
    private static final SoldierStatus BOB =
            new SoldierStatus("Bob", new Position(3, 64, 4), new Order.Idle(), new Report.Moving());
    private static final Objective HILL =
            new Objective("seize the hill", new Position(10, 70, -40), "observe the valley to the north");

    private static final String GOOD_REPLY = """
            {"x": 12, "y": 71, "z": -38, "formation": "FILE", "interval": "TIGHT",
             "verticalTolerance": 3, "intent": "take the hilltop through the trees", "cantco": ""}
            """;

    private static SquadOrderReply reply(double y, Formation formation, double verticalTolerance, String intent) {
        return new SquadOrderReply(0, y, 0, formation, Interval.NORMAL, verticalTolerance, intent, "");
    }

    @Test
    void decideReturnsNothingAndSkipsTheModelBeforeAnyObjective() {
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha", ChatClient.create(FakeChatModels.THROWING));

        assertEquals(Optional.empty(), leader.decide(List.of(ALICE, BOB)));
    }

    @Test
    void buildContextGivesTheSituationInTaggedSectionsWithReports() {
        String prompt = SpringAiSquadLeader.buildContext("Alpha", HILL, null, List.of(ALICE, BOB)).prompt();

        assertTrue(prompt.contains("<objective>") && prompt.contains("seize the hill") && prompt.contains("observe the valley"));
        assertTrue(prompt.contains("Alice (point man) at (1, 64, 2): Cantco: no route out of the pit"));
        assertTrue(prompt.contains("Bob at (3, 64, 4): Moving"));
        assertTrue(prompt.contains("<current_order>\nNone yet."));
    }

    @Test
    void decideRoundTripsStructuredOutputIntoASquadOrder() {
        AtomicReference<Prompt> sent = new AtomicReference<>();
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha",
                ChatClient.builder(FakeChatModels.replying(GOOD_REPLY, sent)).defaultSystem("You are a squad leader.").build());
        leader.receiveObjective(HILL);

        SquadOrder order = leader.decide(List.of(ALICE, BOB)).orElseThrow();

        assertEquals(new SquadOrder(new Position(12, 71, -38), Formation.FILE, Interval.TIGHT, 3,
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

        leader.decide(List.of(ALICE, BOB));

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
