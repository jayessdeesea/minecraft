package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;

/**
 * The narrow, read-only slice of world state a {@code DeterministicSoldier}'s tick depends on -
 * not the shared chunk cache or entity tracker themselves. See AGENTS.md's "Behavior modeling"
 * section on why: passing this in as an argument, rather than the soldier holding a reference to
 * the shared structures, is what keeps the per-tick decision testable with a small fake.
 *
 * <p>Both methods are abstract on purpose. A default "no hostiles" would let an implementation that
 * only knows terrain compile and silently blind every soldier to threats; an implementation has to
 * answer both questions explicitly. {@link WorldModel} is the real one.
 */
public interface WorldView {

    /**
     * Block-height-only - checks the block at the given coordinate, not a full ~1.8-block entity
     * bounding box. Coarser than real client collision, deliberately: nothing yet needs the precision.
     */
    boolean isSolid(int x, int y, int z);

    /** Somewhere never to stand in or walk through: water, lava, fire, powder snow. Not solid either. */
    boolean isHazard(int x, int y, int z);

    /** Whether the block at (x, y, z) is a torch - a light a patrol base is lit by. */
    default boolean isTorch(int x, int y, int z) {
        return false;
    }

    /** The crafting table or furnace at (x, y, z), if there is one (it's also {@link #isSolid}). */
    default java.util.Optional<Workstation> workstation(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /**
     * Whether the block is a log a soldier can chop (it's also {@link #isSolid}). A view that knows
     * no blocks' identity - a test's synthetic terrain - has no logs.
     */
    default boolean isLog(int x, int y, int z) {
        return false;
    }

    /** Whether the block is plain stone a soldier can quarry (it's also {@link #isSolid}). */
    default boolean isStone(int x, int y, int z) {
        return false;
    }

    /** Whether the block is soil - dirt, grass, clay - that a soldier digs by hand (it's also {@link #isSolid}). */
    default boolean isSoil(int x, int y, int z) {
        return false;
    }

    /**
     * Where the bodies within {@code radius} of {@code center} are - squadmates and mobs, which a
     * block can't be placed into. A view that tracks no entities sees none.
     */
    default List<Position> bodiesNear(Position center, double radius) {
        return List.of();
    }

    /** Where the dropped items within {@code radius} of {@code center} are, nearest first. A view that tracks no entities sees none. */
    default List<Position> itemsNear(Position center, double radius) {
        return List.of();
    }

    /** Hostile entities within {@code radius} of {@code center}, nearest first. */
    List<TrackedEntity> nearbyHostiles(Position center, double radius);
}
