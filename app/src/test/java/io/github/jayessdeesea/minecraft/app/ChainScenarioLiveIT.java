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
import java.util.function.Supplier;

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
 * heals and feeds them, replaces their inventories and sets the time of day.
 */
class ChainScenarioLiveIT {

    /** The arena: a stone floor in open air, far from anything built near spawn. */
    static final int ARENA_X = 500;
    static final int ARENA_Z = 500;
    /**
     * The harness's own squads, not the default roster's: a garrison may be living near spawn as
     * Alpha and Bravo while a run goes on, and two connections can't share a name.
     */
    static final String ROSTER = "Charlie:Ike,Jo,Kit,Lou;Delta:Max,Ned,Ola,Pip";
    static final int FLOOR_Y = 150;
    static final int ARENA_HALF = 16;

    /** How a scenario stands: met, failed for good, or not decided yet. */
    enum Verdict { PASS, FAIL, PENDING }

    /**
     * A fixed setup, a mission, and a judge.
     *
     * @param kit     console commands per soldier, {@code %s} for its name, once all have joined
     * @param arena   console commands that furnish the arena once it's built
     * @param time    the time of day to set before the mission is given
     * @param mission the operator's mission to the general
     * @param judge   a fresh judge for each run, giving the verdict from what the app knows now
     * @param limit   wall-clock time before the run is judged failed
     */
    record Scenario(String name, List<String> kit, List<String> arena, long time, String mission,
                    Supplier<Function<Harness, Verdict>> judge, Duration limit) {
    }

    /** The kit both patrol-base scenarios start with: enough cobblestone between four for a hut. */
    private static final List<String> BASE_KIT = List.of("clear %s", "give %s cobblestone 16", "give %s bread 4");

    /** How long a base may stand open or part-empty at night before the squad is judged to have left it. */
    static final Duration NIGHT_GRACE = Duration.ofSeconds(30);

    private static boolean everySquadSealedIn(Harness harness) {
        return harness.roster().squads().stream().allMatch(squad -> harness.runner.base(squad.name())
                .filter(b -> b.allInside() && b.sealed()).isPresent());
    }

