package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.farm.StoresChunk;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Fixtures;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.FarmMission;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * What a template step of a {@link FarmMission} has left, measured from the world: each kind's measure says its count
 * through {@code say} and is what the step was done with, or empty while it isn't. Pure over the world; the step's
 * register and its next are {@link FarmMissions}'.
 */
final class TemplateMeasures {

    private TemplateMeasures() {
    }

    /** A wheat farm's: its ground laid to the template. */
    static Optional<String> farm(WorldView world, FarmMission m, FarmMission.Step step, Consumer<String> say) {
        ChunkFarm farm = new ChunkFarm(m.chunkX(), m.chunkZ(), step.level());
        FarmSite.Work work = FarmSite.work(world, farm);
        say.accept(work.stage() + " - " + work + (work.stage() != FarmSite.Stage.SURVEYED ? "" : ", to fill "
                + FarmSite.surveyed(world, farm).get(1).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()
                + ", to pave " + FarmSite.surveyed(world, farm).get(2).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()
                + ", in the way " + FarmSite.surveyed(world, farm).get(0).stream().limit(6).map(c -> c.x() + "," + c.y() + "," + c.z()).toList()));
        return work.stage() == FarmSite.Stage.SURVEYED ? Optional.empty() : Optional.of("the template's ground laid at y=" + step.level() + ", " + work.stage());
    }

    /** A tree farm's: its trees' soil, then planted, its chest in and lit. */
    static Optional<String> trees(WorldView world, FarmMission m, FarmMission.Step step, Consumer<String> say) {
        TreeChunk chunk = new TreeChunk(m.chunkX(), m.chunkZ(), step.level());
        if (chunk.trunks().stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
            say.accept("not all of it in sight");
            return Optional.empty();
        }
        // Its trees' dirt first: each tree's cell of the paving dug out and dirt laid in it, the squad's (TemplateGround.treeOrders).
        int toDig = chunk.soilToDig(world).size(), toLay = chunk.soilToLay(world).size();
        if (toDig + toLay > 0) {
            say.accept("GROUND - of its " + chunk.soil().size() + " trees' cells, " + toDig + " to dig out of the paving and " + toLay + " to lay in dirt");
            return Optional.empty();
        }
        // Planted, its chest in and lit: the fence is the grid's, not this mission's. A trunk with no soil under it
        // is said, since the stage stands at GROUND while one lacks it and the planters pass it over.
        TreeChunk.Stage stage = chunk.stage(world);
        int takes = chunk.plantable(world).size();
        say.accept(stage + " - " + takes + " of " + chunk.trunks().size() + " trunk cells take a sapling"
                + (stage == TreeChunk.Stage.GROUND ? ", the rest want soil under them and nothing over" : "")
                + (stage != TreeChunk.Stage.PLANTED ? "" : ", not planted: " + chunk.trunks().stream().filter(t -> !io.github.jayessdeesea.minecraft.bot.farm.TreeFarm.planted(world, t))
                .limit(4).map(t -> t.x() + "," + t.y() + "," + t.z()).toList() + (world.isChest(chunk.chest().x(), chunk.chest().y(), chunk.chest().z()) ? "" : ", no chest")));
        return stage.compareTo(TreeChunk.Stage.PRODUCING) >= 0 ? Optional.of("its " + chunk.trunks().size()
                + " saplings in, its chest and its " + chunk.torches().size() + " torches - " + stage) : Optional.empty();
    }

    /** A pasture's: its ground laid, fenced, gated and lit, its chest up. */
    static Optional<String> pasture(WorldView world, FarmMission m, FarmMission.Step step, Consumer<String> say) {
        io.github.jayessdeesea.minecraft.bot.farm.PastureChunk pasture = TemplateDrift.pasture(m);
        if (!world.isLoaded(m.minX(), m.minZ()) || !world.isLoaded(m.minX() + 15, m.minZ() + 15)) {
            say.accept("not all of it in sight");
            return Optional.empty();
        }
        int toLay = pasture.groundToLay(world).size(), toPave = pasture.footingsToPave(world).size();
        Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, pasture.fixtures());
        say.accept((toLay + toPave > 0 ? "GROUND - " + toLay + " cell(s) to lay in dirt, " + toPave + " footing(s) to pave; " : "")
                + "to put up: " + (left.isEmpty() ? "nothing" : left.entrySet().stream()
                .map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "))));
        return toLay + toPave == 0 && left.isEmpty() ? Optional.of("its ground laid, fenced, gated and lit, its chest up") : Optional.empty();
    }

    /** A supply point's: stocked - fenced and lit, its workshop in, its first rows up. */
    static Optional<String> stores(WorldView world, FarmMission m, FarmMission.Step step, Consumer<String> say) {
        StoresChunk stores = new StoresChunk(m.chunkX(), m.chunkZ(), step.level());
        if (stores.ground().stream().anyMatch(c -> !world.isLoaded(c.x(), c.z()))) {
            say.accept("not all of it in sight");
            return Optional.empty();
        }
        // Stocked - fenced and lit, its workshop in, its first rows up: the rows after are a follow-on order.
        Map<io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass, List<BlockPos>> left = Fixtures.left(world, stores.fixtures());
        say.accept(stores.stage(world) + " - to put up: " + (left.isEmpty() ? "nothing" : left.entrySet().stream()
                .map(e -> e.getValue().size() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "))));
        return stores.stocked(world) ? Optional.of("fenced and lit, its workshop in and its "
                + StoresChunk.FIRST_ROWS.size() * 6 + " double chests up") : Optional.empty();
    }
}
