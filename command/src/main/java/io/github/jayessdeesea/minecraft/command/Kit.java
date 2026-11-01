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
 */
public record Kit(Optional<String> weapon, int arrows, int armour) {

    public static final int ARMOUR_SLOTS = 4;
}