    static final Map<String, Scenario> SCENARIOS = java.util.stream.Stream.of(
            new Scenario("patrol-base-before-dark", BASE_KIT, List.of(),
                    // About six minutes of daylight left: the hut is 40 blocks a squad.
                    6000,
                    "Each squad builds a patrol base on the stone platform around x=" + ARENA_X + " z=" + ARENA_Z
                            + " and is sealed inside it before dark. Stay on the platform.",
                    // Judged when dark comes, not the moment both are in: a squad ordered out of its
                    // base before then hasn't met the mission.
                    () -> harness -> harness.chain.time().filter(TimeOfDay::dark).isEmpty() ? Verdict.PENDING
                            : everySquadSealedIn(harness) ? Verdict.PASS : Verdict.FAIL,
                    Duration.ofMinutes(10)),
            new Scenario("hold-base-through-the-night", BASE_KIT, List.of(), 6000,
                    "Each squad builds a patrol base on the stone platform around x=" + ARENA_X + " z=" + ARENA_Z
                            + ", is sealed inside it before dark, and stays inside until dawn. Stay on the platform.",
                    // Watched all night, judged at first light. A base can read open for a moment
                    // while a soldier shifts inside it, so only an absence longer than the grace fails.
                    () -> {
                        boolean[] nightFell = {false};
                        long[] openSince = {0};
                        return harness -> {
                            boolean dark = harness.chain.time().filter(TimeOfDay::dark).isPresent();
                            if (!dark) {
                                return nightFell[0] ? Verdict.PASS : Verdict.PENDING;
                            }
                            nightFell[0] = true;
                            if (everySquadSealedIn(harness)) {
                                openSince[0] = 0;
                            } else if (openSince[0] == 0) {
                                openSince[0] = System.nanoTime();
                            } else if (System.nanoTime() - openSince[0] > NIGHT_GRACE.toNanos()) {
                                return Verdict.FAIL;
                            }
                            return Verdict.PENDING;
                        };
                    },
                    Duration.ofMinutes(20)),
            // The equipment ladder (docs/military-doctrine.md § 11): cobblestone and logs in hand,
            // a row of iron ore on the platform, and a quiet morning to rearm in. Met when every
            // soldier has climbed to a stone sword and a shield in its off hand - the shield's ingot
            // came from ore only a stone pickaxe mines.
            new Scenario("rearm-to-iron", List.of("clear %s", "give %s cobblestone 16", "give %s oak_log 6", "give %s bread 4"),
                    List.of("fill %d %d %d %d %d %d iron_ore".formatted(ARENA_X - 8, FLOOR_Y + 1, ARENA_Z + 10, ARENA_X + 7, FLOOR_Y + 1,
                            ARENA_Z + 10)),
                    // Dawn: ten minutes of daylight, so the run is judged before monsters come out.
                    0,
                    "No fight is expected today. Both squads stay on the stone platform around x=" + ARENA_X + " z=" + ARENA_Z
                            + " and use the time to rearm: improve weapons and armour from what they carry and the iron ore there.",
                    () -> harness -> harness.roster().squads().stream().flatMap(squad -> squad.soldiers().stream())
                            .allMatch(name -> harness.runner.kit(name).filter(k -> k.shield() && k.weapon().filter(w -> w.startsWith("stone_")
                                    || w.startsWith("iron_") || w.startsWith("diamond_")).isPresent()).isPresent())
                            ? Verdict.PASS : Verdict.PENDING,
                    Duration.ofMinutes(9)),
            // Further up the ladder: enough ore for a shield and a chestplate each, and the logs to
            // burn for nine smelts. Met when every soldier wears armour and has its shield.
            new Scenario("rearm-to-chestplate", List.of("clear %s", "give %s cobblestone 16", "give %s oak_log 10", "give %s bread 4"),
                    java.util.stream.IntStream.of(10, 12, 14).mapToObj(dz -> "fill %d %d %d %d %d %d iron_ore".formatted(
                            ARENA_X - ARENA_HALF, FLOOR_Y + 1, ARENA_Z + dz, ARENA_X + ARENA_HALF, FLOOR_Y + 1, ARENA_Z + dz)).toList(),
                    0,
                    "No fight is expected today. Both squads stay on the stone platform around x=" + ARENA_X + " z=" + ARENA_Z
                            + " and use the time to rearm: improve weapons and armour from what they carry and the iron ore there.",
                    () -> harness -> harness.roster().squads().stream().flatMap(squad -> squad.soldiers().stream())
                            .allMatch(name -> harness.runner.kit(name).filter(k -> k.shield() && k.armour() > 0).isPresent())
                            ? Verdict.PASS : Verdict.PENDING,
                    Duration.ofMinutes(9)),
            // Mobility (backlog/mobility.md): a 3-high terrace east of the squads, too high to jump and
            // with no blocks to pillar with. Soil is cut into by hand; stone only with a pickaxe.
            terrace("climb-soil-terrace", "dirt", List.of("clear %s", "give %s bread 4")),
            terrace("climb-stone-terrace", "stone", List.of("clear %s", "give %s wooden_pickaxe", "give %s bread 4")),
            // The shield (docs/military-doctrine.md § 8): armed and shielded, against skeletons that
            // daylight doesn't burn. Met when every skeleton is dead; how often the shields went up
            // shows in the log ("raises its shield").
            new Scenario("shield-against-skeletons", List.of("clear %s", "give %s stone_sword", "item replace entity %s weapon.offhand with shield",
                    "give %s bread 4"),
                    // A helmet keeps daylight off a skeleton.
                    List.of("summon skeleton %d %d %d".formatted(ARENA_X + 12, FLOOR_Y + 1, ARENA_Z + 12),
                            "summon skeleton %d %d %d".formatted(ARENA_X - 12, FLOOR_Y + 1, ARENA_Z + 12),
                            "item replace entity @e[type=skeleton,x=%d,y=%d,z=%d,distance=..30] armor.head with leather_helmet"
                                    .formatted(ARENA_X, FLOOR_Y + 1, ARENA_Z)),
                    1000,
                    "Both squads hold the stone platform around x=" + ARENA_X + " z=" + ARENA_Z + " and clear it of hostiles.",
                    () -> {
                        boolean[] seen = {false};
                        return harness -> {
                            int left = harness.runner.hostilesNear(new Position(ARENA_X, FLOOR_Y + 1, ARENA_Z), ARENA_HALF + 8);
                            seen[0] |= left > 0;
                            return seen[0] && left == 0 ? Verdict.PASS : Verdict.PENDING;
                        };
                    },
                    Duration.ofMinutes(4)))
            .collect(java.util.stream.Collectors.toMap(Scenario::name, scenario -> scenario));

