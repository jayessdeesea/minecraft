package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * What a squad reports upward to a {@link General} - the input a {@code General}'s decision core
 * builds its LLM prompt context from, mirroring how {@link SoldierStatus} feeds a
 * {@link SquadLeader}. Built by the caller (the imperative shell that owns the squad's
 * {@link Soldier} instances), not returned by {@link SquadLeader} itself - a {@code SquadLeader}
 * never holds its soldiers' state. {@code report} is the squad leader's own line - CANTCO when it
 * judged the objective impossible (see {@link SquadLeader#report}).
 */
public record SquadStatus(String squadName, Report report, List<SoldierStatus> soldiers) {
}
