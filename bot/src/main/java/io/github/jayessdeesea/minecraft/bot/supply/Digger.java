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
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Digging out one block of a material: walk to a cell beside it, take the right thing in hand, dig
 * it for its break time, and pick up what drops. The step {@link Woodcraft} and {@link Gatherer}
 * repeat; neither owns its timing. Ticked from the owning routine, which is synchronized - this
 * holds no lock of its own.
 *
 * <p>An expect script (AGENTS.md § Behavior modeling), one per block: the tool is in hand when the
 * server's resent inventory says so, the block is broken when the server's block update says so,
 * and the drop is in when the inventory's count of it rises. Soldiers working the same ground share
 * a set of {@code claims} - blocks and stand cells in use - so each picks a different one; a dig
 * releases its claims when it ends.
 */
final class Digger {

    /** How far a soldier looks for something to dig. */
    static final int SEARCH_RADIUS = 16;
    static final int WALK_TIMEOUT_TICKS = 400;
    /** For the server to answer: the inventory resent, a block update. */
    static final int ANSWER_TICKS = 40;
    /** A dropped block's pickup delay (10 ticks), with a margin for it to fall and the update to arrive. */
    static final int PICKUP_TICKS = 40;

    /**
     * What to dig, and how. Break times are the server's: hardness 2 for a log, 1.5 for stone;
     * progress per tick is tool speed / hardness / 30 when the tool is right for the block, so a log
     * by hand (speed 1) takes 60 ticks and stone with a wooden pickaxe (speed 2) 22.5 - each given a
     * margin, since the server times every dig and refuses an early finish.
     */
    enum Target {
        /** A log, bare-handed: a sword wears and digs no faster. Reached up to 3 blocks above the feet. */
        LOG(70, 3, null, ItemClass.LOG),
        /** Plain stone, with a pickaxe - by hand it drops nothing. At feet or head height. */
        STONE(35, 1, ItemClass.PICKAXE, ItemClass.COBBLESTONE);

        final int digTicks;
        final int reachUp;
        /** What must be in hand, or null for an empty hand. */
        final ItemClass tool;
        /** What it drops. */
        final ItemClass drop;

        Target(int digTicks, int reachUp, ItemClass tool, ItemClass drop) {
            this.digTicks = digTicks;
            this.reachUp = reachUp;
            this.tool = tool;
            this.drop = drop;
        }

        boolean at(WorldView world, BlockPos block) {
            return switch (this) {
                case LOG -> world.isLog(block.x(), block.y(), block.z());
                case STONE -> world.isStone(block.x(), block.y(), block.z());
            };
        }
    }

    /** Where to stand, and the block to dig from there. */
    record Spot(Target target, BlockPos stand, BlockPos block) {
    }

    /** How a dig is going. */
    sealed interface Progress {
        record Working() implements Progress {
        }

        record Done() implements Progress {
        }

        record Failed(String why) implements Progress {
        }
    }

    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;
    private final Spot spot;
    private final Script script;
    private Position soldierAt;
    private WorldView world;
    private int ticks;
    private int digStarted;
    private int dropsBefore;

    /** Starts a dig at {@code spot}, claiming its block and stand cell until {@link #release}. */
    Digger(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Spot spot) {
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.spot = spot;
        claims.add(spot.block());
        claims.add(spot.stand());
        Target target = spot.target();
        BlockPos block = spot.block();
        this.script = new Script(name, List.of(
                new Step("walk to " + spot.stand(), () -> {
                }, new Expect("standing beside the " + target, () -> {
                    if (!target.at(world, block)) {
                        throw new IllegalStateException("the " + target + " at " + block + " is gone");
                    }
                    return at(soldierAt, spot.stand());
                }), WALK_TIMEOUT_TICKS),
                new Step("take " + (target.tool == null ? "an empty hand" : target.tool) + " in hand", this::inHand, this::takeInHand,
                        new Expect((target.tool == null ? "an empty hand" : target.tool) + " in hand", this::inHand), ANSWER_TICKS),
                // The server times the dig itself: finishing early is refused, so wait out its break time.
                new Step("dig the " + target + " at " + block, () -> {
                    digStarted = ticks;
                    dropsBefore = inventory.count(target.drop);
                    session.startDigging(block.x(), block.y(), block.z());
                }, new Expect("the break time to pass", () -> ticks - digStarted >= target.digTicks), target.digTicks + 1),
                new Step("break the " + target, () -> session.finishDigging(block.x(), block.y(), block.z()),
                        new Expect("the " + target + " at " + block + " broken", () -> !target.at(world, block)), ANSWER_TICKS),
                new Step("pick up the " + target.drop, () -> {
                }, new Expect("more " + target.drop + " carried", () -> inventory.count(target.drop) > dropsBefore), PICKUP_TICKS)));
    }

