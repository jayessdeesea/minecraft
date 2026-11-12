package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * One soldier building its squad's {@link Perimeter}: it takes the nearest open cell of the wall no
 * squadmate has taken, stands just inside it, and puts a block there - cobblestone first, then dirt;
 * logs and planks are kept for tools. An expect script: done when the server's block update shows
 * the cell solid. Soldiers on one wall share {@code claims}.
 */
public final class Mason {

    private static final Logger LOG = LogManager.getLogger(Mason.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 100;
    /** What a wall is built of, in the order it's used. */
    static final List<ItemClass> MATERIALS = List.of(ItemClass.COBBLESTONE, ItemClass.DIRT);

    private final String name;
    private final Inventory inventory;
    private final Footing footing;
    private final Set<BlockPos> claims;

    private Script job;
    private BlockPos claimed;
    /**
     * Where it stands for {@link #claimed}, claimed with it: never a wall cell, so the one set holds
     * both. In the squad simulation two masons sent to one corner stand pushed each other off it.
     */
    private BlockPos claimedStand;
    private Position standAt;
    private Position soldier;
    private int cooldown;
    /** Ticks this mason has run, and the cells that failed - their place not walked to, or their block not taken. */
    private long ticks;
    /** How long a cell that failed is first passed over; longer each time it fails again. */
    static final int PASS_OVER_TICKS = 2400;
    private final Setbacks<BlockPos> passedOver = new Setbacks<>(PASS_OVER_TICKS, 16L * PASS_OVER_TICKS);
    /**
     * Stands it couldn't get to, kept from for longer than their cells are passed over: back to a
     * cell once the rest of the wall's done, it stands somewhere else. Live, the camp's wall's last
     * two cells each had a first stand down an old shaft or in a sealed pocket, and were passed over
     * for a day with others within reach.
     */
    private final Setbacks<BlockPos> standsFailed = new Setbacks<>(4L * PASS_OVER_TICKS, 64L * PASS_OVER_TICKS);
    private String failure;
    /** Why the last look found no cell to build, while there were cells open; cleared to look again. */
    private String stalled;
    /** The last such reason logged: said once each time it changes. */
    private String said;

    public Mason(String name, Inventory inventory, Footing footing, Set<BlockPos> claims) {
        this.name = name;
        this.inventory = inventory;
        this.footing = footing;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return job != null;
    }

    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return passedOver.stuck().map(s -> "can't build the wall: " + s)
                .or(() -> job == null && cooldown == 0 ? Optional.ofNullable(stalled).map(s -> "can't build the wall: " + s) : Optional.empty());
    }

    /** Blocks carried to build with. */
    public static int blocks(Inventory inventory) {
        return MATERIALS.stream().mapToInt(inventory::count).sum();
    }

    /** Each tick: builds the {@code open} cells of the wall {@code radius} out round {@code centre} while {@code free}. */
    public synchronized void tick(Position soldier, WorldView world, BlockPos centre, int radius, List<BlockPos> open, boolean free) {
        this.soldier = soldier;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (open.isEmpty()) {
            passedOver.noWork();
        }
        if (open.isEmpty() || !free && !paused()) {
            stop();
            return;
        }
        if (!free) {
            return;
        }
        pausedTicks = 0;
        if (job != null) {
            switch (job.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _ -> {
                    passedOver.worked(claimed);
                    end();
                }
                case Script.Status.Interrupted _ -> end();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        if (cooldown > 0 || blocks(inventory) == 0) {
            return;
        }
        // Any cell with a block to go against - the one under it, or for the lip the wall beside it -
        // so each column rises from its own ground: on a slope the wall's rows are at many heights.
        // The nearest with somewhere inside to stand: one without it waits, rather than holding up the rest.
        Optional<Map.Entry<BlockPos, BlockPos>> next = open.stream()
                .filter(c -> !claims.contains(c) && !passedOver.barred(c, ticks) && Builder.support(world, c).isPresent())
                .sorted(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())))
                .flatMap(cell -> inside(world, cell, centre, radius, soldier, reached(world)).filter(stand -> !claims.contains(stand))
                        .map(stand -> Map.entry(cell, stand)).stream())
                .findFirst();
        if (next.isPresent()) {
            stalled = null;
            said = null;
            start(world, next.get().getKey(), next.get().getValue());
            return;
        }
        // Blocks carried and cells open, and none to build: say which way each is stuck, once.
        long claimed = open.stream().filter(claims::contains).count();
        long passed = open.stream().filter(c -> !claims.contains(c) && passedOver.barred(c, ticks)).count();
        List<BlockPos> unsupported = open.stream().filter(c -> !claims.contains(c) && !passedOver.barred(c, ticks)
                && Builder.support(world, c).isEmpty()).toList();
        List<BlockPos> noStand = open.stream().filter(c -> !claims.contains(c) && !passedOver.barred(c, ticks)
                && Builder.support(world, c).isPresent() && inside(world, c, centre, radius, soldier, this::standOpen).isEmpty()).toList();
        String why = open.size() + " wall cells open and none to build: " + claimed + " taken by squadmates, " + passed
                + " passed over after a failure, " + unsupported.size() + " with nothing to go against"
                + unsupported.stream().limit(3).map(BlockPos::toString).collect(Collectors.joining(", ", " (", ")"))
                + ", " + noStand.size() + " with nowhere inside to stand"
                + noStand.stream().limit(3).map(BlockPos::toString).collect(Collectors.joining(", ", " (", ")"));
        if (!why.equals(said)) {
            LOG.warn("{} {}", name, why);
            said = why;
        }
        stalled = why;
    }

