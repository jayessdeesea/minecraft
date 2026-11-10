package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;

/**
 * The narrow, read-only slice of world state a {@code DeterministicSoldier}'s tick depends on -
 * not the shared chunk cache or entity tracker themselves. See docs/architecture.md's \"Behavior modeling\"
 * section on why: passing this in as an argument, rather than the soldier holding a reference to
 * the shared structures, is what keeps the per-tick decision testable with a small fake.
 *
 * <p>Both methods are abstract on purpose. A default "no hostiles" would let an implementation that
 * only knows terrain compile and silently blind every soldier to threats; an implementation has to
 * answer both questions explicitly. {@link WorldModel} is the real one.
 */
public interface WorldView {

    /**
     * Block-height-only - checks the block at the given coordinate, not a full ~1.8-block entity
     * bounding box. Coarser than real client collision, deliberately: nothing yet needs the precision.
     */
    boolean isSolid(int x, int y, int z);

    /** Somewhere never to stand in or walk through: water, lava, fire, powder snow. Not solid either. */
    boolean isHazard(int x, int y, int z);

    /** Whether the block at (x, y, z) is a torch - a light a patrol base is lit by. */
    default boolean isTorch(int x, int y, int z) {
        return false;
    }

    /** Whether the block at (x, y, z) is a chest (it's also {@link #isSolid}). */
    default boolean isChest(int x, int y, int z) {
        return false;
    }

    /** The crafting table or furnace at (x, y, z), if there is one (it's also {@link #isSolid}). */
    default java.util.Optional<Workstation> workstation(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /**
     * Whether the block is a log a soldier can chop (it's also {@link #isSolid}). A view that knows
     * no blocks' identity - a test's synthetic terrain - has no logs.
     */
    default boolean isLog(int x, int y, int z) {
        return false;
    }

    /** Whether the block is plain stone a soldier can quarry (it's also {@link #isSolid}). */
    default boolean isStone(int x, int y, int z) {
        return false;
    }

    /** Whether the block is iron ore a soldier can mine with a stone pickaxe (it's also {@link #isSolid}). */
    default boolean isIronOre(int x, int y, int z) {
        return false;
    }

    /** Whether the block is a water source - what a farm is tilled beside (it's also {@link #isHazard}). */
    default boolean isWaterSource(int x, int y, int z) {
        return false;
    }

    /** What digging the block takes, if it's one a soldier digs: rock, grit, soil, stone, ore. */
    default java.util.Optional<Hardness> hardness(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /** The tree whose leaves the block is ("spruce", "oak"), if it's leaves (it's also {@link #isSolid}). */
    default java.util.Optional<String> leaves(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /** The tree the block's sapling grows into, if it's a sapling. */
    default java.util.Optional<String> sapling(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /** What ore the block is - "copper", "iron", "diamond" - if it's ore. */
    default java.util.Optional<String> oreOf(int x, int y, int z) {
        return java.util.Optional.empty();
    }

    /** Whether the block is gravel, which drops flint one time in ten. */
    default boolean isGravel(int x, int y, int z) {
        return false;
    }

    /** Whether the block is ore of any kind: coal, iron, copper, gold, redstone, lapis, emerald, diamond. */
    default boolean isOre(int x, int y, int z) {
        return false;
    }

    /** Whether the block is water, source or flowing - what a soldier swims in (it's also {@link #isHazard}). */
    default boolean isWater(int x, int y, int z) {
        return false;
    }

    /** Whether a hoe tills the block into farmland: dirt, grass, a dirt path. */
    default boolean isTillable(int x, int y, int z) {
        return false;
    }

    /** Whether the block is short or tall grass, broken for wheat seeds. */
    default boolean isGrassPlant(int x, int y, int z) {
        return false;
    }

    /** Whether the block is farmland (it's also {@link #isSolid}). */
    default boolean isFarmland(int x, int y, int z) {
        return false;
    }

    /** The wheat's growth stage, 0 to 7 (ripe), if the block is wheat. */
    default java.util.OptionalInt wheatAge(int x, int y, int z) {
        return java.util.OptionalInt.empty();
    }

    /** Whether the block is soil - dirt, grass, clay - that a soldier digs by hand (it's also {@link #isSolid}). */
    default boolean isSoil(int x, int y, int z) {
        return false;
    }

    /**
     * Where the bodies within {@code radius} of {@code center} are - squadmates and mobs, which a
     * block can't be placed into. A view that tracks no entities sees none.
     */
    default List<Position> bodiesNear(Position center, double radius) {
        return List.of();
    }

    /** Where the players within {@code radius} of {@code center} are - squadmates, perhaps the asker itself. A view that tracks no entities sees none. */
    default List<Position> playersNear(Position center, double radius) {
        return List.of();
    }

    /** As {@link #playersNear(Position, double)}, leaving out the player whose entity is {@code except} - the asker itself. */
    default List<Position> playersNear(Position center, double radius, int except) {
        return playersNear(center, radius);
    }

    /** Where the dropped items within {@code radius} of {@code center} are, nearest first. A view that tracks no entities sees none. */
    default List<TrackedEntity> gameNear(Position center, double radius) {
        return List.of();
    }

    default List<Position> itemsNear(Position center, double radius) {
        return List.of();
    }

    /** Hostile entities within {@code radius} of {@code center}, nearest first. */
    List<TrackedEntity> nearbyHostiles(Position center, double radius);

    /**
     * Arrows in flight within {@code radius} of {@code center}, nearest first - what a raised shield
     * is for. A view that tracks no entities sees none.
     */
    default List<Position> arrowsNear(Position center, double radius) {
        return List.of();
    }
}
