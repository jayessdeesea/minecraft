package io.github.jayessdeesea.minecraft.command.ai;

import java.util.List;
import java.util.Objects;

/**
 * The platoon leader's reply to one of the operator's turns: its answer in words, and what the turn's commands did, each as
 * the command said it ({@link PlatoonLeaderTools}) - computed, not in the model's words.
 *
 * @param reply its answer to the operator
 * @param done  each command it gave this turn, as its result said it; empty for none
 */
public record ChatReply(String reply, List<String> done) {

    public ChatReply {
        Objects.requireNonNull(reply, "reply");
        done = List.copyOf(done);
    }
}
