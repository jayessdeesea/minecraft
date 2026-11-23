package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
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
        return forest(logs, List.of());
    }

    /** {@link #forest(Set)}, with dropped items lying at {@code items}. */
    private static WorldView forest(Set<BlockPos> logs, List<Position> items) {
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
            public List<Position> itemsNear(Position center, double radius) {
                return items.stream().filter(i -> Math.hypot(i.x() - center.x(), i.z() - center.z()) <= radius).toList();
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void neverDigsABlockWithWaterBesideIt() {
        BlockPos byThePond = new BlockPos(5, GROUND, 0);
        BlockPos pond = byThePond.offset(1, 0, 0);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || byThePond.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return pond.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return byThePond.equals(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };

        assertEquals(Optional.empty(), Digger.findSpot(world, new BlockPos(0, GROUND, 0), Set.of(), Digger.Target.LOG),
                "broken, the water would flow into its place");
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

    /**
     * A tree whose every stand the soldier can't walk to is passed for one it can: live, stands shut
     * off on both sides of a diagonal were chosen one after another, each a "no route" CANTCO.
     */
    @Test
    void aTreeItCantWalkToIsPassedForOneItCan() {
        BlockPos near = new BlockPos(5, GROUND, 0);
        BlockPos far = new BlockPos(9, GROUND, 0);
        WorldView world = forest(Set.of(near, far));

        Optional<Digger.Spot> tree = Digger.findSpot(world, new BlockPos(0, GROUND, 0), Set.of(), Digger.Target.LOG, Digger.SEARCH_RADIUS,
                stand -> Math.abs(stand.x() - near.x()) + Math.abs(stand.z() - near.z()) > 1);

        assertEquals(far, tree.orElseThrow().block());
    }

    /**
     * A tree ten blocks down the slope from where the wood was asked for is found from the stands the
     * soldier can walk to, though a search four blocks above and below the asking point sees none.
     * Live, squads among trees on a hillside found "no LOG within 48 blocks" all day.
     */
    @Test
    void aTreeDownTheSlopeIsFoundFromTheStandsItCanWalkTo() {
        BlockPos tree = new BlockPos(9, GROUND - 10, 0);
        // A cliff: ground at GROUND for x < 6, ten lower beyond, the tree on the low ground.
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < (x < 6 ? GROUND : GROUND - 10) || tree.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return tree.equals(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        BlockPos asked = new BlockPos(0, GROUND, 0);

        assertEquals(Optional.empty(), Digger.findSpot(world, asked, Set.of(), Digger.Target.LOG), "four blocks above and below: none");
        Optional<Digger.Spot> found = Digger.findSpotAmong(world, asked, Set.of(), Digger.Target.LOG, Digger.SEARCH_RADIUS,
                List.of(new BlockPos(3, GROUND, 0), new BlockPos(8, GROUND - 10, 0), new BlockPos(10, GROUND - 10, 0)));
        assertEquals(tree, found.orElseThrow().block());
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

    @Test
    void ironOreTakesAStonePickaxeAndTheBestOneCarriedIsUsed() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("wooden_pickaxe"), 1});
        assertTrue(Digger.Target.STONE.canDig(inventory));
        assertEquals(false, Digger.Target.IRON_ORE.canDig(inventory), "a wooden pickaxe breaks iron ore for nothing");

        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("stone_pickaxe"), 1});
        assertTrue(Digger.Target.IRON_ORE.canDig(inventory));
        assertEquals(Inventory.MAIN_FIRST + 1, Digger.Target.bestTool(inventory));
    }

    @Test
    void aLogIsChoppedWithTheFastestAxeCarriedInASixthOfTheTime() {
        Inventory inventory = new Inventory();
        assertEquals(Digger.Target.LOG.digTicks, Digger.Target.LOG.digTicks(inventory), "by hand");
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("stone_axe"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("iron_axe"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item("iron_pickaxe"), 1});

        assertEquals(Inventory.MAIN_FIRST + 1, Digger.Target.bestAxe(inventory));
        assertEquals((int) Math.ceil(Digger.Target.LOG.digTicks / 6.0), Digger.Target.LOG.digTicks(inventory));
        assertEquals(Digger.Target.STONE.digTicks, Digger.Target.STONE.digTicks(inventory), "an axe is no use on stone");
    }

    @Test
    void soilIsDugWithTheFastestShovelCarriedInAQuarterOfTheTime() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("wooden_shovel"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("stone_shovel"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item("stone_axe"), 1});

        assertEquals(Inventory.MAIN_FIRST + 1, Digger.Target.SOIL.speedTool(inventory));
        assertEquals((int) Math.ceil(Digger.Target.SOIL.digTicks / 4.0), Digger.Target.SOIL.digTicks(inventory));
        assertEquals((int) Math.ceil(Digger.Target.LOG.digTicks / 4.0), Digger.Target.LOG.digTicks(inventory), "logs by the axe, not the shovel");
    }

    /** A block dug to clear it takes the tool of its own kind: a chest the axe, sand the shovel, andesite the pickaxe, glass a hand. */
    @Test
    void aBlockClearedIsDugWithTheToolOfItsKind() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("stone_axe"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("wooden_shovel"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item("iron_pickaxe"), 1});

        assertEquals(Inventory.MAIN_FIRST, Digger.Target.toolFor(new Hardness(2.5, Hardness.HAND, Hardness.Tool.AXE), inventory), "a chest: the axe");
        assertEquals(Inventory.MAIN_FIRST + 1, Digger.Target.toolFor(new Hardness(0.5, Hardness.HAND, Hardness.Tool.SHOVEL), inventory), "sand: the shovel");
        assertEquals(Inventory.MAIN_FIRST + 2, Digger.Target.toolFor(new Hardness(1.5, 0), inventory), "andesite: the pickaxe");
        assertEquals(-1, Digger.Target.toolFor(new Hardness(0.3, Hardness.HAND, Hardness.Tool.HAND), inventory), "glass: a hand");
        assertEquals(4, Digger.Target.speedFor(new Hardness(2.5, Hardness.HAND, Hardness.Tool.AXE), item("stone_axe")), "a stone axe's speed");
        assertEquals(1, Digger.Target.speedFor(new Hardness(2.5, Hardness.HAND, Hardness.Tool.AXE), item("iron_pickaxe")), "the wrong tool: hand speed");
    }

    static int item(String name) {
        return IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of(name))).findFirst().orElseThrow();
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
        final List<Position> items = new ArrayList<>();
        boolean full;
        /** Where the drop lands instead of in the pack: picked up only by standing on it. */
        Position landsAt;
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
                                if (landsAt != null) {
                                    due.put(tick + 11, () -> items.add(landsAt));
                                } else if (!full) {
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
                progress = digger.tick(soldier, forest(logs, items));
            }
            return progress;
        }

        /** {@link #run}, with the soldier stepping onto wherever the digger says to stand, and picking up what's there. */
        Digger.Progress follow(Digger digger, Position soldier, int ticks) {
            Digger.Progress progress = new Digger.Progress.Working();
            for (int i = 0; i < ticks && progress instanceof Digger.Progress.Working; i++, tick++) {
                Optional.ofNullable(due.remove(tick)).ifPresent(Runnable::run);
                Position here = soldier;
                if (items.removeIf(item -> Math.hypot(item.x() - here.x(), item.z() - here.z()) < 1)) {
                    inventory.setSlot(20, new int[]{logId(), inventory.countAt(20) + 1});
                }
                progress = digger.tick(soldier, forest(logs, items));
                soldier = digger.standAt();
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
    void aDropThatLandsOutOfReachIsWalkedOnto() {
        FakeServer server = new FakeServer();
        BlockPos log = new BlockPos(5, GROUND + 2, 0);
        server.logs.add(log);
        server.landsAt = new Position(6.7, GROUND, 1.8);
        Digger digger = new Digger("Carol", server.session, server.inventory, new HashSet<>(),
                new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), log));

        Digger.Progress progress = server.follow(digger, new Position(4.5, GROUND, 0.5), 300);

        assertEquals(new Digger.Progress.Done(), progress);
        assertEquals(1, server.inventory.count(ItemClass.LOG));
    }

    /**
     * A drop lying where no body can stand - fallen into a gap, here inside the ground - is lost, and
     * the dig is done: not walked to, not waited out. Live, three builders in turn stood a minute and
     * more over one under a dirt block.
     */
    @Test
    void aDropWhereNoBodyCanStandIsLostAndTheDigIsDone() {
        FakeServer server = new FakeServer();
        BlockPos log = new BlockPos(5, GROUND, 0);
        server.logs.add(log);
        server.landsAt = new Position(6.7, GROUND - 2, 1.8);
        Digger digger = new Digger("Carol", server.session, server.inventory, new HashSet<>(),
                new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), log));
        Position stand = new Position(4.5, GROUND, 0.5);

        Digger.Progress progress = server.run(digger, stand, 200);

        assertEquals(new Digger.Progress.Done(), progress);
        assertEquals(0, server.inventory.count(ItemClass.LOG));
        assertEquals(stand, digger.standAt(), "never sent to stand where it lies");
    }

    /**
     * A stand with a block in it - a step put there since it was chosen - ends the dig at once. Live,
     * builders stood six minutes on a step of their own, sent to stand inside it.
     */
    @Test
    void aStandWithABlockInItEndsTheDigAtOnce() {
        FakeServer server = new FakeServer();
        BlockPos log = new BlockPos(5, GROUND, 0);
        BlockPos stand = new BlockPos(4, GROUND, 0);
        server.logs.add(log);
        server.logs.add(stand);
        Digger digger = new Digger("Carol", server.session, server.inventory, new HashSet<>(),
                new Digger.Spot(Digger.Target.LOG, stand, log));

        Digger.Progress progress = server.run(digger, new Position(4.5, GROUND + 1, 0.5), 5);

        assertTrue(progress instanceof Digger.Progress.Failed(String why) && why.contains("has a block in it"), progress.toString());
    }

    @Test
    void aDropASquadmatePicksUpFirstIsPickedUp() {
        FakeServer server = new FakeServer();
        BlockPos log = new BlockPos(5, GROUND, 0);
        server.logs.add(log);
        Position drop = new Position(5.5, GROUND, 1.5);
        server.landsAt = drop;
        Digger digger = new Digger("Carol", server.session, server.inventory, new HashSet<>(),
                new Digger.Spot(Digger.Target.LOG, new BlockPos(4, GROUND, 0), log));
        Position soldier = new Position(4.5, GROUND, 0.5);
        assertEquals(new Digger.Progress.Working(), server.run(digger, soldier, 100));
        assertEquals(List.of(drop), server.items, "lying on the ground");
        server.items.remove(drop); // into someone else's pack

        Digger.Progress progress = server.run(digger, soldier, 300);

        assertEquals(new Digger.Progress.Done(), progress);
        assertEquals(0, server.inventory.count(ItemClass.LOG), "not this soldier's");
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

        assertTrue(progress instanceof Digger.Progress.Failed failed && failed.why().contains("LOG picked up"), progress.toString());
    }

    /**
     * The pickaxe for the job (operator, 2026-11-22: not the best pickaxe - the best pickaxe for the job): for plain rock
     * and for iron ore the stone one, the iron one kept; for diamond the iron one; and with only wood carried, none for
     * diamond or iron ore - they're left - and the wooden one for rock.
     */
    @Test
    void thePickaxeForTheJobIsTheLeastThatDoesItStoneBeforeWood() {
        Inventory all = new Inventory();
        all.setSlot(Inventory.MAIN_FIRST, new int[]{item("wooden_pickaxe"), 1});
        all.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("iron_pickaxe"), 1});
        all.setSlot(Inventory.HOTBAR_FIRST + 1, new int[]{item("stone_pickaxe"), 1});
        assertEquals(Inventory.HOTBAR_FIRST + 1, Digger.Target.pickaxeFor(all, 0), "rock: the stone one, not the wooden nor the iron");
        assertEquals(Inventory.HOTBAR_FIRST + 1, Digger.Target.pickaxeFor(all, 1), "iron ore: the stone one does it");
        assertEquals(Inventory.MAIN_FIRST + 1, Digger.Target.pickaxeFor(all, 2), "diamond: the iron one");
        assertEquals(-1, Digger.Target.pickaxeFor(all, 3), "obsidian: nothing carried does it");

        Inventory wood = new Inventory();
        wood.setSlot(Inventory.MAIN_FIRST, new int[]{item("wooden_pickaxe"), 1});
        assertEquals(Inventory.MAIN_FIRST, Digger.Target.pickaxeFor(wood, 0), "rock with only wood: the wooden one");
        assertEquals(-1, Digger.Target.pickaxeFor(wood, 1), "iron ore with only wood: left");
        assertEquals(-1, Digger.Target.pickaxeFor(new Inventory(), 0), "no pickaxe at all");
    }
}
