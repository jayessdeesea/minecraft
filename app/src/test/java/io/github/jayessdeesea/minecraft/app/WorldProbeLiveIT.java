package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The dev server's real ground, as one connection sees it. A spectator joins above a point, loads
 * the chunks around it, and {@link FarmPlan#site} is run over them; or, with {@code probe.name}, a
 * soldier joins where it last stood. Then, as asked: a terrain survey ({@code survey.at}), or a
 * route planned and walked by a simulated soldier ({@code route}) - DEVELOPMENT.md § 3, "When a
 * soldier is stuck". Prints what it finds - a probe, not a check. Needs the server (§ 1).
 *
 * <pre>mvn -pl app test -Dtest=WorldProbeLiveIT -Dsurefire.failIfNoSpecifiedTests=false [-Dsite.x=0 -Dsite.z=0 -Dsite.radius=48]</pre>
 */
class WorldProbeLiveIT {

    @Test
    void survey() throws Exception {
        int x = Integer.getInteger("site.x", 0);
        int z = Integer.getInteger("site.z", 0);
        int radius = Integer.getInteger("site.radius", 48);
        ChunkCache chunks = new ChunkCache();
        WorldModel world = new WorldModel(chunks, new EntityTracker());
        // As any player - a soldier's own name joins where it last stood, seeing what it sees.
        String name = System.getProperty("probe.name", "Surveyor");
        java.util.concurrent.atomic.AtomicReference<Position> joinedAt = new java.util.concurrent.atomic.AtomicReference<>();
        PlaySession session = MinecraftClient.join("localhost", 25565, name, new PlaySessionListeners(
                update -> chunks.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), name),
                unload -> chunks.unload(unload.chunkX(), unload.chunkZ(), name),
                position -> joinedAt.compareAndSet(null, new Position(position.x(), position.y(), position.z())), entity -> { }, move -> { },
                ids -> { }, health -> { }, inventory -> { }, chat -> { }, container -> { }, (registry, entries) -> { },
                blocks -> blocks.forEach(b -> chunks.setBlock(b.x(), b.y(), b.z(), b.stateId())), time -> { }, death -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);
        try {
            boolean asSoldier = !name.equals("Surveyor");
            if (!asSoldier) {
                ChainScenarioLiveIT.consoleAllowingSilence("gamemode spectator Surveyor");
                ChainScenarioLiveIT.console("tp Surveyor %d 140 %d".formatted(x, z));
            }
            for (int i = 0; i < 60 && chunks.loadedChunkCount() < 100; i++) {
                TimeUnit.MILLISECONDS.sleep(500);
                if (!asSoldier) {
                    session.reportPosition(x + 0.5, 140, z + 0.5, 0, 0, false);
                }
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
            // Camp sites: centres every 4 blocks within 48 of the site, each scored by how many of its
            // 17x17 columns stand within a block of its typical ground - the flattest, lowest first.
            if (Boolean.getBoolean("camp.survey")) {
                record Site(int x, int z, int height, int level, int water) {
                }
                java.util.List<Site> sites = new java.util.ArrayList<>();
                for (int cx = x - 48; cx <= x + 48; cx += 4) {
                    for (int cz = z - 48; cz <= z + 48; cz += 4) {
                        java.util.List<Integer> heights = new java.util.ArrayList<>();
                        int water = 0;
                        for (int dx = -8; dx <= 8; dx++) {
                            for (int dz = -8; dz <= 8; dz++) {
                                for (int y = 120; y > 40; y--) {
                                    if (world.isWater(cx + dx, y, cz + dz)) {
                                        water++;
                                        break;
                                    }
                                    if (world.isSolid(cx + dx, y - 1, cz + dz) && !world.isSolid(cx + dx, y, cz + dz)
                                            && !world.isSolid(cx + dx, y + 1, cz + dz)) {
                                        heights.add(y);
                                        break;
                                    }
                                }
                            }
                        }
                        if (heights.size() < 200) {
                            continue;
                        }
                        java.util.Collections.sort(heights);
                        int typical = heights.get(heights.size() / 2);
                        int level = (int) heights.stream().filter(h -> Math.abs(h - typical) <= 1).count();
                        sites.add(new Site(cx, cz, typical, level, water));
                    }
                }
                sites.stream().filter(site -> site.height() <= Integer.getInteger("camp.maxY", 72))
                        .sorted(java.util.Comparator.comparingInt(Site::level).reversed()).limit(8)
                        .forEach(site -> System.out.printf("Camp site x=%d z=%d: ground y=%d, %d of 289 columns level, %d water, %.0f from x=%d z=%d%n",
                                site.x(), site.z(), site.height(), site.level(), site.water(), Math.hypot(site.x() - x, site.z() - z), x, z));
            }
            String column = System.getProperty("column");
            if (column != null) {
                int[] c = java.util.Arrays.stream(column.split(",")).mapToInt(Integer::parseInt).toArray();
                StringBuilder line = new StringBuilder("Column " + c[0] + "," + c[1] + " from y=" + c[2] + " up: ");
                for (int y = c[2]; y <= c[3]; y++) {
                    line.append(world.isWater(c[0], y, c[1]) ? '~' : world.isSolid(c[0], y, c[1]) ? '#' : '.');
                }
                System.out.println(line);
            }
            String resourcesAt = System.getProperty("resources.at");
            if (resourcesAt != null) {
                double[] p = java.util.Arrays.stream(resourcesAt.split(",")).mapToDouble(Double::parseDouble).toArray();
                Position from = new Position(p[0], p[1], p[2]);
                System.out.println("Resources seen from " + from + ": "
                        + io.github.jayessdeesea.minecraft.bot.supply.ResourceSurveyor.survey(world, from));
                int logs = 0, below = 0, above = 0;
                for (int dx = -64; dx <= 64; dx++) {
                    for (int dz = -64; dz <= 64; dz++) {
                        for (int y = -64; y < 320; y++) {
                            if (world.isLog((int) p[0] + dx, y, (int) p[2] + dz)) {
                                logs++;
                                below += y < p[1] - 24 ? 1 : 0;
                                above += y > p[1] + 24 ? 1 : 0;
                            }
                        }
                    }
                }
                System.out.printf("Logs within 64 across: %d, of them %d more than 24 below, %d more than 24 above; %d chunks loaded%n",
                        logs, below, above, chunks.loadedChunkCount());
            }
            String route = System.getProperty("route");
            if (route != null) {
                int[] r = java.util.Arrays.stream(route.split(",")).mapToInt(Integer::parseInt).toArray();
                BlockPos from = new BlockPos(r[0], r[1], r[2]);
                var goal = new io.github.jayessdeesea.minecraft.bot.nav.Goal(r[3] + 0.5, r[4], r[5] + 0.5, 2, 1);
                for (var falls : java.util.List.of(io.github.jayessdeesea.minecraft.bot.nav.FallAllowance.NONE,
                        new io.github.jayessdeesea.minecraft.bot.nav.FallAllowance(19, 1, 1))) {
                    var result = io.github.jayessdeesea.minecraft.bot.nav.Pathfinder.find(world, from, goal, 4000, falls);
                    System.out.printf("Route %s -> %s with %s: reaches=%b %s%n", from, goal, falls, result.reachesGoal(), result.path());
                }
                // The blocks at the start, by height: S solid, W water, H another hazard, . open.
                for (int y = r[1] + 2; y >= r[1] - 1; y--) {
                    StringBuilder row = new StringBuilder("  y=" + y + " ");
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int bx = r[0] + dx, by = y, bz = r[2] + dz;
                            row.append(world.isWater(bx, by, bz) ? 'W' : world.isHazard(bx, by, bz) ? 'H' : world.isSolid(bx, by, bz) ? 'S' : '.');
                        }
                        row.append(' ');
                    }
                    System.out.println(row);
                }
                // A soldier ticked against this world from there, as the tick loop would.
                double vertical = Double.parseDouble(System.getProperty("route.vertical", "1"));
                Position join = joinedAt.get();
                double[] start = java.util.Arrays.stream(System.getProperty("route.start", join != null && System.getProperty("probe.name") != null
                        ? join.x() + "," + join.y() + "," + join.z() : (r[0] + 0.5) + "," + r[1] + "," + (r[2] + 0.5))
                        .split(",")).mapToDouble(Double::parseDouble).toArray();
                System.out.println("Joined at " + join + ", simulating from " + java.util.Arrays.toString(start));
                var soldier = new io.github.jayessdeesea.minecraft.bot.DeterministicSoldier("Sim", new Position(start[0], start[1], start[2]));
                soldier.receiveOrder(new io.github.jayessdeesea.minecraft.command.Order.MoveTo(r[3], r[4], r[5], 2, vertical,
                        io.github.jayessdeesea.minecraft.command.Risk.LOW));
                for (int tick = 0; tick < 400; tick++) {
                    soldier.tick(world);
                    if (tick % 20 == 0) {
                        System.out.printf("  tick %3d at %s, %s%n", tick, soldier.status().position(), soldier.status().report());
                    }
                }
                // Columns round the two: the top standing height, ~ for water, # for none in reach.
                for (int cz = Math.min(r[2], r[5]) - 3; cz <= Math.max(r[2], r[5]) + 3; cz++) {
                    StringBuilder row = new StringBuilder(String.format("z=%4d ", cz));
                    for (int cx = Math.min(r[0], r[3]) - 4; cx <= Math.max(r[0], r[3]) + 4; cx++) {
                        String cell = "  #";
                        for (int y = r[1] + 6; y >= r[1] - 8; y--) {
                            if (world.isWater(cx, y, cz) && !world.isWater(cx, y + 1, cz)) {
                                cell = String.format("%2d~", y);
                                break;
                            }
                            if (io.github.jayessdeesea.minecraft.bot.nav.Pathfinder.standable(world, new BlockPos(cx, y, cz))) {
                                cell = String.format("%3d", y);
                                break;
                            }
                        }
                        row.append(cell);
                    }
                    System.out.println(row);
                }
            }
            String at = System.getProperty("survey.at");
            if (at != null) {
                String[] xyz = at.split(",");
                Position pointMan = new Position(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
                io.github.jayessdeesea.minecraft.command.TerrainSurvey survey = io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.survey(world, pointMan);
                long reachable = survey.samples().stream().filter(io.github.jayessdeesea.minecraft.command.TerrainSurvey.Sample::reachable).count();
                BlockPos feet = BlockPos.containing(pointMan.x(), pointMan.y(), pointMan.z());
                System.out.printf("Terrain survey from %s: %d/%d reachable; feet %s solid=%b hazard=%b, below solid=%b, head solid=%b%n", pointMan, reachable,
                        survey.samples().size(), feet, world.isSolid(feet.x(), feet.y(), feet.z()), world.isHazard(feet.x(), feet.y(), feet.z()),
                        world.isSolid(feet.x(), feet.y() - 1, feet.z()), world.isSolid(feet.x(), feet.y() + 1, feet.z()));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    System.out.printf("  side %d,%d: feet solid=%b hazard=%b head solid=%b%n", d[0], d[1], world.isSolid(feet.x() + d[0], feet.y(), feet.z() + d[1]),
                            world.isHazard(feet.x() + d[0], feet.y(), feet.z() + d[1]), world.isSolid(feet.x() + d[0], feet.y() + 1, feet.z() + d[1]));
                }
            }
        } finally {
            session.close();
        }
    }
}
