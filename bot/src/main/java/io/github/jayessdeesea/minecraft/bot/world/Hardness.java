package io.github.jayessdeesea.minecraft.bot.world;

/**
 * What digging a block takes: its hardness (the server's destroy time), the least pickaxe that
 * gets its drop - {@code pickaxeTier} 0 for any, 1 stone, 2 iron, 3 diamond
 * ({@link ItemClassRegistry#miningTier}), or {@link #HAND} for a block no pickaxe is needed for -
 * and the {@link Tool} that digs it fastest: a pickaxe for rock, an axe for wood and chests, a
 * shovel for soil, sand and gravel, or none (operator, 2026-11-13: the right tool for the block).
 *
 * <p>The time is vanilla's: each tick a dig gains the tool's speed over the hardness, over 30 with
 * the right tool and over 100 without it where the block needs one for its drop, and the block
 * breaks at 1. A pickaxe's speed is its material's: wooden 2, stone 4, iron 6, diamond 8, netherite
 * 9 - counted at the slowest pickaxe of its tier, so a dig is never ended early. An axe's or a
 * shovel's is the item's own ({@link ItemClassRegistry#axeSpeed}, {@link ItemClassRegistry#shovelSpeed}).
 */
public record Hardness(double hardness, int pickaxeTier, Tool tool) {

    /** The tool class a block is dug fastest with. */
    public enum Tool { PICKAXE, AXE, SHOVEL, HAND }

    /** No pickaxe needed: dug by hand, at hand speed. */
    public static final int HAND = -1;

    private static final double[] PICKAXE_SPEED = {2, 4, 6, 8, 9};

    public Hardness {
        if (hardness < 0 || pickaxeTier < HAND || pickaxeTier >= PICKAXE_SPEED.length) {
            throw new IllegalArgumentException("hardness " + hardness + ", pickaxe tier " + pickaxeTier);
        }
        if (tool == Tool.PICKAXE != pickaxeTier >= 0) {
            throw new IllegalArgumentException("a pickaxe block has a tier, and only one: " + hardness + ", " + pickaxeTier + ", " + tool);
        }
    }

    /** A block whose tool is a pickaxe when it has a tier, and none otherwise. */
    public Hardness(double hardness, int pickaxeTier) {
        this(hardness, pickaxeTier, pickaxeTier >= 0 ? Tool.PICKAXE : Tool.HAND);
    }

    /** Whether a soldier holding a pickaxe of {@code heldTier} - {@link #HAND} for none - gets the block's drop. */
    public boolean drops(int heldTier) {
        return pickaxeTier == HAND || heldTier >= pickaxeTier;
    }

    /** Ticks to dig it holding a pickaxe of {@code heldTier}, or {@link #HAND} for none, and no other tool. */
    public int ticks(int heldTier) {
        return ticks(heldTier, 1);
    }

    /**
     * Ticks to dig it holding a pickaxe of {@code heldTier} ({@link #HAND} for none) and, for a block
     * whose tool is an axe or a shovel, one of speed {@code toolSpeed} - 1 for a hand or the wrong tool.
     */
    public int ticks(int heldTier, double toolSpeed) {
        if (tool == Tool.PICKAXE) {
            double speed = heldTier == HAND ? 1 : PICKAXE_SPEED[heldTier];
            return (int) Math.ceil(hardness * (drops(heldTier) ? 30 : 100) / speed);
        }
        return (int) Math.ceil(hardness * 30 / Math.max(1, tool == Tool.HAND ? 1 : toolSpeed));
    }
}
