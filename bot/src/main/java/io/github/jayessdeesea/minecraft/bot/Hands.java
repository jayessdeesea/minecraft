package io.github.jayessdeesea.minecraft.bot;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

/**
 * Which routine has a soldier's hands. One soldier can work one routine at a time: one window open,
 * one thing in hand, one place to stand. Routines are ticked in priority order, and each is told
 * whether it's free to start or carry on: free while no other routine is under way. So one under
 * way keeps the hands until it's done, fails or its own guard interrupts it, and of two that would
 * start on the same tick the one ranked first does - it's under way by the time the next asks.
 *
 * <p>Nothing here preempts. A routine that should give way mid-run - under threat, say - says so in
 * its own guard (docs/architecture.md § Behavior modeling), which knows when stopping is safe: a handover
 * stops before its first throw, never after.
 */
public final class Hands {

    /** Every routine that needs the soldier's hands, highest priority first. */
    public enum Routine {
        /** Drawing kit from the supply chest. */
        SUPPLY_RUN,
        /** Cutting out of a base the squad is leaving. */
        DOORWAY,
        /** A torchbearer's run at its workshop. */
        TORCHMAKER,
        /** Eating. */
        RATIONS,
        /** Stuck in water: plugging the water round it with blocks, to stop the flow and climb out. */
        SEALER,
        /** Killing an animal for food, with nothing left to eat. */
        HUNTER,
        /** Making its own kit: a weapon, tools, armour. */
        ARMOURER,
        /** Walling in a patrol base. */
        BUILDER,
        /** Building a structure laid out for it: {@code Order.Place}. */
        PLACING,
        /** Taking a full load to the stockpile. */
        DEPOSITOR,
        /** Handing logs to a squadmate. */
        HANDOVER,
        /** Cutting the squad's staircase down. */
        MINER,
        /** Clearing the trees over a camp, before its wall. */
        CLEARER,
        /** Filling a camp's hollows, before its wall. */
        FILLER,
        /** Building the wall round a camp. */
        MASON,
        /** Putting torches down on the ground the squad secures. */
        LAMPLIGHTER,
        /** Keeping the squad's farm. */
        FARMER,
        /** Planting the squad's tree farm. */
        FORESTER,
        /** Chopping or quarrying for the squad. */
        GATHERER,
        /** Picking up what lies near: a spider's string, a chicken's feather. */
        SCAVENGER
    }

    /** One routine's turn: whether it's under way, and its tick, told whether it's free. */
    public interface Turn {
        boolean active();

        void tick(boolean free);
    }

    private Hands() {
    }

    /**
     * Ticks every routine once, in priority order.
     *
     * @return the routine under way after this tick, if any
     * @throws IllegalArgumentException unless every routine has its turn - a routine left out
     *                                  would never be asked, and never block another
     */
    public static Optional<Routine> tick(Map<Routine, Turn> turns) {
        if (!turns.keySet().equals(EnumSet.allOf(Routine.class))) {
            EnumSet<Routine> missing = EnumSet.allOf(Routine.class);
            missing.removeAll(turns.keySet());
            throw new IllegalArgumentException("no turn for " + missing);
        }
        for (Routine routine : Routine.values()) {
            boolean free = turns.entrySet().stream().noneMatch(other -> other.getKey() != routine && other.getValue().active());
            turns.get(routine).tick(free);
        }
        for (Routine routine : Routine.values()) {
            if (turns.get(routine).active()) {
                return Optional.of(routine);
            }
        }
        return Optional.empty();
    }
}
