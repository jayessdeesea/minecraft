package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Stockpile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The force's stores as last seen, kept on disk so a restart doesn't leave the platoon leader blind until a
 * chest is opened: live (field469), a minute after a restart the stockpile at 0 logs read as unknown, wood wasn't
 * short, and both released squads were sent for iron. Kept chest by chest, so the stores are every standing chest's,
 * not those opened since the restart: live (field480-484), the seeds read 702, 64, 0, 88, 0 across five runs in an
 * hour as the first chest opened after each restart stood for all of them. Thread-safe.
 */
final class StoresBook {

    private static final String KEY = "stores";
    /** Each chest's, as (x, y, z) and the eleven counts. */
    private static final String CHESTS = "stores_by_chest";

    private final WallStore store;

    StoresBook(WallStore store) {
        this.store = store;
    }

    /** What each chest held when last seen, as kept before the restart. */
    synchronized Map<BlockPos, Stockpile> chests() {
        Map<BlockPos, Stockpile> chests = new HashMap<>();
        store.loadRows(CHESTS).orElse(List.of()).stream().filter(r -> r.length == 14).forEach(r -> chests.put(new BlockPos((int) r[0], (int) r[1], (int) r[2]),
                new Stockpile((int) r[3], (int) r[4], (int) r[5], (int) r[6], (int) r[7], (int) r[8], (int) r[9], (int) r[10], (int) r[11],
                        (int) r[12], (int) r[13])));
        return chests;
    }

    /**
     * {@code chest}'s counts by kind, as a chest opened says them (Depositor.stockSeen): what a soldier's draw looks
     * for in the chests known - restored with them, not only once one's opened again: live (field640-654), 550 wheat
     * kept in chests by the dump and no baker drew any after a restart.
     */
    static Map<ItemClass, Integer> kinds(Stockpile chest) {
        Map<ItemClass, Integer> kinds = new java.util.EnumMap<>(ItemClass.class);
        kinds.put(ItemClass.LOG, chest.logs());
        kinds.put(ItemClass.COBBLESTONE, chest.stone());
        kinds.put(ItemClass.FOOD, chest.rations());
        kinds.put(ItemClass.RAW_IRON, chest.rawIron());
        kinds.put(ItemClass.RAW_COPPER, chest.rawCopper());
        kinds.put(ItemClass.DIRT, chest.dirt());
        kinds.put(ItemClass.IRON_INGOT, chest.ironIngots());
        kinds.put(ItemClass.COPPER_INGOT, chest.copperIngots());
        kinds.put(ItemClass.WHEAT, chest.wheat());
        kinds.put(ItemClass.SEEDS, chest.seeds());
        kinds.put(ItemClass.SAPLING, chest.saplings());
        return kinds;
    }

    /** Keeps what each of {@code chests} held, and hands on the stores: all of them, summed. */
    synchronized Stockpile keep(Map<BlockPos, Stockpile> chests) {
        store.saveRows(CHESTS, chests.entrySet().stream().map(e -> {
            Stockpile s = e.getValue();
            return new long[]{e.getKey().x(), e.getKey().y(), e.getKey().z(), s.logs(), s.stone(), s.rations(), s.rawIron(), s.rawCopper(),
                    s.dirt(), s.ironIngots(), s.copperIngots(), s.wheat(), s.seeds(), s.saplings()};
        }).toList());
        return keep(Stockpile.sum(chests.values()));
    }

    /** The stores kept from before the restart, if any were. */
    synchronized Optional<Stockpile> kept() {
        return store.loadRows(KEY).filter(rows -> !rows.isEmpty() && rows.getFirst().length == 11).map(rows -> {
            long[] r = rows.getFirst();
            return new Stockpile((int) r[0], (int) r[1], (int) r[2], (int) r[3], (int) r[4], (int) r[5], (int) r[6], (int) r[7],
                    (int) r[8], (int) r[9], (int) r[10]);
        });
    }

    /** Keeps {@code stores}, and hands them on. */
    synchronized Stockpile keep(Stockpile s) {
        store.saveRows(KEY, List.of(new long[]{s.logs(), s.stone(), s.rations(), s.rawIron(), s.rawCopper(), s.dirt(), s.ironIngots(),
                s.copperIngots(), s.wheat(), s.seeds(), s.saplings()}));
        return s;
    }
}
