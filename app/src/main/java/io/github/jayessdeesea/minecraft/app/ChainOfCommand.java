package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The LLM echelons' imperative shell: decides <em>when</em> the general and each squad leader
 * think, runs their model calls off every soldier-facing thread, and hands each squad's
 * {@link SquadOrder} to {@code SoldierRunner}'s squad loop. Cadence follows AGENTS.md's latency
 * architecture - the higher the echelon, the less often - plus events: a new objective, or a
 * <em>new</em> CANTCO from below (docs/military-doctrine.md §3), triggers a decision early, so
 * push-back is answered in seconds rather than at the next routine interval. A CANTCO that's still
 * standing after the echelon has answered it waits for the routine interval - re-asking every few
 * seconds about the same report just churns orders.
 *
 * <p>One general commands every squad in the {@link Roster} at once, so it can split the work
 * between them. A squad the operator attached to a player as an escort ({@code !escort}) is set
 * aside from the general until the next {@code !mission}; its objective follows the player
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
public class ChainOfCommand implements org.springframework.beans.factory.DisposableBean {

    private static final Logger LOG = LogManager.getLogger(ChainOfCommand.class);

    private static final long GENERAL_PERIOD_MILLIS = 120_000;
    /** Least time between decisions, however many CANTCOs arrive - stops a stuck soldier spamming the model. */
    private static final long MIN_GAP_MILLIS = 8_000;
    /** Vanilla chat's own limit is 256 characters. */
    static final int CHAT_LINE_LIMIT = 250;

    /**
     * One squad's SITREP for a cycle: its soldiers, its terrain (surveyed only if its leader
     * decides), its principal, what it can see to gather (surveyed only if the general decides), and
     * how its patrol base stands.
     */
    public record Sitrep(String squad, List<SoldierStatus> soldiers, Supplier<TerrainSurvey> terrain,
                         Optional<Position> principalAt, Supplier<List<io.github.jayessdeesea.minecraft.command.ResourceSite>> resources,
                         boolean holdingBase, java.util.OptionalInt baseBlocksLeft) {
    }

