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
 * @param logs  logs carried - the materials a squad gathers (docs/military-doctrine.md §12)
 * @param stone  cobblestone carried
 * @param rations  food items carried that a soldier eats - the <b>L</b>iquids line's supply
 * @param shield  whether a shield is in the off hand, to raise against arrows
 * @param pickaxe  whether a pickaxe is carried - to cut steps into stone with
 */
public record Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone, int rations,
                  boolean shield, boolean pickaxe, int dirt, boolean bow) {

    public static final int ARMOUR_SLOTS = 4;
    /** Vanilla caps enchantment protection at 20 points, each taking 4% off the damage. */
    public static final int MAX_PROTECTION = 20;

    public Kit {
        if (fallProtection < 0 || fallProtection > MAX_PROTECTION) {
            throw new IllegalArgumentException("fall protection out of range: " + fallProtection);
        }
    }

    /** Kit with no bow. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone, int rations,
               boolean shield, boolean pickaxe, int dirt) {
        this(weapon, arrows, armour, fallProtection, logs, stone, rations, shield, pickaxe, dirt, false);
    }

    /** Kit with no dirt. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone, int rations,
               boolean shield, boolean pickaxe) {
        this(weapon, arrows, armour, fallProtection, logs, stone, rations, shield, pickaxe, 0);
    }

    /** Kit with no pickaxe. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone, int rations, boolean shield) {
        this(weapon, arrows, armour, fallProtection, logs, stone, rations, shield, false);
    }

    /** Kit with no shield or pickaxe. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone, int rations) {
        this(weapon, arrows, armour, fallProtection, logs, stone, rations, false, false);
    }

    /** Kit with no protective enchantments, materials or rations. */
    public Kit(Optional<String> weapon, int arrows, int armour) {
        this(weapon, arrows, armour, 0, 0, 0, 0);
    }

    /** Kit with no materials or rations. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection) {
        this(weapon, arrows, armour, fallProtection, 0, 0, 0);
    }

    /** Kit with no rations. */
    public Kit(Optional<String> weapon, int arrows, int armour, int fallProtection, int logs, int stone) {
        this(weapon, arrows, armour, fallProtection, logs, stone, 0);
    }

    /** The share of fall damage that gets through, 1 with no protection down to 0.2. */
    public double fallDamageFactor() {
        return 1 - fallProtection / 25.0;
    }
}
