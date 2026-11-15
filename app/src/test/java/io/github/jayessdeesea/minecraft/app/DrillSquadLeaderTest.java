package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.app.SquadLeaderScenarios.Outcome;
import io.github.jayessdeesea.minecraft.app.SquadLeaderScenarios.Variant;
import io.github.jayessdeesea.minecraft.bot.scenario.Scenario;
import io.github.jayessdeesea.minecraft.bot.scenario.Terrain;
import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The squad leader's situations - every terrain family, three seeds, every variant - judged for the
 * drills as {@link SquadLeaderScenarioIT} judges the model (backlog/fleet-command.md: its situations
 * become the allocator's tests). Deterministic, so it's a unit test, asserted rather than rated.
 *
 * <p>Two variants are the platoon leader's, not the drills': a soldier detailed in the mission's
 * words - "detail one soldier to mine", "post Bob as a lookout" - is a detachment the drills don't
 * guess at from prose, so there they change no plan, and the platoon leader gives it as data.
 */
class DrillSquadLeaderTest {

    @Test
    void everySituationPassesButTheDetachmentsAskedForInWords() {
        List<String> failed = new ArrayList<>();
        for (Terrain terrain : Terrain.values()) {
            for (long seed = 1; seed <= 3; seed++) {
                for (Variant variant : Variant.values()) {
                    Outcome outcome = SquadLeaderScenarios.run(new DrillSquadLeader("Alpha"), Scenario.generate(terrain, seed), variant);
                    List<String> failures = outcome.failures();
                    boolean inWords = variant == Variant.MINER || variant == Variant.POST;
                    if (inWords ? failures.stream().anyMatch(f -> !f.startsWith("not one MINER") && !f.startsWith("Bob not posted")) : !failures.isEmpty()) {
                        failed.add(outcome.name() + ": " + failures);
                    }
                }
            }
        }
        assertEquals(List.of(), failed);
    }
}
