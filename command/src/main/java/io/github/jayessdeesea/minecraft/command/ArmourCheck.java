package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The platoon leader's armour check, a pre-combat check (docs/echelons/platoon-leader/decisions.md, "Armour"): each
 * soldier's inventory is read - the armour each has on or carries, the copper and iron it carries, whether its pack has
 * room - and each soldier with a slot neither is ordered the pieces for it the metal pays for, its pack's first, then
 * with the stores', which are dealt out so no two are dealt the same ingots. One with no metal for a piece, or no room
 * in its pack, is ordered nothing: that is said instead.
 */
public final class ArmourCheck {

    private ArmourCheck() {
    }

    /** An armour slot, in the order the equipment ladder fills them, and the ingots its piece takes. */
    public enum Piece {
        CHESTPLATE(8), LEGGINGS(7), BOOTS(4), HELMET(5);

        public final int ingots;

        Piece(int ingots) {
            this.ingots = ingots;
        }
    }

    /**
     * A soldier's answer to the call for inventories.
     *
     * @param held   the slots it has a piece for, on or carried - a carried one is put on at its next stand-to
     * @param copper copper carried, ingots and raw ore, one to an ingot
     * @param iron   iron carried, the same
     * @param room   whether its pack has a free slot for a piece made
     */
    public record Inventory(String soldier, String squad, Set<Piece> held, int copper, int iron, boolean room) {
        public Inventory {
            held = held.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(held));
        }
    }

    /** The metal in the stores, ingots and raw ore. */
    public record Stores(int copper, int iron) {
        public static Stores of(Stockpile stockpile) {
            return new Stores(stockpile.copperIngots() + stockpile.rawCopper(), stockpile.ironIngots() + stockpile.rawIron());
        }
    }

    /** What the check gives a soldier. */
    public sealed interface Verdict {
        String soldier();

        String squad();
    }

    /** Every slot has a piece for it, on or carried: nothing to order. */
    public record Armoured(String soldier, String squad) implements Verdict {
    }

    /** Ordered to make {@code pieces} and put them on, {@code fromStores} of them with metal drawn from the stores. */
    public record Ordered(String soldier, String squad, List<Piece> pieces, int fromStores) implements Verdict {
        public Ordered {
            pieces = List.copyOf(pieces);
        }
    }

    /** A slot empty and nothing ordered, for the reason given. */
    public record Unable(String soldier, String squad, String why) implements Verdict {
    }

    static final String NO_METAL = "no copper or iron for a piece, in its pack or the stores";
    static final String NO_ROOM = "no room in its pack for a piece";

    /**
     * Each soldier's verdict, in the order given back. The least armoured are dealt the stores first, then by squad and
     * name; a piece is made of one metal, copper before iron as the ladder climbs, of the pack's alone if it holds enough,
     * else of the pack's and the stores' together, the pack's spent first, as a draw makes it up (bot.supply.MetalPool); and
     * iron counts only where {@code ironIssued} - the camp's level authorises iron armour (docs/military-doctrine.md
     * § 11, basis of issue).
     */
    public static List<Verdict> check(List<Inventory> inventories, Stores stores, boolean ironIssued) {
        int[] storesLeft = {stores.copper(), ironIssued ? stores.iron() : 0};
        List<Inventory> dealt = inventories.stream().sorted(Comparator.<Inventory>comparingInt(i -> i.held().size())
                .thenComparing(Inventory::squad).thenComparing(Inventory::soldier)).toList();
        java.util.Map<String, Verdict> verdicts = new java.util.HashMap<>();
        for (Inventory inventory : dealt) {
            verdicts.put(inventory.soldier(), verdict(inventory, storesLeft, ironIssued));
        }
        return inventories.stream().map(i -> verdicts.get(i.soldier())).toList();
    }

    private static Verdict verdict(Inventory inventory, int[] storesLeft, boolean ironIssued) {
        List<Piece> empty = EnumSet.allOf(Piece.class).stream().filter(p -> !inventory.held().contains(p)).toList();
        if (empty.isEmpty()) {
            return new Armoured(inventory.soldier(), inventory.squad());
        }
        int[] pack = {inventory.copper(), ironIssued ? inventory.iron() : 0};
        int[] stores = storesLeft.clone();
        List<Piece> pieces = new ArrayList<>();
        int fromStores = 0;
        for (Piece piece : empty) {
            if (pay(pack, piece)) {
                pieces.add(piece);
            } else if (payWith(pack, stores, piece)) {
                pieces.add(piece);
                fromStores++;
            }
        }
        if (pieces.isEmpty()) {
            return new Unable(inventory.soldier(), inventory.squad(), NO_METAL);
        }
        if (!inventory.room()) {
            return new Unable(inventory.soldier(), inventory.squad(), NO_ROOM);
        }
        System.arraycopy(stores, 0, storesLeft, 0, stores.length);
        return new Ordered(inventory.soldier(), inventory.squad(), pieces, fromStores);
    }

    /** Takes {@code piece}'s ingots from {@code metal} - copper, else iron - if either holds them. */
    private static boolean pay(int[] metal, Piece piece) {
        for (int i = 0; i < metal.length; i++) {
            if (metal[i] >= piece.ingots) {
                metal[i] -= piece.ingots;
                return true;
            }
        }
        return false;
    }

    /** Takes {@code piece}'s ingots of one metal - copper, else iron - from {@code pack} and {@code stores} together, the pack's first. */
    private static boolean payWith(int[] pack, int[] stores, Piece piece) {
        for (int i = 0; i < pack.length; i++) {
            if (pack[i] + stores[i] >= piece.ingots) {
                stores[i] -= piece.ingots - pack[i];
                pack[i] = 0;
                return true;
            }
        }
        return false;
    }

    /** How a verdict is said: the squad's report of the soldier up, and the platoon leader's order or its reason down. */
    public static String said(Verdict verdict, Inventory inventory) {
        String report = "[" + inventory.squad() + " -> PL] " + inventory.soldier() + ": armour " + inventory.held().size() + " of "
                + Piece.values().length + " held, carries " + inventory.copper() + " copper, " + inventory.iron() + " iron"
                + (inventory.room() ? "" : ", its pack full");
        return report + switch (verdict) {
            case Armoured a -> "; armoured";
            case Ordered o -> "; [PL -> " + o.squad() + "] " + o.soldier() + ": make and put on " + o.pieces().stream()
                    .map(p -> p.name().toLowerCase(java.util.Locale.ROOT)).toList()
                    + (o.fromStores() == 0 ? ", from its pack" : ", " + o.fromStores() + " of the stores' metal");
            case Unable u -> "; no armour ordered: " + u.why();
        };
    }
}
