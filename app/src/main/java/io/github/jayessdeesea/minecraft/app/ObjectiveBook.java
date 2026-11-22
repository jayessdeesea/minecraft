package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Report;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The platoon leader's objective register kept as the chain of command runs (operator, 2026-11-21: the squad leaders push
 * updates to the platoon leader): each objective written when it's given - the squad's last relieved - or found open
 * when it's held after a restart; and each squad's SITREP pushed into its row as it comes - CANTCO with why, the task
 * done with what was measured, progress as said. What the platoon leader and the operator's chat read for "what's under
 * way", whenever it was last decided. Its rows kept in the garrison's {@link Registers}.
 */
final class ObjectiveBook {

    private static final Logger LOG = LogManager.getLogger(ObjectiveBook.class);

    private final Supplier<Registers> registers;
    /** Each squad's open row as last kept: read from the register the first time, after a restart. */
    private final Map<String, ObjectiveRecord> open = new HashMap<>();

    ObjectiveBook(Supplier<Registers> registers) {
        this.registers = registers;
    }

    /** {@code squad} given {@code objective} at {@code now}: its open row, if another, relieved; this one under way. */
    synchronized void given(String squad, Objective objective, long now) {
        ObjectiveRecord was = open(squad).orElse(null);
        if (was != null && same(was, objective)) {
            return;
        }
        if (was != null) {
            keep(was.now(ObjectiveRecord.State.RELIEVED, was.squad(), Optional.of("given " + objective.mission()), now));
        }
        // A mission's step, PLANNED when the mission was given: its own row under way, not a new one.
        Optional<ObjectiveRecord> planned = registers.get().objectives().stream().filter(r -> step(r, objective)
                && r.state() != ObjectiveRecord.State.DONE && r.state() != ObjectiveRecord.State.UNDER_WAY).findFirst(); // relieved, it's taken up again
        ObjectiveRecord row = planned.map(r -> r.now(ObjectiveRecord.State.UNDER_WAY, Optional.of(squad), Optional.empty(), now)).orElse(null);
        if (row != null) {
            registers.get().update(row);
        } else {
            row = registers.get().add(new ObjectiveRecord(0, objective.mission(), 0, OrderTrace.taskName(objective.task()),
                    Math.floorDiv((int) Math.floor(objective.location().x()), 16), Math.floorDiv((int) Math.floor(objective.location().z()), 16),
                    Optional.of(squad), ObjectiveRecord.State.UNDER_WAY, now, Optional.empty()));
        }
        open.put(squad, row);
        LOG.info("[Objectives] {}: {} ({}) under way in chunk ({}, {})", squad, row.mission(), row.task(), row.chunkX(), row.chunkZ());
    }

    /** {@code squad} holding {@code objective} at {@code now}: written if no row of it is open - the first decision after a restart. */
    synchronized void held(String squad, Objective objective, long now) {
        if (open(squad).filter(r -> same(r, objective)).isEmpty()) {
            given(squad, objective, now);
        }
    }

    /**
     * {@code squad}'s SITREP at {@code now}: its squad leader's {@code report} and the {@code progress} of its task, pushed
     * into its open row when how it stands has changed - CANTCO with why, done with what was measured, under way with
     * its progress as said.
     */
    synchronized void sitrep(String squad, Report report, Optional<Progress> progress, long now) {
        Optional<ObjectiveRecord> row = open(squad);
        if (row.isEmpty()) {
            return;
        }
        ObjectiveRecord.State state = report instanceof Report.Cantco ? ObjectiveRecord.State.CANTCO
                : progress.filter(p -> p.state() == Progress.State.DONE).isPresent() ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.UNDER_WAY;
        Optional<String> why = report instanceof Report.Cantco(String reason) ? Optional.of(reason)
                : progress.map(p -> p.state() == Progress.State.STALLED ? "stalled: " + p.detail() : p.detail());
        ObjectiveRecord was = row.get();
        if (was.state() == state && was.why().equals(why)) {
            return;
        }
        ObjectiveRecord now_ = was.now(state, was.squad(), why, now);
        keep(now_);
        if (was.state() != state) {
            LOG.info("[Objectives] {}: {} {}{}", squad, was.mission(), state, why.map(w -> " - " + w).orElse(""));
        }
    }

    /**
     * The register as the platoon leader reads it at {@code now}: each squad's open objective - how it stands, since when,
     * and why - and every chunk kept, what it is, its level and its stage. Computed, for the model to quote.
     */
    synchronized String describe(long now) {
        StringBuilder text = new StringBuilder("Objectives open, as each squad last reported:\n");
        java.util.List<ObjectiveRecord> open = registers.get().openObjectives();
        if (open.isEmpty()) {
            text.append("- none\n");
        }
        for (ObjectiveRecord o : open) {
            text.append(String.format(java.util.Locale.ROOT, "- %s: %s (%s) in chunk (%d, %d) - %s for %d s%s%n", o.squad().orElse("no squad"),
                    o.mission(), o.task(), o.chunkX(), o.chunkZ(), o.state(), Math.max(0, now - o.changedAt()) / 1000, o.why().map(w -> ": " + w).orElse("")));
        }
        java.util.List<ObjectiveRecord> planned = registers.get().objectives().stream().filter(o -> o.state() == ObjectiveRecord.State.PLANNED).toList();
        if (!planned.isEmpty()) {
            text.append("Steps planned, each started when the one before it is measured done:\n");
        }
        for (ObjectiveRecord o : planned) {
            text.append(String.format(java.util.Locale.ROOT, "- %s, step %d: %s%n", o.mission(), o.step() + 1, o.task()));
        }
        text.append("Chunks kept, what the force has made of each:\n");
        java.util.List<io.github.jayessdeesea.minecraft.command.ChunkRecord> chunks = registers.get().chunks();
        if (chunks.isEmpty()) {
            text.append("- none\n");
        }
        for (io.github.jayessdeesea.minecraft.command.ChunkRecord c : chunks) {
            text.append(String.format(java.util.Locale.ROOT, "- chunk (%d, %d), x %d..%d z %d..%d: %s at y=%d, %s%n", c.chunkX(), c.chunkZ(),
                    c.chunkX() * 16, c.chunkX() * 16 + 15, c.chunkZ() * 16, c.chunkZ() * 16 + 15, c.kind(), c.level(), c.stage().orElse("not measured")));
        }
        return text.toString().strip();
    }

