package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Readiness;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The LLM echelons' imperative shell: decides <em>when</em> the platoon leader and each squad leader
 * think, runs their model calls off every soldier-facing thread, and hands each squad's
 * {@link SquadOrder} to {@code SoldierRunner}'s squad loop. Cadence follows docs/architecture.md's latency
 * architecture - the higher the echelon, the less often - plus events: a new objective, or a
 * <em>new</em> CANTCO from below (docs/military-doctrine.md §3), triggers a decision early, so
 * push-back is answered in seconds rather than at the next routine interval. A CANTCO that's still
 * standing after the echelon has answered it waits for the routine interval - re-asking every few
 * seconds about the same report just churns orders.
 *
 * <p>One platoon leader commands every squad in the {@link Roster} at once, so it can split the work
 * between them. A squad the operator attached to a player as an escort ({@code !escort}) is set
 * aside from the platoon leader until the next {@code !mission}; its objective follows the player
 * instead (see {@link SquadCommand}).
 *
 * <p>Every exchange is logged like a radio net - orders down, reports up - so a run reads as a
 * transcript of how doctrine-shaped communication plays out between LLM agents. Orders and
 * CANTCOs are also keyed out over the {@linkplain #setRadio radio}: in game chat, so anyone
 * watching in a Minecraft client sees the net too. SITREPs stay in the log - one every few seconds
 * would trip the server's chat spam kick.
 *
 * <p>A failed model call (malformed reply, model down) is logged loudly and the squad keeps its
 * last order - doctrine's "soldiers keep executing the last order" - and the next scheduled
 * decision tries again.
 */
@Component
public class ChainOfCommand implements DisposableBean {

    private static final Logger LOG = LogManager.getLogger(ChainOfCommand.class);

    /** Least time between decisions, however many CANTCOs arrive - stops a stuck soldier spamming the model. */
    private static final long MIN_GAP_MILLIS = 8_000;
    /**
     * The least time between the platoon leader's decisions that a request or a CANTCO wakes it for:
     * it thinks on a slow clock - live, when every new contact woke it, it was asked every ten seconds
     * at night to rewrite the same order. Contact is the squad's drill to handle, and wakes nobody.
     */
    private static final long WAKE_GAP_MILLIS = 60_000;
    /** Vanilla chat's own limit is 256 characters. */
    static final int CHAT_LINE_LIMIT = 250;

    /**
     * One squad's SITREP for a cycle: its soldiers, its terrain (surveyed only if its leader
     * decides), its principal, what it can see to gather (surveyed only if the platoon leader decides), and
     * how its patrol base and its task stand.
     */
    public record Sitrep(String squad, List<SoldierStatus> soldiers, Supplier<TerrainSurvey> terrain,
                         Optional<Position> principalAt, Supplier<List<ResourceSite>> resources,
                         boolean holdingBase, OptionalInt baseBlocksLeft,
                         Optional<Progress> progress,
                         Optional<Subsistence.Status> classOne, Optional<String> classFour) {

        /** A SITREP with no Class IV request. */
        public Sitrep(String squad, List<SoldierStatus> soldiers, Supplier<TerrainSurvey> terrain, Optional<Position> principalAt,
                      Supplier<List<ResourceSite>> resources, boolean holdingBase,
                      OptionalInt baseBlocksLeft, Optional<Progress> progress, Optional<Subsistence.Status> classOne) {
            this(squad, soldiers, terrain, principalAt, resources, holdingBase, baseBlocksLeft, progress, classOne, Optional.empty());
        }

        /** A SITREP with no food measured. */
        public Sitrep(String squad, List<SoldierStatus> soldiers, Supplier<TerrainSurvey> terrain, Optional<Position> principalAt,
                      Supplier<List<ResourceSite>> resources, boolean holdingBase,
                      OptionalInt baseBlocksLeft, Optional<Progress> progress) {
            this(squad, soldiers, terrain, principalAt, resources, holdingBase, baseBlocksLeft, progress, Optional.empty());
        }

        /** A SITREP with no task's progress measured. */
        public Sitrep(String squad, List<SoldierStatus> soldiers, Supplier<TerrainSurvey> terrain, Optional<Position> principalAt,
                      Supplier<List<ResourceSite>> resources, boolean holdingBase,
                      OptionalInt baseBlocksLeft) {
            this(squad, soldiers, terrain, principalAt, resources, holdingBase, baseBlocksLeft, Optional.empty(), Optional.empty());
        }
    }

    private final PlatoonLeader platoonLeader;
    private final Map<String, SquadCommand> squads = new LinkedHashMap<>();
    /** What each squad last saw round it - the survey the platoon leader was last shown. */
    private final Map<String, List<ResourceSite>> resourcesSeen =
            new ConcurrentHashMap<>();
    private final ExecutorService platoonLeaderThread;
    /** The clock and threads everything here runs on: the real ones, or a simulation's. */
    private final Clockwork clockwork;
    private final AtomicBoolean platoonLeaderBusy = new AtomicBoolean();
    /** Bumped on each operator order, so a platoon leader's decision already in flight is known to be stale. */
    private final AtomicInteger missionVersion = new AtomicInteger();

    private volatile long lastPlatoonLeaderMillis = Long.MIN_VALUE / 2;

    /**
     * Why the platoon leader is to decide next, if it is (backlog/fleet-command.md: it wakes on events,
     * not on a clock, as a commander is told what he's asked to be told): an operator order, dawn,
     * dusk and dusk near, a squad with no objective, an escort attached or ended - at once - and a
     * squad's task finished or stalled, its Class I RED, or its squad leader's CANTCO, at most a
     * minute after the last decision. Each wake is logged with its cause ({@code [PL] wakes:}).
     * Otherwise it sleeps, and the platoon runs on its standing orders.
     */
    private final java.util.Set<String> wakeCauses = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private volatile boolean wakeAtOnce;

    /** Asks the platoon leader to decide, for {@code cause}; {@code atOnce} at the next SITREP, else at most a minute after its last. */
    private void wake(String cause, boolean atOnce) {
        wakeCauses.add(cause);
        wakeAtOnce |= atOnce;
    }
    private volatile Consumer<String> radio;
    /** The ground's height at an x and z, looked for near a height - the world's, as the soldiers see it. */
    private volatile Ground ground = (x, z, nearY) -> OptionalInt.empty();

    /** Where a body stands at ({@code x}, {@code z}): the highest standing cell within reach of {@code nearY}, if the world shows one. */
    @FunctionalInterface
    public interface Ground {
        OptionalInt heightAt(int x, int z, int nearY);

        /** The column's surface under open sky, if the world shows one: none by default. */
        default OptionalInt surfaceAt(int x, int z, int nearY) {
            return OptionalInt.empty();
        }
    }

    /** A ground this far under the surface is a cave's floor or a mine's, not where the objective is. */
    static final int UNDERGROUND = 6;
    private volatile TimeOfDay time;

    /**
     * The operator's standing rule, when set: no squad spends the night in the open. Set it where a
     * mission says so - a garrison - and leave it off where a scenario may need a squad out at night.
     */
    private final boolean shelterAtNight;
    /** Every echelon's model decisions, timed and alarmed on. */
    private final ModelCalls modelCalls = new ModelCalls();
    /**
     * How long before dark the rule starts: none - at nightfall. A hut goes up in a minute or two,
     * and the first monsters of the night take longer than that to spawn and walk in, so the last
     * ninety seconds of the day are worked rather than spent waiting in a hut (operator, 2026-11-08).
     */
    static final long SHELTER_LEAD_TICKS = 0;
    /** How long before dark the platoon leader is asked anyway: time to plan the night before it starts. */
    static final long DUSK_DECIDE_TICKS = 1800;

    /**
     * The app's. Each squad leader decides as {@code decidesWith} says (minecraft.squad-leader.decides-with):
     * "drills", by standing rules ({@link DrillSquadLeader}; backlog/fleet-command.md), or "model", the
     * squad leader's model - anything else fails at the start, loudly.
     */
    @Autowired
    public ChainOfCommand(PlatoonLeader platoonLeader, @Qualifier("squadLeaderChatClient") ChatClient squadLeaderClient,
                          SquadLeaderPrompts squadLeaderPrompts, Roster roster,
                          @Value("${minecraft.platoon-leader.shelter-at-night}") boolean shelterAtNight,
                          @Value("${minecraft.squad-leader.decides-with}") String decidesWith) {
        this(platoonLeader, squadLeaders(decidesWith, squadLeaderClient, squadLeaderPrompts), roster, shelterAtNight, Clockwork.REAL);
    }

    /** As the app's with the squad leader's model, on {@code clockwork}'s time and threads. */
    ChainOfCommand(PlatoonLeader platoonLeader, ChatClient squadLeaderClient,
                   SquadLeaderPrompts squadLeaderPrompts, Roster roster,
                   boolean shelterAtNight, Clockwork clockwork) {
        this(platoonLeader, squadLeaders("model", squadLeaderClient, squadLeaderPrompts), roster, shelterAtNight, clockwork);
    }

    /** As the app's, each squad's leader made by {@code squadLeaders}, on {@code clockwork}'s time and threads. */
    ChainOfCommand(PlatoonLeader platoonLeader, java.util.function.Function<String, SquadLeader> squadLeaders, Roster roster,
                   boolean shelterAtNight, Clockwork clockwork) {
        this.platoonLeader = platoonLeader;
        this.shelterAtNight = shelterAtNight;
        this.clockwork = clockwork;
        this.platoonLeaderThread = clockwork.thread("platoon-leader");
        for (Roster.Squad squad : roster.squads()) {
            squads.put(squad.name(), new SquadCommand(squad.name(), squadLeaders.apply(squad.name()), this::transmit,
                    modelCalls, clockwork));
        }
    }

    /** What each squad leader is made as, by {@code decidesWith}: "drills" or "model". */
    static java.util.function.Function<String, SquadLeader> squadLeaders(String decidesWith, ChatClient client, SquadLeaderPrompts prompts) {
        return switch (decidesWith) {
            case "drills" -> DrillSquadLeader::new;
            case "model" -> name -> new SpringAiSquadLeader(name, client, prompts);
            default -> throw new IllegalArgumentException("minecraft.squad-leader.decides-with is \"" + decidesWith
                    + "\": \"drills\" or \"model\"");
        };
    }

    /** The time, in milliseconds, on the clock everything here runs on. */
    long millis() {
        return clockwork.millis();
    }

    /**
     * Where radio traffic is keyed out - the radio operator's connection. Until one is set (no
     * soldier connected yet), traffic is only logged.
     */
    /** How the platoon leader's objectives find the ground: their heights are its guesses, the world's are known. */
    public void setGround(Ground ground) {
        this.ground = ground;
    }

    /** Whether the night rule holds now: under {@link #shelterAtNight}, from just before dark until day. */
    boolean sheltering() {
        TimeOfDay now = time;
        return shelterAtNight && now != null && (now.dark() || now.ticksUntilDark() <= SHELTER_LEAD_TICKS);
    }

    /**
     * {@code objective}, or - at night, under {@link #shelterAtNight}, when the platoon leader's order
     * would leave a squad in the open or take it out of the base it is sealed in - the patrol base it was already
     * ordered to build, or else one where the squad's point man stands. A guardrail, and a loud one: the platoon leader's own order is logged beside it.
     */
    Objective sheltered(Objective objective, SquadStatus squad) {
        TimeOfDay now = time;
        if (!shelterAtNight || now == null || !(now.dark() || now.ticksUntilDark() <= SHELTER_LEAD_TICKS)
                || squad.soldiers().isEmpty()) {
            return objective;
        }
        // The night isn't wasted: every squad goes down the camp's mine (operator, 2026-11-13) -
        // whatever the platoon leader orders, a patrol base included: seeing the squad out of one, it
        // ordered one two minutes into the shift. Its shift ends in time to climb out at sunrise.
        Optional<Objective> nightShift = nightShift(squad.squadName());
        if (nightShift.isPresent() && surfacing()) {
            return objective;
        }
        if (nightShift.isPresent() && objective.equals(nightShift.get())) {
            return objective;
        }
        // With no camp to go down the mine of, a squad already sealed in is the one most to keep there,
        // and a base the platoon leader orders is the night's.
        Position at = squad.soldiers().getFirst().position();
        if (nightShift.isEmpty() && objective.patrolBase()) {
            return objective;
        }
        if (nightShift.isPresent()) {
            LOG.warn("[PL -> {}] at or near night, ordered '{}' ({}) - the operator's rule sends it down the camp's mine instead, at {}",
                    squad.squadName(), objective.mission(), objective.task(), at(nightShift.get().location()));
            return nightShift.get();
        }
        // A base already ordered is the one to finish: sited again wherever the point man stands at
        // each decision, a squad starts hut after hut and finishes none before dawn.
        Optional<Objective> standing = squads.get(squad.squadName()).objective().filter(Objective::patrolBase);
        if (standing.isPresent()) {
            LOG.warn("[PL -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule keeps it on the patrol base it has",
                    squad.squadName(), objective.mission(), objective.task());
            return standing.get();
        }
        Objective shelter = grounded(new Objective("Build a patrol base where you stand and spend the night in it",
                new Position(Math.floor(at.x()), at.y(), Math.floor(at.z())), "Squad safe from monsters until dawn",
                new Objective.Task.PatrolBase()), squad.soldiers());
        LOG.warn("[PL -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule shelters it instead, at {}",
                squad.squadName(), objective.mission(), objective.task(), at(shelter.location()));
        return shelter;
    }

    /** Under the operator's day rule, every squad builds the keep by day (minecraft.platoon-leader.keep-by-day). */
    private volatile boolean keepByDay;

    @Autowired
    void setKeepByDay(@Value("${minecraft.platoon-leader.keep-by-day:false}") boolean keepByDay) {
        this.keepByDay = keepByDay;
    }

    /**
     * {@code objective}, or - by day, under {@link #keepByDay} - the keep (command.Keep): the
     * operator's order for every squad, whatever the platoon leader would have it do - but for the
     * platoon leader's answer to a request the squad raised: WOOD to its Class IV RED, FARM to its Class I
     * RED. A request outranks the standing rule, or it's never answered: live, the keep's builders
     * asked for logs all day and the rule put every squad back on the keep. Loud, like the night's
     * rule, which still comes first at dusk.
     */
    Objective byDay(Objective objective, SquadStatus squad) {
        return byDay(objective, squad, Optional.empty(), List.of());
    }

    /**
     * As {@link #byDay(Objective, SquadStatus)}, a squad the flatten doesn't need sent to the force's
     * {@code shortfall} (PlatoonSergeant#shortfall) - the farm, the trees in {@code woods} nearest it,
     * the keep's mine - not the platoon leader's objective for it.
     */
    Objective byDay(Objective objective, SquadStatus squad, Optional<PlatoonSergeant.Shortfall> shortfall, List<ResourceSite> woods) {
        if (!keepByDay || time == null || (time.dark() || sheltering()) && !surfacing()) {
            return objective;
        }
        // The farm squad keeps the camp's farm, not the ground - whatever it's given: its standing job by day.
        if (farmSquad.filter(squad.squadName()::equals).isPresent()) {
            Foothold base = foothold;
            Position at = base != null ? base.camp() : objective.location();
            if (!(objective.task() instanceof Objective.Task.None) || !objective.location().equals(at)) {
                LOG.info("[PL -> {}] by day, the farm squad: keeps the camp's farm, not '{}'", squad.squadName(), objective.mission());
            }
            return new Objective("Keep the camp's farm", at, "Food for the force: the farm squad's standing job", new Objective.Task.None());
        }
        // Only as many squads on the ground as its level wants (backlog/flatten-campaign.md): the rest
        // released - to the platoon leader's own objective for them, or the camp's work where that's
        // the ground too - and called back as the levels widen. Live, every squad stood on a level
        // of a few blocks, a third of the crew's time spent with nothing to dig.
        List<String> builders = squads.keySet().stream().filter(n -> farmSquad.filter(n::equals).isEmpty()).toList();
        int wanted = flattenBuilders;
        int perSquad = Math.max(1, squad.soldiers().size());
        int squadsWanted = wanted == Integer.MAX_VALUE ? builders.size() : Math.max(1, (wanted + perSquad - 1) / perSquad);
        if (builders.indexOf(squad.squadName()) >= squadsWanted) {
            Optional<Objective> needed = shortfall.flatMap(s -> toShortfall(s, squad, woods, wanted));
            if (needed.isPresent()) {
                if (!needed.get().equals(squad.standing().orElse(null))) {
                    LOG.info("[PL -> {}] released from the flatten ({} builder(s) wanted): to the force's {} shortfall - '{}'",
                            squad.squadName(), wanted, shortfall.get(), needed.get().mission());
                }
                return needed.get();
            }
            if (!(objective.task() instanceof Objective.Task.Keep)) {
                return objective;
            }
            Foothold base = foothold;
            return new Objective("Work the camp while the ground's level is small", base != null ? base.camp() : objective.location(),
                    "Released from the flatten: its level wants " + wanted + " builder(s)", new Objective.Task.None());
        }
        if (objective.task() instanceof Objective.Task.Keep) {
            return objective;
        }
        boolean answersWood = objective.task() instanceof Objective.Task.Gather g && g.resource() == Resource.WOOD && squad.classFour().isPresent();
        boolean answersFood = objective.task() instanceof Objective.Task.Farm
                && squad.classOne().filter(c -> c.rating() == Subsistence.Rating.RED).isPresent();
        if (answersWood || answersFood) {
            LOG.info("[PL -> {}] by day, ordered '{}' ({}) answering the squad's {} request: it stands over the keep rule",
                    squad.squadName(), objective.mission(), objective.task(), answersWood ? "Class IV" : "Class I");
            return objective;
        }
        Flatten site = flatten;
        Objective keep = new Objective("Flatten " + site.describe(),
                spread(site.centre(), squad.squadName()), "The ground the operator ordered flattened", new Objective.Task.Keep());
        LOG.warn("[PL -> {}] by day, ordered '{}' ({}) - the operator's rule puts it on the keep instead",
                squad.squadName(), objective.mission(), objective.task());
        return keep;
    }

    /** The work that makes good {@code shortfall} for a squad released from the flatten, if there's somewhere to do it. */
    private Optional<Objective> toShortfall(PlatoonSergeant.Shortfall shortfall, SquadStatus squad, List<ResourceSite> woods, int wanted) {
        String why = "Released from the flatten (its level wants " + wanted + " builder(s)): the force is short of ";
        Foothold base = foothold;
        return switch (shortfall) {
            case FOOD -> Optional.ofNullable(base).map(b -> new Objective("Work the camp's farm", b.camp(), why + "food", new Objective.Task.Farm()));
            case IRON -> Optional.of(new Objective("Mine the keep's mine for iron", KEEP_MINE, why + "iron", new Objective.Task.Mine()));
            case WOOD -> {
                Position from = squad.soldiers().isEmpty() ? flatten.centre() : squad.soldiers().getFirst().position();
                yield woods.stream().filter(w -> w.resource() == Resource.WOOD)
                        .min(java.util.Comparator.comparingDouble(w -> Math.hypot(w.location().x() - from.x(), w.location().z() - from.z())))
                        .map(w -> new Objective("Gather wood", w.location(), why + "logs", new Objective.Task.Gather(Resource.WOOD)));
            }
        };
    }

    /**
     * How long before sunrise the night shift ends: the climb out of the mine at y=16 and the walk
     * to the day's work, about 30 seconds - so the squads come out as the sun burns the undead off
     * the surface (operator, 2026-11-13: come up right before dawn).
     */
    static final long SURFACE_LEAD_TICKS = 600;

    /** Whether the night shift is climbing out: dark, and sunrise within {@link #SURFACE_LEAD_TICKS}. */
    boolean surfacing() {
        TimeOfDay now = time;
        return now != null && now.dark() && now.ticksUntilLight() <= SURFACE_LEAD_TICKS;
    }

    /** Where the camp's mine is cut, from the camp's centre: inside its wall, clear of the huts. */
    static final int MINE_HEAD_DX = 4;
    static final int MINE_HEAD_DZ = -4;
    /**
     * The keep's mine head: off its north-west corner, clear of the slab, its depot and the west
     * borrow pit (x -25..-20, z -17..17).
     */
    static final Position KEEP_MINE = new Position(Keep.MIN - 10, ChainOfCommand.MINE_DEPTH, Keep.MIN - 8);
    /** How deep the night shift cuts: the iron, commonest round y=16. */
    static final int MINE_DEPTH = 16;

    /**
     * The night's work for {@code squad} at the camp's mine - every squad's, its stairs shared
     * (operator, 2026-11-13: both squads work the mine at night) - or empty with no camp measured yet.
     */
    Optional<Objective> nightShift(String squad) {
        Foothold camp = foothold;
        if (camp == null || squads.isEmpty()) {
            return Optional.empty();
        }
        // Building the keep by day, the night's mine is the keep's own, beside it: from the camp's,
        // 65 blocks off and 48 down, the climb out and the walk to the keep were two-fifths of the
        // day (field151; operator, 2026-11-13: cutting a second mine is fine).
        if (keepByDay) {
            return Optional.of(new Objective("Cut the keep's mine down to the iron through the night", KEEP_MINE,
                    "Stone for the keep's slab, and the night spent working underground beside it", new Objective.Task.Mine()));
        }
        Position c = camp.camp();
        return Optional.of(new Objective("Cut the camp's mine down to the iron through the night",
                new Position(Math.floor(c.x()) + MINE_HEAD_DX, MINE_DEPTH, Math.floor(c.z()) + MINE_HEAD_DZ),
                "Stone and iron for the camp, and the night spent working underground", new Objective.Task.Mine()));
    }

    /**
     * {@code objective} with its height the ground's at its x and z, looked for near the squad's
     * middle height: the platoon leader guesses heights, and a guess copied from elsewhere in its prompt
     * lands an objective in the sky or underground. MINE keeps its own: that y is a depth.
     */
    Objective grounded(Objective objective, List<SoldierStatus> soldiers) {
        if (objective.mine() || soldiers.isEmpty()) {
            return objective;
        }
        Position at = objective.location();
        // Near the squad's middle height, not its first soldier's: one stranded up a tower or down a
        // hole would take every objective there with it.
        double[] heights = soldiers.stream().mapToDouble(s -> s.position().y()).sorted().toArray();
        int nearY = (int) Math.floor(heights[heights.length / 2]);
        int x = (int) Math.floor(at.x()), z = (int) Math.floor(at.z());
        OptionalInt height = ground.heightAt(x, z, nearY);
        // From a squad down a mine, the ground near its height is the shaft's floor or a cave's: the
        // objective goes on the surface when that's well above. Live, a wood-gathering objective at
        // the keep mine's head went to the shaft's bottom, 40 blocks down, and the squad found no tree.
        OptionalInt surface = ground.surfaceAt(x, z, nearY);
        if (surface.isPresent() && (height.isEmpty() || surface.getAsInt() - height.getAsInt() > UNDERGROUND)) {
            height = surface;
        }
        if (height.isEmpty() || height.getAsInt() == (int) Math.floor(at.y())) {
            return objective;
        }
        LOG.info("[PL] objective at x={} z={} put on the ground at y={}, not the y={} given", (int) Math.floor(at.x()),
                (int) Math.floor(at.z()), height.getAsInt(), (int) Math.floor(at.y()));
        return new Objective(objective.mission(), new Position(at.x(), height.getAsInt(), at.z()), objective.intent(), objective.task());
    }

    public void setRadio(Consumer<String> radio) {
        this.radio = radio;
    }

    /**
     * A new mission from a human operator (see {@code OperatorOrders}): the platoon leader takes it and
     * re-plans at the next SITREP rather than at its routine interval, for every squad - an escort
     * ends. Acknowledged on the radio.
     */
    public void onOperatorMission(String operator, String mission) {
        platoonLeader.receiveMission(mission);
        squads.values().forEach(squad -> squad.escort(null));
        int version = missionVersion.incrementAndGet();
        wake("the operator's mission", true);
        OrderTrace trace = trace(operator, "!mission " + mission, version);
        LOG.info("[{} -> PL] MISSION ({}): {}", operator, trace.id, mission);
        transmit("[PL -> " + operator + "] WILCO (" + trace.id + "): " + mission);
    }

    /**
     * The ground the squads flatten by day (operator, 2026-11-15; command.Flatten): {@code site} in
     * place of the last, from the next squad cycle. Acknowledged on the radio.
     */
    public void onOperatorFlatten(String operator, Flatten site) {
        Flatten was = flatten;
        flatten = site;
        int version = missionVersion.incrementAndGet();
        wake("the operator's ground to flatten", true);
        OrderTrace trace = trace(operator, "!flatten " + site.describe(), version);
        LOG.info("[{} -> PL] FLATTEN ({}): {}, in place of {}", operator, trace.id, site.describe(), was.describe());
        transmit("[PL -> " + operator + "] WILCO (" + trace.id + "): flatten " + site.describe());
    }

    /**
     * The squad that keeps the camp's farm by day as its standing job (operator, 2026-11-15: a
     * dedicated squad for the farming), so no other squad's hands are taken off the work for it -
     * empty for none (minecraft.farm-squad).
     */
    private volatile Optional<String> farmSquad = Optional.empty();

    @Autowired
    void setFarmSquad(@Value("${minecraft.farm-squad:}") String squad) {
        this.farmSquad = Optional.of(squad.strip()).filter(s -> !s.isEmpty());
    }

    /** How far from a shared objective each squad's own point is: clear of the others' formations. */
    static final double SPREAD = 4;

    /**
     * {@code at} for {@code squadName} among every squad given it: a corner of its own round it, by the
     * squad's place in the order of battle, so their point men don't all make for one cell. Live, four
     * squads sent to the keep's middle stood in one body's width of each other all evening.
     */
    Position spread(Position at, String squadName) {
        int k = Math.max(0, List.copyOf(squads.keySet()).indexOf(squadName));
        double dx = k % 2 == 0 ? -SPREAD : SPREAD, dz = k % 4 < 2 ? -SPREAD : SPREAD;
        return new Position(at.x() + dx, at.y(), at.z() + dz);
    }

    /** Builders the ground's level under way wants, as its foreman last planned it; {@code Integer.MAX_VALUE} before the first. */
    private volatile int flattenBuilders = Integer.MAX_VALUE;

    /** The foreman's word on its new level: {@code builders} wanted on it, the squads to send sized from it. */
    public void onFlattenCrew(int builders) {
        if (builders != flattenBuilders) {
            LOG.info("[PL] the ground's level wants {} builder(s) - squads on it sized to that", builders);
        }
        flattenBuilders = builders;
    }

    /** The farm squad, if one is named. */
    public Optional<String> farmSquad() {
        return farmSquad;
    }

    /** The ground the squads flatten by day: the operator's last {@code !flatten}, or the configured site. */
    public Flatten flatten() {
        return flatten;
    }

    /** The ground to flatten - the configured site, then the operator's (minecraft.flatten: "x1 z1 x2 z2 y"). */
    private volatile Flatten flatten = Flatten.KEEP;

    @Autowired
    void setFlatten(@Value("${minecraft.flatten:-16 -16 16 16 63}") String site) {
        this.flatten = Flatten.parse(site).orElseThrow(() -> new IllegalArgumentException(
                "minecraft.flatten is \"x1 z1 x2 z2 y\": " + site));
    }

    /** An order from the operator that isn't one: answered on the radio, with what the orders are. */
    public void onOperatorUnknown(String operator, String command) {
        LOG.warn("[{} -> PL] {} - not an order", operator, command);
        transmit("[PL -> " + operator + "] SAY AGAIN: " + command + " is not an order. Orders: " + OperatorOrders.MISSION
                + " <what to do>, " + OperatorOrders.ESCORT + " [player], " + OperatorOrders.FLATTEN
                + " <x1> <z1> <x2> <z2> <y>; anything else is passed to the platoon leader as a remark");
    }

    /** Operator orders being followed down the chain - each is dropped once carried out or superseded. */
    private final List<OrderTrace> traces = new CopyOnWriteArrayList<>();
    private final AtomicInteger traceIds = new AtomicInteger();

    /** A trace for the operator's new order: the ones before it, not yet carried out, are superseded. */
    private OrderTrace trace(String operator, String order, int version) {
        traces.forEach(t -> t.supersede().ifPresent(line -> traced(operator, line)));
        traces.removeIf(OrderTrace::closed);
        OrderTrace trace = new OrderTrace("OP-" + traceIds.incrementAndGet(), operator, order, clockwork.millis(), version);
        traces.add(trace);
        return trace;
    }

    /** A hop of an order's trace: in the log, and on the radio to the operator who gave it. */
    private void traced(String operator, String line) {
        if (line.startsWith("ALARM")) {
            LOG.warn("[Trace] {}", line);
        } else {
            LOG.info("[Trace] {}", line);
        }
        transmit("[PL -> " + operator + "] " + line);
    }

    /**
     * Something the operator said that isn't an order: the platoon leader hears it at its next decision,
     * brought forward - an operator who speaks up has usually seen something. Acknowledged on the radio.
     */
    public void onOperatorRemark(String operator, String remark) {
        platoonLeader.receiveRemark(remark);
        wake("the operator's remark", true);
        LOG.info("[{} -> PL] REMARK: {}", operator, remark);
        transmit("[PL -> " + operator + "] ROGER: " + remark);
    }

    /**
     * The operator attaches the first squad in the {@link Roster} to {@code principal} as its
     * escort (docs/military-doctrine.md §10): until the next {@code !mission}, the platoon leader leaves
     * it out and its squad leader's objective follows the principal. Acknowledged on the radio.
     */
    public void onOperatorEscort(String operator, String principal) {
        SquadCommand escort = squads.values().iterator().next();
        escort.escort(principal);
        int version = missionVersion.incrementAndGet(); // a platoon leader's decision in flight may have included this squad
        wake("an escort attached: the others are re-planned without it", true);
        OrderTrace trace = trace(operator, "!escort " + principal, version);
        LOG.info("[{} -> {}] ESCORT ({}): {}", operator, escort.name, trace.id, principal);
        transmit("[" + escort.name + " -> " + operator + "] WILCO (" + trace.id + "): escorting " + principal);
        trace.direct(escort.name, Optional.of(new Objective.Task.None()), clockwork.millis())
                .forEach(line -> traced(operator, line));
    }

    /** The camp's progress through the lodgment's phases, measured - the platoon leader is told, and the log when it moves. */
    public void onFoothold(Foothold foothold) {
        String standing = foothold.standards().stream().map(s -> s.name() + (s.met() ? " met" : ": " + s.status()))
                .collect(Collectors.joining("; "));
        if (!standing.equals(lastFoothold)) {
            LOG.info("[Foothold] phase {}: {}", foothold.nextPhase().isPresent() ? foothold.nextPhase().getAsInt() : "all met", standing);
            lastFoothold = standing;
        }
        platoonLeader.receiveFoothold(foothold);
        this.foothold = foothold;
    }

    private volatile String lastFoothold = "";
    /** The home base's progress, last measured - a soldier's thread writes, the squads' read. */
    private volatile Foothold foothold;

    /** The camp as last measured: empty before the first measure, or with no camp. */
    public Optional<Foothold> foothold() {
        return Optional.ofNullable(foothold);
    }
    /** Each squad's readiness as last logged, so only a change of it is. */
    private final Map<String, Readiness> readiness = new ConcurrentHashMap<>();
    /** A squad's readiness seen since the last logged, and in how many SITREPs running: a change is logged once it holds. */
    private final Map<String, Readiness> readinessSeen = new ConcurrentHashMap<>();
    private final Map<String, Integer> readinessHeld = new ConcurrentHashMap<>();
    /** SITREPs a new readiness holds before it's logged: soldiers ranging in and out of the camp flip it back and forth. */
    static final int READINESS_HOLDS = 3;

    /** Logs each squad's readiness where it changed: safety where it stands, and its C-rating. */
    private void logReadiness(List<Sitrep> sitreps) {
        for (Sitrep sitrep : sitreps) {
            Readiness now = Readiness.of(
                    new SquadStatus(sitrep.squad(), squads.get(sitrep.squad()).report(), sitrep.soldiers(), List.of(), Optional.empty(),
                            sitrep.holdingBase()), Optional.ofNullable(foothold));
            Readiness was = readiness.get(sitrep.squad());
            // A change of state, not of the counts behind it: hunger and health move every few seconds.
            boolean changed = was == null || now.safety() != was.safety() || now.rating() != was.rating();
            Readiness seen = readinessSeen.put(sitrep.squad(), now);
            boolean same = seen != null && seen.safety() == now.safety() && seen.rating() == now.rating();
            int held = same ? readinessHeld.merge(sitrep.squad(), 1, Integer::sum) : 1;
            if (!same) {
                readinessHeld.put(sitrep.squad(), 1);
            }
            if (changed && (was == null || held >= READINESS_HOLDS)) {
                readiness.put(sitrep.squad(), now);
                LOG.info("[Readiness] {}: {}, C-{}{}", sitrep.squad(), now.safety(), now.rating(),
                        now.shortfall().isEmpty() ? "" : " - " + now.shortfall());
            }
        }
    }

    /** Every request going up the chain, timed from raised to answered (docs/military-doctrine.md § 4). */
    private final RequestLedger requests = new RequestLedger();
    /** The order each soldier held when its CANTCO went up: a different one is the squad leader's answer. */
    private final Map<String, Order> cantcoOrders = new ConcurrentHashMap<>();

    /**
     * Requests raised and settled from this cycle's SITREPs: each soldier's CANTCO to its squad
     * leader, each squad's finished or stalled task and its Class I RED to the platoon leader. Overdue ones alarm.
     */
    private void trackRequests(List<Sitrep> sitreps, long now) {
        for (Sitrep sitrep : sitreps) {
            for (SoldierStatus soldier : sitrep.soldiers()) {
                String key = "soldier:" + soldier.name();
                if (soldier.report() instanceof Report.Cantco(String reason)) {
                    Order held = cantcoOrders.putIfAbsent(soldier.name(), soldier.order());
                    if (held != null && answers(held, soldier.order())) {
                        requests.answer(key, now).ifPresent(LOG::info);
                        cantcoOrders.put(soldier.name(), soldier.order());
                    }
                    requests.raise(key, RequestLedger.Echelon.SQUAD_LEADER, soldier.name() + " of " + sitrep.squad(), "CANTCO: " + reason, now)
                            .ifPresent(LOG::info);
                } else if (cantcoOrders.remove(soldier.name()) instanceof Order held) {
                    if (!answers(held, soldier.order())) {
                        requests.settle(key);
                    } else {
                        requests.answer(key, now).ifPresent(LOG::info);
                    }
                }
            }
            String task = sitrep.squad() + ":task";
            Optional<Progress> finished = sitrep.progress()
                    .filter(p -> p.state() != Progress.State.UNDER_WAY);
            if (finished.isPresent()) {
                requests.raise(task, RequestLedger.Echelon.PLATOON_LEADER, sitrep.squad(), "task " + finished.get().state() + ": " + finished.get().detail(), now)
                        .ifPresent(line -> {
                            LOG.info(line);
                            wake(sitrep.squad() + "'s task " + finished.get().state(), false);
                        });
            } else {
                requests.settle(task);
            }
            String food = sitrep.squad() + ":food";
            Optional<Subsistence.Status> red = sitrep.classOne()
                    .filter(c -> c.rating() == Subsistence.Rating.RED);
            if (red.isPresent()) {
                requests.raise(food, RequestLedger.Echelon.PLATOON_LEADER, sitrep.squad(), red.get().line(), now).ifPresent(line -> {
                    LOG.info(line);
                    wake(sitrep.squad() + "'s Class I RED", false);
                });
            } else {
                requests.settle(food);
            }
        }
        requests.alarms(now).forEach(LOG::warn);
    }

    /** How far a soldier's ordered place has to move to be a new order: a formation's places shift a block or two every cycle. */
    static final double ANSWER_MOVE = 4;

    /**
     * Whether {@code now} answers a CANTCO raised under {@code held}: another kind of order, or a
     * place more than {@link #ANSWER_MOVE} away - not the cycle-to-cycle shuffle of a formation.
     */
    static boolean answers(Order held, Order now) {
        if (held instanceof Order.MoveTo a && now instanceof Order.MoveTo b) {
            return Math.hypot(a.x() - b.x(), a.z() - b.z()) > ANSWER_MOVE || Math.abs(a.y() - b.y()) > ANSWER_MOVE;
        }
        return held.getClass() != now.getClass();
    }

    /** The requests' part of the dawn review: each echelon's answers and how fast, and what's still open. */
    public List<String> requestReview() {
        return requests.review(clockwork.millis());
    }

    /** A soldier saw what the stockpile holds - the platoon leader is told, for its next decision. */
    public void onStockpile(Stockpile stockpile) {
        this.stockpile = stockpile;
        platoonLeader.receiveStockpile(stockpile);
    }

    private volatile Stockpile stockpile;

    /** What the stockpile held when a soldier last looked in it, if anyone has. */
    public Optional<Stockpile> stockpile() {
        return Optional.ofNullable(stockpile);
    }

    /** The server sent the time of day - the platoon leader plans around nightfall. Dusk and dawn are logged. */
    public void onTime(TimeOfDay time) {
        TimeOfDay last = this.time;
        this.time = time;
        if (last == null || last.dark() != time.dark()) {
            LOG.info("[Clock] {} - tick {} of the day, the chain of command told", time.dark() ? "NIGHT" : "DAY", time.ticks());
            wake(time.dark() ? "dusk" : "dawn", true); // they change what every squad should be doing
        }
        boolean nearDark = !time.dark() && time.ticksUntilDark() <= DUSK_DECIDE_TICKS;
        if (last != null && nearDark && !(!last.dark() && last.ticksUntilDark() <= DUSK_DECIDE_TICKS)) {
            LOG.info("[Clock] DUSK NEAR - dark in {} ticks, the platoon leader decides now", time.ticksUntilDark());
            wake("dusk near", true);
        }
        platoonLeader.receiveTime(time);
        squads.values().forEach(squad -> squad.receiveTime(time));
    }

    /** Stops the platoon leader's and the squad leaders' threads - a decision in flight is abandoned. */
    @Override
    public void destroy() {
        platoonLeaderThread.shutdownNow();
        squads.values().forEach(SquadCommand::stop);
    }

    /** Day or night as a decision was told it, for the log line that records the decision. */
    static String phase(TimeOfDay time) {
        return time == null ? "time unknown" : time.dark() ? "night" : "day";
    }

    /** The time of day the server last sent, if it has. */
    public Optional<TimeOfDay> time() {
        return Optional.ofNullable(time);
    }

    /** Who {@code squad} is escorting, if anyone. */
    public Optional<String> escort(String squad) {
        return squads.get(squad).escort();
    }

    /** The objective's task, as the OPORD says it - nothing when there's none. */
    private static String task(Objective objective) {
        return objective.patrolBase() ? ", patrol base"
                : objective.gather().map(r -> ", gather " + r.name().toLowerCase(Locale.ROOT)).orElse("");
    }

    /** The platoon leader's objective for {@code squad}, once it has one. */
    public Optional<Objective> objective(String squad) {
        return squads.get(squad).objective();
    }

    /** {@code squad}'s objective, when it has the squad build a patrol base (docs/military-doctrine.md §14). */
    public Optional<Objective> patrolBaseObjective(String squad) {
        return squads.get(squad).patrolBaseObjective();
    }

    /** {@code squad}'s objective, when it has the squad gather a material (docs/military-doctrine.md §12). */
    public Optional<Objective> treeFarmObjective(String squad) {
        return squads.get(squad).treeFarmObjective();
    }

    public Optional<Objective> fortifyObjective(String squad) {
        return squads.get(squad).fortifyObjective();
    }

    public Optional<Objective> mineObjective(String squad) {
        return squads.get(squad).mineObjective();
    }

    /** Whether any squad last saw trees round it. */
    public boolean woodSeen() {
        return resourcesSeen.values().stream().flatMap(List::stream)
                .anyMatch(site -> site.resource() == Resource.WOOD);
    }

    /** The trees, exposed stone and farmland {@code squad} last saw round it - none before its first survey. */
    public List<ResourceSite> resourcesSeen(String squad) {
        return resourcesSeen.getOrDefault(squad, List.of());
    }

    public Optional<Objective> patrolObjective(String squad) {
        return squads.get(squad).patrolObjective();
    }

    public Optional<Objective> secureObjective(String squad) {
        return squads.get(squad).secureObjective();
    }

    public Optional<Objective> farmObjective(String squad) {
        return squads.get(squad).farmObjective();
    }

    public Optional<Objective> gatherObjective(String squad) {
        return squads.get(squad).gatherObjective();
    }

    /** {@code squad}'s soldiers' standing plans, as kept, and how each one's work order stands, for its squad leader's next look. */
    public void receivePlans(String squad, Map<String, Plan> plans,
                             Map<String, String> workOrders) {
        squads.get(squad).receivePlans(plans, workOrders);
    }

    /** {@code squad}'s standing order, if its squad leader has issued one. */
    public Optional<SquadOrder> currentOrder(String squad) {
        return squads.get(squad).order();
    }

    /**
     * Called with every present squad's SITREP (formation order, point man first) every second.
     * Cheap: at most kicks off model calls on the echelons' own threads and returns.
     */
    public void onSitreps(List<Sitrep> sitreps) {
        if (sitreps.isEmpty()) {
            return;
        }
        logReadiness(sitreps);
        trackRequests(sitreps, clockwork.millis());
        long traceNow = clockwork.millis();
        for (OrderTrace trace : traces) {
            for (Sitrep sitrep : sitreps) {
                trace.sitrep(sitrep.squad(), squads.get(sitrep.squad()).lastFrago(), sitrep.soldiers(), traceNow)
                        .forEach(line -> traced(trace.operator, line));
            }
        }
        traces.removeIf(OrderTrace::closed);
        long now = clockwork.millis();
        List<Sitrep> underPlatoonLeader = sitreps.stream().filter(s -> squads.get(s.squad()).escort().isEmpty()).toList();
        for (Sitrep sitrep : underPlatoonLeader) {
            SquadCommand squad = squads.get(sitrep.squad());
            if (squad.newCantco()) {
                wake("a CANTCO from " + sitrep.squad() + "'s squad leader: " + squad.report(), false);
            }
            // Not while it decides: the decision under way gives it one - live, each SITREP of the 20
            // seconds a decision took woke it again, for a second decision straight after.
            if (!squad.hasObjective() && !platoonLeaderBusy.get()) {
                wake(sitrep.squad() + " has no objective", true);
            }
        }
        if (!underPlatoonLeader.isEmpty() && !wakeCauses.isEmpty() && (wakeAtOnce || now - lastPlatoonLeaderMillis >= WAKE_GAP_MILLIS)) {
            askPlatoonLeader(underPlatoonLeader, now);
        }
        for (Sitrep sitrep : sitreps) {
            SquadCommand squad = squads.get(sitrep.squad());
            squad.onSitrep(sitrep.soldiers(), sitrep.terrain(), sitrep.principalAt(), sitrep.holdingBase(), now, MIN_GAP_MILLIS);
            squad.takeEscortEnded().ifPresent(principal -> {
                // Back under the platoon leader, who decides for it now rather than at its routine interval.
                missionVersion.incrementAndGet();
                wake("an escort ended", true);
                transmit("[" + squad.name + " -> PL] escort of " + principal + " ended: out of sight");
            });
        }
    }

    private void askPlatoonLeader(List<Sitrep> sitreps, long now) {
        if (!platoonLeaderBusy.compareAndSet(false, true)) {
            return;
        }
        synchronized (wakeCauses) {
            LOG.info("[PL] wakes: {}", String.join("; ", wakeCauses));
            wakeCauses.clear();
        }
        wakeAtOnce = false;
        lastPlatoonLeaderMillis = now;
        int missionAsked = missionVersion.get();
        ModelCalls.Ticket ticket = modelCalls.queued("PL");
        platoonLeaderThread.execute(() -> {
            ticket.started();
            try {
                long started = clockwork.millis();
                List<SquadStatus> statuses = sitreps.stream().map(s -> new SquadStatus(s.squad(),
                        squads.get(s.squad()).report(), s.soldiers(), s.resources().get(), squads.get(s.squad()).objective(),
                        s.holdingBase(), s.baseBlocksLeft(), s.progress(), s.classOne(), s.classFour())).toList();
                statuses.forEach(s -> resourcesSeen.put(s.squadName(), s.resources()));
                List<Objective> objectives = platoonLeader.decide(statuses);
                if (missionVersion.get() != missionAsked) {
                    LOG.info("[PL] discarding a decision made under superseded orders");
                    return; // the order that superseded it woke the platoon leader: the next SITREP asks again
                }
                long millis = clockwork.millis() - started;
                Map<String, Optional<Objective>> decided = new LinkedHashMap<>();
                Optional<PlatoonSergeant.Shortfall> shortfall = PlatoonSergeant.shortfall(statuses, stockpile());
                List<ResourceSite> woods = statuses.stream().flatMap(s -> s.resources().stream()).toList();
                for (int i = 0; i < statuses.size(); i++) {
                    String name = statuses.get(i).squadName();
                    Objective given = sheltered(grounded(byDay(objectives.get(i), statuses.get(i), shortfall, woods),
                            statuses.get(i).soldiers()), statuses.get(i));
                    if (squads.get(name).objective().filter(given::equals).isPresent()) {
                        LOG.info("[PL -> {}] ({} ms, {}) holds: {}", name, millis, phase(time), given.mission());
                        // The feedback not taken: the squad said its task is finished or stuck, and it was kept.
                        statuses.get(i).progress().filter(p -> p.state() != Progress.State.UNDER_WAY)
                                .ifPresent(p -> LOG.warn("[Feedback] the platoon leader kept {}'s task though it reported {}: {}", name, p.state(), p.detail()));
                        decided.put(name, Optional.empty());
                        continue;
                    }
                    decided.put(name, Optional.of(given));
                    // The platoon leader's answer to what the squad asked, if it asked: any new order answers a
                    // finished or stalled task; only a farm answers a request for food - live, the
                    // platoon leader answered a starving squad with torches, and that was counted an answer.
                    long answeredAt = clockwork.millis();
                    requests.answer(name + ":task", answeredAt).ifPresent(LOG::info);
                    if (given.task() instanceof Objective.Task.Farm) {
                        requests.answer(name + ":food", answeredAt).ifPresent(LOG::info);
                    } else if (requests.isOpen(name + ":food")) {
                        LOG.warn("[Request] the platoon leader's order for {} doesn't answer its Class I RED: {} ({})", name, given.mission(),
                                OrderTrace.taskName(given.task()));
                    }
                    LOG.info("[PL -> {}] ({} ms, {}) OPORD: {} at {}{}. Intent: {}", name, millis, phase(time), given.mission(),
                            at(given.location()), task(given), given.intent());
                    transmit("[PL -> " + name + "] OPORD: " + given.mission() + " at " + at(given.location())
                            + task(given) + ". Intent: " + given.intent());
                    squads.get(name).receiveObjective(given);
                }
                long decidedAt = clockwork.millis();
                traces.stream().filter(t -> t.version == missionAsked)
                        .forEach(t -> t.decided(decided, decidedAt).forEach(line -> traced(t.operator, line)));
            } catch (RuntimeException e) {
                LOG.error("[PL] no decision - keeping the current objectives: {}", e.getMessage(), e);
            } finally {
                ticket.finished();
                platoonLeaderBusy.set(false);
            }
        });
    }

    private void transmit(String line) {
        Consumer<String> current = radio;
        if (current != null) {
            current.accept(chatLine(line));
        }
    }

    /**
     * A radio line as a chat command: {@code /me} (any player may use it, and it reads as the radio
     * operator speaking), stripped of characters the server kicks for and cut to chat length.
     * Model-written intents can contain anything.
     */
    static String chatLine(String line) {
        StringBuilder clean = new StringBuilder("me ");
        for (int i = 0; i < line.length() && clean.length() < CHAT_LINE_LIMIT; i++) {
            char c = line.charAt(i);
            clean.append(c == '§' || c < ' ' || c == 127 ? ' ' : c);
        }
        return clean.toString();
    }

    static String at(Position p) {
        return "(" + Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()) + ")";
    }
}
