package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The whole chain of command - real general, real squad leaders, real soldiers - against the dev
 * server, on a named scenario with a measurable end state (backlog/scenario-matrix.md). Needs
 * {@code docker compose up -d}: the server and the local model.
 *
 * <p>Each run sets the world up from the server console, starts the app in-process, hands the
 * general its mission as an operator order, and polls what the app knows until the scenario is
 * met or its deadline passes. The models are nondeterministic, so it reports a pass rate over
 * {@code -Dscenario.runs} runs (default 1) rather than failing on one bad run; it fails only if
 * none pass.
 *
 * <pre>
 * mvn -q install -DskipTests
 * mvn -pl app test -Dtest=ChainScenarioLiveIT -Dsurefire.failIfNoSpecifiedTests=false -Dscenario=patrol-base-before-dark
 * </pre>
 *
 * <p>The setup changes the dev world: it builds an arena far from spawn, moves the soldiers onto it,
 * replaces their inventories and sets the time of day.
 */
class ChainScenarioLiveIT {

    /** The arena: a stone floor in open air, far from anything built near spawn. */
    static final int ARENA_X = 3000;
    static final int ARENA_Z = 3000;
    static final int FLOOR_Y = 150;
    static final int ARENA_HALF = 16;

    /** How a scenario stands: met, failed for good, or not decided yet. */
    enum Verdict { PASS, FAIL, PENDING }

    /**
     * A fixed setup, a mission, and a judge.
     *
     * @param kit     console commands per soldier, {@code %s} for its name, once all have joined
     * @param time    the time of day to set before the mission is given
     * @param mission the operator's mission to the general
     * @param judge   the verdict from what the app knows now
     * @param limit   wall-clock time before the run is judged failed
     */
    record Scenario(String name, List<String> kit, long time, String mission, Function<Harness, Verdict> judge, Duration limit) {
    }

    static final Map<String, Scenario> SCENARIOS = java.util.stream.Stream.of(
            new Scenario("patrol-base-before-dark",
                    List.of("clear %s", "give %s cobblestone 16", "give %s bread 4"),
                    // About six minutes of daylight left: the hut is 40 blocks a squad.
                    6000,
                    "Each squad builds a patrol base on the stone platform around x=" + ARENA_X + " z=" + ARENA_Z
                            + " and is sealed inside it before dark. Stay on the platform.",
                    // Judged when dark comes, not the moment both are in: a squad ordered out of its
                    // base before then hasn't met the mission.
                    harness -> {
                        if (harness.chain.time().filter(TimeOfDay::dark).isEmpty()) {
                            return Verdict.PENDING;
                        }
                        return Roster.SQUADS.stream().allMatch(squad -> harness.runner.base(squad.name())
                                .filter(b -> b.allInside() && b.sealed()).isPresent()) ? Verdict.PASS : Verdict.FAIL;
                    },
                    Duration.ofMinutes(10)))
            .collect(java.util.stream.Collectors.toMap(Scenario::name, scenario -> scenario));

    /** The running app, as a judge sees it. */
    record Harness(SoldierRunner runner, ChainOfCommand chain) {
    }

    @Test
    void runScenario() throws Exception {
        Scenario scenario = Optional.ofNullable(SCENARIOS.get(System.getProperty("scenario", "patrol-base-before-dark")))
                .orElseThrow(() -> new IllegalArgumentException("no scenario '" + System.getProperty("scenario") + "'; known: "
                        + SCENARIOS.keySet()));
        int runs = Integer.getInteger("scenario.runs", 1);
        List<String> results = new ArrayList<>();
        int passed = 0;
        for (int run = 1; run <= runs; run++) {
            String result = runOnce(scenario);
            results.add("run " + run + ": " + result);
            if (result.startsWith("PASS")) {
                passed++;
            }
        }
        System.out.printf("%n%s: %d/%d passed%n  %s%n", scenario.name(), passed, runs, String.join("\n  ", results));
        if (passed == 0) {
            throw new AssertionError(scenario.name() + ": no run passed - " + results);
        }
    }

