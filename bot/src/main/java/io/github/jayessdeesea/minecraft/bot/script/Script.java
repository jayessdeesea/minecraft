package io.github.jayessdeesea.minecraft.bot.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A routine as an expect script: each {@link Step} does something, then waits for what confirms it
 * - an update the server sends, read back through the soldier's own model of the world - or for its
 * deadline. A step never moves on because time has passed; it moves on because the thing it was
 * waiting for happened. A deadline missed fails the whole script, loudly, with what was expected
 * and how long it was waited for: never a guess that the server has probably answered by now.
 *
 * <p>Driven by the soldier's tick ({@link #tick}): an action runs on the tick its step starts; its
 * expectation is checked on every tick after. Pure, apart from the actions and checks it's given,
 * so a test can drive it with fake ones. Not thread-safe: its owner synchronizes.
 */
public final class Script {

    private static final Logger LOG = LogManager.getLogger(Script.class);

    /**
     * What confirms a step, said in words for when it never comes. {@code met} throws
     * {@link IllegalStateException} when what came instead rules the step out - the wrong window
     * opened - and the step fails then, not at its deadline.
     */
    public record Expect(String description, BooleanSupplier met) {
    }

    /**
     * One step: {@code action}, then {@code expect} within {@code timeoutTicks}. Skipped, action and
     * all, when {@code skipIf} holds as the step comes up - the thing is already done.
     */
    public record Step(String name, BooleanSupplier skipIf, Runnable action, Expect expect, int timeoutTicks) {

        public Step {
            if (timeoutTicks <= 0) {
                throw new IllegalArgumentException("step " + name + " needs a deadline, not " + timeoutTicks + " ticks");
            }
        }

        /** A step that's never skipped. */
        public Step(String name, Runnable action, Expect expect, int timeoutTicks) {
            this(name, () -> false, action, expect, timeoutTicks);
        }
    }

    /** Where a script stands after a tick. */
    public sealed interface Status {
        record Running() implements Status {
        }

        record Done() implements Status {
        }

        /** Failed at {@code step}, because {@code reason}. */
        record Failed(String step, String reason) implements Status {
        }
    }

    private final String owner;
    private final List<Step> steps;
    private int index;
    private boolean acted;
    private int waited;
    private Status status = new Status.Running();

    /** A script for {@code owner} - a soldier's name, for the log. */
    public Script(String owner, List<Step> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a script needs steps");
        }
        this.owner = owner;
        this.steps = List.copyOf(steps);
    }

    public Status status() {
        return status;
    }

    /** The step under way, or the last one, once the script has ended. */
    public String currentStep() {
        return steps.get(Math.min(index, steps.size() - 1)).name();
    }

    /** One tick: start the step that's next, or check the one under way against its expectation and deadline. */
    public Status tick() {
        if (!(status instanceof Status.Running)) {
            return status;
        }
        Step step = steps.get(index);
        if (!acted) {
            if (step.skipIf().getAsBoolean()) {
                LOG.debug("{}: {} - already done, skipped", owner, step.name());
                return advance();
            }
            try {
                step.action().run();
            } catch (IllegalStateException e) {
                return fail(step, "couldn't start: " + e.getMessage());
            }
            acted = true;
            waited = 0;
            return status;
        }
        waited++;
        boolean met;
        try {
            met = step.expect().met().getAsBoolean();
        } catch (IllegalStateException e) {
            return fail(step, "after " + waited + " ticks: " + e.getMessage());
        }
        if (met) {
            LOG.debug("{}: {} - {} after {} ticks", owner, step.name(), step.expect().description(), waited);
            return advance();
        }
        if (waited >= step.timeoutTicks()) {
            return fail(step, "expected " + step.expect().description() + " within " + step.timeoutTicks() + " ticks; it didn't come");
        }
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
