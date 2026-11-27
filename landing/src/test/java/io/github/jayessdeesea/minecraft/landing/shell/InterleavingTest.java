package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Submits from two worktrees, integrator runs crashed at a random step or not, and pushes from outside the queue - between
 * runs and in the middle of one, at a random step - each adopted once the run is done; interleaved at random from a
 * fixed list of seeds. Then the queue is run dry and must hold the same as after any crash: every entry settled, no
 * request ref left, main linear, the audit clean; and the run must have meant something - an entry landed, a push
 * adopted. A failure's message names its seed.
 */
@Tag("slow")
class InterleavingTest {

    private static final int OPERATIONS = 12;

    @TempDir
    Path dir;

    @ParameterizedTest
    @ValueSource(longs = {20261126L, 1L, 2L})
    void anyInterleavingSettlesEveryEntryAndAuditsClean(long seed) {
        Random chance = new Random(seed);
        ScratchRepo s = new ScratchRepo(dir, seed);
        String why = "seed " + seed + ": ";
        List<Sha> outside = new ArrayList<>();
        int[] files = {0};
        int adopted = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            // the first two fixed, so every seed submits and has a push from outside land mid-run
            int op = i == 0 ? 0 : i == 1 ? 3 : chance.nextInt(5);
            switch (op) {
                case 0, 1 -> submit(s, chance.nextBoolean() ? s.a : s.b, files, why);
                case 2 -> {
                    int crashAt = chance.nextInt(11);
                    run(s, step -> {
                        if (step == crashAt) {
                            throw new CrashTest.Crashed(step);
                        }
                    });
                }
                case 3 -> {
                    int pushAt = 1 + chance.nextInt(9);
                    List<Sha> during = new ArrayList<>();
                    run(s, step -> {
                        if (step == pushAt && during.isEmpty()) {
                            during.add(s.outsidePush("during" + outside.size()));
                        }
                    });
                    outside.addAll(during);
                }
                default -> outside.add(s.outsidePush("between" + outside.size()));
            }
            for (; adopted < outside.size(); adopted++) {
                assertEquals(0, s.run(s.a, Poke.NONE, "adopt", outside.get(adopted).value(), "the test's outside push"),
                        why + s.printed());
            }
            s.clock.advance(Duration.ofMinutes(1));
        }
        for (int round = 0; round < 20 && !s.requestRefs().isEmpty(); round++) {
            s.clock.advance(Duration.ofMinutes(30));
            s.integrate();
        }
        assertEquals(List.of(), s.requestRefs(), why + s.printed());
        int landed = 0;
        for (String id : s.queuedIds().stream().distinct().toList()) {
            int exit = s.run(s.a, Poke.NONE, "state", id);
            assertTrue(Set.of(0, 1, 2, 3).contains(exit), why + id + " not settled: " + exit);
            landed += exit == 0 ? 1 : 0;
        }
        assertTrue(landed >= 1, why + "nothing landed");
        assertTrue(!outside.isEmpty(), why + "nothing adopted");
        assertEquals("", s.git(s.origin).ok("rev-list", "--merges", "main"), why + "main linear");
        assertEquals(0, s.audit(), why + s.printed());
    }

    private static void submit(ScratchRepo s, Path tree, int[] files, String why) {
        String file = "f" + (files[0]++);
        s.write(tree.resolve(file), file + "\n");
        s.git(tree).ok("add", file);
        int exit = s.queue(tree, "adds " + file);
        assertTrue(Set.of(0, 2, 3, 5).contains(exit), why + "queueing " + file + " exited " + exit + s.printed());
    }

    /** One integrator run, told of each step; a crash ends it as a kill would. */
    private static void run(ScratchRepo s, java.util.function.IntConsumer atStep) {
        s.steps = new Steps() {
            @Override
            public void reached(int step) {
                atStep.accept(step);
            }
        };
        try {
            s.integrate();
        } catch (CrashTest.Crashed crashed) {
            // the run died; the next one reconciles
        } finally {
            s.steps = new Steps() {
            };
        }
    }
}
