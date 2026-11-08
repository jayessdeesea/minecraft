package io.github.jayessdeesea.minecraft.command;

/** One block's place in the world: the cell whose lower north-west corner is (x, y, z). */
public record Cell(int x, int y, int z) {
}
