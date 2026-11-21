package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;

/**
 * Which swing a soldier makes at the hostile in reach (pure): the sword's sweep when it hits nobody of the force's, and
 * a swing that doesn't sweep when a squadmate is in its box. Vanilla's rules, as the pinned server has them
 * ({@code Player.attack}, {@code isSweepAttack}, {@code doSweepAttack}):
 * <ul>
 *   <li>a hit is strong past 0.9 of its charge - charge being (ticks since the last swing + 0.5) over the weapon's
 *   cooldown, 20 over its attacks a second - and a strong hit sprinting is a knockback hit: pushed further, and it
 *   stops the sprint ({@code causeExtraKnockback});</li>
 *   <li>a sweep is a strong hit with a sword that's neither critical nor a knockback hit, on the ground, not moving
 *   fast: it hits every living thing in the target's box grown a block across and a quarter up and down, for 1 and the
 *   sweeping ratio (Sweeping Edge's) times the blow - a squadmate too;</li>
 *   <li>the arm's swing alone ({@code PunchPacket}) starts the charge over.</li>
 * </ul>
 * So with a squadmate in the box: a knockback hit at full charge when it can sprint - full damage, no sweep; else a hit
 * short of 0.9, which sweeps nobody; already charged past that, its charge started over by a swing, then the short hit.
 * Its weapon's sweeping ratio and knockback are what an enchanted sword will change (the operator, 2026-11-21: we'll have
 * enchantments on swords eventually, knockback for one) - inputs here, not rules elsewhere.
 */
public final class Strike {

    /** What the swing is. */
    public enum Kind {
        /** Not yet: charging. */
        WAIT,
        /** At full charge - a sword's sweeps, with none of the force's in its box. */
        FULL,
        /** At full charge, sprinting: a knockback hit, which never sweeps. */
        KNOCKBACK,
        /** Short of a sweep's charge: a lesser blow that sweeps nobody. */
        SHORT,
        /** The arm swung, no hit: the charge started over, for a short hit after. */
        RESET
    }

    /** The weapon in hand, as the swing takes it: a sword or not, its sweeping ratio, its knockback, its attacks a second. */
    public record Weapon(boolean sword, double sweepingRatio, int knockback, double attacksPerSecond) {

        /** The weapon named {@code item} (empty: bare hands), unenchanted. */
        public static Weapon of(java.util.Optional<String> item) {
            WeaponStats.Stats stats = WeaponStats.melee(item);
            return new Weapon(item.filter(i -> i.endsWith("_sword")).isPresent(), 0, 0, stats.attacksPerSecond());
        }

        /** The ticks of its cooldown, 20 over its attacks a second. */
        double cooldown() {
            return 20 / attacksPerSecond;
        }

        /** Its charge {@code ticks} after the last swing, as the server reckons it. */
        public double charge(int ticks) {
            return Math.min(1, (ticks + 0.5) / cooldown());
        }

        /** The ticks after a swing a short hit goes at: the most whose charge is short of a strong hit's 0.9. */
        public int shortTicks() {
            return (int) Math.ceil(STRONG * cooldown() - 0.5) - 1;
        }
    }

    /** Past this share of its charge a hit is strong: a sword's sweeps, sprinting one knocks back. */
    static final double STRONG = 0.9;

    /**
     * What's round the swing: ticks since the last, whether a squadmate is in its sweep's box, how many other hostiles are
     * (what a sweep takes too), and whether it can sprint (food over 6, on its feet).
     */
    public record Situation(int ticksSinceSwing, boolean squadmateInSweep, int othersInSweep, boolean canSprint) {
    }

    private Strike() {
    }

    /** The swing {@code weapon} makes in {@code now}. */
    public static Kind choose(Weapon weapon, Situation now) {
        boolean charged = weapon.charge(now.ticksSinceSwing()) >= 1;
        // A sweep with none of the force's in it is free: against two or more, the best swing there is.
        if (!weapon.sword() || !now.squadmateInSweep()) {
            return charged ? Kind.FULL : Kind.WAIT;
        }
        if (now.canSprint()) {
            return charged ? Kind.KNOCKBACK : Kind.WAIT;
        }
        int since = now.ticksSinceSwing();
        if (weapon.charge(since) > STRONG) {
            return Kind.RESET;
        }
        return since >= weapon.shortTicks() ? Kind.SHORT : Kind.WAIT;
    }

    /** Whether a {@code kind} swing of {@code weapon} sweeps: only a full one with a sword. */
    public static boolean sweeps(Weapon weapon, Kind kind) {
        return weapon.sword() && kind == Kind.FULL;
    }

    /** What a sweep of {@code weapon} with blow {@code damage} does to each other body in its box: 1, and its ratio of the blow. */
    public static double sweepDamage(Weapon weapon, double damage) {
        return 1 + weapon.sweepingRatio() * damage;
    }
}
