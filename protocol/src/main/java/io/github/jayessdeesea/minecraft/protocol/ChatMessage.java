package io.github.jayessdeesea.minecraft.protocol;

import java.util.UUID;

/** A chat line a player sent (Player Chat): who sent it, by UUID, and what it said. */
public record ChatMessage(UUID sender, String message) {
}
