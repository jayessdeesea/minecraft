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

    private static final double PROGRESS_SLACK = 4;
    private static final Optional<Condition> HEALTHY =
            Optional.of(new Condition(20, 20, 0, Optional.empty()));
    private static final int ORACLE_BUDGET = 500_000;

    /**
     * PLAIN: a fresh squad. STUCK: Carol is in a pit and says what a riskier order would cost her.
     * RETASKED: the squad is in position at its last order's spot, near the start, when a new
     * objective arrives - the case where a model can anchor on its standing order.
     */
    /**
     * STRANDED as live (field50-53): a soldier on a ledge for an hour, starving, every way down a
     * fall that would kill it - the recovery drill's, not the squad's mission's, to solve - shown as
     * the squad leader sees it once the drill has it: Recovering, at the rear.
     */
    /**
     * The task organisation (docs/military-doctrine.md § 18): MINER, the general asks for one soldier
     * on a mine at a point - one role change, MINER, at that site; POST, for Bob posted as a lookout
     * at a point - his GO_TO there; KEPT, Carol already the squad's miner and nothing asked - her role
     * left as it is. In every other variant, no plan changes at all.
     */
    private enum Variant { PLAIN, STUCK, RETASKED, STRANDED, STRUGGLING, MINER, POST, KEPT, TIMEDOUT }

    private record Outcome(Scenario scenario, Variant variant, long millis, String risk, List<String> failures) {
        String name() {
            return scenario + (variant == Variant.PLAIN ? "" : " " + variant.name().toLowerCase(Locale.ROOT));
        }
    }

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
        boolean stuck = variant == Variant.STUCK;
        boolean stranded = variant == Variant.STRANDED;
        // STRUGGLING as live (field54): one refusing a job it can't start, one starving at 1 health,
        // and the squad's mission still in reach - the squad leader called it CANTCO "but the squad
        // can still proceed", and the general moved the squad on for it.
        boolean struggling = variant == Variant.STRUGGLING;
        Report settled = variant == Variant.RETASKED ? new Report.InPosition() : new Report.Idle();
        SyntheticWorld world = scenario.world();
        BlockPos start = scenario.start();
        BlockPos objective = scenario.objective();
        Position pointMan = new Position(start.x() + 0.5, start.y(), start.z() + 0.5);
        List<SoldierStatus> squad = struggling ? List.of(
                new SoldierStatus("Alice", pointMan, new Order.Idle(), settled, HEALTHY),
                new SoldierStatus("Bob", new Position(start.x() - 1.5, world.ground(start.x() - 2, start.z() + 2) + 1, start.z() + 2.5),
                        new Order.Idle(), new Report.Cantco("can't make 1 wooden pickaxe: no table or furnace to use within 16 blocks, and not the makings of one"),
                        HEALTHY),
                new SoldierStatus("Carol", new Position(start.x() + 2.5, world.ground(start.x() + 2, start.z() + 2) + 1, start.z() + 2.5),
                        new Order.Idle(), settled, Optional.of(new Condition(1, 0, 0, Optional.empty()))))
                : List.of(
                new SoldierStatus("Alice", pointMan, new Order.Idle(), settled, HEALTHY),
                soldierNear("Bob", world, start.x() - 2, start.z() + 2, settled),
                stuck ? new SoldierStatus("Carol", new Position(start.x() + 6.5, start.y() - 4, start.z() + 4.5),
                        new Order.Idle(), new Report.Cantco("no route from the pit to within 3 of the squad's slot; "
                                + "a route with falls costing 7 of my 20 health exists (MEDIUM risk)"), HEALTHY)
                        : stranded ? new SoldierStatus("Carol", new Position(start.x() + 6.5, start.y() + 4, start.z() + 4.5),
                        new Order.Idle(), new Report.Recovering(),
                        Optional.of(new Condition(8, 0, 0, Optional.empty())))
                        : soldierNear("Carol", world, start.x() + 2, start.z() + 2, settled));
        TerrainSurvey terrain = TerrainSurveyor.survey(world, pointMan);

        String standing = String.format(Locale.ROOT, """
                {"x": %d, "y": %d, "z": %d, "formation": "WEDGE", "interval": "NORMAL", "verticalTolerance": 3,
                 "risk": "LOW", "soldierRisks": [], "rearm": false, "intent": "hold the high ground and observe", "cantco": "",
                 "roleChanges": [], "detached": []}""",
                start.x(), start.y(), start.z());
        ChatClient client = ChatClient.builder(variant == Variant.RETASKED ? answeringFirst(standing, model) : model).build();
        SpringAiSquadLeader leader = new SpringAiSquadLeader("Alpha", client, SquadLeaderPrompts.read(
                new ClassPathResource("prompts/squad-leader-system-prompt.md"), new ClassPathResource("prompts/squad-leader-user-prompt.md")));
        if (variant == Variant.RETASKED) {
            leader.receiveObjective(new Objective("Establish an observation post", pointMan, "watch for hostiles"));
            leader.decide(squad, terrain);
        }
        Position mine = new Position(start.x() + 6, 16, start.z() - 6);
        Position post = new Position(start.x() + 4.5, world.ground(start.x() + 4, start.z() - 4) + 1, start.z() - 3.5);
        String mission = switch (variant) {
            case MINER -> String.format(Locale.ROOT, "Move the squad to the objective, and detail one soldier to mine for iron at x=%.0f, z=%.0f, down to y=%.0f",
                    mine.x(), mine.z(), mine.y());
            case POST -> String.format(Locale.ROOT, "Move the squad to the objective, and post Bob as a lookout at x=%.1f, y=%.0f, z=%.1f",
                    post.x(), post.y(), post.z());
            default -> "Move the squad to the objective";
        };
        Plan carolMining = new Plan(
                Role.MINER, Optional.of(mine), List.of());
        if (variant == Variant.KEPT) {
            leader.receivePlans(Map.of("Carol", carolMining), Map.of());
        }
        // TIMEDOUT: Bob's fetch timed out - nothing to fetch there - and nothing new is asked: don't give it him again.
        Directive fetch = new Directive.Fetch(
                Resource.WOOD, 8, new Position(start.x() + 10, start.y(), start.z()));
        if (variant == Variant.TIMEDOUT) {
            leader.receivePlans(Map.of(), Map.of("Bob", "his last order, " + fetch.describe()
                    + ", timed out 20 s ago: no progress in 120 s, 8 left"));
        }
        leader.receiveObjective(new Objective(mission, new Position(objective.x(), objective.y(), objective.z()), "secure the objective"));
        long t0 = System.nanoTime();
        SquadOrder order;
        try {
            order = leader.decide(squad, terrain).orElseThrow();
        } catch (RuntimeException e) {
            if (Boolean.getBoolean("eval.trace")) {
                e.printStackTrace();
            }
            return new Outcome(scenario, variant, (System.nanoTime() - t0) / 1_000_000, "-", List.of("invalid order: " + e.getMessage()));
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
        // What the order changes: one restating a plan as it stands changes nothing, and the runner makes nothing of it.
        Map<String, Plan> before = new HashMap<>();
        squad.forEach(s -> before.put(s.name(), variant == Variant.KEPT && s.name().equals("Carol")
                ? carolMining : Plan.RIFLEMAN));
        Map<String, Plan> after = new HashMap<>(before);
        order.changes().forEach(c -> after.computeIfPresent(c.soldier(), (n, p) -> p.with(c)));
        List<Plan.Change> changes = order.changes().stream()
                .filter(c -> !after.get(c.soldier()).equals(before.get(c.soldier()))).toList();
        switch (variant) {
            case MINER -> {
                List<Plan.Change> miners = changes.stream()
                        .filter(c -> c.role().filter(r -> r == Role.MINER).isPresent()).toList();
                // A miner may be sent to its site first: it goes on to mine once there.
                boolean onlyTheMiner = changes.stream().allMatch(c -> c.soldier().equals(miners.isEmpty() ? "" : miners.getFirst().soldier())
                        && (c.role().isPresent() || c.directives().filter(d -> d.stream().allMatch(x -> x instanceof
                        Directive.GoTo(Position at) && Math.hypot(at.x() - mine.x(), at.z() - mine.z()) <= 2)).isPresent()));
                if (miners.size() != 1 || miners.getFirst().site().filter(s -> Math.hypot(s.x() - mine.x(), s.z() - mine.z()) <= 2
                        && Math.abs(s.y() - mine.y()) <= 2).isEmpty() || !onlyTheMiner) {
                    failures.add("not one MINER at " + mine + ": " + changes);
                }
            }
            case POST -> {
                boolean posted = changes.stream().anyMatch(c -> c.soldier().equals("Bob") && c.directives().filter(d -> d.size() == 1
                        && d.getFirst() instanceof Directive.GoTo(Position at)
                        && Math.hypot(at.x() - post.x(), at.z() - post.z()) <= 2).isPresent());
                if (!posted || changes.stream().anyMatch(c -> !c.soldier().equals("Bob"))) {
                    failures.add("Bob not posted alone at " + post + ": " + changes);
                }
            }
            case KEPT -> {
                if (changes.stream().anyMatch(c -> !c.soldier().equals("Carol") || !carolMining.with(c).equals(carolMining))) {
                    failures.add("the standing miner's role changed, unasked: " + changes);
                }
            }
            case TIMEDOUT -> {
                if (changes.stream().anyMatch(c -> c.directives().filter(d -> d.contains(fetch)).isPresent())) {
                    failures.add("the timed-out fetch given again: " + changes);
                }
            }
            default -> {
                if (!changes.isEmpty()) {
                    failures.add("unasked plan changes: " + changes);
                }
            }
        }
        Goal goal = new Goal(objective.x() + 0.5, objective.y(), objective.z() + 0.5, 1.5, 1);
        if (leader.report() instanceof Report.Cantco(String reason)
                && Pathfinder.find(world, start, goal, ORACLE_BUDGET).reachesGoal()) {
            failures.add("false CANTCO: " + reason);
        }
        return new Outcome(scenario, variant, millis, order.risk().name()
                + order.soldierRisk().entrySet().stream().map(e -> " " + e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining()), failures);
    }

    private static SoldierStatus soldierNear(String name, SyntheticWorld world, int x, int z, Report report) {
        return new SoldierStatus(name, new Position(x + 0.5, world.ground(x, z) + 1, z + 0.5), new Order.Idle(), report, HEALTHY);
    }

    private static double horizontal(double x, double z, BlockPos objective) {
        return Math.hypot(x - (objective.x() + 0.5), z - (objective.z() + 0.5));
    }
}
