package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Directive;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanBookTest {

    @TempDir
    Path state;

    @Test
    void everyPlanIsReadBackAsItWasKept() {
        Plan miner = new Plan(Role.MINER, Optional.of(new Position(8, 16, 8)), List.of(
                new Directive.GoTo(new Position(1.5, 64, -2.5)),
                new Directive.Fetch(Resource.STONE, 32, new Position(10, 60, 10)),
                new Directive.Level(0, -3, 7, 4, 64)));
        PlanBook book = new PlanBook(state);
        assertTrue(book.put("Alice", miner));
        assertFalse(book.put("Alice", miner), "nothing changed");

        assertEquals(miner, new PlanBook(state).of("Alice"));
        assertEquals(Plan.RIFLEMAN, new PlanBook(state).of("Bob"), "nobody tasked is a rifleman");
    }

    @Test
    void aSoldierPutBackWithTheSquadIsntKept() {
        PlanBook book = new PlanBook(state);
        book.put("Alice", new Plan(Role.FARMER, Optional.of(new Position(0, 64, 0)), List.of()));
        book.put("Alice", Plan.RIFLEMAN);
        assertEquals(Plan.RIFLEMAN, new PlanBook(state).of("Alice"));
    }

    @Test
    void plansThatWontReadAreAFaultNotAFreshStart() throws Exception {
        Files.writeString(state.resolve("plans.json"), "{not json");
        assertThrows(UncheckedIOException.class, () -> new PlanBook(state));
    }
}
