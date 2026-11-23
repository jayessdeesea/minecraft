package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.ActivityTrack;

import java.util.Optional;

/**
 * One soldier's state between ticks, built with its connection: a soldier that reconnects starts it empty. Each
 * field says who writes it; what's read across threads is one value, swapped whole.
 *
 * <p>The home the runner's maps keyed by a soldier's name move into, a routine family at a time
 * (backlog/soldier-state-machine.md); its task and its activity are the first of it.
 */
final class SoldierState {

    /** The task its squad leader has it on this cycle, as the squad's register names it - the squad loop writes, its tick reads. */
    volatile Optional<String> task = Optional.empty();

    /** What it carries, by class of item - "cobblestone" to 64 - as its tick last counted it (ErrandLedger.carried): its tick writes. */
    volatile java.util.Map<String, Integer> carried = java.util.Map.of();
    /** What the soldier is doing, and how often that has changed - its tick writes. */
    final ActivityTrack activity = new ActivityTrack();
}
