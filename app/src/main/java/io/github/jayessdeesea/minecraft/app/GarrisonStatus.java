package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.ActivityTrack;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.TaskRegister;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The garrison at one moment, for the operator to watch: each squad's objective, and each of its soldiers present -
 * what it's doing and for how long, how often that has changed, its task on the squad's register, its order, its
 * report and where it stands. Values only, read off what the runner holds ({@link StatusBoard}); nothing is decided
 * here. A field with nothing to say is null: no task given, a condition the server hasn't reported.
 */
record GarrisonStatus(long at, List<Squad> squads, java.util.Map<String, Integer> stores) {

    /** With no stores counted: none seen yet. */
    GarrisonStatus(long at, List<Squad> squads) {
        this(at, squads, java.util.Map.of());
    }

    /** The force's stores as {@code stockpile} counts them over every chest known, by what's held: "logs" to 1013. */
    static java.util.Map<String, Integer> stores(Optional<io.github.jayessdeesea.minecraft.command.Stockpile> stockpile) {
        java.util.Map<String, Integer> held = new java.util.LinkedHashMap<>();
        stockpile.ifPresent(s -> {
            for (java.lang.reflect.RecordComponent part : io.github.jayessdeesea.minecraft.command.Stockpile.class.getRecordComponents()) {
                try {
                    held.put(part.getName(), (Integer) part.getAccessor().invoke(s));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("the stockpile's " + part.getName() + " can't be read", e);
                }
            }
        });
        return held;
    }


    /** A squad: the platoon leader's objective for it, in the mission's words and its intent, and who's present. */
    record Squad(String name, String objective, String intent, List<Soldier> soldiers, String task, String progress) {
        /** With nothing measured of its task yet. */
        static Squad of(String name, Optional<Objective> objective, List<Soldier> soldiers) {
            return of(name, objective, soldiers, Optional.empty());
        }

        /**
         * As above, with its task as the code names it - MINE, KEEP, FARM - whatever words the objective was given in, and
         * {@code progress}, what was last measured of it: "the stairs cut down to y=12, worked on for its ore". The platoon
         * leader's model words an objective as it likes ("Mine 100 cobblestone ..."); these two are computed.
         */
        static Squad of(String name, Optional<Objective> objective, List<Soldier> soldiers, Optional<String> progress) {
            return new Squad(name, objective.map(Objective::mission).orElse(null), objective.map(Objective::intent).orElse(null), soldiers,
                    objective.map(o -> OrderTrace.taskName(o.task())).orElse(null), progress.orElse(null));
        }
    }

    /**
     * A soldier: its unit (its squad, or the detachment it's in), its activity and the seconds it has held it, the routine
     * that has its hands, its
     * changes of activity since it joined and how many were returns ({@link ActivityTrack}), its task and that
     * task's state on the register, its order, its report, its feet, and its health and food.
     */
    record Soldier(String name, String unit, String activity, String hands, Long seconds, Integer changes, Integer returns, String task, String taskState,
                   String order, String report, long x, long y, long z, Double health, Integer food, java.util.Map<String, Integer> carries) {
        /** Carrying nothing counted: its pack not read yet. */
        static Soldier of(SoldierStatus status, String unit, Optional<ActivityTrack.Now> now, Optional<TaskRegister.Tasking> tasking, long at) {
            return of(status, unit, now, tasking, at, java.util.Map.of());
        }

        static Soldier of(SoldierStatus status, String unit, Optional<ActivityTrack.Now> now, Optional<TaskRegister.Tasking> tasking, long at,
                          java.util.Map<String, Integer> carries) {
            return new Soldier(status.name(), unit,
                    now.map(n -> n.activity().label()).orElse(null), now.flatMap(ActivityTrack.Now::hands).map(h -> h.name().toLowerCase(Locale.ROOT).replace('_', ' ')).orElse(null),
                    now.map(n -> (at - n.since()) / 1000).orElse(null),
                    now.map(ActivityTrack.Now::changes).orElse(null), now.map(ActivityTrack.Now::returns).orElse(null),
                    tasking.map(TaskRegister.Tasking::task).orElse(null), tasking.map(t -> t.state().name()).orElse(null),
                    inWords(status.order()), status.report().toString(),
                    (long) Math.floor(status.position().x()), (long) Math.floor(status.position().y()), (long) Math.floor(status.position().z()),
                    status.condition().map(Condition::health).orElse(null), status.condition().map(Condition::food).orElse(null), new java.util.TreeMap<>(carries));
        }
    }

    /** An order in a few words: its kind, and where for a move. */
    static String inWords(Order order) {
        return order instanceof Order.MoveTo to ? String.format(Locale.ROOT, "MoveTo (%.0f, %.0f, %.0f)", to.x(), to.y(), to.z())
                : order.getClass().getSimpleName();
    }

    /**
     * A squad's SITREP for the log, a line a soldier: where it is, its report and its condition - and whether the
     * world model has any block data there, since ground it has none for reads as solid and a soldier in it can go
     * nowhere.
     */
    static String sitrep(List<SoldierStatus> statuses, Predicate<SoldierStatus> blocksKnownAt) {
        StringBuilder sitrep = new StringBuilder();
        for (SoldierStatus status : statuses) {
            sitrep.append(String.format(Locale.ROOT, "%n  %s at (%.1f, %.1f, %.1f)%s: %s %s",
                    status.name(), status.position().x(), status.position().y(), status.position().z(),
                    blocksKnownAt.test(status) ? "" : " [NO BLOCK DATA HERE]", status.report(),
                    status.condition().map(Object::toString).orElse("(condition not reported)")));
        }
        return sitrep.toString();
    }
}
