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
 */
public enum Risk {

    /** No damage to keep moving: report CANTCO instead. */
    LOW(Double.POSITIVE_INFINITY, 0),
    /** A fall that leaves at least half health, when no safe route exists nearby. */
    MEDIUM(10, 20),
    /** A fall that leaves at least 4 health - enough to survive one more hit - when the mission needs it. */
    HIGH(4, 5);

    private final double minHealthAfter;
    private final double costPerHealth;

    Risk(double minHealthAfter, double costPerHealth) {
        this.minHealthAfter = minHealthAfter;
        this.costPerHealth = costPerHealth;
    }

    public double minHealthAfter() {
        return minHealthAfter;
    }

    public double costPerHealth() {
        return costPerHealth;
    }
}
