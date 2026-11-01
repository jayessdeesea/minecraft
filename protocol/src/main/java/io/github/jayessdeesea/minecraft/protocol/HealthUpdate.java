package io.github.jayessdeesea.minecraft.protocol;

/** The server's word on this player's health and food (Set Health). Health 0 means dead. */
public record HealthUpdate(float health, int food) {
}
