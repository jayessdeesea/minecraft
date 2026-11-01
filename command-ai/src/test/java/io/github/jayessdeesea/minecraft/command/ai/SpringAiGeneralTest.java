package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
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
 * Exercises {@link SpringAiGeneral} the same way {@code SpringAiSquadLeaderTest} exercises
 * {@link SpringAiSquadLeader}: pure {@code buildContext}/{@code parseObjectives} tested directly,
 * {@code decide}'s empty-squad-list short-circuit proven against a model that fails the test if
 * it's ever actually invoked.
 */
class SpringAiGeneralTest {

    private static final SquadStatus ALPHA = new SquadStatus("Alpha", List.of(
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Idle())));
    private static final SquadStatus BRAVO = new SquadStatus("Bravo", List.of(
            new SoldierStatus("Bob", new Position(3, 64, 4), new Order.Idle(), new Report.Idle())));

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
    void decideReturnsNoObjectivesForAnEmptySquadList() {
        SpringAiGeneral general = new SpringAiGeneral(ChatClient.builder(THROWING_MODEL));

        assertEquals(List.of(), general.decide(List.of()));
    }

    @Test
    void buildContextIncludesEachSquadAndSoldier() {
        PromptContext context = SpringAiGeneral.buildContext(List.of(ALPHA, BRAVO));

        String prompt = context.prompt();
        assertTrue(prompt.contains("Alpha"));
        assertTrue(prompt.contains("Alice"));
        assertTrue(prompt.contains("Bravo"));
        assertTrue(prompt.contains("Bob"));
    }

    @Test
    void parseObjectivesReadsOneLinePerSquadInOrder() {
        LlmResponse response = new LlmResponse("Alpha: scout north\nBravo: hold the ridge\n");

        List<Objective> objectives = SpringAiGeneral.parseObjectives(response, List.of(ALPHA, BRAVO));

        assertEquals(List.of(new Objective("scout north"), new Objective("hold the ridge")), objectives);
    }

    @Test
    void parseObjectivesRejectsWrongLineCount() {
        LlmResponse response = new LlmResponse("Alpha: scout north\n");

        assertThrows(IllegalStateException.class, () -> SpringAiGeneral.parseObjectives(response, List.of(ALPHA, BRAVO)));
    }

    @Test
    void parseObjectivesRejectsAMismatchedSquadName() {
        LlmResponse response = new LlmResponse("Bravo: hold the ridge\nAlpha: scout north\n");

        assertThrows(IllegalStateException.class, () -> SpringAiGeneral.parseObjectives(response, List.of(ALPHA, BRAVO)));
    }

    @Test
    void parseObjectivesRejectsAnEmptyObjective() {
        LlmResponse response = new LlmResponse("Alpha: \nBravo: hold the ridge\n");

        assertThrows(IllegalStateException.class, () -> SpringAiGeneral.parseObjectives(response, List.of(ALPHA, BRAVO)));
    }
}
