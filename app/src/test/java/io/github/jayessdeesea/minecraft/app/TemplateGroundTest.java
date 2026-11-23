package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.Tasking;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A wheat chunk floored in cobblestone at y=62, its template's ground at y=63 not laid: whose job the dirt is. */
class TemplateGroundTest {

    private static final ChunkFarm FARM = new ChunkFarm(1, 3, 63);

    /** The floor in, nothing on it. */
    private static final WorldView FLOORED = world(62);
    /** The ground laid too. */
    private static final WorldView LAID = world(63);

    private static WorldView world(int top) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= top;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    private static SoldierStatus soldier(String name, int dirt) {
        Kit kit = new Kit(Optional.empty(), 0, 0, 0, 0, 0, 0, false, true, dirt, false, 0, 1, 1, 0);
        return new SoldierStatus(name, new Position(24.5, 63, 56.5), new Order.Idle(), new Report.InPosition(),
                Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }

    /** Every soldier of the squad gets a strip of the ground, in dirt - the one carrying none too, when a chest holds some. */
    @Test
    void theWholeSquadLaysItAndOneWithNoDirtDrawsItFromAChest() {
        List<SoldierStatus> squad = List.of(soldier("Ivy", 64), soldier("Jack", 0), soldier("Kate", 11), soldier("Liam", 0));
        Set<String> fetching = new HashSet<>();

        Map<String, Order> orders = TemplateGround.orders(FLOORED, FARM, squad, s -> true, fetching);

        assertEquals(Set.of("Ivy", "Jack", "Kate", "Liam"), orders.keySet());
        orders.values().forEach(o -> assertEquals(Order.Fill.Material.SOIL, assertInstanceOf(Order.Fill.class, o).material()));
        assertEquals(4, orders.values().stream().map(o -> ((Order.Fill) o).box()).distinct().count(), "a strip each, none another's");
        assertTrue(fetching.isEmpty(), "none sent to dig for it");
        Order.Fill ivys = (Order.Fill) orders.get("Ivy");
        assertTrue(FARM.holes().stream().allMatch(h -> ivys.except().contains(new io.github.jayessdeesea.minecraft.command.Cell(h.x(), h.y(), h.z()))),
                "the water holes left open");
    }

    /** With no dirt in any chest, the one carrying none is sent for a load from the ground beside the chunk; the others lay. */
    @Test
    void withNoDirtInAChestOneCarryingNoneFetchesALoad() {
        List<SoldierStatus> squad = List.of(soldier("Ivy", 64), soldier("Jack", 0));
        Set<String> fetching = new HashSet<>();

        Map<String, Order> orders = TemplateGround.orders(FLOORED, FARM, squad, s -> false, fetching);

        assertInstanceOf(Order.Fill.class, orders.get("Ivy"));
        Order.Gather load = assertInstanceOf(Order.Gather.class, orders.get("Jack"));
        assertEquals(Resource.SOIL, load.resource());
        assertEquals(Tasking.LOAD, load.amount());
        assertTrue(load.box().minX() > FARM.chunkX() * 16 + 15, "from beside the chunk, east of it: " + load.box());
        assertEquals(Set.of("Jack"), fetching);
    }

    /** Laid, there's nothing to order: the template is its tender's from there. */
    @Test
    void groundAlreadyLaidIsNobodysOrder() {
        assertEquals(List.of(), FarmSite.surveyed(LAID, FARM).get(1), "the survey finds nothing missing");
        assertEquals(Map.of(), TemplateGround.orders(LAID, FARM, List.of(soldier("Ivy", 64)), s -> true, new HashSet<>()));
    }

    /** A chest in reach of a soldier on a mission's task is near it and near its height: not the mine's stash 60 blocks under it. */
    @Test
    void aChestInAMissionWorkersReachIsNearAndNearItsHeight() {
        Position digger = new Position(42.5, 66, 66.5);
        assertTrue(SoldierErrands.inMissionReach(new Position(40.5, 67, 84.5), digger), "the camp's chest, 18 off");
        assertFalse(SoldierErrands.inMissionReach(new Position(39.5, 8, 75.5), digger), "the mine's stash, 58 below (field692)");
        assertFalse(SoldierErrands.inMissionReach(new Position(-47.5, 73, 45.5), digger), "the old camp's chest, 92 off");
    }
}
