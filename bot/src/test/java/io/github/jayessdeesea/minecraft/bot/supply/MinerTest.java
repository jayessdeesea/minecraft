package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A soldier cutting a spiral staircase down, against a server that breaks a block a tick after it's told. */
class MinerTest {

    private static final int GROUND = 64;
    private static final BlockPos HEAD = new BlockPos(0, GROUND, 0);

    private final Set<BlockPos> dug = new HashSet<>();
    private final Set<BlockPos> water = new HashSet<>();
    private final Set<BlockPos> ore = new HashSet<>();
    private final Set<BlockPos> tree = new HashSet<>();
    private final List<Runnable> answers = new ArrayList<>();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            BlockPos cell = new BlockPos(x, y, z);
            return tree.contains(cell) || y < GROUND && !dug.contains(cell) && !water.contains(cell);
        }

        @Override
        public Optional<String> leaves(int x, int y, int z) {
            BlockPos cell = new BlockPos(x, y, z);
            return tree.contains(cell) && !(x == 3 && z == 0) ? Optional.of("oak") : Optional.empty();
        }

        @Override
        public boolean isLog(int x, int y, int z) {
            return x == 3 && z == 0 && tree.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return water.contains(new BlockPos(x, y, z));
        }

        @Override
        public Optional<Hardness> hardness(int x, int y, int z) {
            return !isSolid(x, y, z) ? Optional.empty()
                    : isOre(x, y, z) ? Optional.of(new Hardness(3.0, 1)) : Optional.of(new Hardness(0.5, Hardness.HAND));
        }

        @Override
        public boolean isOre(int x, int y, int z) {
            BlockPos cell = new BlockPos(x, y, z);
            return ore.contains(cell) && !dug.contains(cell);
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "startDigging", "selectHotbarSlot", "swapWithHotbar" -> {
                    }
                    case "finishDigging" -> {
                        BlockPos cell = new BlockPos((int) args[0], (int) args[1], (int) args[2]);
                        answers.add(() -> dug.add(cell));
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private Miner.Progress dig(Staircase stairs, int ticks) {
        return dig(stairs, ticks, new Inventory());
    }

    private Miner.Progress dig(Staircase stairs, int ticks, Inventory inventory) {
        Miner miner = new Miner("Alice", session, inventory);
        Position soldier = new Position(0.5, GROUND, 0.5);
        Miner.Progress progress = null;
        for (int tick = 0; tick < ticks; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            progress = miner.tick(soldier, world, stairs, true);
            if (!(progress instanceof Miner.Progress.Working)) {
                break;
            }
            soldier = miner.standAt().orElse(soldier); // walks there at once
        }
        return progress;
    }

    @Test
    void cutsEveryStepAndTheStairsAreWalkableBothWays() {
        Staircase stairs = new Staircase(HEAD, GROUND - 12);

        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 30000));

        BlockPos bottom = stairs.step(stairs.steps() - 1);
        assertEquals(GROUND - 12, bottom.y());
        assertTrue(Pathfinder.find(world, HEAD, new Goal(bottom.x() + 0.5, bottom.y(), bottom.z() + 0.5, 0.4, 0), 5000).reachesGoal(),
                "down the stairs");
        assertTrue(Pathfinder.find(world, bottom, new Goal(HEAD.x() + 0.5, HEAD.y(), HEAD.z() + 0.5, 0.4, 0), 5000).reachesGoal(),
                "and back up");
        assertTrue(world.isSolid(0, GROUND - 6, 0), "the pillar stands");
    }

    @Test
    void stopsShortOfWaterBesideAStep() {
        Staircase stairs = new Staircase(HEAD, GROUND - 12);
        BlockPos beside = stairs.step(4).offset(0, 0, -1);
        water.add(beside);

        Miner.Progress progress = dig(stairs, 3000);

        assertInstanceOf(Miner.Progress.Stopped.class, progress);
        assertTrue(((Miner.Progress.Stopped) progress).why().contains("water or lava"), progress.toString());
        assertTrue(!dug.containsAll(stairs.cells(4)), "the step by the water stays uncut");
    }

    @Test
    void minesOreBesideTheStairsWithAPickaxeGoodEnoughButLeavesWhatItCantHarvest() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        BlockPos vein = stairs.step(4).offset(0, 0, 0);
        // Outside the 3x3: two blocks out from the pillar, beside step 4's feet (step 2's side is its alcove).
        BlockPos beside = new BlockPos(stairs.step(4).x() + Integer.signum(stairs.step(4).x() - HEAD.x()) * 1,
                stairs.step(4).y(), stairs.step(4).z() + (stairs.step(4).x() == HEAD.x() ? Integer.signum(stairs.step(4).z() - HEAD.z()) : 0));
        ore.add(beside);
        Inventory stonePickaxe = new Inventory();
        stonePickaxe.setSlot(Inventory.HOTBAR_FIRST, new int[]{pickaxe(1), 1});

        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 30000, stonePickaxe));
        assertTrue(dug.contains(beside), "the ore at " + beside + " beside " + vein + " is mined");

        dug.clear();
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 30000));
        assertTrue(!dug.contains(beside), "bare-handed, ore that drops nothing is left");
    }

    @Test
    void atTheBottomTheTunnelsAreCutAndOneMeetingWaterIsGivenUpAlone() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        List<Staircase.Cut> cuts = stairs.cuts();
        BlockPos besideBranch = cuts.stream().filter(c -> c.tunnel() == 1).toList().get(3).feet();
        water.add(new BlockPos(besideBranch.x(), besideBranch.y() + 2, besideBranch.z())); // above the branch's fourth head cell

        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000));

        Staircase.Cut lastMain = cuts.stream().filter(c -> c.tunnel() == 0).reduce((a, b) -> b).orElseThrow();
        assertTrue(dug.containsAll(lastMain.cells()), "the main tunnel is cut to its end");
        assertTrue(cuts.stream().filter(c -> c.tunnel() == 2).allMatch(c -> dug.containsAll(c.cells())), "the other branch at the same root is cut");
        assertTrue(!dug.contains(besideBranch), "the branch by the water stops short of it");
        BlockPos bottom = stairs.step(stairs.steps() - 1);
        assertTrue(cuts.stream().filter(c -> c.tunnel() >= 0).allMatch(c -> Math.abs(c.feet().x() - HEAD.x()) > 1 || Math.abs(c.feet().z() - HEAD.z()) > 1),
                "no tunnel back through the shaft");
        assertEquals(bottom.y(), lastMain.feet().y(), "level with the last step");
    }

    private static int pickaxe(int tier) {
        return IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.miningTier(id).equals(OptionalInt.of(tier)))
                .findFirst().orElseThrow();
    }

    @Test
    void theStairsAreSitedClearOfAPondBesideThePlaceAsked() {
        // A pond east of the place asked, through the top few blocks.
        for (int x = 1; x <= 5; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = GROUND - 4; y < GROUND; y++) {
                    water.add(new BlockPos(x, y, z));
                }
            }
        }

        Staircase stairs = Staircase.site(world, 0, 0, GROUND, 16).orElseThrow();

        assertTrue(stairs.clear(world), "every step clear of the water");
        assertTrue(stairs.head().x() < 0, "sited away from the pond, west: " + stairs.head());
        assertTrue(Math.max(Math.abs(stairs.head().x()), Math.abs(stairs.head().z())) <= Staircase.SITE_RADIUS);
    }

    @Test
    void anOakOverThePlaceAskedTakesTheStairsFromTheGroundNotItsCanopy() {
        // Live, an oak grew beside the camp's mine head, its canopy over it: the trunk 3 east, 4 high.
        for (int y = GROUND; y < GROUND + 4; y++) {
            tree.add(new BlockPos(3, y, 0));
        }
        for (int x = -2; x <= 5; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = GROUND + 3; y <= GROUND + 4; y++) {
                    tree.add(new BlockPos(x, y, z));
                }
            }
        }

        Staircase stairs = Staircase.site(world, 0, 0, GROUND, 16).orElseThrow();

        assertEquals(new BlockPos(0, GROUND, 0), stairs.head(), "sited where asked, on the ground under the leaves");
    }

    @Test
    void openGroundTakesTheStairsWhereAsked() {
        assertEquals(new BlockPos(0, GROUND, 0), Staircase.site(world, 0, 0, GROUND, 16).orElseThrow().head());
    }
}
