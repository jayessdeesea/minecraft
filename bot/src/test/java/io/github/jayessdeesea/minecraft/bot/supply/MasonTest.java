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

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }
}
