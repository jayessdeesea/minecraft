package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRegisterTest {

    private final TaskRegister register = new TaskRegister();

    private static TaskRegister.Assignment wall(int left) {
        return new TaskRegister.Assignment("wall the camp", new Report.Fortifying(), OptionalInt.of(left));
    }

    @Test
    void aTaskingIsFollowedToItsEnd() {
        assertEquals(List.of("[Register] Alice on wall the camp, 50 left"), register.update(0, Map.of("Alice", wall(50))));
        assertEquals(List.of(), register.update(30_000, Map.of("Alice", wall(20))));
        assertEquals(List.of("[Register] Alice done: wall the camp, in 60 s"), register.update(60_000, Map.of("Alice", wall(0))));

        assertEquals(List.of("    open: Alice: wall the camp DONE for 60 s, reports Fortifying[], 0 left"), register.review(60_000));
    }

    @Test
    void aTaskingTakenOffBeforeItCouldGetAnywhereIsChurn() {
        TaskRegister.Assignment farm = new TaskRegister.Assignment("keep the farm", new Report.Farming(), OptionalInt.empty());
        register.update(0, Map.of("Dana", wall(50)));
        register.update(5_000, Map.of("Dana", farm));             // off the wall after 5 s: churn
        register.update(10_000, Map.of("Dana", wall(50)));        // off the farm after 5 s: churn
        register.update(10_000 + TaskRegister.CHURN_MILLIS, Map.of("Dana", farm)); // a fair go at the wall: not
        assertEquals(2, register.takeChurned());
        assertEquals(0, register.takeChurned(), "counted afresh");
    }

    @Test
    void workReportedThatTheWorldDoesntBearOutIsAFinding() {
        register.update(0, Map.of("Dana", wall(50)));
        assertEquals(List.of(), register.update(TaskRegister.VERIFY_MILLIS - 1, Map.of("Dana", wall(50))));

        List<String> findings = register.update(TaskRegister.VERIFY_MILLIS, Map.of("Dana", wall(50)));

        assertEquals(List.of("[Verify] Dana reports Fortifying[] on wall the camp, but 50 left hasn't fallen in 120 s"), findings);
        assertEquals(TaskRegister.State.STALLED, register.open().getFirst().state());
        assertEquals(List.of("[Verify] Dana making progress on wall the camp again, 49 left"),
                register.update(TaskRegister.VERIFY_MILLIS + 1_000, Map.of("Dana", wall(49))));
    }

    @Test
    void stalledStaysStalledUntilWhatsLeftFallsWhateverItReports() {
        register.update(0, Map.of("Dana", wall(50)));
        register.update(TaskRegister.VERIFY_MILLIS, Map.of("Dana", wall(50)));

        List<String> findings = register.update(TaskRegister.VERIFY_MILLIS + 1_000, Map.of("Dana",
                new TaskRegister.Assignment("wall the camp", new Report.Idle(), OptionalInt.of(50))));

        assertEquals(List.of(), findings);
        assertEquals(TaskRegister.State.STALLED, register.open().getFirst().state());
    }

    @Test
    void aCantcoIsHeldOpenOnItsTaskingAndSaidOnce() {
        TaskRegister.Assignment cantco = new TaskRegister.Assignment("wall the camp", new Report.Cantco("no route"), OptionalInt.of(50));
        register.update(0, Map.of("Bob", wall(50)));

        assertEquals(List.of("[Register] Bob CANTCO on wall the camp: no route"), register.update(1_000, Map.of("Bob", cantco)));
        assertEquals(List.of(), register.update(2_000, Map.of("Bob", cantco)));
    }

    @Test
    void aTaskingEndedUnfinishedIsCountedAndForgotten() {
        register.update(0, Map.of("Carol", wall(50)));

        List<String> findings = register.update(10_000, Map.of("Carol",
                new TaskRegister.Assignment("light the ground", new Report.Securing(), OptionalInt.of(9))));

        assertTrue(findings.contains("[Register] Carol taken off wall the camp unfinished, after 10 s (UNDER_WAY)"), findings.toString());
        List<String> review = register.review(10_000);
        assertTrue(review.contains("  wall the camp: 0 done, 1 taken off unfinished"), review.toString());
        assertEquals(1, register.open().size());
        assertEquals(List.of("    open: Carol: light the ground UNDER_WAY for 0 s, reports Securing[], 9 left"), register.review(10_000));
    }

    @Test
    void workThatComesBackAfterItsDoneIsANewTasking() {
        register.update(0, Map.of("Gus", new TaskRegister.Assignment("light the ground", new Report.Securing(), OptionalInt.of(0))));

        List<String> findings = register.update(5_000, Map.of("Gus",
                new TaskRegister.Assignment("light the ground", new Report.Securing(), OptionalInt.of(1))));

        assertEquals(List.of("[Register] Gus on light the ground, 1 left"), findings);
        assertTrue(register.review(5_000).contains("  light the ground: 1 done, 0 taken off unfinished"));
    }
}