    private Optional<ObjectiveRecord> open(String squad) {
        ObjectiveRecord row = open.get(squad);
        if (row == null) {
            row = registers.get().open(squad).orElse(null);
            if (row != null) {
                open.put(squad, row);
            }
        }
        return Optional.ofNullable(row);
    }

    private void keep(ObjectiveRecord row) {
        registers.get().update(row);
        if (row.state() == ObjectiveRecord.State.UNDER_WAY || row.state() == ObjectiveRecord.State.CANTCO) {
            open.put(row.squad().orElseThrow(), row);
        } else {
            open.remove(row.squad().orElseThrow());
        }
    }

    private static boolean same(ObjectiveRecord row, Objective objective) {
        return row.mission().equals(objective.mission()) && row.task().equals(OrderTrace.taskName(objective.task())) || step(row, objective);
    }

    /** Whether {@code row} is the mission's step {@code objective} is given for: said as the mission and the step's task. */
    private static boolean step(ObjectiveRecord row, Objective objective) {
        return objective.mission().equals(row.mission() + ": " + row.task());
    }

    /**
     * {@code mission}'s steps written PLANNED at {@code now}, each on its chunk with no squad yet (operator, 2026-11-21: the
     * platoon leader tracks the outcomes and the subtasks) - unless it's open already.
     */
    synchronized void planned(FarmMission mission, long now) {
        if (registers.get().objectives().stream().anyMatch(r -> r.mission().equals(mission.name()) && r.state() != ObjectiveRecord.State.DONE)) {
            return;
        }
        for (FarmMission.Step step : mission.steps()) {
            registers.get().add(new ObjectiveRecord(0, mission.name(), step.index(), step.task(), mission.chunkX(), mission.chunkZ(), Optional.empty(),
                    ObjectiveRecord.State.PLANNED, now, Optional.empty()));
        }
        LOG.info("[Objectives] {}: {} step(s) PLANNED in chunk ({}, {})", mission.name(), mission.steps().size(), mission.chunkX(), mission.chunkZ());
    }

    /** {@code mission}'s {@code step} measured done at {@code now}, {@code why} what was measured: its row DONE, its squad's no longer open. */
    synchronized void done(FarmMission mission, FarmMission.Step step, String why, long now) {
        registers.get().objectives().stream().filter(r -> r.mission().equals(mission.name()) && r.step() == step.index()
                && r.state() != ObjectiveRecord.State.DONE).forEach(r -> {
            ObjectiveRecord row = r.now(ObjectiveRecord.State.DONE, r.squad(), Optional.of(why), now);
            registers.get().update(row);
            r.squad().filter(sq -> open.containsKey(sq) && open.get(sq).id() == r.id()).ifPresent(open::remove);
            LOG.info("[Objectives] {}: {} DONE - {}", r.squad().orElse("no squad"), r.mission() + ": " + r.task(), why);
        });
    }

    /** {@code mission}'s steps not done relieved at {@code now}, {@code why}: given again for its chunk at another level. */
    synchronized void relieved(FarmMission mission, String why, long now) {
        registers.get().objectives().stream().filter(r -> r.mission().equals(mission.name()) && r.state() != ObjectiveRecord.State.DONE
                && r.state() != ObjectiveRecord.State.RELIEVED).forEach(r -> {
            registers.get().update(r.now(ObjectiveRecord.State.RELIEVED, r.squad(), Optional.of(why), now));
            r.squad().filter(sq -> open.containsKey(sq) && open.get(sq).id() == r.id()).ifPresent(open::remove);
        });
        LOG.info("[Objectives] {}: relieved - {}", mission.name(), why);
    }

    /** Each mission with a step not done - a step's squad relieved, the step stays open - and its first step not done. */
    synchronized java.util.Map<String, Integer> openMissions() {
        java.util.Map<String, Integer> missions = new java.util.LinkedHashMap<>();
        registers.get().objectives().stream().filter(r -> r.state() != ObjectiveRecord.State.DONE && FarmMission.named(r.mission()).isPresent())
                .sorted(java.util.Comparator.comparingLong(ObjectiveRecord::id)).forEach(r -> missions.merge(r.mission(), r.step(), Math::min));
        return missions;
    }

    /** The registers it keeps its rows in. */
    Registers registers() {
        return registers.get();
    }
}
