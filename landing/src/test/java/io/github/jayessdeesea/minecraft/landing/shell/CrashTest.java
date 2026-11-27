package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A crash anywhere is covered by step 2 (design section 3): the integrator dies just after each numbered step - 1
 * fetch, 2 the decision, 3 the stale check, 4 merge-tree and commit-tree, 5 the Attempt journaled, 6 the verify, 7
 * the Verified journaled, 8 the push, 9 the fetch and classifying, 10 the ref deleted - and a fresh run lands the entry
 * once, its waiter exits 0, and the audit is clean.
 */
@Tag("slow")
class CrashTest {

    /** The integrator killed: nothing after it runs, not even a catch. */
    static final class Crashed extends Error {
        Crashed(int step) {
            super("crashed after step " + step);
        }
    }

    @TempDir
    Path dir;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void aCrashAfterEachStepIsReconciledByTheNextRun(int at) {
        ScratchRepo s = new ScratchRepo(dir, at);
        s.write(s.a.resolve("x"), "1\nC\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        s.steps = new Steps() {
            @Override
            public void reached(int step) {
                if (step == at) {
                    throw new Crashed(step);
                }
            }
        };
        assertThrows(Crashed.class, s::integrate);
        s.steps = new Steps() {
        };
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertEquals(List.of("A edits x", "init"), s.subjects());
        assertEquals(Optional.of("1\nC\n3\n"), s.onMain("x"));
        assertEquals(List.of(), s.requestRefs());
        assertEquals(0, s.audit(), s.printed());
        assertFalse(s.printed().contains("[Fault]"), s.printed());
    }
}
