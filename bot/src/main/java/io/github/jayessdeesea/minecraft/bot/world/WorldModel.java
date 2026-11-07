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
    public boolean isTorch(int x, int y, int z) {
        return chunks.isTorch(x, y, z);
    }

    @Override
    public java.util.Optional<Workstation> workstation(int x, int y, int z) {
        return chunks.workstation(x, y, z);
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
    public boolean isIronOre(int x, int y, int z) {
        return chunks.isIronOre(x, y, z);
    }

    @Override
    public boolean isWaterSource(int x, int y, int z) {
        return chunks.isWaterSource(x, y, z);
    }

    @Override
    public java.util.Optional<Hardness> hardness(int x, int y, int z) {
        return chunks.hardness(x, y, z);
    }

    @Override
    public java.util.Optional<String> leaves(int x, int y, int z) {
        return chunks.leaves(x, y, z);
    }

    @Override
    public java.util.Optional<String> sapling(int x, int y, int z) {
        return chunks.sapling(x, y, z);
    }

    @Override
    public boolean isOre(int x, int y, int z) {
        return chunks.isOre(x, y, z);
    }

    @Override
    public boolean isWater(int x, int y, int z) {
        return chunks.isWater(x, y, z);
    }

    @Override
    public boolean isTillable(int x, int y, int z) {
        return chunks.isTillable(x, y, z);
    }

    @Override
    public boolean isGrassPlant(int x, int y, int z) {
        return chunks.isGrassPlant(x, y, z);
    }

    @Override
    public boolean isFarmland(int x, int y, int z) {
        return chunks.isFarmland(x, y, z);
    }

    @Override
    public java.util.OptionalInt wheatAge(int x, int y, int z) {
        return chunks.wheatAge(x, y, z);
    }

    @Override
    public boolean isSoil(int x, int y, int z) {
        return chunks.isSoil(x, y, z);
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        return chunks.isHazard(x, y, z);
    }

    @Override
    public List<Position> bodiesNear(Position center, double radius) {
        return entities.nearbyBodies(center, radius);
    }

    @Override
    public List<Position> itemsNear(Position center, double radius) {
        return entities.nearbyItems(center, radius);
    }

    @Override
    public List<Position> arrowsNear(Position center, double radius) {
        return entities.nearbyArrows(center, radius);
    }

    @Override
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        return entities.nearbyHostiles(center, radius);
    }
}
