package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.VerifyHistory;
import io.github.jayessdeesea.minecraft.landing.VerifyPass;
import io.github.jayessdeesea.minecraft.landing.VerifyProgress;
import io.github.jayessdeesea.minecraft.landing.WatchCodec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * The watchdog's two files, each written whole by temp-and-atomic-move, each with one writer - the lock's holder - and
 * each a witness, as the task's log is: the progress of the verify running ({@code verify-progress.json}, there only
 * while one runs) and the verify history ({@code verify-history.json}). One that cannot be read or written is said
 * once, by this run, and the queue goes on without it.
 */
final class WatchFiles {

    private final Path progress;
    private final Path history;
    private final WatchCodec codec = new WatchCodec();
    private final PrintStream out;
    private boolean progressSaid;
    private boolean historySaid;

    WatchFiles(Path progress, Path history, PrintStream out) {
        this.progress = progress;
        this.history = history;
        this.out = out;
    }

    void writeProgress(VerifyProgress p) {
        try {
            write(progress, codec.write(p));
        } catch (IOException e) {
            if (!progressSaid) {
                progressSaid = true;
                out.println("the verify's progress " + progress + " not written (" + e + "): the verify goes on, its waiters "
                        + "told less");
            }
        }
    }

    /** The verify running, as its integrator last said; none when there is none, or the file is mid-replace. */
    Optional<VerifyProgress> readProgress() {
        try {
            return Files.exists(progress) ? Optional.of(codec.progress(Files.readString(progress, StandardCharsets.UTF_8)))
                    : Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Gone when a verify ends and when a run takes the lock: there only while a verify runs. */
    void clearProgress() {
        try {
            Files.deleteIfExists(progress);
        } catch (IOException e) {
            if (!progressSaid) {
                progressSaid = true;
                out.println("the verify's progress " + progress + " not deleted (" + e + "): its waiters may read it as running");
            }
        }
    }

    /** The kept passes; none there, or unreadable - said once - is no history: counts only, no estimate. */
    VerifyHistory readHistory() {
        try {
            return Files.exists(history) ? codec.history(Files.readString(history, StandardCharsets.UTF_8)) : VerifyHistory.NONE;
        } catch (IOException | IllegalArgumentException e) {
            if (!historySaid) {
                historySaid = true;
                out.println("the verify history " + history + " is unreadable (" + e.getMessage() + "): no estimate, and the "
                        + "next pass starts it again");
            }
            return VerifyHistory.NONE;
        }
    }

    /** A pass added, the oldest dropped past five. */
    void addPass(VerifyPass pass) {
        try {
            write(history, codec.write(readHistory().with(pass)));
        } catch (IOException e) {
            if (!historySaid) {
                historySaid = true;
                out.println("the pass not kept in " + history + " (" + e + "): the estimate goes without it");
            }
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(part, text, StandardCharsets.UTF_8);
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
