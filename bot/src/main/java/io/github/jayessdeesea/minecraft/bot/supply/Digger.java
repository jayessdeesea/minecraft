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
 * it for its break time, and pick up what drops. The step {@link Armourer} and {@link Gatherer}
 * repeat; neither owns its timing. Ticked from the owning routine, which is synchronized - this
 * holds no lock of its own.
 *
 * <p>An expect script (docs/architecture.md § Behavior modeling), one per block: the tool is in hand when the
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
    /**
     * A dropped block's pickup delay (10 ticks), with a margin for it to fall, for the soldier to walk
     * onto it where it landed, and for the update to arrive.
     */
    static final int PICKUP_TICKS = 100;
    /** How far from the dug block a drop is looked for: one knocked off a trunk lands a block or two out. */
    static final double DROP_RADIUS = 4;

    /**
     * What to dig, and how. Break times are the server's: hardness 2 for a log, 1.5 for stone, 3
     * for iron ore and 4.5 in deepslate; progress per tick is tool speed / hardness / 30 when the
     * tool is right for the block, so a log by hand (speed 1) takes 60 ticks, stone with a wooden
     * pickaxe (speed 2) 22.5 and deepslate iron ore with a stone one (speed 4) 34 - each given a
     * margin, since the server times every dig and refuses an early finish.
     */
    enum Target {
        /**
         * A log, with the best axe carried - or bare-handed, since a sword wears and digs no faster.
         * Reached up to 3 blocks above the feet.
         */
        LOG(70, 3, null, 0, ItemClass.LOG),
        /** Plain stone, with a pickaxe - by hand it drops nothing. At feet or head height. */
        STONE(35, 1, ItemClass.PICKAXE, 0, ItemClass.COBBLESTONE),
        /** Iron ore, with a stone pickaxe or better - a wooden one breaks it for nothing. At feet or head height. */
        IRON_ORE(45, 1, ItemClass.PICKAXE, 1, ItemClass.RAW_IRON),
        /** Copper ore, with a stone pickaxe or better: raw copper - armour where there's no iron. At feet or head height. */
        COPPER_ORE(45, 1, ItemClass.PICKAXE, 1, ItemClass.RAW_COPPER),
        /** Short or tall grass, by hand at a touch: wheat seeds, one time in eight. */
        GRASS(3, 1, null, 0, ItemClass.SEEDS, false),
        /** Ripe wheat, by hand at a touch: wheat, and seeds to plant again. */
        WHEAT(3, 1, null, 0, ItemClass.WHEAT),
        /** Gravel, by hand in about a second (hardness 0.6): flint one time in ten, for arrows. At feet or head height. */
        GRAVEL(25, 1, null, 0, ItemClass.FLINT, false),
        /** Dirt or grass at the feet or the ground beside them, by hand in about a second: dirt, for a patrol base's walls. */
        SOIL(25, 0, null, 0, ItemClass.DIRT),
        /** Spruce leaves - the pine the camp prefers - by hand: a spruce sapling, one time in twenty. */
        SPRUCE_LEAVES(10, 3, null, 0, ItemClass.SAPLING, false),
        /** Any tree's leaves, by hand: its sapling, one time in twenty (oak, one in twenty too, and an apple). */
        LEAVES(10, 3, null, 0, ItemClass.SAPLING, false);

        final int digTicks;
        final int reachUp;
        /** What must be in hand, or null for an empty hand. */
        final ItemClass tool;
        /** The pickaxe's least mining tier (ItemClassRegistry.miningTier) that gets the drop. */
        final int tier;
        /** What it drops. */
        final ItemClass drop;
        /** Whether it always drops something - grass mostly drops nothing, and that's a dig done, not a fault. */
        final boolean dropsAlways;

        Target(int digTicks, int reachUp, ItemClass tool, int tier, ItemClass drop) {
            this(digTicks, reachUp, tool, tier, drop, true);
        }

        Target(int digTicks, int reachUp, ItemClass tool, int tier, ItemClass drop, boolean dropsAlways) {
            this.digTicks = digTicks;
            this.reachUp = reachUp;
            this.tool = tool;
            this.tier = tier;
            this.drop = drop;
            this.dropsAlways = dropsAlways;
        }

        boolean at(WorldView world, BlockPos block) {
            return switch (this) {
                case LOG -> world.isLog(block.x(), block.y(), block.z());
                case STONE -> world.isStone(block.x(), block.y(), block.z());
                case IRON_ORE -> world.isIronOre(block.x(), block.y(), block.z());
                case GRAVEL -> world.isGravel(block.x(), block.y(), block.z());
                case COPPER_ORE -> world.oreOf(block.x(), block.y(), block.z()).filter("copper"::equals).isPresent();
                case GRASS -> world.isGrassPlant(block.x(), block.y(), block.z());
                case WHEAT -> world.wheatAge(block.x(), block.y(), block.z()).orElse(-1) == RIPE;
                case SOIL -> world.isSoil(block.x(), block.y(), block.z()) && !world.isFarmland(block.x(), block.y(), block.z())
                        && !world.isSolid(block.x(), block.y() + 1, block.z());
                case SPRUCE_LEAVES -> world.leaves(block.x(), block.y(), block.z()).filter("spruce"::equals).isPresent();
                case LEAVES -> world.leaves(block.x(), block.y(), block.z()).isPresent();
            };
        }

        /** What digs this for its drop, if the soldier's tools can: its best pickaxe meets the tier. */
        boolean canDig(Inventory inventory) {
            return tool == null || bestTool(inventory) >= 0 && ItemClassRegistry.miningTier(inventory.itemAt(bestTool(inventory)))
                    .orElse(-1) >= tier;
        }

        /** The inventory slot of the fastest axe, or -1. */
        static int bestAxe(Inventory inventory) {
            int best = -1;
            double bestSpeed = 0;
            for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
                double speed = ItemClassRegistry.axeSpeed(inventory.itemAt(slot)).orElse(0);
                if (speed > bestSpeed) {
                    best = slot;
                    bestSpeed = speed;
                }
            }
            return best;
        }

        /**
         * Ticks to dig this with what's carried: a log by hand is 60 ticks, with an axe 60 / its
         * speed - given the same margin as the bare-hand time, since the server refuses an early finish.
         */
        int digTicks(Inventory inventory) {
            int axe = this == LOG ? bestAxe(inventory) : -1;
            if (axe < 0) {
                return digTicks;
            }
            return (int) Math.ceil(digTicks / ItemClassRegistry.axeSpeed(inventory.itemAt(axe)).getAsDouble());
        }

        /** The inventory slot of the best pickaxe, or -1. */
        static int bestTool(Inventory inventory) {
            int best = -1;
            int bestTier = -1;
            for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
                int tier = ItemClassRegistry.miningTier(inventory.itemAt(slot)).orElse(-1);
                if (tier > bestTier) {
                    best = slot;
                    bestTier = tier;
                }
            }
            return best;
        }
    }

    /** Wheat's last growth stage. */
    static final int RIPE = 7;
    /** For a drop that may not come: a broken block's drop shows within a few ticks if there is one. */
    static final int MAYBE_DROP_TICKS = 15;

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
    /** The break time of the dig under way, for what's in hand. */
    private int digTicks;
    private int dropsBefore;
    private boolean pickingUp;
    private int pickupStarted;
    /** Whether the drop has been seen on the ground since the block broke. */
    private boolean dropSeen;

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
                    digTicks = target.digTicks(inventory);
                    dropsBefore = inventory.count(target.drop);
                    session.startDigging(block.x(), block.y(), block.z());
                }, new Expect("the break time to pass", () -> ticks - digStarted >= digTicks), target.digTicks + 1),
                new Step("break the " + target, () -> session.finishDigging(block.x(), block.y(), block.z()),
                        new Expect("the " + target + " at " + block + " broken", () -> !target.at(world, block)), ANSWER_TICKS),
                // Done once the drop is off the ground: in the pack, or in a squadmate's, which
                // working the same trees often gets there first. Never seen at all is the fault.
                new Step("pick up the " + target.drop, () -> {
                    pickingUp = true;
                    pickupStarted = ticks;
                }, new Expect("the " + target.drop + " picked up", () -> {
                    if (inventory.count(target.drop) > dropsBefore) {
                        return true;
                    }
                    boolean onGround = !dropsNear().isEmpty();
                    dropSeen |= onGround;
                    return dropSeen && !onGround || !target.dropsAlways && !dropSeen && ticks - pickupStarted > MAYBE_DROP_TICKS;
                }), PICKUP_TICKS)));
    }

    /** Gives up this dig's claims - call once it's done, failed, or abandoned. */
    void release() {
        claims.remove(spot.block());
        claims.remove(spot.stand());
    }

    Spot spot() {
        return spot;
    }

    /** Where the soldier should stand: beside the block while digging, then on the drop wherever it landed. */
    Position standAt() {
        Position stand = new Position(spot.stand().x() + 0.5, spot.stand().y(), spot.stand().z() + 0.5);
        if (!pickingUp) {
            return stand;
        }
        return dropsNear().stream().findFirst().orElse(stand);
    }

    /** Dropped items around the dug block, nearest first. */
    private List<Position> dropsNear() {
        BlockPos block = spot.block();
        return world.itemsNear(new Position(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5), DROP_RADIUS);
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
        int item = inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot());
        Optional<ItemClass> held = ItemClassRegistry.classOf(item);
        ItemClass tool = spot.target().tool;
        int axe = spot.target() == Target.LOG ? Target.bestAxe(inventory) : -1;
        if (axe >= 0) {
            return item == inventory.itemAt(axe);
        }
        return tool == null ? held.filter(c -> c == ItemClass.WEAPON).isEmpty()
                : held.equals(Optional.of(tool)) && ItemClassRegistry.miningTier(item).orElse(-1) >= spot.target().tier;
    }

    /**
     * Holds the target's tool from the hotbar (bringing one up from the main inventory if need be),
     * or for no tool an empty hand - an empty hotbar slot, or failing that anything but a weapon.
     */
    private void takeInHand() {
        ItemClass tool = spot.target().tool;
        int axe = spot.target() == Target.LOG ? Target.bestAxe(inventory) : -1;
        if (axe >= 0) {
            if (axe >= Inventory.HOTBAR_FIRST) {
                hold(axe - Inventory.HOTBAR_FIRST);
            } else {
                session.swapWithHotbar(0, 0, axe, inventory.heldHotbarSlot());
            }
            return;
        }
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
        int slot = Target.bestTool(inventory);
        if (!spot.target().canDig(inventory)) {
            throw new IllegalStateException("no " + tool + " good enough to dig " + spot.target() + " with - " + inventory.summary());
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
        return findSpot(world, around, claimed, target, SEARCH_RADIUS);
    }

    /** As {@link #findSpot(WorldView, BlockPos, Set, Target)}, out to {@code radius} blocks across. */
    static Optional<Spot> findSpot(WorldView world, BlockPos around, Set<BlockPos> claimed, Target target, int radius) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos stand = around.offset(dx, dy, dz);
                    if (claimed.contains(stand) || !standable(world, stand)) {
                        continue;
                    }
                    for (int[] side : sides) {
                        // Soil also from the ground beside its feet: on flat ground there's none at
                        // feet height, and that's where a pit starts.
                        for (int up = target == Target.SOIL ? -1 : 0; up <= target.reachUp; up++) {
                            BlockPos block = stand.offset(side[0], up, side[1]);
                            if (!claimed.contains(block) && target.at(world, block) && !letsInWater(world, block)) {
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

    static boolean letsInWater(WorldView world, BlockPos block) {
        return io.github.jayessdeesea.minecraft.bot.engineer.Clearing.letsInWater(world, block);
    }

    static boolean standable(WorldView world, BlockPos cell) {
        return world.isSolid(cell.x(), cell.y() - 1, cell.z()) && !world.isSolid(cell.x(), cell.y(), cell.z())
                && !world.isSolid(cell.x(), cell.y() + 1, cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z());
    }

    private static boolean at(Position soldier, BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6
                && Math.abs(soldier.y() - cell.y()) < 0.6;
    }
}
