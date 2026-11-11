package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A bounded, generated {@link WorldView}: a column of solid ground under every cell up to its
 * height, plus individually placed solid and hazard blocks (trees, walls, water). Everything
 * outside the bounds is solid, so a scenario is a closed box - nothing can wander off the map.
 */
public final class SyntheticWorld implements WorldView {

    public static final int MIN = -48;
    public static final int MAX = 47;

    private final int[][] ground = new int[MAX - MIN + 1][MAX - MIN + 1];
    private final Set<BlockPos> solids = new HashSet<>();
    private final Set<BlockPos> hazards = new HashSet<>();
    /** Ground cells dug out. */
    private final Set<BlockPos> dug = new HashSet<>();
    /** Whether the ground columns are soil, dug by hand - otherwise they're nothing a soldier digs. */
    private boolean soilGround;

    SyntheticWorld(int baseGround) {
        for (int[] column : ground) {
            Arrays.fill(column, baseGround);
        }
    }

    static boolean inBounds(int x, int z) {
        return x >= MIN && x <= MAX && z >= MIN && z <= MAX;
    }

    /** The y of the top solid block of the ground column - a soldier stands at {@code ground + 1}. */
    public int ground(int x, int z) {
        return ground[x - MIN][z - MIN];
    }

    void setGround(int x, int z, int y) {
        if (inBounds(x, z)) {
            ground[x - MIN][z - MIN] = y;
        }
    }

    void solid(int x, int y, int z) {
        if (inBounds(x, z)) {
            hazards.remove(new BlockPos(x, y, z));
            solids.add(new BlockPos(x, y, z));
        }
    }

    /** Makes the ground columns soil: steps can be cut into them. */
    void soilGround() {
        soilGround = true;
    }

    /** Digs out a block, as the server would on a finished dig. */
    void dig(int x, int y, int z) {
        solids.remove(new BlockPos(x, y, z));
        if (inBounds(x, z) && y <= ground(x, z)) {
            dug.add(new BlockPos(x, y, z));
        }
    }

    void hazard(int x, int y, int z) {
        if (inBounds(x, z)) {
            hazards.add(new BlockPos(x, y, z));
        }
    }

    /** Clears placed blocks in a column above the ground - keeps start and objective standable. */
    void clearAbove(int x, int z) {
        int g = ground(x, z);
        for (int y = g + 1; y < g + 12; y++) {
            solids.remove(new BlockPos(x, y, z));
            hazards.remove(new BlockPos(x, y, z));
        }
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        if (!inBounds(x, z)) {
            return true;
        }
        return y <= ground(x, z) && !dug.contains(new BlockPos(x, y, z)) || solids.contains(new BlockPos(x, y, z));
    }

    @Override
    public boolean isSoil(int x, int y, int z) {
        return soilGround && inBounds(x, z) && y <= ground(x, z) && !dug.contains(new BlockPos(x, y, z));
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        return hazards.contains(new BlockPos(x, y, z));
    }

    @Override
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        return List.of();
    }

    /**
     * Top-down map for failure messages, north at the top: {@code .} open ground, digits the
     * ground height relative to the start (mod 10), {@code #} a placed obstacle at body height,
     * {@code ~} water, {@code S}/{@code G} start and objective, {@code *} the soldier's trail.
     */
    public String render(BlockPos start, BlockPos goal, Collection<BlockPos> trail) {
        Set<Long> trailCells = new HashSet<>();
        for (BlockPos p : trail) {
            trailCells.add(key(p.x(), p.z()));
        }
        int base = ground(start.x(), start.z());
        StringBuilder out = new StringBuilder();
        for (int z = MIN; z <= MAX; z++) {
            for (int x = MIN; x <= MAX; x++) {
                int g = ground(x, z);
                char c;
                if (x == start.x() && z == start.z()) {
                    c = 'S';
                } else if (x == goal.x() && z == goal.z()) {
                    c = 'G';
                } else if (trailCells.contains(key(x, z))) {
                    c = '*';
                } else if (hazards.contains(new BlockPos(x, g + 1, z))) {
                    c = '~';
                } else if (solids.contains(new BlockPos(x, g + 1, z)) || solids.contains(new BlockPos(x, g + 2, z))) {
                    c = '#';
                } else if (g == base) {
                    c = '.';
                } else {
                    c = Character.forDigit(Math.floorMod(g - base, 10), 10);
                }
                out.append(c);
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
