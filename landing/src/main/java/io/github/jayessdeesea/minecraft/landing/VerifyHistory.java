package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The verifies that passed, the last {@link #KEPT} (operator's decision 9), oldest first: what a running verify is
 * measured against. Its file ({@code verify-history.json}) is written by the lock's holder after each Verified.
 */
public record VerifyHistory(List<VerifyPass> passes) {

    public static final int KEPT = 5;
    public static final VerifyHistory NONE = new VerifyHistory(List.of());

    public VerifyHistory {
        passes = List.copyOf(passes);
        if (passes.size() > KEPT) {
            throw new IllegalArgumentException(passes.size() + " passes kept, over " + KEPT);
        }
    }

    /** With {@code pass} the newest, the oldest dropped past five. */
    public VerifyHistory with(VerifyPass pass) {
        List<VerifyPass> kept = new ArrayList<>(passes);
        kept.add(pass);
        return new VerifyHistory(kept.subList(Math.max(0, kept.size() - KEPT), kept.size()));
    }

    /**
     * What a verify is expected to take: the newest pass's list of modules and classes, each time the mean over the kept
     * passes that hold it, and the total the mean of their whole times. None before a pass.
     */
    public Optional<Expected> expected() {
        if (passes.isEmpty()) {
            return Optional.empty();
        }
        Duration total = passes.stream().map(VerifyPass::took).reduce(Duration.ZERO, Duration::plus).dividedBy(passes.size());
        List<VerifyPass.Module> modules = new ArrayList<>();
        for (VerifyPass.Module newest : passes.getLast().modules()) {
            List<VerifyPass.Module> same = passes.stream().flatMap(p -> p.modules().stream())
                    .filter(m -> m.name().equals(newest.name())).toList();
            Map<String, Duration> classes = new LinkedHashMap<>();
            newest.classes().keySet().forEach(c -> classes.put(c, mean(same.stream().map(m -> m.classes().get(c))
                    .filter(java.util.Objects::nonNull).toList())));
            modules.add(new VerifyPass.Module(newest.name(), mean(same.stream().map(VerifyPass.Module::wall).toList()), classes));
        }
        return Optional.of(new Expected(total, modules, passes.size()));
    }

    private static Duration mean(List<Duration> ds) {
        return ds.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(ds.size());
    }
}
