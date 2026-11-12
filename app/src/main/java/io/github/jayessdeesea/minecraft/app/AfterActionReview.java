package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The after-action review at each dawn (TC 25-20; docs/military-doctrine.md § 17): what was meant
 * to be done, what was, and what went wrong - the camp's standards met and open, each death of the
 * day by cause, and the soldiers who stood about by day. Pure: events are noted as they happen, and
 * the review comes back as text at the dawn that closes the day, with the commands' scorecard.
 */
final class AfterActionReview {

    /** How each kind of command went, sampled by the soldiers' ticks. */
    final CommandScorecard commands = new CommandScorecard();

    private final Map<String, List<String>> deaths = new TreeMap<>();
    private final Map<String, Integer> idle = new TreeMap<>();
    /** Each standard's foothold checks this day, and how many found it met. */
    private final Map<String, int[]> held = new TreeMap<>();
    private boolean wasDark;

    /**
     * The share of a day's checks a standard must hold to score as met: one met at dawn but open
     * half the day isn't, and one open for a minute at dawn is. Live, the camp's lit ground and wall
     * went in and out of met 36 times a run, and the dawn's one look decided the day.
     */
    static final double STEADY = 0.9;

    /** The camp as one foothold check measured it: counted toward each standard's day. */
    synchronized void measured(Foothold foothold) {
        foothold.standards().forEach(s -> {
            int[] tally = held.computeIfAbsent(s.name(), n -> new int[2]);
            tally[0]++;
            tally[1] += s.met() ? 1 : 0;
        });
    }

    /** The share of the day's checks {@code standard} held, or its state now with none counted. */
    private double share(Foothold.Standard standard) {
        int[] tally = held.get(standard.name());
        return tally == null || tally[0] == 0 ? (standard.met() ? 1 : 0) : (double) tally[1] / tally[0];
    }

    private boolean steady(Foothold.Standard standard) {
        return share(standard) >= STEADY;
    }

    private String percent(Foothold.Standard standard) {
        return Math.round(share(standard) * 100) + "% of the day";
    }
    private int day;

    /** {@code soldier} died, as the server's death message key says ({@code death.attack.arrow}). */
    synchronized void death(String soldier, String cause) {
        deaths.computeIfAbsent(cause, c -> new ArrayList<>()).add(soldier);
    }

    /** {@code soldier} raised an idle alarm (§ 3). */
    synchronized void idle(String soldier) {
        idle.merge(soldier, 1, Integer::sum);
    }

    /** A dawn's review, as text, and its score for the scoreboard. */
    record Dawn(String review, Scoreboard.Score score) {
    }

    /** As {@link #time(boolean, Optional, int, int, long, List, int)}, with nothing counted. */
    synchronized Optional<Dawn> time(boolean dark, Optional<Foothold> foothold) {
        return time(dark, foothold, 0, 0, 0, List.of(), 0);
    }

    /**
     * The time is now {@code dark} or not; at the dawn that ends a night, the review of the day and
     * night before, with the camp as {@code foothold} measures it and {@code hungry} of
     * {@code soldiers} short of food, and its score. The tally starts again.
     */
    synchronized Optional<Dawn> time(boolean dark, Optional<Foothold> foothold, int hungry, int soldiers, long now, List<String> routes,
                                     int churned) {
        boolean dawn = wasDark && !dark;
        wasDark = dark;
        if (!dawn) {
            return Optional.empty();
        }
        day++;
        StringBuilder review = new StringBuilder("AAR for day " + day + " of this run:");
        // Scored over the day, not at the dawn's one look (STEADY).
        List<Foothold.Standard> standards = foothold.map(Foothold::standards).orElse(List.of());
        int phase = standards.stream().filter(s -> !steady(s)).mapToInt(Foothold.Standard::phase).min().orElse(0);
        foothold.ifPresent(f -> {
            review.append("\n  camp: ").append(phase > 0 ? "phase " + phase + " not yet met" : "every phase met");
            f.standards().forEach(s -> review.append("\n    ").append(steady(s) ? "met: " : "OPEN: ").append(s.name())
                    .append(" - held ").append(percent(s)).append(s.met() ? "" : "; now " + s.status()));
        });
        int dead = deaths.values().stream().mapToInt(List::size).sum();
        int[] shares = commands.shares();
        Scoreboard.Score score = new Scoreboard.Score(now, day, phase,
                (int) standards.stream().filter(this::steady).count(), standards.size(),
                standards.stream().filter(s -> !steady(s)).sorted(Comparator.comparingInt(Foothold.Standard::phase))
                        .map(s -> "phase " + s.phase() + " " + s.name() + " - held " + percent(s) + (s.met() ? "" : "; now " + s.status()))
                        .toList(),
                dead, shares[0], shares[1], hungry, soldiers, routes, churned);
        held.clear();
        review.append("\n  casualties: ").append(dead == 0 ? "none" : dead);
        deaths.forEach((cause, who) -> review.append("\n    ").append(cause).append(": ").append(String.join(", ", who)));
        review.append("\n  idle by day: ").append(idle.isEmpty() ? "none" : idle.toString());
        commands.review().forEach(line -> review.append("\n  ").append(line));
        deaths.clear();
        idle.clear();
        return Optional.of(new Dawn(review.toString(), score));
    }
}
