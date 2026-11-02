package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;

/**
 * The real {@link WorldView}: terrain from a shared {@link ChunkCache}, threats from a shared
 * {@link EntityTracker}. Both are fed by every soldier's connection; this only routes queries.
 */
public final class WorldModel implements WorldView {

    private final ChunkCache chunks;
    private final EntityTracker entities;

    public WorldModel(ChunkCache chunks, EntityTracker entities) {
        this.chunks = chunks;
        this.entities = entities;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return chunks.isSolid(x, y, z);
    }

    @Override
    public boolean isLog(int x, int y, int z) {
        return chunks.isLog(x, y, z);
    }

    @Override
    public boolean isStone(int x, int y, int z) {
        return chunks.isStone(x, y, z);
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        return chunks.isHazard(x, y, z);
    }

    @Override
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        return entities.nearbyHostiles(center, radius);
    }
}
