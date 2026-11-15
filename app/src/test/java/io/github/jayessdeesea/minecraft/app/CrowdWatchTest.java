package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowdWatchTest {

    private static final Position HERE = new Position(0.5, 16, 0.5);

    /** Two in one cell half a minute, moving about in it, alert once; apart, it's said they have room. */
    @Test
    void twoInOneCellHalfAMinuteAlertOnceThenHaveRoom() {
        CrowdWatch watch = new CrowdWatch();
        Optional<String> line = Optional.empty();
        for (long t = 0; t <= CrowdWatch.CROWD_MILLIS; t += 1_000) {
            Position jiggle = new Position(0.5 + (t % 2_000 == 0 ? 0.2 : -0.2), 16, 0.5);
            Optional<String> said = watch.update("Alice", jiggle, t, false, Map.of("Bob", HERE), () -> "cutting");
            if (said.isPresent()) {
                assertTrue(line.isEmpty(), "once a spell");
                line = said;
            }
        }
        assertTrue(line.orElseThrow().startsWith("Alice crowded 30 s within a body's width of Bob"), line.get());
        assertTrue(watch.update("Alice", HERE, CrowdWatch.CROWD_MILLIS + 2_000, false, Map.of("Bob", HERE), () -> "").isEmpty());
        assertEquals("Alice has room again after 32 s",
                watch.update("Alice", new Position(3.5, 16, 0.5), CrowdWatch.CROWD_MILLIS + 2_000, false, Map.of("Bob", HERE), () -> "").orElseThrow());
    }

    /** A squad packed into its patrol base is resting, not crowding; nor is one a block above another's head. */
    @Test
    void restingOrAStoreyApartIsNoCrowd() {
        CrowdWatch watch = new CrowdWatch();
        for (long t = 0; t <= 2 * CrowdWatch.CROWD_MILLIS; t += 1_000) {
            assertTrue(watch.update("Alice", HERE, t, true, Map.of("Bob", HERE), () -> "").isEmpty());
            assertTrue(watch.update("Carol", HERE, t, false, Map.of("Dana", new Position(0.5, 18, 0.5)), () -> "").isEmpty());
        }
    }
}
