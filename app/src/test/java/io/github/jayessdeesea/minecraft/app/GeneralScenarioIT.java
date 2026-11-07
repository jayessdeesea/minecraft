package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiGeneral;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * General evaluation: the real {@link SpringAiGeneral}, with the real system prompt, against a local
 * model, on fixed situations that each ask one policy question - chiefly whether it holds an order
 * that's done its job (docs/military-doctrine.md § 5). Seconds per decision, against minutes for
 * the same question asked live (`ChainScenarioLiveIT`). Reports a pass rate rather than asserting
 * one, so it's an {@code *IT}, run on demand. Needs Ollama (DEVELOPMENT.md § 1), not the server.
 *
 * <pre>mvn -pl app -am test -Dtest=GeneralScenarioIT -Dsurefire.failIfNoSpecifiedTests=false
 *     [-Deval.runs=5] [-Deval.model=qwen3:14b] [-Deval.situation=name [-Deval.prompt=true]]</pre>
 */
class GeneralScenarioIT {

    private static final String BASE_MISSION = "Each squad builds a patrol base on the stone platform around x=3000 z=3000 "
            + "and is sealed inside it before dark, and stays inside until dawn. Stay on the platform.";

    private static final Objective ALPHA_BASE = new Objective("Build a patrol base on the platform and stay inside it",
            new Position(3000, 151, 3000), "Squad safe from monsters until dawn", Optional.empty(), true);
    private static final Objective BRAVO_BASE = new Objective("Build a patrol base on the platform and stay inside it",
            new Position(3008, 151, 3000), "Squad safe from monsters until dawn", Optional.empty(), true);

    private static final Objective SECURE_CAMP = new Objective("Light the camp's ground with torches", new Position(3000, 151, 3000),
            "No monster spawns inside the camp", new Objective.Task.Secure());

    /** The camp's progress, for the situations that have a camp to build up. */
    private static final java.util.Map<String, io.github.jayessdeesea.minecraft.command.Foothold> FOOTHOLDS = java.util.Map.of(
            "foothold-airhead", new io.github.jayessdeesea.minecraft.command.Foothold(new Position(3000, 151, 3000), List.of(
                    new io.github.jayessdeesea.minecraft.command.Foothold.Standard(2, "the camp's ground lit", false,
                            "31 walkable cells within 12 blocks still dark", "SECURE"),
                    new io.github.jayessdeesea.minecraft.command.Foothold.Standard(2, "the camp walled in", false, "not begun", "FORTIFY"),
                    new io.github.jayessdeesea.minecraft.command.Foothold.Standard(3, "food", false,
                            "no ground to farm by water near the camp; 0 of 8 soldiers hungry with nothing to eat", "FARM"),
                    new io.github.jayessdeesea.minecraft.command.Foothold.Standard(3, "stone and iron", false, "no mine yet", "MINE"))));

    private static final Objective GATHER_WOOD = new Objective("Gather logs from the trees below the platform",
            new Position(3007, 130, 2990), "Logs for tools and building", Optional.of(io.github.jayessdeesea.minecraft.command.Resource.WOOD));

    /**
     * One policy question: the mission, the time, each squad's state, and what a right answer is,
     * from the squads as given and the objectives the general returned (in the same order).
     */
    private record Situation(String name, String mission, TimeOfDay time, List<SquadStatus> squads,
                             BiFunction<List<SquadStatus>, List<Objective>, List<String>> judge) {
    }