    /** The terrace's west face, and its top: three blocks over the floor. */
    static final int TERRACE_X = ARENA_X + 10;
    static final int TERRACE_TOP = FLOOR_Y + 4;

    /** A scenario of climbing onto a terrace of {@code block}, three high, with {@code kit}. */
    private static Scenario terrace(String name, String block, List<String> kit) {
        return new Scenario(name, kit,
                List.of("fill %d %d %d %d %d %d %s".formatted(TERRACE_X, FLOOR_Y + 1, ARENA_Z - ARENA_HALF, ARENA_X + ARENA_HALF,
                        TERRACE_TOP - 1, ARENA_Z + ARENA_HALF, block)),
                1000,
                "Both squads climb onto the top of the terrace east of them, at x=" + (TERRACE_X + 3) + " y=" + TERRACE_TOP + " z="
                        + ARENA_Z + ", and hold there. Stay on the platform.",
                // Each squad's point man on the terrace, or in a step cut into its face, and every
                // soldier in its slot: a wedge on a terrace this narrow leaves its rear slots on the floor.
                () -> harness -> harness.roster().squads().stream().allMatch(squad -> harness.runner.position(squad.soldiers().getFirst())
                        .filter(p -> p.x() >= TERRACE_X && p.y() >= TERRACE_TOP - 1.5).isPresent())
                        && harness.roster().squads().stream().flatMap(squad -> squad.soldiers().stream())
                        .allMatch(soldier -> harness.runner.report(soldier).filter(r -> r instanceof io.github.jayessdeesea.minecraft.command.Report.InPosition).isPresent())
                        ? Verdict.PASS : Verdict.PENDING,
                Duration.ofMinutes(5));
    }

    /** The running app, as a judge sees it. */
    record Harness(SoldierRunner runner, ChainOfCommand chain, Roster roster) {
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
        // Held for the run: an app yielding to experiments - a garrison - stands still meanwhile.
        try (ExperimentLock lock = ExperimentLock.hold();
             ConfigurableApplicationContext app = SpringApplication.run(Application.class, "--minecraft.roster=" + ROSTER,
                "--minecraft.general.mission=Hold your positions and wait for orders. Build nothing and gather nothing until then.")) {
            Harness harness = new Harness(app.getBean(SoldierRunner.class), app.getBean(ChainOfCommand.class), app.getBean(Roster.class));
            List<String> soldiers = harness.roster().squads().stream().flatMap(squad -> squad.soldiers().stream()).toList();
            awaitUntil(Duration.ofMinutes(2), "every soldier connected", () -> harness.runner.connected().containsAll(soldiers));
            // Everyone to its start first, clear of anything the scenario builds - a soldier still
            // standing where the last run left it, or walking there, is filled in and suffocates -
            // then the arena furnished, then everyone put back on its start in case one moved.
            for (int i = 0; i < soldiers.size(); i++) {
                String name = soldiers.get(i);
                console("clear " + name);
                // Whole and fed: health and hunger carry over from one run to the next.
                console("effect give " + name + " minecraft:instant_health 1 10");
                console("effect give " + name + " minecraft:saturation 1 20");
                console(start(name, i));
            }
            for (String command : scenario.arena()) {
                console(command);
            }
            for (int i = 0; i < soldiers.size(); i++) {
                console(start(soldiers.get(i), i));
            }
            awaitUntil(Duration.ofSeconds(30), "every soldier on the platform", () -> soldiers.stream()
                    .allMatch(name -> harness.runner.position(name).filter(ChainScenarioLiveIT::onPlatform).isPresent()));
            console("time set " + scenario.time());
            awaitUntil(Duration.ofSeconds(30), "the new time of day", () -> harness.chain.time()
                    .filter(t -> Math.abs(t.ticks() - scenario.time()) < 400).isPresent());
            // The kit goes out just before the mission: early enough that the general's first look
            // at the mission sees it, late enough that orders under the harness's holding mission
            // don't spend it.
            for (String name : soldiers) {
                for (String command : scenario.kit()) {
                    console(command.formatted(name));
                }
            }
            harness.chain.onOperatorMission("harness", scenario.mission());
            Function<Harness, Verdict> judge = scenario.judge().get();
            long deadline = System.nanoTime() + scenario.limit().toNanos();
            while (System.nanoTime() < deadline) {
                Verdict verdict = judge.apply(harness);
                if (verdict != Verdict.PENDING) {
                    return verdict + " at time of day " + harness.chain.time().map(TimeOfDay::ticks).orElse(-1L) + ", " + bases(harness) + ", " + kits(harness);
                }
                TimeUnit.SECONDS.sleep(1);
            }
            return "FAIL: out of time, " + bases(harness) + ", " + kits(harness);
        }
    }