    private static String runOnce(Scenario scenario) throws Exception {
        buildArena();
        try (ConfigurableApplicationContext app = SpringApplication.run(Application.class,
                "--minecraft.general.mission=Hold your positions and wait for orders.")) {
            Harness harness = new Harness(app.getBean(SoldierRunner.class), app.getBean(ChainOfCommand.class));
            List<String> soldiers = Roster.SQUADS.stream().flatMap(squad -> squad.soldiers().stream()).toList();
            awaitUntil(Duration.ofMinutes(2), "every soldier connected", () -> harness.runner.connected().containsAll(soldiers));
            // Each squad on its own half of the platform, a few blocks apart.
            for (int i = 0; i < soldiers.size(); i++) {
                String name = soldiers.get(i);
                for (String command : scenario.kit()) {
                    console(command.formatted(name));
                }
                int squad = i / 4;
                console("tp %s %d %d %d".formatted(name, ARENA_X - 6 + squad * 12 + i % 2 * 2, FLOOR_Y + 1, ARENA_Z - 2 + i % 4 / 2 * 2));
            }
            awaitUntil(Duration.ofSeconds(30), "every soldier on the platform", () -> soldiers.stream()
                    .allMatch(name -> harness.runner.position(name).filter(ChainScenarioLiveIT::onPlatform).isPresent()));
            console("time set " + scenario.time());
            awaitUntil(Duration.ofSeconds(30), "the new time of day", () -> harness.chain.time()
                    .filter(t -> Math.abs(t.ticks() - scenario.time()) < 400).isPresent());
            harness.chain.onOperatorMission("harness", scenario.mission());
            long deadline = System.nanoTime() + scenario.limit().toNanos();
            while (System.nanoTime() < deadline) {
                Verdict verdict = scenario.judge().apply(harness);
                if (verdict != Verdict.PENDING) {
                    return verdict + " at time of day " + harness.chain.time().map(TimeOfDay::ticks).orElse(-1L) + ", " + bases(harness);
                }
                TimeUnit.SECONDS.sleep(1);
            }
            return "FAIL: out of time, " + bases(harness);
        }
    }

    private static String bases(Harness harness) {
        return Roster.SQUADS.stream().map(squad -> squad.name() + " " + harness.runner.base(squad.name())
                .map(Object::toString).orElse("no base")).toList().toString();
    }

    private static boolean onPlatform(Position p) {
        return Math.abs(p.x() - ARENA_X) <= ARENA_HALF && Math.abs(p.z() - ARENA_Z) <= ARENA_HALF && Math.abs(p.y() - (FLOOR_Y + 1)) < 2;
    }

    /** A stone floor with clear air above it, kept loaded so the commands reach it. */
    private static void buildArena() throws IOException, InterruptedException {
        int x0 = ARENA_X - ARENA_HALF;
        int x1 = ARENA_X + ARENA_HALF;
        int z0 = ARENA_Z - ARENA_HALF;
        int z1 = ARENA_Z + ARENA_HALF;
        console("forceload add %d %d %d %d".formatted(x0, z0, x1, z1));
        TimeUnit.SECONDS.sleep(5); // the chunks generate before a fill can reach them
        console("fill %d %d %d %d %d %d stone".formatted(x0, FLOOR_Y, z0, x1, FLOOR_Y, z1));
        // In slabs: a fill is capped at 32768 blocks.
        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 24; y += 8) {
            console("fill %d %d %d %d %d %d air".formatted(x0, y, z0, x1, y + 7, z1));
        }
    }

    /** One server console command (DEVELOPMENT.md § 1). */
    static void console(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "compose", "exec", "-T", "minecraft", "console", command)
                .directory(new File("..")).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("console '" + command + "' failed: " + output);
        }
    }

    private static void awaitUntil(Duration limit, String what, java.util.function.BooleanSupplier met) throws InterruptedException {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!met.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("not within " + limit + ": " + what);
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
    }
}
