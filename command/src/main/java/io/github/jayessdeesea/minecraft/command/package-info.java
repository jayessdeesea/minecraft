/**
 * Chain-of-command interfaces and value types: {@code General},
 * {@code SquadLeader}, {@code Soldier}, {@code Order}, {@code Objective}.
 * Framework-agnostic on purpose - no Spring, no Netty. Implementations of
 * {@code General}/{@code SquadLeader} live in the {@code command-ai} module;
 * an implementation of {@code Soldier} lives in the {@code bot} module. Both
 * depend on this package; neither depends on the other.
 */
package io.github.jayessdeesea.minecraft.command;
