package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The after-action review at each dawn (TC 25-20; docs/military-doctrine.md § 17): what was meant
 * to be done, what was, and what went wrong - the camp's standards met and open, each death of the
 * day by cause, and the soldiers who stood about by day. Pure: events are noted as they happen, and
 * the review comes back as text at the dawn that closes the day.
 */
final class AfterActionReview {

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

    /**
     * The time is now {@code dark} or not; at the dawn that ends a night, the review of the day and
     * night before, with the camp as {@code foothold} measures it. The tally starts again.
     */
    synchronized Optional<String> time(boolean dark, Optional<Foothold> foothold) {
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
        review.append("\n  casualties: ").append(dead == 0 ? "none" : dead);
        deaths.forEach((cause, who) -> review.append("\n    ").append(cause).append(": ").append(String.join(", ", who)));
        review.append("\n  idle by day: ").append(idle.isEmpty() ? "none" : idle.toString());
        deaths.clear();
        idle.clear();
        return Optional.of(review.toString());
    }
}
