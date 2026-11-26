package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The platoon leader's armour check, a pre-combat check (docs/echelons/platoon-leader/decisions.md, "Armour"): each
 * soldier's inventory is read - the armour and shield each has on or carries, the copper and iron it carries, whether its
 * pack has room - and each soldier with a slot neither, of those its camp's level issues, is ordered the pieces for it the
 * metal pays for, its pack's first, then with the stores', which are dealt out so no two are dealt the same ingots. One
 * with no metal for a piece, or no room in its pack, is ordered nothing: that is said instead.
 */
public final class ArmourCheck {

    private ArmourCheck() {
    }

    /**
     * A slot - the off hand's shield, the four of armour - in the order the equipment ladder fills them, and the ingots its
     * piece takes: of copper or iron, the shield's of iron only (and six planks, of the wood a rung is made from).
     */
    public enum Piece {
        SHIELD(1), CHESTPLATE(8), LEGGINGS(7), BOOTS(4), HELMET(5);

        /** The four slots of armour, the shield aside. */
        public static final Set<Piece> ARMOUR = Set.of(CHESTPLATE, LEGGINGS, BOOTS, HELMET);

        public final int ingots;

        Piece(int ingots) {
            this.ingots = ingots;
        }
    }

    /**
     * What the camp's level issues (docs/military-doctrine.md § 11, basis of issue; bot.craft.Ladder.authorised): copper
     * armour at any; {@code ironArmour}, iron armour; {@code shield}, the shield. A slot not issued is neither ordered nor
     * missed.
     */
    public record Issue(boolean ironArmour, boolean shield) {
        /** Whether {@code piece} may be made of metal {@code metal}: 0 copper, 1 iron. */
        boolean of(Piece piece, int metal) {
            return piece == Piece.SHIELD ? shield && metal == 1 : metal == 0 || ironArmour;
        }
    }

    /**
     * A soldier's answer to the call for inventories.
     *
     * @param held   the slots it has a piece for, on or carried - a carried one is put on at its next stand-to, a shield in the off hand
     * @param copper copper carried, ingots and raw ore, one to an ingot
     * @param iron   iron carried, the same
     * @param planks planks carried, and four to each log: a shield takes {@link #SHIELD_PLANKS}
     * @param room   whether its pack has a free slot for a piece made
     */
    public record Inventory(String soldier, String squad, Set<Piece> held, int copper, int iron, int planks, boolean room) {
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

    /** Every slot issued has a piece for it, on or carried: nothing to order. */
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

    /** A shield's planks: its wood is the pack's, as any rung's - logs aren't drawn from the stores. */
    public static final int SHIELD_PLANKS = 6;

    static final String NO_METAL = "no metal for a piece in its pack or the stores - copper or iron for armour, iron for a shield";
    static final String NO_WOOD = "no wood in its pack for a shield's planks";
    static final String NO_ROOM = "no room in its pack for a piece";

    /**
     * Each soldier's verdict, in the order given back. The least armoured are dealt the stores first, then by squad and
     * name; a piece is made of one metal, copper before iron as the ladder climbs, of the pack's alone if it holds enough,
     * else of the pack's and the stores' together, the pack's spent first, as a draw makes it up (bot.supply.MetalPool); and
     * only the slots, and the metal for each, that {@code issued} says.
     */
    public static List<Verdict> check(List<Inventory> inventories, Stores stores, Issue issued) {
        int[] storesLeft = {stores.copper(), stores.iron()};
        List<Inventory> dealt = inventories.stream().sorted(Comparator.<Inventory>comparingInt(i -> (int) i.held().stream().filter(p -> issued.of(p, 0) || issued.of(p, 1)).count())
                .thenComparing(Inventory::squad).thenComparing(Inventory::soldier)).toList();
        java.util.Map<String, Verdict> verdicts = new java.util.HashMap<>();
        for (Inventory inventory : dealt) {
            verdicts.put(inventory.soldier(), verdict(inventory, storesLeft, issued));
        }
        return inventories.stream().map(i -> verdicts.get(i.soldier())).toList();
    }

    private static Verdict verdict(Inventory inventory, int[] storesLeft, Issue issued) {
        List<Piece> empty = EnumSet.allOf(Piece.class).stream()
                .filter(p -> !inventory.held().contains(p) && (issued.of(p, 0) || issued.of(p, 1))).toList();
        if (empty.isEmpty()) {
            return new Armoured(inventory.soldier(), inventory.squad());
        }
        int[] pack = {inventory.copper(), inventory.iron()};
        int[] stores = storesLeft.clone();
        List<Piece> pieces = new ArrayList<>();
        int fromStores = 0;
        boolean noWood = false;
        for (Piece piece : empty) {
            if (piece == Piece.SHIELD && inventory.planks() < SHIELD_PLANKS) {
                noWood = true; // passed over, as a piece the metal doesn't pay for
            } else if (pay(pack, piece, issued)) {
                pieces.add(piece);
            } else if (payWith(pack, stores, piece, issued)) {
                pieces.add(piece);
                fromStores++;
            }
        }
        if (pieces.isEmpty()) {
            return new Unable(inventory.soldier(), inventory.squad(), noWood && empty.size() == 1 ? NO_WOOD : NO_METAL);
        }
        if (!inventory.room()) {
            return new Unable(inventory.soldier(), inventory.squad(), NO_ROOM);
        }
        System.arraycopy(stores, 0, storesLeft, 0, stores.length);
        return new Ordered(inventory.soldier(), inventory.squad(), pieces, fromStores);
    }

    /** Takes {@code piece}'s ingots from {@code metal} - copper, else iron, as issued - if either holds them. */
    private static boolean pay(int[] metal, Piece piece, Issue issued) {
        for (int i = 0; i < metal.length; i++) {
            if (issued.of(piece, i) && metal[i] >= piece.ingots) {
                metal[i] -= piece.ingots;
                return true;
            }
        }
        return false;
    }

    /** Takes {@code piece}'s ingots of one metal - copper, else iron, as issued - from {@code pack} and {@code stores} together, the pack's first. */
    private static boolean payWith(int[] pack, int[] stores, Piece piece, Issue issued) {
        for (int i = 0; i < pack.length; i++) {
            if (issued.of(piece, i) && pack[i] + stores[i] >= piece.ingots) {
                stores[i] -= piece.ingots - pack[i];
                pack[i] = 0;
                return true;
            }
        }
        return false;
    }

    /** How a verdict is said: the squad's report of the soldier up, and the platoon leader's order or its reason down. */
    public static String said(Verdict verdict, Inventory inventory) {
        String report = "[" + inventory.squad() + " -> PL] " + inventory.soldier() + ": armour "
                + inventory.held().stream().filter(Piece.ARMOUR::contains).count() + " of " + Piece.ARMOUR.size() + " held"
                + (inventory.held().contains(Piece.SHIELD) ? " and a shield" : ", no shield") + ", carries " + inventory.copper() + " copper, " + inventory.iron() + " iron"
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
