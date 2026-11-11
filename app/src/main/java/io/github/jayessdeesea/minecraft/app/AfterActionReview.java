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
    private boolean wasDark;
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
        foothold.ifPresent(f -> {
            review.append("\n  camp: ").append(f.nextPhase().isPresent() ? "phase " + f.nextPhase().getAsInt() + " not yet met" : "every phase met");
            f.standards().forEach(s -> review.append("\n    ").append(s.met() ? "met: " : "OPEN: ").append(s.name())
                    .append(s.met() ? "" : " - " + s.status()));
        });
        int dead = deaths.values().stream().mapToInt(List::size).sum();
        int[] shares = commands.shares();
        List<Foothold.Standard> standards = foothold.map(Foothold::standards).orElse(List.of());
        Scoreboard.Score score = new Scoreboard.Score(now, day, foothold.flatMap(f -> f.nextPhase().isPresent()
                ? Optional.of(f.nextPhase().getAsInt()) : Optional.empty()).orElse(0),
                (int) standards.stream().filter(Foothold.Standard::met).count(), standards.size(),
                standards.stream().filter(s -> !s.met()).sorted(Comparator.comparingInt(Foothold.Standard::phase))
                        .map(s -> "phase " + s.phase() + " " + s.name() + " - " + s.status()).toList(),
                dead, shares[0], shares[1], hungry, soldiers, routes, churned);
        review.append("\n  casualties: ").append(dead == 0 ? "none" : dead);
        deaths.forEach((cause, who) -> review.append("\n    ").append(cause).append(": ").append(String.join(", ", who)));
        review.append("\n  idle by day: ").append(idle.isEmpty() ? "none" : idle.toString());
        commands.review().forEach(line -> review.append("\n  ").append(line));
        deaths.clear();
        idle.clear();
        return Optional.of(new Dawn(review.toString(), score));
    }
}
