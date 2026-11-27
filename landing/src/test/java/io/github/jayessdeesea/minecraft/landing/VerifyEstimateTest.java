package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Percent done and time left (design 3b; operator's decisions 9 and 11). f = the sum over known modules of the
 * module's expected wall times its part - 1 finished, 0 not begun, the running one its expected class time done over
 * its expected class time - over the expected total. percent = floor(100 f), at most 99 while running; left =
 * (1 - f)(elapsed + (1 - f) total); over = elapsed - total when positive. The fixture is a 30m verify: protocol, a 10m
 * wall, classes P1 12 s and P2 13 s; app, 20m, X1 24.5 s, X2 50 s, X3 25.5 s.
 */
class VerifyEstimateTest {

    private static final String PROTOCOL = "[INFO] Building protocol 0.1.0-SNAPSHOT [1/2]";
    private static final String APP = "[INFO] Building app 0.1.0-SNAPSHOT [2/2]";
    private static final Optional<Expected> THIRTY = Optional.of(new Expected(Duration.ofMinutes(30), List.of(
            new VerifyPass.Module("protocol", Duration.ofMinutes(10), classes("P1", 12_000, "P2", 13_000)),
            new VerifyPass.Module("app", Duration.ofMinutes(20), classes("X1", 24_500, "X2", 50_000, "X3", 25_500))), 4));

    private static Map<String, Duration> classes(Object... nameAndMillis) {
        Map<String, Duration> m = new LinkedHashMap<>();
        for (int i = 0; i < nameAndMillis.length; i += 2) {
            m.put((String) nameAndMillis[i], Duration.ofMillis((Integer) nameAndMillis[i + 1]));
        }
        return m;
    }

    private static String done(String name) {
        return "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.0 s -- in " + name;
    }

    private static MavenLog log(String... lines) {
        return MavenLog.of(new ArrayList<>(List.of(lines)));
    }

    private static VerifyEstimate estimate(MavenLog log, double elapsedMinutes) {
        return VerifyEstimate.of(THIRTY, log, Duration.ofMillis(Math.round(elapsedMinutes * 60_000))).orElseThrow();
    }

    @Test
    void noHistoryNoEstimate() {
        assertEquals(Optional.empty(), VerifyEstimate.of(Optional.empty(), log(PROTOCOL), Duration.ofMinutes(3)));
    }

    @Test
    void begunIsNoneDone() {
        VerifyEstimate e = estimate(log(PROTOCOL), 0);
        assertEquals(0, e.percent());
        assertEquals(Duration.ofMinutes(30), e.left());
        assertEquals(Optional.empty(), e.over());
        assertEquals(Duration.ofMinutes(30), e.expected());
        assertEquals(4, e.passes());
        assertEquals(5, e.classes());
    }

    /** On pace at 16%: P1 of protocol's 25 s done - 0.48 of its 10m, 4.8m of 30m - 25.2m left. */
    @Test
    void onPaceAtSixteen() {
        VerifyEstimate e = estimate(log(PROTOCOL, done("P1")), 4.8);
        assertEquals(16, e.percent());
        assertEquals(Duration.ofMillis(1_512_000), e.left());
    }

    /** On pace at 83%: protocol finished, X1 and X2 of app's 100 s - 0.745 of 20m - 5.1m left. */
    @Test
    void onPaceAtEightyThree() {
        VerifyEstimate e = estimate(log(PROTOCOL, done("P1"), done("P2"), APP, done("X1"), done("X2")), 24.9);
        assertEquals(83, e.percent());
        assertEquals(Duration.ofMillis(306_000), e.left());
        assertEquals(Optional.empty(), e.over());
    }

    /** Twice as slow at 83%: 49.8m gone - 0.17 x (49.8 + 5.1) = 9m20s left, 19m48s over. */
    @Test
    void twiceAsSlowAtEightyThree() {
        VerifyEstimate e = estimate(log(PROTOCOL, done("P1"), done("P2"), APP, done("X1"), done("X2")), 49.8);
        assertEquals(83, e.percent());
        assertEquals(Duration.ofMillis(559_980), e.left());
        assertEquals(Optional.of(Duration.ofMillis(1_188_000)), e.over());
    }

    /** Every class done but the build not ended: 99, never 100, while it runs. */
    @Test
    void neverAHundredWhileRunning() {
        VerifyEstimate e = estimate(log(PROTOCOL, done("P1"), done("P2"), APP, done("X1"), done("X2"), done("X3")), 30);
        assertEquals(99, e.percent());
        assertEquals(Duration.ZERO, e.left());
    }

    /** A class the history does not know carries no weight. */
    @Test
    void anUnknownClassCarriesNoWeight() {
        assertEquals(16, estimate(log(PROTOCOL, done("P1"), done("NEW")), 4.8).percent());
    }

    /** A known class that never comes is closed by its module finishing: protocol whole, not 0.48, once app begins. */
    @Test
    void aMissingKnownClassIsClosedByItsModule() {
        assertEquals(33, estimate(log(PROTOCOL, done("P1"), APP), 10).percent());
    }

    /**
     * How a verify ended, on the task's log: its time against expected and its longest quiet; over 1.25 x expected, an
     * [Overrun] naming each module over 1.25 x its own expected wall (operator's decision 10). It ends nothing.
     */
    @Test
    void theLinesAVerifyEndsWith() {
        Sha c = Fixtures.sha(7);
        assertEquals(List.of("verify took 31m (no estimate: no verify has passed yet), longest quiet 3m55s"),
                VerifyEstimate.ended(Optional.empty(), c, Duration.ofMinutes(31), Duration.ofSeconds(235), Optional.empty()));
        assertEquals(List.of("verify took 37m30s against 30m expected (4 passes), longest quiet 3m55s"),
                VerifyEstimate.ended(THIRTY, c, Duration.ofSeconds(37 * 60 + 30), Duration.ofSeconds(235), Optional.empty()));
        VerifyPass slow = new VerifyPass(Fixtures.T0, c, Duration.ofMinutes(40), List.of(
                new VerifyPass.Module("protocol", Duration.ofMinutes(12), Map.of()),
                new VerifyPass.Module("app", Duration.ofMinutes(26), Map.of()),
                new VerifyPass.Module("bot", Duration.ofMinutes(2), Map.of())));
        assertEquals(List.of("verify took 40m against 30m expected (4 passes), longest quiet 3m55s",
                        "[Overrun] the verify of " + c + " took 40m, over 1.25 x the 30m expected: app 26m against 20m"),
                VerifyEstimate.ended(THIRTY, c, Duration.ofMinutes(40), Duration.ofSeconds(235), Optional.of(slow)));
        assertEquals(List.of("verify took 40m against 30m expected (4 passes), longest quiet 3m55s",
                        "[Overrun] the verify of " + c + " took 40m, over 1.25 x the 30m expected: no Reactor Summary to name "
                                + "its modules"),
                VerifyEstimate.ended(THIRTY, c, Duration.ofMinutes(40), Duration.ofSeconds(235), Optional.empty()));
    }

    /**
     * The percent's divisor is the walls' sum, as its weights are (review round 1): a verify whose whole time is 60m
     * but whose modules' walls are 30m is a third done when protocol's 10m is - not a sixth. Time left still stretches
     * the whole expected time.
     */
    @Test
    void thePercentIsOfTheWallsSum() {
        Optional<Expected> sixty = Optional.of(new Expected(Duration.ofMinutes(60), THIRTY.orElseThrow().modules(), 4));
        VerifyEstimate e = VerifyEstimate.of(sixty, log(PROTOCOL, APP), Duration.ofMinutes(20)).orElseThrow();
        assertEquals(33, e.percent());
        assertEquals(Duration.ofMillis(Math.round((2.0 / 3) * (20 * 60_000 + (2.0 / 3) * 60 * 60_000))), e.left());
    }

    /** A class done in another module, of the same name as one of the running module's, is not that module's. */
    @Test
    void aClassIsItsModules() {
        assertEquals(33, estimate(log("[INFO] Building bot 0.1.0-SNAPSHOT [0/3]", done("X1"), done("X2"), PROTOCOL, done("P1"),
                done("P2"), APP), 10).percent());
    }

    /**
     * Every pass the parser keeps is one the queue can write and say (review round 2, A): with absurd summary and class
     * times beside sound ones, and spans at the bound, the history is written and a verify at twice expected is said.
     */
    @Test
    void everyKeptPassIsWrittenAndSaid() {
        WatchCodec codec = new WatchCodec();
        for (String time : List.of("100000000000000000:00 min", "10000000000000:00 h", "99999999999999999999 s", "24:00 h",
                "1440:00 min", "  0.001 s")) {
            List<String> lines = List.of(APP, "[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: "
                            + "99999999999999999999 s -- in x.X", done("X1"),
                    "[INFO] app ................................................ SUCCESS [" + time + "]",
                    "[INFO] bot ................................................ SUCCESS [24:00 h]", "[INFO] BUILD SUCCESS");
            VerifyPass pass = VerifyPass.of(lines, Fixtures.T0, Fixtures.sha(7), VerifyPass.MAX_SPAN).orElseThrow();
            VerifyHistory h = VerifyHistory.NONE;
            for (int i = 0; i < VerifyHistory.KEPT; i++) {
                h = h.with(pass);
            }
            assertEquals(h, codec.history(codec.write(h)), time);
            Expected e = h.expected().orElseThrow();
            assertEquals(2, VerifyEstimate.ended(Optional.of(e), Fixtures.sha(7), e.total().multipliedBy(2), Duration.ZERO,
                    Optional.of(pass)).size(), time);
        }
    }

    /** A module the history does not know carries no weight; the known ones before it are finished. */
    @Test
    void anUnknownModuleCarriesNoWeight() {
        assertEquals(33, estimate(log(PROTOCOL, "[INFO] Building bot 0.1.0-SNAPSHOT [2/3]"), 10).percent());
    }
}
