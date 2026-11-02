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
        return new GeneralReply.ObjectiveReply(squad, "scout", 0, 64, -50, "find the village");
    }

    @Test
    void decideReturnsNoObjectivesForAnEmptySquadList() {
        SpringAiGeneral general = new SpringAiGeneral(ChatClient.create(FakeChatModels.THROWING), "win");

        assertEquals(List.of(), general.decide(List.of()));
    }

    @Test
    void buildContextGivesTheMissionAndEachSquadsReports() {
        String prompt = SpringAiGeneral.buildContext("secure the village", List.of(ALPHA, BRAVO)).prompt();

        assertTrue(prompt.contains("<mission>\nsecure the village\n</mission>"));
        assertTrue(prompt.contains("<squad name=\"Alpha\">\nSquad leader reports: Cantco: ravine in the way"));
        assertTrue(prompt.contains("- Bob at (3, 64, 4): Moving"));
        assertTrue(prompt.contains("LOGSTAT: no soldier has reported his condition yet."));
    }

    @Test
    void logstatSaysWhatTheSquadIsShortOf() {
        assertEquals("LOGSTAT: food - 1 of 3 hungry (not regenerating health); weapons - 1 of 2 bare-handed, 18 arrows; "
                        + "armour - 3 of 8 slots empty; casualties - 1 below half health, 3 deaths so far.",
                SpringAiGeneral.describe(new io.github.jayessdeesea.minecraft.command.Logstat(4, 3, 1, 1, 3, 2, 1, 3, 8, 18)));
        assertEquals("LOGSTAT: food - 0 of 1 hungry (not regenerating health); weapons and armour not reported yet; "
                        + "casualties - 0 below half health, 0 deaths so far.",
                SpringAiGeneral.describe(new io.github.jayessdeesea.minecraft.command.Logstat(1, 1, 0, 0, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void decideRoundTripsStructuredOutputInSquadOrder() {
        String reply = """
                {"objectives": [
                  {"squad": "Bravo", "mission": "hold", "x": 5, "y": 64, "z": 5, "intent": "cover Alpha"},
                  {"squad": "Alpha", "mission": "scout north", "x": 0, "y": 70, "z": -60, "intent": "find a way around"}
                ]}
                """;
        SpringAiGeneral general = new SpringAiGeneral(ChatClient.create(FakeChatModels.replying(reply, new AtomicReference<>())), "win");

        List<Objective> objectives = general.decide(List.of(ALPHA, BRAVO));

        assertEquals(List.of(
                new Objective("scout north", new Position(0, 70, -60), "find a way around"),
                new Objective("hold", new Position(5, 64, 5), "cover Alpha")), objectives);
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
