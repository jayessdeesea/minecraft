package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A wall round a camp on flat ground, laid out once and built by a soldier against a server that answers a tick late. */
class MasonTest {

    private static final int GROUND = 64;
    private static final BlockPos CENTRE = new BlockPos(0, GROUND, 0);

    private final Set<BlockPos> placed = new HashSet<>();
    /** Cells holding a torch: not solid, and no block goes in until it's broken. */
    private final Set<BlockPos> torches = new HashSet<>();
    private final List<Runnable> answers = new ArrayList<>();
    private final Inventory inventory = new Inventory();

    private final WorldView flat = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < GROUND || placed.contains(new BlockPos(x, y, z));
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

    @Test
    void aWallCellWhoseOwnColumnsAreBuiltOverIsBuiltFromTheColumnBeside() {
        // The south wall cell (1, 64, 3) of a camp 3 out; its columns in (z=2) and out (z=4) filled to y=71.
        WorldView builtOver = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || x == 1 && (z == 2 || z == 4) && y <= GROUND + 7;
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
        BlockPos cell = new BlockPos(1, GROUND, 3);

        BlockPos stand = Mason.inside(builtOver, cell, CENTRE, 3, new Position(0.5, GROUND, 0.5)).orElseThrow();

        assertEquals(2, stand.z(), "inside the wall");
        assertEquals(1, Math.abs(stand.x() - cell.x()), "a column along from the cell's own: " + stand);
    }

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "useBlockFace" -> {
                        io.github.jayessdeesea.minecraft.protocol.BlockFace face = (io.github.jayessdeesea.minecraft.protocol.BlockFace) args[3];
                        BlockPos cell = new BlockPos((int) args[0] + face.dx, (int) args[1] + face.dy, (int) args[2] + face.dz);
                        if (!torches.contains(cell)) {
                            answers.add(() -> placed.add(cell));
                        }
                    }
                    case "finishDigging" -> torches.remove(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    case "swapWithHotbar", "selectHotbarSlot", "startDigging" -> {
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    @Test
    void layoutIsARingTwoHighAndItStaysLaidOutAsItsBuilt() {
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);

        // 23 columns two high, and the lip: 32 round the 9x9 ring, less the one outside the doorway.
        assertEquals(23 * Perimeter.HEIGHT + 31, layout.size(), "24 columns round a 7x7 square less the doorway, two high, and the lip");
        assertTrue(layout.stream().noneMatch(c -> c.x() == 0 && (c.z() == 3 || c.z() == 4)), "the doorway, mid-south, stays open");
        assertTrue(layout.stream().filter(c -> Math.max(Math.abs(c.x()), Math.abs(c.z())) == 4).allMatch(c -> c.y() == GROUND + 1),
                "the lip at the wall's top row");
        placed.addAll(layout.subList(0, 23));
        assertEquals(23 + 31, Perimeter.open(flat, layout).size(), "the bottom row built, the top row and lip left - not a third row");
    }

    @Test
    void aSoldierBuildsTheWallBottomRowFirstFromInside() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 4000 && !Perimeter.open(flat, layout).isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            mason.tick(soldier, flat, CENTRE, 3, Perimeter.open(flat, layout), true);
            soldier = mason.standAt().orElse(soldier); // walks there at once
            assertTrue(Math.max(Math.abs(soldier.x() - 0.5), Math.abs(soldier.z() - 0.5)) < 3, "builds from inside: " + soldier);
        }

