package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where each soldier's food goes (operator, 2026-11-19: see why the soldiers need to eat so much): its food eaten, set
 * against what the server's hunger rules burn - exhaustion, 4.0 to a point of hunger - for what the soldier did. In
 * Java edition walking burns nothing; healing burns 6.0 a health point, a jump 0.05 (0.2 sprinting), sprinting 0.1 a
 * metre, an attack 0.1 and a hit taken 0.1 (https://minecraft.wiki/w/Hunger#Exhaustion_level_increase). Saturation isn't
 * sent, so food eaten is the hunger it put back, and a loaf's 6 saturation besides is taken as eaten with its 5 hunger.
 * Sampled each tick; read and started again at dawn. Thread-safe.
 */
final class FoodLedger {

    /** Exhaustion to a point of hunger. */
    static final double EXHAUSTION_A_POINT = 4.0;
    static final double HEALING = 6.0, JUMP = 0.05, SPRINT_JUMP = 0.2, SPRINT_A_METRE = 0.1, ATTACK = 0.1, HIT = 0.1;
    /** A loaf: its hunger, and the saturation it brings besides. */
    static final int LOAF_FOOD = 5, LOAF_SATURATION = 6;

    /** What a soldier did since dawn, in the units the rules burn by. */
    private static final class Tally {
        int eaten;
        double healed, hurt;
        int hits, jumps, sprintJumps, attacks;
        double sprinted;
        Integer food;
        Double health;
        Position at;
        boolean onGround = true, sprinting;
        /** Jumps by the column taken off from: many in one column is a soldier jumping in place, not over ground. */
        final Map<String, Integer> jumpsAt = new java.util.HashMap<>();
    }

    private final Map<String, Tally> tallies = new TreeMap<>();

    /** {@code soldier} as it is this tick: its condition, where it is, whether it sprints and stands on the ground. */
    synchronized void sample(String soldier, java.util.Optional<Condition> condition, Position at, boolean sprinting, boolean onGround) {
        Tally t = tallies.computeIfAbsent(soldier, n -> new Tally());
        condition.ifPresent(c -> {
            if (t.food != null && c.food() > t.food) {
                t.eaten += c.food() - t.food;
            }
            if (t.health != null && c.health() > t.health) {
                t.healed += c.health() - t.health;
            } else if (t.health != null && c.health() < t.health) {
                t.hurt += t.health - c.health();
                t.hits++;
            }
            t.food = c.food();
            t.health = c.health();
        });
        if (t.at != null) {
            if (t.onGround && !onGround && at.y() > t.at.y()) {
                t.jumpsAt.merge((int) Math.floor(t.at.x()) + "," + (int) Math.floor(t.at.y()) + "," + (int) Math.floor(t.at.z()), 1, Integer::sum);
                if (sprinting) {
                    t.sprintJumps++;
                } else {
                    t.jumps++;
                }
            }
            if (sprinting && t.sprinting) {
                t.sprinted += Math.hypot(at.x() - t.at.x(), at.z() - t.at.z());
            }
        }
        t.at = at;
        t.onGround = onGround;
        t.sprinting = sprinting;
    }

    /** {@code soldier} struck at something. */
    synchronized void attack(String soldier) {
        tallies.computeIfAbsent(soldier, n -> new Tally()).attacks++;
    }

    /** One soldier's day, in hunger points: what it ate, and what it burnt on what. */
    record Day(String soldier, double eaten, double hurt, double healing, double jumping, double sprinting, double fighting,
               List<String> jumpedMost) {

        double burnt() {
            return healing + jumping + sprinting + fighting;
        }

        /** As said: "Alice ate 7.0 loaves (77 hunger with saturation), lost 30 health; burnt 61 on healing 44, jumping 6, ...". */
        String line() {
            return String.format(Locale.ROOT, "%s ate %.1f loaves (%.0f hunger with saturation), lost %.0f health; burnt %.0f on healing %.0f, "
                    + "jumping %.0f, sprinting %.0f, fighting %.0f; %.0f unexplained%s", soldier, eaten / (LOAF_FOOD + LOAF_SATURATION), eaten, hurt,
                    burnt(), healing, jumping, sprinting, fighting, Math.max(0, eaten - burnt()),
                    jumpedMost.isEmpty() ? "" : "; jumped most at " + String.join(", ", jumpedMost));
        }
    }

    /** Each soldier's day since the last, and the tallies started again. */
    synchronized List<Day> days() {
        List<Day> days = new ArrayList<>();
        tallies.forEach((soldier, t) -> days.add(new Day(soldier, t.eaten * (LOAF_FOOD + LOAF_SATURATION) / (double) LOAF_FOOD, t.hurt,
                t.healed * HEALING / EXHAUSTION_A_POINT, (t.jumps * JUMP + t.sprintJumps * SPRINT_JUMP) / EXHAUSTION_A_POINT,
                t.sprinted * SPRINT_A_METRE / EXHAUSTION_A_POINT, (t.attacks * ATTACK + t.hits * HIT) / EXHAUSTION_A_POINT,
                t.jumpsAt.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(3)
                        .map(e -> "(" + e.getKey() + ") x" + e.getValue()).toList())));
        tallies.values().forEach(t -> {
            t.eaten = 0;
            t.healed = t.hurt = t.sprinted = 0;
            t.hits = t.jumps = t.sprintJumps = t.attacks = 0;
            t.jumpsAt.clear();
        });
        return days;
    }

    /** The day's review: the force's totals, then each soldier's, most eaten first. */
    synchronized List<String> review() {
        List<Day> days = days();
        double eaten = days.stream().mapToDouble(Day::eaten).sum();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "[Food] the force ate %.0f loaves; burnt %.0f hunger on healing, %.0f jumping, %.0f sprinting, "
                        + "%.0f fighting", eaten / (LOAF_FOOD + LOAF_SATURATION), days.stream().mapToDouble(Day::healing).sum(),
                days.stream().mapToDouble(Day::jumping).sum(), days.stream().mapToDouble(Day::sprinting).sum(),
                days.stream().mapToDouble(Day::fighting).sum()));
        days.stream().sorted(java.util.Comparator.comparingDouble(Day::eaten).reversed()).forEach(d -> lines.add("[Food] " + d.line()));
        return lines;
    }
}
