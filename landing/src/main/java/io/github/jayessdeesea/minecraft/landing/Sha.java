package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/** A git object id, full length - SHA-1's 40 hex or SHA-256's 64 - in lower case, as git prints it. */
public record Sha(@JsonValue String value) {

    private static final Pattern FORM = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");

    public Sha {
        Objects.requireNonNull(value, "a sha");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("not a full object id: '" + value + "'");
        }
    }

    /** Whether {@code text} is a full object id. */
    public static boolean valid(String text) {
        return text != null && FORM.matcher(text).matches();
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Sha parse(String text) {
        return new Sha(text);
    }

    @Override
    public String toString() {
        return value;
    }
}
