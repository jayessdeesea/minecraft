package io.github.jayessdeesea.minecraft.command;

/**
 * What the stockpile - the supply point's chest - held when a soldier last opened it
 * (docs/military-doctrine.md §12): gathered materials by kind, the rations put in beyond
 * soldiers' basic loads ({@link Subsistence}), and raw ore pooled for armour - the force's, drawn
 * by whoever it completes a piece for (docs/echelons/tenets.md, specialists produce, missions consume).
 */
public record Stockpile(int logs, int stone, int rations, int rawIron, int rawCopper, int dirt, int ironIngots, int copperIngots,
                        int wheat, int seeds, int saplings) {

    /** None of the farm's or the forest's makings counted. */
    public Stockpile(int logs, int stone, int rations, int rawIron, int rawCopper, int dirt, int ironIngots, int copperIngots) {
        this(logs, stone, rations, rawIron, rawCopper, dirt, ironIngots, copperIngots, 0, 0, 0);
    }

    /** What {@code chests} hold between them. */
    public static Stockpile sum(java.util.Collection<Stockpile> chests) {
        java.util.List<Stockpile> all = java.util.List.copyOf(chests);
        return new Stockpile(all.stream().mapToInt(Stockpile::logs).sum(), all.stream().mapToInt(Stockpile::stone).sum(),
                all.stream().mapToInt(Stockpile::rations).sum(), all.stream().mapToInt(Stockpile::rawIron).sum(),
                all.stream().mapToInt(Stockpile::rawCopper).sum(), all.stream().mapToInt(Stockpile::dirt).sum(),
                all.stream().mapToInt(Stockpile::ironIngots).sum(), all.stream().mapToInt(Stockpile::copperIngots).sum(),
                all.stream().mapToInt(Stockpile::wheat).sum(), all.stream().mapToInt(Stockpile::seeds).sum(),
                all.stream().mapToInt(Stockpile::saplings).sum());
    }

    /** How many of {@code kind} it holds - bread as its rations. */
    public int count(Renewable kind) {
        return switch (kind) {
            case BREAD -> rations;
            case WHEAT -> wheat;
            case SEEDS -> seeds;
            case LOGS -> logs;
            case SAPLINGS -> saplings;
        };
    }

    /** No ingots counted. */
    public Stockpile(int logs, int stone, int rations, int rawIron, int rawCopper, int dirt) {
        this(logs, stone, rations, rawIron, rawCopper, dirt, 0, 0);
    }

    /** No dirt counted. */
    public Stockpile(int logs, int stone, int rations, int rawIron, int rawCopper) {
        this(logs, stone, rations, rawIron, rawCopper, 0);
    }

    /** No ore in it. */
    public Stockpile(int logs, int stone, int rations) {
        this(logs, stone, rations, 0, 0);
    }
}
