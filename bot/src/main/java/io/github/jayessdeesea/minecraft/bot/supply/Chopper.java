package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.Optional;
import java.util.Set;

/**
 * Chopping one log: walk to a cell beside it, put any weapon away (digging wears a sword, and a
 * sword digs badly), dig the log for its break time, and wait for the drop to be picked up. The step
 * {@link Woodcraft} and {@link Gatherer} both repeat; neither owns its timing. Ticked from the owning
 * routine, which is synchronized - this holds no lock of its own.
 *
 * <p>A chop is checked, not assumed: if the world model (kept current by block updates) still has
 * the log there after the dig, the chop failed. Soldiers working the same woods share a set of
 * {@code claims} - logs and stand cells in use - so each picks a different tree, or a different
 * side of it; a chop releases its claims when it ends.
 */
final class Chopper {

    /** How far a soldier looks for a tree. */
    static final int SEARCH_RADIUS = 16;
    /** Logs from the stand cell's floor up to this many blocks above it are within reach. */
    static final int REACH_UP = 3;
    /** A log by hand: hardness 2, so 3 seconds - with a margin, since the server times it. */
    static final int DIG_TICKS = 70;
    static final int PICKUP_TICKS = 20;
    static final int WALK_TIMEOUT_TICKS = 400;

    /** Where to stand, and the log to chop from there. */
    record Tree(BlockPos stand, BlockPos log) {
    }

    /** How a chop is going. */
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
    private final Tree tree;
    private Step step = Step.WALKING;
    private int ticks;

    /** Starts a chop of {@code tree}, claiming its log and stand cell until {@link #release}. */
    Chopper(PlaySession session, Inventory inventory, Set<BlockPos> claims, Tree tree) {
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.tree = tree;
        claims.add(tree.log());
        claims.add(tree.stand());
    }

    /** Gives up this chop's claims - call once it's done, failed, or abandoned. */
    void release() {
        claims.remove(tree.log());
        claims.remove(tree.stand());
    }

    Tree tree() {
        return tree;
    }

    /** Where the soldier should stand while chopping. */
    Position standAt() {
        return new Position(tree.stand().x() + 0.5, tree.stand().y(), tree.stand().z() + 0.5);
    }

    Progress tick(Position soldier, WorldView world) {
        ticks++;
        switch (step) {
            case WALKING -> {
                if (!world.isLog(tree.log().x(), tree.log().y(), tree.log().z())) {
                    return new Progress.Failed("the log at " + tree.log() + " is gone");
                }
                if (at(soldier, tree.stand())) {
                    stowWeapon();
                    session.startDigging(tree.log().x(), tree.log().y(), tree.log().z());
                    next(Step.DIGGING);
                } else if (ticks > WALK_TIMEOUT_TICKS) {
                    return new Progress.Failed("couldn't reach the tree at " + tree.log());
                }
            }
            case DIGGING -> {
                if (ticks >= DIG_TICKS) {
                    session.finishDigging(tree.log().x(), tree.log().y(), tree.log().z());
                    next(Step.PICKING_UP);
                }
            }
            case PICKING_UP -> {
                if (ticks >= PICKUP_TICKS) {
                    return world.isLog(tree.log().x(), tree.log().y(), tree.log().z())
                            ? new Progress.Failed("the log at " + tree.log() + " didn't break")
                            : new Progress.Done();
                }
            }
        }
        return new Progress.Working();
    }

    /** Digs bare-handed: holds an empty hotbar slot (or failing that, anything not a weapon). */
    private void stowWeapon() {
        int held = inventory.heldHotbarSlot();
        if (ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + held)).isEmpty()
                || ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + held))
                .filter(c -> c == ItemClassRegistry.ItemClass.WEAPON).isEmpty()) {
            return;
        }
        int choice = -1;
        for (int slot = 0; slot < 9; slot++) {
            int item = inventory.itemAt(Inventory.HOTBAR_FIRST + slot);
            if (item < 0) {
                choice = slot;
                break;
            }
            if (choice < 0 && ItemClassRegistry.classOf(item).filter(c -> c == ItemClassRegistry.ItemClass.WEAPON).isEmpty()) {
                choice = slot;
            }
        }
        if (choice >= 0) {
            session.selectHotbarSlot(choice);
            inventory.selectHotbar(choice);
        }
    }

    private void next(Step next) {
        step = next;
        ticks = 0;
    }

    /**
     * The nearest log within {@link #SEARCH_RADIUS} of {@code around} that a soldier can chop: one
     * beside a cell it can stand in (solid below, room for its body), at most {@link #REACH_UP}
     * above that cell's floor. Nearest by the stand cell's distance from {@code around}.
     */
    static Optional<Tree> findTree(WorldView world, BlockPos around, Set<BlockPos> claimed) {
        Tree best = null;
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
                        for (int up = 0; up <= REACH_UP; up++) {
                            BlockPos log = stand.offset(side[0], up, side[1]);
                            if (!claimed.contains(log) && world.isLog(log.x(), log.y(), log.z())) {
                                double distance = Math.hypot(dx, dz) + Math.abs(dy) + up * 0.1;
                                if (distance < bestDistance) {
                                    best = new Tree(stand, log);
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
