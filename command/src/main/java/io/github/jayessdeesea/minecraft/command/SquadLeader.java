package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Optional;

/**
 * The chain-of-command view of a squad leader: receive an {@link Objective}, decide how the squad
 * carries it out. Deliberately doesn't hold its soldiers' state - a {@code SquadLeader} is not
 * itself a Minecraft entity or protocol connection (AGENTS.md's Objective section); the imperative
 * shell that owns the {@link Soldier}s passes their {@link SoldierStatus}es (SITREPs) in.
 *
 * <p>The decision is a {@link SquadOrder} - objective, formation, interval, intent - not a
 * per-soldier order: turning that into each soldier's slot is geometry, done deterministically by
 * {@link Formations}, so the LLM spends its judgement only on what needs judgement.
 */
public interface SquadLeader {

    String name();

    void receiveObjective(Objective objective);

    /** The overworld's time of day, as the server last sent it. */
    void receiveTime(TimeOfDay time);

    /**
     * Each of its soldiers' standing plans, as kept: what it last made each of them, and what each
     * still has to do first; and, by soldier, how its work order stands - under way and what's left,
     * or lately timed out and why ({@link WorkOrders}; docs/military-doctrine.md § 18).
     */
    void receivePlans(java.util.Map<String, Plan> plans, java.util.Map<String, String> workOrders);

    /**
     * The squad leader's own SITREP line upward - notably {@link Report.Cantco} when it judged the
     * objective impossible (docs/military-doctrine.md §3), so the general can re-task the squad.
     */
    Report report();

    /**
     * How the squad moves now - empty until an objective has been received. {@code terrain} is the
     * ground around the point man (see {@link TerrainSurvey}), so the objective can be one the
     * squad can actually reach.
     */
    Optional<SquadOrder> decide(List<SoldierStatus> soldierStatuses, TerrainSurvey terrain);
}
