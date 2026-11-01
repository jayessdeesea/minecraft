package io.github.jayessdeesea.minecraft.bot.nav;

/**
 * Which damaging drops the planner may use: falls costing up to {@code maxDamage} health, each
 * point priced as {@code costPerDamage} blocks of walking. A soldier derives it from the order's
 * {@code Risk} and its own condition; {@link #NONE} keeps every route damage-free.
 */
public record FallAllowance(int maxDamage, double costPerDamage) {

    public static final FallAllowance NONE = new FallAllowance(0, 0);

    public FallAllowance {
        if (maxDamage < 0 || costPerDamage < 0) {
            throw new IllegalArgumentException("negative fall allowance: " + maxDamage + ", " + costPerDamage);
        }
    }
}
