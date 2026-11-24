package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.farm.PlaceholderChunk;
import io.github.jayessdeesea.minecraft.bot.farm.StoresChunk;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Fixtures;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.FarmMission;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What has gone from a template that was built, read from the world against its layout: the measure the missions'
 * review opens a template again on ({@code FarmMissions.review}). Pure over the world's view and the mission.
 */
final class TemplateDrift {

    private TemplateDrift() {
    }

    /** Whether a step of {@code kind} is a template whose sky is its own to keep clear here: a wheat farm's is its site's (ChunkFarm.obstructions). */
    static boolean skyKept(FarmMission.Kind kind) {
        return kind == FarmMission.Kind.TREE_TEMPLATE || kind == FarmMission.Kind.STORES_TEMPLATE || kind == FarmMission.Kind.MINE_HEAD
                || kind == FarmMission.Kind.PASTURE_TEMPLATE || kind == FarmMission.Kind.PLACEHOLDER_TEMPLATE;
    }

    /**
     * What hangs in the sky over {@code m}'s chunk and its fence lines (ChunkFarm.sky) - and, on a tree farm, what stands
     * in a trunk's own cell or the one over it that is no tree: a step left there, under the sky's reach, that nothing
     * is planted through.
     */
    static List<BlockPos> sky(FarmMission m, WorldView world) {
        List<BlockPos> hanging = new java.util.ArrayList<>(new ChunkFarm(m.chunkX(), m.chunkZ(), m.level()).sky(world));
        if (m.crop() == FarmMission.Crop.TREES) {
            for (BlockPos trunk : new TreeChunk(m.chunkX(), m.chunkZ(), m.level()).trunks()) {
                for (BlockPos c : List.of(trunk, trunk.offset(0, 1, 0))) {
                    if (world.solidHere(c.x(), c.y(), c.z()) && !world.isLog(c.x(), c.y(), c.z()) && world.leaves(c.x(), c.y(), c.z()).isEmpty() && !hanging.contains(c)) {
                        hanging.add(c);
                    }
                }
            }
        }
        hanging.addAll(misplaced(world, layout(m)));
        clutter(m, world).stream().filter(c -> !hanging.contains(c)).forEach(hanging::add);
        return hanging;
    }

