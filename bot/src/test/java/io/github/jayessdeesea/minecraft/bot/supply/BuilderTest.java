package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderTest {

    /** Flat ground at y=63: the soldier stands at y=64. */
    private final Set<BlockPos> solid = new HashSet<>();
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63 || solid.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }

        @Override
        public List<Position> bodiesNear(Position center, double radius) {
            return bodies;
        }
    };
    /** Who else is standing about: the server won't place a block into one. */
    private final List<Position> bodies = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    /** What the server sends next tick: it answers a placement or a swap a tick after it's asked. */
    private final List<Runnable> answers = new ArrayList<>();
    /** Cells holding something not solid that a block can't replace until it's broken. */
    private final Set<BlockPos> flowers = new HashSet<>();
    /** Whether the server lets a placement land. */
    private boolean placementsLand = true;
    private boolean swapsUnanswered;
    /** The soldier's inventory, which the fake server swaps items in as the real one would. */
    private final Inventory inventory = armedWithCobblestone();
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "useBlockFace" -> {
                        BlockFace face = (BlockFace) args[3];
                        BlockPos placed = new BlockPos((int) args[0] + face.dx, (int) args[1] + face.dy, (int) args[2] + face.dz);
                        calls.add("place " + placed);
                        if (placementsLand && !flowers.contains(placed)) {
                            answers.add(() -> solid.add(placed));
                        }
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = stack(inventory, slot);
                        int[] staying = stack(inventory, hotbar);
                        answers.add(() -> {
                            if (swapsUnanswered) {
                                return;
                            }
                            inventory.setSlot(slot, staying);
                            inventory.setSlot(hotbar, moving);
                        });
                        calls.add("swap " + slot + " to hotbar " + args[3]);
                    }
                    case "selectHotbarSlot" -> calls.add("select " + args[0]);
                    case "startDigging" -> flowers.remove(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    case "finishDigging" -> calls.add("dig " + new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    private static int[] stack(Inventory inventory, int slot) {
        return inventory.itemAt(slot) < 0 ? null : new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
    }

    private static Inventory armedWithCobblestone() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.HOTBAR_FIRST, new int[]{itemOf(ItemClass.WEAPON), 1});
        inventory.setSlot(20, new int[]{itemOf(ItemClass.COBBLESTONE), 32});
        return inventory;
    }

    private Builder.Progress run(Builder builder, Position soldier, List<BlockPos> plan, int ticks) {
        Builder.Progress progress = null;
        for (int i = 0; i < ticks; i++) {
            answers.forEach(Runnable::run);
            answers.clear();
            progress = builder.tick(soldier, world, plan);
            if (!(progress instanceof Builder.Progress.Working)) {
                return progress;
            }
        }
        return progress;
    }

    @Test
    void buildsAWallBesideItFromCobblestoneThenTakesItsSwordBack() {
        Builder builder = new Builder("Alice", session, inventory);
        List<BlockPos> wall = List.of(new BlockPos(2, 64, 0), new BlockPos(2, 65, 0), new BlockPos(2, 64, 1));

        Builder.Progress progress = run(builder, new Position(0.5, 64, 0.5), wall, 100);

        assertInstanceOf(Builder.Progress.Done.class, progress);
        assertEquals(List.of("swap 20 to hotbar 8", "select 8",
                "place BlockPos[x=2, y=64, z=0]", "place BlockPos[x=2, y=65, z=0]", "place BlockPos[x=2, y=64, z=1]",
                "select 0"), calls);
        assertEquals(0, inventory.heldHotbarSlot());
    }

    @Test
    void aHotbarFullOfFoodAndTorchesTakesTheBlocksInPlaceOfTheLast() {
        for (int hotbar = 1; hotbar <= 8; hotbar++) {
            ItemClass kind = hotbar % 3 == 0 ? ItemClass.FOOD : hotbar % 3 == 1 ? ItemClass.TORCH : ItemClass.SEEDS;
            inventory.setSlot(Inventory.HOTBAR_FIRST + hotbar, new int[]{itemOf(kind), 4});
        }
        Builder builder = new Builder("Alice", session, inventory);

        Builder.Progress progress = run(builder, new Position(0.5, 64, 0.5), List.of(new BlockPos(2, 64, 0)), 100);

        assertInstanceOf(Builder.Progress.Done.class, progress);
        assertEquals(List.of("swap 20 to hotbar 8", "select 8", "place BlockPos[x=2, y=64, z=0]", "select 0"), calls);
        assertEquals(itemOf(ItemClass.WEAPON), inventory.itemAt(Inventory.HOTBAR_FIRST), "the sword kept");
    }

    @Test
    void aCellSomeoneStandsInIsWaitedOnNotGivenUp() {
        Builder builder = new Builder("Alice", session, inventory);
        BlockPos cell = new BlockPos(2, 64, 0);
        bodies.add(new Position(2.5, 64, 0.5)); // a player watching, in the wall's footprint

        Builder.Progress waiting = run(builder, new Position(0.5, 64, 0.5), List.of(cell), 100);
        assertInstanceOf(Builder.Progress.Working.class, waiting, "still waiting, not failed");
        assertEquals(0, calls.stream().filter(c -> c.startsWith("place")).count(), "no try spent on a body");

        bodies.clear();
        assertInstanceOf(Builder.Progress.Done.class, run(builder, new Position(0.5, 64, 0.5), List.of(cell), 100));
    }

    @Test
    void aCellThatWontTakeABlockIsSkippedAfterAFewTries() {
        placementsLand = false;
        Builder builder = new Builder("Alice", session, inventory);

        Builder.Progress progress = run(builder, new Position(0.5, 64, 0.5), List.of(new BlockPos(2, 64, 0)), 100);

        assertEquals(new Builder.Progress.Failed("1 cell(s) wouldn't take a block"), progress);
        assertEquals(Builder.ATTEMPTS, calls.stream().filter(c -> c.startsWith("place")).count());
    }

    @Test
    void aBlockIsPutAgainstTheGroundNotAgainstACraftingTable() {
        // Over a hole, the table to the south is the first side found - and clicking it opens it.
        BlockPos cell = new BlockPos(2, 64, 0);
        BlockPos table = new BlockPos(2, 64, 1);
        BlockPos ground = new BlockPos(1, 64, 0);
        WorldView camp = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos at = new BlockPos(x, y, z);
                return at.equals(table) || at.equals(ground);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }

            @Override
            public Optional<io.github.jayessdeesea.minecraft.bot.world.Workstation> workstation(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(table)
                        ? Optional.of(io.github.jayessdeesea.minecraft.bot.world.Workstation.CRAFTING_TABLE) : Optional.empty();
            }
        };

        assertEquals(Optional.of(BlockFace.WEST), Builder.support(camp, cell));
    }

    @Test
    void aFlowerInTheWayIsBrokenAndTheBlockGoesIn() {
        BlockPos cell = new BlockPos(2, 64, 0);
        flowers.add(cell);
        Builder builder = new Builder("Alice", session, inventory);

        Builder.Progress progress = run(builder, new Position(0.5, 64, 0.5), List.of(cell), 100);

        assertInstanceOf(Builder.Progress.Done.class, progress);
        assertEquals(List.of("swap 20 to hotbar 8", "select 8", "place " + cell, "dig " + cell, "place " + cell, "select 0"), calls);
    }

    @Test
    void aSwapTheServerNeverAnswersStopsTheBuildingLoudly() {
        swapsUnanswered = true;
        Builder builder = new Builder("Alice", session, inventory);

        Builder.Progress progress = run(builder, new Position(0.5, 64, 0.5), List.of(new BlockPos(2, 64, 0)), 100);

        assertInstanceOf(Builder.Progress.Failed.class, progress);
        assertTrue(((Builder.Progress.Failed) progress).why().contains("building material in hand"), progress.toString());
        assertEquals(List.of("swap 20 to hotbar 8", "select 8", "select 0"), calls);
    }

    @Test
    void withNoBlocksItCantBuild() {
        Builder builder = new Builder("Alice", session, new Inventory());

        assertEquals(new Builder.Progress.Failed("no blocks to build with"),
                builder.tick(new Position(0.5, 64, 0.5), world, List.of(new BlockPos(2, 64, 0))));
    }

    @Test
    void outOfReachItWalksToASpotBesideTheCellNotIntoIt() {
        Builder builder = new Builder("Alice", session, inventory);
        BlockPos cell = new BlockPos(20, 64, 0);

        builder.tick(new Position(0.5, 64, 0.5), world, List.of(cell));

        Position spot = builder.standAt().orElseThrow();
        assertTrue(Builder.withinReach(spot, cell) && !Builder.overlaps(spot, cell), "stands at " + spot);
    }
}
