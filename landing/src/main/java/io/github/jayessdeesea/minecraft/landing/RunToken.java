package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * One integrator run's name, on every record it writes (invariant I5): sixteen lower-case hex, drawn when the run
 * takes the lock.
 */
public record RunToken(@JsonValue String value) {

    private static final Pattern FORM = Pattern.compile("[0-9a-f]{16}");

    public RunToken {
        Objects.requireNonNull(value, "a run token");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("not a run token (16 hex): '" + value + "'");
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RunToken parse(String text) {
        return new RunToken(text);
    }

    public static RunToken next(RandomGenerator random) {
        return new RunToken(HexFormat.of().toHexDigits(random.nextLong()));
    }

    @Override
    public String toString() {
        return value;
    }
}
