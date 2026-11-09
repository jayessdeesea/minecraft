package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SpringAiGeneral}, the same way {@code SpringAiSquadLeaderTest} covers the squad leader. */
class SpringAiGeneralTest {

    private static final SquadStatus ALPHA = new SquadStatus("Alpha", new Report.Cantco("ravine in the way"), List.of(
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Idle(), java.util.Optional.empty())));
    private static final SquadStatus BRAVO = new SquadStatus("Bravo", new Report.Moving(), List.of(
            new SoldierStatus("Bob", new Position(3, 64, 4), new Order.Idle(), new Report.Moving(), java.util.Optional.empty())));

    private static GeneralReply.ObjectiveReply objective(String squad) {
        return new GeneralReply.ObjectiveReply(squad, false, "", GeneralReply.Task.NONE, "scout", 0, 64, -50, "find the village");
    }

    private static final io.github.jayessdeesea.minecraft.command.Objective BASE = new io.github.jayessdeesea.minecraft.command.Objective(
            "Build a shelter", new Position(10, 64, 10), "safe until dawn", java.util.Optional.empty(), true);

    /** Bravo, sealed in the patrol base it was ordered to build. */
    private static final SquadStatus BRAVO_IN_BASE = new SquadStatus("Bravo", new Report.InPosition(), BRAVO.soldiers(), List.of(),
            java.util.Optional.of(BASE), true);

    @Test
    void theGeneralSeesEachSquadsStandingOrderAndHowItsGoing() {
        String prompt = SpringAiGeneral.buildContext(Prompts.GENERAL, "hold", java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), List.of(ALPHA, BRAVO_IN_BASE)).prompt();

        assertTrue(prompt.contains("Standing order: none yet - there is nothing to keep"), prompt);
        assertTrue(prompt.contains("Standing order: PATROL_BASE - \"Build a shelter\"; its ground x=10 z=10, about level with the squad. Intent: safe until dawn "
                + "Status: the patrol base is built and sealed, the squad inside it."), prompt);
    }

    @Test
    void keepingAnOrderKeepsItExactly() {
        GeneralReply reply = new GeneralReply(List.of(
                new GeneralReply.ObjectiveReply("Bravo", true, "", GeneralReply.Task.NONE, "wander", 99, 70, 99, "whatever")));

        assertEquals(List.of(BASE), SpringAiGeneral.toObjectives(reply, List.of(BRAVO_IN_BASE)));
    }

    @Test
    void changingAnOrderWithoutAReasonIsRejected() {
        GeneralReply reply = new GeneralReply(List.of(objective("Bravo")));

        assertThrows(IllegalStateException.class, () -> SpringAiGeneral.toObjectives(reply, List.of(BRAVO_IN_BASE)));
    }

    @Test
    void keepingAnOrderThatWasNeverGivenIsRejected() {
        GeneralReply reply = new GeneralReply(List.of(
                new GeneralReply.ObjectiveReply("Bravo", true, "", GeneralReply.Task.NONE, "scout", 0, 64, 0, "look")));

        assertThrows(IllegalStateException.class, () -> SpringAiGeneral.toObjectives(reply, List.of(BRAVO)));
    }

    @Test
    void decideReturnsNoObjectivesForAnEmptySquadList() {
        SpringAiGeneral general = new SpringAiGeneral(ChatClient.create(FakeChatModels.THROWING), Prompts.GENERAL, "win", "");

        assertEquals(List.of(), general.decide(List.of()));
    }

    @Test
    void buildContextGivesTheMissionAndEachSquadsReports() {
        String prompt = SpringAiGeneral.buildContext(Prompts.GENERAL, "secure the village", java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), List.of(ALPHA, BRAVO)).prompt();

        assertTrue(prompt.contains("<mission>\nsecure the village\n</mission>"));
        assertTrue(prompt.contains("<squad name=\"Alpha\">\nStanding order: none yet - there is nothing to keep: give it an objective, with \"keep\": false.\nSquad leader reports: Cantco: ravine in the way"));
        assertTrue(prompt.contains("- Bob at (3, 64, 4): Moving"));
        assertTrue(prompt.contains("LOGSTAT: no soldier has reported his condition yet."));
        assertTrue(!prompt.contains("<supply_point>"));
        assertTrue(!prompt.contains("<time>"));
    }

    @Test
    void buildContextSaysHowLongUntilDark() {
        String prompt = SpringAiGeneral.buildContext(Prompts.GENERAL, "secure the village",
                java.util.Optional.of(io.github.jayessdeesea.minecraft.command.TimeOfDay.of(1000, true)),
                java.util.Optional.empty(), java.util.Optional.empty(), List.of(ALPHA)).prompt();

        assertTrue(prompt.contains("<time>\nDay. Dark - monsters spawn anywhere unlit - in about 10 minutes.\n</time>"), prompt);
        assertEquals("Night - already dark: monsters spawn anywhere unlit. Light again in about 5 minutes.",
                SpringAiGeneral.describe(io.github.jayessdeesea.minecraft.command.TimeOfDay.of(18000, true)));
        assertEquals("Day. The clock is stopped: it stays this way.",
                SpringAiGeneral.describe(io.github.jayessdeesea.minecraft.command.TimeOfDay.of(6000, false)));
    }

    @Test
    void buildContextShowsTheSupplyPointWhenThereIsOne() {
        String prompt = SpringAiGeneral.buildContext(Prompts.GENERAL, "secure the village", java.util.Optional.empty(),
                java.util.Optional.of(new Position(10, 70, -5)),
                java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Stockpile(40, 64)), List.of(ALPHA)).prompt();

        assertTrue(prompt.contains("<supply_point>\nA chest stocked with weapons and armour at (10, 70, -5)."));
        assertTrue(prompt.contains("Last seen holding 40 logs and 64 cobblestone."));
    }

    @Test
    void resourcesSeenAreGivenWithDistanceAndDirection() {
        SquadStatus squad = new SquadStatus("Bravo", new Report.Moving(), List.of(
                new SoldierStatus("Dana", new Position(0, 64, 0), new Order.Idle(), new Report.Moving(), java.util.Optional.empty())),
                List.of(new io.github.jayessdeesea.minecraft.command.ResourceSite(io.github.jayessdeesea.minecraft.command.Resource.WOOD,
                        new Position(30, 66, -40), 23)));

        assertEquals("Resources seen: 23 logs around x=30 z=-40, about level with the squad (50 blocks NE).",
                SpringAiGeneral.describeResources(squad));
        SquadStatus onAPlatform = new SquadStatus("Bravo", new Report.Moving(), List.of(
                new SoldierStatus("Dana", new Position(0, 151, 0), new Order.Idle(), new Report.Moving(), java.util.Optional.empty())),
                squad.resources());
        assertTrue(SpringAiGeneral.describeResources(onAPlatform).contains("around x=30 z=-40, 85 below the squad (50 blocks NE)"));
        assertTrue(SpringAiGeneral.describeResources(new SquadStatus("Alpha", new Report.Idle(), List.of()))
                .contains("no trees, exposed stone or ground to farm"));
    }

    @Test
    void logstatSaysWhatTheSquadIsShortOf() {
        assertEquals("LOGSTAT: food - 1 of 3 hungry (not regenerating health), 0 rations carried; weapons - 1 of 2 bare-handed, 18 arrows; "
                        + "armour - 3 of 8 slots empty; casualties - 1 below half health, 3 deaths so far; materials - 0 logs, 0 cobblestone"
                        + " - 40 short of the 40 blocks a patrol base takes; set PATROL_BASE anyway when it needs shelter: its soldiers dig the rest as dirt beside the site.",
                SpringAiGeneral.describe(new io.github.jayessdeesea.minecraft.command.Logstat(4, 3, 1, 1, 3, 2, 1, 3, 8, 18, 0, 0)));
        assertEquals("LOGSTAT: food - 0 of 1 hungry (not regenerating health); weapons and armour not reported yet; "
                        + "casualties - 0 below half health, 0 deaths so far; materials - 0 logs, 0 cobblestone"
                        + " - 40 short of the 40 blocks a patrol base takes; set PATROL_BASE anyway when it needs shelter: its soldiers dig the rest as dirt beside the site.",
                SpringAiGeneral.describe(new io.github.jayessdeesea.minecraft.command.Logstat(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertTrue(SpringAiGeneral.describe(new io.github.jayessdeesea.minecraft.command.Logstat(3, 3, 0, 0, 0, 3, 0, 0, 12, 0, 2, 46))
                .endsWith("materials - 2 logs, 46 cobblestone - enough to build a patrol base (40 blocks)."));
    }

    @Test
    void aSquadHoldingItsBaseIsntToldItsShortOfOne() {
        io.github.jayessdeesea.minecraft.command.Logstat spent = new io.github.jayessdeesea.minecraft.command.Logstat(4, 4, 0, 0, 0, 4, 0, 0, 16, 0, 0, 3);

        assertTrue(SpringAiGeneral.describe(spent, java.util.OptionalInt.of(0))
                .endsWith("materials - 0 logs, 3 cobblestone - their patrol base is already built."));
        assertTrue(SpringAiGeneral.describe(spent, java.util.OptionalInt.empty()).contains("short of the 40 blocks"));
    }

    @Test
    void aSquadPartWayThroughItsBaseIsMeasuredAgainstWhatsLeft() {
        io.github.jayessdeesea.minecraft.command.Logstat carrying = new io.github.jayessdeesea.minecraft.command.Logstat(4, 4, 0, 0, 0, 4, 0, 0, 16, 0, 0, 24);

        assertTrue(SpringAiGeneral.describe(carrying, java.util.OptionalInt.of(20))
                .endsWith("materials - 0 logs, 24 cobblestone - enough for the 20 blocks of their patrol base still to place."));
        SquadStatus building = new SquadStatus("Bravo", new Report.Moving(), BRAVO.soldiers(), List.of(), java.util.Optional.of(BASE), false,
                java.util.OptionalInt.of(20));
        assertTrue(SpringAiGeneral.describeStanding(building).endsWith("Status: the patrol base is laid out, 20 blocks of it still to place."));
    }

    @Test
    void aFinishedOrStalledTaskSaysSoInTheStandingOrder() {
        Objective mine = new Objective("cut the mine", new Position(0, 16, 0), "iron", new Objective.Task.Mine());
        SquadStatus done = new SquadStatus("Bravo", new Report.InPosition(), BRAVO.soldiers(), List.of(), java.util.Optional.of(mine), false,
                java.util.OptionalInt.empty(), java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Progress(
                        io.github.jayessdeesea.minecraft.command.Progress.State.DONE, "the stairs are cut down to y=16")));
        assertTrue(SpringAiGeneral.describeStanding(done).endsWith(
                "Status: DONE - the stairs are cut down to y=16. Nothing is left to do here: give the squad its next task."));
        SquadStatus stalled = new SquadStatus("Bravo", new Report.InPosition(), BRAVO.soldiers(), List.of(), java.util.Optional.of(mine), false,
                java.util.OptionalInt.empty(), java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Progress(
                        io.github.jayessdeesea.minecraft.command.Progress.State.STALLED, "nobody has worked at it for 4 min")));
        assertTrue(SpringAiGeneral.describeStanding(stalled).contains("Status: STALLED - nobody has worked at it for 4 min."));
    }

    @Test
    void decideRoundTripsStructuredOutputInSquadOrder() {
        String reply = """
                {"objectives": [
                  {"squad": "Bravo", "keep": false, "reason": "", "mission": "hold", "x": 5, "y": 64, "z": 5, "intent": "cover Alpha", "task": "WOOD"},
                  {"squad": "Alpha", "keep": false, "reason": "", "mission": "scout north", "x": 0, "y": 70, "z": -60, "intent": "find a way around", "task": "NONE"}
                ]}
                """;
        SpringAiGeneral general = new SpringAiGeneral(ChatClient.create(FakeChatModels.replying(reply, new AtomicReference<>())), Prompts.GENERAL, "win", "");

        List<Objective> objectives = general.decide(List.of(ALPHA, BRAVO));

        assertEquals(List.of(
                new Objective("scout north", new Position(0, 70, -60), "find a way around"),
                new Objective("hold", new Position(5, 64, 5), "cover Alpha",
                        java.util.Optional.of(io.github.jayessdeesea.minecraft.command.Resource.WOOD))), objectives);
    }

    @Test
    void validationRequiresExactlyOneObjectivePerListedSquad() {
        List<SquadStatus> squads = List.of(ALPHA, BRAVO);
        assertThrows(IllegalStateException.class,
                () -> SpringAiGeneral.toObjectives(new GeneralReply(List.of(objective("Alpha"))), squads));
        assertThrows(IllegalStateException.class,
                () -> SpringAiGeneral.toObjectives(new GeneralReply(List.of(objective("Alpha"), objective("Alpha"), objective("Bravo"))), squads));
        assertThrows(IllegalStateException.class,
                () -> SpringAiGeneral.toObjectives(new GeneralReply(List.of(objective("Alpha"), objective("Bravo"), objective("Charlie"))), squads));
        assertEquals(2, SpringAiGeneral.toObjectives(new GeneralReply(List.of(objective("Bravo"), objective("Alpha"))), squads).size());
    }
}
