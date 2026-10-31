package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

/**
 * An entity some soldier's connection has seen. {@code position} is last-known (where it was when
 * it came into view) - nothing decodes entity movement yet.
 */
public record TrackedEntity(int entityId, int typeId, Position position) {
}
