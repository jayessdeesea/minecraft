package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * A journal record to its line and back: one JSON object on one line, its kind in {@code type}, times as
 * {@link Instant#toString} writes them. A line is a record only if writing the record read gives the line back,
 * character for character - the codec accepts its own output and nothing else, so a corrupt, half-written or
 * hand-made line is named, never read as something near it. The file is the shell's.
 */
public final class JournalCodec {

    private final ObjectWriter writer;
    private final ObjectReader reader;

    public JournalCodec() {
        SimpleModule time = new SimpleModule("instants")
                .addSerializer(Instant.class, ToStringSerializer.instance)
                .addDeserializer(Instant.class, new InstantText());
        JsonMapper json = JsonMapper.builder().addModule(time).build();
        this.writer = json.writerFor(JournalRecord.class);
        this.reader = json.readerFor(JournalRecord.class);
    }

    /** The record's line, with no line ending: JSON escapes any newline in its text. */
    public String write(JournalRecord record) {
        try {
            return writer.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a " + record.getClass().getSimpleName() + " record not written as JSON", e);
        }
    }

    public JournalRecord read(String line) {
        JournalRecord record;
        try {
            record = reader.readValue(line);
        } catch (JsonProcessingException e) {
            throw new JournalFormatException("not a journal record: '" + line + "': " + e.getOriginalMessage(), e);
        }
        if (record == null || !write(record).equals(line)) {
            throw new JournalFormatException("not a journal record in the form this codec writes: '" + line + "'", null);
        }
        return record;
    }

    /** An instant from a JSON string only; whether it was written as this codec writes it is the read-back's to say. */
    private static final class InstantText extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return (Instant) context.handleUnexpectedToken(Instant.class, parser);
            }
            try {
                return Instant.parse(parser.getText());
            } catch (DateTimeParseException e) {
                return (Instant) context.handleWeirdStringValue(Instant.class, parser.getText(), "not an ISO 8601 instant");
            }
        }
    }
}
