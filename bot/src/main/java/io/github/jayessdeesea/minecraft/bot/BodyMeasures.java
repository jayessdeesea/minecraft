package io.github.jayessdeesea.minecraft.bot;

/**
 * A player's body, in blocks: how tall it stands, where it looks from and is looked at, how far it swings.
 */
public final class BodyMeasures {
    private BodyMeasures() {
    }

    /** A player's box, feet to crown: what a body occupies above its feet, and what a shot at it is checked against. */
    static final double BODY_HEIGHT = 1.8;

    /** Eye height of a standing player. */
    public static final double EYE_HEIGHT = 1.62;

    /** The height a hostile is looked at - roughly its chest. */
    public static final double TARGET_HEIGHT = 1.2;

    /**
     * Centre-to-centre distance a soldier swings at. The server's check (eye to target hitbox,
     * {@code isWithinAttackRange}) is more generous; staying inside it avoids attacks it would
     * silently drop.
     */
    public static final double ATTACK_REACH = 3.0;

    /**
     * Slack in comparing lengths: a box's edge with a cell's, so a body resting on a floor is not counted inside it,
     * and a distance with zero.
     */
    static final double EPSILON = 1e-9;

    /** How far under its column's surface a cell is under the ground: a cave's, not the surface's. */
    static final int UNDER_THE_SURFACE = 8;
}
