package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * What a squad reports upward to a {@link General} - the input a {@code General}'s decision core
 * builds its LLM prompt context from, mirroring how {@link SoldierStatus} feeds a
 * {@link SquadLeader}. Built by the caller (the imperative shell that owns the squad's
 * {@link Soldier} instances), not returned by {@link SquadLeader} itself - a {@code SquadLeader}
 * never holds its soldiers' state, so it has nothing to report on its own.
 */
public record SquadStatus(String squadName, List<SoldierStatus> soldiers) {
}
