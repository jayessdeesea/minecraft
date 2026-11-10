package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Prints why a mine staircase can or can't be sited round a spot in the live dev server: for every
 * head {@link Staircase#site} tries within {@link Staircase#SITE_RADIUS}, the first step it finds
 * blocked, or "clear". Read-only: joins as {@code Probe} and changes nothing.
 *
 * <pre>mvn -pl app test -Dtest=StaircaseProbeIT -Dsurefire.failIfNoSpecifiedTests=false -Dprobe=-44,67,40 -Ddepth=16</pre>
 */
class StaircaseProbeIT {

    @Test
    void probe() throws Exception {
        String[] at = System.getProperty("probe", "0,64,0").split(",");
        int px = Integer.parseInt(at[0].trim());
        int nearY = Integer.parseInt(at[1].trim());
        int pz = Integer.parseInt(at[2].trim());
        int depth = Integer.getInteger("depth", 16);
        ChunkCache cache = new ChunkCache();
        PlaySession session = MinecraftClient.join("localhost", 25565, "Probe", new PlaySessionListeners(
                update -> cache.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), "probe"),
                unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { },
                health -> { }, inventory -> { }, chat -> { }, container -> { }, (registry, entries) -> { }, blocks -> { }, time -> { }, death -> { }, cause -> { })).get(30, TimeUnit.SECONDS);
        try {
            Thread.sleep(8_000);
            WorldModel world = new WorldModel(cache, new EntityTracker());
            StringBuilder out = new StringBuilder(String.format("%nStaircases round (%d, %d), near y=%d, down to y=%d:%n", px, pz, nearY, depth));
            int r = Staircase.SITE_RADIUS;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    out.append(String.format("(%d, %d) %s%n", px + dx, pz + dz, Staircase.whyNot(world, px + dx, pz + dz, nearY, depth)));
                }
            }
            Optional<Staircase> sited = Staircase.site(world, px, pz, nearY, depth);
            out.append("site: ").append(sited.map(s -> s.head() + " to y=" + s.bottom()).orElse("none")).append('\n');
            System.out.println(out);
        } finally {
            session.close();
        }
    }
}
