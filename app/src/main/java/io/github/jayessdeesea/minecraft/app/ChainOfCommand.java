package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TerrainSurvey;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The LLM echelons' imperative shell: decides <em>when</em> the general and the squad leader
 * think, runs their model calls off every soldier-facing thread, and hands the resulting
 * {@link SquadOrder} to {@code SoldierRunner}'s formation loop. Cadence follows AGENTS.md's
 * latency architecture - the higher the echelon, the less often - plus events: a new objective,
 * or a <em>new</em> CANTCO from below (docs/military-doctrine.md §3), triggers a decision early, so
 * push-back is answered in seconds rather than at the next routine interval. A CANTCO that's
 * still standing after the echelon has answered it waits for the routine interval - re-asking
 * every few seconds about the same report just churns orders.
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
public class ChainOfCommand {

    private static final Logger LOG = LogManager.getLogger(ChainOfCommand.class);

    static final String SQUAD_NAME = "Alpha";

    private static final long SQUAD_PERIOD_MILLIS = 20_000;
    private static final long GENERAL_PERIOD_MILLIS = 120_000;
    /** Least time between decisions, however many CANTCOs arrive - stops a stuck soldier spamming the model. */
    private static final long MIN_GAP_MILLIS = 8_000;
    /** Vanilla chat's own limit is 256 characters. */
    static final int CHAT_LINE_LIMIT = 250;

