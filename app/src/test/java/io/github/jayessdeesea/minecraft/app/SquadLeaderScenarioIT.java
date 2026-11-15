package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.scenario.Scenario;
import io.github.jayessdeesea.minecraft.bot.scenario.SyntheticWorld;
import io.github.jayessdeesea.minecraft.bot.scenario.Terrain;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Role;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import io.github.jayessdeesea.minecraft.app.SquadLeaderScenarios.Outcome;
import io.github.jayessdeesea.minecraft.app.SquadLeaderScenarios.Variant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Squad-leader evaluation: the real {@link SpringAiSquadLeader}, with the real system prompt,
 * against a local model, across the scenario harness's terrain families. Reports a pass rate
 * rather than asserting one - a model's judgement is statistical - so it's an {@code *IT}, run on
 * demand, never part of {@code mvn test}. Needs Ollama (DEVELOPMENT.md § 1), not the server.
 *
 * <pre>mvn -pl app -am test -Dtest=SquadLeaderScenarioIT -Dsurefire.failIfNoSpecifiedTests=false
 *     [-Deval.seeds=5] [-Deval.model=qwen3:14b]</pre>
 *
 * <p>Each order is scored on three checks, each judged against ground truth the model never sees:
 * <ul>
 *   <li><b>reachable</b> - the point man can actually get to the ordered spot (oracle path search);</li>
 *   <li><b>progress</b> - it's meaningfully closer to the objective than the start, whenever some
 *       surveyed reachable ground is;</li>
 *   <li><b>no false CANTCO</b> - it doesn't call the mission impossible when the objective is
 *       reachable.</li>
 * </ul>
 */
class SquadLeaderScenarioIT {



    @Test
    void evaluate() {
        String model = System.getProperty("eval.model", "qwen3:14b");
        int seeds = Integer.getInteger("eval.seeds", 3);
        ChatModel ollama = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                .options(OllamaChatOptions.builder().model(model).temperature(0.3).disableThinking().build())
                .build();

        List<Outcome> outcomes = new ArrayList<>();
        for (Terrain terrain : Terrain.values()) {
            for (long seed = 1; seed <= seeds; seed++) {
                for (Variant variant : Variant.values()) {
                    Outcome outcome = run(ollama, Scenario.generate(terrain, seed), variant);
                    outcomes.add(outcome);
                    System.out.printf(Locale.ROOT, "%-24s %5dms  %-18s  %s%n", outcome.name(), outcome.millis(), outcome.risk(),
                            outcome.failures().isEmpty() ? "PASS" : "FAIL " + String.join("; ", outcome.failures()));
                }
            }
        }
        System.out.printf(Locale.ROOT, "%n%s: %d/%d pass%n", model,
                outcomes.stream().filter(o -> o.failures().isEmpty()).count(), outcomes.size());
        for (Terrain terrain : Terrain.values()) {
            List<Outcome> family = outcomes.stream().filter(o -> o.scenario().terrain() == terrain).toList();
            System.out.printf(Locale.ROOT, "  %-9s %d/%d%n", terrain.name().toLowerCase(),
                    family.stream().filter(o -> o.failures().isEmpty()).count(), family.size());
        }
        for (Variant variant : Variant.values()) {
            List<Outcome> of = outcomes.stream().filter(o -> o.variant() == variant).toList();
            System.out.printf(Locale.ROOT, "  %-9s %d/%d%n", variant.name().toLowerCase(Locale.ROOT),
                    of.stream().filter(o -> o.failures().isEmpty()).count(), of.size());
        }
    }

    /** The model, except that the first call is answered with {@code first} - a standing order to set up. */
    private static ChatModel answeringFirst(String first, ChatModel model) {
        AtomicBoolean answered = new AtomicBoolean();
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return answered.getAndSet(true) ? model.call(prompt) : new ChatResponse(List.of(new Generation(new AssistantMessage(first))));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return model.getDefaultOptions();
            }

            @Override
            public ChatOptions getOptions() {
                return model.getOptions();
            }
        };
    }

    private static Outcome run(ChatModel model, Scenario scenario, Variant variant) {
        BlockPos start = scenario.start();
        String standing = String.format(Locale.ROOT, """
                {"x": %d, "y": %d, "z": %d, "formation": "WEDGE", "interval": "NORMAL", "verticalTolerance": 3,
                 "risk": "LOW", "soldierRisks": [], "rearm": false, "intent": "hold the high ground and observe", "cantco": "",
                 "roleChanges": [], "detached": []}""",
                start.x(), start.y(), start.z());
        ChatClient client = ChatClient.builder(variant == Variant.RETASKED ? answeringFirst(standing, model) : model).build();
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha", client, SquadLeaderPrompts.read(
                new ClassPathResource("prompts/squad-leader-system-prompt.md"), new ClassPathResource("prompts/squad-leader-user-prompt.md")));
        return SquadLeaderScenarios.run(leader, scenario, variant);
    }

}
