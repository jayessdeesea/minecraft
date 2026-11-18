package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmourerTest {

    private static final int GROUND = 63;
    /** Iron ore in the rock, beside a one-cell cave nothing reaches; and one on the surface beside open ground. */
    private static final BlockPos BURIED = new BlockPos(8, 58, 0), CAVE = new BlockPos(8, 58, 1), SURFACE = new BlockPos(5, GROUND + 1, 0);

    private static WorldView world(Set<BlockPos> ore) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return ore.contains(c) || (y <= GROUND && !c.equals(CAVE) && !c.equals(CAVE.offset(0, 1, 0)));
            }

            @Override
            public boolean isIronOre(int x, int y, int z) {
                return ore.contains(new BlockPos(x, y, z));
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

    /**
     * Ore it can't walk to isn't gone for: live, builders left the keep for iron seen 40 blocks off
     * through solid rock, and each stood 20 seconds at "no route" before it gave up, all day.
     */
    @Test
    void oreBesideACaveNothingReachesIsntGoneFor() {
        BlockPos feet = new BlockPos(0, GROUND + 1, 0);

        assertEquals(Optional.empty(), Armourer.spotFor(world(Set.of(BURIED)), feet, Set.of(), Digger.Target.IRON_ORE));
        Digger.Spot spot = Armourer.spotFor(world(Set.of(BURIED, SURFACE)), feet, Set.of(), Digger.Target.IRON_ORE).orElseThrow();
        assertEquals(SURFACE, spot.block(), "the ore on the surface, the one it can walk to");
    }

    @Test
    void aStationWalkedToIsOneThePlanMakesSomethingAt() {
        CraftPlan ingots = new CraftPlan(List.of(new CraftPlan.Session(Recipe.Station.FURNACE,
                List.of(new CraftPlan.Batch(Recipe.COPPER_INGOT, 1)))), Map.of(), Set.of());
        assertEquals(Set.of(Workstation.FURNACE), Armourer.needed(ingots), "an ingot wants a furnace, not the table beside it");
        CraftPlan sword = new CraftPlan(List.of(new CraftPlan.Session(Recipe.Station.GRID, List.of(new CraftPlan.Batch(Recipe.PLANKS, 1))),
                new CraftPlan.Session(Recipe.Station.TABLE, List.of(new CraftPlan.Batch(Recipe.FURNACE, 1)))), Map.of(), Set.of());
        assertEquals(Set.of(Workstation.CRAFTING_TABLE), Armourer.needed(sword));
    }

    /**
     * A soldier on its order's work, short of a pickaxe's logs with a tree beside it, doesn't go and cut it:
     * it says what it's short of (operator, 2026-11-18: a farmer doesn't chop trees because it has no hoe).
     */
    @Test
    void aSoldierOnItsWorkSaysWhatItsShortOfRatherThanDigIt() {
        java.util.List<String> sent = new java.util.ArrayList<>();
        io.github.jayessdeesea.minecraft.protocol.PlaySession session = (io.github.jayessdeesea.minecraft.protocol.PlaySession)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{io.github.jayessdeesea.minecraft.protocol.PlaySession.class}, (proxy, method, args) -> {
                            sent.add(method.getName());
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        });
        BlockPos tree = new BlockPos(2, GROUND + 1, 0);
        WorldView withATree = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND || new BlockPos(x, y, z).equals(tree);
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(tree);
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
        Armourer armourer = new Armourer("Alice", session, new io.github.jayessdeesea.minecraft.bot.world.Inventory(),
                new java.util.HashSet<>(), new java.util.concurrent.ConcurrentHashMap<>(), () -> {
                });
        armourer.digsItsMakings(false);

        armourer.tick(new Position(0.5, GROUND + 1, 0.5), withATree, Set.of(Recipe.WOODEN_PICKAXE), false, Optional.empty());

        assertEquals(true, armourer.failure().filter(f -> f.contains("short of") && f.contains("LOG")).isPresent(), "said: " + armourer.failure());
        assertEquals(false, sent.contains("startDigging"), "no tree cut: " + sent);
    }
}
