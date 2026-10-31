package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

/**
 * An entity some soldier's connection can see, at its latest server-sent position.
 */
public record TrackedEntity(int entityId, int typeId, Position position) {
}
