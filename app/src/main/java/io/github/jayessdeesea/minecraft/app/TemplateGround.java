package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.Tasking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A farm mission's template whose ground isn't laid yet, as each of its squad's soldiers' order (operator, 2026-11-22):
 * the whole squad lays it, a strip of the missing cells each, in dirt - not its one tender. Live (field692), a wheat
 * chunk floored in cobblestone wanted 247 cells of dirt; its tender laid the 11 it carried and stood, the rest of its
 * squad harvesting another farm. One short of dirt draws a load from the nearest chest holding some, at its tick
 * ({@code SoldierRunner}); with none in any chest it's sent for a load from the ground east of the chunk over its level,
 * which is to be dug anyway ({@link Tasking#soil}). The water holes are left open: the template's, to pour.
 */
final class TemplateGround {

    private TemplateGround() {
    }

    /**
     * {@code squad}'s orders on a tree farm's template whose trees have no dirt under them yet (operator, 2026-11-23:
     * cobblestone at the level unless it can't be - dirt under the trees, cobblestone everywhere else): its level is paved
     * like any template's, so each tree's cell has its cobblestone dug out by the one of the squad nearest, and dirt laid
     * in it by the rest. None once all sixteen are dirt. Before the slab, a trunk over a pit was never planted.
     */
    static Map<String, Order> treeOrders(WorldView world, io.github.jayessdeesea.minecraft.bot.farm.TreeChunk trees, List<SoldierStatus> squad,
                                         Predicate<SoldierStatus> dirtInAChest, Set<String> fetching) {
        List<BlockPos> paved = trees.soilToDig(world);
        Map<String, Order> digging = new java.util.HashMap<>();
        if (!paved.isEmpty() && !squad.isEmpty()) {
            BlockPos first = paved.getFirst();
            SoldierStatus nearest = squad.stream().min(java.util.Comparator.comparingDouble(s -> Math.hypot(s.position().x() - first.x(), s.position().z() - first.z()))).orElseThrow();
            digging.put(nearest.name(), Order.Dig.of(paved.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList()));
            squad = squad.stream().filter(s -> s != nearest).toList();
        }
        List<BlockPos> missing = trees.soilToLay(world);
        if (missing.isEmpty() || squad.isEmpty()) {
            return digging;
        }
        // Dirt in its trees' cells only: nothing else of its ground is filled with it, whatever is open there.
        Set<BlockPos> soil = Set.copyOf(trees.soil());
        Set<Cell> notSoil = trees.ground().stream().filter(c -> !soil.contains(c)).map(c -> new Cell(c.x(), c.y(), c.z())).collect(Collectors.toSet());
        List<Tasking.Hand> hands = squad.stream().map(s -> new Tasking.Hand(s.name(), dirtInAChest.test(s) ? Tasking.LOAD
                : s.condition().flatMap(Condition::kit).map(Kit::dirt).orElse(0))).toList();
        Flatten chunk = new Flatten(trees.chunkX() * 16, trees.chunkZ() * 16, trees.chunkX() * 16 + 15, trees.chunkZ() * 16 + 15, trees.level());
        Map<String, Order> orders = new java.util.HashMap<>(Tasking.soil(hands, SiteGeometry.bounds(missing), SiteGeometry.soilBorrow(chunk), notSoil, fetching));
        fetching.clear();
        orders.forEach((soldier, order) -> {
            if (order instanceof Order.Gather) {
                fetching.add(soldier);
            }
        });
        orders.putAll(digging);
        return orders;
    }

    /**
     * {@code squad}'s orders on {@code farm}'s ground as {@code world} stands - none once it's laid. {@code dirtInAChest}
     * says whether a chest in a soldier's reach holds dirt; {@code fetching} is those sent for dirt last cycle, kept here.
     */
    static Map<String, Order> orders(WorldView world, ChunkFarm farm, List<SoldierStatus> squad, Predicate<SoldierStatus> dirtInAChest,
                                     Set<String> fetching) {
        List<List<BlockPos>> surveyed = FarmSite.surveyed(world, farm);
        List<BlockPos> missing = surveyed.get(1);
        // What stands in its way - a block put down as a step and left, a depot's chest - is dug by one of the squad, the
        // one nearest it (operator, 2026-11-22: who cleans this up?): counted since the farm was first surveyed, and nobody's.
        List<BlockPos> inTheWay = surveyed.get(0).stream().filter(c -> io.github.jayessdeesea.minecraft.bot.engineer.Clearing.diggable(world, c.x(), c.y(), c.z())).toList();
        Map<String, Order> clearing = new java.util.HashMap<>();
        if (!inTheWay.isEmpty() && !squad.isEmpty()) {
            BlockPos first = inTheWay.getFirst();
            SoldierStatus nearest = squad.stream().min(java.util.Comparator.comparingDouble(s -> Math.hypot(s.position().x() - first.x(), s.position().z() - first.z()))).orElseThrow();
            clearing.put(nearest.name(), Order.Dig.of(inTheWay.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList()));
            squad = squad.stream().filter(s -> s != nearest).toList();
        }
        if (missing.isEmpty() || squad.isEmpty()) {
            return clearing;
        }
        Set<Cell> holes = farm.holes().stream().map(h -> new Cell(h.x(), h.y(), h.z())).collect(Collectors.toSet());
        // With dirt in a chest, a soldier counts as carrying a load: it's given its strip, and draws on the way.
        List<Tasking.Hand> hands = squad.stream().map(s -> new Tasking.Hand(s.name(), dirtInAChest.test(s) ? Tasking.LOAD
                : s.condition().flatMap(Condition::kit).map(Kit::dirt).orElse(0))).toList();
        Flatten chunk = new Flatten(farm.chunkX() * 16, farm.chunkZ() * 16, farm.chunkX() * 16 + 15, farm.chunkZ() * 16 + 15, farm.level());
        // Down to the bottom of the deepest of them: a fill fills a column from its first solid block up, and one a block
        // deep over an open cell fills nothing - live, two footings under a fence line the chunk beside never slabbed.
        int deepest = missing.stream().mapToInt(c -> {
            int down = 0;
            while (down < MAX_FOOTING_DEPTH && !world.isSolid(c.x(), c.y() - down - 1, c.z())) {
                down++;
            }
            return down;
        }).max().orElse(0);
        Optional<io.github.jayessdeesea.minecraft.command.Box> fill = SiteGeometry.bounds(missing)
                .map(b -> new io.github.jayessdeesea.minecraft.command.Box(b.minX(), b.minY() - deepest, b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
        Map<String, Order> orders = Tasking.soil(hands, fill, SiteGeometry.soilBorrow(chunk), holes, fetching);
        fetching.clear();
        orders.forEach((soldier, order) -> {
            if (order instanceof Order.Gather) {
                fetching.add(soldier);
            }
        });
        Map<String, Order> all = new java.util.HashMap<>(orders);
        all.putAll(clearing);
        return all;
    }

    /**
     * The {@code i}th of {@code n} soldiers' share of {@code farm}'s chunk farms' footings still to pave in cobblestone
     * ({@code ChunkFarm.footingsToPave}): every {@code n}th of them, so each is one paver's. Its squad's work whenever
     * any stands unpaved - a farm built or building - as a floor's cells are a floor squad's.
     */
    static List<BlockPos> footings(WorldView world, io.github.jayessdeesea.minecraft.bot.farm.FarmPlan farm, int i, int n) {
        List<BlockPos> all = farm.chunks().stream().flatMap(c -> c.footingsToPave(world).stream()).distinct().toList();
        return java.util.stream.IntStream.range(0, all.size()).filter(k -> k % Math.max(1, n) == i).mapToObj(all::get).toList();
    }

    /** How far under a template's ground a missing cell's hole is followed down to fill it from its bottom. */
    static final int MAX_FOOTING_DEPTH = 8;
}
