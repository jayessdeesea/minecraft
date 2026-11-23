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

    /** How far down under a trunk ground is looked for to build up from. */
    static final int TRUNK_FILL_DEPTH = 12;

    /**
     * {@code squad}'s orders on a tree farm's template whose trunks have no ground under them (operator, 2026-11-23: the
     * soldiers don't build the base out where there's free space under): a tree farm's flatten only cuts down, and a
     * sapling wants soil under it, so a trunk over a pit was never planted and its template never done. Each such trunk's
     * column is filled in dirt from the ground under it up to the farm's level - lowest first, each block on the one
     * below - by the squad between them, as a wheat template's ground is. None once every trunk has ground.
     */
    static Map<String, Order> treeOrders(WorldView world, io.github.jayessdeesea.minecraft.bot.farm.TreeChunk trees, List<SoldierStatus> squad,
                                         Predicate<SoldierStatus> dirtInAChest, Set<String> fetching) {
        List<BlockPos> missing = new java.util.ArrayList<>();
        for (BlockPos trunk : trees.trunks()) {
            for (int y = trunk.y() - 1; y > trunk.y() - 1 - TRUNK_FILL_DEPTH && !world.isSolid(trunk.x(), y, trunk.z()); y--) {
                missing.add(new BlockPos(trunk.x(), y, trunk.z()));
            }
        }
        if (missing.isEmpty() || squad.isEmpty()) {
            return Map.of();
        }
        List<Tasking.Hand> hands = squad.stream().map(s -> new Tasking.Hand(s.name(), dirtInAChest.test(s) ? Tasking.LOAD
                : s.condition().flatMap(Condition::kit).map(Kit::dirt).orElse(0))).toList();
        Flatten chunk = new Flatten(trees.chunkX() * 16, trees.chunkZ() * 16, trees.chunkX() * 16 + 15, trees.chunkZ() * 16 + 15, trees.level());
        Map<String, Order> orders = Tasking.soil(hands, SiteGeometry.bounds(missing), SiteGeometry.soilBorrow(chunk), Set.of(), fetching);
        fetching.clear();
        orders.forEach((soldier, order) -> {
            if (order instanceof Order.Gather) {
                fetching.add(soldier);
            }
        });
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
        Map<String, Order> orders = Tasking.soil(hands, SiteGeometry.bounds(missing), SiteGeometry.soilBorrow(chunk), holes, fetching);
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
}
