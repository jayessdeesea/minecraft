package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTraceTest {

    private static final Objective WALL = new Objective("Wall the camp in", new Position(0, 64, 0), "Nothing walks in",
            new Objective.Task.Fortify());

    private static List<SoldierStatus> doing(Report report) {
        return List.of(new SoldierStatus("Alice", new Position(0, 64, 0), new Order.Idle(), report, Optional.empty()));
    }

    @Test
    void anOrderIsFollowedFromTheGeneralToTheSoldiersAtIt() {
        OrderTrace trace = new OrderTrace("OP-1", "Jake", "!mission wall the camp", 0, 1);
        Map<String, Optional<Objective>> given = new LinkedHashMap<>();
        given.put("Alpha", Optional.of(WALL));

        assertTrue(trace.decided(given, 6_000).getFirst().contains("the general decided in 6.0 s - Alpha FORTIFY"));
        assertEquals(List.of(), trace.sitrep("Alpha", -1, doing(new Report.Moving()), 8_000), "nothing new: no FRAGO, not at it yet");
        List<String> frago = trace.sitrep("Alpha", 9_000, doing(new Report.Moving()), 10_000);
        assertTrue(frago.getFirst().contains("FRAGO) 9.0 s"), frago.toString());
        List<String> atIt = trace.sitrep("Alpha", 9_000, doing(new Report.Fortifying()), 30_000);
        assertTrue(atIt.getFirst().contains("Alpha WILCO - at it (Fortifying) 30.0 s"), atIt.toString());
        assertTrue(atIt.getLast().contains("carried out"), atIt.toString());
        assertTrue(trace.closed());
    }

    @Test
    void eachHopThatTakesTooLongRaisesOneAlarm() {
        OrderTrace trace = new OrderTrace("OP-2", "Jake", "!mission wall the camp", 0, 1);

        assertTrue(trace.sitrep("Alpha", -1, doing(new Report.Moving()), OrderTrace.GENERAL_ALARM_MILLIS + 1).getFirst()
                .startsWith("ALARM OP-2: no decision from the general"));
        assertEquals(List.of(), trace.sitrep("Alpha", -1, doing(new Report.Moving()), OrderTrace.GENERAL_ALARM_MILLIS + 2), "once");

        trace.decided(Map.of("Alpha", Optional.of(WALL)), 40_000);
        long late = 40_000 + OrderTrace.WORKING_ALARM_MILLIS + 1;
        List<String> lines = trace.sitrep("Alpha", -1, doing(new Report.Gathering(Resource.STONE)), late);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("ALARM OP-2: Alpha's squad leader hasn't passed the order on")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("ALARM OP-2: Alpha not yet at FORTIFY") && l.contains("Alice Gathering")),
                lines.toString());
    }

    @Test
    void aNewerOrderSupersedesOneNotCarriedOut() {
        OrderTrace trace = new OrderTrace("OP-3", "Jake", "!escort Jake", 0, 1);
        trace.direct("Alpha", Optional.of(new Objective.Task.None()), 0);

        assertTrue(trace.supersede().orElseThrow().contains("superseded"));
        assertTrue(trace.closed());
        assertEquals(List.of(), trace.sitrep("Alpha", 1, doing(new Report.Moving()), 2));
    }
}
