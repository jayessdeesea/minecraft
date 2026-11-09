package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.List;
import java.util.Optional;

/**
 * A spiral staircase cut down into the ground (docs/military-doctrine.md §16): one block down a step,
 * round the eight cells about a central pillar, so the whole shaft stays inside 3x3 and every eight
 * steps it passes under its own top, five blocks of rock between turns. Each step is three cells
 * cleared - feet, head, and the head room a body needs to step down onto it - and its floor is left
 * standing. Steps go one block down and sideways, so a soldier walks down and jumps back up them:
 * no ladder, and a slip is a fall of one block. Nothing is crafted on the stairs: the mine base's
 * workshop is at the landing ({@code bot.engineer.Workshop}), and above it the squad's own.
 *
 * <p>At the bottom, the tunnels the ore is mined from: a main tunnel {@value #MAIN_TUNNEL} long
 * straight out from the last step, away from the shaft, then branches off it both ways every
 * {@value #BRANCH_EVERY} blocks, each {@value #BRANCH} long - two blocks of rock between branches,
 * so every block of it is seen from one tunnel or the next. All two high, floors left standing.
 *
 * @param head    the cell a soldier stands in at the top, on the pillar
 * @param bottom  the height the feet reach on the last step
 */
public record Staircase(BlockPos head, int bottom) {

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
    public record Cut(BlockPos stand, List<BlockPos> cells, int tunnel) {

        /** Where the feet are once it's cut. */
        public BlockPos feet() {
            return cells.getLast();
        }
    }

    /** The landing's two recesses, either side of the main tunnel's first cell: off the walkway. */
    public List<BlockPos> landing() {
        BlockPos first = landingStand();
        int[] d = RING[Math.floorMod(steps() - 1, RING.length)];
        int dx = d[0] != 0 ? d[0] : 0;
        int dz = d[0] != 0 ? 0 : d[1];
        return List.of(first.offset(-dz, 0, dx), first.offset(dz, 0, -dx));
    }

    /** Where a soldier stands to work at the landing: the main tunnel's first cell, between the recesses. */
    public BlockPos landingStand() {
        BlockPos last = step(steps() - 1);
        int[] d = RING[Math.floorMod(steps() - 1, RING.length)];
        return d[0] != 0 ? last.offset(d[0], 0, 0) : last.offset(0, 0, d[1]);
    }

    /**
     * Where the stairs are lit, so nothing spawns on them: a torch on a step once a turn, halfway
     * round, and one at the landing's stand. A torch stands in the cell a body walks through and
     * stops none.
     */
    public List<BlockPos> lights() {
        List<BlockPos> lights = new java.util.ArrayList<>();
        for (int k = RING.length / 2; k < steps(); k += RING.length) {
            lights.add(step(k));
        }
        lights.add(landingStand());
        return List.copyOf(lights);
    }

    /** Whether the landing is cut: both recesses open, or holding the mine base's table and furnace. */
    public boolean landingCut(WorldView world) {
        return landing().stream().allMatch(r -> (!world.isSolid(r.x(), r.y(), r.z()) || world.workstation(r.x(), r.y(), r.z()).isPresent())
                && !world.isSolid(r.x(), r.y() + 1, r.z()));
    }

    /**
     * Every cell a body walks through on the stairs and tunnels - the landing's recesses aside: kept
     * clear of tables, furnaces and other diggers.
     */
    public java.util.Set<BlockPos> walkway() {
        java.util.Set<BlockPos> recesses = new java.util.HashSet<>();
        landing().forEach(r -> {
            recesses.add(r);
            recesses.add(r.offset(0, 1, 0));
        });
        java.util.Set<BlockPos> cells = new java.util.HashSet<>();
        cuts().forEach(c -> c.cells().stream().filter(cell -> !recesses.contains(cell)).forEach(cells::add));
        return cells;
    }

    /** Every cut, in order: the steps down, the main tunnel, then the branches nearest the shaft first. */
    public List<Cut> cuts() {
        List<Cut> cuts = new java.util.ArrayList<>();
        for (int k = 0; k < steps(); k++) {
            cuts.add(new Cut(above(k), cells(k), -1));
        }
        BlockPos last = step(steps() - 1);
        int[] d = RING[Math.floorMod(steps() - 1, RING.length)];
        // Out from the shaft along an axis it's already off centre on: never back through the 3x3.
        int dx = d[0] != 0 ? d[0] : 0;
        int dz = d[0] != 0 ? 0 : d[1];
        List<BlockPos> main = new java.util.ArrayList<>();
        for (int i = 1; i <= MAIN_TUNNEL; i++) {
            BlockPos feet = last.offset(dx * i, 0, dz * i);
            cuts.add(new Cut(i == 1 ? last : main.getLast(), List.of(feet.offset(0, 1, 0), feet), 0));
            main.add(feet);
            if (i == 1) {
                // The landing: a recess each side of the tunnel's first cell, off the walkway, where
                // a table and a furnace stand - there's no room for one anywhere on the stairs.
                for (BlockPos recess : landing()) {
                    cuts.add(new Cut(feet, List.of(recess.offset(0, 1, 0), recess), 0));
                }
            }
        }
        int tunnel = 1;
        for (int i = BRANCH_EVERY; i <= MAIN_TUNNEL; i += BRANCH_EVERY) {
            BlockPos root = main.get(i - 1);
            for (int side : new int[]{1, -1}) {
                BlockPos stand = root;
                for (int j = 1; j <= BRANCH; j++) {
                    BlockPos feet = root.offset(-dz * side * j, 0, dx * side * j);
                    cuts.add(new Cut(stand, List.of(feet.offset(0, 1, 0), feet), tunnel));
                    stand = feet;
                }
                tunnel++;
            }
        }
        return List.copyOf(cuts);
    }

    public Staircase {
        if (bottom >= head.y() || bottom < DEEPEST) {
            throw new IllegalArgumentException("a staircase from " + head + " goes down, to no deeper than " + DEEPEST + ", not " + bottom);
        }
    }

    /** How many steps down. */
    public int steps() {
        return head.y() - bottom;
    }

    /** Where the feet are on step {@code k} (0 the first down from the head): one block lower each step. */
    public BlockPos step(int k) {
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

    /** The staircase's head at the ground at ({@code x}, {@code z}): the highest standing cell within 24 blocks of {@code nearY}. */
    /** How far from the place asked a staircase is sited, when the ground there won't take one. */
    public static final int SITE_RADIUS = 8;

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

    /** Whether every step can be cut as the world shows it: no water or lava beside one, and a floor under each. */
    boolean clear(WorldView world) {
        List<Cut> all = cuts();
        for (int k = 0; k < steps(); k++) {
            if (Miner.hazard(world, all.get(k), k).isPresent()) {
                return false;
            }
        }
        return true;
    }

    public static Optional<BlockPos> headAt(WorldView world, int x, int z, int nearY) {
        for (int y = nearY + 24; y >= nearY - 24; y--) {
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
