package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.Optional;
import java.util.Set;

/**
 * Digging out one block of a material: walk to a cell beside it, take the right thing in hand, dig
 * it for its break time, and wait for the drop to be picked up. The step {@link Woodcraft} and
 * {@link Gatherer} repeat; neither owns its timing. Ticked from the owning routine, which is
 * synchronized - this holds no lock of its own.
 *
 * <p>A dig is checked, not assumed: if the world model (kept current by block updates) still has
 * the block there afterwards, the dig failed. Soldiers working the same ground share a set of
 * {@code claims} - blocks and stand cells in use - so each picks a different one; a dig releases
 * its claims when it ends.
 */
final class Digger {

    /** How far a soldier looks for something to dig. */
    static final int SEARCH_RADIUS = 16;
    static final int PICKUP_TICKS = 20;
    static final int WALK_TIMEOUT_TICKS = 400;

    /**
     * What to dig, and how. Break times are the server's: hardness 2 for a log, 1.5 for stone;
     * progress per tick is tool speed / hardness / 30 when the tool is right for the block, so a log
     * by hand (speed 1) takes 60 ticks and stone with a wooden pickaxe (speed 2) 22.5 - each given a
     * margin, since the server times every dig and refuses an early finish.
     */
    enum Target {
        /** A log, bare-handed: a sword wears and digs no faster. Reached up to 3 blocks above the feet. */
        LOG(70, 3, null),
        /** Plain stone, with a pickaxe - by hand it drops nothing. At feet or head height. */
        STONE(35, 1, ItemClass.PICKAXE);

        final int digTicks;
        final int reachUp;
        /** What must be in hand, or null for an empty hand. */
        final ItemClass tool;

        Target(int digTicks, int reachUp, ItemClass tool) {
            this.digTicks = digTicks;
            this.reachUp = reachUp;
            this.tool = tool;
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

    private enum Step { WALKING, DIGGING, PICKING_UP }

    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;
    private final Spot spot;
    private Step step = Step.WALKING;
    private int ticks;

    /** Starts a dig at {@code spot}, claiming its block and stand cell until {@link #release}. */
    Digger(PlaySession session, Inventory inventory, Set<BlockPos> claims, Spot spot) {
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.spot = spot;
        claims.add(spot.block());
        claims.add(spot.stand());
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
        ticks++;
        BlockPos block = spot.block();
        switch (step) {
            case WALKING -> {
                if (!spot.target().at(world, block)) {
                    return new Progress.Failed("the " + spot.target() + " at " + block + " is gone");
                }
                if (at(soldier, spot.stand())) {
                    if (!takeInHand(spot.target().tool)) {
                        return new Progress.Failed("no " + spot.target().tool + " to dig " + spot.target() + " with");
                    }
                    session.startDigging(block.x(), block.y(), block.z());
                    next(Step.DIGGING);
                } else if (ticks > WALK_TIMEOUT_TICKS) {
                    return new Progress.Failed("couldn't reach the " + spot.target() + " at " + block);
                }
            }
            case DIGGING -> {
                if (ticks >= spot.target().digTicks) {
                    session.finishDigging(block.x(), block.y(), block.z());
                    next(Step.PICKING_UP);
                }
            }
            case PICKING_UP -> {
                if (ticks >= PICKUP_TICKS) {
                    return spot.target().at(world, block)
                            ? new Progress.Failed("the " + spot.target() + " at " + block + " didn't break")
                            : new Progress.Done();
                }
            }
        }
        return new Progress.Working();
    }

    /**
     * Holds {@code tool} from the hotbar (bringing one up from the main inventory if need be), or for
     * no tool an empty hand - an empty hotbar slot, or failing that anything but a weapon. False if
     * the tool isn't carried.
     */
    private boolean takeInHand(ItemClass tool) {
        int held = inventory.heldHotbarSlot();
        int heldItem = inventory.itemAt(Inventory.HOTBAR_FIRST + held);
        if (tool == null) {
            if (ItemClassRegistry.classOf(heldItem).filter(c -> c == ItemClass.WEAPON).isEmpty()) {
                return true;
            }
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
            if (choice >= 0) {
                hold(choice);
            }
            return true;
        }
        if (ItemClassRegistry.classOf(heldItem).filter(c -> c == tool).isPresent()) {
            return true;
        }
        int slot = inventory.slotOf(tool);
        if (slot < 0) {
            return false;
        }
        if (slot >= Inventory.HOTBAR_FIRST) {
            hold(slot - Inventory.HOTBAR_FIRST);
        } else {
            session.swapWithHotbar(0, 0, slot, held); // the server resends the inventory
        }
        return true;
    }

    private void hold(int hotbarSlot) {
        session.selectHotbarSlot(hotbarSlot);
        inventory.selectHotbar(hotbarSlot);
    }

    private void next(Step next) {
        step = next;
        ticks = 0;
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
