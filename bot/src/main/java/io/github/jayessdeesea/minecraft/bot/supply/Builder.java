package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier building: filling a plan of cells with blocks it carries - cobblestone first, then
 * planks, then logs, which it would rather keep for tools. Each cell is placed against a solid
 * neighbour, from within reach and clear of the soldier's own body, and checked against the server's
 * block updates; a cell that won't take a block after a few tries is skipped. What to build is
 * decided elsewhere; this only lays it. Driven by the soldier's tick, like {@link Handover}.
 *
 * <p>The material goes into a hotbar slot holding no weapon or tool, so the soldier stays armed;
 * the slot it held before is selected again when the building stops.
 */
public final class Builder {

    private static final Logger LOG = LogManager.getLogger(Builder.class);

    /** Building material, in the order it's spent. */
    static final List<ItemClass> MATERIALS = List.of(ItemClass.COBBLESTONE, ItemClass.PLANKS, ItemClass.LOG);
    /** Eyes to the centre of the cell: inside the server's 4.5-block interaction range. */
    static final double REACH = 4.0;
    static final double EYE_HEIGHT = 1.62;
    static final double HALF_WIDTH = 0.3;
    static final int SETTLE_TICKS = 6;
    static final int ATTEMPTS = 3;

    /** How a tick went. */
    public sealed interface Progress {
        /** Building, or on the way to. */
        record Working() implements Progress {
        }

        /** Every cell of the plan is solid. */
        record Done() implements Progress {
        }

        /** Can't go on: {@code why}. */
        record Failed(String why) implements Progress {
        }
    }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private final Set<BlockPos> skipped = new HashSet<>();
    private BlockPos target;
    private int attempts;
    private int ticksSinceAction = SETTLE_TICKS;
    private int previousHotbar = -1;
    private Position standAt;
    private Position lookAt;
    private boolean done;

