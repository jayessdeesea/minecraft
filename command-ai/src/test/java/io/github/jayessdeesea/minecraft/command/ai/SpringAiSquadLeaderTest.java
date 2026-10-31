package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SpringAiSquadLeader} as a black box wherever possible, per AGENTS.md's
 * "Behavior modeling" section: {@code buildContext}/{@code parseOrders} are pure and tested
 * directly; {@code decide}'s no-objective short-circuit is proven by wiring the model's one impure
 * seam to a fake that fails the test if it's ever actually invoked - no live server or LLM call,
 * no Mockito, mirroring {@code DeterministicSoldierTest}'s fake {@code WorldView}.
 */
class SpringAiSquadLeaderTest {

    private static final SoldierStatus ALICE =
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle());
    private static final SoldierStatus BOB =
            new SoldierStatus("Bob", new Position(3, 64, 4), new Order.TrendToward(10, 20));

    /** A {@link ChatModel} that fails the test if the LLM is actually called. */
    private static final ChatModel THROWING_MODEL = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("model should not have been called");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            throw new AssertionError("model should not have been called");
        }
    };

    @Test
    void decideReturnsIdleForEverySoldierWhenNoObjectiveReceivedYet() {
        SpringAiSquadLeader squadLeader =
                new SpringAiSquadLeader("Alpha", ChatClient.create(THROWING_MODEL));

        List<Order> orders = squadLeader.decide(List.of(ALICE, BOB));

        assertEquals(List.of(new Order.Idle(), new Order.Idle()), orders);
    }

    @Test
    void buildContextIncludesObjectiveAndEachSoldier() {
        PromptContext context = SpringAiSquadLeader.buildContext(
                new Objective("scout the ridge to the north"), List.of(ALICE, BOB));

        String prompt = context.prompt();
        assertTrue(prompt.contains("scout the ridge to the north"));
        assertTrue(prompt.contains("Alice"));
        assertTrue(prompt.contains("Bob"));
        assertTrue(prompt.contains("TrendToward(10.0, 20.0)"));
    }

    @Test
    void parseOrdersReadsOneLinePerSoldierInOrder() {
        LlmResponse response = new LlmResponse("Alice: IDLE\nBob: TREND 12.5 -3.0\n");

        List<Order> orders = SpringAiSquadLeader.parseOrders(response, List.of(ALICE, BOB));

        assertEquals(List.of(new Order.Idle(), new Order.TrendToward(12.5, -3.0)), orders);
    }

    @Test
    void parseOrdersRejectsWrongLineCount() {
        LlmResponse response = new LlmResponse("Alice: IDLE\n");

        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.parseOrders(response, List.of(ALICE, BOB)));
    }

    @Test
    void parseOrdersRejectsAMismatchedSoldierName() {
        LlmResponse response = new LlmResponse("Bob: IDLE\nAlice: IDLE\n");

        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.parseOrders(response, List.of(ALICE, BOB)));
    }

    @Test
    void parseOrdersRejectsAnUnrecognizedKeyword() {
        LlmResponse response = new LlmResponse("Alice: PATROL\nBob: IDLE\n");

        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.parseOrders(response, List.of(ALICE, BOB)));
    }

    @Test
    void parseOrdersRejectsUnparseableCoordinates() {
        LlmResponse response = new LlmResponse("Alice: IDLE\nBob: TREND north south\n");

        assertThrows(IllegalStateException.class, () -> SpringAiSquadLeader.parseOrders(response, List.of(ALICE, BOB)));
    }
}
