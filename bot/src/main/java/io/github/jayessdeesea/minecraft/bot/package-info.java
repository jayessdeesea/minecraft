/**
 * Deterministic soldier runtime: implements {@code command.Soldier} and
 * drives a real protocol connection every tick. No LLM calls happen in this
 * module or on this loop - see docs/architecture.md's latency-architecture section.
 */
package io.github.jayessdeesea.minecraft.bot;