        assertEquals(List.of(), Perimeter.open(flat, layout));
        assertTrue(mason.failure().isEmpty(), mason.failure().orElse(""));
    }

    /**
     * As live: the wall's last dirt block had its stand inside the wall in a pocket sealed in the
     * hill - standable, and no way to it - and every mason walked 400 ticks at it, gave up, and the
     * next tried it again. Open ground outside was a stand too. A stand no route reaches isn't one.
     */
    @Test
    void aStandNoRouteReachesIsPassedOverForOneThatIs() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.COBBLESTONE), 64});
        BlockPos dirt = new BlockPos(3, GROUND, 0);
        BlockPos pocket = new BlockPos(2, GROUND, 0);
        BlockPos outside = new BlockPos(4, GROUND, 0);
        // The pocket: the cell inside the wall cell, sealed round and over, so nobody gets in.
        Set<BlockPos> sealing = new HashSet<>(Set.of(
                new BlockPos(1, GROUND, 0), new BlockPos(1, GROUND + 1, 0),
                new BlockPos(2, GROUND, 1), new BlockPos(2, GROUND + 1, 1),
                new BlockPos(2, GROUND, -1), new BlockPos(2, GROUND + 1, -1),
                new BlockPos(3, GROUND + 1, 0), dirt));
        // The hill over the pocket and the wall cell, too high to stand on top of: the pocket is the
        // first stand there is inside, as live.
        for (int y = GROUND + 2; y <= GROUND + 6; y++) {
            sealing.add(new BlockPos(2, y, 0));
            sealing.add(new BlockPos(3, y, 0));
        }
        WorldView hill = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || sealing.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return dirt.equals(new BlockPos(x, y, z));
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
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());

        mason.repoint(new Position(0.5, GROUND, -3.5), hill, CENTRE, 3, List.of(dirt), true);

        Position stand = mason.standAt().orElseThrow();
        assertTrue(!BlockPos.containing(stand.x(), stand.y(), stand.z()).equals(pocket), "not the sealed " + pocket);
        assertTrue(io.github.jayessdeesea.minecraft.bot.nav.Pathfinder.find(hill, new BlockPos(0, GROUND, -3),
                new io.github.jayessdeesea.minecraft.bot.nav.Goal(stand.x(), stand.y(), stand.z(), 0.3, 0.5), 20_000,
                io.github.jayessdeesea.minecraft.bot.nav.FallAllowance.NONE, 64).reachesGoal(), "a stand a route reaches: " + stand
                + " (open ground outside: " + outside + ")");
    }

    /**
     * As live: a soldier with a hostile hovering at the edge of reach is free one tick and not the
     * next, and its repointing started again at every free tick - 342 times for one cell - so the
     * dirt's break time never ran out. Taken for a moment, the job holds its place and goes on.
     */
    @Test
    void aRepointPausedAMomentAtATimeIsStillFinished() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.COBBLESTONE), 64});
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);
        placed.addAll(layout);
        BlockPos dirt = layout.getFirst();
        Set<BlockPos> soil = new HashSet<>(Set.of(dirt));
        Set<BlockPos> stone = new HashSet<>();
        WorldView walled = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return flat.isSolid(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return soil.contains(new BlockPos(x, y, z));
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
        PlaySession digging = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
                new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "finishDigging" -> {
                            BlockPos cell = new BlockPos((int) args[0], (int) args[1], (int) args[2]);
                            answers.add(() -> {
                                placed.remove(cell);
                                soil.remove(cell);
                            });
                        }
                        case "useBlockFace" -> {
                            io.github.jayessdeesea.minecraft.protocol.BlockFace face = (io.github.jayessdeesea.minecraft.protocol.BlockFace) args[3];
                            BlockPos cell = new BlockPos((int) args[0] + face.dx, (int) args[1] + face.dy, (int) args[2] + face.dz);
                            answers.add(() -> {
                                placed.add(cell);
                                stone.add(cell);
                            });
                        }
                        case "swapWithHotbar", "selectHotbarSlot", "startDigging" -> {
                        }
                        default -> throw new AssertionError("unexpected " + method.getName());
                    }
                    return null;
                });
        Mason mason = new Mason("Alice", inventory, new Footing(digging, inventory), new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 2000 && !stone.contains(dirt); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            mason.repoint(soldier, walled, CENTRE, 3, List.of(dirt), tick % 2 == 0);
            soldier = mason.standAt().orElse(soldier);
        }

        assertTrue(stone.contains(dirt), "the dirt replaced with stone; " + mason.failure().orElse("no failure"));
    }

    /**
     * As live: the masons' dirt pit dug under the wall's east side and the ground inside it, and 39
     * cells had nothing to go against or nowhere to stand. The footing goes in from the pit's floor,
     * built from outside.
     */
    @Test
    void aWallWhoseGroundWasDugAwayIsBuiltBackUpFromTheFootingFromOutside() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);
        // Dug out: two blocks deep under the east side and the column inside it.
        Set<BlockPos> pit = new HashSet<>();
        for (int x = 2; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                pit.add(new BlockPos(x, GROUND - 1, z));
                pit.add(new BlockPos(x, GROUND - 2, z));
            }
        }
        WorldView dug = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return placed.contains(c) || y < GROUND && !pit.contains(c);
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
        assertTrue(Perimeter.open(dug, layout).contains(new BlockPos(3, GROUND - 2, 0)), "the footing is part of what's left");
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 8000 && !Perimeter.open(dug, layout).isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            mason.tick(soldier, dug, CENTRE, 3, Perimeter.open(dug, layout), true);
            soldier = mason.standAt().orElse(soldier);
        }

        assertEquals(List.of(), Perimeter.open(dug, layout));
    }

    @Test
    void aTorchOnTheWallsLineIsBrokenAndTheBlockGoesIn() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);
        torches.add(layout.getFirst());
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 4000 && !Perimeter.open(flat, layout).isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            mason.tick(soldier, flat, CENTRE, 3, Perimeter.open(flat, layout), true);
            soldier = mason.standAt().orElse(soldier);
        }

        assertEquals(List.of(), Perimeter.open(flat, layout));
        assertTrue(mason.failure().isEmpty(), mason.failure().orElse(""));
    }

    @Test
    void aWallOnASlopeRisesColumnByColumnNotRowByRowAcrossTheCamp() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        // The ground steps up a block for each column east: the wall's rows are at seven heights.
        WorldView slope = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND + Math.max(0, x + 3) || placed.contains(new BlockPos(x, y, z));
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
        List<BlockPos> layout = Perimeter.layout(slope, CENTRE, 3);
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position soldier = new Position(0.5, GROUND + 3, 0.5);

        for (int tick = 0; tick < 8000 && !Perimeter.open(slope, layout).isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            mason.tick(soldier, slope, CENTRE, 3, Perimeter.open(slope, layout), true);
            soldier = mason.standAt().orElse(soldier);
        }

        assertEquals(List.of(), Perimeter.open(slope, layout).stream().filter(c -> Math.max(Math.abs(c.x()), Math.abs(c.z())) == 3).toList(),
                "every wall cell built, high side and low");
    }

    @Test
    void aCellWhosePlaceCantBeReachedIsPassedOverForTheRest() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        List<BlockPos> layout = Perimeter.layout(flat, CENTRE, 3);
        BlockPos first = layout.getFirst();
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position stuck = new Position(0.5, GROUND, 0.5);

        // She never gets to the first cell's place: the walk times out.
        for (int tick = 0; tick <= Mason.WALK_TICKS + 1; tick++) {
            mason.tick(stuck, flat, CENTRE, 3, List.of(first, layout.get(1)), true);
        }
        assertTrue(mason.failure().isPresent());
        for (int tick = 0; tick <= Mason.COOLDOWN_TICKS; tick++) {
            mason.tick(stuck, flat, CENTRE, 3, List.of(first, layout.get(1)), true);
        }

        BlockPos next = layout.get(1);
        Position nextPlace = new Position(Mason.inside(flat, next, CENTRE, 3, stuck).orElseThrow().x() + 0.5, GROUND,
                Mason.inside(flat, next, CENTRE, 3, stuck).orElseThrow().z() + 0.5);
        assertEquals(Optional.of(nextPlace), mason.standAt(), "the next cell, not the one out of reach again");
    }

    @Test
    void aCellComingBackRoundIsBuiltFromAnotherPlaceThanTheOneOutOfReach() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 64});
        BlockPos first = Perimeter.layout(flat, CENTRE, 3).getFirst();
        Mason mason = new Mason("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        Position stuck = new Position(0.5, GROUND, 0.5);
        BlockPos unreached = Mason.inside(flat, first, CENTRE, 3, stuck).orElseThrow();

        // She never gets to the cell's first place; then, the cell passed over and back again, she
        // goes for it from another.
        for (int tick = 0; tick <= Mason.WALK_TICKS + 1; tick++) {
            mason.tick(stuck, flat, CENTRE, 3, List.of(first), true);
        }
        for (int tick = 0; tick <= Mason.PASS_OVER_TICKS + Mason.COOLDOWN_TICKS + 2; tick++) {
            mason.tick(stuck, flat, CENTRE, 3, List.of(first), true);
        }

        assertTrue(mason.standAt().isPresent(), "back at the cell");
        assertTrue(!mason.standAt().get().equals(new Position(unreached.x() + 0.5, unreached.y(), unreached.z() + 0.5)),
                "not the place it couldn't get to: " + mason.standAt());
    }

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }
}
