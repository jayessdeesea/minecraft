package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One soldier watering a cell with its bucket (operator, 2026-11-19: the buildup's farms, watered by bucket): it
 * fills the bucket at a pond, walks to the cell, and pours a water source into it. Each step is the server's to
 * confirm (docs/architecture.md § Behavior modeling): the bucket full when the inventory it resends says so, the
 * water there when its block update says so. A step whose answer doesn't come fails the run, which says why.
 *
 * <p>The water's taken only from a source with two sources beside it - a pond's, which fills again - never a lone
 * one, which a bucket empties for good.
 */
public final class WaterCarrier {

    private static final Logger LOG = LogManager.getLogger(WaterCarrier.class);

    /** How far from the cell to water it looks for a pond to fill at. */
    public static final int SOURCE_RADIUS = 32;
    static final int WALK_TICKS = 600;
    static final int ANSWER_TICKS = 40;
    /** A soldier's eyes over its feet. */
    private static final double EYES = 1.62;

    /** How a run stands: under way, the cell watered, or why it can't be. */
    public sealed interface Progress {
        record Working() implements Progress {
        }

        record Done() implements Progress {
        }

        record Failed(String why) implements Progress {
        }
    }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private Script script;
    private BlockPos target;
    private Position standAt;
    private Position soldier;

    public WaterCarrier(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Whether a run is under way. */
    public synchronized boolean active() {
        return script != null;
    }

    /** Where the soldier should be standing for the step under way: by the pond, or by the cell. */
    public synchronized Optional<Position> standAt() {
        return script == null ? Optional.empty() : Optional.ofNullable(standAt);
    }

    /** Stops the run under way; the next starts afresh, past what's already done. */
    public synchronized void stop() {
        script = null;
        target = null;
    }

    /** Each tick: waters {@code cell} - an open cell with ground under it - filling the bucket first if it's empty. */
    public synchronized Progress tick(Position soldier, WorldView world, BlockPos cell) {
        this.soldier = soldier;
        if (world.isWaterSource(cell.x(), cell.y(), cell.z())) {
            stop();
            return new Progress.Done();
        }
        if (script == null || !cell.equals(target)) {
            Optional<String> why = start(world, cell);
            if (why.isPresent()) {
                stop();
                return new Progress.Failed(why.get());
            }
        }
        return switch (script.tick()) {
            case Script.Status.Running _ -> new Progress.Working();
            case Script.Status.Done _ -> {
                stop();
                yield world.isWaterSource(cell.x(), cell.y(), cell.z()) ? new Progress.Done() : new Progress.Working();
            }
            case Script.Status.Interrupted _ -> {
                stop();
                yield new Progress.Working();
            }
            case Script.Status.Failed(String step, String reason) -> {
                stop();
                yield new Progress.Failed(step + ": " + reason);
            }
        };
    }

    /** The run for {@code cell}, or why there's none: no bucket, no pond to fill at, no ground to stand on. */
    private Optional<String> start(WorldView world, BlockPos cell) {
        boolean full = inventory.count(ItemClass.WATER_BUCKET) > 0;
        if (!full && inventory.count(ItemClass.BUCKET) == 0) {
            return Optional.of("no bucket carried");
        }
        Optional<BlockPos> pourFrom = beside(world, cell);
        if (pourFrom.isEmpty()) {
            return Optional.of("no ground beside " + cell + " to pour from");
        }
        Optional<BlockPos[]> fill = full ? Optional.empty() : pond(world, cell);
        if (!full && fill.isEmpty()) {
            return Optional.of("no pond within " + SOURCE_RADIUS + " blocks of " + cell + " to fill the bucket at");
        }
        target = cell;
        BlockPos pour = pourFrom.get();
        Script.Step[] fillSteps = fill.map(f -> new Step[]{
                walk(f[1], () -> inventory.count(ItemClass.WATER_BUCKET) > 0),
                inHand(ItemClass.BUCKET, () -> inventory.count(ItemClass.WATER_BUCKET) > 0),
                new Step("fill the bucket at " + f[0], () -> inventory.count(ItemClass.WATER_BUCKET) > 0,
                        () -> use(new Position(f[0].x() + 0.5, f[0].y() + 0.5, f[0].z() + 0.5)),
                        new Expect("a water bucket carried", () -> inventory.count(ItemClass.WATER_BUCKET) > 0), ANSWER_TICKS)
        }).orElse(new Step[0]);
        List<Step> steps = new java.util.ArrayList<>(List.of(fillSteps));
        steps.add(walk(pour, () -> world.isWaterSource(cell.x(), cell.y(), cell.z())));
        steps.add(inHand(ItemClass.WATER_BUCKET, () -> world.isWaterSource(cell.x(), cell.y(), cell.z())));
        steps.add(new Step("pour water at " + cell, () -> world.isWaterSource(cell.x(), cell.y(), cell.z()),
                () -> use(pourAt(soldier, cell)),
                new Expect("water at " + cell, () -> world.isWaterSource(cell.x(), cell.y(), cell.z())), ANSWER_TICKS));
        script = new Script(name, steps);
        LOG.info("{} waters {}{}", name, cell, fill.map(f -> ", the bucket filled at " + f[0]).orElse(""));
        return Optional.empty();
    }

    private Step walk(BlockPos stand, java.util.function.BooleanSupplier done) {
        Position at = new Position(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
        return new Step("walk to " + stand, done, () -> standAt = at,
                new Expect("standing at " + stand, () -> Math.hypot(soldier.x() - at.x(), soldier.z() - at.z()) < 0.6
                        && Math.abs(soldier.y() - at.y()) < 0.6), Math.max(WALK_TICKS, Digger.walkTicks(soldier, stand)));
    }

    private Step inHand(ItemClass item, java.util.function.BooleanSupplier done) {
        return new Step(item + " in hand", () -> done.getAsBoolean() || held(item), () -> take(item),
                new Expect(item + " in hand", () -> held(item)), ANSWER_TICKS);
    }

    /** Uses what's in hand looking from its eyes at {@code point}: the server casts its ray along that look. */
    private void use(Position point) {
        float[] look = look(soldier, point);
        session.useItem(look[0], look[1]);
    }

    /**
     * Where to look to pour into {@code cell} from {@code feet}: the far side of it, half way up - the server's ray
     * stops on the face of the ground beyond and the water goes in the cell before it. Not the ground under the
     * cell: from more than 1.3 blocks off, the ray to it meets the rim of the ground the soldier stands on first.
     */
    public static Position pourAt(Position feet, BlockPos cell) {
        double dx = cell.x() + 0.5 - feet.x(), dz = cell.z() + 0.5 - feet.z(), across = Math.max(1e-6, Math.hypot(dx, dz));
        return new Position(cell.x() + 0.5 + 0.45 * dx / across, cell.y() + 0.5, cell.z() + 0.5 + 0.45 * dz / across);
    }

    /** The yaw and pitch, in the server's degrees, that a soldier standing at {@code feet} looks at {@code point} with. */
    public static float[] look(Position feet, Position point) {
        double dx = point.x() - feet.x(), dz = point.z() - feet.z(), dy = point.y() - (feet.y() + EYES);
        return new float[]{(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }

    private boolean held(ItemClass item) {
        return ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot())).equals(Optional.of(item));
    }

    /** Holds {@code item}: from the hotbar, or swapped into a spare hotbar slot. */
    private void take(ItemClass item) {
        int slot = inventory.slotOf(item);
        if (slot < 0) {
            throw new IllegalStateException("no " + item + " carried - " + inventory.summary());
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : inventory.spareHotbarSlot();
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Where to stand to pour into {@code cell}: on the ground beside it, level with its top. */
    static Optional<BlockPos> beside(WorldView world, BlockPos cell) {
        return IntStream.range(0, SIDES.length).mapToObj(i -> cell.offset(SIDES[i][0], 1, SIDES[i][1]))
                .filter(s -> Pathfinder.standable(world, s)).findFirst();
    }

    /**
     * The pond nearest {@code cell} to fill a bucket at, and where to stand for it: a source with two sources beside
     * it, so it fills again, and ground on its bank to stand on.
     */
    static Optional<BlockPos[]> pond(WorldView world, BlockPos cell) {
        BlockPos[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -SOURCE_RADIUS; dx <= SOURCE_RADIUS; dx++) {
            for (int dz = -SOURCE_RADIUS; dz <= SOURCE_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos c = cell.offset(dx, dy, dz);
                    if (!world.isWaterSource(c.x(), c.y(), c.z()) || Math.hypot(dx, dz) >= bestDistance) {
                        continue;
                    }
                    long sources = IntStream.range(0, SIDES.length)
                            .filter(i -> world.isWaterSource(c.x() + SIDES[i][0], c.y(), c.z() + SIDES[i][1])).count();
                    Optional<BlockPos> bank = beside(world, c);
                    if (sources >= 2 && bank.isPresent()) {
                        best = new BlockPos[]{c, bank.get()};
                        bestDistance = Math.hypot(dx, dz);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
