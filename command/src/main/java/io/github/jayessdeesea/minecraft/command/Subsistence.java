package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Class I, subsistence (FM 4-0; ATP 4-41 field feeding), for one soldier and for its squad: what a
 * soldier does about food on its own, and when its squad has to ask for help (docs/military-doctrine.md § 4).
 *
 * <p>A soldier carries a <b>basic load</b> of {@link #BASIC_LOAD} rations. Hungry, it eats. Below its
 * basic load it restocks by its own means - bakes the wheat it carries, hunts game in sight - and
 * below {@link #LOW} a squadmate with rations to spare shares them (cross-leveling, § 13). One still
 * low with none of those means makes the squad's Class I {@link Rating#RED}: the squad can't feed
 * itself, and asks. Feeding a force is planned above the squad, as field feeding is above the
 * company, so the request goes to the general, whose answer is a farm or a move to game.
 */
public final class Subsistence {

    /** Rations a soldier keeps: three or four meals, a day and more of work. */
    public static final int BASIC_LOAD = 6;
    /** Below this, a squadmate shares and a soldier with no means of its own is short. */
    public static final int LOW = 2;

    private Subsistence() {
    }

    /** What a soldier does about food now. */
    public enum Need {
        /** Fed and carrying its basic load. */
        NONE,
        /** Hungry, with a ration to eat. */
        EAT,
        /** Below its basic load: bakes, hunts, or is shared some. */
        RESTOCK
    }

    /** A squad's Class I rating, as a LOGSTAT gives it. */
    public enum Rating {
        /** Every soldier at {@link #LOW} rations or more. */
        GREEN,
        /** Some below, each with a means: wheat to bake, game in sight, a squadmate sharing. */
        AMBER,
        /** One or more below with no means: the squad asks. */
        RED
    }

    /**
     * The squad's Class I: its rating, who is short ({@code short_}, below {@link #LOW}), and who of
     * them has no means of getting food ({@code helpless}).
     */
    public record Status(Rating rating, List<String> short_, List<String> helpless) {
        public Status {
            short_ = List.copyOf(short_);
            helpless = List.copyOf(helpless);
        }

        /** The LOGSTAT's Class I line: said as the request it is, when RED. */
        public String line() {
            return switch (rating) {
                case GREEN -> "Class I GREEN: every soldier carries food.";
                case AMBER -> String.format(Locale.ROOT, "Class I AMBER: %s short of food, getting some (baking, hunting, farming or shared).",
                        String.join(", ", short_));
                case RED -> String.format(Locale.ROOT, "Class I RED - request: %s out of food with no way to get any: no wheat to bake,"
                        + " no game in sight, no squadmate with any to share. The squad can't feed itself.", String.join(", ", helpless));
            };
        }
    }

    /** What a soldier with {@code food} (0-20) and {@code rations} carried does about it. */
    public static Need need(int food, int rations) {
        if (food < Condition.REGENERATION_FOOD && rations > 0) {
            return Need.EAT;
        }
        return rations < BASIC_LOAD ? Need.RESTOCK : Need.NONE;
    }

    /** Whether a soldier carrying {@code rations} is short: below {@link #LOW}. */
    public static boolean short_(int rations) {
        return rations < LOW;
    }

    /**
     * The squad's Class I from its soldiers' kit: those short, and of them those in {@code noMeans} -
     * worked out by the soldiers' own shell, which sees the wheat, the game and the shares.
     */
    public static Status of(List<SoldierStatus> squad, Set<String> noMeans) {
        List<String> short_ = squad.stream()
                .filter(s -> s.condition().flatMap(Condition::kit).map(k -> short_(k.rations())).orElse(false))
                .map(SoldierStatus::name).toList();
        List<String> helpless = short_.stream().filter(noMeans::contains).toList();
        Rating rating = short_.isEmpty() ? Rating.GREEN : helpless.isEmpty() ? Rating.AMBER : Rating.RED;
        return new Status(rating, short_, helpless);
    }
}
