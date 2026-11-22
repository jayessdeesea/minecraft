package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The squad leader's answer to a share's miss, a rule per cause (backlog/duty-orders.md). */
class MissAnswerTest {

    private static final Position CHEST = new Position(3.5, 64, 1.5), NEXT = new Position(6.5, 64, -2.5);
    private static final Position TABLE = new Position(1.5, 64, -1.5), KEEP = new Position(40.5, 64, 40.5);

    private static DutyOrder baker(Optional<Position> source, Optional<Position> table) {
        return new DutyOrder("Dana", DutyOrder.Duty.BAKER, Optional.empty(), Optional.of(CHEST)).making(source, table, 8);
    }

    private static MissAnswer.Miss miss(DutyOrder was, DutyOrder now) {
        return new MissAnswer.Miss("Alpha", was, now, 0, 8, 180_000);
    }

    @Test
    void aCantcoAtTheTableIsAnsweredWithTheNextTableOrUpTheChain() {
        MissAnswer.Answer next = MissAnswer.answer(miss(baker(Optional.of(CHEST), Optional.of(TABLE)), baker(Optional.of(CHEST), Optional.of(KEEP))),
                Optional.of("no table or furnace to use"), Map.of(), "ARMOURER");
        assertEquals("CANTCO", next.kind());
        assertTrue(next.line().startsWith("[Alpha -> Dana] CANTCO noted, no table or furnace to use") && next.line().endsWith("the next table, at (40, 64, 40)"),
                next.line());
        MissAnswer.Answer up = MissAnswer.answer(miss(baker(Optional.of(CHEST), Optional.of(TABLE)), baker(Optional.of(CHEST), Optional.empty())),
                Optional.of("no table or furnace to use"), Map.of(), "ARMOURER");
        assertTrue(up.line().startsWith("[Alpha -> PL] Dana, baker, CANTCO: no table or furnace to use") && up.line().endsWith("no table standing to re-point it to"),
                up.line());
    }

    @Test
    void aSourceRunDryIsAnsweredWithTheNextOrUpTheChain() {
        String next = MissAnswer.answer(miss(baker(Optional.of(CHEST), Optional.of(TABLE)), baker(Optional.of(NEXT), Optional.of(TABLE))), Optional.empty(),
                Map.of(), "ARMOURER").line();
        assertTrue(next.endsWith("(3, 64, 1) ran dry, made 0 of 8 expected in 180 s - the next, at (6, 64, -3)"), next);
        assertEquals("[Alpha -> PL] Dana, baker, idle: no wheat stored - made 0 of 8 expected in 180 s", MissAnswer.answer(miss(baker(Optional.of(CHEST),
                Optional.of(TABLE)), baker(Optional.empty(), Optional.of(TABLE))), Optional.empty(), Map.of(), "ARMOURER").line());
    }

    @Test
    void handsTakenByOtherWorkAreTheCauseNotReordered() {
        MissAnswer.Answer taken = MissAnswer.answer(miss(baker(Optional.of(CHEST), Optional.of(TABLE)), baker(Optional.of(CHEST), Optional.of(TABLE))),
                Optional.empty(), Map.of("CONTACT", 120_000L, "ARMOURER", 20_000L, MissAnswer.FREE, 40_000L), "ARMOURER");
        assertEquals("its hands CONTACT's", taken.kind());
        assertEquals("[Alpha] Dana short of its share, made 0 of 8 expected in 180 s - its hands CONTACT's for 120 s of it: the cause, not re-ordered", taken.line());
    }

    @Test
    void noCauseFoundIsAGapReportNamingWhatWasChecked() {
        MissAnswer.Answer gap = MissAnswer.answer(miss(baker(Optional.of(CHEST), Optional.of(TABLE)), baker(Optional.of(CHEST), Optional.of(TABLE))),
                Optional.empty(), Map.of("ARMOURER", 30_000L, MissAnswer.FREE, 150_000L), "ARMOURER");
        assertEquals("no cause found", gap.kind());
        assertTrue(gap.line().startsWith("[Gap] [Alpha -> PL] Dana's baker short: baker, the dump at (3, 64, 1)") && gap.line().endsWith(
                "made 0 of 8 expected in 180 s; checked: no CANTCO, its order unchanged, its hands the duty's for 30 s and free for 150 s"), gap.line());
    }
}
