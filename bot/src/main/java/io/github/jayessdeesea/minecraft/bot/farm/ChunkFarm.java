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
 * gate each side of each corner; its ground tilled but for four water holes at local (4, 4), (4, 12), (12, 4) and (12, 12),
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
     * The least of its ground to farm at one level for a chunk to be sited - over half its 216: the rest is flattened to it
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

    /**
     * The gates, on the ground over its level: each corner a post, and the cell beside it on each of its two lines a gate
     * - eight, going round (operator, 2026-11-21: the corner a fence, right next to it a gate on both sides). One grid for
     * every chunk farm, wheat or trees: the lines are shared, and so are the corners.
     */
    public List<BlockPos> gates() {
        int y = level + 1;
        int x1 = x0() + SIDE;
        int z1 = z0() + SIDE;
        return List.of(new BlockPos(x0() + 1, y, z0()), new BlockPos(x1 - 1, y, z0()), new BlockPos(x1, y, z0() + 1),
                new BlockPos(x1, y, z1 - 1), new BlockPos(x1 - 1, y, z1), new BlockPos(x0() + 1, y, z1),
                new BlockPos(x0(), y, z1 - 1), new BlockPos(x0(), y, z0() + 1));
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

    /** Each field torch's cell, by local x and z: a step in from each hole, on the diagonal toward the middle. */
    private static final int[][] FIELD_TORCHES = {{5, 5}, {5, 11}, {11, 5}, {11, 11}};

    /** Every how many of its fence posts carries a torch on its top. */
    static final int POSTS_A_TORCH = 4;

    /**
     * Its torches, so nothing spawns in it or by it by night (operator, 2026-11-20): four in the field, a step in on the
     * diagonal from each hole, on ground left untilled - no torch stands on farmland - and one on every
     * {@value #POSTS_A_TORCH}th fence post's top.
     */
    public List<BlockPos> torches() {
        List<BlockPos> torches = new ArrayList<>();
        for (int[] t : FIELD_TORCHES) {
            torches.add(new BlockPos(x0() + t[0], level + 1, z0() + t[1]));
        }
        List<BlockPos> posts = fence();
        for (int i = 0; i < posts.size(); i += POSTS_A_TORCH) {
            torches.add(posts.get(i).offset(0, 1, 0));
        }
        return torches;
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

    /** The ground inside its fence at its level, less its holes, the cell under its chest and its field torches': 216 cells. */
    public List<BlockPos> ground() {
        List<BlockPos> holes = new ArrayList<>(holes());
        for (int[] t : FIELD_TORCHES) {
            holes.add(new BlockPos(x0() + t[0], level, z0() + t[1]));
        }
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

    /**
     * The cells of its level its fixtures stand on: its fence lines - posts and gates - its field torches' and its chest's,
     * so its ground at its level is whole, the holes apart. A chunk flattened a block below its level has them to fill
     * as it has its {@link #ground} (operator, 2026-11-21: dirt at the level, on the floor).
     */
    public List<BlockPos> footings() {
        List<BlockPos> cells = new ArrayList<>();
        fence().forEach(p -> cells.add(p.offset(0, -1, 0)));
        gates().forEach(g -> cells.add(g.offset(0, -1, 0)));
        for (int[] t : FIELD_TORCHES) {
            cells.add(new BlockPos(x0() + t[0], level, z0() + t[1]));
        }
        cells.add(chest().offset(0, -1, 0));
        return cells;
    }

    /**
     * Its footings still to pave: what its fence, gates, field torches and chest stand on is cobblestone (operator,
     * 2026-11-23: cobblestone at the level unless it can't be - under the fence, and where the torches are; 2026-11-24:
     * the anti-entropy should fix that). One open, under water, or of ground a hoe tills - dirt, grass, farmland: live
     * (field712), the wheat farm's fence line and its torches' cells were dirt, so they were tilled and sown, and the
     * posts and torches went. Rock found there is left as it is.
     */
    public List<BlockPos> footingsToPave(WorldView world) {
        return footings().stream().filter(c -> !world.isCobblestone(c.x(), c.y(), c.z()) && (!world.isSolid(c.x(), c.y(), c.z())
                || world.isWater(c.x(), c.y(), c.z()) || world.isSoil(c.x(), c.y(), c.z()) || world.isFarmland(c.x(), c.y(), c.z()))).toList();
    }

    /**
     * What stands where its fixtures go, to be dug out (operator, 2026-11-24: blocks on fences - a compromised cell
     * mustn't spread): a block of ground, rock or a chest in a post's or a gate's cell, anything on a post's top but its
     * torch, anything in a field torch's cell. Live: dirt, grass and a chest in the fence line, cobblestone and a
     * furnace on posts, cobblestone in a gate's cell and a field torch's - steps and stations left by those passing.
     */
    public List<BlockPos> obstructions(WorldView world) {
        List<BlockPos> in = new ArrayList<>();
        for (BlockPos p : fence()) {
            if (wrong(world, p)) {
                in.add(p);
            }
            BlockPos top = p.offset(0, 1, 0);
            if (world.solidHere(top.x(), top.y(), top.z())) {
                in.add(top);
            }
        }
        gates().stream().filter(g -> wrong(world, g)).forEach(in::add);
        for (int[] t : FIELD_TORCHES) {
            BlockPos cell = new BlockPos(x0() + t[0], level + 1, z0() + t[1]);
            if (world.solidHere(cell.x(), cell.y(), cell.z())) {
                in.add(cell);
            }
        }
        return in;
    }

    /** Whether a post's or gate's cell {@code c} holds a block known not to be one: ground, rock or a chest. */
    private static boolean wrong(WorldView world, BlockPos c) {
        return world.isSoil(c.x(), c.y(), c.z()) || world.isCobblestone(c.x(), c.y(), c.z()) || world.isStone(c.x(), c.y(), c.z())
                || world.isChest(c.x(), c.y(), c.z());
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

    /** Sea level: the ground's top a farm is best at - the camp's area of influence is flattened to it. */
    public static final int SEA_LEVEL = 63;

    /**
     * The chunk to farm round {@code camp}'s, out to {@code rings} chunks - not the camp's own, nor one in {@code taken}:
     * of those flat enough - {@link #MIN_CELLS} of its ground at one level that makes farmland, on cells {@code walkable}
     * allows - the one nearest sea level, then the nearest the camp (operator, 2026-11-21: flat the requirement, sea level
     * the preference, close by).
     */
    public static Optional<ChunkFarm> site(WorldView world, BlockPos camp, int rings, Predicate<BlockPos> walkable,
                                           java.util.Set<Long> taken) {
        int cx = Math.floorDiv(camp.x(), SIDE), cz = Math.floorDiv(camp.z(), SIDE);
        ChunkFarm chosen = null;
        double score = Double.MAX_VALUE;
        for (int dx = -rings; dx <= rings; dx++) {
            for (int dz = -rings; dz <= rings; dz++) {
                if (dx == 0 && dz == 0 || taken.contains(key(cx + dx, cz + dz))) {
                    continue;
                }
                Optional<ChunkFarm> farm = levelled(world, cx + dx, cz + dz, camp.y(), walkable)
                        .filter(f -> f.plan(world, walkable).plot().size() >= MIN_CELLS);
                if (farm.isEmpty()) {
                    continue;
                }
                // Sea level first - a block of earthwork a cell of the chunk weighs more than a chunk's walk - then near.
                double s = Math.abs(farm.get().level() - SEA_LEVEL) * 100 + Math.hypot(dx, dz);
                if (s < score) {
                    chosen = farm.get();
                    score = s;
                }
            }
        }
        return Optional.ofNullable(chosen);
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
