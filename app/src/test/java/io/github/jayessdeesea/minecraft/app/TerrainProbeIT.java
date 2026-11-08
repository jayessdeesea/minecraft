package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Prints how our terrain model sees the blocks around a spot in the live dev server - for
 * diagnosing why a soldier is stuck somewhere. Joins as {@code Probe}, waits for chunks, then
 * prints one top-down slice per height: {@code #} solid, {@code ~} hazard, {@code .} open,
 * {@code ?} not loaded, {@code @} the probed cell.
 *
 * <pre>mvn -pl app test -Dtest=TerrainProbeIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe=84,67,29</pre>
 */
class TerrainProbeIT {

    private static final int RADIUS = 6;

    @Test
    void probe() throws Exception {
        String[] at = System.getProperty("probe", "0,64,0").split(",");
        int px = Integer.parseInt(at[0].trim());
        int py = Integer.parseInt(at[1].trim());
        int pz = Integer.parseInt(at[2].trim());
        ChunkCache cache = new ChunkCache();
        PlaySession session = MinecraftClient.join("localhost", 25565, "Probe", new PlaySessionListeners(
                update -> cache.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), "probe"),
                unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { },
                health -> { }, inventory -> { }, chat -> { }, container -> { }, (registry, entries) -> { }, blocks -> { }, time -> { }, death -> { }, cause -> { })).get(30, TimeUnit.SECONDS);
        try {
            Thread.sleep(8_000);
            StringBuilder out = new StringBuilder(String.format("%nAround (%d, %d, %d), north at the top, x increasing to the right:%n", px, py, pz));
            for (int y = py + 3; y >= py - 3; y--) {
                out.append(String.format("y=%d%n", y));
                for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
                    for (int x = px - RADIUS; x <= px + RADIUS; x++) {
                        char c;
                        if (cache.stateIdAt(x, y, z).isEmpty()) {
                            c = '?';
                        } else if (x == px && y == py && z == pz) {
                            c = '@';
                        } else if (cache.isHazard(x, y, z)) {
                            c = '~';
                        } else if (cache.isSolid(x, y, z)) {
                            c = '#';
                        } else {
                            c = '.';
                        }
                        out.append(c);
                    }
                    out.append(String.format("   %s%n", z == pz ? "<- z=" + pz : ""));
                }
            }
            for (int y = py + 1; y >= py - 1; y--) {
                out.append(String.format("state ids at y=%d along z=%d: ", y, pz));
                for (int x = px - 2; x <= px + 2; x++) {
                    out.append(cache.stateIdAt(x, y, pz).orElse(-1)).append(' ');
                }
                out.append('\n');
            }
            WorldModel world = new WorldModel(cache, new EntityTracker());
            Set<BlockPos> reachable = Pathfinder.reachable(world, new BlockPos(px, py, pz), 48, 200_000);
            out.append(String.format("%n%d cells reachable from (%d, %d, %d) within 48 blocks (Pathfinder.reachable).%n",
                    reachable.size(), px, py, pz));
            out.append("Top-down, R reachable at some height, . not, @ start:\n");
            for (int z = pz - 24; z <= pz + 24; z++) {
                for (int x = px - 24; x <= px + 24; x++) {
                    final int fx = x;
                    final int fz = z;
                    boolean r = reachable.stream().anyMatch(c -> c.x() == fx && c.z() == fz);
                    out.append(x == px && z == pz ? '@' : r ? 'R' : '.');
                }
                out.append('\n');
            }
            System.out.println(out);
        } finally {
            session.close();
        }
    }
}
