package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.scenario.Scenario;
import io.github.jayessdeesea.minecraft.bot.scenario.SyntheticWorld;
import io.github.jayessdeesea.minecraft.bot.scenario.Terrain;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final double PROGRESS_SLACK = 4;
    private static final java.util.Optional<io.github.jayessdeesea.minecraft.command.Condition> HEALTHY =
            java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Condition(20, 20, 0));
    private static final int ORACLE_BUDGET = 500_000;

    private record Outcome(Scenario scenario, boolean stuck, long millis, String risk, List<String> failures) {
        String name() {
            return scenario + (stuck ? " stuck" : "");
        }
    }

    @Test
    void evaluate() {
        String model = System.getProperty("eval.model", "qwen3:14b");
        int seeds = Integer.getInteger("eval.seeds", 3);
        ChatClient client = ChatClient.builder(OllamaChatModel.builder()
                        .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                        .options(OllamaChatOptions.builder().model(model).temperature(0.3).disableThinking().build())
                        .build())
                .defaultSystem(new ClassPathResource("prompts/squad-leader-system.md"))
                .build();

        List<Outcome> outcomes = new ArrayList<>();
        for (Terrain terrain : Terrain.values()) {
            for (long seed = 1; seed <= seeds; seed++) {
                for (boolean stuck : new boolean[]{false, true}) {
                    Outcome outcome = run(client, Scenario.generate(terrain, seed), stuck);
                    outcomes.add(outcome);
                    System.out.printf(Locale.ROOT, "%-24s %5dms  %-6s  %s%n", outcome.name(), outcome.millis(), outcome.risk(),
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
        List<Outcome> stuck = outcomes.stream().filter(Outcome::stuck).toList();
        System.out.printf(Locale.ROOT, "  one soldier stuck: %d/%d%n",
                stuck.stream().filter(o -> o.failures().isEmpty()).count(), stuck.size());
    }

    private static Outcome run(ChatClient client, Scenario scenario, boolean stuck) {
        SyntheticWorld world = scenario.world();
        BlockPos start = scenario.start();
        BlockPos objective = scenario.objective();
        Position pointMan = new Position(start.x() + 0.5, start.y(), start.z() + 0.5);
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Alice", pointMan, new Order.Idle(), new Report.Idle(), HEALTHY),
                soldierNear("Bob", world, start.x() - 2, start.z() + 2),
                stuck ? new SoldierStatus("Carol", new Position(start.x() + 6.5, start.y() - 4, start.z() + 4.5),
                        new Order.Idle(), new Report.Cantco("no route from the pit to within 3 of the squad's slot; "
                                + "a route with falls costing 7 of my 20 health exists (MEDIUM risk)"), HEALTHY)
                        : soldierNear("Carol", world, start.x() + 2, start.z() + 2));
        TerrainSurvey terrain = TerrainSurveyor.survey(world, pointMan);

        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha", client);
        leader.receiveObjective(new Objective("Move the squad to the objective",
                new Position(objective.x(), objective.y(), objective.z()), "secure the objective"));
        long t0 = System.nanoTime();
        SquadOrder order;
        try {
            order = leader.decide(squad, terrain).orElseThrow();
        } catch (RuntimeException e) {
            return new Outcome(scenario, stuck, (System.nanoTime() - t0) / 1_000_000, "-", List.of("invalid order: " + e.getMessage()));
        }
        long millis = (System.nanoTime() - t0) / 1_000_000;

        List<String> failures = new ArrayList<>();
        Position target = order.objective();
        Goal ordered = new Goal(target.x(), target.y(), target.z(), 1.5, order.verticalTolerance());
        if (!Pathfinder.find(world, start, ordered, ORACLE_BUDGET).reachesGoal()) {
            failures.add(String.format(Locale.ROOT, "unreachable order (%.0f, %.0f, %.0f)", target.x(), target.y(), target.z()));
        }
        double startDistance = horizontal(start.x(), start.z(), objective);
        boolean progressPossible = terrain.samples().stream().anyMatch(s -> s.reachable()
                && horizontal(s.x(), s.z(), objective) < startDistance - PROGRESS_SLACK);
        if (progressPossible && horizontal(target.x(), target.z(), objective) >= startDistance - PROGRESS_SLACK) {
            failures.add(String.format(Locale.ROOT, "no progress (%.0f, %.0f)", target.x(), target.z()));
        }
        Goal goal = new Goal(objective.x() + 0.5, objective.y(), objective.z() + 0.5, 1.5, 1);
        if (leader.report() instanceof Report.Cantco(String reason)
                && Pathfinder.find(world, start, goal, ORACLE_BUDGET).reachesGoal()) {
            failures.add("false CANTCO: " + reason);
        }
        return new Outcome(scenario, stuck, millis, order.risk().name(), failures);
    }

    private static SoldierStatus soldierNear(String name, SyntheticWorld world, int x, int z) {
        return new SoldierStatus(name, new Position(x + 0.5, world.ground(x, z) + 1, z + 0.5), new Order.Idle(), new Report.Idle(), HEALTHY);
    }

    private static double horizontal(double x, double z, BlockPos objective) {
        return Math.hypot(x - (objective.x() + 0.5), z - (objective.z() + 0.5));
    }
}
