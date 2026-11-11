package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardTest {

    @TempDir
    Path state;

    private static Scoreboard.Score day(int day, int met, int deaths) {
        return new Scoreboard.Score(day * 1000L, day, 2, met, 7, List.of("phase 2 every soldier armed - 2 of 8"), deaths, 10, 5, 1, 8);
    }

    @Test
    void eachDayIsKeptAcrossRestartsAndComparedWithTheOneBefore() {
        String first = new Scoreboard(state).add(day(1, 4, 2));
        assertTrue(first.contains("the first day scored"), first);
        String second = new Scoreboard(state).add(day(2, 5, 0));
        assertTrue(second.startsWith("phase 2: 5/7 standards met, 0 dead"), second);
        assertTrue(second.contains("the day before: phase 2: 4/7 standards met, 2 dead"), second);
        assertTrue(second.endsWith("earliest open: phase 2 every soldier armed - 2 of 8"), second);
        assertEquals(List.of(day(1, 4, 2), day(2, 5, 0)), new Scoreboard(state).days());
    }

    @Test
    void aScoreboardThatWontReadIsAFault() throws Exception {
        Files.writeString(state.resolve("scoreboard.jsonl"), "{not json\n");
        assertThrows(UncheckedIOException.class, () -> new Scoreboard(state).days());
    }

    @Test
    void theDawnsScoreHasTheOpenStandardsEarliestPhaseFirst() {
        Foothold camp = new Foothold(new Position(0, 64, 0), List.of(
                new Foothold.Standard(2, "every soldier armed", false, "2 of 8", "NONE"),
                new Foothold.Standard(1, "shelter", true, "", "PATROL_BASE"),
                new Foothold.Standard(1, "the camp's ground lit", false, "8 dark", "SECURE")));
        AfterActionReview aar = new AfterActionReview();
        aar.time(true, Optional.of(camp));
        aar.death("Bob", "death.attack.arrow");
        Scoreboard.Score score = aar.time(false, Optional.of(camp), 3, 8, 42).orElseThrow().score();
        assertEquals(new Scoreboard.Score(42, 1, 1, 1, 3, List.of("phase 1 the camp's ground lit - 8 dark",
                "phase 2 every soldier armed - 2 of 8"), 1, 0, 0, 3, 8), score);
    }
}