    /** Whether the job under way is replacing dirt, not building. */
    public synchronized boolean repointing() {
        return job != null && repointJob;
    }

    /** Whether its last look at the wall found cells open and none it could build: time for other work. */
    public synchronized boolean nothingToBuild() {
        return job == null && stalled != null;
    }

    /**
     * Each tick, with no wall cell it can build: replaces the wall's {@code soft} cells - dirt or grass,
     * which an enderman picks up and opens a hole with (backlog/building-guide.md) - with
     * cobblestone, one at a time while {@code free} and carrying some: dug out, then built again.
     */
    public synchronized void repoint(Position soldier, WorldView world, BlockPos centre, int radius, List<BlockPos> soft, boolean free) {
        this.soldier = soldier;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (soft.isEmpty()) {
            passedOver.noWork();
        }
        if (soft.isEmpty() || !free && !paused()) {
            stop();
            return;
        }
        if (!free) {
            return;
        }
        pausedTicks = 0;
        if (job != null) {
            switch (job.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _ -> {
                    passedOver.worked(claimed);
                    end();
                }
                case Script.Status.Interrupted _ -> end();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        // Between jobs it looks at the wall again: a cell that couldn't be built may be buildable now.
        stalled = null;
        if (cooldown > 0 || inventory.count(ItemClass.COBBLESTONE) == 0) {
            return;
        }
        soft.stream().filter(c -> !claims.contains(c) && !passedOver.barred(c, ticks) && world.isSoil(c.x(), c.y(), c.z()))
                .sorted(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())))
                .flatMap(cell -> (onWall(cell, centre, radius) ? inside(world, cell, centre, radius, soldier, reached(world))
                        : beside(world, cell, soldier, reached(world))).filter(stand -> !claims.contains(stand))
                        .map(stand -> Map.entry(cell, stand)).stream())
                .findFirst().ifPresent(next -> startRepoint(world, next.getKey(), next.getValue()));
    }

    /** Whether {@code cell} is on the wall's line, {@code radius} out from {@code centre}: off it, it's the floor's. */
    static boolean onWall(BlockPos cell, BlockPos centre, int radius) {
        return Math.max(Math.abs(cell.x() - centre.x()), Math.abs(cell.z() - centre.z())) == radius;
    }

    /** Break time for dirt by hand, hardness 0.5 - about 15 ticks - with the margin the server's dig timing wants. */
    static final int DIRT_DIG_TICKS = 25;

    private boolean repointJob;