    /** Gives up this dig's claims - call once it's done, failed, or abandoned. */
    void release() {
        claims.remove(spot.block());
        claims.remove(spot.stand());
    }

    Spot spot() {
        return spot;
    }

    /** Where the soldier should stand while digging. */
    Position standAt() {
        return new Position(spot.stand().x() + 0.5, spot.stand().y(), spot.stand().z() + 0.5);
    }

    Progress tick(Position soldier, WorldView world) {
        this.soldierAt = soldier;
        this.world = world;
        ticks++;
        return switch (script.tick()) {
            case Script.Status.Running _ -> new Progress.Working();
            case Script.Status.Done _ -> new Progress.Done();
            case Script.Status.Failed(String step, String reason) -> new Progress.Failed(step + ": " + reason);
            case Script.Status.Interrupted(String step, String guard) -> new Progress.Failed(step + ": " + guard);
        };
    }

    private boolean inHand() {
        Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()));
        ItemClass tool = spot.target().tool;
        return tool == null ? held.filter(c -> c == ItemClass.WEAPON).isEmpty() : held.equals(Optional.of(tool));
    }

    /**
     * Holds the target's tool from the hotbar (bringing one up from the main inventory if need be),
     * or for no tool an empty hand - an empty hotbar slot, or failing that anything but a weapon.
     */
    private void takeInHand() {
        ItemClass tool = spot.target().tool;
        if (tool == null) {
            int choice = -1;
            for (int slot = 0; slot < 9; slot++) {
                int item = inventory.itemAt(Inventory.HOTBAR_FIRST + slot);
                if (item < 0) {
                    choice = slot;
                    break;
                }
                if (choice < 0 && ItemClassRegistry.classOf(item).filter(c -> c == ItemClass.WEAPON).isEmpty()) {
                    choice = slot;
                }
            }
            if (choice < 0) {
                throw new IllegalStateException("every hotbar slot holds a weapon");
            }
            hold(choice);
            return;
        }
        int slot = inventory.slotOf(tool);
        if (slot < 0) {
            throw new IllegalStateException("no " + tool + " to dig " + spot.target() + " with");
        }
        if (slot >= Inventory.HOTBAR_FIRST) {
            hold(slot - Inventory.HOTBAR_FIRST);
        } else {
            session.swapWithHotbar(0, 0, slot, inventory.heldHotbarSlot()); // stale state: the server resends the inventory
        }
    }

    private void hold(int hotbarSlot) {
        session.selectHotbarSlot(hotbarSlot);
        inventory.selectHotbar(hotbarSlot);
    }

    /**
     * The nearest block of {@code target} within {@link #SEARCH_RADIUS} of {@code around} that a
     * soldier can dig: one beside a cell it can stand in (solid below, room for its body), no higher
     * above that cell's floor than the target's reach, and not {@code claimed}. Nearest by the stand
     * cell's distance from {@code around}.
     */
    static Optional<Spot> findSpot(WorldView world, BlockPos around, Set<BlockPos> claimed, Target target) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos stand = around.offset(dx, dy, dz);
                    if (claimed.contains(stand) || !standable(world, stand)) {
                        continue;
                    }
                    for (int[] side : sides) {
                        for (int up = 0; up <= target.reachUp; up++) {
                            BlockPos block = stand.offset(side[0], up, side[1]);
                            if (!claimed.contains(block) && target.at(world, block)) {
                                double distance = Math.hypot(dx, dz) + Math.abs(dy) + up * 0.1;
                                if (distance < bestDistance) {
                                    best = new Spot(target, stand, block);
                                    bestDistance = distance;
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean standable(WorldView world, BlockPos cell) {
        return world.isSolid(cell.x(), cell.y() - 1, cell.z()) && !world.isSolid(cell.x(), cell.y(), cell.z())
                && !world.isSolid(cell.x(), cell.y() + 1, cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z());
    }

    private static boolean at(Position soldier, BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6
                && Math.abs(soldier.y() - cell.y()) < 0.6;
    }
}
