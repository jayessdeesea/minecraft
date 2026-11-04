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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiggerTest {

    private static final int GROUND = 64;

    /** Flat ground below y=64; the given cells are logs (and solid). */
    private static WorldView forest(Set<BlockPos> logs) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void choosesTheNearestTreeAndACellBesideItsTrunk() {
        BlockPos near = new BlockPos(5, GROUND, 0);
        BlockPos far = new BlockPos(12, GROUND, 0);
        WorldView world = forest(Set.of(near, near.offset(0, 1, 0), far));

        Optional<Digger.Spot> tree = Digger.findSpot(world, new BlockPos(0, GROUND, 0), Set.of(), Digger.Target.LOG);

        assertEquals(Optional.of(new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), near)), tree,
                "stand west of the near trunk, chop its lowest log first");
    }

    @Test
    void aClaimedLogSendsTheNextSoldierToAnotherTree() {
        BlockPos near = new BlockPos(5, GROUND, 0);
        BlockPos far = new BlockPos(9, GROUND, 0);
        WorldView world = forest(Set.of(near, far));

        Optional<Digger.Spot> tree = Digger.findSpot(world, new BlockPos(0, GROUND, 0), Set.of(near), Digger.Target.LOG);

        assertEquals(far, tree.orElseThrow().block());
    }

    @Test
    void noTreeInReachIsNoTree() {
        assertEquals(Optional.empty(), Digger.findSpot(forest(Set.of(new BlockPos(40, GROUND, 0))), new BlockPos(0, GROUND, 0), Set.of(), Digger.Target.LOG));
    }

    /**
     * A server that breaks a block only once its break time has passed, says so a tick later, and
     * puts the drop in the inventory ten ticks after that - unless the inventory is {@code full}.
     */
    private static final class FakeServer {
        final Set<BlockPos> logs = new HashSet<>();
        final Inventory inventory = new Inventory();
        final List<String> calls = new ArrayList<>();
        final Map<Integer, Runnable> due = new HashMap<>();
        boolean full;
        int tick;
        int digStarted;
        final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
                new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    switch (method.getName()) {
                        case "startDigging" -> digStarted = tick;
                        case "finishDigging" -> {
                            BlockPos cell = new BlockPos((int) args[0], (int) args[1], (int) args[2]);
                            if (tick - digStarted >= 60) {
                                due.put(tick + 1, () -> logs.remove(cell));
                                if (!full) {
                                    due.put(tick + 11, () -> inventory.setSlot(20, new int[]{logId(), inventory.countAt(20) + 1}));
                                }
                            }
                        }
                        case "selectHotbarSlot" -> {
                        }
                        default -> throw new AssertionError("unexpected " + method.getName());
                    }
                    return null;
                });

        Digger.Progress run(Digger digger, Position soldier, int ticks) {
            Digger.Progress progress = new Digger.Progress.Working();
            for (int i = 0; i < ticks && progress instanceof Digger.Progress.Working; i++, tick++) {
                Optional.ofNullable(due.remove(tick)).ifPresent(Runnable::run);
                progress = digger.tick(soldier, forest(logs));
            }
            return progress;
        }
    }

    private static int logId() {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.LOG))).findFirst().orElseThrow();
    }

    @Test
    void aLogIsChoppedOnceItsBreakTimeHasPassedAndDoneWhenItsInThePack() {
        FakeServer server = new FakeServer();
        BlockPos log = new BlockPos(5, GROUND, 0);
        server.logs.add(log);
        Set<BlockPos> claims = new HashSet<>();
        Digger digger = new Digger("Carol", server.session, server.inventory, claims,
                new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), log));

        Digger.Progress progress = server.run(digger, new Position(4.5, GROUND, 0.5), 200);

        assertEquals(new Digger.Progress.Done(), progress);
        assertEquals(List.of("startDigging", "finishDigging"), server.calls);
        assertEquals(1, server.inventory.count(ItemClass.LOG));
        digger.release();
        assertEquals(Set.of(), claims);
    }

    @Test
    void aDropThatNeverArrivesFailsLoudly() {
        FakeServer server = new FakeServer();
        server.full = true;
        BlockPos log = new BlockPos(5, GROUND, 0);
        server.logs.add(log);
        Digger digger = new Digger("Carol", server.session, server.inventory, new HashSet<>(),
                new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), log));

        Digger.Progress progress = server.run(digger, new Position(4.5, GROUND, 0.5), 300);

        assertTrue(progress instanceof Digger.Progress.Failed failed && failed.why().contains("more LOG carried"), progress.toString());
    }
}