    private final General general;
    private final Map<String, SquadCommand> squads = new LinkedHashMap<>();
    /** What each squad last saw round it - the survey the general was last shown. */
    private final Map<String, List<io.github.jayessdeesea.minecraft.command.ResourceSite>> resourcesSeen =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final ExecutorService generalThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "general"));
    private final AtomicBoolean generalBusy = new AtomicBoolean();
    /** Bumped on each operator order, so a general's decision already in flight is known to be stale. */
    private final AtomicInteger missionVersion = new AtomicInteger();

    private volatile long lastGeneralMillis = Long.MIN_VALUE / 2;
    private volatile Consumer<String> radio;
    /** The ground's height at an x and z, looked for near a height - the world's, as the soldiers see it. */
    private volatile Ground ground = (x, z, nearY) -> java.util.OptionalInt.empty();

    /** Where a body stands at ({@code x}, {@code z}): the highest standing cell within reach of {@code nearY}, if the world shows one. */
    @FunctionalInterface
    public interface Ground {
        java.util.OptionalInt heightAt(int x, int z, int nearY);
    }
    private volatile io.github.jayessdeesea.minecraft.command.TimeOfDay time;

    /**
     * The operator's standing rule, when set: no squad spends the night in the open. Set it where a
     * mission says so - a garrison - and leave it off where a scenario may need a squad out at night.
     */
    private final boolean shelterAtNight;
    /** Every echelon's model decisions, timed and alarmed on. */
    private final ModelCalls modelCalls = new ModelCalls();
    /**
     * How long before dark the rule starts: a hut takes a few minutes, more with dirt to dig first,
     * so a squad set to it only once it's dark is building in the dark.
     */
    static final long SHELTER_LEAD_TICKS = 1800;

    public ChainOfCommand(General general, @Qualifier("squadLeaderChatClient") ChatClient squadLeaderClient,
                          io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts squadLeaderPrompts, Roster roster,
                          @org.springframework.beans.factory.annotation.Value("${minecraft.general.shelter-at-night}") boolean shelterAtNight) {
        this.general = general;
        this.shelterAtNight = shelterAtNight;
        for (Roster.Squad squad : roster.squads()) {
            squads.put(squad.name(), new SquadCommand(squad.name(), new SpringAiSquadLeader(squad.name(), squadLeaderClient, squadLeaderPrompts), this::transmit,
                    modelCalls));
        }
    }

    /**
     * Where radio traffic is keyed out - the radio operator's connection. Until one is set (no
     * soldier connected yet), traffic is only logged.
     */
    /** How the general's objectives find the ground: their heights are its guesses, the world's are known. */
    public void setGround(Ground ground) {
        this.ground = ground;
    }

    /**
     * {@code objective}, or - at night, under {@link #shelterAtNight}, when the general's order
     * would leave a squad in the open or take it out of the base it is sealed in - the patrol base it was already
     * ordered to build, or else one where the squad's point man stands. A guardrail, and a loud one: the general's own order is logged beside it.
     */
    Objective sheltered(Objective objective, io.github.jayessdeesea.minecraft.command.SquadStatus squad) {
        io.github.jayessdeesea.minecraft.command.TimeOfDay now = time;
        // A squad already sealed in is the one most to keep there: an order out of it is no exception.
        if (!shelterAtNight || now == null || !(now.dark() || now.ticksUntilDark() <= SHELTER_LEAD_TICKS)
                || objective.patrolBase() || squad.soldiers().isEmpty()) {
            return objective;
        }
        // A base already ordered is the one to finish: sited again wherever the point man stands at
        // each decision, a squad starts hut after hut and finishes none before dawn.
        Optional<Objective> standing = squads.get(squad.squadName()).objective().filter(Objective::patrolBase);
        if (standing.isPresent()) {
            LOG.warn("[General -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule keeps it on the patrol base it has",
                    squad.squadName(), objective.mission(), objective.task());
            return standing.get();
        }
        Position at = squad.soldiers().getFirst().position();
        Objective shelter = grounded(new Objective("Build a patrol base where you stand and spend the night in it",
                new Position(Math.floor(at.x()), at.y(), Math.floor(at.z())), "Squad safe from monsters until dawn",
                new Objective.Task.PatrolBase()), squad.soldiers());
        LOG.warn("[General -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule shelters it instead, at {}",
                squad.squadName(), objective.mission(), objective.task(), at(shelter.location()));
        return shelter;
    }

    /**
     * {@code objective} with its height the ground's at its x and z, looked for near the squad's
     * middle height: the general guesses heights, and a guess copied from elsewhere in its prompt
     * lands an objective in the sky or underground. MINE keeps its own: that y is a depth.
     */
    Objective grounded(Objective objective, List<io.github.jayessdeesea.minecraft.command.SoldierStatus> soldiers) {
        if (objective.mine() || soldiers.isEmpty()) {
            return objective;
        }
        Position at = objective.location();
        // Near the squad's middle height, not its first soldier's: one stranded up a tower or down a
        // hole would take every objective there with it.
        double[] heights = soldiers.stream().mapToDouble(s -> s.position().y()).sorted().toArray();
        java.util.OptionalInt height = ground.heightAt((int) Math.floor(at.x()), (int) Math.floor(at.z()),
                (int) Math.floor(heights[heights.length / 2]));
        if (height.isEmpty() || height.getAsInt() == (int) Math.floor(at.y())) {
            return objective;
        }
        LOG.info("[General] objective at x={} z={} put on the ground at y={}, not the y={} given", (int) Math.floor(at.x()),
                (int) Math.floor(at.z()), height.getAsInt(), (int) Math.floor(at.y()));
        return new Objective(objective.mission(), new Position(at.x(), height.getAsInt(), at.z()), objective.intent(), objective.task());
    }

    public void setRadio(Consumer<String> radio) {
        this.radio = radio;
    }

    /**
     * A new mission from a human operator (see {@code OperatorOrders}): the general takes it and
     * re-plans at the next SITREP rather than at its routine interval, for every squad - an escort
     * ends. Acknowledged on the radio.
     */
    public void onOperatorMission(String operator, String mission) {
        general.receiveMission(mission);
        squads.values().forEach(squad -> squad.escort(null));
        missionVersion.incrementAndGet();
        lastGeneralMillis = Long.MIN_VALUE / 2;
        LOG.info("[{} -> General] MISSION: {}", operator, mission);
        transmit("[General -> " + operator + "] WILCO: " + mission);
    }

    /**
     * Something the operator said that isn't an order: the general hears it at its next decision,
     * brought forward - an operator who speaks up has usually seen something. Acknowledged on the radio.
     */
    public void onOperatorRemark(String operator, String remark) {
        general.receiveRemark(remark);
        lastGeneralMillis = Long.MIN_VALUE / 2;
        LOG.info("[{} -> General] REMARK: {}", operator, remark);
        transmit("[General -> " + operator + "] ROGER: " + remark);
    }

    /**
     * The operator attaches the first squad in the {@link Roster} to {@code principal} as its
     * escort (docs/military-doctrine.md §10): until the next {@code !mission}, the general leaves
     * it out and its squad leader's objective follows the principal. Acknowledged on the radio.
     */
    public void onOperatorEscort(String operator, String principal) {
        SquadCommand escort = squads.values().iterator().next();
        escort.escort(principal);
        missionVersion.incrementAndGet(); // a general's decision in flight may have included this squad
        lastGeneralMillis = Long.MIN_VALUE / 2; // the others are re-planned without it
        LOG.info("[{} -> {}] ESCORT: {}", operator, escort.name, principal);
        transmit("[" + escort.name + " -> " + operator + "] WILCO: escorting " + principal);
    }

    /** The camp's progress through the lodgment's phases, measured - the general is told, and the log when it moves. */
    public void onFoothold(io.github.jayessdeesea.minecraft.command.Foothold foothold) {
        String standing = foothold.standards().stream().map(s -> s.name() + (s.met() ? " met" : ": " + s.status()))
                .collect(java.util.stream.Collectors.joining("; "));
        if (!standing.equals(lastFoothold)) {
            LOG.info("[Foothold] phase {}: {}", foothold.nextPhase().isPresent() ? foothold.nextPhase().getAsInt() : "all met", standing);
            lastFoothold = standing;
        }
        general.receiveFoothold(foothold);
        this.foothold = foothold;
    }

    private volatile String lastFoothold = "";
    /** The home base's progress, last measured - a soldier's thread writes, the squads' read. */
    private volatile io.github.jayessdeesea.minecraft.command.Foothold foothold;
    /** Each squad's readiness as last seen, so only a change of it is logged. */
    private final Map<String, io.github.jayessdeesea.minecraft.command.Readiness> readiness = new java.util.concurrent.ConcurrentHashMap<>();

    /** Logs each squad's readiness where it changed: safety where it stands, and its C-rating. */
    private void logReadiness(List<Sitrep> sitreps) {
        for (Sitrep sitrep : sitreps) {
            io.github.jayessdeesea.minecraft.command.Readiness now = io.github.jayessdeesea.minecraft.command.Readiness.of(
                    new SquadStatus(sitrep.squad(), squads.get(sitrep.squad()).report(), sitrep.soldiers(), List.of(), Optional.empty(),
                            sitrep.holdingBase()), Optional.ofNullable(foothold));
            io.github.jayessdeesea.minecraft.command.Readiness was = readiness.put(sitrep.squad(), now);
            // A change of state, not of the counts behind it: hunger and health move every few seconds.
            if (was == null || now.safety() != was.safety() || now.rating() != was.rating()) {
                LOG.info("[Readiness] {}: {}, C-{}{}", sitrep.squad(), now.safety(), now.rating(),
                        now.shortfall().isEmpty() ? "" : " - " + now.shortfall());
            }
        }
    }

    /** A soldier saw what the stockpile holds - the general is told, for its next decision. */
    public void onStockpile(io.github.jayessdeesea.minecraft.command.Stockpile stockpile) {
        general.receiveStockpile(stockpile);
    }

    /** The server sent the time of day - the general plans around nightfall. Dusk and dawn are logged. */
    public void onTime(io.github.jayessdeesea.minecraft.command.TimeOfDay time) {
        io.github.jayessdeesea.minecraft.command.TimeOfDay last = this.time;
        this.time = time;
        if (last == null || last.dark() != time.dark()) {
            LOG.info("[Clock] {} - tick {} of the day, the chain of command told", time.dark() ? "NIGHT" : "DAY", time.ticks());
            lastGeneralMillis = Long.MIN_VALUE / 2; // dusk and dawn change what every squad should be doing
        }
        boolean nearDark = !time.dark() && time.ticksUntilDark() <= SHELTER_LEAD_TICKS;
        if (last != null && nearDark && !(!last.dark() && last.ticksUntilDark() <= SHELTER_LEAD_TICKS)) {
            LOG.info("[Clock] DUSK NEAR - dark in {} ticks, the general decides now", time.ticksUntilDark());
            lastGeneralMillis = Long.MIN_VALUE / 2;
        }
        general.receiveTime(time);
        squads.values().forEach(squad -> squad.receiveTime(time));
    }

    /** Stops the general's and the squad leaders' threads - a decision in flight is abandoned. */
    @Override
    public void destroy() {
        generalThread.shutdownNow();
        squads.values().forEach(SquadCommand::stop);
    }

    /** Day or night as a decision was told it, for the log line that records the decision. */
    static String phase(io.github.jayessdeesea.minecraft.command.TimeOfDay time) {
        return time == null ? "time unknown" : time.dark() ? "night" : "day";
    }

    /** The time of day the server last sent, if it has. */
    public Optional<io.github.jayessdeesea.minecraft.command.TimeOfDay> time() {
        return Optional.ofNullable(time);
    }

    /** Who {@code squad} is escorting, if anyone. */
    public Optional<String> escort(String squad) {
        return squads.get(squad).escort();
    }

    /** The objective's task, as the OPORD says it - nothing when there's none. */
    private static String task(Objective objective) {
        return objective.patrolBase() ? ", patrol base"
                : objective.gather().map(r -> ", gather " + r.name().toLowerCase(java.util.Locale.ROOT)).orElse("");
    }

    /** The general's objective for {@code squad}, once it has one. */
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

    /** The trees, exposed stone and farmland {@code squad} last saw round it - none before its first survey. */
    public List<io.github.jayessdeesea.minecraft.command.ResourceSite> resourcesSeen(String squad) {
        return resourcesSeen.getOrDefault(squad, List.of());
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
        long now = System.currentTimeMillis();
        List<Sitrep> underGeneral = sitreps.stream().filter(s -> squads.get(s.squad()).escort().isEmpty()).toList();
        boolean newCantco = false;
        boolean newContact = false;
        boolean unordered = false;
        for (Sitrep sitrep : underGeneral) {
            SquadCommand squad = squads.get(sitrep.squad());
            newCantco |= squad.newCantco();
            newContact |= squad.newContact(sitrep.soldiers());
            unordered |= !squad.hasObjective();
        }
        if (!underGeneral.isEmpty() && (unordered || now - lastGeneralMillis >= GENERAL_PERIOD_MILLIS
                || ((newCantco || newContact) && now - lastGeneralMillis >= MIN_GAP_MILLIS))) {
            askGeneral(underGeneral, now);
        }
        for (Sitrep sitrep : sitreps) {
            SquadCommand squad = squads.get(sitrep.squad());
            squad.onSitrep(sitrep.soldiers(), sitrep.terrain(), sitrep.principalAt(), sitrep.holdingBase(), now, MIN_GAP_MILLIS);
            squad.takeEscortEnded().ifPresent(principal -> {
                // Back under the general, who decides for it now rather than at its routine interval.
                missionVersion.incrementAndGet();
                lastGeneralMillis = Long.MIN_VALUE / 2;
                transmit("[" + squad.name + " -> General] escort of " + principal + " ended: out of sight");
            });
        }
    }

    private void askGeneral(List<Sitrep> sitreps, long now) {
        if (!generalBusy.compareAndSet(false, true)) {
            return;
        }
        lastGeneralMillis = now;
        int missionAsked = missionVersion.get();
        ModelCalls.Ticket ticket = modelCalls.queued("General");
        generalThread.execute(() -> {
            ticket.started();
            try {
                long started = System.currentTimeMillis();
                List<SquadStatus> statuses = sitreps.stream().map(s -> new SquadStatus(s.squad(),
                        squads.get(s.squad()).report(), s.soldiers(), s.resources().get(), squads.get(s.squad()).objective(),
                        s.holdingBase(), s.baseBlocksLeft())).toList();
                statuses.forEach(s -> resourcesSeen.put(s.squadName(), s.resources()));
                List<Objective> objectives = general.decide(statuses);
                if (missionVersion.get() != missionAsked) {
                    LOG.info("[General] discarding a decision made under superseded orders");
                    return; // lastGeneralMillis was reset: the next SITREP asks again
                }
                long millis = System.currentTimeMillis() - started;
                for (int i = 0; i < statuses.size(); i++) {
                    String name = statuses.get(i).squadName();
                    Objective given = sheltered(grounded(objectives.get(i), statuses.get(i).soldiers()), statuses.get(i));
                    if (squads.get(name).objective().filter(given::equals).isPresent()) {
                        LOG.info("[General -> {}] ({} ms, {}) holds: {}", name, millis, phase(time), given.mission());
                        continue;
                    }
                    LOG.info("[General -> {}] ({} ms, {}) OPORD: {} at {}{}. Intent: {}", name, millis, phase(time), given.mission(),
                            at(given.location()), task(given), given.intent());
                    transmit("[General -> " + name + "] OPORD: " + given.mission() + " at " + at(given.location())
                            + task(given) + ". Intent: " + given.intent());
                    squads.get(name).receiveObjective(given);
                }
            } catch (RuntimeException e) {
                LOG.error("[General] no decision - keeping the current objectives: {}", e.getMessage(), e);
            } finally {
                ticket.finished();
                generalBusy.set(false);
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
