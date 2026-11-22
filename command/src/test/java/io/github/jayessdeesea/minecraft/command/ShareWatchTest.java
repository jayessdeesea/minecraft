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

    /** A spell ended short of its expected yield: behind, said once; a spell that met it after, back at it. */
    @Test
    void aShareShortOfItsYieldIsBehindOnceThenBack() {
        ShareWatch watch = new ShareWatch();
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, () -> 4, 0));
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 3, () -> 4, DUE - 1));
        String alarm = watch.sample("Alpha", CUTTER, 3, () -> 4, DUE).orElseThrow();
        assertTrue(alarm.startsWith("[Alarm] [Alpha] Carl behind its share: woodcutter over x 8..24"), alarm);
        assertTrue(alarm.endsWith("made 3 of 4 expected in 180 s"), alarm);
        assertEquals(1, watch.behind(List.of("Carl", "Alice")));
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 3, () -> 4, 2 * DUE), "once, however many spells it stays behind");
        assertEquals(Optional.of("[Alpha] Carl back at its share after 540 s: made 5 of 4 expected"), watch.sample("Alpha", CUTTER, 8, () -> 4, 3 * DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
    }

    /** Its yield met, however it changed between - a log felled, a load put in the chest - isn't behind. */
    @Test
    void aShareThatMeetsItsYieldIsNotBehind() {
        ShareWatch watch = new ShareWatch();
        watch.sample("Alpha", CUTTER, 10, () -> 4, 0);
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 14, () -> 4, DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
    }

    /** Nothing in its share to work, nothing expected: no spell runs, and a share behind is behind no longer. */
    @Test
    void aShareWithNothingToWorkIsNotBehind() {
        ShareWatch watch = new ShareWatch();
        watch.sample("Alpha", CUTTER, 0, () -> 4, 0);
        watch.sample("Alpha", CUTTER, 0, () -> 0, DUE);
        assertEquals(1, watch.behind(List.of("Carl")));
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, () -> 0, 2 * DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
        assertEquals(Optional.empty(), watch.sample("Alpha", CUTTER, 0, () -> 0, 10 * DUE));
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
