package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndermanGazeTest {

    private static final Position EYE = new Position(0.5, 65.62, 0.5);

    /** Facing south, level: an enderman 10 south, its eyes a block over the soldier's, is met; one off to the side isn't. */
    @Test
    void aSoldierFacingAnEndermansEyesLooksDown() {
        Position ahead = new Position(0.5, 64, 10.5);
        float yaw = 0f; // south
        float atEyes = (float) -Math.toDegrees(Math.atan2(64 + EndermanGaze.EYE_HEIGHT - EYE.y(), 10));
        assertTrue(EndermanGaze.meets(EYE, yaw, atEyes, ahead));
        assertEquals(EndermanGaze.DOWN, EndermanGaze.pitch(EYE, yaw, atEyes, List.of(ahead)));
        Position aside = new Position(10.5, 64, 0.5);
        assertFalse(EndermanGaze.meets(EYE, yaw, atEyes, aside));
        assertEquals(atEyes, EndermanGaze.pitch(EYE, yaw, atEyes, List.of(aside)));
    }

    @Test
    void oneBeyondItsSightIsntMet() {
        Position far = new Position(0.5, 64, 90.5);
        float atEyes = (float) -Math.toDegrees(Math.atan2(64 + EndermanGaze.EYE_HEIGHT - EYE.y(), 90));
        assertFalse(EndermanGaze.meets(EYE, 0f, atEyes, far));
    }
}
