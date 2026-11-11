package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * One soldier keeping its squad's wheat farm ({@link FarmPlan}): each tick it does the most
 * pressing job left, one cell at a time, in this order -
 * <ol>
 *   <li>harvest ripe wheat - wheat, and seeds to plant again;</li>
 *   <li>plant seeds on farmland with nothing growing;</li>
 *   <li>till the plot's ground into farmland, with a hoe;</li>
 *   <li>with no seeds and ground still to plant, break grass nearby for them - one in eight drops some.</li>
 * </ol>
 * The hoe, and bread from the wheat, are the armourer's to make; this tends the ground.
 *
 * <p>Each job is a short expect script (docs/architecture.md § Behavior modeling): the soldier stands on the
 * cell, the item is in hand when the server's resent inventory says so, and the job is done when
 * the server's block update says so - the ground is farmland, the seed has sprouted. Soldiers
 * working one farm share {@code claims}, so each takes a different cell. A job whose answer
 * doesn't come is a fault: it says why ({@link #failure}) and waits before the next.
 */
public final class Farmer {

    private static final Logger LOG = LogManager.getLogger(Farmer.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 200;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;

    private Digger digger;
    private Script fieldWork;
    private BlockPos claimed;
    private Position standAt;
    private Position soldier;
    private int cooldown;
    private String failure;

    public Farmer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this(name, session, inventory, claims, new Setbacks<>(PASS_OVER_TICKS, 16L * PASS_OVER_TICKS));
    }

