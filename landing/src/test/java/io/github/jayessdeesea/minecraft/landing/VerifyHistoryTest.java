package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.T0;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The verify history (design 3b; operator's decision 9): the last five passes kept; expected, the newest pass's list of
 * modules and classes, each time the mean over the kept passes that hold it, the total the mean of their times.
 */
class VerifyHistoryTest {

    /** Pass {@code n}: took {@code minutes}, module a's wall {@code a} minutes with class A1 {@code a1} seconds. */
    private static VerifyPass pass(int n, int minutes, int a, int a1, String... more) {
        List<VerifyPass.Module> modules = new java.util.ArrayList<>(List.of(new VerifyPass.Module("a", Duration.ofMinutes(a),
                Map.of("A1", Duration.ofSeconds(a1)))));
        for (String m : more) {
            modules.add(new VerifyPass.Module(m, Duration.ofMinutes(1), Map.of()));
        }
        return new VerifyPass(T0.plusSeconds(n), sha(n), Duration.ofMinutes(minutes), modules);
    }

    @Test
    void noPassNoExpected() {
        assertEquals(Optional.empty(), VerifyHistory.NONE.expected());
    }

    @Test
    void onePassIsItsOwnExpected() {
        Expected e = VerifyHistory.NONE.with(pass(1, 30, 20, 60)).expected().orElseThrow();
        assertEquals(Duration.ofMinutes(30), e.total());
        assertEquals(1, e.passes());
        assertEquals(List.of(new VerifyPass.Module("a", Duration.ofMinutes(20), Map.of("A1", Duration.ofSeconds(60)))),
                e.modules());
    }

    @Test
    void twoPassesAreAveraged() {
        Expected e = VerifyHistory.NONE.with(pass(1, 30, 20, 60)).with(pass(2, 40, 30, 90)).expected().orElseThrow();
        assertEquals(Duration.ofMinutes(35), e.total());
        assertEquals(2, e.passes());
        assertEquals(Duration.ofMinutes(25), e.modules().getFirst().wall());
        assertEquals(Duration.ofSeconds(75), e.modules().getFirst().classes().get("A1"));
    }

    /** The newest pass's list wins: a module gone from it is dropped; one new in it is its own time alone. */
    @Test
    void theNewestPasssListWins() {
        Expected e = VerifyHistory.NONE.with(pass(1, 30, 20, 60, "old")).with(pass(2, 40, 30, 90, "new")).expected()
                .orElseThrow();
        assertEquals(List.of("a", "new"), e.modules().stream().map(VerifyPass.Module::name).toList());
        assertEquals(Duration.ofMinutes(1), e.modules().get(1).wall());
    }

    @Test
    void aSixthPassDropsTheOldest() {
        VerifyHistory h = VerifyHistory.NONE;
        for (int n = 1; n <= 6; n++) {
            h = h.with(pass(n, n == 1 ? 600 : 30, 20, 60));
        }
        assertEquals(5, h.passes().size());
        assertEquals(sha(2), h.passes().getFirst().candidate());
        assertEquals(Duration.ofMinutes(30), h.expected().orElseThrow().total(), "the 600m pass gone");
    }

    @Test
    void theHistoryRoundTripsAsJson() {
        WatchCodec codec = new WatchCodec();
        VerifyHistory h = VerifyHistory.NONE.with(pass(1, 30, 20, 60, "b")).with(new VerifyPass(T0.plusMillis(1500), sha(2),
                Duration.ofMillis(1_800_123), List.of(new VerifyPass.Module("a", Duration.ofMillis(3985),
                Map.of("x.Y", Duration.ofMillis(326), "x.Z", Duration.ofMillis(57))))));
        assertEquals(h, codec.history(codec.write(h)));
        assertThrows(IllegalArgumentException.class, () -> codec.history("{not json"));
    }

    /** A file holding a span outside [0, 24h] is no history: refused as one that will not read (review round 2, A). */
    @Test
    void aHistoryWithASpanOutsideTheBoundIsNotOne() {
        WatchCodec codec = new WatchCodec();
        String good = codec.write(VerifyHistory.NONE.with(pass(1, 30, 20, 60)));
        for (String wall : List.of("9000000000000000000", "-1")) {
            String bad = good.replace("\"wallMillis\":" + Duration.ofMinutes(20).toMillis(), "\"wallMillis\":" + wall);
            org.junit.jupiter.api.Assertions.assertNotEquals(good, bad);
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> codec.history(bad));
            org.junit.jupiter.api.Assertions.assertTrue(refused.getMessage().startsWith("not a verify history"),
                    refused.getMessage());
        }
    }
}