    public Builder(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Whether the last tick found every cell of the plan solid. */
    public synchronized boolean done() {
        return done;
    }

    /** Where the soldier should stand to reach the cell it's working on, when it isn't within reach. */
    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    /** What to face this tick: the face being built against. */
    public synchronized Optional<Position> lookAt() {
        return Optional.ofNullable(lookAt);
    }

    /** One tick of building {@code plan}, in order, moving about as needed to reach each cell. */
    public synchronized Progress tick(Position soldier, WorldView world, List<BlockPos> plan) {
        return tick(soldier, world, plan, false);
    }

    /**
     * One tick of building {@code plan} from where the soldier stands - inside a hut it's walling in,
     * where walking off to reach a cell would leave it outside. A cell out of reach is skipped.
     */
    public synchronized Progress tickInPlace(Position soldier, WorldView world, List<BlockPos> plan) {
        return tick(soldier, world, plan, true);
    }

    private Progress tick(Position soldier, WorldView world, List<BlockPos> plan, boolean inPlace) {
        ticksSinceAction++;
        lookAt = null;
        done = false;
        if (ticksSinceAction < SETTLE_TICKS) {
            return new Progress.Working(); // the last swap or placement is still being answered
        }
        if (target != null && solid(world, target)) {
            target = null;
            attempts = 0;
        }
        if (target != null && attempts >= ATTEMPTS) {
            LOG.warn("{} couldn't place a block at {} - skipping it", name, target);
            skipped.add(target);
            target = null;
            attempts = 0;
        }
        List<BlockPos> remaining = plan.stream().filter(cell -> !solid(world, cell) && !skipped.contains(cell)).toList();
        if (remaining.isEmpty()) {
            stop();
            done = skipped.isEmpty();
            return done ? new Progress.Done() : new Progress.Failed(skipped.size() + " cell(s) wouldn't take a block");
        }
        if (target == null) {
            target = remaining.stream().filter(cell -> support(world, cell).isPresent()).findFirst().orElse(null);
            if (target == null) {
                stop();
                return new Progress.Failed("nothing to build against for the " + remaining.size() + " cell(s) left");
            }
        }
        int material = MATERIALS.stream().mapToInt(inventory::slotOf).filter(slot -> slot >= 0).findFirst().orElse(-1);
        if (material < 0) {
            stop();
            return new Progress.Failed("no blocks to build with");
        }
        if (!withinReach(soldier, target) || overlaps(soldier, target)) {
            standAt = inPlace ? null : standingSpot(world, soldier, target).orElse(null);
            if (standAt == null) {
                skipped.add(target);
                target = null;
            }
            return new Progress.Working();
        }
        standAt = null;
        int hand = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        if (!MATERIALS.stream().anyMatch(c -> ItemClassRegistry.classOf(inventory.itemAt(hand)).equals(Optional.of(c)))) {
            int hotbar = freeHotbarSlot();
            if (previousHotbar < 0) {
                previousHotbar = inventory.heldHotbarSlot();
            }
            if (material != Inventory.HOTBAR_FIRST + hotbar) {
                session.swapWithHotbar(0, 0, material, hotbar);
            }
            session.selectHotbarSlot(hotbar);
            inventory.selectHotbar(hotbar);
            ticksSinceAction = 0;
            return new Progress.Working();
        }
        BlockFace face = support(world, target).orElseThrow();
        BlockPos against = target.offset(face.dx, face.dy, face.dz);
        BlockFace toward = face.opposite();
        // DeterministicSoldier.lookAt aims at chest height above the position it's given.
        lookAt = new Position(against.x() + 0.5 + toward.dx * 0.5, against.y() + 0.5 + toward.dy * 0.5 - 1.2,
                against.z() + 0.5 + toward.dz * 0.5);
        if (attempts > 0) {
            // A block that didn't land: something not solid but not replaceable either - a flower,
            // a sapling - fills the cell. Those break at a touch; an empty cell ignores the dig.
            session.startDigging(target.x(), target.y(), target.z());
            session.finishDigging(target.x(), target.y(), target.z());
        }
        session.useBlockFace(against.x(), against.y(), against.z(), toward);
        attempts++;
        ticksSinceAction = 0;
        return new Progress.Working();
    }

    /** Tries again every cell given up on - the ground changed (a breach found), so a cell that wouldn't take a block may now. */
    public synchronized void retrySkipped() {
        skipped.clear();
    }

    /** Stops building: the weapon or tool held before goes back in hand. */
    public synchronized void stop() {
        standAt = null;
        target = null;
        attempts = 0;
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
    }

    /** The side of {@code cell} with a solid block to build against, if any - below first. */
    static Optional<BlockFace> support(WorldView world, BlockPos cell) {
        for (BlockFace face : List.of(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP)) {
            if (world.isSolid(cell.x() + face.dx, cell.y() + face.dy, cell.z() + face.dz)) {
                return Optional.of(face);
            }
        }
        return Optional.empty();
    }

    /** A hotbar slot holding no weapon or tool - the last such, out of the way. */
    private int freeHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || MATERIALS.contains(held.get())) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon or a tool");
    }

    private static boolean solid(WorldView world, BlockPos cell) {
        return world.isSolid(cell.x(), cell.y(), cell.z());
    }

    static boolean withinReach(Position soldier, BlockPos cell) {
        double dx = cell.x() + 0.5 - soldier.x();
        double dy = cell.y() + 0.5 - (soldier.y() + EYE_HEIGHT);
        double dz = cell.z() + 0.5 - soldier.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= REACH;
    }

    /** Whether the soldier's body - 0.6 wide, two cells tall - is in {@code cell}. */
    static boolean overlaps(Position soldier, BlockPos cell) {
        boolean horizontal = soldier.x() + HALF_WIDTH > cell.x() && soldier.x() - HALF_WIDTH < cell.x() + 1
                && soldier.z() + HALF_WIDTH > cell.z() && soldier.z() - HALF_WIDTH < cell.z() + 1;
        int feet = (int) Math.floor(soldier.y());
        return horizontal && (cell.y() == feet || cell.y() == feet + 1);
    }

    /** The nearest cell within reach of {@code cell} that a soldier can stand in without being in it. */
    private static Optional<Position> standingSpot(WorldView world, Position soldier, BlockPos cell) {
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -3; dy <= 1; dy++) {
                    int x = cell.x() + dx;
                    int y = cell.y() + dy;
                    int z = cell.z() + dz;
                    Position spot = new Position(x + 0.5, y, z + 0.5);
                    if (!world.isSolid(x, y - 1, z) || world.isSolid(x, y, z) || world.isSolid(x, y + 1, z) || world.isHazard(x, y, z)
                            || !withinReach(spot, cell) || overlaps(spot, cell)) {
                        continue;
                    }
                    double distance = Math.hypot(spot.x() - soldier.x(), spot.z() - soldier.z()) + Math.abs(spot.y() - soldier.y());
                    if (distance < bestDistance) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
