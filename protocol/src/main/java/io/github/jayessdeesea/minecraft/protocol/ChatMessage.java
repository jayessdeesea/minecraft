package io.github.jayessdeesea.minecraft.protocol;

import java.util.UUID;

/**
 * A chat line a player sent - said aloud (Player Chat), or by an unsigned command such as
 * {@code /msg} (Disguised Chat, whose sender's UUID is derived from its name, as the offline-mode
 * server does): who sent it, by UUID, and what it said.
 */
public record ChatMessage(UUID sender, String message) {
}