    /**
     * As {@link #Farmer(String, PlaySession, Inventory, Set)}, with the cells squadmates working the
     * same farm couldn't work, {@code shared}: one out of one soldier's reach is passed over by all
     * of them - live, four soldiers each found the same unreachable wheat for themselves.
     */
    public Farmer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Setbacks<BlockPos> shared) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.shared = shared;
    }

    /** The cells any farmer working this farm couldn't work, for a while. */
    private final Setbacks<BlockPos> shared;

    /**
     * Whether {@code farm} has work in it: wheat ripe, farmland bare, or ground to till. A planted
     * plot growing has none - its farmers are free for other work until it ripens: live, a squad's
     * farm half stood by its growing wheat with the camp to keep.
     */
    public static boolean hasWork(WorldView world, FarmPlan farm) {
        return farm.plot().stream().anyMatch(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).orElse(-1) == Digger.RIPE
                || world.isFarmland(c.x(), c.y(), c.z()) && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty() && !world.isSolid(c.x(), c.y() + 1, c.z())
                || world.isTillable(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z()));
    }

    public synchronized boolean active() {
        return digger != null || fieldWork != null;
    }

    /** Where the soldier should be standing for the job under way. */
    public synchronized Optional<Position> standAt() {
        return digger != null ? Optional.of(digger.standAt()) : Optional.ofNullable(standAt);
    }

    /** Why the last job failed, while the soldier waits before the next. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Each tick: works {@code farm} while {@code free}; anything else stops the job under way. */
    /** How far a farmer looks for grass to break for seeds. */
    static final int SEEDS_RADIUS = 32;
    /** How long a plot cell whose job failed - not walked to, not answered - is first passed over; longer each time. */
    public static final int PASS_OVER_TICKS = 2400;
    private long ticks;
    private final Setbacks<BlockPos> passedOver = new Setbacks<>(PASS_OVER_TICKS, 16L * PASS_OVER_TICKS);

    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return passedOver.stuck().map(s -> "can't keep the farm: " + s);
    }

    public synchronized void tick(Position soldier, WorldView world, Optional<FarmPlan> farm, boolean free) {
        this.soldier = soldier;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (farm.isEmpty()) {
            passedOver.noWork();
        }
        if (!free || farm.isEmpty()) {
            if (active()) {
                // Said, so a job stopped over and over shows why: live, a harvest restarted every few seconds.
                LOG.info("{} leaves its farm work - {}", name, farm.isEmpty() ? "the farm is no longer its task" : "its hands are needed elsewhere");
            }
            stop();
            return;
        }
        if (digger != null) {
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> {
                    passedOver.worked(digger.spot().block().offset(0, -1, 0));
                    stopDigging();
                }
                // The crop gone before it got there is a squadmate's harvest, not a failure: live,
                // it was counted one, and soldiers reported CANTCO on a farm being worked.
                case Digger.Progress.Failed _ when !digger.spot().target().at(world, digger.spot().block()) -> stopDigging();
                case Digger.Progress.Failed(String why) -> fail(why);
            }
            return;
        }
        if (fieldWork != null) {
            switch (fieldWork.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _ -> {
                    if (claimed != null) {
                        passedOver.worked(claimed);
                    }
                    endFieldWork();
                }
                case Script.Status.Interrupted _ -> endFieldWork();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        next(world, farm.get());
    }

    /** Why the farm's cells aren't its next job: for a farmer stuck with work it doesn't start. */
    public synchronized String passedOver(WorldView world, FarmPlan farm) {
        List<BlockPos> plot = farm.plot();
        long claimed = plot.stream().filter(c -> claims.contains(c) || claims.contains(c.offset(0, 1, 0))).count();
        long barred = plot.stream().filter(c -> passedOver.barred(c, ticks)).count();
        long sharedBarred = plot.stream().filter(c -> shared.barred(c, ticks)).count();
        long tillable = plot.stream().filter(c -> world.isTillable(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z())).count();
        long farmland = plot.stream().filter(c -> world.isFarmland(c.x(), c.y(), c.z())).count();
        return plot.size() + " cells: " + claimed + " claimed, " + barred + " passed over, " + sharedBarred + " passed over by all, "
                + tillable + " to till, " + farmland + " farmland; " + inventory.count(ItemClass.SEEDS) + " seeds, "
                + inventory.count(ItemClass.HOE) + " hoes" + (cooldown > 0 ? ", cooling down" : "");
    }

    /** The most pressing job left on {@code farm}, started. */
    private void next(WorldView world, FarmPlan farm) {
        // A harvest claims the crop over its cell, not the cell: live, four farmers harvested one
        // ripe wheat in turn, each checking the ground cell a squadmate's harvest hadn't claimed.
        List<BlockPos> plot = farm.plot().stream().filter(c -> !claims.contains(c) && !claims.contains(c.offset(0, 1, 0))
                        && !passedOver.barred(c, ticks) && !shared.barred(c, ticks))
                .sorted(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z()))).toList();
        Optional<BlockPos> ripe = plot.stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).orElse(-1) == Digger.RIPE).findFirst();
        if (ripe.isPresent()) {
            BlockPos crop = ripe.get().offset(0, 1, 0);
            digger = new Digger(name, session, inventory, claims, new Digger.Spot(Digger.Target.WHEAT, crop, crop), Digger.walkTicks(soldier, crop));
            LOG.info("{} harvests the wheat at {}", name, crop);
            return;
        }
        Optional<BlockPos> bare = plot.stream().filter(c -> world.isFarmland(c.x(), c.y(), c.z())
                && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty() && !world.isSolid(c.x(), c.y() + 1, c.z())).findFirst();
        if (bare.isPresent() && inventory.count(ItemClass.SEEDS) > 0) {
            BlockPos cell = bare.get();
            start(cell, ItemClass.SEEDS, "plant seeds at " + cell, () -> world.wheatAge(cell.x(), cell.y() + 1, cell.z()).isPresent());
            return;
        }
        // Ground to till with room over it to stand in: a block put down on a plot cell - a pillar's,
        // a fill's - leaves no stand, and live, farmers sent to stand inside one reported no route.
        Optional<BlockPos> untilled = plot.stream().filter(c -> world.isTillable(c.x(), c.y(), c.z())
                && !world.isSolid(c.x(), c.y() + 1, c.z())).findFirst();
        if (untilled.isPresent() && inventory.count(ItemClass.HOE) > 0) {
            BlockPos cell = untilled.get();
            start(cell, ItemClass.HOE, "till " + cell, () -> world.isFarmland(cell.x(), cell.y(), cell.z()));
            return;
        }
        if (inventory.count(ItemClass.SEEDS) == 0 && (bare.isPresent() || untilled.isPresent())) {
            // Farther than a gatherer looks: a squad breaking grass for seeds soon clears what's near.
            Optional<Digger.Spot> grass = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims,
                    Digger.Target.GRASS, SEEDS_RADIUS);
            if (grass.isEmpty()) {
                fail("no seeds, and no grass within " + SEEDS_RADIUS + " blocks to find them in");
                return;
            }
            digger = new Digger(name, session, inventory, claims, grass.get());
        }
    }

    /** A job on {@code cell}: stand on it, {@code item} in hand, use it on the cell's top - done when {@code done}. */
    private void start(BlockPos cell, ItemClass item, String job, BooleanSupplier done) {
        claimed = cell;
        claims.add(cell);
        standAt = new Position(cell.x() + 0.5, cell.y() + 1, cell.z() + 0.5);
        fieldWork = new Script(name, List.of(
                new Step("walk to " + cell, () -> {
                }, new Expect("standing on " + cell, () -> on(cell)), Math.max(WALK_TICKS, Digger.walkTicks(soldier, cell))),
                new Step(item + " in hand", () -> inHand(item), () -> takeInHand(item),
                        new Expect(item + " in hand", () -> inHand(item)), ANSWER_TICKS),
                new Step(job, done, () -> {
                    if (item == ItemClass.HOE) {
                        // A hoe tills only under open air: grass or a flower on the cell is broken
                        // first - a touch breaks it, an empty cell ignores the dig, and the server
                        // takes both in the order sent. Live, every till was refused for it.
                        session.startDigging(cell.x(), cell.y() + 1, cell.z());
                        session.finishDigging(cell.x(), cell.y() + 1, cell.z());
                    }
                    session.useBlockFace(cell.x(), cell.y(), cell.z(), BlockFace.UP);
                }, new Expect(job + " done", done), ANSWER_TICKS)));
    }

    private boolean on(BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6 && Math.abs(soldier.y() - (cell.y() + 1)) < 0.6;
    }

    private boolean inHand(ItemClass item) {
        return ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot())).equals(Optional.of(item));
    }

    /** Holds {@code item}: from the hotbar, or swapped into a hotbar slot holding no weapon. */
    private void takeInHand(ItemClass item) {
        int slot = inventory.slotOf(item);
        if (slot < 0) {
            throw new IllegalStateException("no " + item + " carried - " + inventory.summary());
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : spareHotbarSlot();
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }

    private int spareHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON && held.get() != ItemClass.PICKAXE) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon or a tool");
    }

    private void fail(String why) {
        // Its cell out of reach - across water, behind a wall - or its job refused: the rest first.
        // A harvest is a dig, its spot the crop over the plot cell: live, with no cell passed over
        // for it, four soldiers walked at one ripe wheat they couldn't reach all day.
        BlockPos cell = digger != null ? digger.spot().block().offset(0, -1, 0) : claimed;
        if (cell != null) {
            passedOver.failed(cell, ticks, why);
            shared.failed(cell, ticks);
        }
        LOG.warn("{} stops farming for a while - {}", name, why);
        failure = why;
        cooldown = COOLDOWN_TICKS;
        stop();
    }

    private void endFieldWork() {
        fieldWork = null;
        standAt = null;
        if (claimed != null) {
            claims.remove(claimed);
            claimed = null;
        }
    }

    private void stopDigging() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    /** Stops the job under way, its claims given up. */
    public synchronized void stop() {
        stopDigging();
        endFieldWork();
    }
}
