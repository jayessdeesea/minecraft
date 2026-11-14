package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a squad reports upward to a {@link General} - the input a {@code General}'s decision core
 * builds its LLM prompt context from, mirroring how {@link SoldierStatus} feeds a
 * {@link SquadLeader}. Built by the caller (the imperative shell that owns the squad's
 * {@link Soldier} instances), not returned by {@link SquadLeader} itself - a {@code SquadLeader}
 * never holds its soldiers' state. {@code report} is the squad leader's own line - CANTCO when it
 * judged the objective impossible (see {@link SquadLeader#report}). {@code resources} are the
 * materials the squad can see from where it is (docs/military-doctrine.md §12), nearest first.
 * {@code standing} is the objective the general last gave it, if any - an order stands until the
 * general changes it - {@code holdingBase} whether the squad is sealed inside its patrol base, and
 * {@code baseBlocksLeft} how many blocks of the base it has laid out are still to place (empty
 * while it has none laid out), {@code progress} how the standing order's task stands (empty
 * with no task to measure), {@code classOne} its food (empty where not measured): RED is its
 * request for food ({@link Subsistence}) - and {@code classFour} its construction materials, said
 * only as a request, when its task wants logs it hasn't got ({@link Keep#classFour}).
 */
public record SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                          Optional<Objective> standing, boolean holdingBase, OptionalInt baseBlocksLeft,
                          Optional<Progress> progress, Optional<Subsistence.Status> classOne, Optional<String> classFour) {

    public SquadStatus {
        resources = List.copyOf(resources);
        if (holdingBase && baseBlocksLeft.orElse(-1) != 0) {
            throw new IllegalArgumentException("holding a base with " + baseBlocksLeft + " blocks of it left to place");
        }
    }

    /** A squad with no Class IV request. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                       Optional<Objective> standing, boolean holdingBase, OptionalInt baseBlocksLeft,
                       Optional<Progress> progress, Optional<Subsistence.Status> classOne) {
        this(squadName, report, soldiers, resources, standing, holdingBase, baseBlocksLeft, progress, classOne, Optional.empty());
    }

    /** A squad whose task's progress isn't measured. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                       Optional<Objective> standing, boolean holdingBase, OptionalInt baseBlocksLeft) {
        this(squadName, report, soldiers, resources, standing, holdingBase, baseBlocksLeft, Optional.empty());
    }

    /** A squad whose food isn't measured. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                       Optional<Objective> standing, boolean holdingBase, OptionalInt baseBlocksLeft,
                       Optional<Progress> progress) {
        this(squadName, report, soldiers, resources, standing, holdingBase, baseBlocksLeft, progress, Optional.empty());
    }

    /** A squad holding a finished base, or with none laid out. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources,
                       Optional<Objective> standing, boolean holdingBase) {
        this(squadName, report, soldiers, resources, standing, holdingBase,
                holdingBase ? OptionalInt.of(0) : OptionalInt.empty());
    }

    /** A squad with no standing order. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers, List<ResourceSite> resources) {
        this(squadName, report, soldiers, resources, Optional.empty(), false);
    }

    /** A squad with no standing order that has seen nothing to gather. */
    public SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers) {
        this(squadName, report, soldiers, List.of());
    }
}
