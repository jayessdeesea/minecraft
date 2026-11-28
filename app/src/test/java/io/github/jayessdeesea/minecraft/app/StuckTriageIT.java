package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
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
 * A triage of the stuck soldiers the garrison has drafted cases for (operator, 2026-11-23: figure out why something is
 * unreachable - mobility-wise everything should be reachable): the newest {@link #CASES} under
 * {@code garrison-state/worlds/} - each the ground round a soldier that stood stuck, where it was and where it was going
 * - replayed with that soldier alone, and a table written of which reach their goal. One that doesn't, alone on its own
 * recorded ground, is a mobility fault to fix and register under {@code stuck/}; one that does was held by something
 * else - a crowd, its tasking, a block another soldier had put there. Not part of the suite: run it by name,
 * {@code mvn -o test -pl app -am -Dtest=StuckTriageIT -Dsurefire.failIfNoSpecifiedTests=false}; its table is
 * {@code app/target/stuck-triage.txt}.
 */
class StuckTriageIT {

    /** How many of the newest cases are replayed. */
    static final int CASES = 40;

    @TempDir
    Path state;

    @org.junit.jupiter.api.Test
    void theNewestDraftedCasesReplayed() throws IOException {
        Path worlds = Path.of("..", "garrison-state", "worlds");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(worlds), "no garrison-state/worlds here");
        java.util.List<Path> cases;
        try (Stream<Path> files = Files.list(worlds)) {
            cases = files.filter(f -> f.getFileName().toString().endsWith(".case.json"))
                    .sorted(java.util.Comparator.comparingLong((Path f) -> f.toFile().lastModified()).reversed())
                    .limit(CASES).toList();
        }
        java.util.List<String> table = new java.util.ArrayList<>();
        int reached = 0, beyond = 0;
        for (Path file : cases) {
            String name = file.getFileName().toString().replace(".case.json", "");
            StuckCase c = new ObjectMapper().readValue(file.toFile(), StuckCase.class);
            ThreadContext.put("scenario", "triage-" + name);
            try {
                WorldRecords.Snapshot ground = WorldRecords.load(worlds.getParent().resolve(c.world()));
                SimClock clock = new SimClock();
                SimServer server = new SimServer(clock, SimWorlds.replay(ground), 1000);
                Roster roster = new Roster("Alpha:" + c.soldier());
                Position goal = new Position(c.goal()[0], c.goal()[1], c.goal()[2]);
                Path dir = Files.createDirectories(state.resolve(name));
                SimSquad.of(clock, server, roster)
                        .orders(s -> new Objective("The case's goal", goal, "There",
                        new Objective.Task.None())).leader(RoutingSimulationTest.squadLeader(goal)).home("")
                        .connector(new PlayerPosition(c.at()[0], c.at()[1], c.at()[2]),
                                c.kit().stream().map(i -> new int[]{i.slot(), i.id(), i.count()}).toList())
                        .start(dir);
                clock.advance(c.withinMinutes() * 60_000L);
                PlayerPosition p = server.position(c.soldier());
                boolean correct = Math.hypot(p.x() - goal.x(), p.z() - goal.z()) <= c.radius() && Math.abs(p.y() - goal.y()) <= 2;
                // A goal past the edge of the ground kept can't be walked to in the replay, whatever the soldier can do: said apart.
                boolean offTheGround = goal.x() < ground.minX() || goal.x() >= ground.minX() + ground.sizeX() || goal.z() < ground.minZ() || goal.z() >= ground.minZ() + ground.sizeZ()
                        || goal.y() < ground.minY() || goal.y() >= ground.minY() + ground.sizeY();
                reached += correct ? 1 : 0;
                beyond += !correct && offTheGround ? 1 : 0;
                table.add(String.format(Locale.ROOT, "%s %s from (%.0f, %.0f, %.0f) to (%.0f, %.0f, %.0f), %.0f off; ended (%.1f, %.1f, %.1f), %.1f short - %s",
                        correct ? "REACHED" : offTheGround ? "BEYOND " : "STUCK  ", name, c.at()[0], c.at()[1], c.at()[2], goal.x(), goal.y(), goal.z(),
                        Math.hypot(c.at()[0] - goal.x(), c.at()[2] - goal.z()), p.x(), p.y(), p.z(), Math.hypot(p.x() - goal.x(), p.z() - goal.z()),
                        c.why().length() > 150 ? c.why().substring(0, 150) : c.why()));
            } catch (RuntimeException e) {
                table.add("ERROR   " + name + " - " + e);
            } finally {
                ThreadContext.remove("scenario");
            }
        }
        table.add(0, reached + " of " + cases.size() + " reach their goal alone on their recorded ground; " + beyond + " more have a goal beyond it (BEYOND: not a verdict); "
                + (cases.size() - reached - beyond) + " are STUCK alone - a fault of the soldier's own, or of the simulated server's");
        Files.write(Path.of("target", "stuck-triage.txt"), table);
        table.forEach(System.out::println);
    }
}
