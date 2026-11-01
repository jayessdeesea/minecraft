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
 * ../block-class-generator/GenerateBlockClasses.java. Reads the pinned server jar's own {@code
 * generated/reports/registries.json} (produced by {@code --reports}) and emits the resource
 * {@code bot/src/main/resources/.../hostile-entity-types-<version>.txt} that {@code
 * bot.world.HostileMobRegistry} loads at runtime: the {@code minecraft:entity_type} protocol IDs
 * of every entity on the curated list below.
 */
public class GenerateEntityHostility {

    // Entity types that attack a player on sight - from well-established vanilla mechanics, not
    // guessed. Deliberately excludes neutral/conditionally-hostile mobs (wolves, bees, piglins,
    // endermen, zombified piglins, iron golems, spiders-in-daylight edge cases aside) - grow this
    // incrementally if real play shows a gap.
    private static final Set<String> HOSTILE = Set.of(
            "blaze", "bogged", "breeze", "cave_spider", "creeper", "drowned", "elder_guardian",
            "ender_dragon", "endermite", "evoker", "ghast", "guardian", "hoglin", "husk",
            "magma_cube", "phantom", "piglin_brute", "pillager", "ravager", "shulker", "silverfish",
            "skeleton", "slime", "spider", "stray", "vex", "vindicator", "warden", "witch",
            "wither", "wither_skeleton", "zoglin", "zombie", "zombie_villager"
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: GenerateEntityHostility <registries.json> <output.txt>");
            System.exit(1);
        }

        JsonNode entries = new ObjectMapper().readTree(new File(args[0]))
                .get("minecraft:entity_type").get("entries");

        Map<Integer, String> hostile = new TreeMap<>();
        Set<String> unmatched = new TreeSet<>(HOSTILE);
        Iterator<Map.Entry<String, JsonNode>> it = entries.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey().replace("minecraft:", "");
            if (HOSTILE.contains(name)) {
                hostile.put(entry.getValue().get("protocol_id").asInt(), name);
                unmatched.remove(name);
            }
        }

        // A curated name missing from the registry is a typo or a renamed entity - fail, don't
        // silently ship a list that's quietly missing something.
        if (!unmatched.isEmpty()) {
            throw new IllegalStateException("Curated hostile names not in the entity_type registry: " + unmatched);
        }

        try (PrintWriter out = new PrintWriter(args[1])) {
            out.println("# Generated from the pinned server jar's `--reports` registries.json by");
            out.println("# tools/entity-hostility-generator. One hostile entity-type protocol ID per");
            out.println("# line: <id> <name>. Everything NOT listed here is treated as not hostile.");
            hostile.forEach((id, name) -> out.printf("%d %s%n", id, name));
        }

        System.out.println("Entity types: " + entries.size() + ", hostile: " + hostile.size());
    }
}
