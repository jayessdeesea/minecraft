package io.github.jayessdeesea.minecraft.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Where the app's time and threads come from: the wall clock and real threads ({@link #REAL}), or
 * a simulation's, which runs the same code on simulated time with no server and no model
 * (backlog/echelon-contracts.md). Every clock read and every thread the chain of command and the
 * soldiers run on comes from here.
 */
public interface Clockwork {

    /** Milliseconds, in the sense of {@link System#currentTimeMillis()}. */
    long millis();

    /** A single thread that runs scheduled work - a soldier's tick, the squad loop. */
    ScheduledExecutorService scheduler(String name);

    /** A single thread that runs submitted work in order - a model call. */
    default ExecutorService thread(String name) {
        return scheduler(name);
    }

    /** The wall clock, and a real daemon-less thread per name, as the app always ran. */
    Clockwork REAL = new Clockwork() {
        @Override
        public long millis() {
            return System.currentTimeMillis();
        }

        @Override
        public ScheduledExecutorService scheduler(String name) {
            return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name));
        }

        @Override
        public ExecutorService thread(String name) {
            return Executors.newSingleThreadExecutor(r -> new Thread(r, name));
        }
    };
}
