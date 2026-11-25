package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DrillPlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaceTest {

    private static final ChatClient NO_MODEL = ChatClient.create((ChatModel) prompt -> {
        throw new AssertionError("no model call expected");
    });

    private static final PlatoonLeader A_MODEL = (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(),
            new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null);

    /** The waits a model was given before a rule could decide: a rule's being taken away changes none of them. */
    @Test
    void aModelIsPacedAsItAlwaysWas() {
        assertEquals(20_000, Pace.MODEL.periodMillis(), "a squad leader's routine look");
        assertEquals(8_000, Pace.MODEL.reportGapMillis(), "between a squad leader's decisions on a CANTCO or a contact");
        assertEquals(60_000, Pace.MODEL.wakeGapMillis(), "between the platoon leader's decisions on a request");
        assertTrue(Pace.MODEL.queued(), "on its own thread, through the model calls' queue");
        assertEquals(3, ModelCalls.MAX_IN_FLIGHT, "in flight, from which a routine decision is shed");
    }

    @Test
    void aRuleWaitsForNothing() {
        assertEquals(0, Pace.RULES.periodMillis(), "every SITREP: the squad loop's own period");
        assertEquals(0, Pace.RULES.reportGapMillis());
        assertEquals(0, Pace.RULES.wakeGapMillis());
        assertFalse(Pace.RULES.queued(), "where it's asked for");
    }

    /** By what the configuration's choice makes of each leader (minecraft.*.decides-with), and nothing else. */
    @Test
    void theDecidersKindIsItsPace() {
        SquadLeaderPrompts prompts = new SquadLeaderPrompts("", "");
        SquadLeader drilled = DayWorkRules.squadLeaders("drills", NO_MODEL, prompts).apply("Alpha");
        SquadLeader modelled = DayWorkRules.squadLeaders("model", NO_MODEL, prompts).apply("Alpha");

        assertSame(Pace.RULES, Pace.of(drilled));
        assertSame(Pace.MODEL, Pace.of(modelled));
        assertSame(Pace.RULES, Pace.of(DayWorkRules.platoonLeader("drills", A_MODEL)));
        assertSame(Pace.RULES, Pace.of(new DrillPlatoonLeader()));
        assertSame(Pace.MODEL, Pace.of(DayWorkRules.platoonLeader("model", A_MODEL)), "a leader that doesn't say it's a rule waits as a model does");
    }


    @Test
    void aRulesDecisionRunsWhereItsAskedForAndIsNeverShedNorCounted() {
        ModelCalls calls = new ModelCalls();
        calls.queued("PL");
        calls.queued("Alpha's squad leader");
        calls.queued("Bravo's squad leader");
        List<String> ran = new ArrayList<>();

        assertFalse(Pace.RULES.sheds(calls, "Charlie's squad leader"), "the models backed up are nothing to a rule");
        Pace.Deciding deciding = Pace.RULES.deciding("Charlie's squad leader", calls, new Faults(() -> 0));
        deciding.run(task -> ran.add("on a thread"), () -> ran.add("decided"));

        assertEquals(List.of("decided"), ran);
        assertEquals(3, calls.inFlight(), "not counted among the model calls");
    }

    /** The platoon leader's decision surveys what each squad sees each time it's made: never on the squad loop's thread. */
    @Test
    void aDecisionThatSearchesRunsOnItsThreadWhateverDecidesIt() {
        ModelCalls calls = new ModelCalls();
        List<Runnable> thread = new ArrayList<>();

        Pace.RULES.deciding("PL", calls, new Faults(() -> 0)).onItsThread(thread::add, () -> { });
        assertEquals(1, thread.size(), "a rule's, on its thread");
        assertEquals(0, calls.inFlight(), "at once, and not counted among the model calls");

        Pace.MODEL.deciding("PL", calls, new Faults(() -> 0)).onItsThread(thread::add, () -> { });
        assertEquals(2, thread.size());
        assertEquals(1, calls.inFlight(), "a model's, queued as any");
    }

    @Test
    void aModelsDecisionIsQueuedOnItsThreadAndShedWhenTheModelsAreBackedUp() {
        ModelCalls calls = new ModelCalls();
        List<Runnable> thread = new ArrayList<>();
        List<String> ran = new ArrayList<>();

        Pace.Deciding deciding = Pace.MODEL.deciding("PL", calls, new Faults(() -> 0));
        deciding.run(thread::add, () -> ran.add("decided"));

        assertEquals(List.of(), ran, "not before its thread runs it");
        assertEquals(1, calls.inFlight());
        calls.queued("Alpha's squad leader");
        calls.queued("Bravo's squad leader");
        assertTrue(Pace.MODEL.sheds(calls, "Charlie's squad leader"), "three in flight: a routine one waits");
        thread.getFirst().run();
        deciding.started();
        deciding.finished();
        assertEquals(List.of("decided"), ran);
        assertEquals(2, calls.inFlight(), "out of flight once answered");
    }

    /** A failed decision: a model's is said by its caller each time, as it was; a rule's is its echelon's faults', counted. */
    @Test
    void aFailedDecisionIsLoggedAsItsPaceHasIt() {
        List<String> said = new ArrayList<>();
        Faults faults = new Faults(() -> 0);
        RuntimeException bug = new IllegalStateException("a bug");

        Pace.MODEL.deciding("PL", new ModelCalls(), faults).failed(bug, () -> said.add("no decision"));
        assertEquals(List.of("no decision"), said);
        assertEquals(List.of("  no faults"), faults.review());

        Pace.RULES.deciding("PL", new ModelCalls(), faults).failed(bug, () -> said.add("no decision"));
        Pace.RULES.deciding("PL", new ModelCalls(), faults).failed(bug, () -> said.add("no decision"));
        assertEquals(List.of("no decision"), said, "not said the model's way");
        List<String> review = Faults.review(new Faults(() -> 0), faults);
        assertEquals(1, review.size(), review.toString());
        assertTrue(review.getFirst().startsWith("  fault: PL's decision - IllegalStateException") && review.getFirst().endsWith("x2"), review.toString());
    }

    /** Only a rule's order equal to the standing one stands unsaid: a model's every decision is said, and so is the first under a new objective. */
    @Test
    void onlyARulesOrderStands() {
        SquadOrder order = new SquadOrder(CAMP, Formation.WEDGE, Interval.NORMAL, 3, Risk.LOW, java.util.Map.of(), false, "hold");
        SquadOrder again = new SquadOrder(CAMP, Formation.WEDGE, Interval.NORMAL, 3, Risk.LOW, java.util.Map.of(), false, "hold");

        assertTrue(Pace.RULES.stands(again, order, false));
        assertFalse(Pace.RULES.stands(again, order, true), "the answer to a new objective");
        assertFalse(Pace.RULES.stands(again, null, false), "the first");
        assertFalse(Pace.MODEL.stands(again, order, false));
    }

    private static final Position CAMP = new Position(0, 64, 0);

    /** The survey's clock is its own: 20 seconds, or the point moved half the terrain survey's radius, or another objective. */
    @Test
    void aSurveyIsDueOnItsOwnClock() {
        assertEquals(20_000, Surveyed.PERIOD_MILLIS);
        assertEquals(16.0, Surveyed.MOVED_BLOCKS);
        Surveyed<String> kept = new Surveyed<>(value -> new Position(4, 0, 0));
        Faults faults = new Faults(() -> 0);
        assertTrue(kept.due(CAMP, "the camp", 0), "none taken yet");

        kept.keep("the survey", () -> "flat", CAMP, "the camp", 0, Runnable::run, faults);

        assertEquals(Optional.of("flat"), kept.latest("the camp"));
        assertEquals(Optional.empty(), kept.latest("the ford"), "not the ground for another objective");
        assertFalse(kept.due(CAMP, "the camp", 19_999));
        assertTrue(kept.due(CAMP, "the camp", 20_000));
        assertFalse(kept.due(new Position(19, 64, 0), "the camp", 1000), "measured from where the survey says it was taken: x=4");
        assertTrue(kept.due(new Position(20, 64, 0), "the camp", 1000));
        assertTrue(kept.due(CAMP, "the ford", 1000));
    }

    /** A survey taken from another soldier than the one asked from, 16 blocks off, isn't due again till the one asked from has moved. */
    @Test
    void aSurveyTakenFarFromWhereItWasAskedIsNotDueAtOnce() {
        Surveyed<String> kept = new Surveyed<>(value -> new Position(40, 0, 0));
        kept.keep("the survey", () -> "flat", CAMP, "the camp", 0, Runnable::run, new Faults(() -> 0));

        assertFalse(kept.due(CAMP, "the camp", 1000), "the point asked from hasn't moved");
        assertTrue(kept.due(new Position(CAMP.x(), CAMP.y(), CAMP.z() + 16), "the camp", 1000), "moved from both");
    }

    /** A survey that fails leaves the last standing, and is tried again on the survey's clock - not at every SITREP. */
    @Test
    void aFailedSurveyIsTriedAgainOnTheSurveysClock() {
        Surveyed<String> kept = new Surveyed<>(value -> CAMP);
        Faults faults = new Faults(() -> 0);
        List<Long> tried = new ArrayList<>();
        kept.keep("the survey", () -> "flat", CAMP, "the camp", 0, Runnable::run, faults);

        for (long now = 20_000; now <= 60_000; now += 1000) {
            long at = now;
            kept.keep("the survey", () -> {
                tried.add(at);
                throw new IllegalStateException("no world");
            }, CAMP, "the camp", now, Runnable::run, faults);
        }

        assertEquals(List.of(20_000L, 40_000L, 60_000L), tried);
        assertEquals(Optional.of("flat"), kept.latest("the camp"), "the last one taken stands");
    }
}
