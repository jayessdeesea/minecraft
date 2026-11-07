package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
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
 * Where a farm would go round a point, on the dev server's real ground: a spectator joins above it,
 * loads the chunks around it, and {@link FarmPlan#site} is run over them. Prints what it finds -
 * a survey, not a check. Needs the server (DEVELOPMENT.md § 1).
 *
 * <pre>mvn -pl app test -Dtest=FarmSiteLiveIT -Dsurefire.failIfNoSpecifiedTests=false [-Dsite.x=0 -Dsite.z=0 -Dsite.radius=48]</pre>
 */
class FarmSiteLiveIT {

    @Test
    void survey() throws Exception {
        int x = Integer.getInteger("site.x", 0);
        int z = Integer.getInteger("site.z", 0);
        int radius = Integer.getInteger("site.radius", 48);
        ChunkCache chunks = new ChunkCache();
        WorldModel world = new WorldModel(chunks, new EntityTracker());
        PlaySession session = MinecraftClient.join("localhost", 25565, "Surveyor", new PlaySessionListeners(
                update -> chunks.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), "Surveyor"),
                unload -> chunks.unload(unload.chunkX(), unload.chunkZ(), "Surveyor"), position -> { }, entity -> { }, move -> { },
                ids -> { }, health -> { }, inventory -> { }, chat -> { }, container -> { }, (registry, entries) -> { },
                blocks -> blocks.forEach(b -> chunks.setBlock(b.x(), b.y(), b.z(), b.stateId())), time -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);
        try {
            ChainScenarioLiveIT.consoleAllowingSilence("gamemode spectator Surveyor");
            ChainScenarioLiveIT.console("tp Surveyor %d 140 %d".formatted(x, z));
            for (int i = 0; i < 60 && chunks.loadedChunkCount() < 100; i++) {
                TimeUnit.MILLISECONDS.sleep(500);
                session.reportPosition(x + 0.5, 140, z + 0.5, 0, 0, false);
                session.endTick();
            }
            int ground = 140;
            while (ground > -60 && !world.isSolid(x, ground, z) && !world.isHazard(x, ground, z)) {
                ground--;
            }
            int sources = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int y = ground - 30; y <= ground + 30; y++) {
                        if (world.isWaterSource(x + dx, y, z + dz)) {
                            sources++;
                        }
                    }
                }
            }
            java.util.List<FarmPlan> candidates = new java.util.ArrayList<>();
            java.util.Map<Integer, Integer> byHeight = new java.util.TreeMap<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int y = ground - 30; y <= ground + 30; y++) {
                        if (world.isWaterSource(x + dx, y, z + dz)) {
                            byHeight.merge(y, 1, Integer::sum);
                            BlockPos water = new BlockPos(x + dx, y, z + dz);
                            candidates.add(new FarmPlan(water, FarmPlan.plotAround(world, water)));
                        }
                    }
                }
            }
            System.out.println("Water sources by height: " + byHeight);
            candidates.stream().sorted(java.util.Comparator.comparingInt((FarmPlan c) -> -c.plot().size())).limit(5)
                    .forEach(c -> System.out.printf("  candidate: water at %s, %d cells, %d blocks from the point%n", c.water(), c.plot().size(),
                            Math.round(Math.hypot(c.water().x() - x, c.water().z() - z))));
            Optional<FarmPlan> plan = FarmPlan.site(world, new BlockPos(x, ground, z), radius);
            System.out.printf("%nSurvey round (%d, %d): ground at y=%d, %d chunks loaded, %d water sources within %d blocks.%n",
                    x, z, ground, chunks.loadedChunkCount(), sources, radius);
            System.out.println(plan.map(p -> "Best farm: water at " + p.water() + ", " + p.plot().size() + " cells to plant, "
                    + Math.round(Math.hypot(p.water().x() - x, p.water().z() - z)) + " blocks from the point.")
                    .orElse("No water within reach of ground to till: a dry farm, or none."));
        } finally {
            session.close();
        }
    }
}