    /**
     * The loose ground standing on {@code m}'s template, inside its fence, a block or two over its level: soil, stone,
     * gravel or cobblestone - a step put up on the way and left - that the sky's reach starts over. Nothing of a
     * template is ground at that height, so none of it is spared; a chest, a table, a post or a tree isn't ground.
     */
    static List<BlockPos> clutter(FarmMission m, WorldView world) {
        List<BlockPos> loose = new java.util.ArrayList<>();
        for (int x = m.minX() + 1; x <= m.minX() + 15; x++) {
            for (int z = m.minZ() + 1; z <= m.minZ() + 15; z++) {
                for (int y = m.level() + 1; y <= m.level() + 2; y++) {
                    if (world.isSoil(x, y, z) || world.isStone(x, y, z) || world.isGravel(x, y, z) || world.isCobblestone(x, y, z)) {
                        loose.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return loose;
    }

    /** {@code m}'s fixtures as its template lays them out: none for a wheat or tree farm, whose ground work is its own. */
    static Map<ItemClass, List<BlockPos>> layout(FarmMission m) {
        return switch (m.crop()) {
            case STORES -> new StoresChunk(m.chunkX(), m.chunkZ(), m.level()).fixtures();
            case MINE -> new io.github.jayessdeesea.minecraft.bot.farm.MineHead(m.chunkX(), m.chunkZ(), m.level()).fixtures();
            case PLACEHOLDER -> new PlaceholderChunk(m.chunkX(), m.chunkZ(), m.level()).fixtures();
            case SHEEP, COW, PIG, CHICKEN -> pasture(m).fixtures();
            case WHEAT, TREES -> Map.of();
        };
    }

    /**
     * What stands in a fence's or a gate's cell of {@code layout} that is neither, and can be dug: a step left on the line
     * before its post went up. A fixture is passed over where its cell is taken, so the line would stand shut by a block
     * of dirt where its gate belongs - live, a mineshaft's mouth with no way in. A post is known by the cell over it,
     * solid to a body and no block; what isn't dug - a chest - is left for its salvage.
     */
    static List<BlockPos> misplaced(WorldView world, Map<ItemClass, List<BlockPos>> layout) {
        List<BlockPos> wrong = new java.util.ArrayList<>();
        for (BlockPos c : layout.getOrDefault(ItemClass.FENCE, List.of())) {
            if (world.solidHere(c.x(), c.y(), c.z()) && !world.isGate(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z())) {
                wrong.add(c);
            }
        }
        for (BlockPos c : layout.getOrDefault(ItemClass.FENCE_GATE, List.of())) {
            if (world.solidHere(c.x(), c.y(), c.z()) && !world.isGate(c.x(), c.y(), c.z())) {
                wrong.add(c);
            }
        }
        return wrong.stream().filter(c -> io.github.jayessdeesea.minecraft.bot.engineer.Clearing.diggable(world, c.x(), c.y(), c.z())).toList();
    }

    /** {@code m}'s animal farm, as its template lays it out. */
    static io.github.jayessdeesea.minecraft.bot.farm.PastureChunk pasture(FarmMission m) {
        return new io.github.jayessdeesea.minecraft.bot.farm.PastureChunk(m.chunkX(), m.chunkZ(), m.level(),
                io.github.jayessdeesea.minecraft.bot.farm.PastureChunk.Animal.valueOf(m.crop().name()));
    }

    /**
     * What of {@code m}'s template has gone since it was built, as {@code world} stands - its structure, not its crop:
     * wheat's ground (tilling, sowing and its fixtures are its tender's), a tree chunk's chest and torches (felling and
     * planting are its foresters'), a supply point's fence, lights, workshop and first rows. Empty while it stands.
     */
    static Optional<String> drifted(WorldView world, FarmMission m) {
        Optional<String> gone = switch (m.crop()) {
            case WHEAT -> {
                FarmSite.Work work = FarmSite.work(world, new ChunkFarm(m.chunkX(), m.chunkZ(), m.level()));
                yield work.stage() == FarmSite.Stage.SURVEYED ? Optional.of("its ground is gone in places - " + work) : Optional.empty();
            }
            case TREES -> {
                TreeChunk chunk = new TreeChunk(m.chunkX(), m.chunkZ(), m.level());
                long dark = chunk.torches().stream().filter(t -> !world.isTorch(t.x(), t.y(), t.z())).count();
                boolean chest = world.isChest(chunk.chest().x(), chunk.chest().y(), chunk.chest().z());
                yield dark == 0 && chest ? Optional.empty() : Optional.of((chest ? "" : "its chest is gone; ") + dark + " of its " + chunk.torches().size() + " torches gone");
            }
            case MINE -> Optional.empty(); // mined dry and done with: its ground is another template's next
            case PLACEHOLDER -> Optional.of(Fixtures.left(world, new PlaceholderChunk(m.chunkX(), m.chunkZ(), m.level()).fixtures()).values().stream().mapToInt(List::size).sum())
                    .filter(n -> n > 0).map(n -> n + " of its fence, gates and torches gone");
            case SHEEP, COW, PIG, CHICKEN -> {
                io.github.jayessdeesea.minecraft.bot.farm.PastureChunk pasture = pasture(m);
                Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, pasture.fixtures());
                yield pasture.grounded(world) && left.isEmpty() ? Optional.empty() : Optional.of("to put right: " + pasture.groundToLay(world).size()
                        + " of its ground, " + pasture.footingsToPave(world).size() + " footings, " + left.values().stream().mapToInt(List::size).sum() + " fixtures");
            }
            case STORES -> {
                StoresChunk stores = new StoresChunk(m.chunkX(), m.chunkZ(), m.level());
                yield stores.stocked(world) ? Optional.empty() : Optional.of("back at " + stores.stage(world) + " - to put up again: "
                        + Fixtures.left(world, stores.fixtures()).entrySet().stream().map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))
                        .collect(Collectors.joining(", ")));
            }
        };
        // And its sky, whatever it is but a wheat farm (its site's own) or a mine worked out.
        boolean itsSky = m.crop() != FarmMission.Crop.WHEAT && m.crop() != FarmMission.Crop.MINE;
        return gone.or(() -> Optional.of(sky(m, world)).filter(h -> itsSky && !h.isEmpty()).map(h -> h.size() + " block(s) hanging in its sky"));
    }
}