    private static String bases(Harness harness) {
        return harness.roster().squads().stream().map(squad -> squad.name() + " " + harness.runner.base(squad.name())
                .map(Object::toString).orElse("no base")).toList().toString();
    }

    /** Each squad on its own half of the platform, a few blocks apart: the teleport to {@code i}'s start. */
    private static String start(String name, int i) {
        int squad = i / 4;
        return "tp %s %d %d %d".formatted(name, ARENA_X - 6 + squad * 12 + i % 2 * 2, FLOOR_Y + 1, ARENA_Z - 2 + i % 4 / 2 * 2);
    }

    private static String kits(Harness harness) {
        return harness.roster().squads().stream().flatMap(squad -> squad.soldiers().stream())
                .map(name -> name + " " + harness.runner.kit(name).map(k -> k.weapon().orElse("bare") + (k.shield() ? "+shield" : "")
                        + (k.armour() > 0 ? "+" + k.armour() + " armour" : ""))
                        .orElse("?")).toList().toString();
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

    /**
     * In the server container: note where the log ends, send the command line read from stdin to the
     * console, and print the server's reply - its "System chat" lines after that point - once one
     * has come, or nothing after five seconds. No double quotes: Windows' process launcher mangles
     * them in an argument, which is also why the command itself goes on stdin.
     */
    private static final String SEND_AND_READ_REPLY = String.join("; ",
            "n=$(wc -l < logs/latest.log)",
            "head -n 1 > /tmp/console",
            "i=0",
            "while [ $i -lt 50 ] && ! tail -n +$((n + 1)) logs/latest.log | grep -q 'System chat:'; do sleep 0.1; i=$((i + 1)); done",
            "sleep 0.3",
            "tail -n +$((n + 1)) logs/latest.log | grep 'System chat:' || true");

    /**
     * What in a reply means the command didn't do its job: a line the server couldn't parse (it
     * marks where with {@code <--[HERE]}, an unknown command included), a selector or name that
     * matched nobody, a registry entry that isn't there. Other failures - "No blocks were filled"
     * for air that was already air, "No items were found" clearing an empty inventory - leave the
     * world as the setup wants it, and pass.
     */
    private static final List<String> FAILED = List.of("<--[HERE]", "No entity was found", "No player was found", "Can't find element");

    /**
     * One server console command (DEVELOPMENT.md § 1), and the server's reply - failing loudly if
     * the server says it failed, or doesn't answer at all.
     */
    static String console(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "compose", "exec", "-T", "minecraft", "sh", "-c", SEND_AND_READ_REPLY)
                .directory(new File("..")).redirectErrorStream(true).start();
        try (var stdin = process.getOutputStream()) {
            stdin.write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String reply = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("console '" + command + "' couldn't be sent: " + reply);
        }
        if (reply.isEmpty()) {
            throw new IllegalStateException("console '" + command + "': no reply from the server");
        }
        if (FAILED.stream().anyMatch(reply::contains)) {
            throw new IllegalStateException("console '" + command + "' failed: " + reply);
        }
        return reply;
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
