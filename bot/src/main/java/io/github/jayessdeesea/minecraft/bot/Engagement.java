package io.github.jayessdeesea.minecraft.bot;

/**
 * Whether a soldier closes on a hostile, by the exchange (pure; backlog/engagement-doctrine.md, the exchange, not
 * thresholds): its time to kill the hostile - the walk to it counted - against its time to die under the hostile's fire,
 * after its armour, and what breaking off costs once begun. In place of a health line a death each (operator,
 * 2026-11-21: a soldier in combat has states; the evals should flush them out). The server's rules it reckons with:
 * armour as {@code CombatRules.getDamageAfterAbsorb} has it - effective armour {@code armour - damage / (2 + toughness/4)},
 * held between a fifth of the armour and 20, the blow cut by effective/25.
 */
public final class Engagement {

    /** What the soldier does about the hostile. */
    public enum Kind {
        /** Wins the exchange with margin: close and fight. */
        ENGAGE,
        /** Loses it, not begun: keep out of its reach - cover from an archer, distance from the rest. */
        COVER,
        /** Loses it, begun, and breaking off costs less than the fight would: get clear, eat and heal. */
        WITHDRAW
    }

    /** How much longer than its time to kill a soldier's time to die must be to close: a fight's dice go both ways. */
    static final double MARGIN = 1.5;
    /** A walk's blocks a second, as the soldier closes or breaks off: vanilla's 4.3. */
    static final double WALK = 4.3;

    /**
     * The soldier, as the exchange takes it: its health, armour points and toughness, its blow's damage and attacks a
     * second, and its reach.
     */
    public record Soldier(double health, double armour, double toughness, double damage, double attacksPerSecond, double reach) {

        /** A blow of {@code damage} as its armour lets it through. */
        double taken(double damage) {
            double effective = Math.min(20, Math.max(armour / 5, armour - damage / (2 + toughness / 4)));
            return damage * (1 - effective / 25);
        }
    }

    /**
     * The hostile: its health, its blow and blows a second, how far it hits from, and how far off it is now. An archer's
     * reach is its range, so it hits as the soldier closes.
     */
    public record Hostile(double health, double damage, double blowsPerSecond, double reach, double distance) {
    }

    private Engagement() {
    }

    /** Seconds {@code soldier} takes to kill {@code hostile}: the walk into its reach, then the blows. */
    static double timeToKill(Soldier soldier, Hostile hostile) {
        double walk = Math.max(0, hostile.distance() - soldier.reach()) / WALK;
        double blows = Math.ceil(hostile.health() / Math.max(0.01, soldier.damage()));
        return walk + blows / Math.max(0.01, soldier.attacksPerSecond());
    }

    /** Seconds {@code hostile} takes to kill {@code soldier}: its blows through the armour, from when it's in reach. */
    static double timeToDie(Soldier soldier, Hostile hostile) {
        double perSecond = soldier.taken(hostile.damage()) * hostile.blowsPerSecond();
        double untilInReach = Math.max(0, hostile.distance() - hostile.reach()) / WALK;
        return untilInReach + soldier.health() / Math.max(0.01, perSecond);
    }

    /**
     * What {@code soldier} does about {@code hostile}. Not yet {@code begun}: {@link Kind#ENGAGE} if it outlasts the fight
     * by {@link #MARGIN}, else {@link Kind#COVER}. Begun: finished while it outlasts the kill at all - broken off short of
     * the margin, soldiers were shot running (the combat matrix lost four cells to a health line) - and
     * {@link Kind#WITHDRAW} only when it would die first and the blows taken getting {@code clear} blocks off leave it alive.
     */
    public static Kind choose(Soldier soldier, Hostile hostile, boolean begun, double clear) {
        double kill = timeToKill(soldier, hostile);
        double die = timeToDie(soldier, hostile);
        if (!begun) {
            return die >= kill * MARGIN ? Kind.ENGAGE : Kind.COVER;
        }
        if (die >= kill) {
            return Kind.ENGAGE;
        }
        double running = clear / WALK * soldier.taken(hostile.damage()) * hostile.blowsPerSecond();
        return running < soldier.health() ? Kind.WITHDRAW : Kind.ENGAGE;
    }
}
