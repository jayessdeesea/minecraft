package io.github.jayessdeesea.minecraft.bot.nav;

/**
 * Which damaging drops the planner may use: falls costing up to {@code maxDamage} health, each
 * point priced as {@code costPerDamage} blocks of walking. {@code damageFactor} is the share of
 * fall damage that gets through the soldier's armour enchantments ({@code Kit.fallDamageFactor}):
 * 1 per block beyond three, times that. A soldier derives it from the order's {@code Risk} and its
 * own condition; {@link #NONE} keeps every route damage-free.
 */
public record FallAllowance(int maxDamage, double costPerDamage, double damageFactor) {

    public static final FallAllowance NONE = new FallAllowance(0, 0);

    public FallAllowance {
        if (maxDamage < 0 || costPerDamage < 0 || !(damageFactor > 0 && damageFactor <= 1)) {
            throw new IllegalArgumentException("bad fall allowance: " + maxDamage + ", " + costPerDamage + ", " + damageFactor);
        }
    }

    /** Unprotected: every block of fall beyond three costs a point of health. */
    public FallAllowance(int maxDamage, double costPerDamage) {
        this(maxDamage, costPerDamage, 1);
    }
}
