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
    private final ExecutorService generalThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "general"));
    private final AtomicBoolean generalBusy = new AtomicBoolean();
    /** Bumped on each operator order, so a general's decision already in flight is known to be stale. */
    private final AtomicInteger missionVersion = new AtomicInteger();

    private volatile long lastGeneralMillis = Long.MIN_VALUE / 2;
    private volatile Consumer<String> radio;
    private volatile io.github.jayessdeesea.minecraft.command.TimeOfDay time;

    public ChainOfCommand(General general, @Qualifier("squadLeaderChatClient") ChatClient squadLeaderClient, Roster roster) {
        this.general = general;
        for (Roster.Squad squad : roster.squads()) {
            squads.put(squad.name(), new SquadCommand(squad.name(), new SpringAiSquadLeader(squad.name(), squadLeaderClient), this::transmit));
        }
    }

    /**
     * Where radio traffic is keyed out - the radio operator's connection. Until one is set (no
     * soldier connected yet), traffic is only logged.
     */
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

    /** A soldier saw what the stockpile holds - the general is told, for its next decision. */
    public void onStockpile(io.github.jayessdeesea.minecraft.command.Stockpile stockpile) {
        general.receiveStockpile(stockpile);
    }

    /** The server sent the time of day - the general plans around nightfall. */
    public void onTime(io.github.jayessdeesea.minecraft.command.TimeOfDay time) {
        this.time = time;
        general.receiveTime(time);
        squads.values().forEach(squad -> squad.receiveTime(time));
    }

    /** Stops the general's and the squad leaders' threads - a decision in flight is abandoned. */
    @Override
    public void destroy() {
        generalThread.shutdownNow();
        squads.values().forEach(SquadCommand::stop);
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
        long now = System.currentTimeMillis();
        List<Sitrep> underGeneral = sitreps.stream().filter(s -> squads.get(s.squad()).escort().isEmpty()).toList();
        boolean newCantco = false;
        boolean unordered = false;
        for (Sitrep sitrep : underGeneral) {
            SquadCommand squad = squads.get(sitrep.squad());
            newCantco |= squad.newCantco();
            unordered |= !squad.hasObjective();
        }
        if (!underGeneral.isEmpty() && (unordered || now - lastGeneralMillis >= GENERAL_PERIOD_MILLIS
                || (newCantco && now - lastGeneralMillis >= MIN_GAP_MILLIS))) {
            askGeneral(underGeneral, now);
        }
        for (Sitrep sitrep : sitreps) {
            squads.get(sitrep.squad()).onSitrep(sitrep.soldiers(), sitrep.terrain(), sitrep.principalAt(), sitrep.holdingBase(),
                    now, MIN_GAP_MILLIS);
        }
    }

    private void askGeneral(List<Sitrep> sitreps, long now) {
        if (!generalBusy.compareAndSet(false, true)) {
            return;
        }
        lastGeneralMillis = now;
        int missionAsked = missionVersion.get();
        generalThread.execute(() -> {
            try {
                long started = System.currentTimeMillis();
                List<SquadStatus> statuses = sitreps.stream().map(s -> new SquadStatus(s.squad(),
                        squads.get(s.squad()).report(), s.soldiers(), s.resources().get(), squads.get(s.squad()).objective(),
                        s.holdingBase(), s.baseBlocksLeft())).toList();
                List<Objective> objectives = general.decide(statuses);
                if (missionVersion.get() != missionAsked) {
                    LOG.info("[General] discarding a decision made under superseded orders");
                    return; // lastGeneralMillis was reset: the next SITREP asks again
                }
                long millis = System.currentTimeMillis() - started;
                for (int i = 0; i < statuses.size(); i++) {
                    String name = statuses.get(i).squadName();
                    Objective given = objectives.get(i);
                    if (squads.get(name).objective().filter(given::equals).isPresent()) {
                        LOG.info("[General -> {}] ({} ms) holds: {}", name, millis, given.mission());
                        continue;
                    }
                    LOG.info("[General -> {}] ({} ms) OPORD: {} at {}{}. Intent: {}", name, millis, given.mission(),
                            at(given.location()), task(given), given.intent());
                    transmit("[General -> " + name + "] OPORD: " + given.mission() + " at " + at(given.location())
                            + task(given) + ". Intent: " + given.intent());
                    squads.get(name).receiveObjective(given);
                }
            } catch (RuntimeException e) {
                LOG.error("[General] no decision - keeping the current objectives: {}", e.getMessage(), e);
            } finally {
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
