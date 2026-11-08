package io.github.jayessdeesea.minecraft.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simulated time for a squad simulation: every scheduler and thread the app asks {@link Clockwork}
 * for is a queue on this one clock, run in time order on the test's own thread. A simulated hour
 * takes as long as its work, and a run is the same every time. A task that throws fails the
 * simulation at once, with the task's name.
 */
final class SimClock implements Clockwork {

    private long now = 1_000_000;
    private long sequence;
    private final PriorityQueue<Task> queue = new PriorityQueue<>((a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.sequence, b.sequence));

    @Override
    public long millis() {
        return now;
    }

    @Override
    public ScheduledExecutorService scheduler(String name) {
        return new Executor(name);
    }

    /** Runs everything due up to {@code millis} from now, in time order. */
    void advance(long millis) {
        long until = now + millis;
        while (!queue.isEmpty() && queue.peek().at <= until) {
            Task task = queue.poll();
            if (task.cancelled || task.owner.shutdown) {
                continue;
            }
            now = Math.max(now, task.at);
            try {
                task.body.run();
            } catch (RuntimeException | Error e) {
                throw new AssertionError("simulated task on " + task.owner.name + " failed at " + now, e);
            }
            if (task.period > 0 && !task.cancelled && !task.owner.shutdown) {
                task.at += task.period;
                task.sequence = sequence++;
                queue.add(task);
            }
        }
        now = until;
    }

    private Task enqueue(Executor owner, Runnable body, long delay, long period) {
        Task task = new Task(owner, body, now + Math.max(0, delay), period, sequence++);
        queue.add(task);
        return task;
    }

    private final class Task implements ScheduledFuture<Object> {
        final Executor owner;
        final Runnable body;
        long at;
        final long period;
        long sequence;
        boolean cancelled;

        Task(Executor owner, Runnable body, long at, long period, long sequence) {
            this.owner = owner;
            this.body = body;
            this.at = at;
            this.period = period;
            this.sequence = sequence;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(at - now, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterrupt) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("a simulated scheduled task is never waited on");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return get();
        }
    }

    /** One named "thread": its work goes on the shared clock's queue. */
    private final class Executor extends AbstractExecutorService implements ScheduledExecutorService {
        final String name;
        boolean shutdown;

        Executor(String name) {
            this.name = name;
        }

        @Override
        public void execute(Runnable command) {
            enqueue(this, command, 0, 0);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return enqueue(this, command, unit.toMillis(delay), 0);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("simulated: schedule(Callable)");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return enqueue(this, command, unit.toMillis(initialDelay), unit.toMillis(period));
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return enqueue(this, command, unit.toMillis(initialDelay), unit.toMillis(delay));
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("simulated: invokeAll");
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException {
            throw new UnsupportedOperationException("simulated: invokeAny");
        }

        @Override
        protected <T> java.util.concurrent.RunnableFuture<T> newTaskFor(Callable<T> callable) {
            return new FutureTask<>(callable);
        }
    }
}
