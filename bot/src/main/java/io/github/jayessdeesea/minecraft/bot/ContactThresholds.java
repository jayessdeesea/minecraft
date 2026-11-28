package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;

/**
 * Where a soldier draws the lines of contact: how near a hostile is met, shot at, fled or got clear of.
 */
public final class ContactThresholds {
    private ContactThresholds() {
    }

    /**
     * Ballpark starting value, not tuned against real mob behavior - vanilla hostiles typically
     * notice a player around 16 blocks out, so this reacts well inside that. Revisit once a live
     * run shows soldiers reacting too late or too skittishly.
     */
    public static final double CONTACT_RADIUS = 8.0;

    /**
     * Beyond this range a soldier with a bow and arrows shoots rather than closing: a sword's reach
     * is three blocks, and every block closed under an archer's fire is a block it shoots back.
     */
    static final double SHOOT_BEYOND = 5;

    /**
     * Under this health an archer it can't close on isn't let be between its shots: kept out of its sight, as one with no
     * weapon keeps out of it.
     */
    static final double HURT_HEALTH = 10;

    /** How far a soldier fleeing an archer runs before it's fled: past a skeleton's 15 blocks of range. */
    static final double FLED_PAST = 20;

    /** How far below a soldier an archer is out of its reach for a sword, however a route might go down to it. */
    static final double ARCHER_BELOW = 4;

    /** Ticks a creeper holds a soldier inside its blast's reach before it's engaged: ten seconds. */
    static final int CREEPER_STANDOFF_TICKS = 200;

    /** How far off a creeper is shot at by a soldier with a bow: outside where it lights. */
    static final double CREEPER_CLEAR = 4.5;

    /**
     * How far off a creeper stops being contact: past where its fuse, once lit, goes out - 7 in vanilla - so a lit one
     * is never left burning within the 6 of its blast.
     */
    static final double CREEPER_DEFUSE = 7.5;

    /** How far a soldier looks for cover from an archer. */
    static final int COVER_RADIUS = 6;

    /** Sampling step along a sight line: fine enough not to step over the corner of a block. */
    static final double SIGHT_STEP = 0.2;

    /**
     * The deepest drop a step in a fight may leave the soldier over: one the server charges no
     * health for.
     */
    static final int FIGHT_DROP = Pathfinder.MAX_DROP;
}
