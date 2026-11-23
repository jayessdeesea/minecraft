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

    /**
     * {@code squad}'s orders on {@code farm}'s ground as {@code world} stands - none once it's laid. {@code dirtInAChest}
     * says whether a chest in a soldier's reach holds dirt; {@code fetching} is those sent for dirt last cycle, kept here.
     */
    static Map<String, Order> orders(WorldView world, ChunkFarm farm, List<SoldierStatus> squad, Predicate<SoldierStatus> dirtInAChest,
                                     Set<String> fetching) {
        List<BlockPos> missing = FarmSite.surveyed(world, farm).get(1);
        if (missing.isEmpty()) {
            return Map.of();
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
        return orders;
    }
}
