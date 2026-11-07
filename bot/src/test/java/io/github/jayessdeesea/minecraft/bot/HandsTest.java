package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.Hands.Routine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandsTest {

    /** A routine that starts when it's free and wants to, and runs until told it's done. */
    private static final class Fake implements Hands.Turn {
        boolean wants;
        boolean running;
        final List<Boolean> told = new ArrayList<>();

        @Override
        public boolean active() {
            return running;
        }

        @Override
        public void tick(boolean free) {
            told.add(free);
            if (free && wants) {
                running = true;
            }
        }
    }

    private static Map<Routine, Fake> fakes() {
        Map<Routine, Fake> fakes = new EnumMap<>(Routine.class);
        for (Routine routine : Routine.values()) {
            fakes.put(routine, new Fake());
        }
        return fakes;
    }

    private static Optional<Routine> tick(Map<Routine, Fake> fakes) {
        return Hands.tick(new EnumMap<Routine, Hands.Turn>(fakes));
    }

    @Test
    void theOrderIsTheDoctrine() {
        assertEquals(List.of(Routine.SUPPLY_RUN, Routine.DOORWAY, Routine.TORCHMAKER, Routine.RATIONS, Routine.HUNTER, Routine.ARMOURER,
                Routine.BUILDER, Routine.DEPOSITOR, Routine.HANDOVER, Routine.MINER, Routine.MASON, Routine.LAMPLIGHTER, Routine.FARMER, Routine.FORESTER, Routine.GATHERER, Routine.SCAVENGER), List.of(Routine.values()));
    }

    @Test
    void ofTwoWantingTheHandsTheHigherGetsThem() {
        Map<Routine, Fake> fakes = fakes();
        fakes.get(Routine.GATHERER).wants = true;
        fakes.get(Routine.RATIONS).wants = true;

        assertEquals(Optional.of(Routine.RATIONS), tick(fakes));
        assertEquals(List.of(false), fakes.get(Routine.GATHERER).told, "eating started before gathering was asked");
    }

    @Test
    void oneUnderWayKeepsTheHandsFromAHigherOne() {
        Map<Routine, Fake> fakes = fakes();
        fakes.get(Routine.HANDOVER).running = true;
        fakes.get(Routine.RATIONS).wants = true;

        assertEquals(Optional.of(Routine.HANDOVER), tick(fakes));
        assertEquals(List.of(false), fakes.get(Routine.RATIONS).told);
        assertEquals(List.of(true), fakes.get(Routine.HANDOVER).told, "still free to carry on");

        fakes.get(Routine.HANDOVER).running = false;
        assertEquals(Optional.of(Routine.RATIONS), tick(fakes), "eats once the logs are handed over");
    }

    @Test
    void nobodyWantingThemLeavesThemFree() {
        Map<Routine, Fake> fakes = fakes();

        assertEquals(Optional.empty(), tick(fakes));
        fakes.values().forEach(fake -> assertEquals(List.of(true), fake.told));
    }

    @Test
    void aRoutineWithNoTurnFailsLoudly() {
        Map<Routine, Hands.Turn> turns = new EnumMap<>(fakes());
        turns.remove(Routine.DOORWAY);

        assertThrows(IllegalArgumentException.class, () -> Hands.tick(turns));
    }
}
