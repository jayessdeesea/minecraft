package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DutyBookTest {

    /**
     * Two squads on one farm share its plot as one: no two harvesters on the same columns, one baker. Live (field473),
     * Alpha's and Delta's first harvesters were both given x -31..-20 of the camp's farm.
     */
    @Test
    void twoSquadsOnOneFarmShareItsPlotAsOne() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 4; z++) {
                plot.add(new BlockPos(x, 63, z));
            }
        }
        FarmPlan farm = new FarmPlan(new BlockPos(6, 62, 2), plot);
        Map<String, FarmPlan> farming = Map.of("Alice", farm, "Bob", farm, "Mia", farm, "Olga", farm);

        Map<String, DutyOrder> alpha = DutyBook.given(List.of("Alice", "Bob"), Map.of(), farming, Optional.empty());
        Map<String, DutyOrder> delta = DutyBook.given(List.of("Mia", "Olga"), Map.of(), farming, Optional.empty());

        assertEquals(alpha, delta, "each squad's leader reckons the one set of shares");
        assertNotEquals(alpha.get("Alice").ground(), alpha.get("Mia").ground());
        assertEquals(1, alpha.values().stream().filter(o -> o.duty() == DutyOrder.Duty.BAKER).count(), "one baker to the farm");
    }

    /**
     * A farm with a chunk farm on it: the tender's field is its chunk - its plot, its holes and fixtures - and the other
     * harvesters' the ground outside it, whatever their ground's box takes in.
     */
    @Test
    void aChunkFarmsTenderHasItsChunkAndTheOthersTheRest() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(1, 0, 63);
        List<BlockPos> pond = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 16; z++) {
                pond.add(new BlockPos(x, 63, z));
            }
        }
        FarmPlan farm = new FarmPlan(new BlockPos(6, 62, 2), pond).with(chunk.plan(io.github.jayessdeesea.minecraft.app.DutyBookTest.flat(), c -> true));
        Map<String, FarmPlan> farming = Map.of("Alice", farm, "Bob", farm, "Carl", farm);
        DutyBook book = new DutyBook();
        book.assign("Alpha", List.of("Alice", "Bob", "Carl"), DutyBook.given(List.of("Alice", "Bob", "Carl"), Map.of(), farming, Optional.empty()),
                Optional.of("FARM"), 0);

        FarmPlan tended = book.field("Bob", farm);
        assertEquals(List.of(chunk), tended.chunks(), "Bob tends the chunk");
        assertTrue(tended.plot().stream().allMatch(c -> c.x() >= 16), "its plot the chunk's: " + tended.plot().size());
        FarmPlan rest = book.field("Alice", farm);
        assertEquals(List.of(), rest.chunks());
        assertTrue(rest.plot().stream().allMatch(c -> c.x() < 16) && !rest.plot().isEmpty(), "Alice the pond's ground");
    }

    /** A producer's yield goes to its order's dump while a chest stands there, and to none of its order's while none does. */
    @Test
    void aProducersDumpIsItsOrdersWhileAChestStandsThere() {
        io.github.jayessdeesea.minecraft.command.Position chest = new io.github.jayessdeesea.minecraft.command.Position(13, 64, 13);
        DutyBook book = new DutyBook();
        book.assign("Alpha", List.of("Bob"), Map.of("Bob", new DutyOrder("Bob", DutyOrder.Duty.HARVESTER, Optional.empty(), Optional.of(chest))),
                Optional.of("FARM"), 0);
        java.util.Set<BlockPos> chests = new java.util.HashSet<>();
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return chests.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isChest(int x, int y, int z) {
                return chests.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        };

        assertEquals(Optional.empty(), book.dump("Bob", world), "no chest stands there yet");
        chests.add(new BlockPos(13, 64, 13));
        assertEquals(Optional.of(chest), book.dump("Bob", world));
        assertEquals(Optional.empty(), book.dump("Alice", world), "no order, no dump of its own");
    }

    /** A planter with no opened ground in its wood, or all of it planted, plants nothing: its share isn't behind. */
    @Test
    void aPlanterWithNoGroundOpenedPlantsNothing() {
        DutyBook book = new DutyBook();
        book.assign("Bravo", List.of("Hal", "Ivy"), Map.of("Hal", new DutyOrder("Hal", DutyOrder.Duty.PLANTER, Optional.empty(), Optional.empty()),
                "Ivy", new DutyOrder("Ivy", DutyOrder.Duty.WOODCUTTER, Optional.empty(), Optional.empty())), Optional.of("WOOD"), 0);
        io.github.jayessdeesea.minecraft.bot.farm.TreeFarm grove = new io.github.jayessdeesea.minecraft.bot.farm.TreeFarm(
                new BlockPos(0, 63, 0), List.of(new BlockPos(0, 63, 0)));

        assertTrue(book.plantsNothing("Hal", null, flat()), "no ground opened");
        assertTrue(!book.plantsNothing("Hal", grove, flat()), "a cell to plant");
        assertTrue(!book.plantsNothing("Ivy", null, flat()), "a woodcutter isn't a planter");
    }

    /** Grass at y 63 everywhere. */
    static io.github.jayessdeesea.minecraft.bot.world.WorldView flat() {
        return new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                return y == 63;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        };
    }

    private static final io.github.jayessdeesea.minecraft.command.Position FOREST = new io.github.jayessdeesea.minecraft.command.Position(0, 64, 0);
    private static final Optional<io.github.jayessdeesea.minecraft.command.Objective> WOOD = Optional.of(new io.github.jayessdeesea.minecraft.command.Objective("Wood", FOREST, "logs",
            new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(io.github.jayessdeesea.minecraft.command.Resource.WOOD)));

    /**
     * A strip its own woodcutter has looked over and found empty moves that woodcutter alone, onto half of the strip with
     * the most standing, cut between its trees; the others' strips stand as they were (backlog/wood-reshare.md: every
     * strip moved at each cut-out, and a tree half felled was left outside its feller's new one).
     */
    @Test
    void aStripCutOutMovesOnlyItsOwnWoodcutter() {
        DutyBook book = new DutyBook();
        List<String> roster = List.of("Ivy", "Jack", "Kate", "Liam");
        // Four trees in the middle strip, Jack's, at z = -20, -10, 6 and 18; one in Kate's; none in Ivy's.
        java.util.function.Function<io.github.jayessdeesea.minecraft.command.Box, List<int[]>> trees = b -> java.util.stream.Stream.of(new int[]{0, -20}, new int[]{0, -10},
                new int[]{0, 6}, new int[]{0, 18}, new int[]{20, 0}).filter(t -> b.contains(t[0], b.minY(), t[1])).toList();
        java.util.function.Function<String, Optional<io.github.jayessdeesea.minecraft.command.Position>> at = n -> Optional.of(new io.github.jayessdeesea.minecraft.command.Position(0.5, 64, 10.5));

        Map<String, io.github.jayessdeesea.minecraft.command.WoodDuties.Duty> first = book.wood("Charlie", roster, WOOD, trees, at, spent -> { }, 100);
        io.github.jayessdeesea.minecraft.command.Box ivys = first.get("Ivy").area(), jacks = first.get("Jack").area(), kates = first.get("Kate").area();
        book.searched("Jack", Optional.of(ivys));
        assertEquals(first, book.wood("Charlie", roster, WOOD, trees, at, spent -> { }, 200), "nothing found in ground that isn't its own strip says nothing of its strip");

        book.searched("Ivy", Optional.of(ivys));
        Map<String, io.github.jayessdeesea.minecraft.command.WoodDuties.Duty> after = book.wood("Charlie", roster, WOOD, trees, at, spent -> { }, 300);

        assertEquals(kates, after.get("Kate").area(), "Kate's strip as it was");
        assertEquals(new io.github.jayessdeesea.minecraft.command.Box(jacks.minX(), jacks.minY(), -1, jacks.maxX(), jacks.maxY(), jacks.maxZ()), after.get("Jack").area(),
                "Jack keeps the half it stands in, cut between the trees at z=-10 and z=6");
        assertEquals(new io.github.jayessdeesea.minecraft.command.Box(jacks.minX(), jacks.minY(), jacks.minZ(), jacks.maxX(), jacks.maxY(), -2), after.get("Ivy").area(), "Ivy the other half");
        assertEquals(io.github.jayessdeesea.minecraft.command.WoodDuties.whole(FOREST), after.get("Liam").area(), "the planter over the whole");
        assertEquals(after, book.wood("Charlie", roster, WOOD, trees, at, spent -> { }, 400), "and it stands: cut out once");
    }

    /** No strip left and no log standing, its squad leader says the wood is spent - once; with logs standing, not at all. */
    @Test
    void aWoodIsSaidSpentOnceWhenNoStripIsLeftAndNoLogStands() {
        List<String> roster = List.of("Ivy", "Jack");
        List<io.github.jayessdeesea.minecraft.command.Position> spent = new ArrayList<>();
        DutyBook bare = new DutyBook();
        io.github.jayessdeesea.minecraft.command.Box whole = bare.wood("Charlie", roster, WOOD, b -> List.of(), n -> Optional.empty(), spent::add, 100).get("Ivy").area();
        bare.searched("Ivy", Optional.of(whole));
        assertEquals(io.github.jayessdeesea.minecraft.command.WoodDuties.Kind.STANDBY,
                bare.wood("Charlie", roster, WOOD, b -> List.of(), n -> Optional.empty(), spent::add, 200).get("Ivy").kind(), "Ivy has no strip: it stands by");
        bare.wood("Charlie", roster, WOOD, b -> List.of(), n -> Optional.empty(), spent::add, 300);
        assertEquals(List.of(FOREST), spent);
        assertTrue(bare.spent("Charlie"));

        List<io.github.jayessdeesea.minecraft.command.Position> none = new ArrayList<>();
        DutyBook standing = new DutyBook();
        java.util.function.Function<io.github.jayessdeesea.minecraft.command.Box, List<int[]>> oneTree = b -> List.of(new int[]{0, 0});
        standing.wood("Charlie", roster, WOOD, oneTree, n -> Optional.empty(), none::add, 100);
        standing.searched("Ivy", Optional.of(whole));
        standing.wood("Charlie", roster, WOOD, oneTree, n -> Optional.empty(), none::add, 200);
        assertEquals(List.of(), none, "a log stands in it: not spent");
        assertEquals(io.github.jayessdeesea.minecraft.command.WoodDuties.Kind.WOODCUTTER, standing.wood("Charlie", roster, WOOD, oneTree,
                n -> Optional.empty(), none::add, 200 + DutyBook.REDEAL_MILLIS).get("Ivy").kind(), "dealt out whole once more in a while");
        standing.searched("Ivy", Optional.of(whole));
        standing.wood("Charlie", roster, WOOD, oneTree, n -> Optional.empty(), none::add, 300 + DutyBook.REDEAL_MILLIS);
        assertEquals(List.of(FOREST), none.stream().map(p -> new io.github.jayessdeesea.minecraft.command.Position(p.x(), p.y(), p.z())).toList(),
                "cut out a second time with the tree still standing: spent, what's left out of reach");
    }

    /** Two plots round two waters that overlap are one farm: one set of shares over both, no cell in two. */
    @Test
    void overlappingPlotsRoundTwoWatersAreOneFarm() {
        List<BlockPos> west = new ArrayList<>(), east = new ArrayList<>();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 9; x++) {
                west.add(new BlockPos(x, 63, z));
            }
            for (int x = 6; x < 15; x++) {
                east.add(new BlockPos(x, 63, z));
            }
        }
        Map<String, FarmPlan> farming = Map.of("Alice", new FarmPlan(new BlockPos(4, 62, 2), west), "Ivy", new FarmPlan(new BlockPos(10, 62, 2), east));

        Map<String, DutyOrder> alpha = DutyBook.given(List.of("Alice"), Map.of(), farming, Optional.empty());
        Map<String, DutyOrder> charlie = DutyBook.given(List.of("Ivy"), Map.of(), farming, Optional.empty());

        assertEquals(alpha, charlie, "one set of shares, whichever squad reckons it");
        assertEquals(List.of("Alice", "Ivy"), DutyBook.onOneFarm("Ivy", farming));
        assertNotEquals(alpha.get("Alice").ground(), alpha.get("Ivy").ground());
        assertTrue(alpha.get("Alice").ground().orElseThrow().maxX() < alpha.get("Ivy").ground().orElseThrow().minX(), "no column in both");
    }

    /**
     * A chunk farm off every harvester's strip, no tender yet - its ground not level, its chest not up - is the nearest
     * harvester's, and no other's. Live (field516-544), the camp's chunk farm lay a day nobody's, SURVEYED.
     */
    @Test
    void aChunkFarmOffEveryStripIsTheNearestHarvesters() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(1, 3, 63);
        FarmPlan farm = new FarmPlan(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(24, 63, 100), List.of(), List.of(), List.of(chunk));
        DutyBook book = new DutyBook();
        book.assign("Delta", List.of("Mia", "Ned"), Map.of(
                "Mia", new DutyOrder("Mia", DutyOrder.Duty.HARVESTER, Optional.of(new io.github.jayessdeesea.minecraft.command.Box(17, 60, 79, 20, 70, 127)), Optional.empty()),
                "Ned", new DutyOrder("Ned", DutyOrder.Duty.HARVESTER, Optional.of(new io.github.jayessdeesea.minecraft.command.Box(22, 60, 79, 29, 70, 127)), Optional.empty())),
                Optional.of("FARM"), 0);

        assertEquals(List.of(chunk), book.field("Ned", farm).chunks(), "Ned's strip over x 22..29 is nearest its chest at x=29");
        assertEquals(List.of(), book.field("Mia", farm).chunks());
    }
}
