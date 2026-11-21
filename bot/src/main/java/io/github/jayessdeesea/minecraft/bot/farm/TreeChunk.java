package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A grove laid out on one chunk of the server's grid (operator, 2026-11-21; backlog/renewables.md): 16 birch at local 2,
 * 6, 10 and 14 both ways - the tree farm's world grid - nine torches on the ground at local 4, 8 and 12 both ways, light 10
 * at each trunk where growth wants 9, a chest at local (1, 1) for its axes and saplings, and the chunk farms' fence and
 * gates, one grid for wheat and trees. Its stages, wood from the second: ground, planted, lit, producing, fenced.
 *
 * <p>Pure: laid out from its chunk and level, its stage read from a {@link WorldView}.
 */
public record TreeChunk(int chunkX, int chunkZ, int level) {

    /** The trunks' and the torches' local offsets. */
    static final int[] TRUNKS = {2, 6, 10, 14};
    static final int[] TORCHES = {4, 8, 12};
    /** The chest, by local x and z. */
    static final int CHEST = 1;

    /** Its stages, in the order they're built. */
    public enum Stage { GROUND, PLANTED, LIT, PRODUCING, FENCED }

    private int x0() {
        return chunkX * ChunkFarm.SIDE;
    }

    private int z0() {
        return chunkZ * ChunkFarm.SIDE;
    }

    /** The 16 cells a sapling goes in, on the ground over its level. */
    public List<BlockPos> trunks() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : TRUNKS) {
            for (int z : TRUNKS) {
                cells.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        return cells;
    }

    /** The nine ground torches. */
    public List<BlockPos> torches() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : TORCHES) {
            for (int z : TORCHES) {
                cells.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        return cells;
    }

    /** Where its chest stands. */
    public BlockPos chest() {
        return new BlockPos(x0() + CHEST, level + 1, z0() + CHEST);
    }

    /** Its fence and gates: the chunk farms', on the same lines. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    /** Whether the cell under {@code trunk} takes a sapling: dirt or grass. */
    static boolean soil(WorldView world, BlockPos trunk) {
        return world.isSoil(trunk.x(), trunk.y() - 1, trunk.z()) && !world.isSolid(trunk.x(), trunk.y(), trunk.z());
    }

    /** The stage it's at, as the world stands: the first whose work is left undone. */
    public Stage stage(WorldView world) {
        if (!trunks().stream().allMatch(t -> soil(world, t) || TreeFarm.planted(world, t))) {
            return Stage.GROUND;
        }
        if (!trunks().stream().allMatch(t -> TreeFarm.planted(world, t)) || !world.isChest(chest().x(), chest().y(), chest().z())) {
            return Stage.PLANTED;
        }
        if (!torches().stream().allMatch(t -> world.isTorch(t.x(), t.y(), t.z()))) {
            return Stage.LIT;
        }
        if (!grid().fence().stream().allMatch(f -> world.solidHere(f.x(), f.y(), f.z())) ) {
            return Stage.PRODUCING;
        }
        return Stage.FENCED;
    }

    /**
     * The tree chunk round {@code camp}'s out to {@code rings} chunks - not the camp's own, nor one {@code taken} (a wheat
     * chunk's, another grove's): flat enough at its trunks' level, the one beside a {@code farmed} chunk first - wheat and
     * trees alternate - then nearest sea level, then nearest the camp.
     */
    public static Optional<TreeChunk> site(WorldView world, BlockPos camp, int rings, Predicate<BlockPos> walkable,
                                           Set<Long> taken, Set<Long> farmed) {
        int cx = Math.floorDiv(camp.x(), ChunkFarm.SIDE), cz = Math.floorDiv(camp.z(), ChunkFarm.SIDE);
        TreeChunk chosen = null;
        double score = Double.MAX_VALUE;
        for (int dx = -rings; dx <= rings; dx++) {
            for (int dz = -rings; dz <= rings; dz++) {
                int x = cx + dx, z = cz + dz;
                if (dx == 0 && dz == 0 || taken.contains(ChunkFarm.key(x, z))) {
                    continue;
                }
                Optional<ChunkFarm> level = ChunkFarm.levelled(world, x, z, camp.y(), walkable)
                        .filter(f -> f.plan(world, walkable).plot().size() >= ChunkFarm.MIN_CELLS);
                if (level.isEmpty()) {
                    continue;
                }
                boolean beside = farmed.contains(ChunkFarm.key(x + 1, z)) || farmed.contains(ChunkFarm.key(x - 1, z))
                        || farmed.contains(ChunkFarm.key(x, z + 1)) || farmed.contains(ChunkFarm.key(x, z - 1));
                double s = (beside ? 0 : 10_000) + Math.abs(level.get().level() - ChunkFarm.SEA_LEVEL) * 100 + Math.hypot(dx, dz);
                if (s < score) {
                    chosen = new TreeChunk(x, z, level.get().level());
                    score = s;
                }
            }
        }
        return Optional.ofNullable(chosen);
    }
}
