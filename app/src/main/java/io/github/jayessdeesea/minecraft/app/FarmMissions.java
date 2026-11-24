package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.MineHead;
import io.github.jayessdeesea.minecraft.bot.farm.PlaceholderChunk;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.farm.StoresChunk;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.supply.Fixtures;
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
        built.removeIf(m -> m.chunkX() == mission.chunkX() && m.chunkZ() == mission.chunkZ());
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
        return kind == FarmMission.Kind.FARM_TEMPLATE || kind == FarmMission.Kind.TREE_TEMPLATE || kind == FarmMission.Kind.STORES_TEMPLATE
                || kind == FarmMission.Kind.MINE_HEAD || kind == FarmMission.Kind.MINE || kind == FarmMission.Kind.PASTURE_TEMPLATE
                || kind == FarmMission.Kind.PLACEHOLDER_TEMPLATE;
    }

    /**
     * Whether {@code m}, at {@code step}, has ground standing over its slab: a template going up, or its level being paved
     * - a block over what its flatten cut to. Measured against the flatten then, that ground reads as uncut.
     */
    private static boolean overItsSlab(FarmMission m, FarmMission.Step step) {
        return template(step.kind()) || step.level() > m.steps().getFirst().level();
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
        // The farms' steps before the others', each in the order given: food and wood are what the rest is built on
        // (operator, 2026-11-23), so with squads short it's a supply point or a mineshaft that waits.
        List<Map.Entry<FarmMission, Integer>> farmsFirst = missions.entrySet().stream()
                .sorted(java.util.Comparator.comparingInt(e -> e.getKey().crop() == FarmMission.Crop.WHEAT || e.getKey().crop() == FarmMission.Crop.TREES ? 0 : 1)).toList();
        for (Map.Entry<FarmMission, Integer> e : farmsFirst) {
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
                case STORES_TEMPLATE, MINE_HEAD, PASTURE_TEMPLATE, PLACEHOLDER_TEMPLATE -> new Objective.Task.Stores();
                case MINE -> new Objective.Task.Mine();
            };
            // A shaft's objective is its head's x and z and the depth to cut to: a MINE's y is a depth (NightRules.grounded).
            io.github.jayessdeesea.minecraft.command.Position at = step.kind() == FarmMission.Kind.MINE ? new io.github.jayessdeesea.minecraft.command.Position(m.minX() + 8.5, ChainOfCommand.MINE_DEPTH, m.minZ() + 8.5) : m.centre(step);
            return new Objective(m.said(step), at, "The operator's " + m.what()
                    + ", step " + (step.index() + 1) + " of " + m.steps().size() + ": " + step.task(), task);
        }).toList();
    }

    /**
     * Whether {@code objective}'s place is a mission's own, on ground the force has made at a level it knows: a step past
     * its flatten, or a farm built and kept. It isn't put "on the ground" again by what stands there now: live
     * (field708-711), a tree farm's point at y=64 was put on its trees' canopy at y=69, its squads stood up there with
     * nothing to do, and soldiers walking to points five blocks up put steps on the wheat farm beside it - which its
     * review found, reopened and had dug away, every ten minutes.
     */
    synchronized boolean atItsLevel(Objective objective) {
        io.github.jayessdeesea.minecraft.command.Position at = objective.location();
        return java.util.stream.Stream.concat(
                missions.entrySet().stream().filter(e -> e.getKey().steps().get(e.getValue()).kind() != FarmMission.Kind.FLATTEN)
                        .map(e -> e.getKey().centre(e.getKey().steps().get(e.getValue()))),
                built.stream().map(m -> m.centre(m.steps().getLast()))).anyMatch(at::equals);
    }

    /** Whether {@code objective} is a mission's step worked now: its squad is on the operator's mission. */
    boolean on(Optional<Objective> objective) {
        // By its mission, not the whole objective: one given to a squad has its place put on the ground it stands on, and
        // is no longer the one made here. Live (field700), a supply point's flatten and a tree farm's template weren't
        // known as missions' at dusk, and their squads were sent down the camp's mine each night.
        List<String> steps = objectives().stream().map(Objective::mission).toList();
        return objective.filter(o -> steps.contains(o.mission())).isPresent();
    }

    /**
     * The wheat farm {@code objective} is for, by its mission and not by whatever plot its squad found there: one whose
     * template is being laid, or one built and kept. Its squad paves its footings and tends it as that chunk farm.
     */
    synchronized Optional<ChunkFarm> wheatFarm(Optional<Objective> objective) {
        return template(objective).or(() -> objective.flatMap(o -> built.stream().filter(m -> m.crop() == FarmMission.Crop.WHEAT
                && m.centre(m.steps().getLast()).equals(o.location())).findFirst()).map(m -> new ChunkFarm(m.chunkX(), m.chunkZ(), m.level())));
    }

    /** The chunk farm whose template {@code objective} is - a wheat farm mission's last step, worked now - if it is one. */
    Optional<ChunkFarm> template(Optional<Objective> objective) {
        return worked().stream().filter(c -> c.getValue().kind() == FarmMission.Kind.FARM_TEMPLATE
                        && objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent())
                .map(c -> new ChunkFarm(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level())).findFirst();
    }

    /**
     * The fixtures {@code objective}'s squad puts up, if it's a step worked now that lays them out: a supply point's
     * template, a mineshaft's head - which item in which cells, in the order they're built ({@code bot.supply.Fixtures}).
     */
    Optional<Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>>> fixtures(Optional<Objective> objective) {
        return worked().stream().filter(c -> objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent())
                .flatMap(c -> switch (c.getValue().kind()) {
                    case STORES_TEMPLATE -> java.util.stream.Stream.of(new StoresChunk(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level()).fixtures());
                    case MINE_HEAD -> java.util.stream.Stream.of(new MineHead(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level()).fixtures());
                    case PASTURE_TEMPLATE -> java.util.stream.Stream.of(TemplateDrift.pasture(c.getKey()).fixtures());
                    case PLACEHOLDER_TEMPLATE -> java.util.stream.Stream.of(new PlaceholderChunk(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level()).fixtures());
                    default -> java.util.stream.Stream.empty();
                }).findFirst();
    }

    /** What hangs in the sky of the template {@code objective}'s squad is laying, for it to dig: none for any other, or with a clear sky. */
    Optional<List<BlockPos>> inTheSky(Optional<Objective> objective, WorldView world) {
        return worked().stream().filter(c -> TemplateDrift.skyKept(c.getValue().kind()) && objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent())
                .map(c -> TemplateDrift.sky(c.getKey(), world)).filter(hanging -> !hanging.isEmpty()).findFirst();
    }

    /** The animal farm whose template {@code objective} is, worked now - if it is one: its squad lays its ground. */
    Optional<io.github.jayessdeesea.minecraft.bot.farm.PastureChunk> pasture(Optional<Objective> objective) {
        return worked().stream().filter(c -> c.getValue().kind() == FarmMission.Kind.PASTURE_TEMPLATE
                && objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent()).map(c -> TemplateDrift.pasture(c.getKey())).findFirst();
    }

    /**
     * The footings {@code objective}'s squad paves in cobblestone, if it's on a mission's wheat farm - building or built -
     * or an animal farm's template: what its fence, gates, lights and chest stand on.
     */
    Optional<List<BlockPos>> footingsToPave(Optional<Objective> objective, WorldView world) {
        return wheatFarm(objective).map(f -> f.footingsToPave(world)).or(() -> pasture(objective).map(f -> f.footingsToPave(world)));
    }

    /** The mineshafts mined dry, by their chunks' keys: told by whoever works them ({@link #mineDry}). */
    private final Set<Long> dry = new HashSet<>();

    /** The mine whose head is at block ({@code x}, {@code z}) is dry: a mineshaft mission on that chunk is done at its next measure. */
    synchronized void mineDry(int x, int z) {
        dry.add(ChunkFarm.key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
    }

    /** Whether a mineshaft mission's shaft is being worked under chunk ({@code chunkX}, {@code chunkZ}): the one templated chunk a well is sited in. */
    synchronized boolean mineAt(int chunkX, int chunkZ) {
        return missions.entrySet().stream().anyMatch(e -> e.getKey().chunkX() == chunkX && e.getKey().chunkZ() == chunkZ
                && e.getKey().steps().get(e.getValue()).kind() == FarmMission.Kind.MINE);
    }

    /** The base the platoon builds without being told each chunk, if it has one: {@link #works}. */
    private volatile Optional<io.github.jayessdeesea.minecraft.command.BasePlan> plan = Optional.empty();

    /** From now on the missions of {@code base} are given by rule, as squads come free: the platoon leader's strategy (operator, 2026-11-23). */
    void works(io.github.jayessdeesea.minecraft.command.BasePlan base) {
        plan = Optional.of(base);
    }

    /**
     * The plan's next mission given, if there's a squad for it: never more missions open than {@code squads} to work them -
     * the farm squad not among them, it tends what's built - so the force isn't over-extended (operator, 2026-11-23). One a
     * decision. A chunk with a mission already - the operator's, open or built, whatever its template - is left as it is.
     */
    private synchronized void fromThePlan(int squads) {
        if (plan.isEmpty() || book.isEmpty()) {
            return;
        }
        load();
        if (missions.size() >= squads) {
            return;
        }
        Set<Long> taken = new HashSet<>();
        Set<FarmMission.Crop> had = new HashSet<>(), standing = new HashSet<>();
        java.util.stream.Stream.concat(java.util.stream.Stream.concat(missions.keySet().stream(), built.stream()),
                book.get().registers().objectives().stream().flatMap(r -> FarmMission.named(r.mission()).stream())).forEach(m -> {
            taken.add(io.github.jayessdeesea.minecraft.command.BasePlan.key(m.chunkX(), m.chunkZ()));
            had.add(m.crop());
        });
        built.forEach(m -> standing.add(m.crop()));
        // Food and wood first: nothing else is begun until a wheat farm and a tree farm stand (BasePlan.next).
        plan.get().next(taken, had, standing).ifPresent(next -> {
            LOG.info("[Plan] the base's next: {} - {} mission(s) open, {} squad(s) to work them", next.name(), missions.size(), squads);
            give(next, measuredAt);
        });
    }

    /** The tree chunk whose template {@code objective} is - a tree farm mission's last step, worked now - if it is one. */
    Optional<TreeChunk> trees(Optional<Objective> objective) {
        return worked().stream().filter(c -> c.getValue().kind() == FarmMission.Kind.TREE_TEMPLATE
                        && objective.filter(o -> o.mission().equals(c.getKey().said(c.getValue()))).isPresent())
                .map(c -> new TreeChunk(c.getKey().chunkX(), c.getKey().chunkZ(), c.getValue().level())).findFirst();
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

    /**
     * The cells of {@code layer} a floor paves: none of a template's ground beside it (its fence line shared), and at a
     * tree farm's level none of its trees' cells, which are dirt - the template's to lay. Live (field704-707), a tree
     * farm planted and open again at its paving had "16 of 240 cells to pave", and thirteen trees' dirt was paved over.
     */
    List<BlockPos> floorCells(Box layer, FarmPlan campFarm) {
        Optional<FarmMission> mission = on(layer);
        BiPredicate<Integer, Integer> kept = mission.map(m -> kept(m, campFarm)).orElse((x, z) -> false);
        Set<BlockPos> soil = mission.filter(m -> m.crop() == FarmMission.Crop.TREES && m.level() == layer.minY())
                .map(m -> Set.copyOf(new TreeChunk(m.chunkX(), m.chunkZ(), m.level()).soil())).orElse(Set.of());
        List<BlockPos> cells = new ArrayList<>();
        for (int x = layer.minX(); x <= layer.maxX(); x++) {
            for (int z = layer.minZ(); z <= layer.maxZ(); z++) {
                if (!kept.test(x, z) && !soil.contains(new BlockPos(x, layer.minY(), z))) {
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
        fromThePlan(statuses.size() - (farmSquad.isPresent() ? 1 : 0));
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
        if (plan.isPresent()) {
            atTheFarms(out, statuses, held, farmSquad);
        }
        return out;
    }

    /**
     * By the plan, a squad with no mission's step is given a farm to keep, not the camp (operator, 2026-11-23: a reliable
     * food source and a reliable wood source first): one squad to each farm built of the kind its standing work is - the
     * wheat or the trees - at that farm's chunk; and with no such farm left to keep, it joins the first step under way,
     * the farms' before the others' - more hands on what food and wood wait on. Live (field701), three squads given
     * "keep the wheat farms" and "keep the tree farms" at the camp stood there: "no ground to farm by water within 8
     * blocks", one squad on the tree farm's flatten.
     */
    private synchronized void atTheFarms(List<Objective> out, List<SquadStatus> statuses, Set<Integer> held, Optional<String> farmSquad) {
        Optional<Objective> first = objectives().stream().findFirst();
        Set<FarmMission> kept = new HashSet<>();
        tended = kept;
        // The farm squad first, and the wheat is its: live (field702), it stood at the camp with "keep the tree farms"
        // while another squad kept the wheat farm.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            order.add(farmSquad.filter(statuses.get(i).squadName()::equals).isPresent() ? 0 : order.size(), i);
        }
        for (int i : order) {
            Objective standing = out.get(i);
            boolean farms = farmSquad.filter(statuses.get(i).squadName()::equals).isPresent();
            FarmMission.Crop kind = farms || standing.task() instanceof Objective.Task.Farm ? FarmMission.Crop.WHEAT
                    : standing.task() instanceof Objective.Task.TreeFarm ? FarmMission.Crop.TREES : null;
            if (held.contains(i) || kind == null) {
                continue;
            }
            Optional<FarmMission> farm = built.stream().filter(m -> m.crop() == kind && !kept.contains(m)).findFirst();
            if (farm.isPresent()) {
                FarmMission m = farm.get();
                kept.add(m);
                out.set(i, new Objective("Keep the " + m.what() + " at chunk (" + m.chunkX() + ", " + m.chunkZ() + ")", m.centre(m.steps().getLast()),
                        // The farm's own intent and task, whatever the squad was dealt: live, the farm squad kept the wheat farm
                        // under "Wood that grows back" (operator, 2026-11-24: a wood farm or a wheat farm?).
                        kind == FarmMission.Crop.WHEAT ? "Food for the force" : "Wood that grows back",
                        kind == FarmMission.Crop.WHEAT ? new Objective.Task.Farm() : new Objective.Task.TreeFarm()));
            } else if (first.isPresent()) {
                out.set(i, first.get());
            }
        }
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
        measuredAt = now;
        recheck(world, campFarm, now);
        if (trees != null) {
            treeChunk().ifPresent(trees::take);
        }
        for (Map.Entry<FarmMission, FarmMission.Step> under : worked()) {
            measure(world, campFarm, farms, now, under.getKey(), under.getValue());
        }
        review(world, now);
        alarmStalled(now);
    }

    /** The missions done, their templates standing: what the review looks over. */
    private final Set<FarmMission> built = new java.util.LinkedHashSet<>();
    /** How often the review runs: what a squad keeps is looked over each time, the rest when it's due. */
    static final long REVIEW_MILLIS = 5 * 60_000;
    private long reviewedAt = Long.MIN_VALUE;
    /** A garrison day and night; and how long a built template nobody is working goes between looks, at most. */
    static final long DAY_MILLIS = 20 * 60_000, REVIEW_EVERY = 4 * DAY_MILLIS;
    /** When each built template is next due a look: by mission, never one time for all. */
    private final Map<FarmMission, Long> reviewDue = new java.util.HashMap<>();
    /** The built farms a squad was last given to keep. */
    private Set<FarmMission> tended = Set.of();

    /** When {@code m} is next looked over, once it has been. */
    synchronized Optional<Long> reviewDue(FarmMission m) {
        return Optional.ofNullable(reviewDue.get(m));
    }

    /** The templates built and not open again, for whoever asks what stands. */
    synchronized Set<FarmMission> built() {
        return Set.copyOf(built);
    }

    /**
     * The review (operator, 2026-11-22: once the sites are built the construction might drift - who reviews and fixes
     * it?): a template built and in sight - the force near it, no remote outpost with nobody there - is measured against
     * its layout again, and one whose structure has gone ({@link TemplateDrift#drifted}) has its template step opened again - a repair,
     * a squad's task as the build was, kept in the register with what was found. One a squad keeps is looked over every
     * {@link #REVIEW_MILLIS}, as the first thing done with it; any other when first seen and then inside every
     * {@link #REVIEW_EVERY}, up to a day early by its name and the day, so the sites aren't all looked over at once
     * (operator, 2026-11-24).
     */
    private void review(WorldView world, long now) {
        if (reviewedAt != Long.MIN_VALUE && now - reviewedAt < REVIEW_MILLIS) {
            return;
        }
        reviewedAt = now;
        for (FarmMission m : List.copyOf(built)) {
            if (!world.isLoaded(m.minX(), m.minZ()) || !world.isLoaded(m.minX() + 15, m.minZ() + 15)) {
                continue; // not in sight: looked at next time
            }
            if (!tended.contains(m) && now < reviewDue.getOrDefault(m, Long.MIN_VALUE)) {
                continue;
            }
            reviewDue.put(m, now + REVIEW_EVERY - Math.floorMod(java.util.Objects.hash(m.name(), now / DAY_MILLIS), DAY_MILLIS));
            Optional<String> gone = TemplateDrift.drifted(world, m);
            if (gone.isEmpty()) {
                continue;
            }
            FarmMission.Step last = m.steps().getLast();
            built.remove(m);
            missions.put(m, last.index());
            book.ifPresent(b -> b.reopen(m, last, "reviewed: " + gone.get(), now));
            LOG.warn("[Mission] {} reviewed - {}; its template worked again", m.name(), gone.get());
            wake.accept("a template built wants repair");
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
                        + (p.steps().isEmpty() ? "" : ", " + String.join("; ", p.steps()))
                        // The last of them named: live (field697), "up to 16 column(s)" stood four hours and said nothing of which.
                        + (p.dig().isEmpty() || p.dig().size() > 16 ? "" : " - at " + p.dig().stream().limit(8).map(c -> c.x() + "," + c.z()).toList())).orElse("not all of it in sight"));
                // A block of no known break time over the level is in the way though the plan's ground measure can't see it: not
                // done while it stands, and said (FlattenTopsSimulationTest: a chest in the chunk, the flatten measured done round it).
                Box over = new Box(m.minX(), step.level() + 1, m.minZ(), m.minX() + 15, Keep.SKY_TOP, m.minZ() + 15);
                List<BlockPos> unknown = Clearing.unknownIn(world, over, Set.of());
                List<BlockPos> water = Clearing.waterIn(world, over, Set.of()); // the plan's ground measure sees no water: it's filled and dug first
                unknown.stream().findFirst().ifPresent(c -> say(m, step, Clearer.unknownSaid(world, c)));
                // The chunk itself cut and only columns out on its slopes left, their count unchanged for SLOPE_PATIENCE: the
                // step is done with them left and said - the chunk is what the template wants; the slope is how it's walked onto.
                // Live (field697-698), a tree farm's chunk lay flat for five hours while nine columns ten blocks out, eleven
                // up a hill, were handed from one soldier to another every two minutes, neither reaching them.
                Optional<String> slopesLeft = plan.filter(p -> !p.cut() && unknown.isEmpty() && water.isEmpty() && chunkCut(m, p)).flatMap(p -> {
                    long[] seen = slopeLeft.computeIfAbsent(m, k -> new long[]{p.dig().size(), now});
                    if (seen[0] != p.dig().size()) {
                        seen[0] = p.dig().size();
                        seen[1] = now;
                    }
                    return now - seen[1] < SLOPE_PATIENCE_MILLIS ? Optional.<String>empty() : Optional.of("nothing over y=" + step.level() + " in the chunk; "
                            + p.dig().size() + " column(s) out on its slopes left as they are, none cut in " + SLOPE_PATIENCE_MILLIS / 60_000 + " min - out of reach: "
                            + p.dig().stream().limit(8).map(c -> c.x() + "," + c.z() + " at y=" + c.from()).toList());
                });
                if (plan.filter(p -> chunkCut(m, p) && !p.cut()).isEmpty()) {
                    slopeLeft.remove(m);
                }
                yield plan.filter(FlattenPlan::cut).filter(p -> unknown.isEmpty() && water.isEmpty()).map(p -> "nothing over y=" + step.level() + " in the chunk or its " + slopes(m, campFarm).said()
                        + (p.fill().isEmpty() ? "" : ", " + p.fill().size() + " column(s) under it - the floor's inside, the ground's outside")).or(() -> slopesLeft);
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
                        + FarmSite.surveyed(world, farm).get(1).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()
                        + ", to pave " + FarmSite.surveyed(world, farm).get(2).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()
                        + ", in the way " + FarmSite.surveyed(world, farm).get(0).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()));
                yield work.stage() == FarmSite.Stage.SURVEYED ? Optional.empty() : Optional.of("the template's ground laid at y=" + step.level() + ", " + work.stage());
            }
            case TREE_TEMPLATE -> {
                TreeChunk chunk = new TreeChunk(m.chunkX(), m.chunkZ(), step.level());
                if (chunk.trunks().stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                // Its trees' dirt first: each tree's cell of the paving dug out and dirt laid in it, the squad's (TemplateGround.treeOrders).
                int toDig = chunk.soilToDig(world).size(), toLay = chunk.soilToLay(world).size();
                if (toDig + toLay > 0) {
                    say(m, step, "GROUND - of its " + chunk.soil().size() + " trees' cells, " + toDig + " to dig out of the paving and " + toLay + " to lay in dirt");
                    yield Optional.empty();
                }
                // Planted, its chest in and lit: the fence is the grid's, not this mission's. A trunk with no soil under it
                // is said, since the stage stands at GROUND while one lacks it and the planters pass it over.
                TreeChunk.Stage stage = chunk.stage(world);
                int takes = chunk.plantable(world).size();
                say(m, step, stage + " - " + takes + " of " + chunk.trunks().size() + " trunk cells take a sapling"
                        + (stage == TreeChunk.Stage.GROUND ? ", the rest want soil under them and nothing over" : "")
                        + (stage != TreeChunk.Stage.PLANTED ? "" : ", not planted: " + chunk.trunks().stream().filter(t -> !io.github.jayessdeesea.minecraft.bot.farm.TreeFarm.planted(world, t))
                        .limit(4).map(t -> t.x() + "," + t.y() + "," + t.z()).toList() + (world.isChest(chunk.chest().x(), chunk.chest().y(), chunk.chest().z()) ? "" : ", no chest")));
                yield stage.compareTo(TreeChunk.Stage.PRODUCING) >= 0 ? Optional.of("its " + chunk.trunks().size()
                        + " saplings in, its chest and its " + chunk.torches().size() + " torches - " + stage) : Optional.empty();
            }
            case MINE_HEAD, PLACEHOLDER_TEMPLATE -> {
                boolean head = step.kind() == FarmMission.Kind.MINE_HEAD;
                Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> layout = head
                        ? new MineHead(m.chunkX(), m.chunkZ(), step.level()).fixtures() : new PlaceholderChunk(m.chunkX(), m.chunkZ(), step.level()).fixtures();
                if (!world.isLoaded(m.minX(), m.minZ()) || !world.isLoaded(m.minX() + 15, m.minZ() + 15)) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                // Everything its ground takes: a cell with no ground under it, or ground standing in it, is passed over.
                Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, layout);
                say(m, step, "to put up: " + (left.isEmpty() ? "nothing" : left.entrySet().stream()
                        .map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "))));
                yield left.isEmpty() ? Optional.of(head ? "fenced and lit, its head's chests up" : "paved, fenced, gated and lit") : Optional.empty();
            }
            case MINE -> {
                say(m, step, "its squad down it");
                yield dry.contains(ChunkFarm.key(m.chunkX(), m.chunkZ())) ? Optional.of("mined dry: every level cut, its ore taken or noted") : Optional.empty();
            }
            case PASTURE_TEMPLATE -> {
                io.github.jayessdeesea.minecraft.bot.farm.PastureChunk pasture = TemplateDrift.pasture(m);
                if (!world.isLoaded(m.minX(), m.minZ()) || !world.isLoaded(m.minX() + 15, m.minZ() + 15)) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                int toLay = pasture.groundToLay(world).size(), toPave = pasture.footingsToPave(world).size();
                Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, pasture.fixtures());
                say(m, step, (toLay + toPave > 0 ? "GROUND - " + toLay + " cell(s) to lay in dirt, " + toPave + " footing(s) to pave; " : "")
                        + "to put up: " + (left.isEmpty() ? "nothing" : left.entrySet().stream()
                        .map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "))));
                yield toLay + toPave == 0 && left.isEmpty() ? Optional.of("its ground laid, fenced, gated and lit, its chest up") : Optional.empty();
            }
            case STORES_TEMPLATE -> {
                StoresChunk stores = new StoresChunk(m.chunkX(), m.chunkZ(), step.level());
                if (stores.ground().stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
                    say(m, step, "not all of it in sight");
                    yield Optional.empty();
                }
                // Stocked - fenced and lit, its workshop in, its first rows up: the rows after are a follow-on order.
                Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, stores.fixtures());
                say(m, step, stores.stage(world) + " - to put up: " + (left.isEmpty() ? "nothing" : left.entrySet().stream()
                        .map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "))));
                yield stores.stocked(world) ? Optional.of("fenced and lit, its workshop in and its "
                        + StoresChunk.FIRST_ROWS.size() * 6 + " double chests up") : Optional.empty();
            }
        };
        if (done.isEmpty()) {
            return;
        }
        List<BlockPos> hanging = TemplateDrift.skyKept(step.kind()) ? TemplateDrift.sky(m, world) : List.of();
        if (!hanging.isEmpty()) {
            say(m, step, hanging.size() + " block(s) hanging in its sky, to dig: " + hanging.stream().limit(6).map(b -> b.x() + "," + b.y() + "," + b.z()).toList());
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
            built.add(m);
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
        // Not once its template is going on, or its level's paving: the template's own ground and fence, and the paving, stand
        // over the flatten's level, and read as uncut. Live (field693), a wheat farm restarted at its template went back to
        // its flatten, to dig up the ground laid, and - one flatten at a time - held the tree farm's flatten behind it.
        if (overItsSlab(under.get().getKey(), under.get().getValue())) {
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
            if (!chunkCut(m, plan.get())) { // its chunk's own columns: the slopes' may have been left as out of reach
                missions.put(m, k);
                book.ifPresent(b -> b.reopen(m, behind, "measured again: not cut", now));
                LOG.warn("[Mission] {}: {} measured again - not cut; worked again", m.name(), behind.task());
            }
            break;
        }
        rechecked = true;
    }

    /** How long a flatten's slope columns stand uncut, the chunk itself cut, before the step is done with them left. */
    static final long SLOPE_PATIENCE_MILLIS = 15 * 60_000;
    /** Each flattening mission's slope columns left, and since when that count has stood: {count, since}. */
    private final Map<FarmMission, long[]> slopeLeft = new java.util.HashMap<>();

    /** Whether {@code plan} has nothing left to cut in {@code m}'s own chunk: what's left, if anything, is out on its slopes. */
    private static boolean chunkCut(FarmMission m, FlattenPlan plan) {
        return plan.dig().stream().noneMatch(c -> c.x() >= m.minX() && c.x() <= m.minX() + 15 && c.z() >= m.minZ() && c.z() <= m.minZ() + 15);
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

    /**
     * The slopes of {@code m}'s flatten: each side sloped unless the chunk past it is a template's - or another open
     * mission's, to be cut to its own level: a slope into it is that ground cut twice.
     */
    private Slopes slopes(FarmMission m, FarmPlan campFarm) {
        Set<Long> open = openChunks(m);
        return Slopes.around(m.chunkX(), m.chunkZ(), (cx, cz) -> made(campFarm).contains(ChunkFarm.key(cx, cz)) || open.contains(ChunkFarm.key(cx, cz)));
    }

    /** The chunks of the open missions other than {@code m}. */
    private synchronized Set<Long> openChunks(FarmMission m) {
        return missions.keySet().stream().filter(o -> !o.equals(m)).map(o -> ChunkFarm.key(o.chunkX(), o.chunkZ())).collect(Collectors.toSet());
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

    /** Whether block column ({@code x}, {@code z}) is on a template's ground: a chunk made one, or a mission's whose template is being laid. */
    synchronized boolean templateAt(int x, int z) {
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        return made.contains(ChunkFarm.key(cx, cz)) || missions.entrySet().stream().anyMatch(e -> e.getKey().chunkX() == cx && e.getKey().chunkZ() == cz
                && template(e.getKey().steps().get(e.getValue()).kind()));
    }

    /** The template chunks as last measured, with the camp's chunk farms as they stand. */
    private Set<Long> made(FarmPlan campFarm) {
        Set<Long> all = new HashSet<>(made);
        if (campFarm != null) {
            campFarm.chunks().forEach(c -> all.add(ChunkFarm.key(c.chunkX(), c.chunkZ())));
        }
        // And every mission's chunk whose template is going up or stands - a supply point's, a mineshaft's head: its fence
        // is on the line it shares with the chunk beside it, and a flatten there cuts no slope into it. Live (field699), a
        // tree farm's flatten counted the posts of the mineshaft's head south of it among its columns to cut.
        built.forEach(m -> all.add(ChunkFarm.key(m.chunkX(), m.chunkZ())));
        missions.forEach((m, at) -> {
            if (overItsSlab(m, m.steps().get(at))) {
                all.add(ChunkFarm.key(m.chunkX(), m.chunkZ()));
            }
        });
        return all;
    }

    private void say(FarmMission m, FarmMission.Step step, String how) {
        String line = m.name() + ": " + step.task() + " - " + how;
        if (!line.equals(said.put(m, line))) {
            LOG.info("[Mission] {}", line);
            movedAt.put(m, measuredAt);
            stalled.remove(m);
        }
    }

    /** How long a mission's step stands as last measured, nothing of it done, before it's said as stalled. */
    static final long STALL_MILLIS = 20 * 60_000;
    /** When each mission's measure last changed, the missions said as stalled since, and the clock at the measure under way. */
    private final Map<FarmMission, Long> movedAt = new java.util.HashMap<>();
    private final Set<FarmMission> stalled = new HashSet<>();
    private long measuredAt;

    /**
     * The alarm for a mission making no progress (operator, 2026-11-23: blocking on that one block seems very wrong -
     * there should be an alarm): each step worked whose measure hasn't changed in {@link #STALL_MILLIS} is said once, by
     * name and with what it waits on, as an outcome missed - and the platoon leader woken. Live (field697-698), a wheat
     * farm stood at "2 cells to fill" for ten hours and a tree farm's flatten at "16 columns" for five, each in the log
     * and neither an alarm. Not a mineshaft's shaft: its measure is "dry or not", and says nothing of its progress.
     */
    private void alarmStalled(long now) {
        for (Map.Entry<FarmMission, FarmMission.Step> under : worked()) {
            FarmMission m = under.getKey();
            Long moved = movedAt.get(m);
            if (under.getValue().kind() == FarmMission.Kind.MINE || moved == null || now - moved < STALL_MILLIS || !stalled.add(m)) {
                continue;
            }
            LOG.warn("[Outcome] {} has made no progress in {} min - SHORT: {}", m.name(), (now - moved) / 60_000, said.get(m));
            wake.accept("a mission making no progress");
        }
    }

    /** The missions said as stalled, not moved since: for whoever watches. */
    synchronized Set<FarmMission> stalled() {
        return Set.copyOf(stalled);
    }

    /**
     * The missions open in the register before a restart, each at its first step not done: once, at the first measure or
     * mission given - not before, so the register is opened where the garrison's configured it, not at its default.
     */
    /**
     * A mission the register holds in an older shape - fewer steps than it has now - given the steps it has now, from
     * its first: each of its rows the step that now has its place, PLANNED, and the ones it lacks added. A tree farm was
     * a flatten and a template; it's a flatten one lower, a slab and a template (operator, 2026-11-23), so one open or
     * built before is worked again from its flatten - what's done already measures done at once.
     */
    private static void reshape(ObjectiveBook book) {
        Map<String, List<io.github.jayessdeesea.minecraft.command.ObjectiveRecord>> rows = book.registers().objectives().stream()
                .filter(r -> r.state() != io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.SUPERSEDED)
                .collect(Collectors.groupingBy(io.github.jayessdeesea.minecraft.command.ObjectiveRecord::mission, LinkedHashMap::new, Collectors.toList()));
        rows.forEach((name, had) -> FarmMission.named(name).filter(m -> had.size() < m.steps().size()).ifPresent(m -> {
            String why = "its steps are now " + m.steps().stream().map(FarmMission.Step::task).toList();
            for (FarmMission.Step step : m.steps()) {
                Optional<io.github.jayessdeesea.minecraft.command.ObjectiveRecord> row = had.stream().filter(r -> r.step() == step.index()).findFirst();
                if (row.isPresent()) {
                    book.registers().retask(new io.github.jayessdeesea.minecraft.command.ObjectiveRecord(row.get().id(), name, step.index(), step.task(), m.chunkX(), m.chunkZ(),
                            Optional.empty(), io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.PLANNED, row.get().changedAt(), Optional.of(why)));
                } else {
                    book.registers().add(new io.github.jayessdeesea.minecraft.command.ObjectiveRecord(0, name, step.index(), step.task(), m.chunkX(), m.chunkZ(),
                            Optional.empty(), io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.PLANNED, had.getFirst().changedAt(), Optional.empty()));
                }
            }
            LOG.info("[Mission] {} given the steps it has now, from its first - it had {} of {}", name, had.size(), m.steps().size());
        }));
    }

    private void load() {
        if (loaded || book.isEmpty()) {
            return;
        }
        loaded = true;
        reshape(book.get());
        book.get().openMissions().forEach((name, step) -> FarmMission.named(name).ifPresent(m -> {
            missions.putIfAbsent(m, step);
            LOG.info("[Mission] {} carried on from the register, at step {}", m.name(), step + 1);
        }));
        // What was built before the restart: each mission the register holds with every step done.
        Map<String, List<io.github.jayessdeesea.minecraft.command.ObjectiveRecord>> rows = book.get().registers().objectives().stream()
                .collect(Collectors.groupingBy(io.github.jayessdeesea.minecraft.command.ObjectiveRecord::mission, LinkedHashMap::new, Collectors.toList()));
        rows.forEach((name, steps) -> FarmMission.named(name).filter(m -> !missions.containsKey(m) && steps.size() >= m.steps().size()
                && steps.stream().allMatch(r -> r.state() == io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.DONE)).ifPresent(m -> {
            built.removeIf(old -> old.chunkX() == m.chunkX() && old.chunkZ() == m.chunkZ());
            built.add(m);
        }));
        // The tree chunk a mission made before the restart: its template's row done, the last of them.
        treesMade = book.get().registers().objectives().stream()
                .filter(row -> row.state() == io.github.jayessdeesea.minecraft.command.ObjectiveRecord.State.DONE)
                .flatMap(row -> FarmMission.named(row.mission()).filter(m -> m.crop() == FarmMission.Crop.TREES
                        && m.steps().get(row.step()).kind() == FarmMission.Kind.TREE_TEMPLATE).stream())
                .map(m -> new TreeChunk(m.chunkX(), m.chunkZ(), m.level())).reduce((first, last) -> last);
    }
}
