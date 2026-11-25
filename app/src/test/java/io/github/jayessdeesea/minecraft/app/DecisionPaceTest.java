package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DayWork;
import io.github.jayessdeesea.minecraft.command.DecidesByRules;
import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When each echelon's leader decides, by what decides for it ({@link Pace}): the chain of command given a squad's
 * SITREP every second on simulated time, as the squad loop gives it. A rule answers a report at the SITREP that
 * carries it; a model keeps every wait it had.
 */
class DecisionPaceTest {

    /** The squad loop's period. */
    private static final long SITREP_MILLIS = 1000;
    /** What "answered at once" is held to: the SITREP that carries the report, and one more. */
    private static final long ANSWERED_WITHIN_MILLIS = 2000;

    private static final Objective FIRST = new Objective("Hold the crossing", new Position(8, 64, 8), "Nobody comes over it");
    private static final Objective NEXT = new Objective("Hold the ford", new Position(-8, 64, -8), "Nor round it");

    /** Flat ground all round the point man, every spot of it walked to. */
    private static final TerrainSurvey TERRAIN = new TerrainSurvey(0, 0, 8, 8, List.of(
            new TerrainSurvey.Sample(-8, -8, TerrainSurvey.Ground.LAND, 64, true), new TerrainSurvey.Sample(0, -8, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(8, -8, TerrainSurvey.Ground.LAND, 64, true), new TerrainSurvey.Sample(-8, 0, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(0, 0, TerrainSurvey.Ground.LAND, 64, true), new TerrainSurvey.Sample(8, 0, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(-8, 8, TerrainSurvey.Ground.LAND, 64, true), new TerrainSurvey.Sample(0, 8, TerrainSurvey.Ground.LAND, 64, true),
            new TerrainSurvey.Sample(8, 8, TerrainSurvey.Ground.LAND, 64, true)));

    private static final Report STUCK = new Report.Cantco("no route to (9, 64, 9) (MEDIUM risk): a 5-block drop");
    private static final Optional<Progress> DONE = Optional.of(new Progress(Progress.State.DONE, "the crossing is held"));

    private final SimClock clock = new SimClock();
    /** Everything keyed out on the radio net. */
    private final List<String> net = new ArrayList<>();
    /** When each terrain survey was taken. */
    private final List<Long> surveyedAt = new ArrayList<>();
    /** How many of them were taken inside the squad loop's own call, where a search holds up every squad's orders. */
    private int surveyedInTheSquadLoop;
    private boolean inTheSquadLoop;
    /** While set, a survey fails. */
    private boolean noWorld;

    /** A platoon leader that gives the first objective, and the next once the squad reports its task done; when it was asked. */
    private static class Scripted implements PlatoonLeader {
        final List<Long> askedAt = new ArrayList<>();
        private final SimClock clock;

        Scripted(SimClock clock) {
            this.clock = clock;
        }

        @Override
        public List<Objective> decide(List<SquadStatus> squads) {
            askedAt.add(clock.millis());
            return squads.stream().map(s -> s.progress().filter(p -> p.state() == Progress.State.DONE).isPresent() ? NEXT : FIRST).toList();
        }

        @Override
        public void receiveMission(String mission) {
        }

        @Override
        public void receiveStockpile(Stockpile stockpile) {
        }

        @Override
        public void receiveRemark(String remark) {
        }

        @Override
        public void receiveFoothold(Foothold foothold) {
        }

        @Override
        public void receiveDayWork(DayWork work) {
        }

        @Override
        public void receiveTime(TimeOfDay time) {
        }
    }

    /** The same, deciding by rules. */
    private static final class Ruled extends Scripted implements DecidesByRules {
        Ruled(SimClock clock) {
            super(clock);
        }
    }

    /** A squad leader's model that always gives the same order; when it was asked. */
    private ChatClient model(List<Long> askedAt) {
        String reply = """
                {"x": 8.5, "y": 64, "z": 8.5, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 3, "risk": "LOW", "soldierRisks": [], "rearm": false,
                 "intent": "hold the crossing", "cantco": "", "roleChanges": [], "detached": []}
                """;
        return ChatClient.create((ChatModel) prompt -> {
            askedAt.add(clock.millis());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        });
    }

    private ChainOfCommand chain(PlatoonLeader platoonLeader, java.util.function.Function<String, SquadLeader> squadLeaders) {
        ChainOfCommand chain = new ChainOfCommand(platoonLeader, squadLeaders, new Roster("Alpha:Alice,Bob"), false, clock);
        chain.onTime(TimeOfDay.of(1000, true));
        chain.setRadio(net::add);
        return chain;
    }

    private static SoldierStatus soldier(String name, double x, Report report) {
        return new SoldierStatus(name, new Position(x, 64, 0), new Order.Idle(), report, Optional.empty());
    }

    /** One squad-loop cycle: Alpha's SITREP - Bob's report, and how its task stands - then a second passes. */
    private void cycle(ChainOfCommand chain, Report bob, Optional<Progress> progress) {
        cycle(chain, 0, bob, progress);
    }

    /** The same, the point man - Alice - at {@code x}. */
    private void cycle(ChainOfCommand chain, double x, Report bob, Optional<Progress> progress) {
        inTheSquadLoop = true;
        chain.onSitreps(List.of(new ChainOfCommand.Sitrep("Alpha", List.of(soldier("Alice", x, new Report.Moving()), soldier("Bob", x + 2, bob)),
                () -> {
                    surveyedAt.add(clock.millis());
                    surveyedInTheSquadLoop += inTheSquadLoop ? 1 : 0;
                    if (noWorld) {
                        throw new IllegalStateException("no world to survey");
                    }
                    return new TerrainSurvey((int) x, 0, 8, 8, TERRAIN.samples()); // taken from the point man's column
                }, Optional.empty(), List::of, false, OptionalInt.empty(), progress)));
        inTheSquadLoop = false;
        clock.advance(SITREP_MILLIS);
    }

    private void cycles(ChainOfCommand chain, int count) {
        for (int i = 0; i < count; i++) {
            cycle(chain, new Report.Moving(), Optional.empty());
        }
    }

    /**
     * A soldier's CANTCO is its request to its squad leader. Live, behind a model's 8 second gap and 20 second look, a
     * rule's answer took 36 to 73 seconds and the request's 60 second alarm went off first.
     */
    @Test
    void aSoldiersCantcoIsAnsweredByARulesSquadLeaderAtOnce() {
        ChainOfCommand chain = chain(new Scripted(clock), DrillSquadLeader::new);
        cycles(chain, 4);
        assertEquals(Map.of(), chain.currentOrder("Alpha").orElseThrow().soldierRisk(), "nobody stuck yet");

        long reported = clock.millis();
        while (clock.millis() < reported + ANSWERED_WITHIN_MILLIS) {
            cycle(chain, STUCK, Optional.empty());
        }

        assertEquals(Map.of("Bob", Risk.MEDIUM), chain.currentOrder("Alpha").orElseThrow().soldierRisk(),
                "the squad leader's answer to Bob's CANTCO, " + ANSWERED_WITHIN_MILLIS + " ms after it was reported");
    }

    /** A squad's task done is its request for the next: step-by-step tasking loses nothing to a wait between steps. */
    @Test
    void aSquadsTaskDoneIsAnsweredByARulesPlatoonLeaderAtOnce() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
        cycles(chain, 5);
        assertEquals(Optional.of(FIRST), chain.objective("Alpha"));

        long reported = clock.millis();
        while (clock.millis() < reported + ANSWERED_WITHIN_MILLIS) {
            cycle(chain, new Report.Moving(), DONE);
        }

        assertEquals(Optional.of(NEXT), chain.objective("Alpha"), "the platoon leader's answer, " + ANSWERED_WITHIN_MILLIS + " ms after the task was reported done");
    }

    /**
     * The first order under a new objective is given from ground surveyed for that objective, once: from the last
     * objective's survey it would be said, then said again when the survey came in - and a CANTCO sent up, if that
     * ground showed nothing reachable toward the new one.
     */
    @Test
    void aNewObjectivesOrderWaitsForTheSurveyTakenForIt() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
        cycles(chain, 5);
        SquadOrder standing = chain.currentOrder("Alpha").orElseThrow();
        int surveys = surveyedAt.size();
        net.clear();

        cycle(chain, new Report.Moving(), DONE); // the platoon leader gives the next objective
        assertEquals(Optional.of(NEXT), chain.objective("Alpha"));
        cycle(chain, new Report.Moving(), Optional.empty()); // its survey is asked for
        assertSame(standing, chain.currentOrder("Alpha").orElseThrow(), "no order till the new objective's survey is in");
        assertEquals(surveys + 1, surveyedAt.size());
        cycles(chain, 10);

        assertEquals(new Position(-7.5, 64, -7.5), chain.currentOrder("Alpha").orElseThrow().objective());
        assertEquals(1, net.stream().filter(line -> line.contains("[Alpha -> squad] FRAGO")).count(), "one order, from fresh ground: " + net);
    }

    /**
     * A rule asked every second gives the order it gave once: the same one again is nothing to send or to log - nor to
     * hand the squad loop as another order, which works an order's plan changes once for each order object it's given.
     */
    @Test
    void anOrderThatStandsIsNotGivenAgain() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
        cycles(chain, 4);
        SquadOrder given = chain.currentOrder("Alpha").orElseThrow();

        for (int i = 0; i < 30; i++) {
            cycles(chain, 1);
            assertSame(given, chain.currentOrder("Alpha").orElseThrow(), "the same order object, " + i + " SITREPs on");
        }

        assertEquals(1, net.stream().filter(line -> line.contains("[Alpha -> squad] FRAGO")).count(), "one order, said once: " + net);
        assertEquals(1, net.stream().filter(line -> line.contains("[PL -> Alpha] OPORD")).count(), "and one objective: " + net);
    }