    private void startRepoint(WorldView world, BlockPos cell, BlockPos stand) {
        repointJob = true;
        claimed = cell;
        claims.add(cell);
        claimedStand = stand;
        claims.add(stand);
        standAt = new Position(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
        long[] digStarted = {0};
        job = new Script(name, List.of(
                new Step("walk to " + stand, () -> {
                }, new Expect("standing in " + stand, () -> on(stand)), WALK_TICKS),
                new Step("dig the dirt at " + cell, () -> {
                    digStarted[0] = ticks;
                    footing.startDigging(cell);
                }, new Expect("the break time to pass", () -> ticks - digStarted[0] >= DIRT_DIG_TICKS), DIRT_DIG_TICKS + 2),
                new Step("break the dirt at " + cell, () -> footing.finishDigging(cell),
                        new Expect("the dirt at " + cell + " gone", () -> !world.isSolid(cell.x(), cell.y(), cell.z())), ANSWER_TICKS),
                new Step("stone at " + cell, () -> {
                    if (!footing.place(cell, ItemClass.COBBLESTONE, Builder.support(world, cell).orElseThrow(
                            () -> new IllegalStateException("nothing beside " + cell + " to put a block against")))) {
                        throw new IllegalStateException("no COBBLESTONE carried - " + inventory.summary());
                    }
                }, new Expect("cobblestone at " + cell, () -> world.isSolid(cell.x(), cell.y(), cell.z())
                        && !world.isSoil(cell.x(), cell.y(), cell.z())), ANSWER_TICKS)));
        LOG.info("{} replaces the dirt at {} with cobblestone", name, cell);
    }

    /**
     * Where to stand to build {@code cell}: the column nearest it just inside the wall {@code radius}
     * out - beside a wall cell, two in from the lip, never on the wall's top - at a standing height
     * near the cell's. Where the ground inside has been dug away, the column just outside, under the
     * lip. A soldier outside the wall line - back from a dirt run off base - builds from outside
     * first: in the squad simulation, one sent for a stand inside had no way in and walked nowhere.
     */
    static Optional<BlockPos> inside(WorldView world, BlockPos cell, BlockPos centre, int radius, Position soldier) {
        return inside(world, cell, centre, radius, soldier, stand -> true);
    }

    /** How far a route to a stand is looked for before the stand counts as out of reach. */
    static final int STAND_ROUTE_BUDGET = 20_000;

    /**
     * Whether a stand is one to try: not lately one it couldn't get to, and one a route reaches from
     * where the soldier stands - or it's barred now, not after a walk at it. Live, the wall's last
     * dirt block had its stand in a pocket sealed in the hill, and every mason walked 400 ticks at it.
     */
    private Predicate<BlockPos> reached(WorldView world) {
        return stand -> {
            if (!standOpen(stand)) {
                return false;
            }
            BlockPos from = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
            // As the soldier gets about: pillaring and building steps on the blocks it carries, and
            // cutting steps with a pickaxe if it has one - a wall going up is crossed that way.
            int blocks = inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT);
            boolean pickaxe = inventory.count(ItemClass.PICKAXE) > 0;
            if (Pathfinder.find(world, from, new Goal(stand.x() + 0.5, stand.y(), stand.z() + 0.5,
                    ON_PLACE, 0.5), STAND_ROUTE_BUDGET, FallAllowance.NONE, blocks, pickaxe).reachesGoal()) {
                return true;
            }
            standsFailed.failed(stand, ticks, "no route to " + stand);
            return false;
        };
    }

    /** Whether {@code stand} hasn't lately been one it couldn't get to. */
    private boolean standOpen(BlockPos stand) {
        return !standsFailed.barred(stand, ticks);
    }

