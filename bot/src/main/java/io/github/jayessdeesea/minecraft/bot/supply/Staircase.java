package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A spiral staircase cut down into the ground (docs/military-doctrine.md §16): one block down a step,
 * round the eight cells about a central pillar, so the whole shaft stays inside 3x3 and every eight
 * steps it passes under its own top, five blocks of rock between turns. Each step is three cells
 * cleared - feet, head, and the head room a body needs to step down onto it - and its floor is left
 * standing. Steps go one block down and sideways, so a soldier walks down and jumps back up them:
 * no ladder, and a slip is a fall of one block. Nothing is crafted on the stairs: the mine base's
 * workshop is at the landing ({@code bot.engineer.Workshop}), and above it the squad's own.
 *
 * <p>At the bottom, the tunnels the ore is mined from: a main tunnel {@value #MAIN_TUNNEL} long a
 * stretch straight out from the last step, away from the shaft, then branches off it both ways every
 * {@value #BRANCH_EVERY} blocks, each {@value #BRANCH} long - two blocks of rock between branches,
 * so every block of it is seen from one tunnel or the next. All two high, floors left standing. A
 * mine worked out is run on a stretch at a time ({@link #extended}), up to {@link #MAX_STRETCHES}.
 *
 * <p>Or a <b>well</b> ({@link #well}; operator, 2026-11-15): 4x4, centred in its chunk -
 * chunk-local x and z 6..9 - round a 2x2 pillar at 7..8, its walkway the twelve cells round it,
 * one step down a cell, twelve blocks a turn, its north-east corner (9, 6) at every y = 12k, and
 * its landing there. Mines cut before keep the spiral they were cut to.
 *
 * @param head       the cell a soldier stands in at the top: on the pillar, or a well's first cell
 * @param bottom     the height the feet reach on the last step
 * @param stretches  the main tunnel's length, in stretches of {@link #MAIN_TUNNEL}
 * @param well       a 4x4 well to the standard, not the 3x3 spiral
 */
public record Staircase(BlockPos head, int bottom, int stretches, boolean well) {

    /** A spiral staircase whose main tunnel is one stretch. */
    public Staircase(BlockPos head, int bottom) {
        this(head, bottom, 1, false);
    }

    /** A spiral staircase. */
    public Staircase(BlockPos head, int bottom, int stretches) {
        this(head, bottom, stretches, false);
    }

    /**
     * The well's walkway, chunk-local, in the order the stairs go down it: clockwise from the
     * north-east corner, each beside the last.
     */
    private static final int[][] WELL_RING = {{9, 6}, {9, 7}, {9, 8}, {9, 9}, {8, 9}, {7, 9}, {6, 9}, {6, 8}, {6, 7}, {6, 6}, {7, 6}, {8, 6}};
    /** Every so many blocks the well's north-east corner comes round: a turn of its twelve. */
    public static final int WELL_TURN = 12;
    /** The well's first and last chunk-local x and z, and its pillar's. */
    public static final int WELL_MIN = 6, WELL_MAX = 9, PILLAR_MIN = 7, PILLAR_MAX = 8;

    /** The well's walkway cell the feet are in at height {@code y}, in chunk ({@code chunkX}, {@code chunkZ}). */
    static BlockPos wellCell(int chunkX, int chunkZ, int y) {
        int[] local = WELL_RING[Math.floorMod(-y, WELL_TURN)];
        return new BlockPos(chunkX * 16 + local[0], y, chunkZ * 16 + local[1]);
    }

    /**
     * A well in chunk ({@code chunkX}, {@code chunkZ}), its head at height {@code headY}, its
     * landing at the deepest y = 12k no deeper than {@code depth} nor {@link #DEEPEST}.
     */
    public static Staircase well(int chunkX, int chunkZ, int headY, int depth) {
        return new Staircase(wellCell(chunkX, chunkZ, headY), wellBottom(depth, headY), 1, true);
    }

    /** A well's landing: the deepest y = 12k at or above {@code depth}, {@link #DEEPEST} and under the head. */
    public static int wellBottom(int depth, int headY) {
        int floor = Math.floorDiv(DEEPEST + WELL_TURN - 1, WELL_TURN) * WELL_TURN;
        return Math.max(floor, Math.floorDiv(Math.min(depth, headY - 1), WELL_TURN) * WELL_TURN);
    }

    /** Whether column ({@code x}, {@code z}) is in the shaft: a well's 4x4, a spiral's 3x3. */
    public boolean inShaft(int x, int z) {
        if (well) {
            int lx = x - Math.floorDiv(head.x(), 16) * 16, lz = z - Math.floorDiv(head.z(), 16) * 16;
            return lx >= WELL_MIN && lx <= WELL_MAX && lz >= WELL_MIN && lz <= WELL_MAX;
        }
        return Math.abs(x - head.x()) <= 1 && Math.abs(z - head.z()) <= 1;
    }

    /** Whether column ({@code x}, {@code z}) is the pillar's: a well's 2x2, a spiral's one. */
    public boolean inPillar(int x, int z) {
        if (well) {
            int lx = x - Math.floorDiv(head.x(), 16) * 16, lz = z - Math.floorDiv(head.z(), 16) * 16;
            return lx >= PILLAR_MIN && lx <= PILLAR_MAX && lz >= PILLAR_MIN && lz <= PILLAR_MAX;
        }
        return x == head.x() && z == head.z();
    }

    /** Out from the shaft from the last step, along an axis it's already off centre on: never back through it. */
    private int[] outward() {
        if (well) {
            // Out through the wall the walkway's on at the landing (WELL_RING's order): east down the
            // east side from the north-east corner, south along the south, west, then north. At the
            // levels y % 4 == 0 that's east, south and west in turn, so levels one above another run
            // off different ways (the rules of a mine).
            int i = Math.floorMod(-bottom, WELL_TURN);
            return i <= 3 ? new int[]{1, 0} : i <= 6 ? new int[]{0, 1} : i <= 9 ? new int[]{-1, 0} : new int[]{0, -1};
        }
        int[] d = RING[Math.floorMod(steps() - 1, RING.length)];
        return d[0] != 0 ? new int[]{d[0], 0} : new int[]{0, d[1]};
    }

    /**
     * The most stretches a main tunnel runs to: 192 blocks, a walk of a minute from the landing. A mine
     * is run wider before it's left (operator, 2026-11-17): every move to a fresh site cost a night of
     * stairs and a long walk back at dawn (field346-355).
     */
    public static final int MAX_STRETCHES = 8;

    /** The main tunnel's length in blocks. */
    public int tunnel() {
        return stretches * MAIN_TUNNEL;
    }

    /** How much deeper a mine's next level is cut: fresh rock a level under the last one's branches. */
    public static final int LEVEL_DROP = 16;
    /** The heights a mine's levels are at: a multiple of this (the rules of a mine: y % 4 == 0). */
    public static final int LEVEL_EVERY = 4;

    /** This mine's stairs down to a level at {@code level} instead - the same steps, its own tunnels there - if it can be. */
    public java.util.Optional<Staircase> atLevel(int level) {
        if (level >= head.y() || level < DEEPEST || well && Math.floorMod(level, LEVEL_EVERY) != 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Staircase(head, level, 1, well));
    }

    /**
     * This mine a level deeper (operator, 2026-11-16: go deeper, or pick other sites): its stairs cut
     * on down {@link #LEVEL_DROP} blocks from the same head, a fresh main tunnel and branches there;
     * empty once that would be under {@link #DEEPEST}. The stairs above are the same steps.
     */
    public java.util.Optional<Staircase> deeper() {
        int next = bottom - LEVEL_DROP;
        return next < DEEPEST ? java.util.Optional.empty() : java.util.Optional.of(new Staircase(head, next, 1, well));
    }

    /** This mine with its main tunnel run on a stretch, its branches with it; empty at {@link #MAX_STRETCHES}. */
    public java.util.Optional<Staircase> extended() {
        return stretches >= MAX_STRETCHES ? java.util.Optional.empty() : java.util.Optional.of(new Staircase(head, bottom, stretches + 1, well));
    }

    /**
     * The tunnels miner {@code miner} of {@code miners} on this mine cuts: -1 the stairs, 0 the main
     * tunnel, then the branches. The first cuts the stairs and the main tunnel; the branches go in
     * contiguous stretches along the main tunnel, one to each miner, both sides of it - so each
     * works its own stretch of the mine, never at another's root. One with no stretch - more miners
     * than branch roots - has nothing. Live, four squads' cutters on one mine each worked the whole
     * of it and stood on one another (operator, 2026-11-15).
     */
    public Set<Integer> share(int miner, int miners) {
        if (miners < 1 || miner < 0 || miner >= miners) {
            throw new IllegalArgumentException("miner " + miner + " of " + miners);
        }
        Set<Integer> tunnels = new java.util.HashSet<>();
        if (miner == 0) {
            tunnels.add(-1);
            tunnels.add(0);
        }
        // Two to a level, one to each side of the spine (the rules of a mine): the first the strips
        // to its one side, the second the other's - never two in one strip, nor at one root.
        if (miners == 2) {
            for (int t = 1; t <= branches(); t++) {
                if (t % 2 == (miner == 0 ? 1 : 0)) {
                    tunnels.add(t);
                }
            }
            return Set.copyOf(tunnels);
        }
        int roots = branches() / 2;
        for (int root = miner * roots / miners; root < (miner + 1) * roots / miners; root++) {
            // Tunnel 2r+1 is root r's branch to one side, 2r+2 the other's (cuts()).
            tunnels.add(2 * root + 1);
            tunnels.add(2 * root + 2);
        }
        return Set.copyOf(tunnels);
    }

    /** The main tunnel's far cell, where the feet are: kept on disk, it says how far it was run. */
    public BlockPos tunnelEnd() {
        BlockPos last = step(steps() - 1);
        int[] out = outward();
        int dx = out[0], dz = out[1];
        return last.offset(dx * tunnel(), 0, dz * tunnel());
    }

    /** The staircase from {@code head} down to {@code lastStep}, its main tunnel run to {@code end}: as kept on disk. */
    public static Staircase kept(BlockPos head, BlockPos lastStep, java.util.Optional<BlockPos> end) {
        return kept(head, lastStep, end, false);
    }

    /** As {@link #kept(BlockPos, BlockPos, Optional)}, a well's or a spiral's. */
    public static Staircase kept(BlockPos head, BlockPos lastStep, java.util.Optional<BlockPos> end, boolean well) {
        Staircase one = new Staircase(head, lastStep.y(), 1, well);
        return end.map(e -> new Staircase(head, lastStep.y(), Math.max(1, Math.min(MAX_STRETCHES,
                Math.round((Math.abs(e.x() - lastStep.x()) + Math.abs(e.z() - lastStep.z())) / (float) MAIN_TUNNEL))), well)).orElse(one);
    }

    /** The eight cells round the pillar, each beside the last: north, then clockwise. */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    /** Deepest a staircase goes: a few blocks clear of bedrock's ragged top. */
    public static final int DEEPEST = -58;
    /** The main tunnel's length, out from the last step. */
    public static final int MAIN_TUNNEL = 24;
    /** Blocks along the main tunnel between branches. */
    public static final int BRANCH_EVERY = 3;
    /** Each branch's length. */
    public static final int BRANCH = 8;

    /**
     * One cut of the mine: stand in {@code stand} and clear {@code cells}, top first; their floor is
     * left. {@code tunnel} is -1 for a step of the stairs, else which tunnel it's in: 0 the main
     * tunnel, then each branch - one blocked is given up, the rest go on.
     */
    public record Cut(BlockPos stand, List<BlockPos> cells, int tunnel, boolean recess) {

        /** A cut of the stairs or a tunnel - no recess. */
        public Cut(BlockPos stand, List<BlockPos> cells, int tunnel) {
            this(stand, cells, tunnel, false);
        }

        /** Where the feet are once it's cut. */
        public BlockPos feet() {
            return cells.getLast();
        }

        /**
         * Whether {@code cell} of this cut is still to cut. A recess's table, furnace or chest is left
         * standing - what was put there; anywhere else a table or furnace is in the way and is cut -
         * live (field324), the camp's stairs were laid through its workshop's table, and the miner
         * stood at step 1 of 184 - and only a chest, whose contents would spill, is left.
         */
        public boolean toCut(WorldView world, BlockPos cell) {
            return recess ? uncut(world, cell) : world.isSolid(cell.x(), cell.y(), cell.z()) && !world.isChest(cell.x(), cell.y(), cell.z());
        }
    }

    /** The landing's two recesses, either side of the main tunnel's first cell: off the walkway. */
    public List<BlockPos> landing() {
        BlockPos first = landingStand();
        int[] out = outward();
        int dx = out[0], dz = out[1];
        return List.of(first.offset(-dz, 0, dx), first.offset(dz, 0, -dx));
    }

    /**
     * The store's two recesses, either side of the main tunnel's second cell: where the mine's stash
     * of ore is kept in chests, off the walkway, a step from the landing's table and furnace
     * (backlog/sites.md, the mine base's store).
     */
    public List<BlockPos> store() {
        BlockPos first = landingStand();
        int[] out = outward();
        int dx = out[0], dz = out[1];
        BlockPos second = first.offset(dx, 0, dz);
        return List.of(second.offset(-dz, 0, dx), second.offset(dz, 0, -dx));
    }

    /**
     * Whether {@code cell} is still to cut: solid, and not something put there - a table, a furnace
     * or a chest in a recess is left standing.
     */
    public static boolean uncut(WorldView world, BlockPos cell) {
        return world.isSolid(cell.x(), cell.y(), cell.z()) && world.workstation(cell.x(), cell.y(), cell.z()).isEmpty()
                && !world.isChest(cell.x(), cell.y(), cell.z());
    }

    /** Where a soldier stands to work at the landing: the main tunnel's first cell, between the recesses. */
    public BlockPos landingStand() {
        int[] out = outward();
        return step(steps() - 1).offset(out[0], 0, out[1]);
    }

    /**
     * Where the stairs and tunnels are lit, so nothing spawns in them: a torch on a step once a
     * turn, halfway round, one at the landing's stand, one every {@link #TUNNEL_LIGHT_EVERY} along
     * the main tunnel, and one {@link #BRANCH_LIGHT} into every other pair of branches - a torch
     * lights 7 blocks either way, and the pairs between are 3 off. A torch stands in the cell a body
     * walks through and stops none.
     */
    public List<BlockPos> lights() {
        List<BlockPos> lights = new ArrayList<>();
        // A spiral's once a turn, halfway round; a well's every half turn, its walkway half as long again.
        int every = well ? WELL_TURN / 2 : RING.length;
        for (int k = every / 2; k < steps(); k += every) {
            lights.add(step(k));
        }
        lights.add(landingStand());
        BlockPos last = step(steps() - 1);
        int[] out = outward();
        int dx = out[0], dz = out[1];
        for (int i = TUNNEL_LIGHT_EVERY; i <= tunnel(); i += TUNNEL_LIGHT_EVERY) {
            lights.add(last.offset(dx * i, 0, dz * i));
        }
        List<BlockPos> main = new ArrayList<>();
        for (int i = 1; i <= tunnel(); i++) {
            main.add(last.offset(dx * i, 0, dz * i));
        }
        List<Integer> roots = roots(main, dx, dz);
        for (int r = 0; r < roots.size(); r += 2) {
            BlockPos root = main.get(roots.get(r) - 1);
            for (int side : new int[]{1, -1}) {
                lights.add(root.offset(-dz * side * BRANCH_LIGHT, 0, dx * side * BRANCH_LIGHT));
            }
        }
        return List.copyOf(lights);
    }

    /** Blocks along the main tunnel between its torches. */
    static final int TUNNEL_LIGHT_EVERY = 6;
    /** How far into a branch its torch goes: the middle of its 8. */
    static final int BRANCH_LIGHT = 4;

    /**
     * How many steps from the top the world shows cut: each one's cells open, or holding a table or
     * furnace. A cell nobody has loaded reads solid, so this never counts a step it can't see.
     */
    public int stepsCut(WorldView world) {
        for (int k = 0; k < steps(); k++) {
            for (BlockPos cell : cells(k)) {
                if (uncut(world, cell)) {
                    return k;
                }
            }
        }
        return steps();
    }

    /**
     * Whether the landing is cut: every step down to it open, and both recesses open or holding the
     * mine base's table and furnace. Live, a cave at the landing's depth left its recesses open
     * while the stairs were a third of the way down, and a soldier set off for a workshop it had
     * no way to reach.
     */
    public boolean landingCut(WorldView world) {
        for (int k = 0; k < steps(); k++) {
            for (BlockPos cell : cells(k)) {
                if (uncut(world, cell)) {
                    return false;
                }
            }
        }
        return landing().stream().allMatch(r -> (!world.isSolid(r.x(), r.y(), r.z()) || world.workstation(r.x(), r.y(), r.z()).isPresent())
                && !world.isSolid(r.x(), r.y() + 1, r.z()));
    }

    /**
     * Every cell a body walks through on the stairs and tunnels - the landing's recesses aside: kept
     * clear of tables, furnaces and other diggers.
     */
    public Set<BlockPos> walkway() {
        Set<BlockPos> recesses = new HashSet<>();
        landing().forEach(r -> {
            recesses.add(r);
            recesses.add(r.offset(0, 1, 0));
        });
        Set<BlockPos> cells = new HashSet<>();
        cuts().forEach(c -> c.cells().stream().filter(cell -> !recesses.contains(cell)).forEach(cells::add));
        return cells;
    }

    /** Every cell the mine cuts, meant to be open. */
    public Set<BlockPos> cutCells() {
        Set<BlockPos> cells = new HashSet<>();
        cuts().forEach(c -> cells.addAll(c.cells()));
        return cells;
    }

    /** Every cut, in order: the steps down, the main tunnel, then the branches nearest the shaft first. */
    public List<Cut> cuts() {
        List<Cut> cuts = new ArrayList<>();
        for (int k = 0; k < steps(); k++) {
            cuts.add(new Cut(above(k), cells(k), -1));
        }
        BlockPos last = step(steps() - 1);
        int[] out = outward();
        int dx = out[0], dz = out[1];
        List<BlockPos> main = new ArrayList<>();
        for (int i = 1; i <= tunnel(); i++) {
            BlockPos feet = last.offset(dx * i, 0, dz * i);
            cuts.add(new Cut(i == 1 ? last : main.getLast(), tunnelCells(feet), 0));
            main.add(feet);
            if (i == 1) {
                // The landing: a recess each side of the tunnel's first cell, off the walkway, where
                // a table and a furnace stand - there's no room for one anywhere on the stairs.
                for (BlockPos recess : landing()) {
                    cuts.add(new Cut(feet, List.of(recess.offset(0, 1, 0), recess), 0, true));
                }
            }
            if (i == 2) {
                // The store: a recess each side of the second cell, for the stash's chests.
                for (BlockPos recess : store()) {
                    cuts.add(new Cut(feet, List.of(recess.offset(0, 1, 0), recess), 0, true));
                }
            }
        }
        int tunnel = 1;
        for (int i : roots(main, dx, dz)) {
            BlockPos root = main.get(i - 1);
            for (int side : new int[]{1, -1}) {
                BlockPos stand = root;
                for (int j = 1; j <= BRANCH; j++) {
                    BlockPos feet = root.offset(-dz * side * j, 0, dx * side * j);
                    cuts.add(new Cut(stand, tunnelCells(feet), tunnel));
                    stand = feet;
                }
                tunnel++;
            }
        }
        return List.copyOf(cuts);
    }

    /** A tunnel's height (the rules of a mine): three blocks, y to y + 2, so the rock between strips and levels is all seen. */
    public static final int TUNNEL_HEIGHT = 3;

    /** The cells a tunnel's cut at {@code feet} clears, top first, the feet last. */
    private static List<BlockPos> tunnelCells(BlockPos feet) {
        return List.of(feet.offset(0, 2, 0), feet.offset(0, 1, 0), feet);
    }

    /**
     * The main tunnel's cells a strip goes off at, 1-based: where the world's coordinate along it is a
     * multiple of {@link #BRANCH_EVERY} (the rules of a mine: x % 3 == 0 along x, z % 3 == 0 along z) -
     * the grid the world's, not each mine's own - none in its first two cells, the landing's and the
     * store's recesses.
     */
    private static List<Integer> roots(List<BlockPos> main, int dx, int dz) {
        List<Integer> roots = new ArrayList<>();
        for (int i = 3; i <= main.size(); i++) {
            BlockPos cell = main.get(i - 1);
            if (Math.floorMod(dx != 0 ? cell.x() : cell.z(), BRANCH_EVERY) == 0) {
                roots.add(i);
            }
        }
        return roots;
    }

    /** How many strips the mine's main tunnel has off it, both sides counted. */
    int branches() {
        BlockPos last = step(steps() - 1);
        int[] out = outward();
        List<BlockPos> main = new ArrayList<>();
        for (int i = 1; i <= tunnel(); i++) {
            main.add(last.offset(out[0] * i, 0, out[1] * i));
        }
        return 2 * roots(main, out[0], out[1]).size();
    }

    public Staircase {
        if (stretches < 1 || stretches > MAX_STRETCHES) {
            throw new IllegalArgumentException("a main tunnel of 1 to " + MAX_STRETCHES + " stretches, not " + stretches);
        }
        if (bottom >= head.y() || bottom < DEEPEST) {
            throw new IllegalArgumentException("a staircase from " + head + " goes down, to no deeper than " + DEEPEST + ", not " + bottom);
        }
        if (well && (!head.equals(wellCell(Math.floorDiv(head.x(), 16), Math.floorDiv(head.z(), 16), head.y()))
                || Math.floorMod(bottom, LEVEL_EVERY) != 0)) {
            throw new IllegalArgumentException("a well's head is its walkway's cell at its height, and its landing at y = 4k: "
                    + head + " to " + bottom);
        }
    }

    /** How many steps down. */
    public int steps() {
        return head.y() - bottom;
    }

    /** Where the feet are on step {@code k} (0 the first down from the head): one block lower each step. */
    public BlockPos step(int k) {
        if (well) {
            return wellCell(Math.floorDiv(head.x(), 16), Math.floorDiv(head.z(), 16), head.y() - 1 - k);
        }
        int[] d = RING[Math.floorMod(k, RING.length)];
        return new BlockPos(head.x() + d[0], head.y() - 1 - k, head.z() + d[1]);
    }

    /** Where a soldier stands to cut step {@code k}: the step above it, or the head. */
    public BlockPos above(int k) {
        return k == 0 ? head : step(k - 1);
    }

    /**
     * The cells step {@code k} clears: its feet, its head, and the head room over them to step down
     * into it. The feet last.
     */
    public List<BlockPos> cells(int k) {
        BlockPos feet = step(k);
        return List.of(feet.offset(0, 2, 0), feet.offset(0, 1, 0), feet);
    }

    /** How far from the place asked a staircase is sited, when the ground there won't take one. */
    public static final int SITE_RADIUS = 12;

    /**
     * A staircase down to {@code depth} (no deeper than {@link #DEEPEST}) as near ({@code x},
     * {@code z}) as one can be cut all the way: every step clear of water and lava beside it and
     * with a floor under it, as the world shows them. The nearest such head within
     * {@link #SITE_RADIUS}, or empty with none.
     */
    public static Optional<Staircase> site(WorldView world, int x, int z, int nearY, int depth) {
        for (int r = 0; r <= SITE_RADIUS; r++) {
            Optional<Staircase> best = Optional.empty();
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Optional<BlockPos> head = headAt(world, x + dx, z + dz, nearY);
                    if (head.isEmpty()) {
                        continue;
                    }
                    int bottom = Math.max(DEEPEST, Math.min(depth, head.get().y() - 1));
                    Staircase stairs = new Staircase(head.get(), bottom);
                    double distance = Math.hypot(dx, dz);
                    if (distance < bestDistance && stairs.clear(world)) {
                        best = Optional.of(stairs);
                        bestDistance = distance;
                    }
                }
            }
            if (best.isPresent()) {
                return best;
            }
        }
        return Optional.empty();
    }

    /**
     * A well (the standard: {@link #well}) in the chunk holding ({@code x}, {@code z}), else the
     * nearest of the eight round it, that can be cut all the way down to its landing by the world as
     * it shows it - its head the first walkway cell, from {@code nearY} up and down 24, a soldier
     * can stand in. Empty with none.
     */
    public static Optional<Staircase> siteWell(WorldView world, int x, int z, int nearY, int depth) {
        return siteWell(world, x, z, nearY, depth, head -> false);
    }

    /**
     * As {@link #siteWell(WorldView, int, int, int, int)}, never a well whose head {@code dead} names - one
     * worked out or blocked already. Live (field353), the keep's mine moved 32 west of a blocked well,
     * was sited back in it, the chunk beside, and moved on again without ever being cut.
     */
    public static Optional<Staircase> siteWell(WorldView world, int x, int z, int nearY, int depth,
                                               java.util.function.Predicate<BlockPos> dead) {
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        List<int[]> chunks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add(new int[]{cx + dx, cz + dz});
            }
        }
        chunks.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - x, c[1] * 16 + 8 - z)));
        for (int[] c : chunks) {
            Optional<Staircase> well = wellAt(world, c[0], c[1], nearY, depth);
            if (well.isPresent() && !dead.test(well.get().head()) && well.get().clear(world)) {
                return well;
            }
        }
        return Optional.empty();
    }

    /** The well chunk ({@code chunkX}, {@code chunkZ}) would have, its head the highest walkway cell a soldier stands in near {@code nearY}. */
    static Optional<Staircase> wellAt(WorldView world, int chunkX, int chunkZ, int nearY, int depth) {
        for (int y = nearY + 24; y >= nearY - 24; y--) {
            BlockPos head = wellCell(chunkX, chunkZ, y);
            if (Pathfinder.standable(world, head) && !world.isLog(head.x(), y - 1, head.z()) && world.leaves(head.x(), y - 1, head.z()).isEmpty()) {
                return wellBottom(depth, y) < y ? Optional.of(well(chunkX, chunkZ, y, depth)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Whether every step can be cut as the world shows it: no water or lava beside one, and a floor under each. */
    boolean clear(WorldView world) {
        return blocked(world).isEmpty();
    }

    /** Why a step can't be cut as the world shows it - the first one's water, lava or open cave - or empty if every one can. */
    public Optional<String> blocked(WorldView world) {
        List<Cut> all = cuts();
        for (int k = 0; k < steps(); k++) {
            Optional<String> why = Miner.hazard(world, all.get(k), k);
            if (why.isPresent()) {
                return why;
            }
        }
        return Optional.empty();
    }

    /** The well {@link #siteWell} tries in the chunk at ({@code x}, {@code z}) itself, and why it won't do. */
    public static String whyNotWell(WorldView world, int x, int z, int nearY, int depth) {
        return wellAt(world, Math.floorDiv(x, 16), Math.floorDiv(z, 16), nearY, depth)
                .map(w -> "from " + w.head() + " to y=" + w.bottom() + ": " + w.blocked(world).orElse("clear"))
                .orElse("no walkway cell to stand on within 24 blocks of y=" + nearY);
    }

    /** The staircase {@link #site} tries at ({@code x}, {@code z}) itself, and why it won't do - for saying why none was sited. */
    public static String whyNot(WorldView world, int x, int z, int nearY, int depth) {
        Optional<BlockPos> head = headAt(world, x, z, nearY);
        if (head.isEmpty()) {
            return "no ground to stand on within 24 blocks of y=" + nearY;
        }
        Staircase stairs = new Staircase(head.get(), Math.max(DEEPEST, Math.min(depth, head.get().y() - 1)));
        return "from " + head.get() + " to y=" + stairs.bottom() + ": " + stairs.blocked(world).orElse("clear");
    }

    /**
     * The ground at ({@code x}, {@code z}): the highest standing cell within 24 blocks of {@code nearY}
     * on something other than a tree. Live, an oak grew on the camp's mine head, and a staircase sited
     * from its canopy found the open air under the leaves no floor.
     */
    public static Optional<BlockPos> headAt(WorldView world, int x, int z, int nearY) {
        for (int y = nearY + 24; y >= nearY - 24; y--) {
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell) && !world.isLog(x, y - 1, z) && world.leaves(x, y - 1, z).isEmpty()) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
