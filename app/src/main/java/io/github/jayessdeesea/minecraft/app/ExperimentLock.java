package io.github.jayessdeesea.minecraft.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file that says a live experiment is running on this machine: the scenario harness holds it for
 * a run, and an app told to yield to experiments ({@code minecraft.yield-to-experiments}) - a
 * garrison - stands its squads still and asks no model anything while it's there. One GPU serves
 * every model call on the machine; an experiment measures pass rates and decision times that
 * another app's calls would skew.
 */
final class ExperimentLock implements AutoCloseable {

    /** Where the lock lives: the machine's temp directory, which both apps see. */
    static final Path PATH = Path.of(System.getProperty("java.io.tmpdir"), "minecraft-experiment.lock");

    private ExperimentLock() {
    }

    /** Takes the lock for a run; closing it gives it back. */
    static ExperimentLock hold() {
        try {
            // Written aside and moved into place, so a reader never sees it half-written.
            Path written = Files.writeString(Files.createTempFile(PATH.getParent(), "minecraft-experiment", ".tmp"),
                    Long.toString(ProcessHandle.current().pid()));
            Files.move(written, PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("can't take the experiment lock at " + PATH, e);
        }
        return new ExperimentLock();
    }

    /** What the lock says now. */
    enum State {
        FREE,
        HELD,
        /** Its holder has gone without giving it back - a run killed mid-way. Not held. */
        STALE
    }

    static State state() {
        try {
            if (!Files.exists(PATH)) {
                return State.FREE;
            }
            long holder = Long.parseLong(Files.readString(PATH).strip());
            return ProcessHandle.of(holder).filter(ProcessHandle::isAlive).isPresent() ? State.HELD : State.STALE;
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("can't read the experiment lock at " + PATH, e);
        }
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(PATH);
        } catch (IOException e) {
            throw new UncheckedIOException("can't give back the experiment lock at " + PATH, e);
        }
    }
}
