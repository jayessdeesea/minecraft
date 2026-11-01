package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;

import java.util.Random;

/**
 * One generated situation: a terrain family, the seed that reproduces it exactly, the world, and
 * where the soldier starts and is sent. Start is near the south edge and the objective near the
 * north, so every scenario has to cross the family's feature.
 */
public record Scenario(Terrain terrain, long seed, SyntheticWorld world, BlockPos start, BlockPos objective) {

    static final int BASE_GROUND = 63;

    public static Scenario generate(Terrain terrain, long seed) {
        Random random = new Random(seed * 1_000_003L + terrain.ordinal());
        SyntheticWorld world = new SyntheticWorld(BASE_GROUND);
        int startX = random.nextInt(31) - 15;
        int goalX = random.nextInt(31) - 15;
        int startZ = 36;
        int goalZ = -36;
        terrain.build(world, random, startZ, goalX, goalZ);
        world.clearAbove(startX, startZ);
        world.clearAbove(goalX, goalZ);
        BlockPos start = new BlockPos(startX, world.ground(startX, startZ) + 1, startZ);
        BlockPos objective = new BlockPos(goalX, world.ground(goalX, goalZ) + 1, goalZ);
        return new Scenario(terrain, seed, world, start, objective);
    }

    @Override
    public String toString() {
        return terrain.name().toLowerCase() + " seed=" + seed;
    }
}