    /** As {@link #inside(WorldView, BlockPos, BlockPos, int, Position)}, only among stands {@code allowed}. */
    static Optional<BlockPos> inside(WorldView world, BlockPos cell, BlockPos centre, int radius, Position soldier,
                                     Predicate<BlockPos> allowed) {
        boolean out = Math.max(Math.abs(soldier.x() - (centre.x() + 0.5)), Math.abs(soldier.z() - (centre.z() + 0.5))) > radius + 0.5;
        // Straight in or out from the cell first; then a column along the wall either side, still in
        // reach: live, a cell's column in and out were both built over, and the wall stood three
        // blocks short of closing for a day.
        boolean onZFace = Math.abs(cell.z() - centre.z()) == radius;
        for (int along : new int[]{0, 1, -1}) {
            for (int ring : out ? new int[]{radius + 1, radius - 1} : new int[]{radius - 1, radius + 1}) {
                Optional<BlockPos> stand = standIn(world, cell, centre, ring, onZFace ? along : 0, onZFace ? 0 : along, allowed);
                if (stand.isPresent()) {
                    return stand;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A stand beside a floor cell off the wall's line - one of the four next to it a block up, on
     * solid ground - nearest {@code soldier} first: the floor is paved from the floor.
     */
    static Optional<BlockPos> beside(WorldView world, BlockPos cell, Position soldier, Predicate<BlockPos> allowed) {
        return java.util.stream.Stream.of(cell.offset(1, 1, 0), cell.offset(-1, 1, 0), cell.offset(0, 1, 1), cell.offset(0, 1, -1))
                .filter(stand -> Pathfinder.standable(world, stand) && allowed.test(stand))
                .min(Comparator.comparingDouble(s -> Math.hypot(s.x() + 0.5 - soldier.x(), s.z() + 0.5 - soldier.z())));
    }

    /** A stand in the column {@code ring} out from {@code centre} toward {@code cell}, shifted {@code (sx, sz)} along the wall. */
    private static Optional<BlockPos> standIn(WorldView world, BlockPos cell, BlockPos centre, int ring, int sx, int sz,
                                              Predicate<BlockPos> allowed) {
        int x = centre.x() + Math.clamp(cell.x() - centre.x(), -ring, ring) + sx;
        int z = centre.z() + Math.clamp(cell.z() - centre.z(), -ring, ring) + sz;
        // Up to three above the cell, as well as below: where the wall steps down a slope and the
        // camp inside it is level, the ground inside stands higher - live, five wall cells with
        // "nowhere inside to stand" held a wall open with a level camp a block or two above them.
        for (int dy : new int[]{1, 0, -1, -2, 2, -3, 3}) { // nearest the cell's height first
            BlockPos stand = new BlockPos(x, cell.y() + dy, z);
            if (Pathfinder.standable(world, stand) && !stand.equals(cell) && !stand.equals(cell.offset(0, -1, 0)) && allowed.test(stand)) {
                return Optional.of(stand);
            }
        }
        return Optional.empty();
    }

    private void start(WorldView world, BlockPos cell, BlockPos stand) {
        repointJob = false;
        claimed = cell;
        claims.add(cell);
        claimedStand = stand;
        claims.add(stand);
        standAt = new Position(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
        ItemClass material = MATERIALS.stream().filter(m -> inventory.count(m) > 0).findFirst().orElseThrow();
        job = new Script(name, List.of(
                new Step("walk to " + stand, () -> {
                }, new Expect("standing in " + stand, () -> on(stand)), WALK_TICKS),
                new Step("wall at " + cell, () -> world.isSolid(cell.x(), cell.y(), cell.z()), () -> {
                    // A torch put down on the wall's line when the camp was lit holds the cell.
                    if (!footing.placeClearing(cell, material, Builder.support(world, cell).orElseThrow(
                            () -> new IllegalStateException("nothing beside " + cell + " to put a block against")))) {
                        throw new IllegalStateException("no " + material + " carried - " + inventory.summary());
                    }
                }, new Expect("a block at " + cell, () -> world.isSolid(cell.x(), cell.y(), cell.z())), ANSWER_TICKS)));
    }

    /** Within this of its place's centre: a 0.6-wide body clear of the wall cell beside it. */
    static final double ON_PLACE = 0.2;

    private boolean on(BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < ON_PLACE && Math.abs(soldier.y() - cell.y()) < 0.6;
    }

    private void fail(String why) {
        if (claimed != null) {
            // Its place out of reach - inside an old hut, across water - or a block the server won't
            // take there: the rest of the wall first. And a place out of reach isn't tried again
            // when the cell comes back round.
            passedOver.failed(claimed, ticks, why);
            if (claimedStand != null && why.startsWith("walk to ")) {
                standsFailed.failed(claimedStand, ticks, why);
            }
        }
        LOG.warn("{} stops walling for a while - {}", name, why);
        failure = why;
        cooldown = COOLDOWN_TICKS;
        stop();
    }

    private void end() {
        job = null;
        standAt = null;
        if (claimed != null) {
            claims.remove(claimed);
            claimed = null;
        }
        if (claimedStand != null) {
            claims.remove(claimedStand);
            claimedStand = null;
        }
    }

    /** How long a job holds its place while the soldier's hands are taken - a hostile near, a meal. */
    static final int PAUSE_TICKS = 200;

    private int pausedTicks;

    /**
     * Whether a job under way holds its place this tick, its hands taken: for a while, not for good.
     * Dropped at once, live, a mason with a hostile hovering at the edge of reach started one cell
     * again at every free tick, 342 times, and the dirt's break time never ran out.
     */
    private boolean paused() {
        return job != null && ++pausedTicks <= PAUSE_TICKS;
    }

    public synchronized void stop() {
        end();
    }
}
