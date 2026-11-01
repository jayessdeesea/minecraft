package io.github.jayessdeesea.minecraft.bot.nav;

/** A block cell. As a path node it's where a soldier's feet stand: this cell and the one above clear, the one below solid. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
