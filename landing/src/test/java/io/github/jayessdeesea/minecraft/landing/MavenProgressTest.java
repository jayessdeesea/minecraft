package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where Maven's log says the build is, for display and the estimate only - nothing parsed decides a kill. A module is
 * its {@code Building <name> <version> [i/n]} line; a test class done is a {@code Tests run: ... -- in <class>} line of
 * its own at a line's start, failing when it had failures or errors; a module's summary {@code Tests run:} line (no
 * {@code -- in}) and a test's own output naming the words are not. Colour codes are stripped. The lines are the live
 * log's shapes (C:/mc-state/landing/verify.log).
 */
class MavenProgressTest {

    private static final String PARENT = "[INFO] Building minecraft-parent 0.1.0-SNAPSHOT                           [1/7]";
    private static final String PROTOCOL = "[INFO] Building protocol 0.1.0-SNAPSHOT                                   [2/7]";
    private static final String APP = "[INFO] Building app 0.1.0-SNAPSHOT                                        [7/7]";
    private static final String FRAME = "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in "
            + "io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoderTest";
    private static final String NBT = "[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in "
            + "io.github.jayessdeesea.minecraft.protocol.packet.NbtTest";
    private static final String SUMMARY = "[INFO] Tests run: 97, Failures: 0, Errors: 0, Skipped: 0";
    private static final String FAILING = "[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 2.1 s <<< FAILURE! "
            + "-- in io.github.jayessdeesea.minecraft.app.CampMineSitesTest";
    private static final String ERRORED = "[ERROR] Tests run: 2, Failures: 0, Errors: 2, Skipped: 0, Time elapsed: 0.5 s <<< ERROR! "
            + "-- in io.github.jayessdeesea.minecraft.app.ChatTest";

    @Test
    void noLineYet() {
        MavenProgress p = MavenProgress.of(List.of());
        assertEquals(new MavenProgress(Optional.empty(), 0, 0, 0, 0), p);
        assertEquals("no module begun yet, 0 test classes done", p.words());
    }

    @Test
    void theLastModuleBegunIsWhereItIs() {
        MavenProgress p = MavenProgress.of(List.of(PARENT, PROTOCOL, FRAME, SUMMARY, APP));
        assertEquals(new MavenProgress(Optional.of("app"), 7, 7, 1, 0), p);
        assertEquals("app [7/7], 1 test class done, 0 failing", p.words());
    }

    /** The module's summary Tests run line is no class; a test printing the words mid-line is none either. */
    @Test
    void onlyAClasssOwnLineCounts() {
        MavenProgress p = MavenProgress.of(List.of(PROTOCOL, FRAME, NBT, SUMMARY, "some test said: [INFO] Tests run: 3, "
                + "Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.1 s -- in x.Y", "Tests run: 1 -- in x.Z"));
        assertEquals(2, p.classesDone());
    }

    @Test
    void aFailingOrErroredClassIsDoneAndFailing() {
        MavenProgress p = MavenProgress.of(List.of(APP, FAILING, ERRORED, FRAME));
        assertEquals(3, p.classesDone());
        assertEquals(2, p.classesFailing());
        assertEquals("app [7/7], 3 test classes done, 2 failing", p.words());
    }

    @Test
    void colourCodesAreStripped() {
        MavenProgress p = MavenProgress.of(List.of("[\u001b[1;34mINFO\u001b[m] Building \u001b[1mprotocol 0.1.0-SNAPSHOT"
                        + "\u001b[m                                   [2/7]",
                "[\u001b[1;34mINFO\u001b[m] \u001b[1;32mTests run: \u001b[0;1;32m3\u001b[m, Failures: 0, Errors: 0, Skipped: 0, "
                        + "Time elapsed: 0.013 s - in io.github.Frame\u001b[m\r",
                "[\u001b[1;34mINFO\u001b[m] \u001b[1;32mTests run: \u001b[0;1;32m3\u001b[m, Failures: 0, Errors: 0, Skipped: 0, "
                        + "Time elapsed: 0.013 s -- in io.github.Nbt\u001b[m\r"));
        // an older Surefire's "- in" is the same line
        assertEquals(new MavenProgress(Optional.of("protocol"), 2, 7, 2, 0), p);
    }

    /** A line that looks like Maven's but will not parse is skipped, never thrown: the reading is display only. */
    @Test
    void anUnparseableLineIsSkipped() {
        MavenProgress p = MavenProgress.of(List.of(PROTOCOL,
                "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2.3 s -- in x.Odd",
                "[INFO] Tests run: 3, Failures: 99999999999, Errors: 0, Skipped: 0, Time elapsed: 0.1 s -- in x.Big",
                "[INFO] Building app 0.1.0-SNAPSHOT                                        [99999999999/7]",
                FRAME));
        assertEquals(new MavenProgress(Optional.of("protocol"), 2, 7, 1, 0), p);
    }

    /** A class time no verify could take still counts its class done: the count reads only the name (review round 2). */
    @Test
    void anAbsurdClassTimeStillCountsTheClassDone() {
        assertEquals(1, MavenProgress.of(List.of(PROTOCOL, "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, "
                + "Time elapsed: 99999999999999999999 s -- in x.X")).classesDone());
    }

    /** "Building jar: ..." is a plugin's line, not a module's. */
    @Test
    void aBuildingJarLineIsNoModule() {
        assertEquals(Optional.of("protocol"), MavenProgress.of(List.of(PROTOCOL,
                "[INFO] Building jar: C:\\mc-worktrees\\landing\\protocol\\target\\protocol-0.1.0-SNAPSHOT.jar")).module());
    }

    /** The log in full, for the estimate: the modules begun in order, each class done with its module and time. */
    @Test
    void theLogInFull() {
        MavenLog log = MavenLog.of(List.of(PARENT, PROTOCOL, FRAME, NBT, SUMMARY, APP, FAILING));
        assertEquals(List.of("minecraft-parent", "protocol", "app"), log.modules());
        assertEquals(java.util.Set.of(new MavenLog.Done("protocol", "io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoderTest"),
                new MavenLog.Done("protocol", "io.github.jayessdeesea.minecraft.protocol.packet.NbtTest"),
                new MavenLog.Done("app", "io.github.jayessdeesea.minecraft.app.CampMineSitesTest")), log.done());
        // the same class name in two modules is two classes done (review round 1)
        MavenLog twice = MavenLog.of(List.of(PROTOCOL, FRAME, APP, FRAME));
        assertEquals(2, twice.done().size());
        assertEquals(2, twice.progress().classesDone());
        assertTrue(log.finished("protocol"));
        assertFalse(log.finished("app"), "the running one");
        assertFalse(log.finished("bot"), "not begun");
        assertTrue(MavenLog.of(List.of(PROTOCOL, APP, "[INFO] BUILD SUCCESS")).finished("app"), "a BUILD line ends the last");
        assertTrue(MavenLog.of(List.of(PROTOCOL, APP, "[INFO] BUILD FAILURE")).finished("app"));
    }
}