    /**
     * A survey that fails is tried again on the survey's clock, not at every SITREP. With none ever taken there is no
     * decision - as a model has none when its survey fails: ground not surveyed isn't ground that can't be reached.
     * One that fails later leaves the last standing, and the decisions go on reading it.
     */
    @Test
    void aFailedSurveyIsTriedAgainOnItsClockAndTheLastOneStands() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
        noWorld = true;

        cycles(chain, 45);

        assertEquals(List.of(0L, 20_000L, 40_000L), surveyedAt.stream().map(at -> at - surveyedAt.getFirst()).toList());
        assertEquals(Optional.empty(), chain.currentOrder("Alpha"), "no order from ground nobody surveyed");

        noWorld = false;
        cycles(chain, 20);
        SquadOrder given = chain.currentOrder("Alpha").orElseThrow();
        int taken = surveyedAt.size();
        noWorld = true;
        long from = clock.millis();
        while (clock.millis() < from + 45_000) {
            cycle(chain, STUCK, Optional.empty());
        }

        assertTrue(surveyedAt.size() - taken <= 3, "tried again every 20 seconds: " + surveyedAt);
        assertEquals(Map.of("Bob", Risk.MEDIUM), chain.currentOrder("Alpha").orElseThrow().soldierRisk(), "decided on the last survey taken");
        assertEquals(given.objective(), chain.currentOrder("Alpha").orElseThrow().objective());
    }

    /** The log of the scenario named, as written so far: for the lines that go nowhere else. */
    private static List<String> logOf(String scenario) throws java.io.IOException {
        return java.nio.file.Files.readAllLines(java.nio.file.Path.of("target/sim/" + scenario + ".log"));
    }

    private static long count(List<String> lines, String containing) {
        return lines.stream().filter(line -> line.contains(containing)).count();
    }

    private void contact(ChainOfCommand chain, boolean bobInContact) {
        cycle(chain, bobInContact ? new Report.InContact(1) : new Report.Moving(), Optional.empty());
    }

    /**
     * A contact that comes and goes each second is one contact (command.SquadContact): reported once, and - on a squad
     * holding with no task, whose rule lets it rearm only out of contact - ordered on twice, at its start and once no
     * soldier has been in contact for the settle time. Without the settle it was a report each time it came and an
     * order each second: five reports and ten orders in these ten seconds.
     */
    @Test
    void aContactThatFlickersEachSecondIsOneContact() throws java.io.IOException {
        String scenario = "aContactThatFlickersEachSecondIsOneContact";
        org.apache.logging.log4j.ThreadContext.put("scenario", scenario);
        try {
            ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
            cycles(chain, 5);
            assertTrue(chain.currentOrder("Alpha").orElseThrow().rearm(), "holding by day, out of contact");
            net.clear();

            for (int second = 0; second < 10; second++) {
                contact(chain, second % 2 == 0);
            }

            assertEquals(1, count(net, "[Alpha -> squad] FRAGO"), "one order, at its start: " + net);
            assertEquals(false, chain.currentOrder("Alpha").orElseThrow().rearm());
            assertEquals(1, count(logOf(scenario), "contact report:"), "one contact report");
            assertEquals(1, count(logOf(scenario), "[PL] wakes:"), "the platoon leader woken for the squad with no objective, and by no contact");

            // Bob was last in contact in the ninth of those seconds: the squad's contact is over ten seconds after that.
            cycles(chain, 8);
            assertEquals(false, chain.currentOrder("Alpha").orElseThrow().rearm(), "not rearmed before the contact has settled");
            assertEquals(1, count(net, "[Alpha -> squad] FRAGO"), "and no order while it settles: " + net);
            cycles(chain, 2);
            assertTrue(chain.currentOrder("Alpha").orElseThrow().rearm(), "rearmed once it has");
            assertEquals(2, count(net, "[Alpha -> squad] FRAGO"), "one order after it: " + net);

            // A contact after that is another contact.
            contact(chain, true);
            assertEquals(false, chain.currentOrder("Alpha").orElseThrow().rearm());
            assertEquals(3, count(net, "[Alpha -> squad] FRAGO"), net.toString());
            assertEquals(2, count(logOf(scenario), "contact report:"), "a second contact, reported");
        } finally {
            org.apache.logging.log4j.ThreadContext.remove("scenario");
        }
    }

    /** A platoon leader by rules that keeps every squad on the first objective, whatever it reports. */
    private static final class Unmoved extends Scripted implements DecidesByRules {
        Unmoved(SimClock clock) {
            super(clock);
        }

        @Override
        public List<Objective> decide(List<SquadStatus> squads) {
            super.decide(squads);
            return squads.stream().map(s -> FIRST).toList();
        }
    }

    /**
     * NOT the wanted answer, today's: with no gap, a squad's task reported done and under way on alternate seconds wakes
     * the platoon leader each time it's done - a decision, its resource surveys, a "holds" line and a feedback warning,
     * five times in ten seconds. Pinned so the number is known; a report that flickers is fixed where it's made, not by a wait.
     */
    @Test
    void aTaskStateThatFlickersEachSecondWakesThePlatoonLeaderEachTime() throws java.io.IOException {
        String scenario = "aTaskStateThatFlickersEachSecondWakesThePlatoonLeaderEachTime";
        org.apache.logging.log4j.ThreadContext.put("scenario", scenario);
        try {
            Unmoved platoonLeader = new Unmoved(clock);
            ChainOfCommand chain = chain(platoonLeader, DrillSquadLeader::new);
            cycles(chain, 5);
            int asked = platoonLeader.askedAt.size();

            for (int second = 0; second < 10; second++) {
                cycle(chain, new Report.Moving(), second % 2 == 0 ? DONE : Optional.empty());
            }

            assertEquals(5, platoonLeader.askedAt.size() - asked, "woken each time the task reads done");
            assertEquals(1 + 5, count(logOf(scenario), "[PL] wakes:"));
            assertEquals(5, count(logOf(scenario), ") holds: "), "a 'holds' line each time");
            assertEquals(5, count(logOf(scenario), "[Feedback]"));
        } finally {
            org.apache.logging.log4j.ThreadContext.remove("scenario");
        }
    }

    /**
     * NOT the wanted answer, today's: a squad leader's CANTCO that comes and goes each second is sent up and wakes the
     * platoon leader each time it comes - five in ten seconds. Pinned so the number is known. A squad leader by rules
     * reports CANTCO off its terrain survey, which is taken every 20 seconds, so its own doesn't flicker this fast.
     */
    @Test
    void aSquadLeadersCantcoThatFlickersEachSecondWakesThePlatoonLeaderEachTime() throws java.io.IOException {
        String scenario = "aSquadLeadersCantcoThatFlickersEachSecondWakesThePlatoonLeaderEachTime";
        org.apache.logging.log4j.ThreadContext.put("scenario", scenario);
        try {
            boolean[] flickering = new boolean[1];
            int[] decided = new int[1];
            final class Wavering implements SquadLeader, DecidesByRules {
                private final SquadLeader drills = new DrillSquadLeader("Alpha");

                @Override
                public String name() {
                    return "Alpha";
                }

                @Override
                public void receiveObjective(Objective objective) {
                    drills.receiveObjective(objective);
                }

                @Override
                public void receiveTime(TimeOfDay time) {
                }

                @Override
                public void receivePlans(Map<String, Plan> plans, Map<String, String> workOrders) {
                }

                @Override
                public Report report() {
                    return flickering[0] && decided[0] % 2 == 1 ? new Report.Cantco("no way over the river") : drills.report();
                }

                @Override
                public Optional<SquadOrder> decide(List<SoldierStatus> soldiers, TerrainSurvey terrain) {
                    decided[0]++;
                    return drills.decide(soldiers, terrain);
                }
            }
            Unmoved platoonLeader = new Unmoved(clock);
            ChainOfCommand chain = chain(platoonLeader, name -> new Wavering());
            cycles(chain, 5);
            int asked = platoonLeader.askedAt.size();
            net.clear();
            decided[0] = 0;
            flickering[0] = true;

            cycles(chain, 10);

            assertEquals(5, count(net, "[Alpha -> PL] CANTCO"), "sent up each time it comes: " + net);
            assertEquals(5, platoonLeader.askedAt.size() - asked, "and the platoon leader woken each time");
            assertEquals(1 + 5, count(logOf(scenario), "[PL] wakes:"));
            assertEquals(0, count(net, "FRAGO"), "the order itself stands: " + net);
        } finally {
            org.apache.logging.log4j.ThreadContext.remove("scenario");
        }
    }

    /** A rule that throws fails that decision alone (docs/conventions.md, "No fallbacks"): the next SITREP asks again. */
    @Test
    void aRuleThatThrowsIsAskedAgainAtTheNextSitrep() {
        List<Long> askedAt = new ArrayList<>();
        final class Faulty implements SquadLeader, DecidesByRules {
            private final SquadLeader drills = new DrillSquadLeader("Alpha");

            @Override
            public String name() {
                return "Alpha";
            }

            @Override
            public void receiveObjective(Objective objective) {
                drills.receiveObjective(objective);
            }

            @Override
            public void receiveTime(TimeOfDay time) {
            }

            @Override
            public void receivePlans(Map<String, Plan> plans, Map<String, String> workOrders) {
            }

            @Override
            public Report report() {
                return drills.report();
            }

            @Override
            public Optional<SquadOrder> decide(List<SoldierStatus> soldiers, TerrainSurvey terrain) {
                askedAt.add(clock.millis());
                if (askedAt.size() <= 3) {
                    throw new IllegalStateException("a rule's bug");
                }
                return drills.decide(soldiers, terrain);
            }
        }
        ChainOfCommand chain = chain(new Ruled(clock), name -> new Faulty());

        while (askedAt.size() < 3) {
            cycles(chain, 1);
        }
        assertEquals(Optional.empty(), chain.currentOrder("Alpha"), "no order from a decision that failed");
        cycles(chain, 1);

        assertTrue(chain.currentOrder("Alpha").isPresent(), "the fourth SITREP's decision stands");
        assertEquals(List.of(0L, SITREP_MILLIS, 2 * SITREP_MILLIS, 3 * SITREP_MILLIS), askedAt.stream().map(at -> at - askedAt.getFirst()).toList());
    }

    /**
     * A rule decides every second, but the flood of the ground it reads is the costliest thing a decision asks for: that
     * is taken on its own clock - every 20 seconds for a squad that stays where it is - and never in the squad loop.
     */
    @Test
    void aRulesDecisionsReadASurveyTakenOnItsOwnClock() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);

        cycles(chain, 60);

        assertTrue(surveyedAt.size() <= 4, "a minute of decisions a second, on at most four surveys: " + surveyedAt);
        assertEquals(List.of(0L, 20_000L, 40_000L), surveyedAt.stream().map(at -> at - surveyedAt.getFirst()).toList());
        assertEquals(0, surveyedInTheSquadLoop, "taken on the squad leader's thread");
        assertTrue(chain.currentOrder("Alpha").isPresent());
    }

    /** Sooner than its clock only for what changes the ground to read: the point moved half a survey's radius, a new objective. */
    @Test
    void aSurveyIsTakenSoonerWhenThePointHasMovedOrTheObjectiveChanged() {
        ChainOfCommand chain = chain(new Ruled(clock), DrillSquadLeader::new);
        cycles(chain, 3);
        assertEquals(1, surveyedAt.size());

        // The move is measured to the soldier nearest the objective - Bob, 2 on, then Alice once past it - from where the
        // survey was taken, Alice's column, and from where that soldier stood when it was asked for: sixteen from both.
        for (int x = 1; x <= 12; x++) {
            cycle(chain, x, new Report.Moving(), Optional.empty());
        }
        assertEquals(1, surveyedAt.size(), "under sixteen blocks on: the one in hand still shows the ground ahead");
        for (int x = 13; x <= 19; x++) {
            cycle(chain, x, new Report.Moving(), Optional.empty());
        }
        assertEquals(2, surveyedAt.size(), "sixteen blocks from where it was taken, and once");

        cycle(chain, 19, new Report.Moving(), DONE);
        cycle(chain, 19, new Report.Moving(), DONE);
        assertEquals(Optional.of(NEXT), chain.objective("Alpha"));
        assertEquals(3, surveyedAt.size(), "for the new objective");
        assertEquals(0, surveyedInTheSquadLoop);
    }

    /** A model squad leader's survey, as it was: its own, each time it decides, on its own thread. */
    @Test
    void aModelsDecisionTakesItsOwnSurvey() {
        List<Long> askedAt = new ArrayList<>();
        SquadLeaderPrompts prompts = SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                new ClassPathResource("prompts/squad-leader-user-prompt.md"));
        ChainOfCommand chain = new ChainOfCommand(new Scripted(clock), model(askedAt), prompts, new Roster("Alpha:Alice,Bob"), false, clock);
        chain.onTime(TimeOfDay.of(1000, true));
        cycles(chain, 4);

        for (int i = 0; i < 40; i++) {
            cycle(chain, STUCK, Optional.empty());
        }

        assertEquals(3, askedAt.size());
        assertEquals(askedAt, surveyedAt, "one survey a decision, when it's made");
        assertEquals(0, surveyedInTheSquadLoop);
    }

    /** A model squad leader's waits, as they were: its look every 20 seconds, and 8 seconds from its last decision to one on a CANTCO. */
    @Test
    void aModelSquadLeaderKeepsItsWaits() {
        List<Long> askedAt = new ArrayList<>();
        SquadLeaderPrompts prompts = SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                new ClassPathResource("prompts/squad-leader-user-prompt.md"));
        ChainOfCommand chain = new ChainOfCommand(new Scripted(clock), model(askedAt), prompts, new Roster("Alpha:Alice,Bob"), false, clock);
        chain.onTime(TimeOfDay.of(1000, true));
        cycles(chain, 4);
        assertEquals(1, askedAt.size(), "asked on its objective");

        for (int i = 0; i < 40; i++) {
            cycle(chain, STUCK, Optional.empty());
        }

        assertEquals(List.of(0L, 8_000L, 28_000L), askedAt.stream().map(at -> at - askedAt.getFirst()).toList(),
                "on its objective; on the CANTCO, 8 s after; then its look, 20 s after that");
    }

    /**
     * A model squad leader's contact report, as it was: a soldier in contact who wasn't at its last decision. One in
     * contact at that decision, out of it past the settle time and in again is no new report to it - its look comes round.
     */
    @Test
    void aModelSquadLeadersContactReportIsAsItWas() {
        List<Long> askedAt = new ArrayList<>();
        SquadLeaderPrompts prompts = SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                new ClassPathResource("prompts/squad-leader-user-prompt.md"));
        ChainOfCommand chain = new ChainOfCommand(new Scripted(clock), model(askedAt), prompts, new Roster("Alpha:Alice,Bob"), false, clock);
        chain.onTime(TimeOfDay.of(1000, true));
        cycles(chain, 4);
        assertEquals(1, askedAt.size(), "asked on its objective");

        for (int i = 0; i < 40; i++) {
            contact(chain, i < 10 || i >= 22);
        }

        assertEquals(List.of(0L, 8_000L, 28_000L), askedAt.stream().map(at -> at - askedAt.getFirst()).toList(),
                "on its objective; on the contact, 8 s after; then its look - not on the contact's return");
    }

    /** A model platoon leader's wait, as it was: a request wakes it a minute after its last decision, not before. */
    @Test
    void aModelPlatoonLeaderKeepsItsWait() {
        Scripted platoonLeader = new Scripted(clock);
        ChainOfCommand chain = chain(platoonLeader, DrillSquadLeader::new);
        cycles(chain, 5);
        assertEquals(1, platoonLeader.askedAt.size(), "asked for the squad with no objective");

        for (int i = 0; i < 70; i++) {
            cycle(chain, new Report.Moving(), DONE);
        }

        assertEquals(List.of(0L, 60_000L), platoonLeader.askedAt.stream().map(at -> at - platoonLeader.askedAt.getFirst()).toList());
        assertEquals(Optional.of(NEXT), chain.objective("Alpha"));
    }
}
