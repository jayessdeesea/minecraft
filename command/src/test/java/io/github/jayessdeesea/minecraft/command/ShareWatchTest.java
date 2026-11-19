package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareWatchTest {

    private static final DutyOrder CUTTER = new DutyOrder("Carl", DutyOrder.Duty.WOODCUTTER, Optional.of(new Box(8, 56, -24, 24, 88, 24)),
            Optional.empty());
    private static final long DUE = ShareWatch.DUE_MILLIS;

    /** Nothing made or spent in its due time: behind, said once; once it changes, back at it. */
    @Test
    void aShareThatMakesNothingIsBehindOnceThenBack() {
        ShareWatch watch = new ShareWatch();
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, 0));
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, DUE - 1));
        String alarm = watch.sample("Alpha", CUTTER, 0, DUE).orElseThrow();
        assertTrue(alarm.startsWith("[Alarm] [Alpha] Carl behind its share: woodcutter over x 8..24"), alarm);
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, 2 * DUE), "once a spell");
        assertEquals(1, watch.behind(List.of("Carl", "Alice")));
        assertEquals(Optional.of("[Alpha] Carl back at its share after 360 s"), watch.sample("Alpha", CUTTER, 4, 2 * DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
    }

    /** Making anything - a log felled, a load put in the chest - keeps the share's clock going. */
    @Test
    void anyChangeIsWork() {
        ShareWatch watch = new ShareWatch();
        watch.sample("Alpha", CUTTER, 0, 0);
        watch.sample("Alpha", CUTTER, 16, DUE - 1);
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 16, DUE + 1));
    }

    /** One of a production squad with no share is alarmed once it's held so its due time. */
    @Test
    void holdingWithoutAShareIsAlarmed() {
        ShareWatch watch = new ShareWatch();
        assertEquals(Optional.empty(), watch.withoutShare("Alpha", "Eve", "FARM", 0));
        assertEquals("[Alarm] [Alpha] Eve holds without a share of the FARM for 180 s", watch.withoutShare("Alpha", "Eve", "FARM", DUE).orElseThrow());
        assertEquals(Optional.empty(), watch.withoutShare("Alpha", "Eve", "FARM", 2 * DUE), "once a spell");
        watch.shared("Eve");
        assertEquals(Optional.empty(), watch.withoutShare("Alpha", "Eve", "FARM", 3 * DUE), "a new spell starts");
    }

    /** The platoon leader's alarm: half or more of a squad's producers behind, said once; and when it's back. */
    @Test
    void theSquadIsBehindWhenHalfItsProducersAre() {
        ShareWatch watch = new ShareWatch();
        assertEquals(Optional.empty(), watch.squadBehind("Alpha", "WOOD", 1, 4));
        assertEquals("[Alarm] [PL] Alpha's WOOD is behind its order: 2 of 4 producers behind their shares",
                watch.squadBehind("Alpha", "WOOD", 2, 4).orElseThrow());
        assertEquals(Optional.empty(), watch.squadBehind("Alpha", "WOOD", 3, 4));
        assertEquals("[PL] Alpha's WOOD back to its order: 1 of 4 producers behind", watch.squadBehind("Alpha", "WOOD", 1, 4).orElseThrow());
    }
}
