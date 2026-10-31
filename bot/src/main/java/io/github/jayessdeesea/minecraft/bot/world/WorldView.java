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

    /** Hostile entities within {@code radius} of {@code center}, nearest first. */
    List<TrackedEntity> nearbyHostiles(Position center, double radius);
}