    private static final List<Situation> SITUATIONS = List.of(
            new Situation("fresh-start", BASE_MISSION, TimeOfDay.of(6000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())), GeneralScenarioIT::buildsOnThePlatform),
            // The live failure: the base ordered at the height of the trees below the platform.
            new Situation("fresh-start-trees-below", BASE_MISSION, TimeOfDay.of(6000, true),
                    List.of(withTrees(fresh("Alpha", 2996, Optional.empty())), withTrees(fresh("Bravo", 3004, Optional.empty()))),
                    GeneralScenarioIT::buildsOnThePlatform),
            // The live failure: orders given under the operator's earlier mission outlive it.
            new Situation("mission-replaced", BASE_MISSION, TimeOfDay.of(6200, true),
                    List.of(fresh("Alpha", 2996, Optional.of(GATHER_WOOD)), fresh("Bravo", 3004, Optional.of(GATHER_WOOD))),
                    GeneralScenarioIT::buildsOnThePlatform),
            // The live failure: a mission to stay put read as "keep" a standing order that isn't there.
            new Situation("stay-with-no-orders", "No fight is expected today. Both squads stay on the stone platform around "
                    + "x=3000 z=3000 and use the time to rearm.", TimeOfDay.of(1000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> given.stream().filter(o -> distance(o.location(), new Position(3000, 151, 3000)) > 16)
                            .map(o -> "sent off the platform: " + o.mission() + " at " + o.location()).toList()),
            // The live failure: night already fallen on squads out gathering, ordered to gather on.
            new Situation("gathering-at-night", BASE_MISSION, TimeOfDay.of(18000, true),
                    List.of(fresh("Alpha", 2996, Optional.of(GATHER_WOOD)), fresh("Bravo", 3004, Optional.of(GATHER_WOOD))),
                    GeneralScenarioIT::buildsOnThePlatform),
            // The live failure: the garrison's mission names SECURE among the day's work, and at night
            // the general kept a squad in the open lighting the camp until it died.
            new Situation("securing-at-night", "Make camp on the stone platform around x=3000 z=3000 and stay within 16 blocks "
                    + "of it. Each squad spends the night sealed inside a patrol base there, until dawn. By day, secure the camp's "
                    + "ground with torches and gather the wood and stone the camp needs.", TimeOfDay.of(18000, true),
                    List.of(fresh("Alpha", 2996, Optional.of(SECURE_CAMP)), fresh("Bravo", 3004, Optional.of(GATHER_WOOD))),
                    GeneralScenarioIT::buildsOnThePlatform),
            // The live gap: the trees round the camp all cut, and every squad sent for wood finding none.
            new Situation("no-trees-left", "The camp is on the stone platform around x=3000 z=3000. Gather the wood the camp "
                    + "needs; where there is none left near the camp, plant a tree farm by it.", TimeOfDay.of(2000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> given.stream().anyMatch(o -> o.treeFarm() && distance(o.location(), new Position(3000, 151, 3000)) <= 24)
                            ? List.of() : List.of("no squad set to plant a tree farm by the camp: " + given.stream()
                                    .map(o -> o.task() + " at " + o.location()).toList())),
            // The foothold's airhead not yet met: the general splits its two standards between the squads.
            new Situation("foothold-airhead", "Build the camp on the stone platform around x=3000 z=3000 into a lodgment.",
                    TimeOfDay.of(2000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> {
                        List<String> failures = new ArrayList<>();
                        if (given.stream().noneMatch(Objective::secure)) {
                            failures.add("nobody lighting the camp's ground");
                        }
                        if (given.stream().noneMatch(Objective::fortify)) {
                            failures.add("nobody walling the camp in");
                        }
                        if (!failures.isEmpty()) {
                            failures.add("given " + given.stream().map(o -> o.task().toString()).toList());
                        }
                        return failures;
                    }),
            new Situation("secure-the-camp", "The camp is on the stone platform around x=3000 z=3000. Both squads light "
                    + "its ground with torches today.", TimeOfDay.of(2000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> given.stream().filter(o -> !o.secure() || distance(o.location(), new Position(3000, 151, 3000)) > 16)
                            .map(o -> "not set to secure the camp: " + o.task() + " " + o.mission() + " at " + o.location()).toList()),
            new Situation("wall-the-camp", "The camp is on the stone platform around x=3000 z=3000, its ground already lit. "
                    + "Both squads wall the camp in today.", TimeOfDay.of(2000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> given.stream().anyMatch(o -> o.fortify() && distance(o.location(), new Position(3000, 151, 3000)) <= 16)
                            ? List.of() : List.of("no squad set to wall the camp in: " + given.stream().map(o -> o.task() + " at " + o.location()).toList())),
            new Situation("mine-for-iron", "The camp is on the stone platform around x=3000 z=3000. Alpha cuts a mine down "
                    + "at the camp to where the iron is. Bravo holds the camp.", TimeOfDay.of(2000, true),
                    List.of(fresh("Alpha", 2996, Optional.empty()), fresh("Bravo", 3004, Optional.empty())),
                    (squads, given) -> {
                        List<String> failures = new ArrayList<>();
                        Objective alpha = given.get(0);
                        if (!alpha.mine() || Math.hypot(alpha.location().x() - 3000, alpha.location().z() - 3000) > 16
                                || alpha.location().y() < -10 || alpha.location().y() > 40) {
                            failures.add("Alpha wasn't set to mine down to the iron at the camp: " + alpha.task() + " at " + alpha.location());
                        }
                        if (given.get(1).mine() || distance(given.get(1).location(), new Position(3000, 151, 3000)) > 16) {
                            failures.add("Bravo didn't hold the camp: " + given.get(1).task() + " at " + given.get(1).location());
                        }
                        return failures;
                    }),
            new Situation("walking-to-site", BASE_MISSION, TimeOfDay.of(7000, true),
                    List.of(fresh("Alpha", 2996, Optional.of(ALPHA_BASE)), fresh("Bravo", 3004, Optional.of(BRAVO_BASE))),
                    GeneralScenarioIT::buildsOnThePlatform),
            new Situation("sealed-at-dusk", BASE_MISSION, TimeOfDay.of(11800, true),
                    List.of(sealed("Alpha", ALPHA_BASE, 3000), sealed("Bravo", BRAVO_BASE, 3008)), GeneralScenarioIT::keepsEvery),
            // Bare-handed with trees in sight: re-arming is tempting, and not worth leaving shelter for at dusk.
            new Situation("sealed-unarmed-trees-near", BASE_MISSION, TimeOfDay.of(11800, true),
                    List.of(withTrees(sealed("Alpha", ALPHA_BASE, 3000)), withTrees(sealed("Bravo", BRAVO_BASE, 3008))),
                    GeneralScenarioIT::keepsEvery),
            new Situation("sealed-at-night", BASE_MISSION, TimeOfDay.of(18000, true),
                    List.of(sealed("Alpha", ALPHA_BASE, 3000), sealed("Bravo", BRAVO_BASE, 3008)), GeneralScenarioIT::keepsEvery),
            new Situation("building-at-dusk", BASE_MISSION, TimeOfDay.of(11800, true),
                    List.of(building("Alpha", ALPHA_BASE, 3000), sealed("Bravo", BRAVO_BASE, 3008)),
                    (squads, given) -> {
                        List<String> failures = new ArrayList<>();
                        if (!given.get(0).patrolBase() || distance(given.get(0).location(), ALPHA_BASE.location()) > 8) {
                            failures.add("Alpha, still building at dusk, was sent elsewhere: " + given.get(0).mission());
                        }
                        failures.addAll(keeps(squads.get(1), given.get(1)));
                        return failures;
                    }),
            new Situation("mission-changed", "Alpha leaves its patrol base and scouts east to x=3060 z=3000. Bravo stays in its "
                    + "patrol base.", TimeOfDay.of(1000, true),
                    List.of(sealed("Alpha", ALPHA_BASE, 3000), sealed("Bravo", BRAVO_BASE, 3008)),
                    (squads, given) -> {
                        List<String> failures = new ArrayList<>();
                        if (distance(given.get(0).location(), new Position(3060, 151, 3000)) > 16) {
                            failures.add("Alpha wasn't sent east: " + given.get(0).mission() + " at " + given.get(0).location());
                        }
                        failures.addAll(keeps(squads.get(1), given.get(1)));
                        return failures;
                    }));

    @Test
    void evaluate() {
        String model = System.getProperty("eval.model", "qwen3:14b");
        int runs = Integer.getInteger("eval.runs", 3);
        ChatModel ollama = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                .options(OllamaChatOptions.builder().model(model).temperature(0.3).disableThinking().build())
                .build();
        ChatClient client = ChatClient.builder(ollama).defaultSystem(new ClassPathResource("prompts/general-system.md")).build();

        int passed = 0;
        List<String> summary = new ArrayList<>();
        String only = System.getProperty("eval.situation");
        for (Situation situation : SITUATIONS) {
            if (only != null && !only.equals(situation.name())) {
                continue;
            }
            if (Boolean.getBoolean("eval.prompt")) {
                SpringAiGeneral shown = new SpringAiGeneral(client, situation.mission(), "");
                shown.receiveTime(situation.time());
                System.out.println(shown.prompt(situation.squads()));
            }
            int situationPassed = 0;
            for (int run = 1; run <= runs; run++) {
                SpringAiGeneral general = new SpringAiGeneral(client, situation.mission(), "");
                general.receiveTime(situation.time());
                Optional.ofNullable(FOOTHOLDS.get(situation.name())).ifPresent(general::receiveFoothold);
                long start = System.nanoTime();
                List<String> failures;
                try {
                    failures = situation.judge().apply(situation.squads(), general.decide(situation.squads()));
                } catch (IllegalStateException e) {
                    failures = List.of("invalid reply: " + e.getMessage());
                }
                long millis = (System.nanoTime() - start) / 1_000_000;
                System.out.printf(Locale.ROOT, "%-18s run %d %5dms  %s%n", situation.name(), run, millis,
                        failures.isEmpty() ? "PASS" : "FAIL " + String.join("; ", failures));
                if (failures.isEmpty()) {
                    situationPassed++;
                }
            }
            passed += situationPassed;
            summary.add(String.format(Locale.ROOT, "  %-18s %d/%d", situation.name(), situationPassed, runs));
        }
        System.out.printf(Locale.ROOT, "%n%s: %d/%d pass%n%s%n", model, passed, runs * SITUATIONS.size(), String.join("\n", summary));
    }

    /** Every squad, carrying enough for a hut, is set to build one on the platform - not sent to gather, nor below it. */
    private static List<String> buildsOnThePlatform(List<SquadStatus> squads, List<Objective> given) {
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < squads.size(); i++) {
            Objective objective = given.get(i);
            if (!objective.patrolBase() || distance(objective.location(), new Position(3000, 151, 3000)) > 16
                    || Math.abs(objective.location().y() - 151) > 3) {
                failures.add(squads.get(i).squadName() + " wasn't set to build on the platform: " + objective.mission() + " at "
                        + objective.location() + objective.gather().map(r -> ", gathering " + r).orElse(""));
            }
        }
        return failures;
    }

    private static List<String> keepsEvery(List<SquadStatus> squads, List<Objective> given) {
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < squads.size(); i++) {
            failures.addAll(keeps(squads.get(i), given.get(i)));
        }
        return failures;
    }

    private static List<String> keeps(SquadStatus squad, Objective given) {
        return given.equals(squad.standing().orElseThrow()) ? List.of()
                : List.of(squad.squadName() + " was pulled out of its base: " + given.mission() + " at " + given.location());
    }

    private static double distance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    /** A squad of four sealed inside the base it was ordered to build, its cobblestone spent on it. */
    private static SquadStatus sealed(String name, Objective base, int x) {
        return new SquadStatus(name, new Report.InPosition(), soldiers(name, x, new Report.InPosition(), 1),
                List.of(), Optional.of(base), true);
    }

    /** The same squad, seeing a stand of trees below the platform. */
    private static SquadStatus withTrees(SquadStatus squad) {
        return new SquadStatus(squad.squadName(), squad.report(), squad.soldiers(),
                List.of(new io.github.jayessdeesea.minecraft.command.ResourceSite(io.github.jayessdeesea.minecraft.command.Resource.WOOD,
                        new Position(3005, 130, 3012), 36)),
                squad.standing(), squad.holdingBase(), squad.baseBlocksLeft());
    }

    /** A squad of four on the platform, 16 cobblestone each, its base not laid out yet. */
    private static SquadStatus fresh(String name, int x, Optional<Objective> standing) {
        return new SquadStatus(name, new Report.Moving(), soldiers(name, x, new Report.Moving(), 16), List.of(), standing, false);
    }

    /** A squad of four inside the outline of its base, its walls half up, the blocks for the rest in hand. */
    private static SquadStatus building(String name, Objective base, int x) {
        return new SquadStatus(name, new Report.Moving(), soldiers(name, x, new Report.Fortifying(), 8),
                List.of(), Optional.of(base), false, java.util.OptionalInt.of(20));
    }

    private static List<SoldierStatus> soldiers(String squad, int x, Report report, int stone) {
        List<SoldierStatus> soldiers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Condition condition = new Condition(20, 18, 0, Optional.of(new Kit(Optional.empty(), 0, 0, 0, 0, stone)));
            soldiers.add(new SoldierStatus(squad + (i + 1), new Position(x + 0.5 + i % 2, 151, 3000.5 + i / 2), new Order.Idle(), report,
                    Optional.of(condition)));
        }
        return soldiers;
    }
}
