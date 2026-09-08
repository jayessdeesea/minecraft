package io.github.jayessdeesea.minecraft.bot.world;

/**
 * The narrow, read-only slice of world state a {@code DeterministicSoldier}'s tick depends on -
 * not the shared chunk cache itself. See AGENTS.md's "Behavior modeling" section on why the cache
 * behind this (a shared, reference-counted structure fed by every soldier's own connection) is a
 * separate concern from this query interface: passing this in as an argument, rather than the
 * soldier holding a reference to the cache, is what keeps the per-tick decision testable with a
 * one-line fake.
 *
 * <p>Block-height-only for now - checks the block at the given coordinate, not a full ~1.8-block
 * entity bounding box. Coarser than real client collision, deliberately: nothing yet needs the
 * precision, and the real chunk-backed implementation doesn't exist yet either (this interface has
 * no implementation in this module today - only test fakes).
 */
@FunctionalInterface
public interface WorldView {

    boolean isSolid(int x, int y, int z);
}
