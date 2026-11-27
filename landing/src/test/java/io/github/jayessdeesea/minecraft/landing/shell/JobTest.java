package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job object (amendment 2, R5), each row in a JVM of its own ({@link JobHolder}), since "everything in the job but
 * me" in this JVM would be a neighbour test's children. The chain is verify.sh's shape - sh, an sh script, {@code exec}
 * of a native program - which a parent chain loses and the job's own list does not.
 */
@EnabledOnOs(OS.WINDOWS)
class JobTest {

    @TempDir
    Path dir;

    /** Entered, the job holds this process alone. */
    @Test
    void enteredTheJobHoldsTheHolderAlone() throws Exception {
        Held held = hold("enter");
        assertEquals("entered", held.answer);
        assertEquals(List.of(held.self), held.members);
    }

    /** A verify past its time: ended by the job's list - every pid the job held dead, the job the holder again. */
    @Test
    void aVerifyShapedChainPastItsTimeIsEndedByTheJob() throws Exception {
        Held held = hold("verify");
        assertEquals("timedOut=true", held.answer);
        assertTrue(held.others.size() >= 2, "the chain was seen in the job: " + held.others);
        held.others.forEach(pid -> assertFalse(alive(pid), "pid " + pid + " outlived the kill"));
        assertEquals(List.of(held.self), held.members);
    }

    /** A git call past its time, the same chain as a fake git's fetch: timed out, every pid dead. */
    @Test
    void aGitCallsChainPastItsTimeIsEndedByTheJob() throws Exception {
        Held held = hold("git");
        assertEquals("timedOut=true", held.answer);
        assertTrue(held.others.size() >= 2, "the chain was seen in the job: " + held.others);
        held.others.forEach(pid -> assertFalse(alive(pid), "pid " + pid + " outlived the kill"));
    }

    /** The holder killed hard while its chain runs: the kernel closes the job and ends the chain - every pid dead in 5 s. */
    @Test
    void aHolderKilledHardTakesItsChainWithIt() throws Exception {
        Held held = hold("wait");
        assertTrue(held.others.size() >= 2, "the chain was seen in the job: " + held.others);
        held.process.destroyForcibly();
        held.process.waitFor();
        Instant until = Instant.now().plusSeconds(5);
        while (held.others.stream().anyMatch(JobTest::alive) && Instant.now().isBefore(until)) {
            Thread.sleep(100);
        }
        held.others.forEach(pid -> assertFalse(alive(pid), "pid " + pid + " outlived its job"));
    }

    private record Held(Process process, String answer, List<Long> others, List<Long> members, long self) {
    }

    private Held hold(String mode) throws IOException, InterruptedException {
        Path inner = dir.resolve("inner.sh");
        Files.writeString(inner, "exec \"${SystemRoot:-$SYSTEMROOT}/System32/PING.EXE\" -n 300 127.0.0.1 > /dev/null\n");
        Path outer = dir.resolve("outer.sh");
        Files.writeString(outer, "sh '" + ScratchRepo.slash(inner) + "'\n");
        Path out = dir.resolve("held.txt");
        String java = ProcessHandle.current().info().command().orElseThrow();
        Process process = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), JobHolder.class.getName(), mode, out.toString(), outer.toString())
                .redirectErrorStream(true).redirectOutput(dir.resolve("holder.log").toFile()).start();
        Instant until = Instant.now().plusSeconds(60);
        while (!Files.exists(out) && Instant.now().isBefore(until) && (process.isAlive() || Files.exists(out))) {
            Thread.sleep(100);
        }
        if (!mode.equals("wait")) {
            process.waitFor();
        }
        assertTrue(Files.exists(out), "the holder wrote nothing: " + Files.readString(dir.resolve("holder.log")));
        List<String> lines = Files.readAllLines(out);
        return new Held(process, lines.get(0), pids(lines.get(1)), pids(lines.get(2)), Long.parseLong(lines.get(3)));
    }

    private static List<Long> pids(String line) {
        return line.isBlank() ? List.of() : Arrays.stream(line.trim().split(" ")).map(Long::parseLong).toList();
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
