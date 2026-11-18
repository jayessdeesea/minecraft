package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;

/**
 * Never meeting an enderman's eye (operator, 2026-11-17: engage if close, do not provoke): an enderman
 * turns on a player whose view lies along the line to its eyes - vanilla's {@code isLookingAtMe}, the view
 * vector's dot with the direction to its eyes over {@code 1 - 0.025 / distance}. A soldier's facing as
 * it's sent to the server is turned down off any enderman's eyes it would meet, with a margin wide of
 * vanilla's. Live (field412-413), endermen killed seven miners at the mine's base, none striking back.
 */
public final class EndermanGaze {

    /** An enderman's eyes over its feet. */
    static final double EYE_HEIGHT = 2.55;
    /** How much wider than vanilla's test the facing stays off its eyes. */
    static final double MARGIN = 4;
    /** How far an enderman sees a player look at it. */
    static final double SIGHT = 64;
    /** The pitch looked down to, off an enderman's eyes: the ground at its feet. */
    static final float DOWN = 60f;

    private EndermanGaze() {
    }

    /**
     * The pitch to send for a soldier with eyes at {@code eye} facing ({@code yaw}, {@code pitch}) with
     * endermen at {@code endermen}: {@code pitch} itself unless that meets one's eyes, else {@link #DOWN}.
     */
    public static float pitch(Position eye, float yaw, float pitch, List<Position> endermen) {
        return endermen.stream().anyMatch(e -> meets(eye, yaw, pitch, e)) ? DOWN : pitch;
    }

    /** Whether the view ({@code yaw}, {@code pitch}) from {@code eye} lies along the line to the eyes of the enderman at {@code feet}. */
    static boolean meets(Position eye, float yaw, float pitch, Position feet) {
        double dx = feet.x() - eye.x(), dy = feet.y() + EYE_HEIGHT - eye.y(), dz = feet.z() - eye.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6 || distance > SIGHT) {
            return false;
        }
        double yawR = Math.toRadians(yaw), pitchR = Math.toRadians(pitch);
        double vx = -Math.sin(yawR) * Math.cos(pitchR), vy = -Math.sin(pitchR), vz = Math.cos(yawR) * Math.cos(pitchR);
        double dot = (vx * dx + vy * dy + vz * dz) / distance;
        return dot > 1 - MARGIN * 0.025 / distance;
    }
}
