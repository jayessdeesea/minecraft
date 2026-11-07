package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * Where a camp stands in building a lodgment, the way an airborne force builds one after the drop
 * (docs/military-doctrine.md §17): measured, not judged - each phase's standards checked against
 * the world, so the general works on the earliest phase not yet met instead of reading progress
 * off a mission sentence.
 *
 * @param camp      the camp's ground
 * @param standards each phase's standards, in phase order
 */
public record Foothold(Position camp, List<Standard> standards) {

    /**
     * One standard of a phase: whether it's met, how far along it is, and the task that advances it.
     *
     * @param phase  1 shelter, 2 airhead, 3 buildup, 4 equipment
     * @param name   what's measured: "the camp's ground lit"
     * @param met    whether it's met
     * @param status how it stands, in plain words: "18 cells still dark"
     * @param task   the general's task that advances it: "SECURE"
     */
    public record Standard(int phase, String name, boolean met, String status, String task) {
    }

    /** The earliest phase with a standard not met, or empty when all are. */
    public java.util.OptionalInt nextPhase() {
        return standards.stream().filter(s -> !s.met()).mapToInt(Standard::phase).min();
    }
}
