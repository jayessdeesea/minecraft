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
 * {@code standing} is the objective the general last gave it, if any - an order stands until the
 * general changes it - and {@code holdingBase} whether the squad is sealed inside its patrol base.
 */
public record SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                          java.util.Optional<Objective> standing, boolean holdingBase) {

    public SquadStatus {
        resources = List.copyOf(resources);
    }

    /** A squad with no standing order. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources) {
        this(squadName, report, soldiers, resources, java.util.Optional.empty(), false);
    }

    /** A squad with no standing order that has seen nothing to gather. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers) {
        this(squadName, report, soldiers, List.of());
    }
}
