package io.github.jayessdeesea.minecraft.command;

import java.util.Optional;

/**
 * What a soldier carries that a fight cares about - the LACE report's <b>A</b>mmunition and
 * <b>E</b>quipment (docs/military-doctrine.md §4): the weapon in its hand if it holds one, its
 * arrows, and how many of its four armour slots are filled.
 *
 * @param weapon  the held weapon's item name ("iron_sword"), or empty if bare-handed
 * @param arrows  arrows carried, of any kind
 * @param armour  armour pieces worn, 0 to {@link #ARMOUR_SLOTS}
 * @param fallProtection  the worn armour's enchantment protection against falls, 0 to
 *        {@link #MAX_PROTECTION}: 3 per level of Feather Falling, 1 per level of Protection
 */
public record Kit(Optional<String> weapon, int arrows, int armour, int fallProtection) {

    public static final int ARMOUR_SLOTS = 4;
    /** Vanilla caps enchantment protection at 20 points, each taking 4% off the damage. */
    public static final int MAX_PROTECTION = 20;

    public Kit {
        if (fallProtection < 0 || fallProtection > MAX_PROTECTION) {
            throw new IllegalArgumentException("fall protection out of range: " + fallProtection);
        }
    }

    /** Kit with no protective enchantments. */
    public Kit(Optional<String> weapon, int arrows, int armour) {
        this(weapon, arrows, armour, 0);
    }

    /** The share of fall damage that gets through, 1 with no protection down to 0.2. */
    public double fallDamageFactor() {
        return 1 - fallProtection / 25.0;
    }
}
