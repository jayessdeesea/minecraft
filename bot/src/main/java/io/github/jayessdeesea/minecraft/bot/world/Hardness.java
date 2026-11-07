package io.github.jayessdeesea.minecraft.bot.world;

/**
 * What digging a block takes: its hardness (the server's destroy time) and the least pickaxe that
 * gets its drop - {@code pickaxeTier} 0 for any, 1 stone, 2 iron, 3 diamond
 * ({@link ItemClassRegistry#miningTier}), or {@link #HAND} for ground dug by hand: soil, sand, gravel.
 *
 * <p>The time is vanilla's: each tick a dig gains the tool's speed over the hardness, over 30 with
 * the right tool and over 100 without it, and the block breaks at 1. A pickaxe's speed is its
 * material's: wooden 2, stone 4, iron 6, diamond 8, netherite 9 - counted at the slowest pickaxe of
 * its tier, so a dig is never ended early.
 */
public record Hardness(double hardness, int pickaxeTier) {

    /** No pickaxe needed: dug by hand, at hand speed. */
    public static final int HAND = -1;

    private static final double[] PICKAXE_SPEED = {2, 4, 6, 8, 9};

    public Hardness {
        if (hardness < 0 || pickaxeTier < HAND || pickaxeTier >= PICKAXE_SPEED.length) {
            throw new IllegalArgumentException("hardness " + hardness + ", pickaxe tier " + pickaxeTier);
        }
    }

    /** Whether a soldier holding a pickaxe of {@code heldTier} - {@link #HAND} for none - gets the block's drop. */
    public boolean drops(int heldTier) {
        return pickaxeTier == HAND || heldTier >= pickaxeTier;
    }

    /** Ticks to dig it holding a pickaxe of {@code heldTier}, or {@link #HAND} for none. */
    public int ticks(int heldTier) {
        if (pickaxeTier == HAND) {
            return (int) Math.ceil(hardness * 30); // a pickaxe is no faster at soil than a hand
        }
        double speed = heldTier == HAND ? 1 : PICKAXE_SPEED[heldTier];
        return (int) Math.ceil(hardness * (drops(heldTier) ? 30 : 100) / speed);
    }
}
