package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

/**
 * An entity some soldier's connection can see, at its latest server-sent position.
 */
public record TrackedEntity(int entityId, int typeId, Position position) {

    /** Whether to get away from this one rather than fight it - it explodes. */
    public boolean explodes() {
        return HostileMobRegistry.explodes(typeId);
    }

    /** Whether this one shoots - a bare-handed soldier takes cover from it rather than closing. */
    public boolean shoots() {
        return HostileMobRegistry.shoots(typeId);
    }
}
