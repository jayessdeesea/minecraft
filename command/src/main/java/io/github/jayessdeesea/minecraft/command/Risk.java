package io.github.jayessdeesea.minecraft.command;

/**
 * How much of a soldier's health the mission justifies spending to keep moving - the commander's
 * risk acceptance decision (ATP 5-19, <i>Risk Management</i>; docs/military-doctrine.md §7). The
 * squad leader sets it per order; each soldier weighs it against its own condition.
 *
 * <p>Concretely it governs damaging drops: falls of more than 3 blocks, which cost 1 health per
 * block beyond 3. Armour doesn't soften them - fall damage bypasses armour in vanilla; only the
 * Feather Falling and Protection enchantments do. The planner treats each point of damage as
 * {@code costPerHealth} blocks of extra walking, so a safe route always wins when one exists, and
 * a damaging one is taken only when it's worth it - and never one that would leave the soldier
 * below {@code minHealthAfter}.
 *
 * @param minHealthAfter the least health a soldier may be left with after a fall
 * @param costPerHealth  extra route cost, in blocks walked, per point of fall damage
 * @param maxDamage      the most health one route may spend, whatever's left
 */
public enum Risk {

    /**
     * A minor fall only - at most 3 health, keeping 15 - when no safe route is within about 40
     * blocks' walk: the squad's standing risk decision, so a soldier on a roof or a ledge a block
     * too high gets down without a leader's call. Anything more is CANTCO. Live, a soldier stood on
     * a ledge all night reporting a way down that cost 2 of its 19 health.
     */
    LOW(15, 40, 3),
    /** A fall that leaves at least half health, when no safe route exists nearby. */
    MEDIUM(10, 20, Integer.MAX_VALUE),
    /** A fall that leaves at least 4 health - enough to survive one more hit - when the mission needs it. */
    HIGH(4, 5, Integer.MAX_VALUE);

    private final double minHealthAfter;
    private final double costPerHealth;
    private final int maxDamage;

    Risk(double minHealthAfter, double costPerHealth, int maxDamage) {
        this.minHealthAfter = minHealthAfter;
        this.costPerHealth = costPerHealth;
        this.maxDamage = maxDamage;
    }

    public int maxDamage() {
        return maxDamage;
    }

    public double minHealthAfter() {
        return minHealthAfter;
    }

    public double costPerHealth() {
        return costPerHealth;
    }
}
