package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

/**
 * The animals the force brings in, and what leads each: an animal walks after a player holding its kind's food
 * within {@link #TEMPT_RANGE}. The one table of it - a scouting order names a kind, and the food drawn for it and
 * held on the way back is read from here.
 */
public enum Livestock {
    CHICKEN(EntityTracker.CHICKEN_TYPE_ID, ItemClass.SEEDS),
    SHEEP(EntityTracker.SHEEP_TYPE_ID, ItemClass.WHEAT),
    /** The cow, 30 in protocol 777's registry. */
    COW(30, ItemClass.WHEAT);

    /** How far off an animal sees its food in a player's hand, and past which it stops following. */
    public static final double TEMPT_RANGE = 10;

    private final int typeId;
    private final ItemClass food;

    Livestock(int typeId, ItemClass food) {
        this.typeId = typeId;
        this.food = food;
    }

    /** Its entity type in the pinned protocol's registry. */
    public int typeId() {
        return typeId;
    }

    /** What a soldier holds to lead it. */
    public ItemClass food() {
        return food;
    }
}