    private final General general;
    private final SquadLeader squadLeader;
    private final ExecutorService generalThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "general"));
    private final ExecutorService squadLeaderThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "squad-leader-" + SQUAD_NAME));
    private final AtomicBoolean generalBusy = new AtomicBoolean();
    private final AtomicBoolean squadLeaderBusy = new AtomicBoolean();
    /** Bumped on each operator mission, so a general's decision already in flight is known to be stale. */
    private final java.util.concurrent.atomic.AtomicInteger missionVersion = new java.util.concurrent.atomic.AtomicInteger();

    private volatile SquadOrder order;
    private volatile Objective objective;
    private volatile boolean newObjective;
    private volatile long lastGeneralMillis = Long.MIN_VALUE / 2;
    private volatile long lastSquadLeaderMillis = Long.MIN_VALUE / 2;
    // The reports each echelon last answered - a decision is triggered early only when these change.
    private volatile Report squadReportAnswered = new Report.Idle();
    private volatile java.util.Set<String> cantcosAnswered = java.util.Set.of();
    private volatile Consumer<String> radio;

    public ChainOfCommand(General general, @Qualifier("squadLeaderChatClient") ChatClient squadLeaderClient) {
        this.general = general;
        this.squadLeader = new SpringAiSquadLeader(SQUAD_NAME, squadLeaderClient);
    }

    /**
     * Where radio traffic is keyed out - the squad's radio operator's connection. Until one is set
     * (no soldier connected yet), traffic is only logged.
     */
    public void setRadio(Consumer<String> radio) {
        this.radio = radio;
    }

    /**
     * A new mission from a human operator (see {@code OperatorOrders}): the general takes it and
     * re-plans at the next SITREP rather than at its routine interval. Acknowledged on the radio.
     */
    public void onOperatorMission(String operator, String mission) {
        general.receiveMission(mission);
        missionVersion.incrementAndGet();
        lastGeneralMillis = Long.MIN_VALUE / 2;
        LOG.info("[{} -> General] MISSION: {}", operator, mission);
        transmit("[General -> " + operator + "] WILCO: " + mission);
    }

    /** The squad's standing order, if the squad leader has issued one. */
    public Optional<SquadOrder> currentOrder() {
        return Optional.ofNullable(order);
    }

    /**
     * Called with the squad's SITREPs (formation order, point man first) every second. Cheap: at
     * most kicks off a model call on its echelon's own thread and returns. {@code terrain} is only
     * evaluated - on the squad leader's thread - when the squad leader is actually about to decide.
     */
    public void onSitrep(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain) {
        if (squad.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Report squadReport = squadLeader.report();
        // New = the squad went from able to unable. A CANTCO re-worded on the next decision is the
        // same CANTCO; comparing the text re-tasked the squad every few seconds.
        boolean newSquadCantco = squadReport instanceof Report.Cantco && !(squadReportAnswered instanceof Report.Cantco);
        if (objective == null || since(lastGeneralMillis, now) >= GENERAL_PERIOD_MILLIS
                || (newSquadCantco && since(lastGeneralMillis, now) >= MIN_GAP_MILLIS)) {
            askGeneral(squad, now);
        }
        squadReportAnswered = squadReport;
        java.util.Set<String> cantcos = squad.stream().filter(s -> s.report() instanceof Report.Cantco)
                .map(SoldierStatus::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean newSoldierCantco = !cantcosAnswered.containsAll(cantcos);
        if (objective != null && (newObjective || since(lastSquadLeaderMillis, now) >= SQUAD_PERIOD_MILLIS
                || (newSoldierCantco && since(lastSquadLeaderMillis, now) >= MIN_GAP_MILLIS))) {
            cantcosAnswered = cantcos;
            askSquadLeader(squad, terrain, now);
        }
    }

    private void askGeneral(List<SoldierStatus> squad, long now) {
        if (!generalBusy.compareAndSet(false, true)) {
            return;
        }
        lastGeneralMillis = now;
        SquadStatus status = new SquadStatus(SQUAD_NAME, squadLeader.report(), squad);
        int missionAsked = missionVersion.get();
        generalThread.execute(() -> {
            try {
                long started = System.currentTimeMillis();
                Objective given = general.decide(List.of(status)).getFirst();
                if (missionVersion.get() != missionAsked) {
                    LOG.info("[General] discarding a decision made under the superseded mission");
                    return; // lastGeneralMillis was reset: the next SITREP asks again
                }
                LOG.info("[General -> {}] ({} ms) OPORD: {} at ({}, {}, {}). Intent: {}", SQUAD_NAME,
                        System.currentTimeMillis() - started, given.mission(),
                        Math.round(given.location().x()), Math.round(given.location().y()), Math.round(given.location().z()),
                        given.intent());
                transmit("[General -> " + SQUAD_NAME + "] OPORD: " + given.mission() + " at " + at(given.location())
                        + ". Intent: " + given.intent());
                squadLeader.receiveObjective(given);
                objective = given;
                newObjective = true;
            } catch (RuntimeException e) {
                LOG.error("[General] no decision - keeping the current objective: {}", e.getMessage(), e);
            } finally {
                generalBusy.set(false);
            }
        });
    }

    private void askSquadLeader(List<SoldierStatus> squad, Supplier<TerrainSurvey> terrain, long now) {
        if (!squadLeaderBusy.compareAndSet(false, true)) {
            return;
        }
        lastSquadLeaderMillis = now;
        newObjective = false;
        squadLeaderThread.execute(() -> {
            try {
                long started = System.currentTimeMillis();
                TerrainSurvey survey = terrain.get();
                long reachable = survey.samples().stream().filter(TerrainSurvey.Sample::reachable).count();
                squadLeader.decide(squad, survey).ifPresent(decided -> {
                    LOG.info("[{} -> squad] ({} ms, terrain {}/{} reachable) FRAGO: move to ({}, {}, {}) in {} at {} interval, {} risk{}. Intent: {}",
                            SQUAD_NAME, System.currentTimeMillis() - started, reachable, survey.samples().size(),
                            Math.round(decided.objective().x()), Math.round(decided.objective().y()), Math.round(decided.objective().z()),
                            decided.formation(), decided.interval(), decided.risk(), soldierRisk(decided), decided.intent());
                    transmit("[" + SQUAD_NAME + " -> squad] FRAGO: " + decided.formation() + " to " + at(decided.objective())
                            + ", " + decided.risk() + " risk" + soldierRisk(decided)
                            + ". Intent: " + decided.intent());
                    if (squadLeader.report() instanceof Report.Cantco cantco) {
                        LOG.warn("[{} -> General] CANTCO: {}", SQUAD_NAME, cantco.reason());
                        transmit("[" + SQUAD_NAME + " -> General] CANTCO: " + cantco.reason());
                    }
                    order = decided;
                });
            } catch (RuntimeException e) {
                LOG.error("[{}] no decision - squad keeps its last order: {}", SQUAD_NAME, e.getMessage(), e);
            } finally {
                squadLeaderBusy.set(false);
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

    private static String at(io.github.jayessdeesea.minecraft.command.Position p) {
        return "(" + Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()) + ")";
    }

    /** ", Carol at MEDIUM risk" for each per-soldier override, in name order; empty if none. */
    private static String soldierRisk(SquadOrder order) {
        return order.soldierRisk().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> ", " + e.getKey() + " at " + e.getValue() + " risk").collect(java.util.stream.Collectors.joining());
    }

    private static long since(long then, long now) {
        return now - then;
    }
}
