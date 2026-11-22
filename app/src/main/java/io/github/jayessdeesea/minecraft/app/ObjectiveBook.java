package io.github.jayessdeesea.minecraft.app;

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
        ObjectiveRecord row = registers.get().add(new ObjectiveRecord(0, objective.mission(), 0, OrderTrace.taskName(objective.task()),
                Math.floorDiv((int) Math.floor(objective.location().x()), 16), Math.floorDiv((int) Math.floor(objective.location().z()), 16),
                Optional.of(squad), ObjectiveRecord.State.UNDER_WAY, now, Optional.empty()));
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
        return row.mission().equals(objective.mission()) && row.task().equals(OrderTrace.taskName(objective.task()));
    }
}
