package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorwayTest {

    private final Set<BlockPos> wall = new HashSet<>(Set.of(new BlockPos(2, 64, 0), new BlockPos(2, 65, 0)));
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return wall.contains(new BlockPos(x, y, z));
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
    private final List<String> calls = new ArrayList<>();
    private final List<Integer> digStarted = new ArrayList<>();
    private int tick;
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                BlockPos cell = args != null && args.length == 3 ? new BlockPos((int) args[0], (int) args[1], (int) args[2]) : null;
                switch (method.getName()) {
                    case "startDigging" -> {
                        calls.add("start " + cell);
                        digStarted.add(tick);
                    }
                    case "finishDigging" -> {
                        // The server breaks it only if the full break time has passed.
                        if (tick - digStarted.getLast() >= Doorway.HAND_TICKS - 20) {
                            wall.remove(cell);
                        }
                        calls.add("finish " + cell);
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    @Test
    void bareHandedItBreaksBothBlocksOfTheDoorway() {
        Doorway doorway = new Doorway("Alice", session, new Inventory());
        List<BlockPos> cells = List.of(new BlockPos(2, 64, 0), new BlockPos(2, 65, 0));

        boolean open = false;
        for (tick = 0; tick < 1000 && !open; tick++) {
            open = doorway.tick(world, cells);
        }

        assertTrue(open);
        assertEquals(List.of("start BlockPos[x=2, y=64, z=0]", "finish BlockPos[x=2, y=64, z=0]",
                "start BlockPos[x=2, y=65, z=0]", "finish BlockPos[x=2, y=65, z=0]"), calls);
    }
}
