package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
    public boolean solidHere(int x, int y, int z) {
        return chunks.solidHere(x, y, z);
    }

    @Override
    public boolean openSection(int x, int y, int z) {
        return chunks.openSection(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return chunks.isSolid(x, y, z);
    }

    /** The cells kept open - the force's staircases' - as the squad loops last set them. */
    private volatile java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> keptOpen = java.util.Set.of();

    /** Sets the cells nothing is built in ({@link #noBuild}): the squad loops' staircases. */
    public void keepOpen(java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells) {
        keptOpen = java.util.Set.copyOf(cells);
    }

    @Override
    public boolean noBuild(int x, int y, int z) {
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> open = keptOpen;
        return !open.isEmpty() && open.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
    }

    @Override
    public boolean isTorch(int x, int y, int z) {
        return chunks.isTorch(x, y, z);
    }

    @Override
    public boolean isChest(int x, int y, int z) {
        return chunks.isChest(x, y, z);
    }

    @Override
    public boolean isGate(int x, int y, int z) {
        return chunks.isGate(x, y, z);
    }

    /** Whether block state {@code stateId} is a fence gate, open or shut - for whoever stands in for the server. */
    public static boolean gateState(int stateId) {
        return BlockClassRegistry.isGate(stateId);
    }

    /** Whether block state {@code stateId} is a fence gate that's shut: its open state is two under it. */
    public static boolean shutGateState(int stateId) {
        return BlockClassRegistry.isGate(stateId) && BlockClassRegistry.classOf(stateId) != BlockClassRegistry.BlockClass.PASSABLE;
    }

    @Override
    public boolean isShutGate(int x, int y, int z) {
        return chunks.isShutGate(x, y, z);
    }

    @Override
    public long stamp(int x, int z, int chunks) {
        return this.chunks.stamp(x, z, chunks);
    }

    @Override
    public Optional<Workstation> workstation(int x, int y, int z) {
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
    public boolean isStalagmite(int x, int y, int z) {
        return chunks.isStalagmite(x, y, z);
    }

    @Override
    public boolean isCobblestone(int x, int y, int z) {
        return chunks.isCobblestone(x, y, z);
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
    public Optional<Hardness> hardness(int x, int y, int z) {
        return chunks.hardness(x, y, z);
    }

    /** The chunks whose farmland is given up, by ChunkFarm#key: the farm missions' chunks under flatten and floor, set each cycle. */
    private volatile java.util.Set<Long> farmlandGivenUp = java.util.Set.of();

    /** {@code chunks} - by ChunkFarm#key - the ones whose farmland is given up from now (farmlandGivenUp). */
    public void farmlandGivenUp(java.util.Set<Long> chunks) {
        farmlandGivenUp = java.util.Set.copyOf(chunks);
    }

    @Override
    public boolean farmlandGivenUp(int x, int z) {
        return farmlandGivenUp.contains(io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm.key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
    }

    @Override
    public String describeAt(int x, int y, int z) {
        return chunks.describeAt(x, y, z);
    }

    @Override
    public Optional<String> leaves(int x, int y, int z) {
        return chunks.leaves(x, y, z);
    }

    @Override
    public Optional<String> sapling(int x, int y, int z) {
        return chunks.sapling(x, y, z);
    }

    @Override
    public Optional<String> oreOf(int x, int y, int z) {
        return chunks.oreOf(x, y, z);
    }

    @Override
    public boolean isLoaded(int x, int z) {
        return chunks.isLoaded(x, z);
    }

    @Override
    public boolean isGravel(int x, int y, int z) {
        return chunks.isGravel(x, y, z);
    }

    @Override
    public boolean falls(int x, int y, int z) {
        return chunks.falls(x, y, z);
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
    public OptionalInt wheatAge(int x, int y, int z) {
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
    public List<Position> playersNear(Position center, double radius) {
        return entities.nearbyPlayers(center, radius);
    }

    @Override
    public List<Position> playersNear(Position center, double radius, int except) {
        return entities.nearbyPlayers(center, radius, except);
    }

    @Override
    public List<TrackedEntity> gameNear(Position center, double radius) {
        return entities.nearbyGame(center, radius);
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
