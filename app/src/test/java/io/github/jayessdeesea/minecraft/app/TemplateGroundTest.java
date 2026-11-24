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

    /**
     * Ground open deeper than a block is filled from its bottom: live (field705-707) the wheat farm stood SURVEYED
     * "fill=2 ... to fill [32,63,56, 32,63,59]" for two hours, eight soldiers on it reporting nothing left - a fill a
     * block deep over an open cell fills nothing. (Those two were footings, paved in cobblestone since - the third list.)
     */
    @Test
    void groundOpenDeeperThanABlockIsFilledFromItsBottom() {
        WorldView holed = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 && !((x == 22 || x == 32) && (z == 56 || z == 59) && y >= 61);
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
        assertEquals(2, FarmSite.surveyed(holed, FARM).get(1).size(), "the survey's two of its ground: " + FarmSite.surveyed(holed, FARM).get(1));
        assertEquals(2, FarmSite.surveyed(holed, FARM).get(2).size(), "and two footings, open: paved, not filled with dirt: " + FarmSite.surveyed(holed, FARM).get(2));

        Map<String, Order> orders = TemplateGround.orders(holed, FARM, List.of(soldier("Ivy", 64), soldier("Jack", 64)), s -> true, new HashSet<>());

        Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> filled = new HashSet<>();
        orders.values().forEach(o -> {
            Order.Fill fill = assertInstanceOf(Order.Fill.class, o);
            filled.addAll(io.github.jayessdeesea.minecraft.bot.engineer.Clearing.toFill(holed, fill.box(), fill.except()));
        });
        assertEquals(6, filled.size(), "both columns, three cells each, bottom to level: " + filled);
        assertTrue(filled.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(22, 63, 56))
                && filled.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(22, 61, 59)), filled.toString());
    }

    /** A trunk standing on its ground is no part of a template's sky; its top left hanging when its foot was cut is. */
    @Test
    void aLogWithNothingUnderItHangsInTheSky() {
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> logs = new java.util.HashSet<>();
        for (int y = 64; y <= 68; y++) {
            logs.add(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(20, y, 52));
        }
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos top = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(26, 67, 58);
        logs.add(top);
        logs.add(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(24, 72, 54)); // a branch, out of a hand's reach
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || logs.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
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
        assertEquals(List.of(top), FARM.sky(world), "the log three over an open cell - not the trunk on its ground, nor a branch nine up");
    }

    /** A step left standing in a tree farm's trunk cell is its squad's to dig: no sapling goes in through it. */
    @Test
    void whatStandsInATrunksCellIsDugWithTheSky() {
        io.github.jayessdeesea.minecraft.command.FarmMission trees = new io.github.jayessdeesea.minecraft.command.FarmMission(2, 3, 63,
                io.github.jayessdeesea.minecraft.command.FarmMission.Crop.TREES);
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos trunk = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(2, 3, 63).trunks().getFirst();
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> steps = java.util.Set.of(trunk, trunk.offset(0, 1, 0));
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || steps.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
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
        assertEquals(java.util.Set.of(trunk, trunk.offset(0, 1, 0)), java.util.Set.copyOf(TemplateDrift.sky(trees, world)), "the two steps on its first trunk's cell");
        assertEquals(2, TemplateDrift.sky(trees, world).size(), "each counted once");
        assertEquals(List.of(trunk.offset(0, 1, 0)), TemplateDrift.sky(new io.github.jayessdeesea.minecraft.command.FarmMission(2, 3, 63,
                io.github.jayessdeesea.minecraft.command.FarmMission.Crop.PLACEHOLDER), world), "on any other template only what is two over its level: its sky");
    }

    /** A step left in a post's or a gate's cell is dug before the fixture goes up: a post, and a gate, are left as they stand. */
    @Test
    void aStepInAFencesOrAGatesCellIsMisplaced() {
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos post = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(5, 64, 37), stepOnLine = post.offset(0, 0, 1),
                stepInGate = post.offset(0, 0, 2), gate = post.offset(0, 0, 3);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z);
                return y <= 63 || at.equals(post) || at.equals(post.offset(0, 1, 0)) || at.equals(stepOnLine) || at.equals(stepInGate) || at.equals(gate);
            }

            @Override
            public boolean solidHere(int x, int y, int z) {
                return isSolid(x, y, z) && !new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z).equals(post.offset(0, 1, 0));
            }

            @Override
            public boolean isGate(int x, int y, int z) {
                return new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z).equals(gate);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return y == 64;
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
        var layout = new java.util.LinkedHashMap<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>>();
        layout.put(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.FENCE, List.of(post, stepOnLine));
        layout.put(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.FENCE_GATE, List.of(stepInGate, gate));

        assertEquals(List.of(stepOnLine, stepInGate), TemplateDrift.misplaced(world, layout), "the two steps, not the post or the gate");
    }

    /** Loose ground left standing on a template, a block or two over its level, is its squad's to clear - whatever the template. */
    @Test
    void aStepLeftStandingOnATemplateIsClutter() {
        io.github.jayessdeesea.minecraft.command.FarmMission stores = new io.github.jayessdeesea.minecraft.command.FarmMission(1, 2, 63,
                io.github.jayessdeesea.minecraft.command.FarmMission.Crop.STORES);
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos step = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(21, 64, 47),
                over = step.offset(0, 1, 0), outside = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(40, 64, 47);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || isSoil(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                io.github.jayessdeesea.minecraft.bot.nav.BlockPos at = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z);
                return at.equals(step) || at.equals(over) || at.equals(outside);
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
        assertEquals(List.of(step, over), TemplateDrift.clutter(stores, world), "the two blocks of dirt inside its fence, not the one in the chunk beside");
    }

    /** A fixture's footing that stands open is its squad's to pave - under what stands on the ground, not under a post-top torch. */
    @Test
    void anOpenFootingUnderAFixtureIsToPave() {
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos trench = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(16, 63, 40);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 && !(x == 16 && z == 40);
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
        var layout = new java.util.LinkedHashMap<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos>>();
        layout.put(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.FENCE, List.of(trench.offset(0, 1, 0), trench.offset(0, 1, 1)));
        layout.put(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.TORCH, List.of(trench.offset(0, 2, 0)));

        assertEquals(List.of(trench), TemplateGround.openFootings(world, layout), "the trench under the post, and nothing under the torch on its top");
    }

    /** What hangs in a template's sky is one soldier's to dig, the nearest: every template's sky is kept clear. */
    @Test
    void whatHangsInATemplatesSkyIsDugByTheNearest() {
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos hanging = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(24, 70, 56);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z).equals(hanging);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z).equals(hanging);
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
        assertEquals(List.of(hanging), FARM.sky(world), "the block seven over its level, and nothing of its ground");

        Map<String, Order> orders = TemplateGround.skyOrders(world, FARM.sky(world), List.of(soldier("Ivy", 0), soldier("Jack", 0)));

        assertEquals(1, orders.size(), "one soldier's: " + orders);
        Order.Dig dig = assertInstanceOf(Order.Dig.class, orders.values().iterator().next());
        assertTrue(dig.box().contains(24, 70, 56), "the block hanging: " + dig);
        assertEquals(Map.of(), TemplateGround.skyOrders(world, List.of(), List.of(soldier("Ivy", 0))), "a clear sky is nobody's order");
    }

    /** A chest in reach of a soldier on a mission's task is near it and near its height: not the mine's stash 60 blocks under it. */
    @Test
    void aChestInAMissionWorkersReachIsNearAndNearItsHeight() {
        Position digger = new Position(42.5, 66, 66.5);
        assertTrue(SoldierErrands.inMissionReach(new Position(40.5, 67, 84.5), digger), "the camp's chest, 18 off");
        assertFalse(SoldierErrands.inMissionReach(new Position(39.5, 8, 75.5), digger), "the mine's stash, 58 below (field692)");
        assertFalse(SoldierErrands.inMissionReach(new Position(-47.5, 73, 45.5), digger), "the old camp's chest, 92 off");
    }

    /** On a mission's task a soldier makes only the weapon or the pickaxe it has none of: no spare, no armour (field692, Dana). */
    @Test
    void onAMissionsTaskOnlyTheWeaponOrPickaxeItLacksIsMade() {
        var weapon = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.WEAPON;
        var pickaxe = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.PICKAXE;
        var armour = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.ARMOUR;
        assertTrue(SoldierErrands.madeOnMission(weapon, 0, 2), "bare-handed: its weapon");
        assertFalse(SoldierErrands.madeOnMission(weapon, 1, 2), "a spare sword waits");
        assertTrue(SoldierErrands.madeOnMission(pickaxe, 1, 0), "no pickaxe: one is made");
        assertFalse(SoldierErrands.madeOnMission(pickaxe, 1, 1), "a second pickaxe waits");
        assertFalse(SoldierErrands.madeOnMission(armour, 1, 1), "armour waits");
        // But for what's all in its pack already: nothing fetched, the task not left.
        assertTrue(SoldierErrands.madeOnMission(armour, 1, 1, true), "armour from the ingots it carries");
        assertTrue(SoldierErrands.madeOnMission(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.SHIELD, 1, 1, true));
        assertFalse(SoldierErrands.madeOnMission(weapon, 1, 2, true), "a spare sword still waits");
        var copper = io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.COPPER_INGOT;
        assertTrue(SoldierErrands.carried(io.github.jayessdeesea.minecraft.bot.craft.Recipe.COPPER_CHESTPLATE, k -> k == copper ? 8 : 0), "eight ingots: a chestplate");
        assertFalse(SoldierErrands.carried(io.github.jayessdeesea.minecraft.bot.craft.Recipe.COPPER_CHESTPLATE, k -> k == copper ? 7 : 0));
        assertFalse(SoldierErrands.carried(io.github.jayessdeesea.minecraft.bot.craft.Recipe.SHIELD, k -> 0), "no iron, no planks: no shield");
    }
}
