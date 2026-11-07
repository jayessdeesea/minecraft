package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
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

/** Where torches go to secure ground, and a soldier putting them down against a server that answers a tick late. */
class LightingTest {

    private static final int GROUND = 64;
    private static final BlockPos CENTRE = new BlockPos(0, GROUND, 0);

    private final Set<BlockPos> torches = new HashSet<>();

    private final WorldView flat = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < GROUND;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isTorch(int x, int y, int z) {
            return torches.contains(new BlockPos(x, y, z));
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    @Test
    void lightsEveryWalkableCellOfFlatGround() {
        List<BlockPos> plan = Lighting.plan(flat, CENTRE, 12);

        assertTrue(plan.size() <= 20, "a 25x25 square takes a sparse grid, not " + plan.size());
        torches.addAll(plan);
        assertEquals(0, Lighting.dark(flat, CENTRE, 12));
        Pathfinder.reachable(flat, CENTRE, 12, 6000).forEach(cell -> assertTrue(Lighting.lit(cell, List.copyOf(plan)), cell + " left dark"));
    }

    @Test
    void groundAlreadyLitNeedsNoMoreTorches() {
        torches.addAll(Lighting.plan(flat, CENTRE, 12));

        assertEquals(List.of(), Lighting.plan(flat, CENTRE, 12));
    }

    @Test
    void aSoldierPutsDownATorchOnEachCellItTakes() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.TORCH), 64});
        List<Runnable> answers = new ArrayList<>();
        PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(), new Class<?>[]{PlaySession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "useBlockFace" -> {
                            BlockPos on = new BlockPos((int) args[0], (int) args[1] + 1, (int) args[2]);
                            answers.add(() -> torches.add(on));
                        }
                        case "swapWithHotbar", "selectHotbarSlot" -> {
                        }
                        default -> throw new AssertionError("unexpected " + method.getName());
                    }
                    return null;
                });
        Lamplighter lamplighter = new Lamplighter("Alice", inventory, new Footing(session, inventory), new HashSet<>());
        List<BlockPos> plan = Lighting.plan(flat, CENTRE, 12);
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 2000 && !Lighting.plan(flat, CENTRE, 12).isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            lamplighter.tick(soldier, flat, plan, true);
            soldier = lamplighter.standAt().orElse(soldier); // walks there at once
        }

        assertEquals(Set.copyOf(plan), torches, "a torch on every planned cell");
        assertTrue(lamplighter.failure().isEmpty(), lamplighter.failure().orElse(""));
    }

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }
}
