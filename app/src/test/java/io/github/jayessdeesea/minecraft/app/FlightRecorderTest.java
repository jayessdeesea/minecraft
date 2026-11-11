package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightRecorderTest {

    @Test
    void aFallIsReportedWithTheHeightAndWhatHadTheHands() {
        FlightRecorder recorder = new FlightRecorder();
        for (int i = 0; i < 100; i++) {
            double y = i < 90 ? 80 : 80 - (i - 89) * 2.5;
            recorder.record(new FlightRecorder.Sample(i * 250L, new Position(1, y, 2), "Gathering[resource=STONE]", 20, 18,
                    Optional.of(i < 90 ? "GATHERER" : "SCAVENGER"), Optional.empty()));
        }

        String report = recorder.casualty("Hal", "death.fell.accident.generic[Hal]");

        assertTrue(report.startsWith("Hal KIA: death.fell.accident.generic[Hal] at (1.0, 55.0, 2.0), hands on SCAVENGER - 25.0 blocks below"
                + " where it stood at (1.0, 80.0, 2.0) 2.5 s before, hands on GATHERER"), report);
        assertEquals(FlightRecorder.SHOWN + 1, report.lines().count(), "the cause, then the last five seconds");
        assertTrue(recorder.casualty("Hal", "again").endsWith("nothing recorded"), "a respawned soldier's record starts again");
    }

    @Test
    void aDeathOnLevelGroundSaysNothingOfAFall() {
        FlightRecorder recorder = new FlightRecorder();
        recorder.record(new FlightRecorder.Sample(0, new Position(0, 64, 0), "InContact[]", 3, 10, Optional.empty(),
                Optional.of("skeleton 6.0 away")));

        String report = recorder.casualty("Dana", "death.attack.arrow[Dana, entity.minecraft.skeleton]");

        assertFalse(report.contains("below"), report);
        assertTrue(report.contains("hands free") && report.contains("skeleton 6.0 away"), report);
    }

    @Test
    void aSampleIsDueEveryFewTicks() {
        FlightRecorder recorder = new FlightRecorder();
        long due = IntStream.range(0, 20).filter(i -> recorder.due()).count();
        assertEquals(20 / FlightRecorder.EVERY_TICKS, due);
    }
}
