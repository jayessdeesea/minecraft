package io.github.jayessdeesea.minecraft.landing.shell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * One integrator: a lock the kernel holds for its holder's process life and drops when that process ends, however it
 * ends - no staleness, no breaking, no heartbeat. The file is never deleted. Held by another process
 * ({@code tryLock} gives none) or already in this one ({@code OverlappingFileLockException}): both are held.
 */
public final class IntegratorLock {

    private final Path file;

    public IntegratorLock(Path file) {
        this.file = file;
    }

    /** The lock, if no one holds it. */
    public Optional<Held> tryAcquire() {
        FileChannel channel;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("the lock file " + file + " not opened", e);
        }
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return Optional.of(new Held(channel, lock));
            }
        } catch (OverlappingFileLockException held) {
            // held in this JVM: as held as by another process
        } catch (IOException e) {
            close(channel);
            throw new UncheckedIOException("the lock " + file + " not tried", e);
        }
        close(channel);
        return Optional.empty();
    }

    private static void close(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("the lock file not closed", e);
        }
    }

    /** The lock held, until closed. */
    public static final class Held implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private Held(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
                channel.close();
            } catch (IOException e) {
                throw new UncheckedIOException("the lock not released", e);
            }
        }
    }
}
