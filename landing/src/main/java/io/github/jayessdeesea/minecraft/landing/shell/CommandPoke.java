package io.github.jayessdeesea.minecraft.landing.shell;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The poke as a command - {@code schtasks /run /tn mc-landing} - whose failure is said and is not fatal. Past its time
 * it is ended, with all it started, through {@link Children}, as every child of landing is.
 */
public final class CommandPoke implements Poke {

    /** How long the scheduler is given to take the poke. */
    public static final Duration WAIT = Duration.ofSeconds(30);

    private final List<String> command;
    private final PrintStream out;
    private final Children children;
    private final Duration wait;

    public CommandPoke(List<String> command, PrintStream out, Children children, Duration wait) {
        this.command = List.copyOf(command);
        this.out = out;
        this.children = children;
        this.wait = wait;
    }

    @Override
    public void poke() {
        if (command.isEmpty()) {
            return;
        }
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                boolean gone = children.kill(p.toHandle());
                out.println("the poke failed: " + command + " ran past " + wait + (gone ? "" : ", its processes not proven gone")
                        + "; the scheduler will come");
            } else if (p.exitValue() != 0) {
                out.println("the poke failed: " + command + " exited " + p.exitValue() + "; the scheduler will come");
            }
        } catch (IOException e) {
            out.println("the poke failed: " + command + " did not run (" + e.getMessage() + "); the scheduler will come");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println("the poke failed: interrupted; the scheduler will come");
        }
    }
}
