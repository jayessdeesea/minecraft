package io.github.jayessdeesea.minecraft.command;

/**
 * What the force grows back where it stands, and keeps between a least and a most
 * ({@link ProductionLevels}; docs/echelons/tenets.md, stock levels drive production). Bread and logs are
 * the products a shortfall is called on; wheat, seeds and saplings are what the producers keep their
 * work going on.
 */
public enum Renewable {
    BREAD(PlatoonSergeant.Shortfall.FOOD, true),
    WHEAT(PlatoonSergeant.Shortfall.FOOD, false),
    SEEDS(PlatoonSergeant.Shortfall.FOOD, false),
    LOGS(PlatoonSergeant.Shortfall.WOOD, true),
    SAPLINGS(PlatoonSergeant.Shortfall.WOOD, false);

    private final PlatoonSergeant.Shortfall shortfall;
    private final boolean product;

    Renewable(PlatoonSergeant.Shortfall shortfall, boolean product) {
        this.shortfall = shortfall;
        this.product = product;
    }

    /** The shortfall whose work produces it: the farm's or the wood's. */
    public PlatoonSergeant.Shortfall shortfall() {
        return shortfall;
    }

    /**
     * Whether it's a product - bread, logs - whose shortage calls a squad onto its work. Wheat and
     * seeds are the farm's own makings, saplings the forest's: a harvester bakes its wheat as it goes
     * and replants its seed, so the depot's count of them says how the work runs, not that it's short.
     */
    public boolean product() {
        return product;
    }
}
