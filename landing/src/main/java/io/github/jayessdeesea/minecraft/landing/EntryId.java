package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One request to land one squashed commit: {@code <UTC timestamp>-<8 random hex>}, chosen by its submitter. The
 * timestamp is ISO 8601's basic form to the second ({@code 20261126T143005Z}), because a ref name may hold no colon
 * ({@code refs/queue/<id>}); the hex is lower case. Ids sort as their timestamps do, so the oldest request sorts first.
 */
public record EntryId(@JsonValue String value) implements Comparable<EntryId> {

    private static final Pattern FORM = Pattern.compile("(\\d{8}T\\d{6}Z)-[0-9a-f]{8}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).withResolverStyle(ResolverStyle.STRICT);

    public EntryId {
        Objects.requireNonNull(value, "an entry id");
        Matcher form = FORM.matcher(value);
        if (!form.matches()) {
            throw new IllegalArgumentException("not an entry id (<yyyyMMdd'T'HHmmss'Z'>-<8 hex>): '" + value + "'");
        }
        try {
            STAMP.parse(form.group(1));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("not a UTC time in entry id '" + value + "'", e);
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EntryId parse(String text) {
        return new EntryId(text);
    }

    /** A new id: the clock's second, in UTC, and eight hex from the random source. */
    public static EntryId next(Clock clock, RandomGenerator random) {
        return new EntryId(STAMP.format(clock.instant()) + "-" + HexFormat.of().toHexDigits(random.nextInt()));
    }

    /** When its submitter made it, by the submitter's clock. */
    public Instant submitted() {
        return Instant.from(STAMP.parse(value.substring(0, value.indexOf('-'))));
    }

    @Override
    public int compareTo(EntryId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
