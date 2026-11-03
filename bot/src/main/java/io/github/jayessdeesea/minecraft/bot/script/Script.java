package io.github.jayessdeesea.minecraft.bot.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A routine as an expect script: each {@link Step} does something, then waits for what confirms it
 * - an update the server sends, read back through the soldier's own model of the world - or for its
 * deadline. A step never moves on because time has passed; it moves on because the thing it was
 * waiting for happened. A deadline missed fails the whole script, loudly, with what was expected
 * and how long it was waited for: never a guess that the server has probably answered by now.
 *
 * <p>Two ways a script ends early, kept apart on purpose:
 * <ul>
 *   <li><b>Failed</b> - a fault: a deadline missed, an answer that rules the step out, an action
 *       that can't start. Logged as a warning; the owner backs off before trying again.
 *   <li><b>Interrupted</b> - a {@link Guard} stopped holding: something more important than this
 *       work came up (a hostile near) or the work's reason went away. Not a fault, so no backoff.
 * </ul>
 * Either way the owner can simply run a new script later: a step whose result is already there
 * ({@link Step#skipIf}) is skipped, so a re-run picks up where the last one stopped.
 *
 * <p>Driven by the soldier's tick ({@link #tick}): an action runs on the tick its step starts and its
 * expectation is checked on every tick after; guards are checked every tick, after any answer that
 * has come in is recorded. Pure, apart from
 * the actions and checks it's given, so a test can drive it with fake ones. Not thread-safe: its
 * owner synchronizes.
 */
public final class Script {

    private static final Logger LOG = LogManager.getLogger(Script.class);

    /**
     * What confirms a step, said in words for when it never comes. {@code met} throws
     * {@link IllegalStateException} when what came instead rules the step out - the wrong window
     * opened - and the step fails then, not at its deadline. {@code withinTicks} is its own
     * deadline, or 0 for the step's.
     */
    public record Expect(String description, BooleanSupplier met, int withinTicks) {

        public Expect(String description, BooleanSupplier met) {
            this(description, met, 0);
        }
    }

    /** A condition the whole script runs under, said in words for when it stops holding. */
    public record Guard(String description, BooleanSupplier holds) {
    }

    /**
     * One step: {@code act} does something and says what confirms it, which must come within
     * {@code timeoutTicks} (or the expectation's own deadline). Skipped, action and all, when
     * {@code skipIf} holds as the step comes up - the thing is already done. A {@code repeat} step
     * goes round again after each confirmation until {@code skipIf} holds: a batch made as many
     * times as it takes, each round confirmed on its own.
     */
    public record Step(String name, BooleanSupplier skipIf, Supplier<Expect> act, int timeoutTicks, boolean repeat) {

        public Step {
            if (timeoutTicks <= 0) {
                throw new IllegalArgumentException("step " + name + " needs a deadline, not " + timeoutTicks + " ticks");
            }
        }

        /** A step skipped when {@code skipIf} holds: {@code action}, then {@code expect}. */
        public Step(String name, BooleanSupplier skipIf, Runnable action, Expect expect, int timeoutTicks) {
            this(name, skipIf, () -> {
                action.run();
                return expect;
            }, timeoutTicks, false);
        }

        /** A step that's never skipped. */
        public Step(String name, Runnable action, Expect expect, int timeoutTicks) {
            this(name, () -> false, action, expect, timeoutTicks);
        }

        /** A step done over and over, each time confirmed, until {@code goal} holds. */
        public static Step until(String name, BooleanSupplier goal, Runnable action, Expect expect, int timeoutTicks) {
            return new Step(name, goal, () -> {
                action.run();
                return expect;
            }, timeoutTicks, true);
        }

        /** A step done over and over until {@code goal} holds, each round saying what confirms it. */
        public static Step untilEach(String name, BooleanSupplier goal, Supplier<Expect> act, int timeoutTicks) {
            return new Step(name, goal, act, timeoutTicks, true);
        }
    }

    /** Where a script stands after a tick. */
    public sealed interface Status {
        record Running() implements Status {
        }

        record Done() implements Status {
        }

        /** Failed at {@code step}, because {@code reason}: a fault. */
        record Failed(String step, String reason) implements Status {
        }

        /** Stopped at {@code step} because {@code guard} no longer held: not a fault. */
        record Interrupted(String step, String guard) implements Status {
        }
    }

    private final String owner;
    private final List<Step> steps;
    private final List<Guard> guards;
    private int index;
    private boolean acted;
    private int waited;
    private Expect expecting;
    private Status status = new Status.Running();

    /** A script for {@code owner} - a soldier's name, for the log - with no guards. */
    public Script(String owner, List<Step> steps) {
        this(owner, steps, List.of());
    }

    /** A script for {@code owner} that runs only while every one of {@code guards} holds. */
    public Script(String owner, List<Step> steps, List<Guard> guards) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a script needs steps");
        }
        this.owner = owner;
        this.steps = List.copyOf(steps);
        this.guards = List.copyOf(guards);
    }

    public Status status() {
        return status;
    }

    /** The step under way, or the last one, once the script has ended. */
    public String currentStep() {
        return steps.get(Math.min(index, steps.size() - 1)).name();
    }

    /** Whether the step under way has acted and is now waiting on its expectation. */
    public boolean waiting() {
        return status instanceof Status.Running && acted;
    }

    /**
     * One tick: record the answer the step under way was waiting for, if it's come; then, if the
     * guards still hold, start the next step or keep waiting. An answer already in is never thrown
     * away by an interruption - the sword made is made - but nothing new is started against a
     * guard.
     */
    public Status tick() {
        if (!(status instanceof Status.Running)) {
            return status;
        }
        Step step = steps.get(index);
        if (acted) {
            waited++;
            boolean met;
            try {
                met = expecting.met().getAsBoolean();
            } catch (IllegalStateException e) {
                return fail(step, "after " + waited + " ticks: " + e.getMessage());
            }
            if (met) {
                LOG.debug("{}: {} - {} after {} ticks", owner, step.name(), expecting.description(), waited);
                acted = false;
                if (!step.repeat() || step.skipIf().getAsBoolean()) {
                    advance(); // otherwise round again: the goal doesn't hold yet
                }
                return status;
            }
        }
        for (Guard guard : guards) {
            if (!guard.holds().getAsBoolean()) {
                LOG.info("{}: '{}' interrupted - {}", owner, step.name(), guard.description());
                status = new Status.Interrupted(step.name(), guard.description());
                return status;
            }
        }
        if (acted) {
            int deadline = expecting.withinTicks() > 0 ? expecting.withinTicks() : step.timeoutTicks();
            if (waited >= deadline) {
                return fail(step, "expected " + expecting.description() + " within " + deadline + " ticks; it didn't come");
            }
            return status;
        }
        if (step.skipIf().getAsBoolean()) {
            LOG.debug("{}: {} - already done", owner, step.name());
            return advance();
        }
        try {
            expecting = step.act().get();
        } catch (IllegalStateException e) {
            return fail(step, "couldn't start: " + e.getMessage());
        }
        acted = true;
        waited = 0;
        return status;
    }

    /** Stops the script from outside - no longer wanted - as a failure with {@code reason}. */
    public Status cancel(String reason) {
        if (status instanceof Status.Running) {
            status = new Status.Failed(currentStep(), reason);
        }
        return status;
    }

    private Status advance() {
        index++;
        acted = false;
        status = index >= steps.size() ? new Status.Done() : new Status.Running();
        return status;
    }

    private Status fail(Step step, String reason) {
        LOG.warn("{}: step '{}' failed - {}", owner, step.name(), reason);
        status = new Status.Failed(step.name(), reason);
        return status;
    }
}
