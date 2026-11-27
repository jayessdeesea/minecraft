package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main's shared ref is read in one place, {@link Gather#mainTip}, which reads an absent ref again: any {@code git fetch}
 * in any worktree rewrites it, and a read mid-rewrite finds it gone. Outside Gather the ref is only a fetch's
 * destination; in Gather its one read is a private method that only the re-reading function calls.
 */
class MainRefReadsTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final String GATHER = "Gather.java";

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void outsideGatherTheRefIsOnlyAFetchsDestination() throws IOException {
        List<String> reads = new ArrayList<>();
        for (Path file : sources()) {
            if (file.getFileName().toString().equals(GATHER)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")
                        || line.contains("+refs/heads/main:refs/remotes/")) {
                    continue;
                }
                // the ref spelt out anywhere in code, or its short name or a remote's main handed to a git call
                boolean gitCall = line.contains("resolve(") || line.contains("List.of(") || line.contains(".ok(")
                        || line.contains(".run(");
                if (line.contains("refs/remotes/") || gitCall && line.contains("origin/main")
                        || line.contains("resolve(") && line.contains("/main")) {
                    reads.add(file.getFileName() + ":" + (i + 1) + " " + line.strip());
                }
            }
        }
        assertEquals(List.of(), reads, "read main through Gather.mainTip, which reads an absent ref again");
    }

    @Test
    void inGatherTheOneReadIsPrivateAndCalledOnlyByTheRereadingFunction() throws IOException {
        String gather = Files.readString(MAIN.resolve("io/github/jayessdeesea/minecraft/landing/shell/" + GATHER));
        assertEquals(1, gather.split("\"refs/remotes/\"", -1).length - 1, "one read of the ref in Gather");
        assertTrue(gather.contains("private Optional<Sha> mainRead() {"), "the one read private");
        long calls = 0;
        for (Path file : sources()) {
            calls += Files.readString(file).split("mainRead\\(", -1).length - 1;
        }
        assertEquals(2, calls, "mainRead declared once and called once, by mainTip(String)");
    }
}
