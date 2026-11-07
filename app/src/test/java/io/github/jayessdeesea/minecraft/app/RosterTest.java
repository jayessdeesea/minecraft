package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RosterTest {

    @Test
    void readsEachSquadAndItsSoldiersInFormationOrder() {
        assertEquals(List.of(new Roster.Squad("Alpha", List.of("Alice", "Bob")), new Roster.Squad("Bravo", List.of("Dana"))),
                Roster.parse("Alpha:Alice,Bob; Bravo: Dana"));
    }

    @Test
    void aMalformedRosterFailsRatherThanRunningShortHanded() {
        assertThrows(IllegalArgumentException.class, () -> Roster.parse("Alpha Alice,Bob"), "no colon");
        assertThrows(IllegalArgumentException.class, () -> Roster.parse("Alpha:Alice,,Bob"), "an empty name");
        assertThrows(IllegalArgumentException.class, () -> Roster.parse("Alpha:Alice;Bravo:Alice"), "a soldier in two squads");
        assertThrows(IllegalArgumentException.class, () -> Roster.parse("Alpha:Alice;Alpha:Bob"), "a squad twice");
    }
}
