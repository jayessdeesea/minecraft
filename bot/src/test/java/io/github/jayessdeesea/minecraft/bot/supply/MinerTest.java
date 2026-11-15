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
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
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
    /** An open cave, and the blocks put into it. */
    private final Set<BlockPos> cave = new HashSet<>();
    private final Set<BlockPos> placed = new HashSet<>();
    /** Chests standing - a stash in the store's recess. */
    private final Set<BlockPos> chests = new HashSet<>();
    private final List<Runnable> answers = new ArrayList<>();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            BlockPos cell = new BlockPos(x, y, z);
            return tree.contains(cell) || placed.contains(cell) || chests.contains(cell) || y < GROUND && !dug.contains(cell) && !water.contains(cell) && !cave.contains(cell);
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
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
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
                    case "useBlockFace" -> {
                        BlockFace face = (BlockFace) args[3];
                        BlockPos cell = new BlockPos((int) args[0] + face.dx, (int) args[1] + face.dy, (int) args[2] + face.dz);
                        answers.add(() -> placed.add(cell));
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
        return dig(stairs, ticks, inventory, new Position(0.5, GROUND, 0.5));
    }

    private Miner.Progress dig(Staircase stairs, int ticks, Inventory inventory, Position from) {
        Miner miner = new Miner("Alice", session, inventory);
        Position soldier = from;
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

    /** A well to the standard is cut to its landing, walkable both ways, its 2x2 pillar standing. */
    @Test
    void aWellIsCutAndWalkableBothWaysItsPillarStanding() {
        Staircase well = Staircase.wellAt(world, 0, 0, GROUND, GROUND - 16).orElseThrow();
        assertEquals(48, well.bottom(), "the landing at y=48, the deepest twelve under the 52 asked");

        assertInstanceOf(Miner.Progress.Done.class, dig(well, 60000, new Inventory(), new Position(well.head().x() + 0.5, well.head().y(), well.head().z() + 0.5)));

        BlockPos bottom = well.step(well.steps() - 1);
        assertEquals(new BlockPos(9, 48, 6), bottom);
        assertTrue(Pathfinder.find(world, well.head(), new Goal(bottom.x() + 0.5, bottom.y(), bottom.z() + 0.5, 0.4, 0), 5000).reachesGoal(),
                "down the well");
        assertTrue(Pathfinder.find(world, bottom, new Goal(well.head().x() + 0.5, well.head().y(), well.head().z() + 0.5, 0.4, 0), 5000).reachesGoal(),
                "and back up");
        for (int y = bottom.y(); y < GROUND; y++) {
            for (int x = 7; x <= 8; x++) {
                for (int z = 7; z <= 8; z++) {
                    assertTrue(world.isSolid(x, y, z), "the pillar at " + x + "," + y + "," + z);
                }
            }
        }
    }

    /**
     * The stair cutter keeps its well whole (operator, 2026-11-15): a gap in the pillar and a hole in
     * the wall beside a step - a cave's mouth - are filled as it passes, in cobblestone.
     */
    @Test
    void holesInTheWellsPillarAndWallsAreFilledAsTheStairsAreCut() {
        Staircase well = Staircase.wellAt(world, 0, 0, GROUND, GROUND - 16).orElseThrow();
        BlockPos step = well.step(4);
        BlockPos pillarGap = java.util.stream.Stream.of(step.offset(1, 0, 0), step.offset(-1, 0, 0), step.offset(0, 0, 1), step.offset(0, 0, -1))
                .filter(c -> well.inPillar(c.x(), c.z())).findFirst().orElseThrow();
        // Out of the shaft from the step: east if it's on the east side, west if west, else north or south.
        int lx = step.x(), lz = step.z();
        BlockPos wallHole = lx == 9 ? step.offset(1, 0, 0) : lx == 6 ? step.offset(-1, 0, 0) : lz == 6 ? step.offset(0, 0, -1) : step.offset(0, 0, 1);
        cave.add(pillarGap);
        cave.add(wallHole);
        Inventory stone = new Inventory();
        stone.setSlot(Inventory.MAIN_FIRST, new int[]{IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClassRegistry.ItemClass.COBBLESTONE))).findFirst().orElseThrow(), 64});

        dig(well, 60000, stone, new Position(well.head().x() + 0.5, well.head().y(), well.head().z() + 0.5));

        assertTrue(placed.contains(pillarGap), "the pillar's gap at " + pillarGap + " filled");
        assertTrue(placed.contains(wallHole), "the wall's hole at " + wallHole + " filled");
    }

    /** Ore in the well's wall is mined, and its cell put back once the vein's done, so the well stays closed. */
    @Test
    void oreInTheWellsWallIsMinedAndItsCellPutBack() {
        Staircase well = Staircase.wellAt(world, 0, 0, GROUND, GROUND - 16).orElseThrow();
        BlockPos step = well.step(4);
        BlockPos vein = step.x() == 9 ? step.offset(1, 0, 0) : step.x() == 6 ? step.offset(-1, 0, 0) : step.z() == 6 ? step.offset(0, 0, -1) : step.offset(0, 0, 1);
        ore.add(vein);
        Inventory kit = new Inventory();
        kit.setSlot(Inventory.HOTBAR_FIRST, new int[]{pickaxe(1), 1});
        kit.setSlot(Inventory.MAIN_FIRST, new int[]{IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClassRegistry.ItemClass.COBBLESTONE))).findFirst().orElseThrow(), 64});

        dig(well, 60000, kit, new Position(well.head().x() + 0.5, well.head().y(), well.head().z() + 0.5));

        assertTrue(dug.contains(vein), "the ore at " + vein + " mined");
        assertTrue(placed.contains(vein), "and its cell put back");
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

    /**
     * A branch over an open cave is floored with cobblestone from the cell before it and cut on, not
     * given up: live, every branch of the mine met a cave, and nobody mined at night.
     */
    @Test
    void aTunnelOverACaveIsFlooredAndCutOn() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        List<Staircase.Cut> branch = stairs.cuts().stream().filter(c -> c.tunnel() == 1).toList();
        // A cave under the branch's third and fourth cells: their floors and two below them open.
        for (Staircase.Cut c : branch.subList(2, 4)) {
            for (int down = 1; down <= 3; down++) {
                cave.add(c.feet().offset(0, -down, 0));
            }
        }
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of("cobblestone"))).findFirst().orElseThrow(), 64});

        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000, inventory));

        assertTrue(branch.subList(2, 4).stream().allMatch(c -> placed.contains(c.feet().offset(0, -1, 0))), "the gap floored: " + placed);
        assertTrue(dug.containsAll(branch.getLast().cells()), "and the branch cut to its end");
    }

    /**
     * A gap in a wide cave with no solid block beside it, the stand diagonal to it - as live, off the
     * stairs' last step into the main tunnel: a block goes beside the gap first, against the stand's
     * floor it touches, and the gap against that.
     */
    @Test
    void aGapWithNothingBesideItIsBridgedFromTheBlockNextToIt() {
        // The stand on (1, 64, 1), its floor at y=63; the gap under (0, 64, 0), diagonal to it.
        BlockPos stand = new BlockPos(1, GROUND, 1), feet = new BlockPos(0, GROUND, 0), gap = feet.offset(0, -1, 0);
        Staircase.Cut cut = new Staircase.Cut(stand, List.of(feet.offset(0, 1, 0), feet), 0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    if (!(dx == 1 && dz == 1 && dy == 1)) { // the stand's floor stays
                        cave.add(new BlockPos(dx, GROUND - dy, dz));
                    }
                }
            }
        }

        Optional<Miner.Bridge> first = Miner.bridgeable(world, cut);
        assertTrue(first.isPresent(), "a way to build out to the gap");
        assertTrue(!first.get().cell().equals(gap) && Math.abs(first.get().cell().x() - gap.x()) + Math.abs(first.get().cell().z() - gap.z()) == 1,
                "a block beside the gap first: " + first.get());
        placed.add(first.get().cell());
        assertEquals(gap, Miner.bridgeable(world, cut).orElseThrow().cell(), "then the gap, against it");
    }

    /**
     * With nothing to floor a cave with, the tunnel over it waits rather than being given up: the
     * mine's other tunnels are cut meanwhile, and the stone they give floors it after.
     */
    @Test
    void aTunnelOverACaveWaitsForBlocksToFloorItRatherThanBeingGivenUp() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        List<Staircase.Cut> branch = stairs.cuts().stream().filter(c -> c.tunnel() == 1).toList();
        cave.add(branch.get(2).feet().offset(0, -1, 0));
        Inventory inventory = new Inventory();
        Miner miner = new Miner("Alice", session, inventory);
        Position soldier = new Position(0.5, GROUND, 0.5);
        for (int tick = 0; tick < 60000; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            // The stone the other tunnels give, carried once some are cut.
            if (dug.size() > 40 && inventory.count(ItemClassRegistry.ItemClass.COBBLESTONE) == 0 && placed.isEmpty()) {
                inventory.setSlot(Inventory.MAIN_FIRST, new int[]{IntStream.range(0, 4096)
                        .filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of("cobblestone"))).findFirst().orElseThrow(), 8});
            }
            if (!(miner.tick(soldier, world, stairs, true) instanceof Miner.Progress.Working)) {
                break;
            }
            soldier = miner.standAt().orElse(soldier);
        }

        assertTrue(placed.contains(branch.get(2).feet().offset(0, -1, 0)), "the cave floored once it had the stone");
        assertTrue(dug.containsAll(branch.getLast().cells()), "and the branch cut to its end");
    }

    /**
     * A mine long cut is worked again for its ore: ore in sight of a tunnel the miner cut before -
     * passed by, or one it couldn't harvest then - is mined before the mine counts as done. Live,
     * no miner mined ore in twelve runs: the tunnels were cut, and nobody went back down them.
     */
    @Test
    void aMineAlreadyCutIsWorkedForTheOreInSightOfItsTunnels() {
        Staircase stairs = new Staircase(HEAD, GROUND - 20);
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 120000));
        BlockPos above = stairs.cuts().stream().filter(c -> c.tunnel() == 1).toList().get(3).feet().offset(0, 2, 0);
        dug.remove(above);
        ore.add(above);
        Inventory stonePickaxe = new Inventory();
        stonePickaxe.setSlot(Inventory.HOTBAR_FIRST, new int[]{pickaxe(1), 1});

        Miner miner = new Miner("Alice", session, stonePickaxe);
        assertInstanceOf(Miner.Progress.Done.class, miner.tick(new Position(0.5, GROUND, 0.5), world, stairs, true),
                "from the top, the mine reads done: the squad holds the top");

        BlockPos landing = stairs.step(stairs.steps() - 1);
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000, stonePickaxe, new Position(landing.x() + 0.5, landing.y(), landing.z() + 0.5)));
        assertTrue(dug.contains(above), "from the bottom, the ore over branch 1 at " + above + " is mined");
    }

    /**
     * A vein running back into the wall is followed: the block touching the tunnel opens the next,
     * and that one the next, while they're in reach (operator, 2026-11-15: coal and copper left
     * behind).
     */
    @Test
    void aVeinIsFollowedIntoTheWallWhileItsInReach() {
        Staircase stairs = new Staircase(HEAD, GROUND - 20);
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 120000));
        Staircase.Cut cut = stairs.cuts().stream().filter(c -> c.tunnel() == 1).toList().get(3);
        Staircase.Cut next = stairs.cuts().stream().filter(c -> c.tunnel() == 1).toList().get(4);
        // Out of the branch's side, at head height: across the branch from the way it runs.
        int sx = next.feet().z() - cut.feet().z(), sz = cut.feet().x() - next.feet().x();
        BlockPos first = cut.feet().offset(sx, 1, sz), second = first.offset(sx, 0, sz);
        for (BlockPos vein : List.of(first, second)) {
            dug.remove(vein);
            ore.add(vein);
        }
        Inventory stonePickaxe = new Inventory();
        stonePickaxe.setSlot(Inventory.HOTBAR_FIRST, new int[]{pickaxe(1), 1});

        BlockPos landing = stairs.step(stairs.steps() - 1);
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000, stonePickaxe, new Position(landing.x() + 0.5, landing.y(), landing.z() + 0.5)));
        assertTrue(dug.contains(first), "the vein's first block, touching the branch, at " + first);
        assertTrue(dug.contains(second), "and the one behind it, opened by the first, at " + second);
    }

    /**
     * Four miners on one mine, each its share (operator, 2026-11-15: they stood on one another): the
     * whole mine is cut, each cuts only in its own tunnels, and no two ever stand on one cell, cutting
     * or waiting. Those whose stretch the main tunnel hasn't reached wait on steps of their own.
     */
    @Test
    void fourMinersShareOneMineAndNeverStandOnOneAnother() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        int miners = 4;
        List<Miner> crew = new ArrayList<>();
        List<Position> at = new ArrayList<>();
        List<Set<BlockPos>> cutBy = new ArrayList<>();
        for (int m = 0; m < miners; m++) {
            Set<BlockPos> mine = new HashSet<>();
            cutBy.add(mine);
            crew.add(new Miner("Miner" + m, recording(mine), new Inventory()));
            at.add(new Position(0.5, GROUND, 0.5));
        }
        boolean[] done = new boolean[miners];
        for (int tick = 0; tick < 200_000 && !allTrue(done); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            for (int m = 0; m < miners; m++) {
                if (done[m]) {
                    continue;
                }
                Miner.Progress progress = crew.get(m).tick(at.get(m), world, stairs, true, Optional.of(stairs.share(m, miners)), m);
                done[m] = progress instanceof Miner.Progress.Done;
                at.set(m, crew.get(m).standAt().orElse(at.get(m)));
            }
            for (int m = 0; m < miners; m++) {
                for (int o = m + 1; o < miners; o++) {
                    BlockPos a = BlockPos.containing(at.get(m).x(), at.get(m).y(), at.get(m).z());
                    BlockPos b = BlockPos.containing(at.get(o).x(), at.get(o).y(), at.get(o).z());
                    // Only at the head, before there's a step to wait on, may two stand in one cell.
                    assertTrue(a.equals(stairs.head()) || !a.equals(b), "miners " + m + " and " + o + " both on " + a + " at tick " + tick);
                }
            }
        }
        assertTrue(allTrue(done), "every share cut");
        assertTrue(stairs.cuts().stream().allMatch(c -> c.cells().stream().noneMatch(cell -> world.isSolid(cell.x(), cell.y(), cell.z()))),
                "the whole mine cut; left: " + stairs.cuts().stream().filter(c -> c.cells().stream().anyMatch(cell -> world.isSolid(cell.x(), cell.y(), cell.z())))
                .map(c -> c.tunnel() + "@" + c.feet()).toList());
        for (int m = 0; m < miners; m++) {
            Set<Integer> share = stairs.share(m, miners);
            Set<BlockPos> theirs = new HashSet<>();
            stairs.cuts().stream().filter(c -> share.contains(c.tunnel())).forEach(c -> theirs.addAll(c.cells()));
            int miner = m;
            assertTrue(theirs.containsAll(cutBy.get(m)), "miner " + miner + " cut only its own tunnels");
            assertTrue(!cutBy.get(m).isEmpty(), "and every miner cut some");
        }
    }

    private static boolean allTrue(boolean[] flags) {
        for (boolean f : flags) {
            if (!f) {
                return false;
            }
        }
        return true;
    }

    /** A session like the shared one, that also notes each block it breaks in {@code cut}. */
    private PlaySession recording(Set<BlockPos> cut) {
        return (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(), new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
            if (method.getName().equals("finishDigging")) {
                cut.add(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
            }
            return method.invoke(session, args);
        });
    }

    /** A chest standing in the store's recess - the mine's stash - is left standing, not cut out again. */
    @Test
    void theStashsChestInTheStoreIsNeverCutOut() {
        Staircase stairs = new Staircase(HEAD, GROUND - 6);
        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000));
        BlockPos stash = stairs.store().getFirst();
        dug.remove(stash);
        chests.add(stash);

        assertInstanceOf(Miner.Progress.Done.class, dig(stairs, 60000));
        assertTrue(!dug.contains(stash), "the chest at " + stash + " stands");
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
