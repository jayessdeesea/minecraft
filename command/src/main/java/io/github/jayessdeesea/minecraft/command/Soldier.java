package io.github.jayessdeesea.minecraft.command;

/**
 * The chain-of-command view of a soldier: receive a goal, report status. Deliberately doesn't
 * expose ticking, world queries, or anything connection-related - those are {@code bot}'s own
 * concern (see {@code DeterministicSoldier}), not something a {@code SquadLeader} should be able
 * to reach past this interface to touch.
 */
public interface Soldier {

    void receiveOrder(Order order);

    SoldierStatus status();
}
