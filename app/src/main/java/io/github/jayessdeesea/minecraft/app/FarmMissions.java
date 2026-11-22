package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Clearer;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.FlattenPlan;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Slopes;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * The operator's farm missions carried out by doctrine (operator, 2026-11-21: a farm at a chunk, at any height, sea level by
 * default): each {@link FarmMission}'s three steps written to the objective register as PLANNED when it's given; one squad
 * put on the step under way - a guardrail over the day's work, after the platoon leader's and the day's own - and the next
 * started when the world measures this one done: the chunk and its slopes cut to a block below the farm's level
 * ({@link FlattenPlan#cut} over the world's tops), every cell of that layer cobblestone, the farm template on it past
 * SURVEYED ({@link FarmSite}). Each step's result kept in the chunk register (FLATTENED, FLOORED; the template's WHEAT is
 * the chunk ledger's to keep as it grows). Measured, never asked of a model. One mission worked at a time, in the order
 * given; a mission given again while open is the one already under way.
 */
final class FarmMissions {

    private static final Logger LOG = LogManager.getLogger(FarmMissions.class);

    /** Each open mission, in the order given, and the step under way: the first is the one worked. */
    private final Map<FarmMission, Integer> missions = new LinkedHashMap<>();
    /** Wakes the platoon leader: a mission given, a step done. */
    private final Consumer<String> wake;
    private volatile Optional<ObjectiveBook> book = Optional.empty();
    private boolean loaded;
    /** The chunks the force has made a template of, as last measured: a flatten slopes off none of them, nor digs into any. */
    private volatile Set<Long> made = Set.of();
    /** What was last said of the step under way, so each change is said once. */
    private String said = "";

    FarmMissions(Consumer<String> wake) {
        this.wake = wake;
    }

    /** Keeps the missions' steps in {@code book}'s register: those open in it - before a restart - carried on. */
    void keptIn(ObjectiveBook book) {
        this.book = Optional.of(book);
    }

    /** {@code mission} given at {@code now}: its steps PLANNED in the register, the first worked once those before it are done. */
    synchronized void give(FarmMission mission, long now) {
        load();
        // The latest order for a chunk is the one worked: one at another level, still open, relieved - live (field668), a
        // mission given at the wrong level had no other way to be put right.
        for (FarmMission old : missions.keySet().stream().filter(m -> !m.equals(mission) && m.chunkX() == mission.chunkX() && m.chunkZ() == mission.chunkZ()).toList()) {
            missions.remove(old);
            book.ifPresent(b -> b.relieved(old, "superseded by " + mission.name(), now));
            LOG.info("[Mission] {} superseded by {}", old.name(), mission.name());
        }
        if (missions.containsKey(mission)) {
            LOG.info("[Mission] {} is open already, at step {}", mission.name(), missions.get(mission) + 1);
            return;
        }
        missions.put(mission, 0);
        book.ifPresent(b -> b.planned(mission, now));
        LOG.info("[Mission] {}: {} - PLANNED", mission.name(), mission.steps().stream().map(FarmMission.Step::task).toList());
        wake.accept("the operator's farm");
    }

    /**
     * The mission open on chunk ({@code chunkX}, {@code chunkZ}) cancelled at {@code now}, {@code why} kept in its phases'
     * rows: those not done SUPERSEDED - never deleted - so it isn't carried on after a restart; the platoon leader woken to
     * give its squad another task. Empty, and nothing done, if none is open there.
     */
    synchronized Optional<FarmMission> cancel(int chunkX, int chunkZ, String why, long now) {
        load();
        Optional<FarmMission> open = missions.keySet().stream().filter(m -> m.chunkX() == chunkX && m.chunkZ() == chunkZ).findFirst();
        open.ifPresent(m -> {
            missions.remove(m);
            book.ifPresent(b -> b.relieved(m, why, now));
            LOG.info("[Mission] {} cancelled - {}", m.name(), why);
            wake.accept("the operator's farm cancelled");
        });
        return open;
    }

    /** The mission worked now and its step under way, if any is open: those before a restart once the first measure has read them. */
    synchronized Optional<Map.Entry<FarmMission, FarmMission.Step>> current() {
        return missions.entrySet().stream().findFirst().map(e -> Map.entry(e.getKey(), e.getKey().steps().get(e.getValue())));
    }

    /** The objective a squad on the step under way is given: the step as said down the chain, at the chunk, its task. */
    Optional<Objective> objective() {
        return current().map(c -> {
            FarmMission m = c.getKey();
            FarmMission.Step step = c.getValue();
            Objective.Task task = switch (step.kind()) {
                case FLATTEN -> new Objective.Task.Keep();
                case FLOOR -> new Objective.Task.Floor(m.layer(step.level()));
                case FARM_TEMPLATE -> new Objective.Task.Farm();
            };
            return new Objective(m.said(step), m.centre(step), "The operator's farm, step " + (step.index() + 1) + " of "
                    + m.steps().size() + ": " + step.task(), task);
        });
    }

    /** The ground to flatten while a mission's flatten is under way: its chunk, down to a block below the farm's level. */
    Optional<Flatten> flatten() {
        return current().filter(c -> c.getValue().kind() == FarmMission.Kind.FLATTEN)
                .map(c -> new Flatten(c.getKey().minX(), c.getKey().minZ(), c.getKey().minX() + 15, c.getKey().minZ() + 15, c.getValue().level()));
    }

    /**
     * The columns {@code ground}'s flatten works, if it's a mission's: its chunk and its slopes ({@link FlattenPlan#reach}),
     * sloped on each side with no template beside it, never into a template's ground.
     */
    Optional<List<FlattenPlan.Column>> reach(Flatten ground, FarmPlan campFarm) {
        return current().filter(c -> c.getValue().kind() == FarmMission.Kind.FLATTEN && flatten().filter(ground::equals).isPresent())
                .map(c -> FlattenPlan.reach(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level(), slopes(c.getKey(), campFarm),
                        kept(c.getKey(), campFarm)));
    }

    /** The cells of {@code layer} a floor paves: none of a template's ground beside it (its fence line shared). */
    List<BlockPos> floorCells(Box layer, FarmPlan campFarm) {
        BiPredicate<Integer, Integer> kept = current().map(c -> kept(c.getKey(), campFarm)).orElse((x, z) -> false);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = layer.minX(); x <= layer.maxX(); x++) {
            for (int z = layer.minZ(); z <= layer.maxZ(); z++) {
                if (!kept.test(x, z)) {
                    cells.add(new BlockPos(x, layer.minY(), z));
                }
            }
        }
        return cells;
    }

    /** The {@code i}th of {@code n} pavers' share of {@code layer}: a strip of it each, west to east or north to south, none another's. */
    List<BlockPos> share(Box layer, FarmPlan campFarm, int i, int n) {
        List<Box> strips = layer.strips(Math.max(1, n));
        return i < strips.size() ? floorCells(strips.get(i), campFarm) : List.of();
    }

    /**
     * {@code day}'s objectives with one squad on the mission's step under way, if one is and none holds it (operator,
     * 2026-11-21): the squad already on it; else one given its task already - the flatten's squad, say - which is put
     * on the step as said; else the last in the order of battle but the farm squad. Each move said.
     */
    List<Objective> guarded(List<Objective> day, List<SquadStatus> statuses, Optional<String> farmSquad) {
        Optional<Objective> step = objective();
        if (step.isEmpty() || day.contains(step.get())) {
            return day;
        }
        List<Objective> out = new ArrayList<>(day);
        List<Integer> movable = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            if (farmSquad.filter(statuses.get(i).squadName()::equals).isEmpty()) {
                movable.add(i);
            }
        }
        if (movable.isEmpty()) {
            LOG.warn("[Guardrail] no squad to put on '{}': the farm squad keeps the farm", step.get().mission());
            return day;
        }
        int k = movable.stream().filter(i -> statuses.get(i).standing().filter(step.get()::equals).isPresent()).findFirst()
                .or(() -> movable.stream().filter(i -> out.get(i).task().getClass() == step.get().task().getClass()).findFirst())
                .orElse(movable.getLast());
        LOG.warn("[Guardrail] the operator's farm - {} put on '{}', not '{}'", statuses.get(k).squadName(), step.get().mission(), out.get(k).mission());
        out.set(k, step.get());
        return out;
    }

    /**
     * Measures the step under way in {@code world} at {@code now}, {@code campFarm} the camp's farm as kept: done, it's
     * written DONE with what was measured, its result kept in the chunk register, and the next started - the template's
     * chunk farm taken into the camp's ({@code farms}) as it is.
     */
    synchronized void measure(WorldView world, FarmPlan campFarm, CampFarm farms, long now) {
        load();
        Set<Long> chunks = new HashSet<>();
        book.ifPresent(b -> b.registers().chunks().stream().filter(c -> c.kind() == ChunkRecord.Kind.WHEAT || c.kind() == ChunkRecord.Kind.TREES
                || c.kind() == ChunkRecord.Kind.SUPPLY_POINT).forEach(c -> chunks.add(ChunkFarm.key(c.chunkX(), c.chunkZ()))));
        if (campFarm != null) {
            campFarm.chunks().forEach(c -> chunks.add(ChunkFarm.key(c.chunkX(), c.chunkZ())));
        }
        made = Set.copyOf(chunks);
        recheck(world, campFarm, now);
        Optional<Map.Entry<FarmMission, FarmMission.Step>> under = current();
        if (under.isEmpty()) {
            return;
        }
        FarmMission m = under.get().getKey();
        FarmMission.Step step = under.get().getValue();
        Optional<String> done = switch (step.kind()) {
            case FLATTEN -> {
                Optional<FlattenPlan> plan = plan(world, m, step, campFarm);
                // Said a chunk's row of columns at a time, not each block: the count moves with every cut.
                say(m, step, plan.map(p -> (p.dig().isEmpty() ? "no" : "up to " + (p.dig().size() + 15) / 16 * 16) + " column(s) to cut"
                        + (p.steps().isEmpty() ? "" : ", " + String.join("; ", p.steps()))).orElse("not all of it in sight"));
                // A block of no known break time over the level is in the way though the plan's ground measure can't see it: not
                // done while it stands, and said (FlattenTopsSimulationTest: a chest in the chunk, the flatten measured done round it).
                List<BlockPos> unknown = Clearing.unknownIn(world, new Box(m.minX(), step.level() + 1, m.minZ(), m.minX() + 15, Keep.SKY_TOP, m.minZ() + 15), Set.of());
                unknown.stream().findFirst().ifPresent(c -> say(m, step, Clearer.unknownSaid(world, c)));
                yield plan.filter(FlattenPlan::cut).filter(p -> unknown.isEmpty()).map(p -> "nothing over y=" + step.level() + " in the chunk or its " + slopes(m, campFarm).said()
                        + (p.fill().isEmpty() ? "" : ", " + p.fill().size() + " column(s) under it - the floor's inside, the ground's outside"));
            }
            case FLOOR -> {
                List<BlockPos> cells = floorCells(m.layer(step.level()), campFarm);
                if (cells.stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                long left = cells.stream().filter(c -> !world.isCobblestone(c.x(), c.y(), c.z())).count();
                say(m, step, (left == 0 ? "none" : left <= 16 ? String.valueOf(left) : "up to " + (left + 15) / 16 * 16) + " of " + cells.size() + " cells to pave");
                yield left == 0 ? Optional.of("all " + cells.size() + " cells of y=" + step.level() + " cobblestone") : Optional.empty();
            }
            case FARM_TEMPLATE -> {
                ChunkFarm farm = new ChunkFarm(m.chunkX(), m.chunkZ(), step.level());
                FarmSite.Work work = FarmSite.work(world, farm);
                say(m, step, work.stage() + " - " + work + (work.stage() != FarmSite.Stage.SURVEYED ? "" : ", to fill "
                        + FarmSite.surveyed(world, farm).get(1).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()));
                yield work.stage() == FarmSite.Stage.SURVEYED ? Optional.empty() : Optional.of("the template's ground laid at y=" + step.level() + ", " + work.stage());
            }
        };
        if (done.isEmpty()) {
            return;
        }
        LOG.info("[Mission] {}: {} - DONE, {}", m.name(), step.task(), done.get());
        book.ifPresent(b -> {
            b.done(m, step, done.get(), now);
            if (step.kind() != FarmMission.Kind.FARM_TEMPLATE) {
                b.registers().put(new ChunkRecord(m.chunkX(), m.chunkZ(), step.kind() == FarmMission.Kind.FLATTEN ? ChunkRecord.Kind.FLATTENED
                        : ChunkRecord.Kind.FLOORED, step.level(), Optional.of(step.kind() == FarmMission.Kind.FLATTEN ? "CUT" : "PAVED"), now));
            }
        });
        if (step.index() + 1 < m.steps().size()) {
            missions.put(m, step.index() + 1);
            FarmMission.Step next = m.steps().get(step.index() + 1);
            if (next.kind() == FarmMission.Kind.FARM_TEMPLATE) {
                farms.take(new ChunkFarm(m.chunkX(), m.chunkZ(), next.level()));
            }
            LOG.info("[Mission] {}: {} - next", m.name(), next.task());
        } else {
            missions.remove(m);
            LOG.info("[Mission] {} done", m.name());
        }
        wake.accept("a step of the operator's farm done");
    }

    /** Whether the mission carried on from the register has had its steps behind it measured again. */
    private boolean rechecked;

    /**
     * Once, the first measure with the chunk in sight after the missions are carried on from the register: a flatten
     * behind the step under way measured again, and gone back to if the world says it isn't cut - the register's
     * index is only what was kept. Live (field672), a flatten closed by a squad's "done" before that couldn't happen
     * came back at its floor, 640 columns uncut.
     */
    private void recheck(WorldView world, FarmPlan campFarm, long now) {
        if (rechecked) {
            return;
        }
        Optional<Map.Entry<FarmMission, FarmMission.Step>> under = current();
        if (under.isEmpty()) {
            rechecked = true;
            return;
        }
        FarmMission m = under.get().getKey();
        for (int k = 0; k < under.get().getValue().index(); k++) {
            FarmMission.Step behind = m.steps().get(k);
            if (behind.kind() != FarmMission.Kind.FLATTEN) {
                continue;
            }
            Optional<FlattenPlan> plan = plan(world, m, behind, campFarm);
            if (plan.isEmpty()) {
                return; // not in sight yet: looked at again next measure
            }
            if (!plan.get().cut()) {
                missions.put(m, k);
                book.ifPresent(b -> b.reopen(m, behind, "measured again: not cut", now));
                LOG.warn("[Mission] {}: {} measured again - not cut; worked again", m.name(), behind.task());
            }
            break;
        }
        rechecked = true;
    }

    /** The flatten's plan for {@code m} as {@code world} stands, if every column it works is in sight. */
    private Optional<FlattenPlan> plan(WorldView world, FarmMission m, FarmMission.Step step, FarmPlan campFarm) {
        Slopes slopes = slopes(m, campFarm);
        BiPredicate<Integer, Integer> kept = kept(m, campFarm);
        if (FlattenPlan.reach(m.chunkX(), m.chunkZ(), step.level(), slopes, kept).stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
            return Optional.empty();
        }
        return Optional.of(FlattenPlan.of(m.chunkX(), m.chunkZ(), step.level(), slopes, (x, z) -> top(world, x, z), kept));
    }

    /** The highest solid block's y at ({@code x}, {@code z}): down from the top of the world, an open section at a time. */
    static int top(WorldView world, int x, int z) {
        for (int y = Keep.SKY_TOP; y > -64; y--) {
            int bottom = Math.floorDiv(y, 16) * 16;
            if (y > bottom && world.openSection(x, y, z)) {
                y = bottom + 1;
                continue;
            }
            if (world.isSolid(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The slopes of {@code m}'s flatten: each side sloped unless the chunk past it is a template's. */
    private Slopes slopes(FarmMission m, FarmPlan campFarm) {
        return Slopes.around(m.chunkX(), m.chunkZ(), (cx, cz) -> made(campFarm).contains(ChunkFarm.key(cx, cz)));
    }

    /** The columns a template's ground stands on, but {@code m}'s own chunk's: its box, fence lines and all, never dug or paved. */
    private BiPredicate<Integer, Integer> kept(FarmMission m, FarmPlan campFarm) {
        Set<Long> templates = made(campFarm);
        long own = ChunkFarm.key(m.chunkX(), m.chunkZ());
        return (x, z) -> {
            // A template's box is its chunk and the fence lines past it: x0..x0+16, so a column on a line is its west neighbour's too.
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int cx = Math.floorDiv(x, 16) - (dx == 1 && Math.floorMod(x, 16) == 0 ? 1 : 0);
                    int cz = Math.floorDiv(z, 16) - (dz == 1 && Math.floorMod(z, 16) == 0 ? 1 : 0);
                    long key = ChunkFarm.key(cx, cz);
                    if (key != own && templates.contains(key)) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    /** The template chunks as last measured, with the camp's chunk farms as they stand. */
    private Set<Long> made(FarmPlan campFarm) {
        Set<Long> all = new HashSet<>(made);
        if (campFarm != null) {
            campFarm.chunks().forEach(c -> all.add(ChunkFarm.key(c.chunkX(), c.chunkZ())));
        }
        return all;
    }

    private void say(FarmMission m, FarmMission.Step step, String how) {
        String line = m.name() + ": " + step.task() + " - " + how;
        if (!line.equals(said)) {
            LOG.info("[Mission] {}", line);
            said = line;
        }
    }

    /**
     * The missions open in the register before a restart, each at its first step not done: once, at the first measure or
     * mission given - not before, so the register is opened where the garrison's configured it, not at its default.
     */
    private void load() {
        if (loaded || book.isEmpty()) {
            return;
        }
        loaded = true;
        book.get().openMissions().forEach((name, step) -> FarmMission.named(name).ifPresent(m -> {
            missions.putIfAbsent(m, step);
            LOG.info("[Mission] {} carried on from the register, at step {}", m.name(), step + 1);
        }));
    }
}
