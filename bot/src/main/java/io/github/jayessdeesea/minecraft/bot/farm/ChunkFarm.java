package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A farm laid out on one chunk of the server's grid (operator, 2026-11-19), built once the force has a bucket: flat
 * at one level, fenced on the chunk's lines - x%16==0 and z%16==0, each line shared with the farm beside it - with a
 * gate beside each corner; its ground tilled but for four water holes at local (4, 4), (4, 12), (12, 4) and (12, 12),
 * dug in the farmland's own layer and filled by bucket - each hydrates the 9x9 round it, the four the whole of the
 * fenced 15x15 - and a chest at local (13, 13) for the farm's seed and wheat.
 *
 * <p>Pure: laid out from its chunk and level, and sited and planned from a {@link WorldView}.
 */
public record ChunkFarm(int chunkX, int chunkZ, int level) {

    /** A chunk's side. */
    public static final int SIDE = 16;
    /** The water holes, by local x and z. */
    static final int[][] HOLES = {{4, 4}, {4, 12}, {12, 4}, {12, 12}};
    /** The chest, by local x and z. */
    static final int CHEST = 13;
    /**
     * The least of its ground to farm at one level for a chunk to be sited - over half its 220: the rest is flattened to it
     * in time. Live (field502), the best chunk by the camp at sea level made 138 of 160, and the farm waited on a flatten
     * the camp's phase holds off (operator, 2026-11-20: the high-density farms as soon as can be).
     */
    public static final int MIN_CELLS = 128;

    private int x0() {
        return chunkX * SIDE;
    }

    private int z0() {
        return chunkZ * SIDE;
    }

    /** The gates, on the ground over its level: beside each corner, one on each of its four lines, going round. */
    public List<BlockPos> gates() {
        int y = level + 1;
        return List.of(new BlockPos(x0() + 1, y, z0()), new BlockPos(x0() + SIDE, y, z0() + 1),
                new BlockPos(x0() + SIDE - 1, y, z0() + SIDE), new BlockPos(x0(), y, z0() + SIDE - 1));
    }

    /** The fence posts, on the ground over its level: its four lines, the corners in, the gates out. */
    public List<BlockPos> fence() {
        int y = level + 1;
        List<BlockPos> gates = gates();
        List<BlockPos> posts = new ArrayList<>();
        for (int i = 0; i <= SIDE; i++) {
            for (BlockPos c : List.of(new BlockPos(x0() + i, y, z0()), new BlockPos(x0() + i, y, z0() + SIDE),
                    new BlockPos(x0(), y, z0() + i), new BlockPos(x0() + SIDE, y, z0() + i))) {
                if (!gates.contains(c) && !posts.contains(c)) {
                    posts.add(c);
                }
            }
        }
        return posts;
    }

    /** The water holes, in the farmland's own layer: water a block below hydrates nothing. */
    public List<BlockPos> holes() {
        List<BlockPos> holes = new ArrayList<>();
        for (int[] h : HOLES) {
            holes.add(new BlockPos(x0() + h[0], level, z0() + h[1]));
        }
        return holes;
    }

    /** Where the chest stands: on the ground at local (13, 13). */
    public BlockPos chest() {
        return new BlockPos(x0() + CHEST, level + 1, z0() + CHEST);
    }

    /** Its ground as a box: the chunk and the fence lines beyond it, from under its level to over its fence. */
    public io.github.jayessdeesea.minecraft.command.Box box() {
        return new io.github.jayessdeesea.minecraft.command.Box(x0(), level - 1, z0(), x0() + SIDE, level + 2, z0() + SIDE);
    }

    /** Its chest's cell as a position: its tender's dump. */
    public io.github.jayessdeesea.minecraft.command.Position chestAt() {
        BlockPos c = chest();
        return new io.github.jayessdeesea.minecraft.command.Position(c.x(), c.y(), c.z());
    }

