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

    private static ShareWatch.Seen seen(int made) {
        return new ShareWatch.Seen(made, Optional.empty(), () -> java.util.Map.of("GATHERER", 0L), "GATHERER");
    }

    /** A spell ended short of its expected yield: behind, said once for its cause; a spell that met it after, back at it. */
    @Test
    void aShareShortOfItsYieldIsBehindOnceThenBack() {
        ShareWatch watch = new ShareWatch();
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(0), () -> 4, 0));
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(3), () -> 4, DUE - 1));
        List<String> said = watch.sample("Alpha", CUTTER, seen(3), () -> 4, DUE);
        assertTrue(said.getFirst().startsWith("[Alarm] [Alpha] Carl behind its share: woodcutter over x 8..24"), said.toString());
        assertTrue(said.getFirst().endsWith("made 3 of 4 expected in 180 s - no cause found"), said.toString());
        assertTrue(said.get(1).startsWith("[Gap] [Alpha -> PL] Carl's woodcutter short"), said.toString());
        assertEquals(1, watch.behind(List.of("Carl", "Alice")));
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(3), () -> 4, 2 * DUE), "once, however many spells it stays short for one cause");
        assertEquals(List.of("[Alpha] Carl back at its share after 540 s: made 5 of 4 expected"), watch.sample("Alpha", CUTTER, seen(8), () -> 4, 3 * DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
    }

    /** Its yield met, however it changed between - a log felled, a load put in the chest - isn't behind. */
    @Test
    void aShareThatMeetsItsYieldIsNotBehind() {
        ShareWatch watch = new ShareWatch();
        watch.sample("Alpha", CUTTER, seen(10), () -> 4, 0);
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(14), () -> 4, DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
    }

    /** Nothing in its share to work, nothing expected: no spell runs, and a share behind is behind no longer. */
    @Test
    void aShareWithNothingToWorkIsNotBehind() {
        ShareWatch watch = new ShareWatch();
        watch.sample("Alpha", CUTTER, seen(0), () -> 4, 0);
        watch.sample("Alpha", CUTTER, seen(0), () -> 0, DUE);
        assertEquals(1, watch.behind(List.of("Carl")));
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(0), () -> 0, 2 * DUE));
        assertEquals(0, watch.behind(List.of("Carl")));
        assertEquals(List.of(), watch.sample("Alpha", CUTTER, seen(0), () -> 0, 10 * DUE));
    }

    /** A baker with no wheat stored is idle, said up the chain once - not behind - and shown the platoon leader while it's so. */
    @Test
    void aBakerWithNoWheatIsIdleSaidUpNotBehind() {
        ShareWatch watch = new ShareWatch();
        DutyOrder baker = new DutyOrder("Dana", DutyOrder.Duty.BAKER, Optional.empty(), Optional.empty()).making(Optional.empty(), Optional.empty(), 8);
        assertEquals(List.of("[Alpha -> PL] Dana, baker, idle: no wheat stored"), watch.sample("Alpha", baker, seen(0), () -> 0, 0));
        assertEquals(List.of(), watch.sample("Alpha", baker, seen(0), () -> 0, 2 * DUE), "said once");
        assertEquals(List.of("Dana idle: no wheat stored"), watch.idle(List.of("Dana", "Carl")));
        assertEquals(0, watch.behind(List.of("Dana")));
        watch.sample("Alpha", baker, seen(0), () -> 8, 3 * DUE);
        assertEquals(1, watch.idle(List.of("Dana")).size(), "idle till a spell with work in it has run: one idle, not one a draw");
        watch.sample("Alpha", baker, seen(8), () -> 8, 4 * DUE);
        assertEquals(List.of(), watch.idle(List.of("Dana")), "wheat stored again, and baked");
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
