import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One-off, dev-time generator - NOT part of the Maven build, same as
 * ../entity-hostility-generator/GenerateEntityHostility.java. Reads the pinned server jar's own
 * {@code generated/reports/registries.json} and emits the resource
 * {@code bot/src/main/resources/.../item-classes-<version>.txt} that {@code bot.world.ItemClassRegistry}
 * loads: the {@code minecraft:item} protocol IDs of every item a soldier's LACE report counts -
 * {@code W} weapons, {@code A} armour, {@code R} arrows (rounds), {@code F} food a soldier may eat
 * (from {@code tools/item-food-dumper}'s list, less what harms or is too precious to eat). Everything
 * else is just carried.
 */
public class GenerateItemClasses {

    private static final Set<String> WEAPON_SUFFIXES = Set.of("_sword", "_axe", "_spear");
    private static final Set<String> WEAPONS = Set.of("bow", "crossbow", "trident", "mace");
    private static final Set<String> ARMOUR_SUFFIXES = Set.of("_helmet", "_chestplate", "_leggings", "_boots");
    private static final Set<String> ROUNDS = Set.of("arrow", "spectral_arrow", "tipped_arrow");
    /** Food that poisons, teleports or is worth more than a meal: never eaten as rations. */
    private static final Set<String> NOT_RATIONS = Set.of("chorus_fruit", "poisonous_potato", "pufferfish", "rotten_flesh",
            "spider_eye", "suspicious_stew", "golden_apple", "enchanted_golden_apple");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: GenerateItemClasses <registries.json> <food-items.txt> <output.txt>");
            System.exit(1);
        }
        Set<String> food = new TreeSet<>();
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[1]))) {
            String name = line.replaceFirst(".*\\]: ", "").trim();
            if (!name.isEmpty() && !NOT_RATIONS.contains(name)) {
                food.add(name);
            }
        }

        JsonNode entries = new ObjectMapper().readTree(new File(args[0])).get("minecraft:item").get("entries");

        Map<Integer, String> classes = new TreeMap<>();
        Set<String> unmatched = new TreeSet<>(WEAPONS);
        unmatched.addAll(ROUNDS);
        unmatched.addAll(Set.of("stick", "crafting_table", "cobblestone", "wooden_pickaxe", "furnace", "coal", "charcoal", "torch"));
        unmatched.addAll(food);
        Iterator<Map.Entry<String, JsonNode>> it = entries.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey().replace("minecraft:", "");
            char kind = WEAPONS.contains(name) || WEAPON_SUFFIXES.stream().anyMatch(name::endsWith) ? 'W'
                    : ARMOUR_SUFFIXES.stream().anyMatch(name::endsWith) ? 'A'
                    : ROUNDS.contains(name) ? 'R'
                    : name.endsWith("_log") ? 'L'
                    : name.endsWith("_planks") ? 'P'
                    : name.equals("stick") ? 'S'
                    : name.equals("crafting_table") ? 'C'
                    : name.equals("cobblestone") ? 'K'
                    : name.endsWith("_pickaxe") ? 'X'
                    : food.contains(name) ? 'F'
                    : name.equals("furnace") ? 'U'
                    : name.equals("coal") || name.equals("charcoal") ? 'O'
                    : name.equals("torch") ? 'T'
                    : '-';
            if (kind != '-') {
                classes.put(entry.getValue().get("protocol_id").asInt(), kind + " " + name);
                unmatched.remove(name);
            }
        }
        // A curated name missing from the registry is a typo or a renamed item - fail loudly.
        if (!unmatched.isEmpty()) {
            throw new IllegalStateException("Curated item names not in the item registry: " + unmatched);
        }

        try (PrintWriter out = new PrintWriter(args[2], "UTF-8")) {
            out.println("# Generated from the pinned server jar's `--reports` registries.json by");
            out.println("# tools/item-class-generator. One counted item per line: <id> <class> <name>,");
            out.println("# W weapon, A armour, R arrows; and what a wooden sword is made from: L logs, P planks,");
            out.println("# S stick, C crafting table; K cobblestone, X pickaxes; F food eaten as rations;");
            out.println("# U furnace, O coal or charcoal, T torch.");
            out.println("# Everything NOT listed is just carried.");
            classes.forEach((id, line) -> out.println(id + " " + line));
        }
        System.out.println(classes.size() + " counted items written to " + args[2]);
    }
}
