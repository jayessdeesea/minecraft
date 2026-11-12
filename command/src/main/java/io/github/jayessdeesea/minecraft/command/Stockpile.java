package io.github.jayessdeesea.minecraft.command;

/**
 * What the stockpile - the supply point's chest - held when a soldier last opened it
 * (docs/military-doctrine.md §12): gathered materials by kind, and the rations put in beyond
 * soldiers' basic loads ({@link Subsistence}).
 */
public record Stockpile(int logs, int stone, int rations) {
}
