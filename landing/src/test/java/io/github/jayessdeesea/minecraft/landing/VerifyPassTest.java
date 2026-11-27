package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.T0;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A verify that passed, read from its log once (design 3b): each module's wall from its Reactor Summary line - compile
 * and packaging included - and each test class's time from its {@code -- in <class>} line, under the module begun
 * before it. The lines are a real passing log's (mvn -o test -pl protocol,landing, 2026-11-27), cut to what is read.
 */
class VerifyPassTest {

    private static final List<String> LOG = List.of(
            "WARNING: A restricted method in java.lang.System has been called",
            "[INFO] Scanning for projects...",
            "[INFO] Reactor Build Order:",
            "[INFO] protocol                                                           [jar]",
            "[INFO] -------------< io.github.jayessdeesea.minecraft:protocol >--------------",
            "[INFO] Building protocol 0.1.0-SNAPSHOT                                   [1/2]",
            "[INFO] Running io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoderTest",
            "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.326 s -- in "
                    + "io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoderTest",
            "INFO  FrameStatsHandler - Connected: embedded",
            "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.057 s -- in "
                    + "io.github.jayessdeesea.minecraft.protocol.netty.FrameStatsHandlerTest",
            "[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0",
            "[INFO] Building landing 0.1.0-SNAPSHOT                                    [2/2]",
            "[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.163 s -- in "
                    + "io.github.jayessdeesea.minecraft.landing.VerifyOutcomeTest",
            "[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0",
            "[INFO] ------------------------------------------------------------------------",
            "[INFO] Reactor Summary for protocol 0.1.0-SNAPSHOT:",
            "[INFO] ",
            "[INFO] protocol ........................................... SUCCESS [  3.985 s]",
            "[INFO] landing ............................................ SUCCESS [  0.913 s]",
            "[INFO] ------------------------------------------------------------------------",
            "[INFO] BUILD SUCCESS",
            "[INFO] Total time:  5.264 s");

    private static Map<String, Duration> classes(Object... nameAndMillis) {
        Map<String, Duration> m = new LinkedHashMap<>();
        for (int i = 0; i < nameAndMillis.length; i += 2) {
            m.put((String) nameAndMillis[i], Duration.ofMillis((Integer) nameAndMillis[i + 1]));
        }
        return m;
    }

    @Test
    void aPassingLogsModulesAndClasses() {
        VerifyPass pass = VerifyPass.of(LOG, T0, sha(7), Duration.ofSeconds(6)).orElseThrow();
        assertEquals(new VerifyPass(T0, sha(7), Duration.ofSeconds(6), List.of(
                new VerifyPass.Module("protocol", Duration.ofMillis(3985), classes(
                        "io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoderTest", 326,
                        "io.github.jayessdeesea.minecraft.protocol.netty.FrameStatsHandlerTest", 57)),
                new VerifyPass.Module("landing", Duration.ofMillis(913), classes(
                        "io.github.jayessdeesea.minecraft.landing.VerifyOutcomeTest", 163)))), pass);
    }

    /** Maven's summary times: seconds, minutes and hours; a module with no tests has a wall and no classes. */
    @Test
    void theSummarysTimeForms() {
        List<String> log = List.of("[INFO] Building minecraft-parent 0.1.0-SNAPSHOT [1/3]",
                "[INFO] Building app 0.1.0-SNAPSHOT [2/3]",
                "[INFO] Building bot 0.1.0-SNAPSHOT [3/3]",
                "[INFO] Reactor Summary for minecraft-parent 0.1.0-SNAPSHOT:",
                "[INFO] minecraft-parent ................................... SUCCESS [  0.297 s]",
                "[INFO] app ................................................ SUCCESS [25:31 min]",
                "[INFO] bot ................................................ SUCCESS [01:02 h]",
                "[INFO] BUILD SUCCESS");
        VerifyPass pass = VerifyPass.of(log, T0, sha(7), Duration.ofMinutes(90)).orElseThrow();
        assertEquals(List.of(Duration.ofMillis(297), Duration.ofSeconds(25 * 60 + 31), Duration.ofMinutes(62)),
                pass.modules().stream().map(VerifyPass.Module::wall).toList());
        assertEquals(Map.of(), pass.modules().getFirst().classes());
    }

