package io.github.jayessdeesea.minecraft.command;

/**
 * Where the overworld's day stands - the deadline for whatever a squad must finish before monsters
 * come out. A day is {@link #DAY_TICKS} ticks, 20 minutes, starting at sunrise.
 *
 * <p>Dark means monsters are about in the open: from when they start spawning there until the
 * sun is up to burn the ones that did. The server's {@code day} timeline scales sky light by 1.0
 * from tick 133 to 11867, down to 0.267 by 13670, holds it to 22330 and ramps back up by 24133. A
 * monster spawns where light is at most a random 0-7; sky light 15 scaled falls to 7 at tick
 * {@link #DUSK}, and the spawning stops when it rises past it at 22822 - but the zombies and
 * skeletons it made walk until sunrise, {@link #DAWN}, burns them. Like a unit's stand-to before
 * first light, a squad stays in until then.
 *
 * @param ticks    0 to {@link #DAY_TICKS}, ticks since sunrise
 * @param running  false when the server has stopped the clock - the time of day never changes
 */
public record TimeOfDay(long ticks, boolean running) {

    public static final long DAY_TICKS = 24000;
    public static final long DUSK = 13178;
    /** Sunrise: the sun's up, and burns the undead out in the open. Ticks count from it. */
    public static final long DAWN = DAY_TICKS;

    public TimeOfDay {
        if (ticks < 0 || ticks >= DAY_TICKS) {
            throw new IllegalArgumentException("time of day out of range: " + ticks);
        }
    }

    /** The time of day from the overworld clock's total ticks. */
    public static TimeOfDay of(long totalTicks, boolean running) {
        return new TimeOfDay(Math.floorMod(totalTicks, DAY_TICKS), running);
    }

    public boolean dark() {
        return ticks >= DUSK && ticks < DAWN;
    }

    /** Ticks until it's dark: 0 while it is, and never when the clock is stopped in daylight. */
    public long ticksUntilDark() {
        if (dark()) {
            return 0;
        }
        if (!running) {
            return Long.MAX_VALUE;
        }
        return ticks < DUSK ? DUSK - ticks : DAY_TICKS - ticks + DUSK;
    }

    /** Ticks until it's light again: 0 while it is, and never when the clock is stopped at night. */
    public long ticksUntilLight() {
        if (!dark()) {
            return 0;
        }
        return running ? DAWN - ticks : Long.MAX_VALUE;
    }
}
