package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry of stuck soldiers (operator, 2026-11-20: any time a soldier is stuck, an eval of what correct is there).
 * Each case under {@code stuck/} is a live failure: the ground round the soldier as recorded (app.WorldRecords), where it
 * stood, what it carried, where it was going and what correct is - replayed in the squad simulation. A case not fixed
 * yet is a known failure: reported, not failed. One that passes while still marked known fails, so the mark comes off
 * with the fix and the case holds it from then on.
 */
class StuckEvalsTest {

    @TempDir
    Path state;

    @TestFactory
    Stream<DynamicTest> everyStuckSoldiersCase() throws IOException {
        Resource[] cases = new PathMatchingResourcePatternResolver().getResources("classpath:stuck/*.json");
        assertTrue(cases.length > 0, "no cases under stuck/");
        return Arrays.stream(cases).map(r -> DynamicTest.dynamicTest(r.getFilename(), () -> run(r)));
    }

    private void run(Resource resource) throws Exception {
        String name = resource.getFilename().replace(".json", "");
        StuckCase c = new ObjectMapper().readValue(resource.getInputStream(), StuckCase.class);
        ThreadContext.put("scenario", "stuck-" + name);
        try {
            WorldRecords.Snapshot ground = WorldRecords.load(Path.of(new ClassPathResource(c.world()).getURI()));
            SimClock clock = new SimClock();
            SimServer server = new SimServer(clock, SimWorlds.replay(ground), 1000);
            Roster roster = new Roster("Alpha:" + c.soldier());
            Position goal = new Position(c.goal()[0], c.goal()[1], c.goal()[2]);
            ChainOfCommand chain = new ChainOfCommand(SquadSimulationTest.scripted(s -> new Objective("The case's goal", goal, "There",
                    new Objective.Task.None())), RoutingSimulationTest.squadLeader(goal),
                    SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                            new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
            Path dir = Files.createDirectories(state.resolve(name));
            new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", dir.toString())
                    .simulated(clock, server.connector(new PlayerPosition(c.at()[0], c.at()[1], c.at()[2]),
                            c.kit().stream().map(i -> new int[]{i.slot(), i.id(), i.count()}).toList()))
                    .run();

            clock.advance(c.withinMinutes() * 60_000L);

            server.dumpLog("stuck-" + name);
            PlayerPosition p = server.position(c.soldier());
            boolean correct = Math.hypot(p.x() - goal.x(), p.z() - goal.z()) <= c.radius() && Math.abs(p.y() - goal.y()) <= 2;
            System.out.printf(Locale.ROOT, "[Eval] stuck/%s: %s%s - %s at (%.1f, %.1f, %.1f), goal (%.1f, %.1f, %.1f)%n", name,
                    correct ? "PASS" : "FAIL", c.knownFailure() ? " (known)" : "", c.soldier(), p.x(), p.y(), p.z(), goal.x(), goal.y(), goal.z());
            if (c.knownFailure()) {
                assertFalse(correct, "stuck/" + name + " passes now: take its knownFailure mark off, so it holds the fix");
            } else {
                assertTrue(correct, "stuck/" + name + ": " + c.correct() + " - " + c.why() + "; at " + p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ThreadContext.remove("scenario");
        }
    }
}