    /** A summary or class line that will not parse is skipped, never thrown: the rest of the pass is kept. */
    @Test
    void anUnparseableLineIsSkipped() {
        List<String> log = List.of("[INFO] Building app 0.1.0-SNAPSHOT [1/2]",
                "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2.3 s -- in x.Odd",
                "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.5 s -- in x.Even",
                "[INFO] Building bot 0.1.0-SNAPSHOT [2/2]",
                "[INFO] app ................................................ SUCCESS [  1.2.3 s]",
                "[INFO] app ................................................ SUCCESS [  4.000 s]",
                "[INFO] bot ................................................ SUCCESS [1:2:3 min]",
                "[INFO] bot ................................................ SUCCESS [99999999999999999999:00 min]",
                "[INFO] BUILD SUCCESS");
        VerifyPass pass = VerifyPass.of(log, T0, sha(7), Duration.ofSeconds(6)).orElseThrow();
        assertEquals(List.of(new VerifyPass.Module("app", Duration.ofSeconds(4), Map.of("x.Even", Duration.ofMillis(500)))),
                pass.modules());
    }

    private static final String GOOD = "[INFO] bot ................................................ SUCCESS [  4.000 s]";

    private static List<String> summary(String... lines) {
        List<String> log = new java.util.ArrayList<>(List.of("[INFO] Building app 0.1.0-SNAPSHOT [1/2]",
                "[INFO] Building bot 0.1.0-SNAPSHOT [2/2]"));
        log.addAll(List.of(lines));
        log.add("[INFO] BUILD SUCCESS");
        return log;
    }

    private static String app(String time) {
        return "[INFO] app ................................................ SUCCESS [" + time + "]";
    }

    /** A summary time no verify could take is skipped as any line that will not read (review round 2, A). */
    @Test
    void aSummaryTimeNoVerifyCouldTakeIsSkipped() {
        for (String absurd : List.of("100000000000000000:00 min", "10000000000000:00 h", "99999999999999999999 s")) {
            VerifyPass pass = VerifyPass.of(summary(app(absurd), GOOD), T0, sha(7), Duration.ofSeconds(6)).orElseThrow();
            assertEquals(List.of("bot"), pass.modules().stream().map(VerifyPass.Module::name).toList(), absurd);
        }
    }

    @Test
    void onlyAnAbsurdSummaryIsNoPass() {
        assertEquals(Optional.empty(), VerifyPass.of(summary(app("100000000000000000:00 min")), T0, sha(7),
                Duration.ofSeconds(6)));
    }

    /** The bound itself: 24h is a span a pass may hold, a minute past it is not. */
    @Test
    void theBoundItself() {
        for (String time : List.of("24:00 h", "1440:00 min")) {
            assertEquals(List.of("app", "bot"), VerifyPass.of(summary(app(time), GOOD), T0, sha(7), Duration.ofSeconds(6))
                    .orElseThrow().modules().stream().map(VerifyPass.Module::name).toList(), time);
        }
        for (String time : List.of("24:01 h", "1440:01 min")) {
            assertEquals(List.of("bot"), VerifyPass.of(summary(app(time), GOOD), T0, sha(7), Duration.ofSeconds(6))
                    .orElseThrow().modules().stream().map(VerifyPass.Module::name).toList(), time);
        }
    }

    /** A class time no verify could take is left out: a class the history does not know, no weight. */
    @Test
    void aClassTimeNoVerifyCouldTakeIsLeftOut() {
        List<String> log = List.of("[INFO] Building app 0.1.0-SNAPSHOT [1/1]",
                "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 99999999999999999999 s -- in x.X",
                "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.5 s -- in x.Y",
                app("  4.000 s"), "[INFO] BUILD SUCCESS");
        assertEquals(Map.of("x.Y", Duration.ofMillis(500)), VerifyPass.of(log, T0, sha(7), Duration.ofSeconds(6)).orElseThrow()
                .modules().getFirst().classes());
    }

    /** The bound is the type's: a span outside [0, 24h] is refused where a pass is made; a pass that took one is none. */
    @Test
    void aSpanOutsideTheBoundIsRefused() {
        Duration over = VerifyPass.MAX_SPAN.plusMillis(1);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VerifyPass.Module("m", over, Map.of()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VerifyPass.Module("m", Duration.ofMillis(-1), Map.of()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VerifyPass.Module("m", Duration.ZERO, Map.of("x.X", over)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VerifyPass(T0, sha(7), over, List.of()));
        new VerifyPass(T0, sha(7), VerifyPass.MAX_SPAN, List.of(new VerifyPass.Module("m", VerifyPass.MAX_SPAN, Map.of())));
        assertEquals(Optional.empty(), VerifyPass.of(LOG, T0, sha(7), Duration.ofHours(25)));
    }

    /** No Reactor Summary, no pass to learn from. */
    @Test
    void noSummaryGivesNone() {
        assertEquals(Optional.empty(), VerifyPass.of(LOG.subList(0, 14), T0, sha(7), Duration.ofSeconds(6)));
        assertEquals(Optional.empty(), VerifyPass.of(List.of(), T0, sha(7), Duration.ofSeconds(6)));
    }
}
