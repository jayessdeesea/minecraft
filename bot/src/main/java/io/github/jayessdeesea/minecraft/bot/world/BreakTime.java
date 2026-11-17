package io.github.jayessdeesea.minecraft.bot.world;

/**
 * How long a block takes to break, in ticks, staged as vanilla stages it: a block's base facts
 * ({@link Hardness}: its hardness, the tool it wants, the pickaxe tier its drop wants), then the
 * tool's speed, then its Efficiency added, then Haste and Mining Fatigue multiplied, then a fifth
 * underwater without Aqua Affinity and a fifth off the ground, then the hardness and the drop's
 * factor (30 with the drop, 100 without) - so no table of every combination is kept, only these
 * few facts and their order (operator, 2026-11-17: a base table and a set of modifiers).
 *
 * @param toolSpeed      the held tool's speed against the block - 1 for a hand, or a tool of the wrong kind
 * @param drops          whether the held tool gets the block's drop
 * @param efficiency     the held tool's Efficiency level - 0 for none; counted only with the right tool
 * @param haste          the Haste level on the soldier - 0 for none
 * @param fatigue        the Mining Fatigue level on the soldier - 0 for none
 * @param underwater     whether its eyes are in water
 * @param aquaAffinity   whether its helmet has Aqua Affinity
 * @param onGround       whether it stands on the ground - not jumping, falling or floating
 */
public record BreakTime(double toolSpeed, boolean drops, int efficiency, int haste, int fatigue,
                        boolean underwater, boolean aquaAffinity, boolean onGround) {

    /** A soldier as they dig today: the tool's speed and whether it drops, no enchantment or effect, on dry ground. */
    public static BreakTime of(double toolSpeed, boolean drops) {
        return new BreakTime(toolSpeed, drops, 0, 0, 0, false, false, true);
    }

    /** The same, off the ground - on a pillar's jump, or falling: a fifth of the speed. */
    public BreakTime airborne() {
        return new BreakTime(toolSpeed, drops, efficiency, haste, fatigue, underwater, aquaAffinity, false);
    }

    /** The same, its eyes in water: a fifth of the speed without Aqua Affinity. */
    public BreakTime submerged() {
        return new BreakTime(toolSpeed, drops, efficiency, haste, fatigue, true, aquaAffinity, onGround);
    }

    /** Ticks to break a block of {@code hardness} this way; 0 for one that breaks at once, and none for one that never does. */
    public java.util.OptionalInt ticks(double hardness) {
        if (hardness < 0) {
            return java.util.OptionalInt.empty(); // bedrock and the like
        }
        if (hardness == 0) {
            return java.util.OptionalInt.of(0);
        }
        double speed = toolSpeed;
        if (toolSpeed > 1 && efficiency > 0) {
            speed += efficiency * efficiency + 1;
        }
        if (haste > 0) {
            speed *= 1 + 0.2 * haste;
        }
        if (fatigue > 0) {
            speed *= Math.pow(0.3, Math.min(fatigue, 4));
        }
        if (underwater && !aquaAffinity) {
            speed /= 5;
        }
        if (!onGround) {
            speed /= 5;
        }
        double perTick = speed / hardness / (drops ? 30 : 100);
        return java.util.OptionalInt.of(perTick >= 1 ? 0 : (int) Math.ceil(1 / perTick));
    }
}