    /** The ground inside its fence at its level, less its holes and the cell under its chest: 220 cells. */
    public List<BlockPos> ground() {
        List<BlockPos> holes = holes();
        BlockPos underChest = chest().offset(0, -1, 0);
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = 1; dx < SIDE; dx++) {
            for (int dz = 1; dz < SIDE; dz++) {
                BlockPos c = new BlockPos(x0() + dx, level, z0() + dz);
                if (!holes.contains(c) && !c.equals(underChest)) {
                    cells.add(c);
                }
            }
        }
        return cells;
    }

    /** Whether {@code c} makes farmland as the world stands: tillable or tilled, open above. */
    static boolean farmable(WorldView world, BlockPos c) {
        return (world.isTillable(c.x(), c.y(), c.z()) || world.isFarmland(c.x(), c.y(), c.z())) && !world.isWater(c.x(), c.y(), c.z())
                && !world.isSolid(c.x(), c.y() + 1, c.z()) && !world.isHazard(c.x(), c.y() + 1, c.z());
    }

    /**
     * It as a farm to tend: its plot the ground that makes farmland now, standing on cells {@code walkable} allows; its
     * water its first hole; itself, for its holes to dig and fill and its fence, gates and chest to put up.
     */
    public FarmPlan plan(WorldView world, Predicate<BlockPos> walkable) {
        List<BlockPos> plot = ground().stream().filter(c -> farmable(world, c) && walkable.test(c.offset(0, 1, 0))).toList();
        return new FarmPlan(holes().getFirst(), plot, List.of(), List.of(this));
    }

    /**
     * The chunk to farm nearest {@code camp}'s, out to {@code rings} chunks round it - not the camp's own, nor one in
     * {@code taken}: the one with the most of its ground at one level that makes farmland, standing on cells
     * {@code walkable} allows, the nearer of equals - and at least {@link #MIN_CELLS}.
     */
    public static Optional<ChunkFarm> site(WorldView world, BlockPos camp, int rings, Predicate<BlockPos> walkable,
                                           java.util.Set<Long> taken) {
        return best(world, camp, rings, walkable, taken).filter(f -> f.plan(world, walkable).plot().size() >= MIN_CELLS);
    }

    /** As {@link #site}, at any size: the chunk nearest the most ground at one level, for the log to say how near a farm it came. */
    public static Optional<ChunkFarm> best(WorldView world, BlockPos camp, int rings, Predicate<BlockPos> walkable,
                                           java.util.Set<Long> taken) {
        int cx = Math.floorDiv(camp.x(), SIDE), cz = Math.floorDiv(camp.z(), SIDE);
        ChunkFarm best = null;
        int bestCells = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -rings; dx <= rings; dx++) {
            for (int dz = -rings; dz <= rings; dz++) {
                if (dx == 0 && dz == 0 || taken.contains(key(cx + dx, cz + dz))) {
                    continue;
                }
                Optional<ChunkFarm> farm = levelled(world, cx + dx, cz + dz, camp.y(), walkable);
                if (farm.isEmpty()) {
                    continue;
                }
                int cells = farm.get().plan(world, walkable).plot().size();
                double distance = Math.hypot(dx, dz);
                if (cells > bestCells || cells == bestCells && distance < bestDistance) {
                    best = farm.get();
                    bestCells = cells;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** A chunk's key in {@code taken}. */
    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    /** How far below and above the camp a chunk farm's level is looked for. */
    static final int VERTICAL = 16;

    /** The chunk's farm at the level most of its ground would farm at, if any of it does. */
    static Optional<ChunkFarm> levelled(WorldView world, int chunkX, int chunkZ, int around, Predicate<BlockPos> walkable) {
        Map<Integer, Integer> atLevel = new HashMap<>();
        for (int dx = 1; dx < SIDE; dx++) {
            for (int dz = 1; dz < SIDE; dz++) {
                for (int y = around - VERTICAL; y <= around + VERTICAL; y++) {
                    BlockPos c = new BlockPos(chunkX * SIDE + dx, y, chunkZ * SIDE + dz);
                    if (farmable(world, c) && walkable.test(c.offset(0, 1, 0))) {
                        atLevel.merge(y, 1, Integer::sum);
                    }
                }
            }
        }
        return atLevel.entrySet().stream().max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(e -> -Math.abs(e.getKey() - around)))
                .map(e -> new ChunkFarm(chunkX, chunkZ, e.getKey()));
    }
}
