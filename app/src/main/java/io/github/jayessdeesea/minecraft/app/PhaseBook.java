package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Phase;
import io.github.jayessdeesea.minecraft.command.PhaseTracker;
import io.github.jayessdeesea.minecraft.command.Supply;

import java.util.List;
import java.util.Optional;

/**
 * The lodgment's phase kept across restarts, and the camp's readiness it's moved on, measured (command.Phase,
 * command.PhaseTracker): the camp secured and every soldier armed or armoured by its standards, Class I and
 * Class IV in days of supply. Thread-safe.
 */
final class PhaseBook {

    private static final String KEY = "phase";
    /** The least a day's burn is taken to be, a soldier: rations and logs - a burn not yet measured isn't none. */
    static final double RATIONS_A_SOLDIER = 2, LOGS_A_SOLDIER = 1;

    private final WallStore store;
    private long[] kept = new long[0];

    PhaseBook(WallStore store) {
        this.store = store;
    }

    /** The phase kept from before a restart, and when it was entered. */
    synchronized Optional<long[]> kept() {
        return store.loadRows(KEY).filter(rows -> !rows.isEmpty() && rows.getFirst().length >= 2).map(List::getFirst);
    }

    /** Keeps {@code phase}, entered at {@code since}, when it's changed. */
    synchronized void keep(Phase phase, long since) {
        keep(phase, since, Long.MIN_VALUE);
    }

    /** Keeps {@code phase}, entered at {@code since}, the next one's standard held since {@code upSince}, when it's changed. */
    synchronized void keep(Phase phase, long since, long upSince) {
        long[] row = {phase.ordinal(), since, upSince};
        if (!java.util.Arrays.equals(row, kept)) {
            store.saveRows(KEY, List.of(row));
            kept = row;
        }
    }

    /** The phase kept from before a restart put back into {@code chain}'s tracker. */
    void restoreInto(ChainOfCommand chain) {
        kept().ifPresent(row -> chain.restorePhase(Phase.values()[(int) row[0]], row[1], row.length > 2 ? row[2] : Long.MIN_VALUE));
    }

    /** The camp's readiness measured into {@code chain}'s phase, a breach three killed in it lately, and the phase kept. */
    void measure(ChainOfCommand chain, Foothold camp, List<Supply> supplies, int soldiers, long killedInCamp, long now) {
        chain.onReadiness(readiness(camp, supplies, soldiers, killedInCamp >= io.github.jayessdeesea.minecraft.command.DangerAreas.DEATHS), now);
        keep(chain.currentPhase(), chain.phaseSince(), chain.phaseUpSince());
    }

    /** The camp's readiness: its standards by name, its supply in days against a floor burn for {@code soldiers}. */
    static PhaseTracker.Readiness readiness(Foothold camp, List<Supply> supplies, int soldiers, boolean breached) {
        return new PhaseTracker.Readiness(met(camp, "the camp's ground lit"), met(camp, "every soldier armed"),
                met(camp, "every soldier armoured"), days(supplies, "Class I", RATIONS_A_SOLDIER * soldiers),
                days(supplies, "Class IV", LOGS_A_SOLDIER * soldiers), breached);
    }

    private static boolean met(Foothold camp, String standard) {
        return camp.standards().stream().filter(s -> s.name().equals(standard)).allMatch(Foothold.Standard::met);
    }

    private static double days(List<Supply> supplies, String name, double floor) {
        return supplies.stream().filter(s -> s.name().startsWith(name)).findFirst()
                .map(s -> s.stock() / Math.max(Math.max(s.burn(), floor), 1)).orElse(0.0);
    }
}
