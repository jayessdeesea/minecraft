package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTrackTest {

    private static final Optional<String> THE_MINE = Optional.of("cut the mine"), NO_TASK = Optional.empty();
    private static final SoldierActivity UNASSIGNED = SoldierActivity.of(new Report.Moving(), NO_TASK);
    private static final SoldierActivity MINING = SoldierActivity.of(new Report.Mining(3, 20), THE_MINE);
    private static final SoldierActivity FIGHTING = SoldierActivity.of(new Report.InContact(2), THE_MINE);
    private static final Optional<Hands.Routine> FREE = Optional.empty(), MINER = Optional.of(Hands.Routine.MINER);

    @Test
    void anActivityIsTheFightTheRecoveryTheTaskOrNone() {
        assertEquals(new SoldierActivity.Fighting(), FIGHTING);
        assertEquals(new SoldierActivity.Recovering(), SoldierActivity.of(new Report.Recovering(), THE_MINE));
        assertEquals(new SoldierActivity.OnTask("cut the mine"), MINING);
        assertEquals("cut the mine", MINING.label());
        assertEquals("no task", UNASSIGNED.label());
    }

    @Test
    void theSameTaskUnderAnotherReportIsNoChange() {
        ActivityTrack track = new ActivityTrack();
        assertTrue(track.observe(0, MINING, FREE).isEmpty(), "the first reading is no change");
        assertTrue(track.observe(50, SoldierActivity.of(new Report.Moving(), THE_MINE), FREE).isEmpty(), "walking to the next step");
        assertTrue(track.observe(100, SoldierActivity.of(new Report.InPosition(), THE_MINE), FREE).isEmpty());
        assertEquals(0, track.now().orElseThrow().changes());
    }

    @Test
    void aRoutineTakingTheHandsIsAStepOfTheTaskNotAChange() {
        ActivityTrack track = new ActivityTrack();
        track.observe(0, MINING, FREE);
        assertTrue(track.observe(4_000, MINING, MINER).isEmpty());
        assertEquals(new ActivityTrack.Now(MINING, MINER, 0, 0, 0), track.now().orElseThrow(), "its hands are the miner's, its task since the start");
        assertTrue(track.observe(5_000, MINING, FREE).isEmpty());
        assertEquals(FREE, track.now().orElseThrow().hands());
    }

    @Test
    void aChangeSaysWhatWasLeftAfterHowLong() {
        ActivityTrack track = new ActivityTrack();
        track.observe(1_000, UNASSIGNED, FREE);
        ActivityTrack.Change change = track.observe(13_000, MINING, MINER).orElseThrow();
        assertEquals(UNASSIGNED, change.from());
        assertEquals(MINING, change.to());
        assertEquals(12_000, change.heldMillis());
        assertFalse(change.returned());
        assertEquals("no task -> cut the mine after 12 s", change.describe());
        assertEquals(new ActivityTrack.Now(MINING, MINER, 13_000, 1, 0), track.now().orElseThrow());
    }

    @Test
    void backToTheActivityJustLeftWithinTenSecondsIsAReturn() {
        ActivityTrack track = new ActivityTrack();
        track.observe(0, MINING, FREE);
        track.observe(60_000, UNASSIGNED, FREE);
        ActivityTrack.Change back = track.observe(63_000, MINING, FREE).orElseThrow();
        assertTrue(back.returned());
        assertEquals("no task -> cut the mine after 3 s (back again)", back.describe());
        assertEquals(1, track.now().orElseThrow().returns());
    }

    @Test
    void backAfterTheActivityBetweenWasHeldTenSecondsIsNoReturn() {
        ActivityTrack track = new ActivityTrack();
        track.observe(0, MINING, FREE);
        track.observe(60_000, FIGHTING, FREE);
        assertFalse(track.observe(60_000 + ActivityTrack.RETURN_MILLIS, MINING, FREE).orElseThrow().returned());
        assertEquals(0, track.now().orElseThrow().returns());
    }
}
