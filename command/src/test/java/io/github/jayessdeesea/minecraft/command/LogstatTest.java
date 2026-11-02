package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogstatTest {

    private static SoldierStatus soldier(Optional<Condition> condition) {
        return new SoldierStatus("S", new Position(0, 64, 0), new Order.Idle(), new Report.Idle(), condition);
    }

    @Test
    void sumsTheSquadsLaceReports() {
        Logstat logstat = Logstat.of(List.of(
                soldier(Optional.of(new Condition(20, 20, 0, Optional.of(new Kit(Optional.of("iron_sword"), 16, 4))))),
                soldier(Optional.of(new Condition(6, 12, 2, Optional.of(new Kit(Optional.empty(), 2, 1))))),
                soldier(Optional.of(new Condition(15, 18, 1, Optional.empty()))),
                soldier(Optional.empty())));

        assertEquals(new Logstat(4, 3, 1, 1, 3, 2, 1, 3, 8, 18, 0, 0), logstat);
    }
}
