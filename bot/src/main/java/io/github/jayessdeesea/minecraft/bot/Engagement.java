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
    public static final double WALK = 4.3;

    /**
     * The soldier, as the exchange takes it: its health, armour points and toughness, its blow's damage and attacks a
     * second, and its reach.
     */
    public record Soldier(double health, double armour, double toughness, double damage, double attacksPerSecond, double reach,
                          double speed) {

        /** As above, at a walk. */
        public Soldier(double health, double armour, double toughness, double damage, double attacksPerSecond, double reach) {
            this(health, armour, toughness, damage, attacksPerSecond, reach, WALK);
        }

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
    public record Hostile(double health, double damage, double blowsPerSecond, double reach, double distance, double speed) {

        /** One that holds its ground - an archer at its standoff: it doesn't follow a soldier breaking off. */
        public Hostile(double health, double damage, double blowsPerSecond, double reach, double distance) {
            this(health, damage, blowsPerSecond, reach, distance, 0);
        }
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
     * The blows {@code soldier} takes getting {@code clear} blocks off {@code hostile}: from one that holds its ground, the
     * walk away under its fire; from one that follows, the time to open the gap past its reach at the difference in their
     * speeds - none to be had, it can't be got away from, and the cost is beyond any health. Live in the matrix, a soldier
     * with bare hands backed off two zombies for good and was caught on hills: a zombie keeps pace with a walk.
     */
    static double escape(Soldier soldier, Hostile hostile, double clear) {
        double seconds;
        if (hostile.speed() <= 0) {
            seconds = clear / Math.max(0.01, soldier.speed());
        } else if (soldier.speed() > hostile.speed()) {
            // The gap still to open: past its reach and clear of it, less how far off it is already.
            seconds = Math.max(0, hostile.reach() + clear - hostile.distance()) / (soldier.speed() - hostile.speed());
        } else {
            return Double.POSITIVE_INFINITY;
        }
        return seconds * soldier.taken(hostile.damage()) * hostile.blowsPerSecond();
    }

    /**
     * What {@code soldier} does about {@code hostile}. Not yet {@code begun}: {@link Kind#ENGAGE} if it outlasts the fight
     * by {@link #MARGIN}; else {@link Kind#COVER} - but one that follows and can't be got away from is a fight forced on
     * it, fought. Begun: finished while it outlasts the kill at all - broken off short of the margin, soldiers
     * were shot running (the combat matrix lost four cells to a health line) - and {@link Kind#WITHDRAW} only when it would
     * die first and can get clear alive.
     */
    public static Kind choose(Soldier soldier, Hostile hostile, boolean begun, double clear) {
        double kill = timeToKill(soldier, hostile);
        double die = timeToDie(soldier, hostile);
        boolean getsAway = escape(soldier, hostile, clear) < soldier.health();
        if (!begun) {
            // The margin is for closing under fire, on one that fights from range; one that follows into its own reach brings
            // the fight to the soldier whatever it does, and is broken off only when it would win outright - and can be got
            // away from. An archer follows only to keep its range: closed on with the margin still.
            if (hostile.speed() > 0 && hostile.reach() <= soldier.reach()) {
                return die >= kill || !getsAway ? Kind.ENGAGE : Kind.COVER;
            }
            return die >= kill * MARGIN ? Kind.ENGAGE : Kind.COVER;
        }
        if (die >= kill) {
            return Kind.ENGAGE;
        }
        return getsAway ? Kind.WITHDRAW : Kind.ENGAGE;
    }

    /** A sprint's blocks a second: vanilla's 5.6. And a swim's, at the surface: about 2. */
    public static final double SPRINT = 5.6;
    public static final double SWIM = 2;

    /** An archer's shot as the exchange reckons it: a skeleton's bow, 3 to 5. */
    static final double ARCHER_SHOT = 4;
    /** Armour points a piece worn, as the kit counts pieces: between leather's and iron's. */
    static final double ARMOUR_A_PIECE = 2.5;

    /** An archer's shots a second, its range - its reach - and its health: a skeleton's bow, a shot every 2 s from 15. */
    static final double ARCHER_SHOTS_A_SECOND = 0.5;
    static final double ARCHER_RANGE = 15;
    static final double ARCHER_HEALTH = 20;
    /**
     * An archer's pace after a soldier breaking off - it follows to keep its range, at half speed with its bow drawn
     * ({@code RangedBowAttackGoal}): about 2.2 blocks a second, faster than a soldier swims. In the combat matrix's pond, a
     * soldier swimming off a skeleton was followed and shot dead; fought, it won.
     */
    static final double ARCHER_PACE = 2.2;

    /** An archer {@code distance} off, as the exchange takes it. */
    private static Hostile archerAt(double distance) {
        return new Hostile(ARCHER_HEALTH, ARCHER_SHOT, ARCHER_SHOTS_A_SECOND, ARCHER_RANGE, distance, ARCHER_PACE);
    }

    /** A zombie's blow as the exchange reckons it, its blows a second, its reach and its pace - it keeps up with a walk. */
    static final double MELEE_BLOW = 3;
    static final double MELEE_BLOWS_A_SECOND = 1;
    static final double MELEE_REACH = 2;
    static final double MELEE_PACE = 4;
    /** How far past its reach a soldier breaking off from one hand to hand goes. */
    static final double MELEE_CLEAR = 4;

    /** The soldier at {@code health} carrying {@code kit}, at {@code speed}, as the exchange takes it. */
    private static Soldier soldier(double health, io.github.jayessdeesea.minecraft.command.Kit kit, double speed) {
        io.github.jayessdeesea.minecraft.bot.world.WeaponStats.Stats held = io.github.jayessdeesea.minecraft.bot.world.WeaponStats.melee(
                kit == null ? java.util.Optional.empty() : kit.weapon());
        double armour = kit == null ? 0 : kit.armour() * ARMOUR_A_PIECE;
        return new Soldier(health, armour, 0, held.damage(), held.attacksPerSecond(), DeterministicSoldier.ATTACK_REACH, speed);
    }

    /**
     * The exchange with an archer {@code range} off, for a soldier at {@code health} carrying {@code kit} and moving at
     * {@code pace} - a walk, a sprint, a swim, or none boxed in - its fight {@code begun} or not, and {@code closing} on
     * it or stalled: got away from past {@link #CLEAR_OF}, the archer following to keep its range. With its health not yet reported
     * (under 0), no exchange to reckon: it engages.
     */
    public static Kind archer(double health, io.github.jayessdeesea.minecraft.command.Kit kit, double range, boolean begun, double pace,
                              boolean closing) {
        if (health < 0) {
            return Kind.ENGAGE;
        }
        Soldier soldier = soldier(health, kit, pace);
        // One that isn't closing - its steps at it gaining no ground - strikes nothing however near it stands.
        return choose(closing ? soldier : new Soldier(health, soldier.armour(), soldier.toughness(), 0, soldier.attacksPerSecond(),
                soldier.reach(), pace), archerAt(range), begun, CLEAR_OF - ARCHER_RANGE);
    }

    /** The exchange with one that fights hand to hand {@code range} off, got away from at {@code pace} - a sprint, a walk, none. */
    public static Kind melee(double health, io.github.jayessdeesea.minecraft.command.Kit kit, double range, boolean begun, double pace) {
        if (health < 0) {
            return Kind.ENGAGE;
        }
        return choose(soldier(health, kit, pace),
                new Hostile(20, MELEE_BLOW, MELEE_BLOWS_A_SECOND, MELEE_REACH, range, MELEE_PACE), begun, MELEE_CLEAR);
    }

    /** What a soldier that withdrew from a hostile does next. */
    public enum Withdrawal {
        /** The hostile still near and in sight, or following: get further off it. */
        BREAK_CONTACT,
        /** Clear of it: hold where it is - out of contact it eats, if it carries food - and heal. */
        RECOVER,
        /** Healed past its line - or with nothing to heal on - the withdrawal is over: back to its work, the hostile met afresh if it's in the way. */
        RESUME
    }

    /** How far off a hostile in sight, or one that follows, a withdrawing soldier is clear of it: past a skeleton's bow. */
    public static final double CLEAR_OF = 20;

    /**
     * The withdrawal's next step, pure: at {@code health} against its {@code line}, able to heal or not ({@code canHeal}:
     * at the food it regenerates on, or carrying food to eat), the hostile {@code distance} off - infinite once lost
     * track of - in its sight or not (shot at is in sight, whatever the soldier sees), and following or holding its ground. One that holds its ground out of sight is
     * cleared at any distance; one that follows only past {@link #CLEAR_OF}, as it comes on round what's between.
     */
    public static Withdrawal withdrawal(double health, double line, boolean canHeal, double distance, boolean inSight, boolean follows) {
        if (health >= line || !canHeal) {
            return Withdrawal.RESUME;
        }
        return distance < CLEAR_OF && (inSight || follows) ? Withdrawal.BREAK_CONTACT : Withdrawal.RECOVER;
    }

    /**
     * The health past which {@code soldier} closes on {@code hostile}, not yet begun, with the {@link #MARGIN} to spare - its
     * line to re-engage at after a withdrawal - at most {@code most}: the time to die it needs, back into health at the
     * hostile's rate of fire through its armour.
     */
    static double line(Soldier soldier, Hostile hostile, double most) {
        double perSecond = soldier.taken(hostile.damage()) * hostile.blowsPerSecond();
        double untilInReach = Math.max(0, hostile.distance() - hostile.reach()) / WALK;
        return Math.min(most, Math.max(0, timeToKill(soldier, hostile) * MARGIN - untilInReach) * perSecond);
    }

    /**
     * The line a soldier carrying {@code kit} re-engages at, against an archer (from its range) or one that fights hand to
     * hand (from its reach): at most {@code most}, a whole soldier's health.
     */
    public static double line(io.github.jayessdeesea.minecraft.command.Kit kit, boolean archer, double most) {
        return line(soldier(most, kit, WALK), archer ? archerAt(ARCHER_RANGE)
                : new Hostile(20, MELEE_BLOW, MELEE_BLOWS_A_SECOND, MELEE_REACH, MELEE_REACH, MELEE_PACE), most);
    }
}
