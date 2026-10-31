package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;

/**
 * The narrow, read-only slice of world state a {@code DeterministicSoldier}'s tick depends on -
 * not the shared chunk cache or entity tracker themselves. See AGENTS.md's "Behavior modeling"
 * section on why: passing this in as an argument, rather than the soldier holding a reference to
 * the shared structures, is what keeps the per-tick decision testable with a one-line fake.
 *
 * <p>{@link #isSolid} is block-height-only - checks the block at the given coordinate, not a full
 * ~1.8-block entity bounding box. Coarser than real client collision, deliberately: nothing yet
 * needs the precision.
 */
@FunctionalInterface
public interface WorldView {

    boolean isSolid(int x, int y, int z);

    /**
     * Hostile entities within {@code radius} of {@code center}, nearest first. A default rather than
     * a second abstract method so {@code WorldView} stays a functional interface: a fake that only
     * cares about terrain is still a one-line lambda, and correctly reports no known threats.
     */
    default List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        return List.of();
    }
}
