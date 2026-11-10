package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
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
 * neighbour, from within reach and clear of the soldier's own body; a cell that won't take a block
 * after a few tries is skipped. A cell someone stands in - a squadmate, a mob, a player watching -
 * is waited on, not tried: the server won't place a block into a body, and the body moves on. What to build is decided elsewhere; this only lays it. Driven by the
 * soldier's tick, like {@link Handover}.
 *
 * <p>A loop of short expect scripts, one per cell (docs/architecture.md § Behavior modeling): the material is
 * in hand when the server's resent inventory says so, and a block is in when the server's block
 * update says so. A placement with no block update by {@link #PLACE_TICKS} is a try that didn't
 * land; a material swap that's never answered stops the building, loudly.
 *
 * <p>The material goes into a hotbar slot holding no weapon or tool, so the soldier stays armed;
 * the slot it held before is selected again when the building stops.
 */
public final class Builder {

    private static final Logger LOG = LogManager.getLogger(Builder.class);

    /** Building material, in the order it's spent. */
    static final List<ItemClass> MATERIALS = List.of(ItemClass.COBBLESTONE, ItemClass.DIRT, ItemClass.PLANKS, ItemClass.LOG);
    /** Eyes to the centre of the cell: inside the server's 4.5-block interaction range. */
    static final double REACH = 4.0;
    static final double EYE_HEIGHT = 1.62;
    static final double HALF_WIDTH = 0.3;
    /** For the server to resend the inventory after a swap. */
    static final int ANSWER_TICKS = 40;
    /**
     * For a placement's block update: the server handles it on its next tick and sends the update
     * the same tick, so a block not in after half a second didn't land.
     */
    static final int PLACE_TICKS = 10;
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
    private Script script;
    private WorldView world;
    private int previousHotbar = -1;
    private Position standAt;
    /** Ticks spent walking to reach {@link #target}: a stand it never reaches skips the cell. */
    private int walking;
    /** How long a walk to a stand may take before the cell it's for is given up. */
    static final int WALK_TICKS = 400;
    private Position lookAt;
    private boolean done;

    /** What it builds with, in the order it's spent. */
    private final List<ItemClass> materials;

    /** A builder of walls and steps: with {@link #MATERIALS}. */
    public Builder(String name, PlaySession session, Inventory inventory) {
        this(name, session, inventory, MATERIALS);
    }

    /** A builder with {@code materials} only: a workshop's fixture, say, built from its one item. */
    public Builder(String name, PlaySession session, Inventory inventory, List<ItemClass> materials) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.materials = List.copyOf(materials);
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
        this.world = world;
        lookAt = null;
        done = false;
        if (script != null) {
            switch (script.tick()) {
                case Script.Status.Running _, Script.Status.Interrupted _ -> {
                    return new Progress.Working();
                }
                case Script.Status.Done _ -> script = null; // on to the next cell
                case Script.Status.Failed(String step, String reason) -> {
                    script = null;
                    if (step.equals(MATERIAL_IN_HAND)) {
                        stop();
                        return new Progress.Failed("couldn't take a block in hand: " + reason);
                    }
                    // A try that didn't land: counted, and tried again next tick, up to ATTEMPTS.
                }
            }
        }
        if (target != null && solid(world, target)) {
            target = null;
            attempts = 0;
            walking = 0;
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
        int material = materials.stream().mapToInt(inventory::slotOf).filter(slot -> slot >= 0).findFirst().orElse(-1);
        if (material < 0) {
            stop();
            return new Progress.Failed("no blocks to build with");
        }
        if (!withinReach(soldier, target) || overlaps(soldier, target)) {
            standAt = inPlace ? null : standingSpot(world, soldier, target).orElse(null);
            // A stand the soldier never gets to - no route there - gives the cell up, as one with no
            // stand does: live in the squad simulation, a filler stood still for good walking nowhere.
            if (standAt != null && ++walking > WALK_TICKS) {
                LOG.info("{} couldn't get to {} to build {} - skipping it", name, standAt, target);
                standAt = null;
            }
            if (standAt == null) {
                skipped.add(target);
                target = null;
                walking = 0;
            }
            return new Progress.Working();
        }
        standAt = null;
        walking = 0;
        if (occupied(world, soldier, target)) {
            if (!waitingOn) {
                LOG.info("{} waits for whoever is standing at {} to move", name, target);
                waitingOn = true;
            }
            return new Progress.Working();
        }
        waitingOn = false;
        script = place(target);
        script.tick();
        return new Progress.Working();
    }

    /** Whether it's waiting on a body in the cell it's building. */
    private boolean waitingOn;

    /** Whether a body other than the builder's own overlaps {@code cell}. */
    private static boolean occupied(WorldView world, Position soldier, BlockPos cell) {
        return world.bodiesNear(new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5), 2).stream()
                .filter(b -> Math.abs(b.x() - soldier.x()) > 1e-6 || Math.abs(b.y() - soldier.y()) > 1e-6 || Math.abs(b.z() - soldier.z()) > 1e-6)
                .anyMatch(b -> b.x() + HALF_WIDTH > cell.x() && b.x() - HALF_WIDTH < cell.x() + 1
                        && b.z() + HALF_WIDTH > cell.z() && b.z() - HALF_WIDTH < cell.z() + 1
                        && b.y() < cell.y() + 1 && b.y() + 1.8 > cell.y());
    }

    private static final String MATERIAL_IN_HAND = "a block in hand";

    /** Material in hand, then one try at putting a block in {@code cell}. */
    private Script place(BlockPos cell) {
        return new Script(name, List.of(
                new Step(MATERIAL_IN_HAND, this::materialInHand, () -> {
                    int material = materials.stream().mapToInt(inventory::slotOf).filter(slot -> slot >= 0).findFirst()
                            .orElseThrow(() -> new IllegalStateException("no blocks to build with"));
                    int hotbar = freeHotbarSlot();
                    if (previousHotbar < 0) {
                        previousHotbar = inventory.heldHotbarSlot();
                    }
                    if (material != Inventory.HOTBAR_FIRST + hotbar) {
                        session.swapWithHotbar(0, 0, material, hotbar); // stale state: the server resends the inventory
                    }
                    session.selectHotbarSlot(hotbar);
                    inventory.selectHotbar(hotbar);
                }, new Expect("building material in hand", this::materialInHand), ANSWER_TICKS),
                new Step("place a block at " + cell, () -> {
                    BlockFace face = support(world, cell).orElseThrow(() -> new IllegalStateException("nothing to build against"));
                    BlockPos against = cell.offset(face.dx, face.dy, face.dz);
                    BlockFace toward = face.opposite();
                    // DeterministicSoldier.lookAt aims at chest height above the position it's given.
                    lookAt = new Position(against.x() + 0.5 + toward.dx * 0.5, against.y() + 0.5 + toward.dy * 0.5 - 1.2,
                            against.z() + 0.5 + toward.dz * 0.5);
                    if (attempts > 0) {
                        // A block that didn't land: something not solid but not replaceable either - a
                        // flower, a sapling - fills the cell. Those break at a touch; an empty cell
                        // ignores the dig.
                        session.startDigging(cell.x(), cell.y(), cell.z());
                        session.finishDigging(cell.x(), cell.y(), cell.z());
                    }
                    session.useBlockFace(against.x(), against.y(), against.z(), toward);
                    attempts++;
                }, new Expect("the block at " + cell + " in", () -> solid(world, cell), PLACE_TICKS), PLACE_TICKS)));
    }

    private boolean materialInHand() {
        Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()));
        return held.isPresent() && materials.contains(held.get());
    }

    /** Tries again every cell given up on - the ground changed (a breach found), so a cell that wouldn't take a block may now. */
    public synchronized void retrySkipped() {
        skipped.clear();
    }

    /** Stops building: the weapon or tool held before goes back in hand. */
    public synchronized void stop() {
        standAt = null;
        script = null;
        target = null;
        attempts = 0;
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
    }

    /**
     * The side of {@code cell} with a solid block to build against, if any - below first. Not a
     * crafting table or furnace: clicking one opens it, and no block goes in.
     */
    public static Optional<BlockFace> support(WorldView world, BlockPos cell) {
        for (BlockFace face : List.of(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP)) {
            int x = cell.x() + face.dx, y = cell.y() + face.dy, z = cell.z() + face.dz;
            if (world.isSolid(x, y, z) && world.workstation(x, y, z).isEmpty()) {
                return Optional.of(face);
            }
        }
        return Optional.empty();
    }

    /** A hotbar slot holding no weapon or tool - the last such, out of the way. */
    private int freeHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || materials.contains(held.get())) {
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
