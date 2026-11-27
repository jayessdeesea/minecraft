package io.github.jayessdeesea.minecraft.landing.shell;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The task's own log, {@code landing-task.log} beside the jar: what integrate and job-check print, so a failure before
 * the journal opens (exit 6) is written somewhere. Each write opens the file to append and closes it - a second process
 * appends beside it and neither fails, and a rotation is followed at the next line. Kept to 1 MB and one file before it.
 * The log is the run's witness, never its master: a log that cannot be written or rotated is said once and the run goes
 * on.
 */
final class TaskLog {

    /** Past this the log is moved aside, by the run that holds the integrator's lock. */
    static final long MOST_BYTES = 1024 * 1024;

    private TaskLog() {
    }

    /** {@code out}, and the log too; the first write the log refuses is said on {@code err}, the rest dropped quietly. */
    static PrintStream tee(PrintStream out, Path log, PrintStream err) {
        AtomicBoolean said = new AtomicBoolean();
        OutputStream both = new OutputStream() {
            @Override
            public void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] bytes, int off, int len) {
                out.write(bytes, off, len);
                try (OutputStream file = Files.newOutputStream(log, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    file.write(bytes, off, len);
                } catch (IOException e) {
                    if (!said.getAndSet(true)) {
                        err.println("the task's log " + log + " not written (" + e + "): the run goes on without it");
                    }
                }
            }

            @Override
            public void flush() {
                out.flush();
            }
        };
        return new PrintStream(both, true, StandardCharsets.UTF_8);
    }

    /** A log over {@link #MOST_BYTES} moved to {@code .1}, replacing it; only the lock's holder calls this. */
    static void rotate(Path log, PrintStream err) {
        try {
            if (Files.isRegularFile(log) && Files.size(log) > MOST_BYTES) {
                Files.move(log, log.resolveSibling(log.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            err.println("the task's log " + log + " not rotated (" + e + "): the run goes on");
        }
    }
}
