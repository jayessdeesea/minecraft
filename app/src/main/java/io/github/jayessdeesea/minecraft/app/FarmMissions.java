package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
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
import java.util.stream.Collectors;

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

    /** Each open mission, in the order given, and the step under way: each is worked, but one flatten at a time ({@link #worked}). */
    private final Map<FarmMission, Integer> missions = new LinkedHashMap<>();
    /** The tree chunk a tree farm mission made, the last of them: the camp's from then on, read back from the register at a restart. */
    private volatile Optional<TreeChunk> treesMade = Optional.empty();
    /** Wakes the platoon leader: a mission given, a step done. */
    private final Consumer<String> wake;
    private volatile Optional<ObjectiveBook> book = Optional.empty();
    private boolean loaded;
    /** The chunks the force has made a template of, as last measured: a flatten slopes off none of them, nor digs into any. */
    private volatile Set<Long> made = Set.of();
    /** What was last said of each mission's step under way, so each change is said once. */
    private final Map<FarmMission, String> said = new java.util.HashMap<>();

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

    /** Whether a step of {@code kind} lays a template on ground already made, digging and paving nothing. */
    private static boolean template(FarmMission.Kind kind) {
        return kind == FarmMission.Kind.FARM_TEMPLATE || kind == FarmMission.Kind.TREE_TEMPLATE;
    }

    /** The chunks - by ChunkFarm#key - whose farmland is given up: each mission's worked, while it's flattened and floored. */
    synchronized Set<Long> farmlandGivenUp() {
        return worked().stream().filter(e -> !template(e.getValue().kind()))
                .map(e -> ChunkFarm.key(e.getKey().chunkX(), e.getKey().chunkZ())).collect(Collectors.toSet());
    }

    /** The first mission given and its step under way, if any is open: those before a restart once the first measure has read them. */
    synchronized Optional<Map.Entry<FarmMission, FarmMission.Step>> current() {
        return missions.entrySet().stream().findFirst().map(e -> Map.entry(e.getKey(), e.getKey().steps().get(e.getValue())));
    }

    /**
     * The steps worked now, in the order their missions were given: each open mission's step under way - a wheat farm's
     * floor and a tree farm's flatten at once, a squad each - but one flatten at a time, the first given, since the squads
     * flatten one ground ({@code ChainOfCommand.flatten}); a later mission's flatten waits for it.
     */
    synchronized List<Map.Entry<FarmMission, FarmMission.Step>> worked() {
        List<Map.Entry<FarmMission, FarmMission.Step>> worked = new ArrayList<>();
        boolean flattening = false;
        for (Map.Entry<FarmMission, Integer> e : missions.entrySet()) {
            FarmMission.Step step = e.getKey().steps().get(e.getValue());
            if (step.kind() == FarmMission.Kind.FLATTEN) {
                if (flattening) {
                    continue;
                }
                flattening = true;
            }
            worked.add(Map.entry(e.getKey(), step));
        }
        return worked;
    }

    /** The first mission's objective, if one is open ({@link #objectives}). */
    Optional<Objective> objective() {
        return objectives().stream().findFirst();
    }

    /** The objective a squad on each step worked is given: the step as said down the chain, at the chunk, its task. */
    List<Objective> objectives() {
        return worked().stream().map(c -> {
            FarmMission m = c.getKey();
            FarmMission.Step step = c.getValue();
            Objective.Task task = switch (step.kind()) {
                case FLATTEN -> new Objective.Task.Keep();
                case FLOOR -> new Objective.Task.Floor(m.layer(step.level()));
                case FARM_TEMPLATE -> new Objective.Task.Farm();
                case TREE_TEMPLATE -> new Objective.Task.TreeFarm();
            };
            return new Objective(m.said(step), m.centre(step), "The operator's " + (m.crop() == FarmMission.Crop.TREES ? "tree farm" : "farm")
                    + ", step " + (step.index() + 1) + " of " + m.steps().size() + ": " + step.task(), task);
        }).toList();
    }

    /** Whether {@code objective} is a mission's step worked now: its squad is on the operator's mission. */
    boolean on(Optional<Objective> objective) {
        return objective.filter(objectives()::contains).isPresent();
    }

    /** The chunk farm whose template {@code objective} is - a wheat farm mission's last step, worked now - if it is one. */
    Optional<ChunkFarm> template(Optional<Objective> objective) {
        return worked().stream().filter(c -> c.getValue().kind() == FarmMission.Kind.FARM_TEMPLATE
                        && objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent())
                .map(c -> new ChunkFarm(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level())).findFirst();
    }

    /** The mission whose flatten is under way, and that step. */
    private Optional<Map.Entry<FarmMission, FarmMission.Step>> flattening() {
        return worked().stream().filter(c -> c.getValue().kind() == FarmMission.Kind.FLATTEN).findFirst();
    }

    /** The ground to flatten while a mission's flatten is under way: its chunk, down to the step's level. */
    Optional<Flatten> flatten() {
        return flattening().map(c -> new Flatten(c.getKey().minX(), c.getKey().minZ(), c.getKey().minX() + 15, c.getKey().minZ() + 15, c.getValue().level()));
    }

    /**
     * The columns {@code ground}'s flatten works, if it's a mission's: its chunk and its slopes ({@link FlattenPlan#reach}),
     * sloped on each side with no template beside it, never into a template's ground.
     */
    Optional<List<FlattenPlan.Column>> reach(Flatten ground, FarmPlan campFarm) {
        return flattening().filter(c -> flatten().filter(ground::equals).isPresent())
                .map(c -> FlattenPlan.reach(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level(), slopes(c.getKey(), campFarm),
                        kept(c.getKey(), campFarm)));
    }

    /** The cells of {@code layer} a floor paves: none of a template's ground beside it (its fence line shared). */
    List<BlockPos> floorCells(Box layer, FarmPlan campFarm) {
        BiPredicate<Integer, Integer> kept = on(layer).map(m -> kept(m, campFarm)).orElse((x, z) -> false);
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

    /** The open mission whose chunk {@code layer} lies in. */
    private synchronized Optional<FarmMission> on(Box layer) {
        return missions.keySet().stream().filter(m -> m.chunkX() == Math.floorDiv(layer.minX(), 16) && m.chunkZ() == Math.floorDiv(layer.minZ(), 16)).findFirst();
    }

    /** The {@code i}th of {@code n} pavers' share of {@code layer}: a strip of it each, west to east or north to south, none another's. */
    List<BlockPos> share(Box layer, FarmPlan campFarm, int i, int n) {
        List<Box> strips = layer.strips(Math.max(1, n));
        return i < strips.size() ? floorCells(strips.get(i), campFarm) : List.of();
    }

    /**
     * {@code day}'s objectives with a squad on each step worked ({@link #worked}) that none holds (operator, 2026-11-21):
     * the squad already on it; else one given its task already - the flatten's squad, say - which is put on the step as
     * said; else the last in the order of battle but the farm squad and those holding another mission's step. Each move
     * said.
     */
    List<Objective> guarded(List<Objective> day, List<SquadStatus> statuses, Optional<String> farmSquad) {
        List<Objective> out = new ArrayList<>(day);
        Set<Integer> held = new HashSet<>();
        for (Objective step : objectives()) {
            int on = out.indexOf(step);
            if (on >= 0) {
                held.add(on);
                continue;
            }
            List<Integer> movable = new ArrayList<>();
            for (int i = 0; i < statuses.size(); i++) {
                if (!held.contains(i) && farmSquad.filter(statuses.get(i).squadName()::equals).isEmpty()) {
                    movable.add(i);
                }
            }
            if (movable.isEmpty()) {
                LOG.warn("[Guardrail] no squad to put on '{}': the others hold the missions' steps before it, the farm squad keeps the farm", step.mission());
                continue;
            }
            int k = movable.stream().filter(i -> statuses.get(i).standing().filter(step::equals).isPresent()).findFirst()
                    .or(() -> movable.stream().filter(i -> out.get(i).task().getClass() == step.task().getClass()).findFirst())
                    .orElse(movable.getLast());
            LOG.warn("[Guardrail] the operator's farm - {} put on '{}', not '{}'", statuses.get(k).squadName(), step.mission(), out.get(k).mission());
            out.set(k, step);
            held.add(k);
        }
        return out;
    }

    /**
     * Measures each step worked in {@code world} at {@code now}, {@code campFarm} the camp's farm as kept: done, it's
     * written DONE with what was measured, its result kept in the chunk register, and the next started - a wheat
     * template's chunk farm taken into the camp's ({@code farms}) as it is, a tree template's chunk the camp's tree chunk
     * ({@code trees}) from then on.
     */
    synchronized void measure(WorldView world, FarmPlan campFarm, CampFarm farms, CampTrees trees, long now) {
        load();
        Set<Long> chunks = new HashSet<>();
        book.ifPresent(b -> b.registers().chunks().stream().filter(c -> c.kind() == ChunkRecord.Kind.WHEAT || c.kind() == ChunkRecord.Kind.TREES
                || c.kind() == ChunkRecord.Kind.SUPPLY_POINT).forEach(c -> chunks.add(ChunkFarm.key(c.chunkX(), c.chunkZ()))));
        if (campFarm != null) {
            campFarm.chunks().forEach(c -> chunks.add(ChunkFarm.key(c.chunkX(), c.chunkZ())));
        }
        made = Set.copyOf(chunks);
        recheck(world, campFarm, now);
        if (trees != null) {
            treeChunk().ifPresent(trees::take);
        }
        for (Map.Entry<FarmMission, FarmMission.Step> under : worked()) {
            measure(world, campFarm, farms, now, under.getKey(), under.getValue());
        }
    }

    /**
     * The tree chunk the missions give the camp: the one a tree farm mission is laying its template on, else the last one
     * made - kept in the register's rows, so a restart reads it back.
     */
    private Optional<TreeChunk> treeChunk() {
        return missions.entrySet().stream().filter(e -> e.getKey().steps().get(e.getValue()).kind() == FarmMission.Kind.TREE_TEMPLATE)
                .map(e -> new TreeChunk(e.getKey().chunkX(), e.getKey().chunkZ(), e.getKey().level())).reduce((first, last) -> last).or(() -> treesMade);
    }

    private void measure(WorldView world, FarmPlan campFarm, CampFarm farms, long now, FarmMission m, FarmMission.Step step) {
        // A chunk being flattened and floored is no farm: what the camp's farm holds there - a superseded mission's template
        // (field668-682, at y=67) - is given up, or the farm squad sows it and the dig cuts round it. Its own template's
        // taken in again when the floor's done.
        if (!template(step.kind()) && farms != null) {
            farms.giveUp(m.chunkX(), m.chunkZ());
        }
        Optional<String> done = switch (step.kind()) {
            case FLATTEN -> {
                Optional<FlattenPlan> plan = plan(world, m, step, campFarm);
                // Said a chunk's row of columns at a time, not each block: the count moves with every cut.
                say(m, step, plan.map(p -> (p.dig().isEmpty() ? "no" : "up to " + (p.dig().size() + 15) / 16 * 16) + " column(s) to cut"
                        + (p.steps().isEmpty() ? "" : ", " + String.join("; ", p.steps()))).orElse("not all of it in sight"));
                // A block of no known break time over the level is in the way though the plan's ground measure can't see it: not
                // done while it stands, and said (FlattenTopsSimulationTest: a chest in the chunk, the flatten measured done round it).
                Box over = new Box(m.minX(), step.level() + 1, m.minZ(), m.minX() + 15, Keep.SKY_TOP, m.minZ() + 15);
                List<BlockPos> unknown = Clearing.unknownIn(world, over, Set.of());
                List<BlockPos> water = Clearing.waterIn(world, over, Set.of()); // the plan's ground measure sees no water: it's filled and dug first
                unknown.stream().findFirst().ifPresent(c -> say(m, step, Clearer.unknownSaid(world, c)));
                yield plan.filter(FlattenPlan::cut).filter(p -> unknown.isEmpty() && water.isEmpty()).map(p -> "nothing over y=" + step.level() + " in the chunk or its " + slopes(m, campFarm).said()
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
            case TREE_TEMPLATE -> {
                TreeChunk chunk = new TreeChunk(m.chunkX(), m.chunkZ(), step.level());
                if (chunk.trunks().stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                // Planted, its chest in and lit: the fence is the grid's, not this mission's. A trunk with no soil under it
                // is said, since the stage stands at GROUND while one lacks it and the planters pass it over.
                TreeChunk.Stage stage = chunk.stage(world);
                int takes = chunk.plantable(world).size();
                say(m, step, stage + " - " + takes + " of " + chunk.trunks().size() + " trunk cells take a sapling"
                        + (stage == TreeChunk.Stage.GROUND ? ", the rest want soil under them and nothing over" : ""));
                yield stage.compareTo(TreeChunk.Stage.PRODUCING) >= 0 ? Optional.of("its " + chunk.trunks().size()
                        + " saplings in, its chest and its " + chunk.torches().size() + " torches - " + stage) : Optional.empty();
            }
        };
        if (done.isEmpty()) {
            return;
        }
        LOG.info("[Mission] {}: {} - DONE, {}", m.name(), step.task(), done.get());
        book.ifPresent(b -> {
            b.done(m, step, done.get(), now);
            if (!template(step.kind())) {
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
            said.remove(m);
            if (step.kind() == FarmMission.Kind.TREE_TEMPLATE) {
                treesMade = Optional.of(new TreeChunk(m.chunkX(), m.chunkZ(), step.level()));
            }
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
        if (!line.equals(said.put(m, line))) {
            LOG.info("[Mission] {}", line);
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
        // The tree chunk a mission made before the restart: its template's row done, the last of them.
        treesMade = book.get().registers().objectives().stream()
                .filter(row -> row.state() == io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.DONE)
                .flatMap(row -> FarmMission.named(row.mission()).filter(m -> m.crop() == FarmMission.Crop.TREES
                        && m.steps().get(row.step()).kind() == FarmMission.Kind.TREE_TEMPLATE).stream())
                .map(m -> new TreeChunk(m.chunkX(), m.chunkZ(), m.level())).reduce((first, last) -> last);
    }
}
