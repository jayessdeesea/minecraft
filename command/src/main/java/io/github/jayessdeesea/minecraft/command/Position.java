package io.github.jayessdeesea.minecraft.command;

/**
 * A world-space position. Deliberately its own type here rather than reused from {@code protocol}
 * - {@code command} depends on nothing but the JDK (see AGENTS.md's module layout), and a
 * soldier's position is a fact the chain of command reasons about regardless of where it came from
 * on the wire.
 */
public record Position(double x, double y, double z) {
}
