package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * What a squad reports upward to a {@link General} - the input a {@code General}'s decision core
 * builds its LLM prompt context from, mirroring how {@link SoldierStatus} feeds a
 * {@link SquadLeader}. Built by the caller (the imperative shell that owns the squad's
 * {@link Soldier} instances), not returned by {@link SquadLeader} itself - a {@code SquadLeader}
 * never holds its soldiers' state. {@code report} is the squad leader's own line - CANTCO when it
 * judged the objective impossible (see {@link SquadLeader#report}). {@code resources} are the
 * materials the squad can see from where it is (docs/military-doctrine.md §12), nearest first.
 */
public record SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources) {

    public SquadStatus {
        resources = List.copyOf(resources);
    }

    /** A squad that has seen nothing to gather. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers) {
        this(squadName, report, soldiers, List.of());
    }
}
