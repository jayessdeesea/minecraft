package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Report;

import java.util.Optional;

/**
 * What a soldier is doing, as one value: fighting, recovering, the task its squad leader has it on, or none. One
 * at a time by construction - two can't be written down.
 *
 * <p>It's the task, not the step of it: a farmer tilling a cell, walking to the next and standing at it reports
 * farming, moving and in position by turns, and a routine takes its hands and lets them go - all one task. The
 * routine that has its hands at the moment ({@link Hands}) is kept beside the activity
 * ({@link ActivityTrack.Now#hands}); a change of it, or of the report, is no change of activity.
 *
 * <p>Read off what the tick already holds ({@link #of}). It doesn't decide anything yet; it names what was
 * decided, so each change can be logged and counted (backlog/soldier-state-machine.md).
 */
public sealed interface SoldierActivity {

    /** In contact: its fight has it, whatever its task was. */
    record Fighting() implements SoldierActivity {
        @Override
        public String label() {
            return "fighting";
        }
    }

    /** Getting back to where it can carry on: out of water, a pit, a block it's stuck in. */
    record Recovering() implements SoldierActivity {
        @Override
        public String label() {
            return "recovering";
        }
    }

    /** On the task its squad leader gave it, as the squad's register names it. */
    record OnTask(String task) implements SoldierActivity {
        @Override
        public String label() {
            return task;
        }
    }

    /** No task of its own: with its squad, moving with it or holding its place. */
    record Unassigned() implements SoldierActivity {
        @Override
        public String label() {
            return "no task";
        }
    }

    /** In a few words, for the log and the operator. */
    String label();

    /** The activity of a soldier reporting {@code report}, on {@code task} if its squad leader gave it one. */
    static SoldierActivity of(Report report, Optional<String> task) {
        return switch (report) {
            case Report.InContact contact -> new Fighting();
            case Report.Recovering recovering -> new Recovering();
            default -> task.<SoldierActivity>map(OnTask::new).orElseGet(Unassigned::new);
        };
    }
}
