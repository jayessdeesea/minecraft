package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.JournalCodec;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The journal: one file of JSON lines, kept whole, appended to only by the integrator holding the lock, each record
 * forced to disk before the append returns. A record is its line and its line ending: a crash mid-append leaves a last
 * line with no ending, which was never written - it is not read, and the next append cuts it off. A whole line that is
 * not a record is corruption, and reading fails loud.
 */
public final class Journal {

    private final Path file;
    private final JournalCodec codec;

    public Journal(Path file, JournalCodec codec) {
        this.file = file;
        this.codec = codec;
    }

    public Path file() {
        return file;
    }

    /** Every record, in the order written. */
    public List<JournalRecord> read() {
        if (!Files.exists(file)) {
            return List.of();
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the journal " + file + " unreadable", e);
        }
        List<JournalRecord> records = new ArrayList<>();
        int start = 0;
        for (int end = text.indexOf('\n'); end >= 0; end = text.indexOf('\n', start)) {
            records.add(codec.read(text.substring(start, end)));
            start = end + 1;
        }
        return records;
    }

    /** Writes {@code record} at the end, forced to disk. */
    public void append(JournalRecord record) {
        byte[] line = (codec.write(record) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.READ)) {
                long end = whole(channel);
                channel.truncate(end);
                ByteBuffer buffer = ByteBuffer.wrap(line);
                long at = end;
                while (buffer.hasRemaining()) {
                    at += channel.write(buffer, at);
                }
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the journal " + file + " not written", e);
        }
    }

    /** The length of the file's whole lines: past it is a torn append. */
    private static long whole(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size == 0) {
            return 0;
        }
        ByteBuffer one = ByteBuffer.allocate(1);
        channel.read(one, size - 1);
        if (one.get(0) == '\n') {
            return size;
        }
        ByteBuffer all = ByteBuffer.allocate((int) size);
        channel.read(all, 0);
        for (int i = (int) size - 1; i >= 0; i--) {
            if (all.get(i) == '\n') {
                return i + 1;
            }
        }
        return 0;
    }
}
