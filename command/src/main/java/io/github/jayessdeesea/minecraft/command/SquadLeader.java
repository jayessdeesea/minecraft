package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * The chain-of-command view of a squad leader: receive an {@link Objective}, decide {@link Order}s
 * for its soldiers. Deliberately doesn't hold its soldiers' state - a {@code SquadLeader} is not
 * itself a Minecraft entity or protocol connection (see AGENTS.md's Objective section); the
 * imperative shell that owns the actual {@link Soldier} instances passes their current
 * {@link SoldierStatus}es in and applies the returned {@link Order}s back out.
 */
public interface SquadLeader {

    String name();

    void receiveObjective(Objective objective);

    /**
     * Decides an {@link Order} for each soldier. The returned list has exactly the same length and
     * order as {@code soldierStatuses} - element {@code i} of the result is the order for the
     * soldier described by element {@code i} of the input.
     */
    List<Order> decide(List<SoldierStatus> soldierStatuses);
}
