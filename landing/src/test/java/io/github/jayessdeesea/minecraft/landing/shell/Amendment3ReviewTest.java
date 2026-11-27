package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The review of amendment 3: a child's PATH whatever its spelling, a log that never fails a run, job-check's yes. */
class Amendment3ReviewTest {

    @TempDir
    Path dir;

    private static String inheritedPath() {
        return System.getenv().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH")).map(Map.Entry::getValue)
                .findFirst().orElseThrow();
    }

    /** Setting a variable replaces every spelling of its name: one PATH, one JAVA_HOME, the others kept. */
    @Test
    void aSetReplacesEverySpellingOfItsName() {
        Map<String, String> env = new HashMap<>(Map.of("Path", "inherited", "java_home", "old", "GIT_DIR", "kept"));
        Programs.set(env, Map.of("PATH", "mine", "JAVA_HOME", "jdk"));
        assertEquals(Map.of("PATH", "mine", "JAVA_HOME", "jdk", "GIT_DIR", "kept"), env);
    }

    /**
     * N1: a child whose inherited environment spells it {@code Path}, as Windows hands it, sees tool.path first - a real
     * sh finds a program that is only there.
     */
    @Test
    void aChildWithAnInheritedPathSpelledPathSeesToolPathFirst() throws IOException, InterruptedException {
        Path sentinel = Files.createDirectories(dir.resolve("only-here"));
        Files.writeString(sentinel.resolve("landing-sentinel"), "#!/bin/sh\necho sentinel\n");
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", "command -v landing-sentinel").redirectErrorStream(true);
        String inherited = inheritedPath();
        builder.environment().keySet().removeIf(k -> k.equalsIgnoreCase("PATH"));
        builder.environment().put("Path", inherited);
        Programs.set(builder.environment(), Map.of("PATH", sentinel + java.io.File.pathSeparator + inherited));
        assertEquals(1, builder.environment().keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).count());
        Process p = builder.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), out);
        assertTrue(out.endsWith("/only-here/landing-sentinel"), out);
    }

    /** N2: a log that cannot be written is said once on stderr; what is printed still reaches out, and nothing throws. */
    @Test
    void aLogThatCannotBeWrittenIsSaidOnceAndTheRunGoesOn() throws IOException {
        Path log = Files.createDirectories(dir.resolve("landing-task.log"));
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        PrintStream tee = TaskLog.tee(new PrintStream(out, true, StandardCharsets.UTF_8), log,
                new PrintStream(err, true, StandardCharsets.UTF_8));
        tee.println("one");
        tee.println("two");
        assertEquals("one\ntwo\n", out.toString(StandardCharsets.UTF_8).replace("\r", ""));
        assertEquals(1, err.toString(StandardCharsets.UTF_8).lines().count(), err.toString(StandardCharsets.UTF_8));
        TaskLog.rotate(log, new PrintStream(err, true, StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(log));
    }

    /** N2: with the log's path a directory, job-check still answers - its four lines, 0 - as the exit table says. */
    @Test
    void jobCheckAnswersWhenItsLogCannotBeWritten() throws IOException, InterruptedException {
        Files.createDirectories(dir.resolve("landing-task.log"));
        Files.writeString(dir.resolve("config"), "repo=" + ScratchRepo.slash(dir) + "\ntool.path="
                + String.join(";", ShellPartsTest.toolDirs()).replace("\\", "\\\\") + "\n");
        Path said = dir.resolve("job-check.txt");
        var quiet = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        assertEquals(0, Landing.launch(new String[]{"job-check", said.toString()}, Map.of("PATH", inheritedPath()), dir, quiet,
                Optional.of(dir.resolve("landing.jar")), ProcessTree::new));
        assertEquals(4, Files.readAllLines(said).size(), Files.readString(said));
    }

    /** N4: a probe that exits 0 with a line not of its program's form is a NO. */
    @Test
    void aProbeThatExitsZeroWithTheWrongAnswerIsNo() {
        JobCheck.Answer answer = JobCheck.probe("git", List.of("sh", "-c", "echo fatal: not a git"), l -> l.startsWith("git version"),
                Map.of("PATH", inheritedPath()), new ProcessTree(), Duration.ofSeconds(30));
        assertFalse(answer.yes(), answer.line());
        assertTrue(answer.line().startsWith("git: NO - not its answer: fatal: not a git"), answer.line());
    }

    /** N4: a probe that does not answer in its time is ended through the job's children and is a NO. */
    @Test
    void aProbeThatHangsIsEndedAndNo() {
        String ping = System.getenv().getOrDefault("SystemRoot", "C:\\Windows") + "\\System32\\PING.EXE";
        long started = System.nanoTime();
        JobCheck.Answer answer = JobCheck.probe("sh", List.of(ping, "-n", "60", "127.0.0.1"), l -> true, Map.of(),
                new ProcessTree(), Duration.ofSeconds(1));
        assertFalse(answer.yes(), answer.line());
        assertTrue(answer.line().contains("no answer in 1 s"), answer.line());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(20)) < 0, "bounded by its wait");
    }
}
