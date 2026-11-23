package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Miner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ore the force has left in the rock for want of the pickaxe (operator, 2026-11-22: if you only have wood, leave the
 * diamonds there, make a note, and we will come back): each block and the pickaxe's tier it wants, kept on disk with the
 * mines ({@link WallStore}), so the note outlives the miner that made it and a restart. Read back by tier, for whoever
 * sends a soldier with that pickaxe.
 */
final class OreNotes {

    private static final Logger LOG = LogManager.getLogger(OreNotes.class);

    private final WallStore store;
    private final Map<Integer, List<BlockPos>> byTier = new HashMap<>();

    OreNotes(WallStore store) {
        this.store = store;
    }

    private static String key(int tier) {
        return "ore-left,tier" + tier;
    }

    /** The ore noted as wanting a pickaxe of {@code tier}, in the order it was found. */
    synchronized List<BlockPos> left(int tier) {
        return List.copyOf(byTier.computeIfAbsent(tier, t -> new ArrayList<>(store.load(key(t)).orElse(List.of()))));
    }

    /** Notes {@code ore}, {@code soldier}'s find: kept, and said, once a block. */
    synchronized void note(Miner.OreLeft ore, String soldier) {
        left(ore.tier());
        List<BlockPos> noted = byTier.get(ore.tier());
        if (noted.contains(ore.at())) {
            return;
        }
        noted.add(ore.at());
        store.save(key(ore.tier()), noted);
        LOG.info("[Ore] {} left ore at ({}, {}, {}) that wants a pickaxe of tier {} - {} noted for one", soldier, ore.at().x(), ore.at().y(), ore.at().z(),
                ore.tier(), noted.size());
    }
}
