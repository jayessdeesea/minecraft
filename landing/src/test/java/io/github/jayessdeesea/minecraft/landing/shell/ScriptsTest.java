package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The C3 scripts' decisions, run as sh runs them against a fake {@code java} - never the installed tool, Task Scheduler,
 * C:/mc-state or a real hooks directory: the pre-push hook's source, land.sh's exits, install-landing.sh's dry run.
 */
class ScriptsTest {

    private static final Path OPS = Path.of("..", "tools", "ops").toAbsolutePath().normalize();
    private static final String MAIN = "refs/heads/main";
    private static final String LOCAL = String.format("%040x", 7);
    private static final String REMOTE = String.format("%040x", 6);

    @TempDir
    Path dir;

    private record Ran(int exit, String out) {
    }

    private Ran sh(Path script, Map<String, String> env, String stdin, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("sh", script.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().putAll(env);
        Process p = builder.start();
        try (OutputStream in = p.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        return new Ran(p.exitValue(), out);
    }

    /** A fake java: may-push answers MAY_PUSH, land answers 3, no arguments answers usage (PRE, default 2). */
    private Path fakeJava() throws IOException {
        Path fake = dir.resolve("java");
        Files.writeString(dir.resolve("javaw"), "#!/bin/sh\nexit 0\n");
        Files.writeString(fake, "#!/bin/sh\necho \"${LANDING_CONFIG-unset}\" > '" + ScratchRepo.slash(dir.resolve("config-env"))
                + "'\necho \"$*\" >> '" + ScratchRepo.slash(dir.resolve("calls")) + "'\n"
                + "case \"$3\" in\n  may-push) exit \"${MAY_PUSH:-1}\";;\n  land) exit 3;;\n  '') exit \"${PRE:-2}\";;\nesac\nexit 9\n");
        return fake;
    }

    private List<String> calls() throws IOException {
        Path calls = dir.resolve("calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    /** The hook refuses an update of main unless may-push says 0, lets every other ref by, and is closed with no jar. */
    @ParameterizedTest
    @CsvSource({
            // the remote ref pushed, may-push's answer, the jar there -> the hook's exit, may-push asked
            "refs/heads/main, 0, true, 0, true",
            "refs/heads/main, 1, true, 1, true",
            "refs/heads/main, 6, true, 1, true",
            "refs/heads/feature, 1, true, 0, false",
            "refs/queue/20261126T120000Z-0000abcd, 1, true, 0, false",
            "refs/heads/main, 0, false, 1, false",
            "refs/heads/feature, 0, false, 0, false"})
    void thePrePushHookGuardsMainOnly(String ref, String mayPush, boolean jar, int exit, boolean asked)
            throws IOException, InterruptedException {
        Path jarFile = dir.resolve("landing.jar");
        if (jar) {
            Files.writeString(jarFile, "a jar");
        }
        Ran ran = sh(OPS.resolve("landing-pre-push"), Map.of("LANDING_JAR", jarFile.toString(), "LANDING_JAVA",
                fakeJava().toString(), "MAY_PUSH", mayPush), "refs/heads/x " + LOCAL + " " + ref + " " + REMOTE + "\n", "origin",
                "url");
        assertEquals(exit, ran.exit(), ran.out());
        assertEquals(asked ? List.of("-jar " + jarFile + " may-push " + LOCAL + " " + REMOTE) : List.of(), calls());
        if (exit != 0) {
            assertTrue(ran.out().contains("sh C:/mc-state/landing/land.sh <message file>"), ran.out());
        }
    }

    /** Several refs in one push: one of them main and refused refuses the push. */
    @Test
    void aPushWithMainAmongOtherRefsIsGuarded() throws IOException, InterruptedException {
        Path jarFile = dir.resolve("landing.jar");
        Files.writeString(jarFile, "a jar");
        String lines = "refs/heads/x " + LOCAL + " refs/heads/feature " + REMOTE + "\nrefs/heads/y " + LOCAL + " " + MAIN + " "
                + REMOTE + "\n";
        assertEquals(1, sh(OPS.resolve("landing-pre-push"), Map.of("LANDING_JAR", jarFile.toString(), "LANDING_JAVA",
                fakeJava().toString(), "MAY_PUSH", "1"), lines, "origin", "url").exit());
    }

    /** land.sh: no jar, or a java that does not start, is 6 - never 1; started, the tool's own exit and arguments. */
    @ParameterizedTest
    @CsvSource({
            // the jar there, the tool's no-argument answer -> land.sh's exit
            "false, 2, 6",
            "true, 1, 6",
            "true, 6, 6",
            "true, 2, 3"})
    void landShExitsAsTheTableSays(boolean jar, String started, int exit) throws IOException, InterruptedException {
        Path jarFile = dir.resolve("landing.jar");
        if (jar) {
            Files.writeString(jarFile, "a jar");
        }
        Ran ran = sh(OPS.resolve("land.sh"), Map.of("LANDING_JAR", jarFile.toString(), "LANDING_JAVA", fakeJava().toString(),
                "PRE", started), "", "message.txt", "--timeout", "1");
        assertEquals(exit, ran.exit(), ran.out());
        if (exit == 3) {
            // the tool finds its config beside its jar (amendment 3, R10): land.sh names neither
            assertEquals("-jar " + jarFile + " land message.txt --timeout 1", calls().getLast());
            assertEquals("unset", Files.readString(dir.resolve("config-env")).strip());
        }
    }

    /** A fake schtasks: /query answers TASK_XML if set (the task there), else none; anything else is noted, never run. */
    private Path fakeSchtasks() throws IOException {
        Path fake = dir.resolve("schtasks");
        Files.writeString(fake, "#!/bin/sh\necho \"schtasks $*\" >> '" + ScratchRepo.slash(dir.resolve("calls")) + "'\n"
                + "if [ \"$1\" = /query ]; then [ -n \"$TASK_XML\" ] && { printf '%s\\n' \"$TASK_XML\"; exit 0; }; exit 1; fi\nexit 0\n");
        return fake;
    }

    private Map<String, String> dryRun(Path state, String taskXml) throws IOException {
        Map<String, String> env = new java.util.HashMap<>(Map.of("LANDING_STATE", ScratchRepo.slash(state), "LANDING_WORKTREE",
                ScratchRepo.slash(dir.resolve("landing-worktree")), "LANDING_REF", "HEAD", "LANDING_TASK", "mc-landing-dry-run",
                "LANDING_JAVA", fakeJava().toString(), "LANDING_SCHTASKS", fakeSchtasks().toString()));
        if (taskXml != null) {
            env.put("TASK_XML", taskXml);
        }
        return env;
    }

    /**
     * install-landing.sh --dry-run says every step and makes nothing - no state, worktree, build dir or temp dir - and of
     * Task Scheduler only asks; the build dir it would use is named. Run from this linked worktree, its repo is the main
     * checkout: the common git dir's parent. The task is javaw on the jar, nothing between (amendment 3, R11); the four
     * questions are said, and the exit 1 if any is not yes (R13).
     */
    @Test
    void theInstallsDryRunSaysEveryStepAndMakesNothing() throws IOException, InterruptedException {
        Path state = dir.resolve("state");
        Ran ran = sh(OPS.resolve("install-landing.sh"), dryRun(state, null), "", "--dry-run");
        assertEquals(0, ran.exit(), ran.out());
        String common = new String(new ProcessBuilder("git", "rev-parse", "--path-format=absolute", "--git-common-dir")
                .directory(OPS.toFile()).start().getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        String mainCheckout = ScratchRepo.slash(Path.of(common).getParent());
        for (String said : List.of("in <a new temp dir, made at install>", "would: git worktree add -q --detach",
                "mvn' -o -q -pl landing package -DskipTests", "would write " + ScratchRepo.slash(state) + "/config",
                "  repo=" + mainCheckout + "\n", "  poke=schtasks /run /tn mc-landing-dry-run", " install ",
                ScratchRepo.slash(state) + "/landing.jar", "/land.sh", "javaw", "-jar ", " integrate", "time limit PT210M",
                "/create /tn mc-landing-dry-run", "/f", "disabled",
                "whoami /groups", "job-check", "schtasks /run without elevation", "enters its job",
                "git, sh, mvn and java", "exit 1 if any is not yes", "audit")) {
            assertTrue(ran.out().contains(said), "said " + said + ":\n" + ran.out());
        }
        assertFalse(ran.out().contains("conhost"), ran.out());
        assertFalse(ran.out().contains("landing-task.cmd"), ran.out());
        assertFalse(Files.exists(state), "no state written");
        assertFalse(Files.exists(dir.resolve("landing-worktree")), "no worktree made");
        assertEquals(List.of("schtasks /query /tn mc-landing-dry-run /xml"), calls(), "Task Scheduler only asked");
    }

    /**
     * Every install registers the task again from the template (amendment 3, R12; this row flipped from "never runs, no
     * /create"): /create /f, enabled if it was there and enabled, else disabled - never /run.
     */
    @ParameterizedTest
    @CsvSource({
            "there-enabled, enabled",
            "there-disabled, disabled",
            "absent, disabled"})
    void everyInstallRegistersTheTaskAgainItsEnabledStateKept(String was, String kept) throws IOException, InterruptedException {
        String xml = switch (was) {
            case "there-enabled" -> "<Task><Settings></Settings></Task>";
            case "there-disabled" -> "<Task><Settings><Enabled>false</Enabled></Settings></Task>";
            default -> null;
        };
        Ran ran = sh(OPS.resolve("install-landing.sh"), dryRun(dir.resolve("state"), xml), "", "--dry-run");
        assertEquals(0, ran.exit(), ran.out());
        assertTrue(ran.out().contains("would: register mc-landing-dry-run again, " + kept), ran.out());
        assertTrue(ran.out().contains("/create /tn mc-landing-dry-run /xml") && ran.out().contains(" /f"), ran.out());
        assertFalse(ran.out().contains("would: run mc-landing-dry-run"), "the task itself never /run: " + ran.out());
    }

    /** The template's Settings in the order this machine's exports write them (amendment 3, R12). */
    @Test
    void theTemplatesSettingsAreInTheExportsOrder() throws Exception {
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(OPS.resolve("mc-landing.xml").toFile());
        var settings = doc.getElementsByTagName("Settings").item(0).getChildNodes();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < settings.getLength(); i++) {
            if (settings.item(i) instanceof org.w3c.dom.Element e) {
                names.add(e.getTagName());
            }
        }
        assertEquals(List.of("DisallowStartIfOnBatteries", "StopIfGoingOnBatteries", "Enabled", "ExecutionTimeLimit",
                "MultipleInstancesPolicy", "StartWhenAvailable"), names);
    }

    /** A program the task will need and this machine has not - here mvn - is exit 1 naming it, before anything is made. */
    @Test
    void aMissingProgramIsRefusedBeforeAnythingIsMade() throws IOException, InterruptedException {
        Map<String, String> env = new java.util.HashMap<>(dryRun(dir.resolve("state"), null));
        env.put("LANDING_MVN", "no-such-mvn");
        Ran ran = sh(OPS.resolve("install-landing.sh"), env, "", "--dry-run");
        assertEquals(1, ran.exit(), ran.out());
        assertTrue(ran.out().contains("no-such-mvn"), ran.out());
        assertEquals(List.of(), calls(), "no call made");
    }

    /** LANDING_BUILD holds the build worktree only: the filled XML goes to a temp dir of its own (amendment 3, R12). */
    @Test
    void theFilledXmlGoesToATempDirOfItsOwn() throws IOException, InterruptedException {
        Path build = dir.resolve("build");
        Map<String, String> env = new java.util.HashMap<>(dryRun(dir.resolve("state"), null));
        env.put("LANDING_BUILD", ScratchRepo.slash(build));
        Ran ran = sh(OPS.resolve("install-landing.sh"), env, "", "--dry-run");
        assertEquals(0, ran.exit(), ran.out());
        assertTrue(ran.out().contains("<a temp dir of its own>/mc-landing.xml"), ran.out());
        assertFalse(ran.out().contains(ScratchRepo.slash(build) + "/mc-landing.xml"), ran.out());
    }

    /** A build dir given must be new or empty - a jar left there is never installed - and an unknown argument is usage. */
    @Test
    void theInstallRefusesAFullBuildDirAndUnknownArguments() throws IOException, InterruptedException {
        Path build = dir.resolve("build");
        Files.createDirectories(build.resolve("src/landing/target"));
        Files.writeString(build.resolve("src/landing/target/landing-0-app.jar"), "a stale jar");
        Map<String, String> env = new java.util.HashMap<>(dryRun(dir.resolve("state"), null));
        env.put("LANDING_BUILD", ScratchRepo.slash(build));
        Ran full = sh(OPS.resolve("install-landing.sh"), env, "");
        assertEquals(1, full.exit(), full.out());
        assertTrue(full.out().contains("is not empty: refused"), full.out());
        assertEquals(List.of(), calls(), "refused before anything ran");
        assertEquals(2, sh(OPS.resolve("install-landing.sh"), env, "", "--dryrun").exit());
        assertEquals(2, sh(OPS.resolve("install-landing.sh"), env, "", "--dry-run", "more").exit());
    }

    /** The hook reads a last line with no newline: a push of main given so is guarded all the same. */
    @Test
    void theHookReadsALastLineWithNoNewline() throws IOException, InterruptedException {
        Path jarFile = dir.resolve("landing.jar");
        Files.writeString(jarFile, "a jar");
        Ran ran = sh(OPS.resolve("landing-pre-push"), Map.of("LANDING_JAR", jarFile.toString(), "LANDING_JAVA",
                fakeJava().toString(), "MAY_PUSH", "1"), "refs/heads/x " + LOCAL + " " + MAIN + " " + REMOTE, "origin", "url");
        assertEquals(1, ran.exit(), ran.out());
        assertEquals(List.of("-jar " + jarFile + " may-push " + LOCAL + " " + REMOTE), calls());
    }
}
