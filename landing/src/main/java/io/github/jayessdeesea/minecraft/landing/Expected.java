package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * What a verify is expected to take, from the kept passes ({@link VerifyHistory#expected}).
 *
 * @param total   the mean whole time
 * @param modules each module's mean wall and its classes' mean times, as the newest pass lists them
 * @param passes  how many passes it is the mean of
 */
public record Expected(Duration total, List<VerifyPass.Module> modules, int passes) {

    public Expected {
        modules = List.copyOf(modules);
    }

    public Optional<VerifyPass.Module> module(String name) {
        return modules.stream().filter(m -> m.name().equals(name)).findFirst();
    }

    /** The test classes expected in all. */
    public int classes() {
        return modules.stream().mapToInt(m -> m.classes().size()).sum();
    }
}
