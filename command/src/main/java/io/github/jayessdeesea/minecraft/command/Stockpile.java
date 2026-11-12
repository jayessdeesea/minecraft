package io.github.jayessdeesea.minecraft.command;

/**
 * What the stockpile - the supply point's chest - held when a soldier last opened it
 * (docs/military-doctrine.md §12): gathered materials by kind, the rations put in beyond
 * soldiers' basic loads ({@link Subsistence}), and raw ore pooled for armour - the force's, drawn
 * by whoever it completes a piece for (docs/echelons/tenets.md, specialists produce, missions consume).
 */
public record Stockpile(int logs, int stone, int rations, int rawIron, int rawCopper) {

    /** No ore in it. */
    public Stockpile(int logs, int stone, int rations) {
        this(logs, stone, rations, 0, 0);
    }
}
